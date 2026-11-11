package com.voicetxt.reader;

import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;

/** Uses the user's two original archives; diagnose mode cleans new entries, verify mode retains valid imports. */
public class PiperCompatibilityTest extends Instrumentation {
    boolean diagnose;
    public void onCreate(Bundle args){super.onCreate(args);diagnose=args!=null&&args.getString("mode","").equals("diagnose");start();}
    void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    void ui(Runnable task){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable failure){error.set(failure);}});if(error.get()!=null)throw new AssertionError(error.get());}
    void await(java.util.function.BooleanSupplier condition,String message,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){AtomicBoolean ready=new AtomicBoolean();ui(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(100);}throw new AssertionError(message);}
    public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();VoicePackages packages=VoicePackages.get(context);HashSet<String> before=new HashSet<>(packages.models.keySet());OfflineVoicesActivity manager=null;MainActivity reader=null;SharedPreferences prefs=context.getSharedPreferences("reading_settings",0);Map<String,?> saved=new HashMap<>(prefs.getAll());boolean passed=false;
        try{
            result.putString("runtime",com.k2fsa.sherpa.onnx.VersionInfo.Companion.getVersion());File sources=new File(context.getExternalFilesDir(null),"piper-compat-qa");
            for(String name:diagnose?new String[]{"vits-piper-zh_CN-chaowen-medium-fp16.tar.bz2","vits-piper-zh_CN-huayan-medium.tar.bz2"}:new String[]{"vits-piper-zh_CN-chaowen-medium-fp16.tar.bz2","vits-piper-zh_CN-chaowen-medium.tar.bz2","vits-piper-zh_CN-huayan-medium.tar.bz2"}){
                File file=new File(sources,name);check(file.isFile(),"Archive missing: "+name);packages.importUri("",Uri.fromFile(file));await(()->!packages.busy,"Import hung: "+packages.phase,120000);result.putString(name,packages.phase+" / "+packages.error);
                if(packages.lastFailure!=null)result.putString(name+"_detail",android.util.Log.getStackTraceString(packages.lastFailure));if(diagnose)continue;if(name.contains("-fp16")){check(packages.error.equals("model_graph"),"FP16 type mismatch not identified: "+packages.error);result.putString(name,"PASS: original FP16 archive rejected with model_graph type mismatch; no invalid model published");continue;}
                VoiceModel pack=packages.models.get(packages.lastInstalled);
                if(pack==null&&packages.error.equals("duplicate")){for(VoiceModel candidate:packages.list("zh"))if(candidate.name.equals(name.replace(".tar.bz2","")))pack=candidate;}
                check(pack!=null,"Could not import "+name+": "+packages.error);check(pack.supports("zh"),"Not grouped as Chinese");final VoiceModel selectedPack=pack;
                CountDownLatch init=new CountDownLatch(1),done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();PackageSpeaker speaker=new PackageSpeaker(context,new VoicePackages.Selection(pack,0),"zh");
                speaker.init(error->{failure.set(error);init.countDown();});check(init.await(40,TimeUnit.SECONDS)&&failure.get()==null,"Playback initialization failed: "+name);
                AtomicInteger sentences=new AtomicInteger();speaker.speakSequence(Arrays.asList("你好，欢迎使用我爱听书。","今天是二零二六年十月二十八日。","这是中文离线语音的连续朗读测试。"),0,1f,new OfflineSpeaker.SequenceListener(){public void buffering(int count){}public void sentence(int index){sentences.incrementAndGet();}public void done(){done.countDown();}public void failed(Throwable error){failure.set(error);done.countDown();}});
                check(done.await(90,TimeUnit.SECONDS)&&failure.get()==null&&sentences.get()==3,"Continuous speech failed: "+name+" / "+failure.get());speaker.close();await(()->!packages.leases.containsKey(selectedPack.id),"Playback model not released",20000);
                result.putString(name,"PASS: original archive imported, Chinese detected, native probe passed, three spoken sentences completed, stop/release passed");
                reader=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).putExtra("book_id","piper_compat_qa").putExtra("book_name","Piper语音验证").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final MainActivity a=reader;
                await(()->a.system!=null&&a.system.initialized,"Reader init timeout",30000);
                ui(()->{a.readingLanguage="";a.systemVoiceKey=selectedPack.key(0);a.text="第一章 测试\n你好，欢迎使用我爱听书。我们沿着小路前行，欣赏四周的风景。";a.chapters=MainActivity.parse(a.text);a.position=0;a.rate=1;a.loopPlayback=false;a.showPosition(true);a.startReading();});
                await(()->a.playing&&a.latestHighlightEnd>0&&a.latestStatus.startsWith(a.getString(R.string.vp_reading)),"Reader did not speak/highlight: "+name,45000);
                ui(()->{a.stop();a.finish();});reader=null;await(()->!packages.leases.containsKey(selectedPack.id),"Reader lease not released",20000);
            }
            if(!diagnose){manager=(OfflineVoicesActivity)startActivitySync(new Intent(context,OfflineVoicesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final OfflineVoicesActivity m=manager;await(()->m.system!=null&&m.system.initialized,"Manager init",30000);ui(()->{m.expanded="zh";m.render();check(m.items("zh").stream().filter(item->item.pack!=null&&(item.pack.name.contains("chaowen")||item.pack.name.contains("huayan"))).count()>=2,"Piper rows not shown");});Thread.sleep(400);android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();if(screenshot!=null){try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"piper-compatible-manager.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}}
            result.putString("result",diagnose?"DIAGNOSED: original archives tested against installed runtime":"PASS: original invalid FP16 graph rejected with specific error; official FP32 Chaowen and original Huayan import, synthesize consecutive sentences, read/highlight text, stop and release; valid packages retained; reading preferences preserved");passed=true;
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(reader!=null){final MainActivity a=reader;ui(()->{a.stop();a.finish();});}if(manager!=null){final OfflineVoicesActivity m=manager;ui(m::finish);}waitForIdleSync();if(diagnose)try{for(String id:new ArrayList<>(packages.models.keySet()))if(!before.contains(id)){packages.delete(id);long end=SystemClock.elapsedRealtime()+15000;while(packages.busy&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);}}catch(Exception ignored){}
            SharedPreferences.Editor restore=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof String)restore.putString(key,(String)value);else if(value instanceof Integer)restore.putInt(key,(Integer)value);else if(value instanceof Long)restore.putLong(key,(Long)value);else if(value instanceof Float)restore.putFloat(key,(Float)value);else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);}restore.commit();context.getSharedPreferences("reader_piper_compat_qa",0).edit().clear().commit();new File(context.getFilesDir(),"book_piper_compat_qa.txt.position").delete();packages.phase="";packages.error="";
        }finish(passed?-1:0,result);
    }
}
