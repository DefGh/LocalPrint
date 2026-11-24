package org.localprint.core;

import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

/** Streaming PDF writer: bounded memory per page, one-bit Flate images, no native dependencies. */
public final class PdfDriver implements Driver {
    private Counter out;
    private final Map<Integer,Long> offsets=new TreeMap<>();
    private final List<Integer> pages=new ArrayList<>();
    private void text(String s)throws IOException{Driver.ascii(out,s);}
    private void object(int id,String body)throws IOException{start(id);text(body+"\nendobj\n");}
    private void start(int id)throws IOException{offsets.put(id,out.position);text(id+" 0 obj\n");}
    @Override public void begin(OutputStream output,PrintOptions o)throws IOException{
        out=new Counter(output);text("%PDF-1.4\n");out.write(new byte[]{'%',(byte)0xe2,(byte)0xe3,(byte)0xcf,(byte)0xd3,10});
        object(1,"<< /Type /Catalog /Pages 2 0 R >>");
    }
    @Override public void page(OutputStream ignored,RasterPage raster,PrintOptions o)throws IOException{
        int page=3+3*pages.size(),image=page+1,content=page+2;pages.add(page);
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(DeflaterOutputStream zip=new DeflaterOutputStream(compressed)){
            for(int y=0;y<raster.height();y+=128){
                Driver.checkCanceled();int rows=Math.min(128,raster.height()-y);
                byte[] band=new byte[raster.stride()*rows];raster.readRows(y,rows,band);
                for(int i=0;i<band.length;i++)band[i]=(byte)~band[i];zip.write(band);
            }
        }
        object(page,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "+o.widthPoints+" "+o.heightPoints+"] "
            +"/Resources << /XObject << /Im0 "+image+" 0 R >> >> /Contents "+content+" 0 R >>");
        start(image);text("<< /Type /XObject /Subtype /Image /Width "+raster.width()+" /Height "+raster.height()
            +" /ColorSpace /DeviceGray /BitsPerComponent 1 /Filter /FlateDecode /Length "+compressed.size()+" >>\nstream\n");
        compressed.writeTo(out);text("\nendstream\nendobj\n");
        byte[] paint=("q\n"+o.widthPoints+" 0 0 "+o.heightPoints+" 0 0 cm\n/Im0 Do\nQ\n").getBytes("US-ASCII");
        start(content);text("<< /Length "+paint.length+" >>\nstream\n");out.write(paint);text("endstream\nendobj\n");
    }
    @Override public void end(OutputStream ignored)throws IOException{
        StringBuilder kids=new StringBuilder();for(int id:pages)kids.append(id).append(" 0 R ");
        object(2,"<< /Type /Pages /Count "+pages.size()+" /Kids ["+kids+"] >>");
        long xref=out.position;int count=3+3*pages.size();text("xref\n0 "+count+"\n0000000000 65535 f \n");
        for(int i=1;i<count;i++){
            if(!offsets.containsKey(i))throw new IOException("Invalid PDF object table");
            text(String.format(Locale.ROOT,"%010d 00000 n \n",offsets.get(i)));
        }
        text("trailer\n<< /Size "+count+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n");
    }
    private static final class Counter extends FilterOutputStream{
        long position;
        Counter(OutputStream out){super(out);}
        @Override public void write(int n)throws IOException{out.write(n);position++;}
        @Override public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);position+=len;}
    }
}
