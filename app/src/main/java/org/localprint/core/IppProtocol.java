package org.localprint.core;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Minimal IPP Print-Job envelope. Rendering is complete before transmission. */
public final class IppProtocol {
    private static void value(DataOutputStream out,int tag,String name,byte[] value)throws IOException{
        byte[] n=name.getBytes(StandardCharsets.US_ASCII);
        out.writeByte(tag);out.writeShort(n.length);out.write(n);out.writeShort(value.length);out.write(value);
    }
    private static void text(DataOutputStream out,int tag,String name,String value)throws IOException{
        value(out,tag,name,value.getBytes(StandardCharsets.UTF_8));
    }
    public static byte[] printJob(String uri,String format,String media)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(0x0101);out.writeShort(0x0002);out.writeInt(1);out.writeByte(1);
        text(out,0x47,"attributes-charset","utf-8");text(out,0x48,"attributes-natural-language","en");
        text(out,0x45,"printer-uri",uri);text(out,0x42,"requesting-user-name","LocalPrint");
        text(out,0x42,"job-name","LocalPrint");text(out,0x49,"document-format",format);
        out.writeByte(2);text(out,0x44,"media",media);text(out,0x44,"sides","one-sided");
        text(out,0x44,"print-color-mode","monochrome");out.writeByte(3);return bytes.toByteArray();
    }
    public static int readStatus(InputStream in)throws IOException{
        DataInputStream d=new DataInputStream(in);int version=d.readUnsignedShort();
        if(version<0x0100||version>0x0202)throw new IOException("Ответ не является IPP");
        int status=d.readUnsignedShort();int request=d.readInt();
        if(request!=1)throw new IOException("Неожиданный идентификатор IPP");
        if(status>0x0007)throw new IOException(String.format(java.util.Locale.ROOT,"Принтер отклонил задание: IPP 0x%04X",status));
        return status;
    }
}
