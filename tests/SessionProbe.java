package org.localprint.android;

import android.app.*;
import android.content.*;
import android.graphics.pdf.PdfDocument;
import android.os.*;
import android.print.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import org.json.JSONObject;

/** Actual system printer list: empty session -> save -> update -> remove -> invalid profile. */
public final class SessionProbe extends Instrumentation {
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void status(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
    @Override public void onStart(){Bundle result=new Bundle();
        try{
            android.accessibilityservice.AccessibilityServiceInfo automation=getUiAutomation().getServiceInfo();
            automation.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(automation);
            AccessibilityNodeInfo initial=getUiAutomation().getRootInActiveWindow();
            if(initial!=null&&!initial.findAccessibilityNodeInfosByText("System UI isn't responding").isEmpty())clickText("Wait",3000);
            Context c=getTargetContext();Store store=new Store(c);
            for(Store.Printer p:store.printers())store.removePrinter(p.id);
            Store.Printer existing=new Store.Printer(new JSONObject().put("id","qa-existing").put("name","QA Existing SPL")
                .put("host","127.0.0.1").put("port",9100).put("profile","spl-samsung-scx-3200"));
            store.savePrinter(existing);
            Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            File document=new File(c.getCacheDir(),"session-probe.pdf");
            PdfDocument pdf=new PdfDocument();
            PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
            pdf.finishPage(page);try(OutputStream out=new FileOutputStream(document)){pdf.writeTo(out);}finally{pdf.close();}
            runOnMainSync(()->((PrintManager)activity.getSystemService(Context.PRINT_SERVICE)).print("Session probe",new PrintDocumentAdapter(){
                @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,CancellationSignal signal,LayoutResultCallback cb,Bundle extras){
                    cb.onLayoutFinished(new PrintDocumentInfo.Builder("probe.pdf").setPageCount(1).build(),true);
                }
                @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor fd,CancellationSignal signal,WriteResultCallback cb){
                    try(InputStream in=new FileInputStream(document);OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(fd)){
                        byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
                        cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                    }catch(Exception e){cb.onWriteFailed(e.getMessage());}
                }
            },null));
            clickId("com.android.printspooler:id/destination_spinner");
            clickText("All printers",15000);
            require(waitText("QA Existing SPL",true,15000),"Previously saved SPL printer missing");
            status("PASS: previously saved SPL printer appears in actual Android list");
            runOnMainSync(()->{try{store.removePrinter(existing.id);}catch(Exception e){throw new RuntimeException(e);}});
            require(waitText("QA Existing SPL",false,10000),"Previously saved printer removal");
            // The printer is deliberately saved AFTER Android has opened discovery.
            Store.Printer printer=new Store.Printer(new JSONObject().put("id","qa-session").put("name","QA Live Laser")
                .put("host","127.0.0.1").put("port",9100).put("profile","generic-pcl5"));
            runOnMainSync(()->{try{store.savePrinter(printer);}catch(Exception e){throw new RuntimeException(e);}});
            require(waitText("QA Live Laser",true,15000),"Saved printer missing from actual Android list");
            status("PASS: printer saved during active system discovery appears without restarting service");
            printer.name="QA Renamed Laser";
            runOnMainSync(()->{try{store.savePrinter(printer);}catch(Exception e){throw new RuntimeException(e);}});
            require(waitText("QA Renamed Laser",true,10000),"Renamed printer missing");
            runOnMainSync(()->{try{store.removePrinter(printer.id);}catch(Exception e){throw new RuntimeException(e);}});
            require(waitText("QA Renamed Laser",false,10000),"Deleted printer still listed");
            status("PASS: live rename and removal reach the Android list");
            printer.name="QA Missing Profile";printer.profile="missing-profile";
            runOnMainSync(()->{try{store.savePrinter(printer);}catch(Exception e){throw new RuntimeException(e);}});
            require(waitText("QA Missing Profile",true,10000),"Invalid-profile printer silently hidden");
            require(store.serviceHistory().contains("missing")||store.serviceHistory().contains("Профиль принтера не установлен"),"Profile failure absent from diagnostic log");
            status("PASS: missing profile remains visible as unavailable and records its cause");
            store.removePrinter(printer.id);
            getUiAutomation().performGlobalAction(1);
            getUiAutomation().performGlobalAction(1);
            result.putString("stream","SYSTEM DISCOVERY CHECKS PASSED\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){dump(getUiAutomation().getRootInActiveWindow(),0);StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.putString("stream",trace.toString());finish(Activity.RESULT_CANCELED,result);}
    }
    private boolean waitText(String text,boolean present,long timeout)throws InterruptedException{
        long end=System.currentTimeMillis()+timeout;
        do{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();boolean found=root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();
            if(found==present)return true;Thread.sleep(100);
        }while(System.currentTimeMillis()<end);return false;
    }
    private void clickText(String text,long timeout)throws InterruptedException{
        long end=System.currentTimeMillis()+timeout;
        do{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){
                for(int i=0;i<6&&node!=null;i++,node=node.getParent())if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
            }Thread.sleep(100);
        }while(System.currentTimeMillis()<end);throw new AssertionError("Missing UI text: "+text);
    }
    private void clickId(String id)throws InterruptedException{
        long end=System.currentTimeMillis()+15000;
        do{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByViewId(id))if(node.isEnabled()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
            Thread.sleep(100);
        }while(System.currentTimeMillis()<end);throw new AssertionError("Missing UI control: "+id);
    }
    private void dump(AccessibilityNodeInfo n,int depth){if(n==null||depth>12)return;status("UI "+n.getClassName()+" "+n.getViewIdResourceName()+" text="+n.getText()+" desc="+n.getContentDescription());for(int i=0;i<n.getChildCount();i++)dump(n.getChild(i),depth+1);}
}
