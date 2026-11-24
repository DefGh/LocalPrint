package org.localprint.android;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.net.nsd.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.localprint.R;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int INK=0xff172b30,MUTED=0xff54686e,TEAL=0xff136f63;
    private Store store;
    private LinearLayout root,content;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<NsdManager.DiscoveryListener> discoveries=new ArrayList<>();
    private int screen;
    private boolean alive=true;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);
        getWindow().setStatusBarColor(0xfff5f7f4);getWindow().setNavigationBarColor(0xfff5f7f4);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff5f7f4);
        root.setPadding(dp(20),dp(16),dp(20),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(20),insets.getSystemWindowInsetTop()+dp(12),dp(20),insets.getSystemWindowInsetBottom()+dp(8));return insets;});
        setContentView(root);
        TextView title=text("LocalPrint",30,INK);title.setTypeface(null,android.graphics.Typeface.BOLD);root.addView(title);
        root.addView(text("Печать напрямую. Без компьютера.",15,MUTED));
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(0,dp(16),0,dp(8));
        String[] names={"Принтеры","Драйверы","Журнал"};
        for(int i=0;i<names.length;i++){final int page=i;Button b=button(names[i],()->show(page));tabs.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}
        root.addView(tabs);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));show(0);
    }
    @Override protected void onResume(){super.onResume();if(content!=null)show(screen);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));return t;}
    private Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEAL);b.setOnClickListener(v->action.run());return b;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(10),dp(16),dp(14));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));bg.setStroke(dp(1),0xffdce5df);c.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));content.addView(c,lp);return c;}
    private void show(int page){screen=page;content.removeAllViews();if(page==0)printers();else if(page==1)drivers();else journal();}
    private boolean enabled(){
        android.print.PrintManager manager=(android.print.PrintManager)getSystemService(PRINT_SERVICE);
        ComponentName service=new ComponentName(this,LocalPrintService.class);
        if(Build.VERSION.SDK_INT>=33&&manager!=null){
            try{return (Boolean)android.print.PrintManager.class.getMethod("isPrintServiceEnabled",ComponentName.class).invoke(manager,service);}
            catch(Exception ignored){}
        }
        try{
            String values=Settings.Secure.getString(getContentResolver(),"enabled_print_services");
            if(values!=null)for(String value:values.split(":"))if(service.equals(ComponentName.unflattenFromString(value)))return true;
        }catch(SecurityException ignored){}
        return false;
    }
    private void printers(){
        LinearLayout search=card();search.addView(text("Найти → выбрать → сохранить",21,INK));
        search.addView(text("Найди принтер автоматически в сети или укажи его адрес вручную. После выбора проверь драйвер и сохрани принтер.",15,MUTED));
        search.addView(button("Найти автоматически",this::discover));search.addView(button("Найти вручную",this::manualSearch));
        List<Store.Printer> printers=store.printers();
        if(printers.isEmpty()){
            LinearLayout empty=card();empty.addView(text("Сохранённых принтеров пока нет",20,INK));empty.addView(text("Поиск и сохранение не отправляют задания на печать. Сохранённые принтеры будут доступны в стандартном меню Android.",14,MUTED));
        }
        for(Store.Printer p:printers){
            LinearLayout c=card();c.addView(text(p.name,21,INK));c.addView(text(p.host+":"+p.port+" · "+p.transport.toUpperCase(Locale.ROOT),14,MUTED));
            try{c.addView(text(store.profile(p.profile).name,14,MUTED));}catch(Exception e){c.addView(text("Выбери установленный профиль",14,MUTED));}
            LinearLayout actions=new LinearLayout(this);actions.addView(button("Изменить",()->edit(p,p.name,p.host,p.port,p.transport,p.path,p.profile)),new LinearLayout.LayoutParams(0,-2,1));
            actions.addView(button("Проверить связь",()->probe(p)),new LinearLayout.LayoutParams(0,-2,1));c.addView(actions);
            c.addView(button("Удалить",()->new AlertDialog.Builder(this).setMessage("Удалить «"+p.name+"» из LocalPrint?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{try{store.removePrinter(p.id);show(0);}catch(Exception e){error(e);}}).show()));
        }
        LinearLayout intro=card();intro.addView(text(enabled()?"Служба печати включена":"Включи службу для печати",20,INK));
        intro.addView(text("После сохранения принтера включи LocalPrint в настройках. Сам документ отправляется из меню «Печать» нужного приложения.",14,MUTED));
        intro.addView(button("Открыть настройки печати",()->startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))));
        content.addView(text("Версия 0.1 · A4, A5, Letter, Legal · чёрно-белая печать · одна сторона. Данные отправляются в локальную сеть.",12,MUTED));
    }
    private EditText field(LinearLayout layout,String label,String value,int type){TextView caption=text(label,14,MUTED);layout.addView(caption);EditText e=new EditText(this);e.setTag(caption);e.setSingleLine(true);e.setInputType(type);e.setText(value);layout.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private void edit(Store.Printer old,String name,String host,int port,String transport,String path,String selectedProfile){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),0,dp(20),0);
        EditText nameField=field(form,"Название",name,android.text.InputType.TYPE_CLASS_TEXT);
        EditText hostField=field(form,"IP или имя узла — без :порта",host,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText portField=field(form,"Порт",String.valueOf(port),android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(text("Подключение",14,MUTED));Spinner protocols=new Spinner(this);String[] protocolsNames={"RAW / JetDirect","IPP · PDF Direct","IPPS · PDF Direct"};String[] protocolValues={"raw","ipp","ipps"};
        protocols.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,protocolsNames));form.addView(protocols);
        protocols.setSelection(Arrays.asList(protocolValues).indexOf(transport));
        EditText pathField=field(form,"Путь IPP",path,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        pathField.setVisibility(transport.equals("raw")?View.GONE:View.VISIBLE);((View)pathField.getTag()).setVisibility(pathField.getVisibility());
        form.addView(text("Драйвер / профиль модели",14,MUTED));Spinner profiles=new Spinner(this);List<Profile> available=store.profiles();List<String> labels=new ArrayList<>();labels.add("Выбери совместимый профиль…");for(Profile p:available)labels.add(p.toString());
        profiles.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));form.addView(profiles);
        for(int i=0;i<available.size();i++)if(available.get(i).id.equals(selectedProfile))profiles.setSelection(i+1);
        protocols.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            boolean first=true;
            @Override public void onNothingSelected(android.widget.AdapterView<?> a){}
            @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int position,long id){
                pathField.setVisibility(position==0?View.GONE:View.VISIBLE);((View)pathField.getTag()).setVisibility(pathField.getVisibility());
                if(first){first=false;return;}
                String p=portField.getText().toString();if(p.equals("9100")||p.equals("631"))portField.setText(position==0?"9100":"631");
                if(position>0)for(int i=0;i<available.size();i++)if(available.get(i).engine.equals("pdf"))profiles.setSelection(i+1);
            }
        });
        form.addView(text("Выбери профиль своей модели либо универсальный драйвер, который поддерживает принтер.",12,MUTED));
        ScrollView sc=new ScrollView(this);sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(old==null?"Сохранить выбранный принтер":"Настройки принтера").setView(sc).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                int index=profiles.getSelectedItemPosition()-1;if(index<0)throw new IOException("Выбери драйвер для модели");
                Profile profile=available.get(index);String mode=protocolValues[protocols.getSelectedItemPosition()];
                if(!mode.equals("raw")&&!profile.engine.equals("pdf"))throw new IOException("Для IPP/IPPS выбери PDF Direct");
                Store.Printer p=new Store.Printer(new JSONObject().put("id",old==null?UUID.randomUUID().toString():old.id).put("name",nameField.getText().toString().trim())
                    .put("host",hostField.getText().toString().trim()).put("port",Integer.parseInt(portField.getText().toString()))
                    .put("profile",profile.id).put("transport",mode).put("path",pathField.getText().toString().trim()));
                store.savePrinter(p);dialog.dismiss();show(0);
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
    private void drivers(){
        LinearLayout intro=card();intro.addView(text("Движки уже установлены",21,INK));
        intro.addView(text("Samsung SPL/QPDL 0x11 · PCL 5 Mono · PostScript Level 2 · PDF Direct. Все работают на телефоне.",15,MUTED));
        intro.addView(text("Профиль задаёт параметры модели; он не добавляет новый язык печати. Windows-драйверы .exe и .dll не поддерживаются.",13,MUTED));
        intro.addView(button("Импорт профиля JSON или PPD",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,11);}));
        LinearLayout installed=card();installed.addView(text("Установленные профили",20,INK));
        for(Profile p:store.profiles())installed.addView(text(p.toString(),14,MUTED));
        LinearLayout channel=card();channel.addView(text("Каталог OpenPrinting / SpliX",21,INK));
        channel.addView(text("Скачиваются только совместимые монохромные профили. Контрольная сумма проверяется. Цветной SPL, JBIG, CAPT, PCL XL и фирменные GDI пока не поддерживаются.",13,MUTED));
        try{
            JSONArray entries=new JSONArray(store.asset("catalog.json"));
            for(int i=0;i<entries.length();i++){
                JSONObject e=entries.getJSONObject(i);channel.addView(button("↓ "+e.getString("name"),()->download(e)));
            }
        }catch(Exception e){channel.addView(text("Каталог недоступен: "+e.getMessage(),14,MUTED));}
        content.addView(button("Лицензии и исходники",this::licenses));
    }
    private void download(JSONObject entry){
        Toast.makeText(this,"Скачиваю профиль…",Toast.LENGTH_SHORT).show();
        worker.submit(()->{try{Profile p=DriverDownloads.download(entry);store.installProfile(p);main.post(()->{if(alive){Toast.makeText(this,"Профиль установлен: "+p.name,Toast.LENGTH_LONG).show();show(1);}});}catch(Exception e){main.post(()->error(e));}});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=11||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();worker.submit(()->{try{
            String source;try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Файл не найден");source=Store.read(in,256*1024);}
            Profile p=source.trim().startsWith("{")?new Profile(new JSONObject(source)):Profile.fromPpd(source,"Импорт PPD");
            main.post(()->{if(alive)new AlertDialog.Builder(this).setTitle("Установить профиль?").setMessage(p.name+"\nДвижок: "+p.engine.toUpperCase(Locale.ROOT))
                .setNegativeButton("Отмена",null).setPositiveButton("Установить",(d,w)->{try{store.installProfile(p);show(1);}catch(Exception e){error(e);}}).show();});
        }catch(Exception e){main.post(()->error(e));}});
    }
    private void journal(){LinearLayout c=card();c.addView(text("Последнее задание",21,INK));c.addView(text(store.lastJob(),15,MUTED));c.addView(text("Статус «отправлено» означает, что данные переданы принтеру. У RAW нет универсального подтверждения выхода листа.",13,MUTED));content.addView(button("Обновить",()->show(2)));}
    private void licenses(){try{TextView t=text(store.asset("NOTICE.txt"),13,INK);t.setPadding(dp(20),dp(8),dp(20),dp(8));t.setTextIsSelectable(true);ScrollView s=new ScrollView(this);s.addView(t);new AlertDialog.Builder(this).setTitle("LocalPrint · GPL-2.0").setView(s).setPositiveButton("Закрыть",null).show();}catch(Exception e){error(e);}}
    private void probe(Store.Printer p){
        worker.submit(()->{try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(p.host,p.port),5000);main.post(()->{if(alive)new AlertDialog.Builder(this).setTitle("Связь есть").setMessage(p.host+":"+p.port+" отвечает. Теперь можно отправить документ через меню «Печать».").setPositiveButton("ОК",null).show();});}catch(Exception e){main.post(()->error(new IOException("Нет связи с "+p.host+":"+p.port+". Проверь сеть Wi-Fi и питание принтера.",e)));}});
    }
    private void error(Exception e){if(alive)new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("ОК",null).show();}
    private void chooseFound(FoundPrinter found){edit(null,found.name,found.host,found.port,found.transport,found.path,found.profile);}
    private void manualSearch(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),0,dp(20),0);
        EditText host=field(form,"IP или имя узла — без :порта","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(text("Подключение",14,MUTED));Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"RAW / JetDirect","IPP","IPPS"}));form.addView(mode);
        EditText port=field(form,"Порт","9100",android.text.InputType.TYPE_CLASS_NUMBER);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> a){}public void onItemSelected(android.widget.AdapterView<?> a,View v,int p,long id){String s=port.getText().toString();if(s.equals("9100")||s.equals("631"))port.setText(p==0?"9100":"631");}});
        form.addView(text("Проверяем только доступность порта. Пробные страницы и команды печати не отправляются.",13,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Найти по адресу").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Найти",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String address=host.getText().toString().trim();int number;
            try{number=Integer.parseInt(port.getText().toString());if(address.isEmpty()||address.contains(":")||address.contains("/")||number<1||number>65535)throw new Exception();}
            catch(Exception e){Toast.makeText(this,"Проверь адрес и порт",Toast.LENGTH_LONG).show();return;}
            String transport=new String[]{"raw","ipp","ipps"}[mode.getSelectedItemPosition()];
            Button find=dialog.getButton(AlertDialog.BUTTON_POSITIVE);find.setEnabled(false);find.setText("Поиск…");
            worker.submit(()->{try(Socket socket=new Socket()){
                socket.connect(new InetSocketAddress(address,number),5000);
                FoundPrinter found=new FoundPrinter("Устройство "+address,address,number,transport,"/ipp/print",null);
                main.post(()->{if(!alive||!dialog.isShowing())return;dialog.dismiss();new AlertDialog.Builder(this).setTitle("Выбери найденное устройство")
                    .setItems(new String[]{found.toString()},(dd,index)->chooseFound(found)).setNegativeButton("Отмена",null).show();});
            }catch(Exception e){main.post(()->{if(!alive||!dialog.isShowing())return;find.setEnabled(true);find.setText("Найти");error(new IOException("Порт недоступен. Проверь адрес, сеть и питание устройства."));});}});
        }));dialog.show();
    }
    private void discover(){
        stopDiscovery();final NsdManager nsd=(NsdManager)getSystemService(NSD_SERVICE);
        List<FoundPrinter> found=new ArrayList<>();Set<String> names=new HashSet<>(),endpoints=new HashSet<>();
        ArrayAdapter<FoundPrinter> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,found);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),0,dp(18),0);
        TextView status=text("Идёт поиск в локальной сети…",15,MUTED);panel.addView(status);
        panel.addView(text("Выбери принтер в списке, затем сохрани. Если устройство не объявляет себя в сети, воспользуйся ручным поиском.",13,MUTED));
        ListView list=new ListView(this);list.setAdapter(adapter);panel.addView(list,new LinearLayout.LayoutParams(-1,dp(280)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Выбери найденный принтер").setView(panel).setNegativeButton("Закрыть",null).setNeutralButton("Вручную",(d,w)->manualSearch()).create();
        list.setOnItemClickListener((a,v,position,id)->{FoundPrinter selected=found.get(position);dialog.dismiss();chooseFound(selected);});
        ArrayDeque<NsdServiceInfo> queue=new ArrayDeque<>();Map<String,Integer> retries=new HashMap<>();boolean[] resolving={false};Runnable[] next={null};
        next[0]=()->{
            if(!alive||!dialog.isShowing()||resolving[0]||queue.isEmpty())return;
            resolving[0]=true;NsdServiceInfo service=queue.poll();
            try{nsd.resolveService(service,new NsdManager.ResolveListener(){
                @Override public void onResolveFailed(NsdServiceInfo info,int code){main.post(()->{
                    resolving[0]=false;String key=service.getServiceName()+service.getServiceType();int attempt=retries.containsKey(key)?retries.get(key):0;
                    if(attempt<2&&dialog.isShowing()){retries.put(key,attempt+1);main.postDelayed(()->{if(dialog.isShowing()){queue.add(service);next[0].run();}},600);}
                    next[0].run();
                });}
                @Override public void onServiceResolved(NsdServiceInfo info){main.post(()->{
                    if(alive&&dialog.isShowing())try{FoundPrinter printer=FoundPrinter.fromNsd(info,store.profiles());if(endpoints.add(printer.key())){adapter.add(printer);status.setText("Найдено: "+found.size()+". Поиск продолжается…");}}catch(Exception ignored){}
                    resolving[0]=false;next[0].run();
                });}
            });}catch(Exception e){resolving[0]=false;next[0].run();}
        };
        Runnable timeout=()->{if(dialog.isShowing()){stopDiscovery();status.setText(found.isEmpty()?"Принтеры не объявлены в этой сети. Попробуй ручной поиск.":"Найдено принтеров: "+found.size());}};
        dialog.setOnDismissListener(d->{stopDiscovery();main.removeCallbacks(timeout);queue.clear();});dialog.show();main.postDelayed(timeout,30000);
        for(String type:new String[]{"_pdl-datastream._tcp.","_ipp._tcp.","_ipps._tcp."}){
            NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
                @Override public void onDiscoveryStarted(String t){}
                @Override public void onDiscoveryStopped(String t){}
                @Override public void onStartDiscoveryFailed(String t,int e){}
                @Override public void onStopDiscoveryFailed(String t,int e){}
                @Override public void onServiceLost(NsdServiceInfo s){}
                @Override public void onServiceFound(NsdServiceInfo info){main.post(()->{if(dialog.isShowing()&&names.add(info.getServiceName()+info.getServiceType())){queue.add(info);next[0].run();}});}
            };
            discoveries.add(listener);try{nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);}catch(Exception ignored){}
        }
    }
    private void stopDiscovery(){NsdManager n=(NsdManager)getSystemService(NSD_SERVICE);for(NsdManager.DiscoveryListener l:discoveries)try{n.stopServiceDiscovery(l);}catch(Exception ignored){}discoveries.clear();}
    @Override protected void onDestroy(){alive=false;stopDiscovery();worker.shutdownNow();super.onDestroy();}
}
