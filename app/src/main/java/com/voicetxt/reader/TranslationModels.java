package com.voicetxt.reader;

import android.content.*;
import android.os.*;
import com.google.android.gms.tasks.*;
import com.google.mlkit.common.model.*;
import com.google.mlkit.nl.translate.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One process-wide model catalogue. All mutations use the supported ML Kit API. */
final class TranslationModels {
    private static TranslationModels instance;
    static synchronized TranslationModels get(Context context){if(instance==null)instance=new TranslationModels(context);return instance;}
    interface Backend {Task<Set<TranslateRemoteModel>> list();Task<Void> download(TranslateRemoteModel model,DownloadConditions conditions);Task<Void> delete(TranslateRemoteModel model);}
    static final class SdkBackend implements Backend {final RemoteModelManager manager=RemoteModelManager.getInstance();public Task<Set<TranslateRemoteModel>> list(){return manager.getDownloadedModels(TranslateRemoteModel.class);}public Task<Void> download(TranslateRemoteModel model,DownloadConditions conditions){return manager.download(model,conditions);}public Task<Void> delete(TranslateRemoteModel model){return manager.deleteDownloadedModel(model);}}
    final Context context;final Backend backend;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService disk=Executors.newSingleThreadExecutor();
    final Set<Runnable> listeners=new HashSet<>();
    final Map<String,Task<Void>> downloads=new HashMap<>();
    final Set<String> deleting=new HashSet<>();
    final Map<String,Exception> errors=new HashMap<>();
    volatile Set<String> installed=Collections.emptySet();volatile Map<String,Long> sizes=Collections.emptyMap();
    volatile long totalBytes;volatile boolean loaded;volatile Exception loadError;
    TranslationModels(Context context){this(context,new SdkBackend());}
    TranslationModels(Context context,Backend backend){this.context=context.getApplicationContext();this.backend=backend;}
    synchronized void listen(Runnable listener){listeners.add(listener);}
    synchronized void unlisten(Runnable listener){listeners.remove(listener);}
    void changed(){main.post(()->{ArrayList<Runnable> copy;synchronized(this){copy=new ArrayList<>(listeners);}for(Runnable item:copy)item.run();});}
    boolean wifiOnly(){return context.getSharedPreferences("translation_models",0).getBoolean("wifi_only",true);}
    void wifiOnly(boolean value){context.getSharedPreferences("translation_models",0).edit().putBoolean("wifi_only",value).apply();changed();}
    synchronized boolean downloading(String code){return downloads.containsKey(code);}
    synchronized boolean deleting(String code){return deleting.contains(code);}
    synchronized Exception error(String code){return errors.get(code);}
    Task<Set<String>> refresh(){return backend.list().continueWithTask(task->{if(!task.isSuccessful()){loadError=task.getException();changed();throw OfflineTranslation.failure(task);}Set<String> found=new LinkedHashSet<>();found.add("en");for(TranslateRemoteModel model:task.getResult())found.add(OfflineTranslation.normalize(model.getLanguage()));installed=Collections.unmodifiableSet(found);loaded=true;loadError=null;return Tasks.call(disk,()->{measure();changed();return installed;});});}
    // Storage reporting only. Missing/changed SDK storage layout falls back to a labelled estimate.
    void measure(){File root=new File(context.getNoBackupFilesDir(),"com.google.mlkit.translate.models");Map<String,Long> measured=new HashMap<>();File[] entries=root.listFiles();if(entries!=null)for(File entry:entries){String[] pair=entry.getName().split("_");if(pair.length==2){String code=pair[0].equals("en")?pair[1]:pair[1].equals("en")?pair[0]:"";if(installed.contains(code))measured.put(code,bytes(entry));}}sizes=Collections.unmodifiableMap(measured);totalBytes=bytes(root);}
    static long bytes(File file){if(!file.exists())return 0;if(file.isFile())return file.length();long sum=0;File[] children=file.listFiles();if(children!=null)for(File child:children)sum+=bytes(child);return sum;}
    synchronized Task<Void> download(String code){if(!TranslationText.SUPPORTED.contains(code))return Tasks.forException(new IllegalArgumentException(code));if(code.equals("en"))return Tasks.forResult(null);if(downloads.containsKey(code))return downloads.get(code);if(deleting.contains(code))return Tasks.forException(new IOException(context.getString(R.string.models_busy)));errors.remove(code);TaskCompletionSource<Void> completion=new TaskCompletionSource<>();Task<Void> result=completion.getTask();downloads.put(code,result);changed();TranslateRemoteModel model=new TranslateRemoteModel.Builder(code).build();DownloadConditions.Builder conditions=new DownloadConditions.Builder();if(wifiOnly())conditions.requireWifi();backend.download(model,conditions.build()).addOnCompleteListener(task->{synchronized(this){downloads.remove(code);if(!task.isSuccessful())errors.put(code,OfflineTranslation.failure(task));}refresh().addOnCompleteListener(ignored->{if(task.isSuccessful())completion.trySetResult(null);else completion.trySetException(OfflineTranslation.failure(task));changed();});});return result;}
    static boolean translationPlaying(){if(PlaybackService.owner instanceof MainActivity){MainActivity reader=(MainActivity)PlaybackService.owner;return reader.playing&&!reader.readingLanguage.isEmpty();}return false;}
    synchronized Task<Void> delete(String code){if(code.equals("en")||!TranslationText.SUPPORTED.contains(code))return Tasks.forException(new IllegalArgumentException(code));if(downloading(code)||deleting(code)||translationPlaying()||OfflineTranslation.translating.get()>0)return Tasks.forException(new IOException(context.getString(R.string.models_busy)));deleting.add(code);errors.remove(code);changed();TaskCompletionSource<Void> completion=new TaskCompletionSource<>();backend.delete(new TranslateRemoteModel.Builder(code).build()).addOnCompleteListener(task->{synchronized(this){deleting.remove(code);if(!task.isSuccessful())errors.put(code,OfflineTranslation.failure(task));}refresh().addOnCompleteListener(ignored->{if(task.isSuccessful())completion.setResult(null);else completion.setException(OfflineTranslation.failure(task));changed();});});return completion.getTask();}
}
