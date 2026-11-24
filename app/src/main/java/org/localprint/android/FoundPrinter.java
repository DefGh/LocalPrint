package org.localprint.android;

import android.net.nsd.NsdServiceInfo;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A discovery result is temporary; only an explicit Save writes a printer to Store. */
final class FoundPrinter {
    final String name,host,transport,path,profile;
    final int port;
    FoundPrinter(String name,String host,int port,String transport,String path,String profile){
        this.name=name;this.host=host;this.port=port;this.transport=transport;this.path=path;this.profile=profile;
    }
    String key(){return transport+"://"+host+":"+port+path;}
    @Override public String toString(){return name+"\n"+host+":"+port+" · "+transport.toUpperCase(Locale.ROOT)+(profile==null?" · выбери драйвер":" · профиль найден");}
    static FoundPrinter fromNsd(NsdServiceInfo info,List<Profile> profiles)throws Exception{
        String host=null;
        if(android.os.Build.VERSION.SDK_INT>=34){
            try{
                Object addresses=NsdServiceInfo.class.getMethod("getHostAddresses").invoke(info);
                for(Object a:(Iterable<?>)addresses)if(a instanceof Inet4Address){host=((InetAddress)a).getHostAddress();break;}
            }catch(Exception ignored){}
        }
        if(host==null&&info.getHost() instanceof Inet4Address)host=info.getHost().getHostAddress();
        if(host==null)throw new java.io.IOException("Нет IPv4-адреса");
        String name=clean(info.getServiceName()),details=name;
        for(String key:new String[]{"ty","usb_MFG","usb_MDL","product"})details+=" "+attr(info,key);
        String mode=info.getServiceType().contains("_ipps")?"ipps":info.getServiceType().contains("_ipp")?"ipp":"raw";
        String path=attr(info,"rp");if(path.isEmpty())path="/ipp/print";if(!path.startsWith("/"))path="/"+path;
        String pdl=attr(info,"pdl").toLowerCase(Locale.ROOT),selected=null;
        if(mode.equals("raw")){
            String normalized=details.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");int score=0;
            for(Profile p:profiles){
                if(!p.engine.equals("spl"))continue;
                String model=p.name.substring(p.name.indexOf(' ')+1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                if(model.length()>=5&&model.length()>score&&normalized.contains(model)){selected=p.id;score=model.length();}
            }
            if(selected==null){
                if(pdl.contains("application/pdf"))selected="generic-pdf";
                else if(pdl.contains("application/postscript"))selected="generic-ps";
                else if(pdl.contains("application/vnd.hp-pcl")&&!pdl.contains("application/vnd.hp-pclxl"))selected="generic-pcl5";
            }
        }else if(pdl.contains("application/pdf"))selected="generic-pdf";
        return new FoundPrinter(name,host,info.getPort(),mode,path,selected);
    }
    private static String attr(NsdServiceInfo info,String key){byte[] b=info.getAttributes().get(key);return b==null?"":clean(new String(b,StandardCharsets.UTF_8));}
    private static String clean(String s){if(s==null)return "";s=s.replaceAll("[\\p{Cntrl}]"," ").trim();return s.length()>150?s.substring(0,150):s;}
}
