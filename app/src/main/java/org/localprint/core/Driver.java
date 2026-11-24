package org.localprint.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public interface Driver {
    void begin(OutputStream out, PrintOptions options) throws IOException;
    void page(OutputStream out, RasterPage page, PrintOptions options) throws IOException;
    void end(OutputStream out) throws IOException;
    static void ascii(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.US_ASCII));
    }
    static void checkCanceled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Печать отменена");
    }
}
