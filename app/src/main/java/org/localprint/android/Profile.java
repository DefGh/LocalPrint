package org.localprint.android;

import org.json.*;
import org.localprint.core.PrintOptions;
import java.util.regex.*;

/** Data only. A profile cannot execute commands, load code or choose arbitrary filters. */
public final class Profile {
    public final String id, name, engine, source;
    public final int qpdl, header1, header2, header3;
    public final boolean fixedWidth;
    public final double marginX,marginY;
    public Profile(JSONObject j) throws JSONException {
        id=j.getString("id");name=j.getString("name");engine=j.getString("engine");
        source=j.optString("source","Встроенный");
        qpdl=j.optInt("qpdl",3);header1=j.optInt("header1",0);header2=j.optInt("header2",0);header3=j.optInt("header3",1);
        fixedWidth=j.optBoolean("fixedWidth",false);
        marginX=j.optDouble("marginX",10.75);marginY=j.optDouble("marginY",15);
        if(!id.matches("[A-Za-z0-9_.-]{1,100}") || name.length()>150
            || !(engine.equals("spl")||engine.equals("pcl5")||engine.equals("ps")||engine.equals("pdf")))
            throw new JSONException("Профиль содержит неподдерживаемый движок или имя");
        if(qpdl<1||qpdl>3||header1<0||header1>255||header2<0||header2>255||header3<0||header3>255)
            throw new JSONException("Этот вариант SPL пока не поддерживается");
        if(!Double.isFinite(marginX)||!Double.isFinite(marginY)||marginX<0||marginX>36||marginY<0||marginY>36)
            throw new JSONException("Некорректные поля профиля");
    }
    public JSONObject json() throws JSONException {
        return new JSONObject().put("id",id).put("name",name).put("engine",engine).put("source",source)
            .put("qpdl",qpdl).put("header1",header1).put("header2",header2).put("header3",header3).put("fixedWidth",fixedWidth).put("marginX",marginX).put("marginY",marginY);
    }
    public PrintOptions options() {
        PrintOptions o=new PrintOptions();o.qpdlVersion=qpdl;o.header1=header1;o.header2=header2;o.header3=header3;o.fixedBandWidth=fixedWidth;
        o.marginXPoints=marginX;o.marginYPoints=marginY;
        o.dpi=engine.equals("spl")?600:300;return o;
    }
    public static Profile fromPpd(String ppd,String source) throws JSONException {
        if(!"False".equals(value(ppd,"ColorDevice")) || !ppd.contains("/cupsCompression 17")
            || ppd.contains("*QPDL SpecialBandWidth") || !"128".equals(value(ppd,"QPDL BandSize")))
            throw new JSONException("PPD требует цветной, JBIG или другой движок. Поддерживается монохромный SpliX 0x11, полоса 128.");
        String model=value(ppd,"ModelName"),version=value(ppd,"QPDL QPDLVersion"),header=value(ppd,"General DocHeaderValues");
        if(model.isEmpty()||!version.matches("[123]"))throw new JSONException("Неподдерживаемый PPD");
        Matcher m=Pattern.compile("<([0-9A-Fa-f]{1,2})>").matcher(header);int[] bytes=new int[3];int i=0;
        while(m.find()&&i<3)bytes[i++]=Integer.parseInt(m.group(1),16);
        if(i!=3)throw new JSONException("Некорректный заголовок PPD");
        Matcher area=Pattern.compile("(?m)^\\*ImageableArea A4(?:/[^:]*)?:\\s*\"([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\\s+([0-9.]+)\"").matcher(ppd);
        if(!area.find())throw new JSONException("В PPD нет поддерживаемой геометрии A4");
        return new Profile(new JSONObject().put("id","spl-"+model.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+","-"))
            .put("name",model).put("engine","spl").put("source",source).put("qpdl",Integer.parseInt(version))
            .put("header1",bytes[0]).put("header2",bytes[1]).put("header3",bytes[2])
            .put("marginX",Double.parseDouble(area.group(1))).put("marginY",Double.parseDouble(area.group(2)))
            .put("fixedWidth",!value(ppd,"QPDL FixedBandWidth").isEmpty()&&!value(ppd,"QPDL FixedBandWidth").equals("0")));
    }
    private static String value(String ppd,String name) {
        Matcher m=Pattern.compile("(?m)^\\*"+Pattern.quote(name)+":\\s*\"?([^\"\\r\\n]*)").matcher(ppd);
        return m.find()?m.group(1).trim():"";
    }
    @Override public String toString(){return name+" · "+engine.toUpperCase(java.util.Locale.ROOT);}
}
