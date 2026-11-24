package org.localprint.core;

public final class PrintOptions {
    public int dpi = 600;
    public int widthPoints = 595;
    public int heightPoints = 842;
    public String paper = "A4";
    public String title = "LocalPrint";
    public int qpdlVersion = 3;
    public int bandHeight = 128;
    public double marginXPoints = 10.75;
    public double marginYPoints = 15;
    public int header1 = 0, header2 = 0, header3 = 1;
    public boolean fixedBandWidth = false;
    public void check() {
        if ((dpi != 300 && dpi != 600) || widthPoints < 100 || widthPoints > 1300
                || heightPoints < 100 || heightPoints > 1800)
            throw new IllegalArgumentException("Unsupported page geometry");
        if (qpdlVersion < 1 || qpdlVersion > 3 || bandHeight != 128)
            throw new IllegalArgumentException("Unsupported SPL variant");
        if (!Double.isFinite(marginXPoints) || !Double.isFinite(marginYPoints)
                || marginXPoints < 0 || marginXPoints > 36 || marginYPoints < 0 || marginYPoints > 36)
            throw new IllegalArgumentException("Unsupported hardware margins");
    }
}
