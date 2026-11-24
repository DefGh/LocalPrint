import org.localprint.core.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class CoreProbe {
    public static void main(String[] args)throws Exception{
        Path output=Paths.get(args[0]);Files.createDirectories(output);
        PrintOptions options=new PrintOptions();
        byte[] bitmap=Files.readAllBytes(output.resolve("page.bin"));
        RasterPage raster=new RasterPage(){public int width(){return 4960;}public int height(){return 7017;}
            public void readRows(int first,int count,byte[] target){System.arraycopy(bitmap,first*620,target,0,count*620);}};
        for(Driver driver:new Driver[]{new QpdlDriver(),new Pcl5Driver(),new PostScriptDriver(),new PdfDriver()}){
            String extension=driver instanceof QpdlDriver?"qpdl":driver instanceof Pcl5Driver?"pcl":driver instanceof PdfDriver?"pdf":"ps";
            try(OutputStream out=Files.newOutputStream(output.resolve("java."+extension))){driver.begin(out,options);driver.page(out,raster,options);driver.end(out);}
        }
        for(String model:new String[]{"ml1510","ml1630","ml1915"}){
            PrintOptions o=new PrintOptions();
            if(model.equals("ml1510")){o.qpdlVersion=1;o.header3=0;}
            if(model.equals("ml1630"))o.qpdlVersion=2;
            if(model.equals("ml1915")){o.marginXPoints=12.5;o.marginYPoints=12.5;}
            try(OutputStream out=Files.newOutputStream(output.resolve("java-"+model+".qpdl"))){Driver d=new QpdlDriver();d.begin(out,o);d.page(out,raster,o);d.end(out);}
        }
        Random random=new Random(721004);
        for(int i=0;i<12;i++){
            byte[] band=new byte[620*128];if(i<4)Arrays.fill(band,(byte)(i*83));else random.nextBytes(band);
            if(i==4)for(int n=0;n<band.length;n++)band[n]=(byte)(n/128);
            Files.write(output.resolve("band-"+i+".raw"),band);
            Files.write(output.resolve("band-"+i+".compressed"),QpdlDriver.compress(band));
        }
        for(int len:new int[]{0,1,2,3,127,128,129,620,1024}){
            byte[] row=new byte[len];random.nextBytes(row);Files.write(output.resolve("row-"+len+".raw"),row);
            Files.write(output.resolve("row-"+len+".packed"),Pcl5Driver.packBits(row));
        }
        byte[] envelope=IppProtocol.printJob("ipp://printer.local:631/ipp/print","application/pdf","iso_a4_210x297mm");
        Files.write(output.resolve("ipp.bin"),envelope);
        for(int status:new int[]{0,1,0x0400,0x0500}){
            byte[] response={2,0,(byte)(status>>>8),(byte)status,0,0,0,1};boolean rejected=false;
            try{IppProtocol.readStatus(new ByteArrayInputStream(response));}catch(IOException e){rejected=true;}
            if(rejected!=(status>7))throw new AssertionError("IPP result handling");
        }
        Thread.currentThread().interrupt();
        try{Driver.checkCanceled();throw new AssertionError("Cancellation ignored");}catch(IOException expected){}finally{Thread.interrupted();}
        System.out.println("Core encoder fixtures generated; cancellation and IPP statuses passed");
    }
}
