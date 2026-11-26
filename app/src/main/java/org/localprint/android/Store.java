package org.localprint.android;

import android.content.*;
import org.json.*;
import java.io.*;
import java.util.*;

public final class Store {
    public static final class Printer {
        public String id,name,host,profile,path,transport;
        public int port;
        public Printer(JSONObject j)throws JSONException{
            id=j.getString("id");name=j.getString("name");host=j.getString("host");profile=j.getString("profile");
            transport=j.optString("transport","raw");port=j.optInt("port",9100);path=j.optString("path","/ipp/print");
            if(!id.matches("[A-Za-z0-9_.-]{1,100}")||name.trim().isEmpty()||name.length()>150||host.isEmpty()||host.length()>253
                ||host.contains(":")||host.contains("/")||host.contains(" ")||port<1||port>65535
                ||!(transport.equals("raw")||transport.equals("ipp")||transport.equals("ipps"))
                ||!path.startsWith("/")||path.contains("\r")||path.contains("\n"))throw new JSONException("Проверь адрес, порт и имя принтера. IP вводится без :порта.");
        }
        public JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("host",host).put("profile",profile).put("port",port).put("transport",transport).put("path",path);}
    }
    private final Context context;
    private final android.content.SharedPreferences prefs;
    public Store(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("localprint",Context.MODE_PRIVATE);}
    public synchronized List<Printer> printers(){
        List<Printer> list=new ArrayList<>();
        try{JSONArray a=new JSONArray(prefs.getString("printers","[]"));for(int i=0;i<a.length();i++)list.add(new Printer(a.getJSONObject(i)));}catch(Exception ignored){}
        return list;
    }
    public synchronized void savePrinter(Printer printer)throws JSONException{
        List<Printer> list=printers();list.removeIf(p->p.id.equals(printer.id));list.add(printer);JSONArray a=new JSONArray();
        for(Printer p:list)a.put(p.json());if(!prefs.edit().putString("printers",a.toString()).commit())throw new JSONException("Не удалось сохранить принтер");
    }
    public synchronized void removePrinter(String id)throws JSONException{
        List<Printer> list=printers();JSONArray a=new JSONArray();for(Printer p:list)if(!p.id.equals(id))a.put(p.json());prefs.edit().putString("printers",a.toString()).apply();
    }
    public synchronized List<Profile> profiles(){
        LinkedHashMap<String,Profile> map=new LinkedHashMap<>();
        try{JSONArray a=new JSONArray(asset("profiles.json"));for(int i=0;i<a.length();i++){Profile p=new Profile(a.getJSONObject(i));map.put(p.id,p);}
            a=new JSONArray(prefs.getString("profiles","[]"));for(int i=0;i<a.length();i++){Profile p=new Profile(a.getJSONObject(i));map.put(p.id,p);}}
        catch(Exception e){throw new IllegalStateException("Не удалось прочитать профили",e);}
        return new ArrayList<>(map.values());
    }
    public Profile profile(String id){for(Profile p:profiles())if(p.id.equals(id))return p;throw new IllegalArgumentException("Профиль принтера не установлен");}
    public synchronized void installProfile(Profile p)throws JSONException{
        JSONArray a=new JSONArray(prefs.getString("profiles","[]")),out=new JSONArray();
        for(int i=0;i<a.length();i++)if(!a.getJSONObject(i).getString("id").equals(p.id))out.put(a.getJSONObject(i));
        out.put(p.json());if(!prefs.edit().putString("profiles",out.toString()).commit())throw new JSONException("Не удалось сохранить профиль");
    }
    public String asset(String path)throws IOException{try(InputStream in=context.getAssets().open(path)){return read(in,1024*1024);}}
    public static String read(InputStream in,int max)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Файл слишком большой");out.write(b,0,n);}
        return out.toString("UTF-8");
    }
    public void log(String message){prefs.edit().putString("lastJob",new java.text.SimpleDateFormat("dd.MM HH:mm",java.util.Locale.ROOT).format(new Date())+"\n"+message).apply();}
    public String lastJob(){return prefs.getString("lastJob","Заданий пока нет");}
    public synchronized void serviceEvent(String message){
        String entry=new java.text.SimpleDateFormat("dd.MM HH:mm:ss",java.util.Locale.ROOT).format(new Date())+" · "+message;
        String history=entry+"\n"+prefs.getString("serviceHistory","");
        if(history.length()>8000)history=history.substring(0,8000);
        prefs.edit().putString("serviceHistory",history).apply();
        android.util.Log.i("LocalPrint",message);
    }
    public String serviceHistory(){return prefs.getString("serviceHistory","Android ещё не запускал службу печати. Открой системный список принтеров, затем обнови этот журнал.");}
}
