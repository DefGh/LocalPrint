package org.localprint.core;

import java.io.IOException;

/** One-bit, MSB-first rows: a set bit means black. Padding bits must be white. */
public interface RasterPage {
    int width();
    int height();
    void readRows(int first, int count, byte[] target) throws IOException;
    default int stride() { return (width() + 7) / 8; }
}
