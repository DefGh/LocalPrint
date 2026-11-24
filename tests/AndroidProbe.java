package org.localprint.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.*;
import android.os.*;
import android.print.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import org.localprint.core.*;
import java.io.*;
import java.util.*;

/** On-device integration probe, run only in the disposable emulator. */
public final class AndroidProbe extends Instrumentation {
    private File document;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void status(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(0,b);}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context c=getTargetContext();Store store=new Store(c);
            List<Profile> profiles=store.profiles();require(profiles.size()==53,"53 bundled profiles");
            for(Profile p:profiles)p.options().check();
            JSONObject catalogue=new JSONArray(store.asset("catalog.json")).getJSONObject(0);
            Profile remote=DriverDownloads.download(catalogue);require(remote.engine.equals("spl"),"OpenPrinting download");
            try{new Profile(new JSONObject("{\"id\":\"bad\",\"name\":\"Bad\",\"engine\":\"shell\"}"));throw new AssertionError("Executable engine accepted");}catch(JSONException expected){}
            try{Profile.fromPpd("*ColorDevice: True\n", "test");throw new AssertionError("Unsupported PPD accepted");}catch(JSONException expected){}
            status("PASS: 53 profiles, pinned OpenPrinting download and unsafe-profile rejection");
            document=new File(c.getCacheDir(),"integration.pdf");
            PdfDocument pdfDocument=new PdfDocument();
            try(OutputStream out=new FileOutputStream(document)){
                PdfDocument pdf=pdfDocument;
                for(int n=1;n<=2;n++){
                    PdfDocument.Page p=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,n).create());
                    Paint paint=new Paint();paint.setColor(Color.BLACK);paint.setTextSize(18);
                    p.getCanvas().drawRect(72,72,144,144,paint);
                    p.getCanvas().drawText("LocalPrint Android / page "+n,72,190,paint);
                    p.getCanvas().drawText("Привет! Проверка службы печати",72,225,paint);
                    pdf.finishPage(p);
                }
                pdf.writeTo(out);
            }finally{pdfDocument.close();}
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(document,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd);
                PdfRaster page=new PdfRaster(pdf.openPage(0),4960,7017)){
                byte[] row=new byte[page.stride()];page.readRows(900,1,row);int black=0;
                for(byte b:row)black+=Integer.bitCount(b&255);
                require(black>=590&&black<=610,"600 dpi square width: "+black);
                page.readRows(20,1,row);for(byte b:row)require(b==0,"White page margin");
                try(OutputStream out=new FileOutputStream(new File(c.getCacheDir(),"android.qpdl"))){QpdlDriver q=new QpdlDriver();PrintOptions o=new PrintOptions();q.begin(out,o);q.page(out,page,o);q.end(out);}
            }
            status("PASS: Android PDF rendering, strip cache and SPL encoding");
            Store.Printer printer=new Store.Printer(new JSONObject().put("id","qa-samsung").put("name","QA Samsung SCX-3200")
                .put("host","10.0.2.2").put("port",19100).put("profile","spl-samsung-scx-3200"));
            store.savePrinter(printer);
            Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            final android.print.PrintJob[] active=new android.print.PrintJob[1];
            PrintAttributes attrs=new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("dpi600","600 dpi",600,600))
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).setMinMargins(new PrintAttributes.Margins(200,220,200,220)).build();
            runOnMainSync(()->{
                PrintManager pm=(PrintManager)activity.getSystemService(Context.PRINT_SERVICE);
                active[0]=pm.print("LocalPrint integration",new PrintDocumentAdapter(){
                    @Override public void onLayout(PrintAttributes oldAttrs,PrintAttributes newAttrs,CancellationSignal cancel,LayoutResultCallback cb,Bundle extras){
                        if(cancel.isCanceled())cb.onLayoutCancelled();else cb.onLayoutFinished(new PrintDocumentInfo.Builder("integration.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(2).build(),true);
                    }
                    @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor target,CancellationSignal cancel,WriteResultCallback cb){
                        new Thread(()->{try(InputStream in=new FileInputStream(document);OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(target)){
                            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(cancel.isCanceled()){cb.onWriteCancelled();return;}out.write(b,0,n);}cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                        }catch(Exception e){cb.onWriteFailed(e.getMessage());}}).start();
                    }
                },attrs);
            });
            PrintJobInfo.Builder builder=new PrintJobInfo.Builder(active[0].getInfo());builder.setAttributes(attrs);builder.setCopies(1);builder.setPages(new PageRange[]{new PageRange(1,1)});
            PrintJobInfo selected=builder.build();
            for(String engine:new String[]{"pcl5","ps","pdf"}){
                Profile p=new Profile(new JSONObject().put("id","qa-"+engine).put("name","QA").put("engine",engine));
                Store.Printer target=new Store.Printer(new JSONObject().put("id","qa").put("name","QA "+engine).put("host","10.0.2.2").put("port",19100).put("profile",p.id));
                JobRunner runner=new JobRunner(c);runner.run(ParcelFileDescriptor.open(document,ParcelFileDescriptor.MODE_READ_ONLY),selected,target,p);
                status("PASS: Android render + selected-page + "+engine+" socket transport");
            }
            Profile pdfProfile=store.profile("generic-pdf");
            Store.Printer ipp=new Store.Printer(new JSONObject().put("id","qa-ipp").put("name","QA IPP").put("host","10.0.2.2").put("port",19631).put("profile",pdfProfile.id).put("transport","ipp").put("path","/ipp/print"));
            new JobRunner(c).run(ParcelFileDescriptor.open(document,ParcelFileDescriptor.MODE_READ_ONLY),selected,ipp,pdfProfile);
            status("PASS: IPP HTTP Print-Job and successful IPP response");
            status("Checking the Android print dialog");
            clickId("com.android.printspooler:id/destination_spinner");
            if(!tryClickText("QA Samsung SCX-3200",3000)){
                if(!tryClickText("All printers…",2000))clickText("All printers");
                clickText("QA Samsung SCX-3200");
            }
            clickId("com.android.printspooler:id/print_button");
            tryClickText("OK",2000);
            long deadline=System.currentTimeMillis()+240000;
            while(System.currentTimeMillis()<deadline){
                Thread.sleep(1000);int state=active[0].getInfo().getState();
                if(state==PrintJobInfo.STATE_COMPLETED){status("PASS: real Android PrintService job completed");result.putString("stream","ALL ANDROID CHECKS PASSED\n");finish(Activity.RESULT_OK,result);return;}
                if(state==PrintJobInfo.STATE_FAILED||state==PrintJobInfo.STATE_CANCELED)throw new AssertionError("System print job ended with state "+state+" / "+store.lastJob());
            }
            throw new AssertionError("Timed out waiting for print dialog");
        }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.putString("stream",trace.toString());finish(Activity.RESULT_CANCELED,result);}
    }
    private boolean tryClickText(String text,long timeout)throws InterruptedException{
        long deadline=System.currentTimeMillis()+timeout;
        do{
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null){
                for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){
                    if(text.contentEquals(node.getText()==null?"":node.getText())){
                        for(int i=0;i<6&&node!=null;i++,node=node.getParent())if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
                    }
                }
            }
            Thread.sleep(100);
        }while(System.currentTimeMillis()<deadline);
        return false;
    }
    private void clickText(String text)throws InterruptedException{
        if(!tryClickText(text,10000)){dumpUi(getUiAutomation().getRootInActiveWindow(),0);throw new AssertionError("UI text missing: "+text);}
    }
    private void clickId(String id)throws InterruptedException{
        long deadline=System.currentTimeMillis()+10000;
        do{
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByViewId(id))if(n.isEnabled()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
            Thread.sleep(100);
        }while(System.currentTimeMillis()<deadline);
        dumpUi(getUiAutomation().getRootInActiveWindow(),0);throw new AssertionError("UI control missing: "+id);
    }
    private void dumpUi(AccessibilityNodeInfo node,int depth){
        if(node==null||depth>14)return;
        status("UI "+depth+" "+node.getViewIdResourceName()+" text="+node.getText()+" desc="+node.getContentDescription());
        for(int i=0;i<node.getChildCount();i++)dumpUi(node.getChild(i),depth+1);
    }
}
