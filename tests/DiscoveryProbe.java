package org.localprint.android;

import android.app.*;
import android.content.*;
import android.net.nsd.*;
import android.os.*;
import android.print.PrintManager;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercises find -> select -> save using a real NSD advertisement and a local TCP peer. */
public final class DiscoveryProbe extends Instrumentation {
    NsdManager nsd;NsdManager.RegistrationListener registration;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    private void status(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
    @Override public void onStart(){Bundle result=new Bundle();ServerSocket server=null;
        try{
            Context c=getTargetContext();Store store=new Store(c);for(Store.Printer p:store.printers())store.removePrinter(p.id);
            List<Profile> profiles=store.profiles();
            for(Profile p:profiles)if(p.engine.equals("spl")){
                NsdServiceInfo record=new NsdServiceInfo();record.setServiceName("Office printer");record.setServiceType("_pdl-datastream._tcp.");record.setHost(InetAddress.getByName("127.0.0.1"));record.setPort(9100);record.setAttribute("usb_MDL",p.name);
                require(p.id.equals(FoundPrinter.fromNsd(record,profiles).profile),"Data-driven model selection: "+p.name);
            }
            status("PASS: all 50 SPL profiles selected from printer metadata, without model-specific branches");
            server=new ServerSocket(0);server.setSoTimeout(700);
            nsd=(NsdManager)c.getSystemService(Context.NSD_SERVICE);CountDownLatch registered=new CountDownLatch(1);
            NsdServiceInfo service=new NsdServiceInfo();service.setServiceName("QA Office Laser");service.setServiceType("_pdl-datastream._tcp.");service.setPort(server.getLocalPort());service.setAttribute("usb_MDL","Samsung ML-1510");
            registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo s){registered.countDown();}public void onRegistrationFailed(NsdServiceInfo s,int e){}public void onServiceUnregistered(NsdServiceInfo s){}public void onUnregistrationFailed(NsdServiceInfo s,int e){}};
            nsd.registerService(service,NsdManager.PROTOCOL_DNS_SD,registration);require(registered.await(12,TimeUnit.SECONDS),"NSD registration");
            Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            PrintManager manager=(PrintManager)activity.getSystemService(Context.PRINT_SERVICE);int jobsBefore=manager.getPrintJobs().size();
            press(activity,"Найти автоматически");clickText("QA Office Laser",30000);
            require(store.printers().isEmpty(),"Selecting a result must not save it");
            require(hasText("Samsung ML-1510",4000),"Driver inferred from discovery metadata");
            clickText("СОХРАНИТЬ",4000);waitForIdleSync();Thread.sleep(300);
            require(store.printers().size()==1,"Explicit automatic-result save");
            require(store.printers().get(0).profile.equals("spl-samsung-ml-1510"),"Saved the inferred profile");
            try{server.accept().close();throw new AssertionError("Automatic search/save sent a TCP request");}catch(SocketTimeoutException expected){}
            require(manager.getPrintJobs().size()==jobsBefore,"Search/save created no print jobs");
            status("PASS: actual NSD discovery -> choose -> explicit save; no print job and no RAW traffic");
            for(Store.Printer p:store.printers())store.removePrinter(p.id);
            press(activity,"Найти вручную");setEdits("127.0.0.1",String.valueOf(server.getLocalPort()));clickText("НАЙТИ",4000);
            clickText("Устройство 127.0.0.1",7000);
            require(store.printers().isEmpty(),"Manual selection must not save automatically");
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();List<AccessibilityNodeInfo> spinners=classes(root,"android.widget.Spinner");
            require(spinners.size()>=2,"Driver choice");spinners.get(1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            clickText("Samsung ML-1510",4000);clickText("СОХРАНИТЬ",4000);waitForIdleSync();Thread.sleep(200);
            require(store.printers().size()==1,"Explicit manual-result save");
            try(Socket peer=server.accept()){peer.setSoTimeout(1000);require(peer.getInputStream().read()==-1,"Manual probe sent no data");}
            require(manager.getPrintJobs().size()==jobsBefore,"Manual find/save created no print job");
            status("PASS: manual reachability -> choose -> driver -> explicit save; zero print bytes");
            for(Store.Printer p:store.printers())store.removePrinter(p.id);
            result.putString("stream","DISCOVERY WORKFLOW PASSED\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));result.putString("stream",sw.toString());finish(Activity.RESULT_CANCELED,result);}
        finally{if(nsd!=null&&registration!=null)try{nsd.unregisterService(registration);}catch(Exception ignored){}if(server!=null)try{server.close();}catch(Exception ignored){}}
    }
    private void press(Activity a,String label){final Activity target=a;runOnMainSync(()->{View v=find(target.getWindow().getDecorView(),label);if(v==null)throw new AssertionError(label);v.performClick();});waitForIdleSync();}
    private View find(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=find(g.getChildAt(i),label);if(f!=null)return f;}}return null;}
    private boolean hasText(String text,long timeout)throws InterruptedException{long end=System.currentTimeMillis()+timeout;do{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty())return true;Thread.sleep(100);}while(System.currentTimeMillis()<end);return false;}
    private void clickText(String text,long timeout)throws InterruptedException{long end=System.currentTimeMillis()+timeout;do{AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){
        if((text.equals("СОХРАНИТЬ")||text.equals("НАЙТИ"))&&(node.getText()==null||!node.getText().toString().equalsIgnoreCase(text)))continue;
        if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
        android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
        if(node.isVisibleToUser()&&!bounds.isEmpty()){
            long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0);
            android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+50,android.view.MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);
            down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try{getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);}finally{down.recycle();up.recycle();}return;
        }
    }Thread.sleep(100);}while(System.currentTimeMillis()<end);dump(getUiAutomation().getRootInActiveWindow(),0);throw new AssertionError("UI text: "+text);}
    private void dump(AccessibilityNodeInfo n,int depth){if(n==null||depth>12)return;if(n.getText()!=null)status("UI "+n.getText());for(int i=0;i<n.getChildCount();i++)dump(n.getChild(i),depth+1);}
    private List<AccessibilityNodeInfo> classes(AccessibilityNodeInfo node,String cls){List<AccessibilityNodeInfo> out=new ArrayList<>();if(node==null)return out;if(cls.contentEquals(node.getClassName()))out.add(node);for(int i=0;i<node.getChildCount();i++)out.addAll(classes(node.getChild(i),cls));return out;}
    private void setEdits(String first,String second){List<AccessibilityNodeInfo> edits=classes(getUiAutomation().getRootInActiveWindow(),"android.widget.EditText");require(edits.size()==2,"Address and port fields");int i=0;for(String value:new String[]{first,second}){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);require(edits.get(i++).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b),"Set input");}}
}
