package org.localprint.android;

import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import org.localprint.core.RasterPage;
import java.io.IOException;

/** Small cached strips avoid allocating a 140 MB A4 bitmap at 600 dpi. */
final class PdfRaster implements RasterPage,AutoCloseable {
    private final PdfRenderer.Page page;
    private final int width,height;
    private byte[] cached;
    private int cacheFirst=-1,cacheRows;
    private static final int[] BAYER={0,48,12,60,3,51,15,63,32,16,44,28,35,19,47,31,8,56,4,52,11,59,7,55,40,24,36,20,43,27,39,23,2,50,14,62,1,49,13,61,34,18,46,30,33,17,45,29,10,58,6,54,9,57,5,53,42,26,38,22,41,25,37,21};
    PdfRaster(PdfRenderer.Page p,int width,int height){this.page=p;this.width=width;this.height=height;}
    @Override public int width(){return width;}
    @Override public int height(){return height;}
    @Override public void readRows(int first,int count,byte[] target)throws IOException{
        if(first<0||first+count>height||target.length<stride()*count)throw new IOException("Некорректная полоса страницы");
        for(int row=0;row<count;){
            int y=first+row;
            if(cacheFirst<0||y<cacheFirst||y>=cacheFirst+cacheRows)renderStrip(y);
            int n=Math.min(count-row,cacheFirst+cacheRows-y);
            System.arraycopy(cached,(y-cacheFirst)*stride(),target,row*stride(),n*stride());row+=n;
        }
    }
    private void renderStrip(int y){
        cacheFirst=y;cacheRows=Math.min(128,height-y);cached=new byte[stride()*cacheRows];
        Bitmap bmp=Bitmap.createBitmap(width,cacheRows,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);
        try{
            float sx=(float)width/page.getWidth(),sy=(float)height/page.getHeight();
            float scale=Math.min(sx,sy);
            float left=(width-page.getWidth()*scale)/2f,top=(height-page.getHeight()*scale)/2f;
            Matrix m=new Matrix();
            if(page.getWidth()>page.getHeight()&&width<height){
                scale=Math.min((float)width/page.getHeight(),(float)height/page.getWidth());
                left=(width-page.getHeight()*scale)/2f;top=(height-page.getWidth()*scale)/2f;
                m.setValues(new float[]{0,-scale,page.getHeight()*scale+left,scale,0,top-y,0,0,1});
            }else{m.setScale(scale,scale);m.postTranslate(left,top-y);}
            page.render(bmp,null,m,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            int[] pixels=new int[width];
            for(int r=0;r<cacheRows;r++){
                bmp.getPixels(pixels,0,width,0,r,width,1);
                for(int x=0;x<width;x++){
                    int c=pixels[x],l=(54*Color.red(c)+183*Color.green(c)+19*Color.blue(c))>>>8;
                    int threshold=2+4*BAYER[((r+y)&7)*8+(x&7)];
                    if(l<threshold)cached[r*stride()+x/8]|=(byte)(0x80>>>(x&7));
                }
            }
        }finally{bmp.recycle();}
    }
    @Override public void close(){cached=null;page.close();}
}
