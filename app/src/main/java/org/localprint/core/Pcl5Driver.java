package org.localprint.core;

import java.io.*;

/** Monochrome PCL 5 raster, TIFF/PackBits compression (mode 2). */
public final class Pcl5Driver implements Driver {
    private static void esc(OutputStream o,String s) throws IOException { Driver.ascii(o,"\033"+s); }
    @Override public void begin(OutputStream out,PrintOptions o) throws IOException {
        esc(out,"E"); esc(out,"&l0O");
        esc(out,"&l"+("Letter".equals(o.paper)?2:"Legal".equals(o.paper)?3:"A5".equals(o.paper)?25:26)+"A");
        esc(out,"&l0E"); esc(out,"&l0L"); esc(out,"*t"+o.dpi+"R");
    }
    public static byte[] packBits(byte[] row) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < row.length) {
            int run=1;
            while(i+run<row.length && run<128 && row[i+run]==row[i]) run++;
            if(run>=3) { out.write(257-run);out.write(row[i]);i+=run;continue; }
            int start=i;
            i+=run;
            while(i<row.length && i-start<128) {
                run=1;
                while(i+run<row.length && run<3 && row[i+run]==row[i])run++;
                if(run>=3)break;
                i+=Math.min(run,128-(i-start));
            }
            out.write(i-start-1);out.write(row,start,i-start);
        }
        return out.toByteArray();
    }
    @Override public void page(OutputStream out,RasterPage page,PrintOptions o) throws IOException {
        esc(out,"*p0x0Y");esc(out,"*r"+page.width()+"S");esc(out,"*r"+page.height()+"T");
        esc(out,"*r1A");esc(out,"*b2M");
        byte[] row=new byte[page.stride()];
        for(int y=0;y<page.height();y++) {
            Driver.checkCanceled();page.readRows(y,1,row);
            byte[] packed=packBits(row);esc(out,"*b"+packed.length+"W");out.write(packed);
        }
        esc(out,"*rB");out.write(12);
    }
    @Override public void end(OutputStream out) throws IOException { esc(out,"E"); }
}
