package org.localprint.android;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.*;
import android.os.ParcelFileDescriptor;
import android.print.*;
import org.localprint.core.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class JobRunner {
    final AtomicBoolean canceled=new AtomicBoolean();
    volatile Closeable connection;
    volatile HttpURLConnection http;
    private final Context context;
    JobRunner(Context c){context=c;}
    void cancel(){canceled.set(true);try{if(connection!=null)connection.close();}catch(IOException ignored){}if(http!=null)http.disconnect();}
    void check()throws IOException{if(canceled.get()||Thread.currentThread().isInterrupted())throw new IOException("Печать отменена");}
    long run(ParcelFileDescriptor data,PrintJobInfo info,Store.Printer printer,Profile profile)throws Exception{
        File pdf=File.createTempFile("input-",".pdf",context.getCacheDir()),encoded=File.createTempFile("output-",".prn",context.getCacheDir());
        try{
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(data);OutputStream out=new FileOutputStream(pdf)){
                byte[] b=new byte[32768];int n;long size=0;
                while((n=in.read(b))!=-1){check();size+=n;if(size>100L*1024*1024)throw new IOException("Документ больше 100 МБ");out.write(b,0,n);}
            }
            encode(pdf,encoded,info,profile);
            check();send(encoded,printer,profile,info.getAttributes());return encoded.length();
        }finally{pdf.delete();encoded.delete();}
    }
    private static boolean selected(int page,PageRange[] ranges){if(ranges==null||ranges.length==0)return true;for(PageRange r:ranges)if(page>=r.getStart()&&page<=r.getEnd())return true;return false;}
    private void encode(File pdf,File encoded,PrintJobInfo info,Profile profile)throws Exception{
        PrintOptions o=profile.options();PrintAttributes a=info.getAttributes();
        PrintAttributes.MediaSize media=a.getMediaSize();
        if(media==null)media=PrintAttributes.MediaSize.ISO_A4;
        String id=media.getId();
        if(id.equals(PrintAttributes.MediaSize.NA_LETTER.getId())){o.paper="Letter";o.widthPoints=612;o.heightPoints=792;}
        else if(id.equals(PrintAttributes.MediaSize.NA_LEGAL.getId())){o.paper="Legal";o.widthPoints=612;o.heightPoints=1008;}
        else if(id.equals(PrintAttributes.MediaSize.ISO_A5.getId())){o.paper="A5";o.widthPoints=420;o.heightPoints=595;}
        if(a.getResolution()!=null)o.dpi=a.getResolution().getHorizontalDpi();
        o.check();
        int width=((int)Math.ceil(o.widthPoints*o.dpi/72.0)+7)&~7,height=(int)Math.ceil(o.heightPoints*o.dpi/72.0);
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd);
            OutputStream out=new LimitedOutput(new BufferedOutputStream(new FileOutputStream(encoded)))){
            if(renderer.getPageCount()>200)throw new IOException("За одно задание поддерживается до 200 страниц");
            int copies=info.getCopies();if(copies<1||copies>99||(long)copies*renderer.getPageCount()>500)throw new IOException("Слишком много копий или страниц");
            Driver driver=profile.engine.equals("spl")?new QpdlDriver():profile.engine.equals("pcl5")?new Pcl5Driver():profile.engine.equals("pdf")?new PdfDriver():new PostScriptDriver();
            {
                driver.begin(out,o);
                int outputPage=0;
                for(int c=0;c<copies;c++)for(int p=0;p<renderer.getPageCount();p++){
                    if(!selected(p,info.getPages()))continue;check();
                    if(outputPage++>=500)throw new IOException("Слишком много страниц");
                    try(PdfRaster raster=new PdfRaster(renderer.openPage(p),width,height)){
                        driver.page(out,raster,o);
                    }
                }
                if(outputPage==0)throw new IOException("В задании нет выбранных страниц");
                driver.end(out);
            }
        }
    }
    private void send(File data,Store.Printer p,Profile profile,PrintAttributes attrs)throws Exception{
        if(p.transport.equals("raw")){
            Socket socket=new Socket();connection=socket;
            java.util.Timer timer=new java.util.Timer(true);
            timer.schedule(new java.util.TimerTask(){@Override public void run(){try{socket.close();}catch(Exception ignored){}}},180000);
            try{
                socket.connect(new InetSocketAddress(p.host,p.port),7000);socket.setSoTimeout(15000);
                try(OutputStream out=new BufferedOutputStream(socket.getOutputStream());InputStream in=new FileInputStream(data)){copy(in,out);out.flush();socket.shutdownOutput();}
            }finally{timer.cancel();socket.close();connection=null;}
        }else{
            if(!profile.engine.equals("pdf"))throw new IOException("IPP/IPPS в этой версии требует профиль PDF Direct");
            String scheme=p.transport.equals("ipps")?"https":"http";
            URL url=new URI(scheme,null,p.host,p.port,p.path,null,null).toURL();
            http=(HttpURLConnection)url.openConnection();http.setConnectTimeout(7000);http.setReadTimeout(30000);http.setInstanceFollowRedirects(false);
            http.setRequestMethod("POST");http.setDoOutput(true);http.setRequestProperty("Content-Type","application/ipp");
            String uri=new URI(p.transport,null,p.host,p.port,p.path,null,null).toASCIIString();
            String media="iso_a4_210x297mm";
            if(attrs.getMediaSize()!=null){String id=attrs.getMediaSize().getId();if(id.equals(PrintAttributes.MediaSize.NA_LETTER.getId()))media="na_letter_8.5x11in";else if(id.equals(PrintAttributes.MediaSize.NA_LEGAL.getId()))media="na_legal_8.5x14in";else if(id.equals(PrintAttributes.MediaSize.ISO_A5.getId()))media="iso_a5_148x210mm";}
            byte[] header=IppProtocol.printJob(uri,"application/pdf",media);http.setFixedLengthStreamingMode(data.length()+header.length);
            try{
                try(OutputStream out=http.getOutputStream();InputStream in=new FileInputStream(data)){out.write(header);copy(in,out);}
                int status=http.getResponseCode();if(status!=200)throw new IOException("Сервер IPP: HTTP "+status);
                try(InputStream in=http.getInputStream()){IppProtocol.readStatus(in);}
            }finally{http.disconnect();http=null;}
        }
    }
    private void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){check();out.write(b,0,n);}}
    private static final class LimitedOutput extends FilterOutputStream{
        private long size;
        LimitedOutput(OutputStream out){super(out);}
        private void count(int n)throws IOException{size+=n;if(size>256L*1024*1024)throw new IOException("Задание больше 256 МБ. Раздели документ на части.");}
        @Override public void write(int b)throws IOException{count(1);out.write(b);}
        @Override public void write(byte[] b,int off,int len)throws IOException{count(len);out.write(b,off,len);}
    }
}
