package org.localprint.android;

import android.content.*;
import android.os.*;
import android.print.*;
import android.printservice.*;
import android.printservice.PrintJob;
import java.util.*;
import java.util.concurrent.*;

public final class LocalPrintService extends PrintService {
    private final ExecutorService workers=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<PrintJob,JobRunner> jobs=new HashMap<>();
    private final Map<PrintJob,Future<?>> futures=new HashMap<>();
    private final Map<PrintJob,ParcelFileDescriptor> descriptors=new HashMap<>();
    private Store store;
    @Override public void onCreate(){super.onCreate();store=new Store(this);store.serviceEvent("Служба создана · Android "+Build.VERSION.RELEASE);}
    @Override protected void onConnected(){super.onConnected();store.serviceEvent("Android подключился к службе");}
    @Override protected void onDisconnected(){store.serviceEvent("Android отключился от службы");super.onDisconnected();}
    @Override protected PrinterDiscoverySession onCreatePrinterDiscoverySession(){store.serviceEvent("Android запросил список принтеров");return new Session();}
    @Override protected void onPrintJobQueued(PrintJob job){
        Store.Printer printer=null;
        PrintJobInfo info=job.getInfo();
        for(Store.Printer p:store.printers())if(generatePrinterId(p.id).equals(info.getPrinterId()))printer=p;
        if(printer==null){job.fail("Принтер удалён. Добавь его в LocalPrint.");return;}
        Profile profile;
        try{profile=store.profile(printer.profile);}catch(Exception e){job.fail(e.getMessage());return;}
        if(!job.start())return;
        ParcelFileDescriptor data=job.getDocument().getData();
        if(data==null){job.fail("Android не передал документ");return;}
        JobRunner runner=new JobRunner(this);jobs.put(job,runner);descriptors.put(job,data);final Store.Printer target=printer;
        Future<?> future=workers.submit(()->{
            String result;
            try{
                long bytes=runner.run(data,info,target,profile);result=target.name+": отправлено принтеру, "+bytes+" байт";
                main.post(()->{if(!runner.canceled.get())job.complete();});
            }catch(Exception|OutOfMemoryError e){
                result=target.name+": "+(runner.canceled.get()?"отменено":e.getClass().getSimpleName()+": "+e.getMessage());
                final String message=result;main.post(()->{if(!runner.canceled.get())job.fail(message);});
            }finally{
                try{data.close();}catch(Exception ignored){}
                main.post(()->{jobs.remove(job);futures.remove(job);descriptors.remove(job);});
            }
            store.log(result);
        });
        futures.put(job,future);
    }
    @Override protected void onRequestCancelPrintJob(PrintJob job){
        JobRunner runner=jobs.get(job);if(runner!=null)runner.cancel();
        Future<?> f=futures.remove(job);if(f!=null)f.cancel(true);
        ParcelFileDescriptor data=descriptors.remove(job);if(data!=null)try{data.close();}catch(Exception ignored){}
        jobs.remove(job);job.cancel();store.log("Задание отменено");
    }
    @Override public void onDestroy(){for(JobRunner r:jobs.values())r.cancel();for(ParcelFileDescriptor d:descriptors.values())try{d.close();}catch(Exception ignored){}workers.shutdownNow();super.onDestroy();}
    private final class Session extends PrinterDiscoverySession {
        private final Set<PrinterId> published=new HashSet<>();
        private final android.content.SharedPreferences prefs=getSharedPreferences("localprint",MODE_PRIVATE);
        private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->{if("printers".equals(key)||"profiles".equals(key))publish();};
        Session(){prefs.registerOnSharedPreferenceChangeListener(listener);publish();}
        private void publish(){
            if(isDestroyed())return;
            List<PrinterInfo> all=new ArrayList<>();Set<PrinterId> current=new HashSet<>();
            for(Store.Printer p:store.printers()){
                try{
                    Profile profile=store.profile(p.profile);PrinterId id=generatePrinterId(p.id);
                    PrinterCapabilitiesInfo.Builder caps=new PrinterCapabilitiesInfo.Builder(id)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A4,true).addMediaSize(PrintAttributes.MediaSize.NA_LETTER,false)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A5,false).addMediaSize(PrintAttributes.MediaSize.NA_LEGAL,false)
                        .setMinMargins(new PrintAttributes.Margins(200,220,200,220))
                        .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME,PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE,PrintAttributes.DUPLEX_MODE_NONE);
                    int dpi=profile.engine.equals("spl")?600:300;
                    caps.addResolution(new PrintAttributes.Resolution("dpi"+dpi,dpi+" dpi",dpi,dpi),true);
                    all.add(new PrinterInfo.Builder(id,p.name,PrinterInfo.STATUS_IDLE)
                        .setDescription("LocalPrint · "+p.host+":"+p.port+" · "+profile.name).setCapabilities(caps.build()).build());
                    current.add(id);
                }catch(Exception e){
                    store.serviceEvent("Не удалось подготовить «"+p.name+"»: "+e.getClass().getSimpleName()+": "+e.getMessage());
                    // A broken/missing profile must not silently hide a saved printer.
                    PrinterId id=generatePrinterId(p.id);
                    all.add(new PrinterInfo.Builder(id,p.name,PrinterInfo.STATUS_UNAVAILABLE)
                        .setDescription("LocalPrint · проверь драйвер в приложении: "+e.getMessage()).build());
                    current.add(id);
                }
            }
            List<PrinterId> removed=new ArrayList<>(published);removed.removeAll(current);if(!removed.isEmpty())removePrinters(removed);
            if(!all.isEmpty())addPrinters(all);published.clear();published.addAll(current);
            store.serviceEvent("Передано в Android: "+all.size()+" принтер(ов)");
        }
        @Override public void onStartPrinterDiscovery(List<PrinterId> priorityList){store.serviceEvent("Поиск в системном списке начат");publish();}
        @Override public void onStopPrinterDiscovery(){store.serviceEvent("Поиск в системном списке остановлен");}
        @Override public void onValidatePrinters(List<PrinterId> ids){publish();}
        @Override public void onStartPrinterStateTracking(PrinterId id){publish();}
        @Override public void onStopPrinterStateTracking(PrinterId id){}
        @Override public void onDestroy(){prefs.unregisterOnSharedPreferenceChangeListener(listener);published.clear();}
    }
}
