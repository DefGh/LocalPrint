/*
 * SPL/QPDL and algorithm 0x11 port based on OpenPrinting SpliX:
 * Copyright (C) 2006-2008 Aurélien Croc (AP²C) and contributors.
 * LocalPrint Java adaptation, 2026. GPL-2.0-only; see COPYING and NOTICE.
 */
package org.localprint.core;

import java.io.*;
import java.util.*;

public final class QpdlDriver implements Driver {
    private static void be16(OutputStream o, int n) throws IOException { o.write(n >>> 8); o.write(n); }
    private static void be32(OutputStream o, long n) throws IOException {
        o.write((int)(n >>> 24)); o.write((int)(n >>> 16)); o.write((int)(n >>> 8)); o.write((int)n);
    }
    private static void le16(OutputStream o, int n) throws IOException { o.write(n); o.write(n >>> 8); }
    private static void le32(OutputStream o, int n) throws IOException {
        o.write(n); o.write(n >>> 8); o.write(n >>> 16); o.write(n >>> 24);
    }

    /** Algorithm 0x11: 64 back-reference offsets, 128-byte literal runs. */
    public static byte[] compress(byte[] data) throws IOException {
        if (data.length < 132) throw new IOException("SPL band is too small");
        int[] occurrences = new int[2048];
        for (int i = 2048; i < data.length; i += 2048)
            for (int j = 1; j < 2048; j++) if (data[i-j] == data[i]) occurrences[j-1]++;
        Integer[] order = new Integer[2048];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a,b) -> Integer.compare(occurrences[b], occurrences[a]));
        int[] offsets = new int[64];
        boolean one = false;
        int prefix = 0;
        for (int i = 0; i < 64; i++) { offsets[i] = order[i]+1; one |= offsets[i] == 1; }
        if (!one) offsets[63] = 1;
        for (int n : offsets) prefix = Math.max(prefix, n);
        prefix = Math.min(128, prefix);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4);
        le32(out, prefix);
        for (int n : offsets) le16(out, n);
        out.write(data, 0, prefix);
        int r = prefix;
        ByteArrayOutputStream literals = new ByteArrayOutputStream(128);
        while (r < data.length) {
            int best = 0, ptr = 0, limit = Math.min(514, data.length-r);
            for (int i = 0; i < 64; i++) {
                int off = offsets[i];
                if (off > r) continue;
                int count = 0;
                while (count < limit && data[r+count] == data[r-off+count]) count++;
                if (count > best) { best = count; ptr = i; }
                if (best == limit) break;
            }
            if (best >= 3) {
                flushLiterals(out, literals);
                int count = best-3;
                out.write(0x80 | (count & 127));
                out.write(((count >>> 1) & 0xc0) | ptr);
                r += best;
            } else {
                literals.write(data[r++]);
                if (literals.size() == 128) flushLiterals(out, literals);
            }
        }
        flushLiterals(out, literals);
        return out.toByteArray();
    }
    private static void flushLiterals(OutputStream out, ByteArrayOutputStream literals) throws IOException {
        if (literals.size() > 0) { out.write(literals.size()-1); literals.writeTo(out); literals.reset(); }
    }
    @Override public void begin(OutputStream out, PrintOptions o) throws IOException {
        o.check();
        Driver.ascii(out, "\033%-12345X@PJL SET USERNAME=\"LocalPrint\"\n"
                + "@PJL SET JOBNAME=\"LocalPrint\"\n@PJL SET JAMRECOVERY=OFF\n"
                + "@PJL SET DUPLEX=OFF\n@PJL SET PAPERTYPE=OFF\n@PJL SET ALTITUDE=LOW\n"
                + "@PJL SET DENSITY=3\n@PJL SET RET=NORMAL\n@PJL ENTER LANGUAGE = QPDL\n");
    }
    @Override public void page(OutputStream out, RasterPage page, PrintOptions o) throws IOException {
        Driver.checkCanceled();
        int paperCode = "Letter".equals(o.paper) ? 0 : "Legal".equals(o.paper) ? 1 : "A5".equals(o.paper) ? 16 : 2;
        out.write(0); out.write(o.dpi/100); be16(out,1); out.write(paperCode);
        be16(out,(int)Math.ceil(300.0*o.widthPoints/72));
        be16(out,(int)Math.ceil(300.0*o.heightPoints/72));
        out.write(1); out.write(o.header1); out.write(1); out.write(0);
        out.write(o.header2); out.write(o.qpdlVersion); out.write(o.header3); out.write(o.dpi/100);
        int bh = o.dpi == 300 ? 64 : 128;
        int cropX = (int)Math.ceil(o.marginXPoints * o.dpi/72.0 / 8.0);
        int cropY = (int)Math.ceil(o.marginYPoints * o.dpi/72.0);
        int bw = o.fixedBandWidth ? (o.dpi*4960/600/8) : page.stride();
        int bandNr = 0;
        for (int y = cropY; y < page.height(); y += bh, bandNr++) {
            Driver.checkCanceled();
            int rows = Math.min(bh,page.height()-y);
            byte[] raster = new byte[page.stride()*rows];
            page.readRows(y,rows,raster);
            byte[] band = new byte[bw*bh];
            boolean nonEmpty = false;
            for (int row = 0; row < rows; row++)
                for (int x = 0; x < Math.min(bw, page.stride()-cropX); x++) {
                    byte b = raster[row*page.stride()+x+cropX];
                    band[x*bh+row] = b;
                    nonEmpty |= b != 0;
                }
            if (!nonEmpty) continue;
            for (int i = 0; i < band.length; i++) band[i] = (byte)~band[i];
            byte[] compressed = compress(band);
            out.write(0x0c); out.write(bandNr); be16(out,bw*8); be16(out,bh);
            out.write(0x11); be32(out,compressed.length+8L);
            byte[] signature = {(byte)0xef,(byte)0xcd,(byte)0xab,9};
            out.write(signature); out.write(compressed);
            long sum = 0;
            for (byte b : signature) sum += b & 255;
            for (byte b : compressed) sum += b & 255;
            be32(out,sum);
        }
        out.write(1); be16(out,1);
    }
    @Override public void end(OutputStream out) throws IOException { Driver.ascii(out,"\t\033%-12345X"); }
}
