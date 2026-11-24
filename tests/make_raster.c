#include <cups/raster.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
int main(int argc,char **argv){
    if(argc!=3)return 2;
    FILE *in=fopen(argv[1],"rb");int fd=open(argv[2],O_CREAT|O_WRONLY|O_TRUNC,0600);
    if(!in||fd<0)return 3;
    cups_raster_t *r=cupsRasterOpen(fd,CUPS_RASTER_WRITE);
    cups_page_header2_t h;memset(&h,0,sizeof(h));h.HWResolution[0]=h.HWResolution[1]=600;
    h.PageSize[0]=595;h.PageSize[1]=842;h.cupsWidth=4960;h.cupsHeight=7017;
    h.cupsBitsPerColor=h.cupsBitsPerPixel=1;h.cupsBytesPerLine=620;h.cupsColorSpace=CUPS_CSPACE_K;
    h.cupsCompression=17;h.NumCopies=1;h.cupsNumColors=1;
    if(!cupsRasterWriteHeader2(r,&h))return 4;
    unsigned char row[620];for(int y=0;y<7017;y++){if(fread(row,1,620,in)!=620)return 5;if(cupsRasterWritePixels(r,row,620)!=620)return 6;}
    cupsRasterClose(r);close(fd);fclose(in);return 0;
}
