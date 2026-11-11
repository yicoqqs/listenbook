package com.voicetxt.reader;

import android.content.*;import android.net.*;import android.os.*;import com.google.android.gms.tasks.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;import java.util.concurrent.*;

/** Curated HY-MT model management. Only fully verified files become available to readers. */
final class HunyuanModels {
    static final String NAME="HY-MT1.5-1.8B · 1.25-bit",REVISION="hymt15-stq1-v1",SHA256="93e025c93cc082e73a3f142b757623a8b9cf541c020a8013ca4ee669556860ab";
    static final long BYTES=461860704L;
    static final String[] URLS={"https://modelscope.cn/models/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF/resolve/d95cf4d0d1eee82619df809f784176f524e2e0ae/Hy-MT1.5-1.8B-STQ1_0.gguf","https://huggingface.co/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF/resolve/1bed36c0a8f5a0eddf77987b02ba66d4c268aca2/Hy-MT1.5-1.8B-1.25bit.gguf"};
    private static HunyuanModels instance;static synchronized HunyuanModels get(Context c){if(instance==null)instance=new HunyuanModels(c);return instance;}
    final Context context;final File directory,file,stamp,partial;final ExecutorService disk=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());final Set<Runnable> listeners=new HashSet<>();
    volatile boolean busy,cancelled,installed;volatile long received;volatile String phase="",error="";volatile Throwable lastFailure;volatile HttpURLConnection connection;Task<Void> operation;
    HunyuanModels(Context c){context=c.getApplicationContext();directory=new File(context.getNoBackupFilesDir(),"hunyuan-translation");directory.mkdirs();file=new File(directory,"model.gguf");stamp=new File(directory,"verified.sha256");partial=new File(directory,"model.partial");if(partial.exists()){partial.delete();error="cancelled";}try{installed=file.length()==BYTES&&stamp.isFile()&&new String(VoiceModel.readBytes(stamp),StandardCharsets.US_ASCII).trim().equals(SHA256);}catch(IOException ignored){}if(!installed){file.delete();stamp.delete();} }
    boolean ready(){return installed&&!busy&&file.isFile()&&file.length()==BYTES;}
    synchronized void listen(Runnable listener){listeners.add(listener);}synchronized void unlisten(Runnable listener){listeners.remove(listener);}
    void changed(){main.post(()->{ArrayList<Runnable> copy;synchronized(this){copy=new ArrayList<>(listeners);}for(Runnable item:copy)item.run();});}
    boolean wifiOnly(){return context.getSharedPreferences("translation_models",0).getBoolean("wifi_only",true);}void wifiOnly(boolean enabled){context.getSharedPreferences("translation_models",0).edit().putBoolean("wifi_only",enabled).apply();}
    boolean wifi(){ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);NetworkCapabilities capabilities=cm.getNetworkCapabilities(cm.getActiveNetwork());return capabilities!=null&&capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);}
    synchronized Task<Void> download(){if(ready())return Tasks.forResult(null);return start(null);}
    synchronized Task<Void> importUri(Uri uri){return start(uri);}
    private Task<Void> start(Uri uri){
        if(busy)return operation==null?Tasks.forException(new IOException(context.getString(R.string.models_busy))):operation;
        if(HunyuanEngine.get(context).busy()||TranslationModels.translationPlaying())return Tasks.forException(new IOException(context.getString(R.string.models_busy)));
        if(uri==null&&wifiOnly()&&!wifi())return Tasks.forException(new IOException(context.getString(R.string.hy_wifi_required)));
        busy=true;cancelled=false;error="";lastFailure=null;received=0;phase=uri==null?"download":"import";TaskCompletionSource<Void> result=new TaskCompletionSource<>();operation=result.getTask();changed();
        disk.execute(()->{try{
            if(uri!=null){try(InputStream input=context.getContentResolver().openInputStream(uri)){if(input==null)throw new IOException("file");copy(input,false);}}
            else{IOException last=null;for(String address:URLS){check();try{receive(address);last=null;break;}catch(IOException failure){last=failure;if(cancelled)throw new CancellationException();partial.delete();received=0;changed();}}if(last!=null)throw last;}
            check();if(partial.length()!=BYTES)throw new IOException("model");phase="verify";changed();long model=0;
            try{model=HunyuanBridge.load(partial.getAbsolutePath(),3);check();}finally{if(model!=0)HunyuanBridge.release(model);}
            check();if(file.exists()){if(!installed)file.delete();else{partial.delete();installed=true;return;}}
            if(!partial.renameTo(file))throw new IOException("storage");
            android.util.AtomicFile marker=new android.util.AtomicFile(stamp);FileOutputStream out=marker.startWrite();try{out.write(SHA256.getBytes(StandardCharsets.US_ASCII));marker.finishWrite(out);}catch(IOException failure){marker.failWrite(out);file.delete();throw failure;}installed=true;
        }catch(CancellationException cancelled){error="cancelled";}catch(Throwable failure){lastFailure=failure;android.util.Log.w("HunyuanModels","Model operation failed",failure);error=phase.equals("verify")?"runtime":uri!=null?"model":"network";}
        finally{partial.delete();connection=null;busy=false;phase="";changed();if(error.isEmpty())result.trySetResult(null);else result.trySetException(new IOException(context.getString(error.equals("cancelled")?R.string.hy_cancelled:error.equals("runtime")?R.string.hy_device_error:error.equals("model")?R.string.hy_invalid_model:R.string.hy_network_error)));}});return operation;
    }
    void check(){if(cancelled||Thread.currentThread().isInterrupted())throw new CancellationException();}
    void copy(InputStream input,boolean network)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long last=0;byte[] buffer=new byte[256*1024];received=0;
        try(FileOutputStream out=new FileOutputStream(partial)){int n;while((n=input.read(buffer))!=-1){check();if(network&&wifiOnly()&&!wifi())throw new IOException("wifi");received+=n;if(received>BYTES)throw new IOException("model");out.write(buffer,0,n);digest.update(buffer,0,n);long now=SystemClock.elapsedRealtime();if(now-last>250){last=now;changed();}}out.getFD().sync();}
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));if(received!=BYTES||!SHA256.equals(hash.toString()))throw new IOException("model");
    }
    void receive(String address)throws Exception{
        URL url=new URL(address);for(int redirect=0;redirect<7;redirect++){
            check();if(!url.getProtocol().equals("https"))throw new IOException("https");HttpURLConnection request=(HttpURLConnection)url.openConnection();connection=request;request.setInstanceFollowRedirects(false);request.setConnectTimeout(20000);request.setReadTimeout(30000);request.setRequestProperty("User-Agent","VoiceTxt-HYMT/1.0");
            try{int status=request.getResponseCode();if(status>=300&&status<400){String next=request.getHeaderField("Location");if(next==null)throw new IOException("redirect");url=new URL(url,next);continue;}if(status!=200)throw new IOException("HTTP "+status);long length=request.getContentLengthLong();if(length>0&&length!=BYTES)throw new IOException("model");try(InputStream input=request.getInputStream()){copy(input,true);}return;}finally{request.disconnect();connection=null;}
        }throw new IOException("redirect");
    }
    void cancel(){cancelled=true;HttpURLConnection current=connection;if(current!=null)current.disconnect();}
    synchronized Task<Void> delete(){
        if(busy||HunyuanEngine.get(context).busy()||TranslationModels.translationPlaying())return Tasks.forException(new IOException(context.getString(R.string.models_busy)));
        busy=true;phase="delete";error="";changed();TaskCompletionSource<Void> completion=new TaskCompletionSource<>();operation=completion.getTask();HunyuanEngine.get(context).unload().addOnCompleteListener(Runnable::run,unloaded->disk.execute(()->{try{if(!unloaded.isSuccessful())throw OfflineTranslation.failure(unloaded);if(file.exists()&&!file.delete())throw new IOException("storage");stamp.delete();installed=false;completion.trySetResult(null);}catch(Exception failure){error="delete";completion.trySetException(failure);}finally{busy=false;phase="";changed();}}));return operation;
    }
}
