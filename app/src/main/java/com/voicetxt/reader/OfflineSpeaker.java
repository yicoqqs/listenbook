package com.voicetxt.reader;

import android.content.Context;
import android.media.*;
import android.os.*;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.concurrent.*;

/** Serializes native inference and audio ownership; all UI callbacks run on the main thread. */
class OfflineSpeaker {
    public static final class CancelCallback implements kotlin.jvm.functions.Function1<float[],Integer> {
        final OfflineSpeaker owner; final int request; final String cache; CancelCallback(OfflineSpeaker o,int r){owner=o;request=r;cache=null;} CancelCallback(OfflineSpeaker o,String key){owner=o;request=0;cache=key;}
        @Override public Integer invoke(float[] samples){return (cache==null?owner.current(request):!owner.closed&&cache.equals(owner.prefetchKey))?1:0;}
    }
    interface Init { void done(Throwable error); }
    interface Listener { void started(); void done(); void failed(Throwable error); }
    final Context context;
    final Handler main=new Handler(Looper.getMainLooper());
    final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    final ExecutorService playback=Executors.newSingleThreadExecutor();
    volatile String prefetchKey="";
    volatile GeneratedAudio prefetched;
    volatile long lastSynthesisMs=0;
    volatile boolean lastCacheHit=false;
    volatile int epoch=0;
    volatile boolean closed=false;
    volatile boolean detailedUpdates=true;
    volatile AudioTrack track;
    volatile DlnaCast cast;
    OfflineTts engine,flowEngine;
    GeneratedAudio generate(String text,int voice,float speed,CancelCallback callback){try(CpuWork lease=new CpuWork(context)){return engineFor(voice).generateWithCallback(text,speakerFor(voice),speed,callback);}}
    OfflineTts engineFor(int speaker){return speaker==1000?flowEngine:engine;}
    int speakerFor(int speaker){return speaker==1000?0:speaker-2000;}
    OfflineSpeaker(Context c){context=c.getApplicationContext();}
    void init(Init callback){worker.execute(()->{Throwable error=null;try{
        File base=new File(context.getFilesDir(),"icefall-aishell3-v1");File marker=new File(base,"complete-v1");if(!marker.exists()){copy("fastmale",base);try(FileOutputStream out=new FileOutputStream(marker)){out.write(1);}}
        String dir=base.getAbsolutePath()+"/";OfflineTtsVitsModelConfig male=new OfflineTtsVitsModelConfig();male.setModel(dir+"model.onnx");male.setLexicon(dir+"lexicon.txt");male.setTokens(dir+"tokens.txt");male.setNoiseScale(.5f);male.setNoiseScaleW(.6f);
        OfflineTtsModelConfig model=new OfflineTtsModelConfig();model.setVits(male);model.setNumThreads(3);OfflineTtsConfig config=new OfflineTtsConfig();config.setModel(model);config.setMaxNumSentences(1);config.setRuleFsts(dir+"date.fst,"+dir+"number.fst,"+dir+"phone.fst");engine=new OfflineTts(null,config);if(engine.numSpeakers()<22)throw new IOException(context.getString(R.string.ui_129));
        File flowBase=new File(context.getFilesDir(),"matcha-baker-v1");File flowMarker=new File(flowBase,"complete-v1");if(!flowMarker.exists()){copy("matcha",flowBase);try(FileOutputStream out=new FileOutputStream(flowMarker)){out.write(1);}}
        String fd=flowBase.getAbsolutePath()+"/";OfflineTtsMatchaModelConfig matcha=new OfflineTtsMatchaModelConfig();matcha.setAcousticModel(fd+"model-steps-3.onnx");matcha.setVocoder(fd+"vocos.onnx");matcha.setLexicon(fd+"lexicon.txt");matcha.setTokens(fd+"tokens.txt");matcha.setDictDir(fd+"dict");
        OfflineTtsModelConfig flowModel=new OfflineTtsModelConfig();flowModel.setMatcha(matcha);flowModel.setNumThreads(3);OfflineTtsConfig flowConfig=new OfflineTtsConfig();flowConfig.setModel(flowModel);flowConfig.setRuleFsts(config.getRuleFsts());flowConfig.setMaxNumSentences(1);flowEngine=new OfflineTts(null,flowConfig);
    }catch(Throwable e){error=e;android.util.Log.e("VoiceTxtOffline","init failed",e);}final Throwable result=error;main.post(()->{if(!closed)callback.done(result);});});}
    void copy(String asset,File destination)throws IOException{String[] names=context.getAssets().list(asset);if(names!=null&&names.length>0){if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException(context.getString(R.string.ui_130));for(String name:names)copy(asset+"/"+name,new File(destination,name));}else{try(InputStream in=context.getAssets().open(asset);FileOutputStream out=new FileOutputStream(destination)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}}
    static String key(String text,int voice,float speed){return voice+"|"+speed+"|"+text;}
    void prefetch(String text,int voice,float speed){if(closed||text.trim().isEmpty())return;final String key=key(text,voice,speed);if(key.equals(prefetchKey))return;prefetchKey=key;prefetched=null;worker.execute(()->{try{if(closed||!key.equals(prefetchKey)||engine==null)return;GeneratedAudio data=generate(text,voice,speed,new CancelCallback(this,key));if(!closed&&key.equals(prefetchKey))prefetched=data;}catch(Throwable e){android.util.Log.w("VoiceTxtOffline","prefetch unavailable",e);}});}
    void speak(String text,int speaker,float speed,Listener listener){if(closed)return;cancelPlayback();final int request=epoch;final String key=key(text,speaker,speed);worker.execute(()->{try{
        if(!current(request)||engine==null)return;long began=SystemClock.elapsedRealtime();
        GeneratedAudio generated=key.equals(prefetchKey)?prefetched:null;lastCacheHit=generated!=null;
        if(generated==null)generated=generate(text,speaker,speed,new CancelCallback(this,request));
        lastSynthesisMs=SystemClock.elapsedRealtime()-began;
        if(key.equals(prefetchKey)){prefetched=null;prefetchKey="";}
        if(!current(request))return;final GeneratedAudio data=generated;
        playback.execute(()->play(data,request,listener));
    }catch(Throwable e){main.post(()->{if(current(request))listener.failed(e);});}});}
    void play(GeneratedAudio generated,int request,Listener listener){AudioTrack audio=null;try{
        if(!current(request))return;float[] values=generated.getSamples();if(values.length==0)throw new IOException(context.getString(R.string.ui_131));
        short[] pcm=new short[values.length];for(int i=0;i<pcm.length;i++)pcm[i]=(short)(Math.max(-1f,Math.min(1f,values[i]))*32767);
        int sampleRate=generated.getSampleRate();int min=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
        audio=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(min,8192)).build();
        track=audio;if(!current(request))return;audio.play();main.post(()->{if(current(request))listener.started();});
        int offset=0;while(offset<pcm.length&&current(request)){int n=audio.write(pcm,offset,Math.min(4096,pcm.length-offset),AudioTrack.WRITE_BLOCKING);if(n<=0)throw new IOException(context.getString(R.string.ui_132)+n);offset+=n;}
        while(current(request)&&Integer.toUnsignedLong(audio.getPlaybackHeadPosition())<pcm.length)Thread.sleep(50);
        main.post(()->{if(current(request))listener.done();});
    }catch(Throwable e){main.post(()->{if(current(request))listener.failed(e);});}finally{if(audio!=null){if(track==audio)track=null;try{audio.release();}catch(Exception ignored){}}}}
    interface SequenceListener { void buffering(int ready); void sentence(int index); void done(); void failed(Throwable error); }
    static class Clip {final int index,rate;final short[] pcm;Clip(int i,int r,short[] p){index=i;rate=r;pcm=p;}}
    static class Cue {final int index;final long frame;Cue(int i,long f){index=i;frame=f;}}
    volatile int sequenceUnderruns=0,sequenceEndUnderruns=0;
    volatile long maximumQueueWaitMs=0;
    volatile SpeechStartupTiming lastTiming;
    void speakSequence(java.util.List<String> sentences,int voice,float speed,SequenceListener listener){speakSequence(sentences,voice,speed,listener,new SpeechStartupTiming());}
    void speakSequence(java.util.List<String> sentences,int voice,float speed,SequenceListener listener,SpeechStartupTiming timing){
        stop();if(closed)return;final int request=epoch;lastTiming=timing;
        ArrayBlockingQueue<Clip> queue=new ArrayBlockingQueue<>(6);SpeechStartupBuffer startup=new SpeechStartupBuffer();
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        worker.execute(()->{try{for(int i=0;i<sentences.size()&&current(request);i++){
            long began=SystemClock.elapsedRealtime();startup.begin(began);String value=sentences.get(i);if(!current(request))break;startup.textReady(value.length());
            long synthesisAt=SystemClock.elapsedRealtime();
            GeneratedAudio data;
            try{
                data=generate(value,voice,speed,new CancelCallback(this,request));
            }catch(java.util.concurrent.CancellationException cancelled){
                throw cancelled;
            }catch(RuntimeException error){
                if(!current(request))break;
                android.util.Log.w("VoiceTxtOffline","Skipping failed synthesis segment index="+i,error);
                continue;
            }
            if(!current(request))break;
            long generated=SystemClock.elapsedRealtime(),synthesis=generated-synthesisAt;lastSynthesisMs=synthesis;
            float[] values=data.getSamples();if(values.length==0){android.util.Log.w("VoiceTxtOffline","Skipping unsynthesizable segment index="+i);continue;}short[] pcm=new short[values.length];for(int j=0;j<pcm.length;j++)pcm[j]=(short)(Math.max(-1f,Math.min(1f,values[j]))*32767);
            long duration=(long)pcm.length*1000/data.getSampleRate();timing.synthesisMs+=synthesis;timing.preparedAudioMs+=duration;timing.preparedClips++;
            if(timing.firstPreparedAt==0){timing.firstChunkMs=generated-began;timing.firstSynthesisMs=synthesis;timing.firstTextMs=synthesisAt-began;timing.firstPreparedAt=generated;}
            startup.prepared(value.length(),generated-began,duration,generated);
            Clip clip=new Clip(i,data.getSampleRate(),pcm);while(current(request)&&!queue.offer(clip,250,TimeUnit.MILLISECONDS)){}
            final int ready=queue.size();if(i<3)main.post(()->{if(current(request)&&timing.audioStartedAt==0)listener.buffering(ready);});
        }}catch(Throwable error){failure.set(error);}finally{finished.set(true);}});
        playback.execute(()->playSequence(request,queue,finished,failure,listener,startup,timing));
    }
    void playSequence(int request,ArrayBlockingQueue<Clip> queue,java.util.concurrent.atomic.AtomicBoolean finished,java.util.concurrent.atomic.AtomicReference<Throwable> failure,SequenceListener listener,SpeechStartupBuffer startup,SpeechStartupTiming timing){
        final DlnaCast remote=cast;
        if(remote!=null&&remote.device!=null){
            try{while(current(request)){
                Clip clip=queue.poll(250,TimeUnit.MILLISECONDS);
                if(clip==null){if(finished.get()){if(failure.get()!=null)throw failure.get();timing.completed=true;main.post(()->{if(current(request))listener.done();});return;}continue;}
                final int index=clip.index;
                remote.play(clip.pcm,clip.rate,()->current(request),()->main.post(()->{if(current(request))listener.sentence(index);}));
            }}catch(Throwable error){main.post(()->{if(current(request)){stop();listener.failed(error);}});}finally{android.util.Log.i("VoicePlaybackTiming",timing.json().toString());}
            return;
        }
        AudioTrack audio=null;Handler ticker=new Handler(Looper.getMainLooper());ConcurrentLinkedQueue<Cue> cues=new ConcurrentLinkedQueue<>();
        try{
            long buffered=0;int readyCount=0;
            while(current(request)){
                Clip[] ready=queue.toArray(new Clip[0]);buffered=0;for(Clip clip:ready)buffered+=(long)clip.pcm.length*1000/clip.rate;readyCount=ready.length;
                if(startup.ready(readyCount,buffered,finished.get(),SystemClock.elapsedRealtime()))break;
                if(finished.get()&&readyCount==0){if(failure.get()!=null)throw failure.get();timing.completed=true;main.post(()->{if(current(request))listener.done();});return;}
                Thread.sleep(10);
            }
            if(!current(request))return;if(failure.get()!=null&&queue.isEmpty())throw failure.get();
            timing.initialClips=readyCount;timing.initialAudioMs=buffered;
            Clip first=queue.poll();if(first==null)throw new IOException(context.getString(R.string.ui_133));int sampleRate=first.rate;
            int min=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            audio=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(min,sampleRate*2)).build();
            if(Build.VERSION.SDK_INT>=31)audio.setStartThresholdInFrames(1);
            track=audio;final AudioTrack clock=audio;sequenceUnderruns=0;sequenceEndUnderruns=0;maximumQueueWaitMs=0;
            Runnable tick=new Runnable(){public void run(){if(!current(request))return;try{long head=Integer.toUnsignedLong(clock.getPlaybackHeadPosition());Cue cue;while((cue=cues.peek())!=null&&head>=cue.frame){cues.poll();listener.sentence(cue.index);}Cue next=cues.peek();long delay=next==null?(detailedUpdates?100:500):Math.max(40,Math.min(detailedUpdates?250:1000,(next.frame-head)*1000/sampleRate));ticker.postDelayed(this,delay);}catch(IllegalStateException ignored){}}};
            long frames=0,gapAt=0;boolean started=false;Clip clip=first;
            while(current(request)){
                if(clip==null){
                    if(started&&queue.isEmpty()&&!finished.get()&&Integer.toUnsignedLong(audio.getPlaybackHeadPosition())>=frames&&gapAt==0){gapAt=SystemClock.elapsedRealtime();timing.gapCount++;}
                    long waiting=SystemClock.elapsedRealtime();clip=queue.poll(50,TimeUnit.MILLISECONDS);maximumQueueWaitMs=Math.max(maximumQueueWaitMs,SystemClock.elapsedRealtime()-waiting);
                    if(clip==null){if(finished.get()){clip=queue.poll();if(clip==null)break;}else continue;}
                }
                if(gapAt!=0){timing.gapMs+=SystemClock.elapsedRealtime()-gapAt;gapAt=0;}
                if(clip.rate!=sampleRate)throw new IOException(context.getString(R.string.ui_134));cues.add(new Cue(clip.index,frames));
                int offset=0;while(offset<clip.pcm.length&&current(request)){int count=Math.min(4096,clip.pcm.length-offset);int n=audio.write(clip.pcm,offset,count,AudioTrack.WRITE_BLOCKING);if(n<=0)throw new IOException(context.getString(R.string.ui_135));offset+=n;if(!started){audio.play();started=true;timing.audioStartedAt=SystemClock.elapsedRealtime();ticker.post(tick);}}
                frames+=offset;sequenceUnderruns=Math.max(sequenceUnderruns,audio.getUnderrunCount());clip=null;
            }
            while(current(request)&&Integer.toUnsignedLong(audio.getPlaybackHeadPosition())<frames)Thread.sleep(50);
            if(current(request)){sequenceEndUnderruns=audio.getUnderrunCount();timing.platformUnderruns=sequenceUnderruns;if(failure.get()!=null)throw failure.get();timing.completed=true;main.post(()->{if(current(request))listener.done();});}
        }catch(Throwable error){main.post(()->{if(current(request))listener.failed(error);});}finally{ticker.removeCallbacksAndMessages(null);if(!timing.completed)timing.cancelled=true;android.util.Log.i("VoicePlaybackTiming",timing.json().toString());if(audio!=null){if(track==audio)track=null;try{audio.release();}catch(Exception ignored){}}}
    }
    boolean current(int request){return !closed&&request==epoch;}
    void cancelPlayback(){epoch++;if(cast!=null)cast.stop();AudioTrack audio=track;if(audio!=null){try{audio.pause();audio.flush();audio.stop();}catch(Exception ignored){}}}
    void stop(){cancelPlayback();prefetchKey="";prefetched=null;worker.getQueue().clear();}
    void close(){stop();closed=true;worker.execute(()->{if(engine!=null){engine.release();engine=null;}if(flowEngine!=null){flowEngine.release();flowEngine=null;}});worker.shutdown();playback.shutdown();}
}
