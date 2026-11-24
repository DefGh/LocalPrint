package org.localprint.android;

import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;

/** Only pinned data files from OpenPrinting; never downloads executable driver code. */
final class DriverDownloads {
    static Profile download(JSONObject entry)throws Exception{
        String url=entry.getString("url"),expected=entry.getString("sha256");
        URI uri=new URI(url);
        if(!"https".equals(uri.getScheme())||!"raw.githubusercontent.com".equals(uri.getHost())
            ||!uri.getPath().matches("/OpenPrinting/splix/[0-9a-f]{40}/ppd/[a-z0-9]+\\.ppd")||!expected.matches("[0-9a-f]{64}"))
            throw new IOException("Источник профиля не поддерживается");
        HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
        try{
            if(c.getResponseCode()!=200)throw new IOException("Источник ответил HTTP "+c.getResponseCode());
            byte[] data;
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>256*1024)throw new IOException("Профиль слишком большой");out.write(b,0,n);}data=out.toByteArray();
            }
            StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!expected.equals(hash.toString()))throw new IOException("Контрольная сумма профиля не совпала");
            return Profile.fromPpd(new String(data,java.nio.charset.StandardCharsets.UTF_8),"OpenPrinting / SpliX");
        }finally{c.disconnect();}
    }
}
