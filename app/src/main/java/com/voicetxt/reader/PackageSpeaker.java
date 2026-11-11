package com.voicetxt.reader;

import android.content.Context;
import android.os.SystemClock;
import java.util.ArrayList;

/** Uses the existing bounded audio queue with a user-installed model, never bundled assets. */
final class PackageSpeaker extends OfflineSpeaker {
    static final Object INITIALIZATION_LOCK=new Object();
    final VoicePackages packages;final VoicePackages.Selection selection;final String language,key;
    volatile boolean ready,loading;volatile long loadMillis;volatile int loadCount;boolean leased;
    Throwable loadError;final ArrayList<Init> initializationCallbacks=new ArrayList<>();
    PackageSpeaker(Context context,VoicePackages.Selection selection,String language){super(context);packages=VoicePackages.get(context);this.selection=selection;this.language=language;key=selection.pack.key(selection.speaker);}
    @Override synchronized void init(Init callback){
        if(closed)return;
        if(ready||loadError!=null){main.post(()->{if(!closed)callback.done(loadError);});return;}
        initializationCallbacks.add(callback);if(loading)return;loading=true;
        worker.execute(()->{long began=SystemClock.elapsedRealtime();Throwable failure=null;
            try{synchronized(INITIALIZATION_LOCK){if(closed)return;loadCount++;packages.acquire(selection.pack.id);leased=true;engine=selection.pack.createEngine();if(engine.sampleRate()!=selection.pack.sampleRate||selection.speaker>=Math.max(1,engine.numSpeakers()))throw new IllegalArgumentException("invalid");}}
            catch(Throwable error){failure=error;}
            loadMillis=SystemClock.elapsedRealtime()-began;final Throwable result=failure;final ArrayList<Init> callbacks;
            synchronized(this){loading=false;loadError=failure;ready=failure==null;callbacks=new ArrayList<>(initializationCallbacks);initializationCallbacks.clear();}
            main.post(()->{if(!closed)for(Init waiting:callbacks)waiting.done(result);});
        });
    }
    @Override com.k2fsa.sherpa.onnx.OfflineTts engineFor(int voice){return engine;}
    @Override int speakerFor(int voice){return selection.speaker;}
    @Override synchronized void stop(){cancelPlayback();prefetchKey="";prefetched=null;if(!loading)worker.getQueue().clear();}
    @Override synchronized void close(){if(closed)return;stop();closed=true;initializationCallbacks.clear();worker.execute(()->{try{if(engine!=null){engine.release();engine=null;}}finally{if(leased){packages.release(selection.pack.id);leased=false;}}});worker.shutdown();playback.shutdown();}
}
