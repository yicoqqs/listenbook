package com.voicetxt.reader;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Tests the packaged model/native libraries on the device, without Android TextToSpeech. */
public class OfflineSmokeTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){Bundle result=new Bundle();OfflineSpeaker speaker=new OfflineSpeaker(getTargetContext());try{
        CountDownLatch initialized=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();long begin=android.os.SystemClock.elapsedRealtime();
        speaker.init(e->{error.set(e);initialized.countDown();});
        if(!initialized.await(90,TimeUnit.SECONDS))throw new AssertionError("model initialization timed out");
        if(error.get()!=null)throw new AssertionError("model initialization failed",error.get());
        result.putLong("initMs",android.os.SystemClock.elapsedRealtime()-begin);
        for(int sid:new int[]{2010,2015,2021,1000}){CountDownLatch completed=new CountDownLatch(1);AtomicBoolean started=new AtomicBoolean();error.set(null);long t=android.os.SystemClock.elapsedRealtime();
            speaker.speak(sid>=2000?"你好，这是内置中文男声。":"你好，这是内置中文女声。",sid,1f,new OfflineSpeaker.Listener(){public void started(){started.set(true);}public void done(){completed.countDown();}public void failed(Throwable e){error.set(e);completed.countDown();}});
            if(!completed.await(60,TimeUnit.SECONDS))throw new AssertionError("voice timed out: "+sid);
            if(error.get()!=null)throw new AssertionError("voice failed: "+sid,error.get());
            if(!started.get())throw new AssertionError("playback did not start");
            result.putLong("voice"+sid+"Ms",android.os.SystemClock.elapsedRealtime()-t);result.putLong("synthesis"+sid+"Ms",speaker.lastSynthesisMs);
        }
        java.util.List<String> passages=java.util.Arrays.asList("夜色渐渐深了。","远处的山林里，传来几声清脆的鸟鸣。","他停下脚步。","回头看时，来路已经隐没在浓重的雾气之中。","有人轻声说道，你终于来了。","她没有回答。","只把手中的书轻轻放在桌上，转身推开了窗。","风从窗外吹来。","纸页翻动的声音，在安静的房间中格外清晰。","这一次，他终于明白了事情的经过。","故事还在继续。","明天清晨，他们就要出发，去寻找那座传说中的城市。");
        for(int voice:new int[]{2010,2015,2021,1000}){
        CountDownLatch sequenceDone=new CountDownLatch(1);AtomicInteger sentenceCount=new AtomicInteger();AtomicInteger order=new AtomicInteger(-1);error.set(null);long sequenceStart=android.os.SystemClock.elapsedRealtime();java.util.concurrent.atomic.AtomicLong firstAudio=new java.util.concurrent.atomic.AtomicLong();
        speaker.speakSequence(passages,voice,1.8f,new OfflineSpeaker.SequenceListener(){public void buffering(int n){}public void sentence(int index){if(index!=order.incrementAndGet())error.set(new AssertionError("highlight out of order"));sentenceCount.incrementAndGet();firstAudio.compareAndSet(0,android.os.SystemClock.elapsedRealtime()-sequenceStart);}public void done(){sequenceDone.countDown();}public void failed(Throwable e){error.set(e);sequenceDone.countDown();}});
        if(!sequenceDone.await(150,TimeUnit.SECONDS))throw new AssertionError("continuous playback timed out");if(error.get()!=null)throw new AssertionError("continuous playback failed",error.get());
        result.putInt("underruns"+voice,speaker.sequenceUnderruns);result.putInt("sentences"+voice,sentenceCount.get());result.putLong("firstAudio"+voice+"Ms",firstAudio.get());
        if(sentenceCount.get()!=passages.size())throw new AssertionError("missing sentence events");if(speaker.sequenceUnderruns>0)throw new AssertionError("buffer underrun for voice "+voice);
        }
        AtomicInteger stale=new AtomicInteger();speaker.speak("这是一段用于验证暂停取消的语音。",2010,1f,new OfflineSpeaker.Listener(){public void started(){stale.incrementAndGet();}public void done(){stale.incrementAndGet();}public void failed(Throwable e){stale.incrementAndGet();}});speaker.stop();Thread.sleep(1000);if(stale.get()!=0)throw new AssertionError("stale callback after stop");
        SystemSpeaker sys=new SystemSpeaker(getTargetContext());try{CountDownLatch discovered=new CountDownLatch(1);sys.init(()->{if(sys.initialized)discovered.countDown();});if(!discovered.await(30,TimeUnit.SECONDS))throw new AssertionError("system engine enumeration timed out");result.putInt("systemVoiceCount",sys.options.size());if(sys.options.isEmpty())throw new AssertionError("no system Chinese voices");SystemSpeaker.Option option=sys.options.get(0);for(SystemSpeaker.Option candidate:sys.options)if(candidate.engine.contains("hihonor")&&!candidate.voice.isNetworkConnectionRequired()){option=candidate;break;}CountDownLatch spoken=new CountDownLatch(1);AtomicInteger systemEvents=new AtomicInteger();error.set(null);sys.speak(java.util.Arrays.asList("这是系统音色朗读测试。","支持切换系统安装的中文声音。"),option,1.5f,new SystemSpeaker.Listener(){public void sentence(int index){systemEvents.incrementAndGet();}public void range(int index,int start,int end){}public void done(){spoken.countDown();}public void failed(){error.set(new AssertionError("system TTS failed"));spoken.countDown();}});if(!spoken.await(45,TimeUnit.SECONDS)||error.get()!=null||systemEvents.get()!=2)throw new AssertionError("system voice playback did not complete",error.get());result.putInt("systemSentenceEvents",systemEvents.get());}finally{sys.close();}
        result.putString("result","PASS: 3 replacement male voices + retained flow female: 12 sentences each at 1.8x, zero underruns; system enumeration/playback; cancellation");speaker.close();finish(-1,result);
    }catch(Throwable e){speaker.close();result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}}
}
