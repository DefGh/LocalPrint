package org.localprint.android;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import java.io.*;

/** Captures only the disposable test device, not the user's desktop. */
public final class UiSnapshotProbe extends Instrumentation {
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Store store=new Store(getTargetContext());for(Store.Printer p:store.printers())store.removePrinter(p.id);
        Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        capture("main.png");press(activity,"Драйверы");capture("drivers.png");press(activity,"Принтеры");press(activity,"Найти вручную");capture("printer-dialog.png");
        result.putString("stream","UI snapshots captured\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,result);}}
    private void press(Activity a,String label){runOnMainSync(()->{View v=find(a.getWindow().getDecorView(),label);if(v==null)throw new AssertionError(label);v.performClick();});waitForIdleSync();}
    private View find(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private void capture(String name)throws Exception{
        Thread.sleep(400);Bitmap b=getUiAutomation().takeScreenshot();
        File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
    }
}
