package com.voicetxt.reader;

import android.content.Context;import android.os.*;import com.google.android.gms.tasks.*;
import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;

/** One bounded CPU translator per process, shared by readers and released when idle. */
final class HunyuanEngine {
    private static HunyuanEngine instance;
    static synchronized HunyuanEngine get(Context context){if(instance==null)instance=new HunyuanEngine(context);return instance;}
    final Context context;final ExecutorService worker=Executors.newSingleThreadExecutor();final AtomicLong epoch=new AtomicLong(1);final Object pointerLock=new Object();
    final Handler main=new Handler(Looper.getMainLooper());final Runnable idle=this::releaseIdle;volatile long handle;volatile int pending;volatile long lastLoadMs,lastTranslationMs;volatile Object activeOwner;
    HunyuanEngine(Context context){this.context=context.getApplicationContext();}
    synchronized Task<String> translate(String source,String target,String text,Object owner,java.util.function.BooleanSupplier continuing){
        TaskCompletionSource<String> completion=new TaskCompletionSource<>();pending++;main.removeCallbacks(idle);
        worker.execute(()->{long request=epoch.get();activeOwner=owner;try{
            check(request);if(!continuing.getAsBoolean())throw new CancellationException();HunyuanModels models=HunyuanModels.get(context);if(!models.ready())throw new IOException(context.getString(R.string.hy_missing_hint));
            if(handle==0){long began=SystemClock.elapsedRealtime();long loaded=HunyuanBridge.load(models.file.getAbsolutePath(),3);synchronized(pointerLock){handle=loaded;}lastLoadMs=SystemClock.elapsedRealtime()-began;}
            check(request);if(!continuing.getAsBoolean())throw new CancellationException();String language=Locale.forLanguageTag(target).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE);
            String instruction=OfflineTranslation.normalize(source).equals("zh")||OfflineTranslation.normalize(target).equals("zh")?"将以下文本翻译为"+language+"，注意只需要输出翻译后的结果，不要额外解释：":"Translate the following segment into "+Locale.forLanguageTag(target).getDisplayLanguage(Locale.ENGLISH)+", without additional explanation.";
            String prompt=instruction+"\n\n"+text.replace("<｜hy_","＜｜hy_");
            long began=SystemClock.elapsedRealtime();String result;try(CpuWork lease=new CpuWork(context)){result=HunyuanBridge.generate(handle,prompt,384,request);}lastTranslationMs=SystemClock.elapsedRealtime()-began;check(request);if(!continuing.getAsBoolean())throw new CancellationException();
            if(result==null||result.trim().isEmpty())throw new IOException(context.getString(R.string.hy_translate_error));completion.trySetResult(result.trim());
        }catch(Exception error){completion.trySetException(error);}catch(LinkageError error){completion.trySetException(new IOException(context.getString(R.string.hy_device_error),error));}
        finally{activeOwner=null;synchronized(this){pending--;}main.post(()->{main.removeCallbacks(idle);main.postDelayed(idle,60000);});}});return completion.getTask();
    }
    void check(long request){if(epoch.get()!=request||Thread.currentThread().isInterrupted())throw new CancellationException();}
    void cancel(Object owner){if(activeOwner!=owner)return;long next=epoch.incrementAndGet();synchronized(pointerLock){if(handle!=0)HunyuanBridge.cancel(handle,next);}}
    synchronized void releaseIdle(){if(pending!=0)return;main.removeCallbacks(idle);worker.execute(()->{synchronized(HunyuanEngine.this){if(pending!=0)return;}synchronized(pointerLock){if(handle!=0){HunyuanBridge.release(handle);handle=0;}}});}
    synchronized boolean busy(){return pending!=0;}
    Task<Void> unload(){TaskCompletionSource<Void> completion=new TaskCompletionSource<>();synchronized(this){if(pending!=0)return Tasks.forException(new IOException(context.getString(R.string.models_busy)));worker.execute(()->{try{synchronized(pointerLock){if(handle!=0){HunyuanBridge.release(handle);handle=0;}}completion.setResult(null);}catch(Exception error){completion.setException(error);}});}return completion.getTask();}
}
