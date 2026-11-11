package com.voicetxt.reader;

import android.content.Context;
import java.io.File;
import java.io.IOException;

/** Removes only the two model directories created by previous app versions. */
final class BundledVoiceCleanup {
    static void start(Context context){
        Context app=context.getApplicationContext();
        new Thread(()->{
            for(String name:new String[]{"icefall-aishell3-v1","matcha-baker-v1"}){
                try{
                    File root=new File(app.getFilesDir(),name);
                    remove(root,root.getCanonicalPath());
                }catch(IOException error){android.util.Log.w("VoiceCleanup","Old voice could not be removed",error);}
            }
        },"old-voice-cleanup").start();
    }
    static void remove(File file,String root)throws IOException{
        String path=file.getCanonicalPath();
        if(!path.equals(root)&&!path.startsWith(root+File.separator))return;
        if(!file.exists())return;
        File[] children=file.listFiles();
        if(children!=null)for(File child:children)remove(child,root);
        if(!file.delete())throw new IOException("Cannot remove "+file.getName());
    }
}
