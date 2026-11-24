package org.localprint.core;

import java.io.*;

/** Level 2 PostScript, monochrome image and RunLengthDecode. */
public final class PostScriptDriver implements Driver {
    private int pages;
    @Override public void begin(OutputStream out,PrintOptions o) throws IOException {
        Driver.ascii(out,"%!PS-Adobe-3.0\n%%Creator: LocalPrint\n%%Pages: (atend)\n%%EndComments\n");
    }
    @Override public void page(OutputStream out,RasterPage page,PrintOptions o) throws IOException {
        pages++;
        Driver.ascii(out,"%%Page: "+pages+" "+pages+"\n<< /PageSize ["+o.widthPoints+" "+o.heightPoints+"] >> setpagedevice\n"
                +"gsave\n"+o.widthPoints+" "+o.heightPoints+" scale\n"
                +"<< /ImageType 1 /Width "+page.width()+" /Height "+page.height()+" /BitsPerComponent 1\n"
                +"/ImageMatrix ["+page.width()+" 0 0 -"+page.height()+" 0 "+page.height()+"] /Decode [0 1]\n"
                +"/DataSource currentfile /ASCIIHexDecode filter /RunLengthDecode filter >> image\n");
        byte[] row=new byte[page.stride()];
        for(int y=0;y<page.height();y++) {
            Driver.checkCanceled();page.readRows(y,1,row);
            for(int x=0;x<row.length;x++)row[x]=(byte)~row[x];
            byte[] packed=Pcl5Driver.packBits(row);hex(out,packed);
        }
        Driver.ascii(out,"80>\ngrestore\nshowpage\n");
    }
    private static void hex(OutputStream out,byte[] data) throws IOException {
        byte[] hex=new byte[data.length*2+1];final byte[] chars="0123456789ABCDEF".getBytes("US-ASCII");
        for(int i=0;i<data.length;i++){hex[2*i]=chars[(data[i]&255)>>>4];hex[2*i+1]=chars[data[i]&15];}
        hex[hex.length-1]=10;out.write(hex);
    }
    @Override public void end(OutputStream out) throws IOException { Driver.ascii(out,"%%Trailer\n%%Pages: "+pages+"\n%%EOF\n"); }
}
