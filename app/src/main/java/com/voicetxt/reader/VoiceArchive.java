package com.voicetxt.reader;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/** Bounded extraction of data files. Links and paths outside the staging directory are rejected. */
final class VoiceArchive {
    static final long MAX_ARCHIVE=1024L*1024*1024,MAX_EXPANDED=2L*1024*1024*1024;
    interface Cancel {void check()throws IOException;}
    final File root;final Cancel cancel;long expanded;int count;final Set<String> paths=new HashSet<>();
    VoiceArchive(File root,Cancel cancel){this.root=root;this.cancel=cancel;}
    void extract(File archive)throws IOException{
        try(BufferedInputStream input=new BufferedInputStream(new FileInputStream(archive))){
            input.mark(8);byte[] magic=new byte[4];if(input.read(magic)!=4)throw new IOException("archive");input.reset();
            if(magic[0]=='P'&&magic[1]=='K'){
                try(ZipInputStream zip=new ZipInputStream(input)){ZipEntry entry;while((entry=zip.getNextEntry())!=null){file(entry.getName(),entry.isDirectory(),entry.getSize(),zip);zip.closeEntry();}}
            }else{
                InputStream stream=input;
                if(magic[0]=='B'&&magic[1]=='Z'&&magic[2]=='h')stream=new BZip2CompressorInputStream(input,true);
                else if((magic[0]&255)==31&&(magic[1]&255)==139)stream=new GZIPInputStream(input);
                try(TarArchiveInputStream tar=new TarArchiveInputStream(stream)){TarArchiveEntry entry;while((entry=tar.getNextTarEntry())!=null){if(!entry.isCheckSumOK()||entry.isSymbolicLink()||entry.isLink()||(!entry.isFile()&&!entry.isDirectory())||!tar.canReadEntryData(entry))throw new IOException("archive");file(entry.getName(),entry.isDirectory(),entry.getSize(),tar);}}
            }
        }
        if(count==0||expanded==0)throw new IOException("archive");
    }
    void file(String name,boolean directory,long size,InputStream input)throws IOException{
        cancel.check();if(++count>20000||name.length()>1024||name.indexOf('\0')>=0||name.contains("\\")||name.startsWith("/")||name.matches("^[A-Za-z]:.*"))throw new IOException("archive");
        String[] parts=name.split("/");if(parts.length>40)throw new IOException("archive");for(String part:parts)if(part.equals(".."))throw new IOException("archive");
        File destination=new File(root,name);String canonical=destination.getCanonicalPath(),base=root.getCanonicalPath();if(directory&&canonical.equals(base))return;if(!canonical.startsWith(base+File.separator))throw new IOException("archive");
        if(directory){if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException("storage");return;}
        if(!paths.add(canonical)||size>MAX_EXPANDED)throw new IOException("archive");File parent=destination.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("storage");
        try(FileOutputStream output=new FileOutputStream(destination)){byte[] buffer=new byte[65536];long written=0;int n;while((n=input.read(buffer))!=-1){cancel.check();written+=n;expanded+=n;if(expanded>MAX_EXPANDED||(size>=0&&written>size))throw new IOException("archive");output.write(buffer,0,n);}if(size>=0&&written!=size)throw new IOException("archive");}
    }
}
