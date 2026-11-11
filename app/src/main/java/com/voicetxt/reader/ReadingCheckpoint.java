package com.voicetxt.reader;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Atomic lifecycle checkpoint supplements the periodic preferences bookmark. */
final class ReadingCheckpoint {
    static void write(File file,String book,String text,int position,long version){AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;try{JSONObject value=new JSONObject().put("book",book).put("length",text.length()).put("position",position).put("version",version);out=atomic.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);android.util.Log.w("ReadingCheckpoint","Checkpoint write failed",e);}}
    static int restore(File file,String book,String text,int fallback,long version){int at=fallback;try{JSONObject value=new JSONObject(new String(new AtomicFile(file).readFully(),StandardCharsets.UTF_8));if(book.equals(value.getString("book"))&&value.getInt("length")==text.length()&&value.getLong("version")>=version)at=value.getInt("position");}catch(Exception ignored){}return Math.max(0,Math.min(at,Math.max(0,text.length()-1)));}
}
