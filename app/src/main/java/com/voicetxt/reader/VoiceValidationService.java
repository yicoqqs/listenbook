package com.voicetxt.reader;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import com.k2fsa.sherpa.onnx.*;
import java.io.File;

/** Native probing runs in a separate process so malformed third-party graphs cannot crash the reader. */
public class VoiceValidationService extends Service {
    HandlerThread thread;
    Messenger messenger;
    @Override public void onCreate(){super.onCreate();thread=new HandlerThread("voice-probe");thread.start();messenger=new Messenger(new Handler(thread.getLooper(),message->{Bundle result=new Bundle();OfflineTts engine=null;String stage="manifest";try{
        String id=message.getData().getString("id","");if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException();
        File root=new File(new File(getFilesDir(),"voice-imports"),id);VoiceModel pack=VoiceModel.read(root);String language=message.getData().getString("language","");if(!pack.supports(language))throw new IllegalArgumentException();
        stage="engine initialization";engine=pack.createEngine();int rate=engine.sampleRate(),nativeSpeakers=engine.numSpeakers();if(rate<8000||rate>96000||nativeSpeakers<0||nativeSpeakers>10000)throw new IllegalArgumentException();int speakers=Math.max(1,nativeSpeakers);
        stage="synthesis";String sample=VoiceLanguages.sample(language);sample=sample.substring(0,Math.min(sample.length(),language.startsWith("zh")?2:12));GeneratedAudio audio=engine.generate(sample,0,1f);float[] samples=audio.getSamples();if(samples.length<100||audio.getSampleRate()!=rate)throw new IllegalArgumentException("Invalid audio: samples="+samples.length+", rate="+audio.getSampleRate());boolean audible=false;for(float sampleValue:samples){if(Float.isNaN(sampleValue)||Float.isInfinite(sampleValue))throw new IllegalArgumentException("Non-finite audio sample");if(Math.abs(sampleValue)>.00001f)audible=true;}if(!audible)throw new IllegalArgumentException("Silent audio");result.putBoolean("ok",true);result.putInt("sampleRate",rate);result.putInt("speakers",speakers);
    }catch(Throwable error){result.putBoolean("ok",false);result.putString("detail",stage+": "+error.getClass().getSimpleName()+": "+error.getMessage());}finally{if(engine!=null)engine.release();}
        try{Message reply=Message.obtain();reply.setData(result);message.replyTo.send(reply);}catch(Exception ignored){}return true;}));}
    @Override public IBinder onBind(Intent intent){return messenger.getBinder();}
    @Override public void onDestroy(){if(thread!=null)thread.quitSafely();super.onDestroy();}
}
