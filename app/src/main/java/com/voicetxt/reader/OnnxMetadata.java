package com.voicetxt.reader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads only ONNX ModelProto metadata_props, skipping tensor payloads without allocating them. */
final class OnnxMetadata {
    static long var(DataInput input)throws IOException{long value=0;for(int shift=0;shift<64;shift+=7){int b=input.readUnsignedByte();value|=(long)(b&127)<<shift;if((b&128)==0)return value;}throw new IOException("Invalid protobuf integer");}
    static Map<String,String> read(File file)throws IOException{
        Map<String,String> metadata=new LinkedHashMap<>();
        try(RandomAccessFile input=new RandomAccessFile(file,"r")){
            int fields=0;
            while(input.getFilePointer()<input.length()){
                if(++fields>100000)throw new IOException("Too many ONNX fields");
                long tag=var(input);int wire=(int)(tag&7);
                if(wire==0)var(input);
                else if(wire==1)skip(input,8);
                else if(wire==5)skip(input,4);
                else if(wire==2){long size=var(input);if(size<0||size>input.length()-input.getFilePointer())throw new IOException("Truncated ONNX");if(tag>>>3==14){if(size>1024*1024||metadata.size()>1024)throw new IOException("Invalid ONNX metadata");byte[] bytes=new byte[(int)size];input.readFully(bytes);entry(bytes,metadata);}else skip(input,size);}
                else throw new IOException("Unsupported protobuf wire type");
            }
        }
        if(metadata.isEmpty())throw new IOException("No ONNX metadata");return metadata;
    }
    static void skip(RandomAccessFile input,long size)throws IOException{long end=input.getFilePointer()+size;if(size<0||end>input.length())throw new IOException("Truncated ONNX");input.seek(end);}
    static void entry(byte[] bytes,Map<String,String> result)throws IOException{
        String key=null,value=null;DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));
        while(input.available()>0){long tag=var(input);if((tag&7)!=2)throw new IOException("Invalid ONNX metadata entry");long size=var(input);if(size<0||size>input.available())throw new IOException("Truncated metadata");byte[] text=new byte[(int)size];input.readFully(text);if(tag>>>3==1)key=new String(text,StandardCharsets.UTF_8);else if(tag>>>3==2)value=new String(text,StandardCharsets.UTF_8);}
        if(key!=null&&value!=null)result.put(key,value);
    }
}
