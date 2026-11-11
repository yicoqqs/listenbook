package com.voicetxt.reader;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import com.k2fsa.sherpa.onnx.OfflineTts;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Imports are staged, checked, probed and atomically published. Never exposes partial models. */
final class VoicePackages {
    private static VoicePackages instance;
    static synchronized VoicePackages get(Context context){if(instance==null)instance=new VoicePackages(context);return instance;}
    static final class Selection {final VoiceModel pack;final int speaker;Selection(VoiceModel pack,int speaker){this.pack=pack;this.speaker=speaker;}}
    final Context context;final File installed,staging;
    final ExecutorService worker=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());
    final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    final LinkedHashMap<String,VoiceModel> models=new LinkedHashMap<>();final HashMap<String,Integer> leases=new HashMap<>();
    volatile boolean busy,cancelled;volatile String phase="",error="",jobLanguage="",lastInstalled="";volatile long transferred,total=-1;long lastNotice;
    volatile Throwable lastFailure;
    interface Connections {HttpURLConnection open(URL url)throws IOException;}
    Connections connections=url->(HttpURLConnection)url.openConnection();
    VoicePackages(Context context){this.context=context.getApplicationContext();installed=new File(context.getFilesDir(),"voice-packages");staging=new File(context.getFilesDir(),"voice-imports");installed.mkdirs();staging.mkdirs();File[] dirs=installed.listFiles();if(dirs!=null)for(File dir:dirs)if(dir.isDirectory())try{VoiceModel pack=VoiceModel.read(dir);models.put(pack.id,pack);}catch(Exception ignored){}cleanStale();}
    void cleanStale(){File[] files=staging.listFiles();if(files!=null)for(File file:files)try{deleteTree(file,staging);}catch(IOException ignored){}}
    synchronized List<VoiceModel> all(){return new ArrayList<>(models.values());}
    synchronized List<VoiceModel> list(String language){ArrayList<VoiceModel> result=new ArrayList<>();for(VoiceModel pack:models.values())if(pack.supports(language))result.add(pack);return result;}
    static boolean isKey(String key){return key!=null&&key.startsWith("pack:");}
    synchronized Selection resolve(String key,String language){try{String[] parts=key.split(":");if(parts.length!=3||!parts[0].equals("pack"))return null;VoiceModel pack=models.get(parts[1]);int speaker=Integer.parseInt(parts[2]);return pack!=null&&pack.supports(language)&&speaker>=0&&speaker<pack.speakers?new Selection(pack,speaker):null;}catch(RuntimeException error){return null;}}
    synchronized Selection preferred(String language,String key){Selection selection=resolve(key,language);if(selection!=null)return selection;List<VoiceModel> list=list(language);return list.isEmpty()?null:new Selection(list.get(0),list.get(0).defaultSpeaker(language));}
    synchronized void acquire(String id)throws IOException{if(!models.containsKey(id))throw new IOException("invalid");leases.put(id,leases.getOrDefault(id,0)+1);}
    synchronized void release(String id){int remaining=leases.getOrDefault(id,0)-1;if(remaining<=0)leases.remove(id);else leases.put(id,remaining);}
    void add(Runnable listener){listeners.add(listener);}
    void remove(Runnable listener){listeners.remove(listener);}
    void notifyChanged(){main.post(()->{for(Runnable listener:listeners)listener.run();});}
    void phase(String value){phase=value;notifyChanged();}
    void checkCancelled()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("cancelled");}
    synchronized boolean begin(String language){if(busy)return false;busy=true;cancelled=false;phase="copy";error="";lastInstalled="";lastFailure=null;jobLanguage=language;transferred=0;total=-1;notifyChanged();return true;}
    void cancel(){cancelled=true;}
    void importUri(String language,Uri uri){importUri(language,uri,false);}
    void releaseGrant(Uri uri){try{context.getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}}
    void importUri(String language,Uri uri,boolean releaseGrant){if(!begin(language)){if(releaseGrant)releaseGrant(uri);return;}worker.execute(()->{try{String name=uri.getLastPathSegment();try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){int n=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=cursor.getColumnIndex(OpenableColumns.SIZE);if(n>=0)name=cursor.getString(n);if(s>=0&&!cursor.isNull(s))total=cursor.getLong(s);}}catch(Exception ignored){}final String title=name==null||name.trim().isEmpty()?"voice":name;runImport(language,title,()->context.getContentResolver().openInputStream(uri),null);}finally{if(releaseGrant)releaseGrant(uri);}});}
    static URL address(String raw)throws IOException{try{URL url=new URL(raw.trim());if(!url.getProtocol().equalsIgnoreCase("https")||url.getHost().isEmpty()||url.getUserInfo()!=null)throw new IOException("url");return url;}catch(MalformedURLException error){throw new IOException("url",error);}}
    void download(String language,String raw){if(!begin(language))return;worker.execute(()->{HttpURLConnection connection=null;try{URL url=address(raw);phase("download");for(int redirects=0;redirects<6;redirects++){checkCancelled();connection=connections.open(url);connection.setConnectTimeout(20000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("User-Agent","VoiceTxt/1.0");int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("download");URL next=new URL(url,location);address(next.toString());connection.disconnect();connection=null;url=next;continue;}if(code!=200)throw new IOException("download");total=connection.getContentLengthLong();final HttpURLConnection response=connection;String title=Uri.parse(raw).getLastPathSegment();runImport(language,title==null?"voice":title,response::getInputStream,response);connection=null;return;}throw new IOException("download");}catch(Exception failure){finishFailure(failure);}finally{if(connection!=null)connection.disconnect();}});}
    interface Source {InputStream open()throws Exception;}
    void runImport(String language,String title,Source source,HttpURLConnection connection){String id=UUID.randomUUID().toString();File root=new File(staging,id),archive=new File(staging,id+".download");try{
        if(total>VoiceArchive.MAX_ARCHIVE)throw new IOException("large");if(staging.getUsableSpace()<128L*1024*1024)throw new IOException("storage");if(!root.mkdirs())throw new IOException("storage");MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=source.open();FileOutputStream output=new FileOutputStream(archive)){if(input==null)throw new IOException("invalid");byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1){checkCancelled();transferred+=n;if(transferred>VoiceArchive.MAX_ARCHIVE)throw new IOException("large");output.write(buffer,0,n);digest.update(buffer,0,n);if(SystemClock.elapsedRealtime()-lastNotice>200){lastNotice=SystemClock.elapsedRealtime();notifyChanged();}}output.getFD().sync();}
        if(total>=0&&transferred!=total)throw new IOException("download");String hash=hex(digest.digest());synchronized(this){for(VoiceModel pack:models.values())if(pack.digest.equals(hash)){if(!language.isEmpty()&&!pack.supports(language))throw new IOException("language");throw new IOException("duplicate");}}
        checkCancelled();phase("extract");new VoiceArchive(root,this::checkCancelled).extract(archive);checkCancelled();phase("validate");VoiceModel pack=VoiceModel.inspect(root,title.replaceFirst("(?i)\\.(tar\\.bz2|tar\\.gz|tgz|zip|tar)$",""),language);pack.digest=hash;String detected=language.isEmpty()?pack.languages.iterator().next():language;jobLanguage=detected;write(pack);probe(pack,detected);checkCancelled();write(pack);
        File destination=new File(installed,id);if(!root.renameTo(destination))throw new IOException("storage");pack.directory=destination;synchronized(this){models.put(id,pack);}lastInstalled=id;phase="done";error="";
    }catch(Exception failure){lastFailure=failure;error=errorCode(failure);if(connection!=null&&phase.equals("download")&&error.equals("invalid"))error="download";phase="failed";}finally{if(connection!=null)connection.disconnect();try{deleteTree(root,staging);}catch(IOException ignored){}archive.delete();busy=false;notifyChanged();}}
    static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();}
    static String errorCode(Exception failure){if(failure instanceof SocketTimeoutException||failure instanceof UnknownHostException||failure instanceof ConnectException||failure instanceof javax.net.ssl.SSLException)return "download";for(Throwable cause=failure;cause!=null;cause=cause.getCause()){String message=cause.getMessage();if(message!=null&&message.contains("Type Error:")&&message.contains("tensor(float16)"))return "model_graph";}String code=failure.getMessage();return Arrays.asList("cancelled","url","download","large","storage","invalid","archive","incomplete","unsupported","unknown_language","language","duplicate","in_use").contains(code)?code:"invalid";}
    void finishFailure(Exception failure){error=errorCode(failure);phase="failed";busy=false;notifyChanged();}
    static void write(VoiceModel pack)throws Exception{try(FileOutputStream output=new FileOutputStream(new File(pack.directory,"installed.json"))){output.write(pack.json().toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();}}
    void probe(VoiceModel pack,String language)throws Exception{
        CountDownLatch completed=new CountDownLatch(1);final Bundle[] result={null};final boolean[] bound={false};
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){try{Messenger remote=new Messenger(binder);Message message=Message.obtain();Bundle input=new Bundle();input.putString("id",pack.id);input.putString("language",language);message.setData(input);message.replyTo=new Messenger(new Handler(Looper.getMainLooper(),reply->{result[0]=reply.getData();completed.countDown();return true;}));remote.send(message);}catch(RemoteException error){completed.countDown();}}public void onServiceDisconnected(ComponentName name){completed.countDown();}public void onNullBinding(ComponentName name){completed.countDown();}public void onBindingDied(ComponentName name){completed.countDown();}};
        try{bound[0]=context.bindService(new Intent(context,VoiceValidationService.class),connection,Context.BIND_AUTO_CREATE);if(!bound[0])throw new IOException("invalid");long until=SystemClock.elapsedRealtime()+90000;while(!completed.await(200,TimeUnit.MILLISECONDS)){checkCancelled();if(SystemClock.elapsedRealtime()>until)throw new IOException("invalid");}checkCancelled();if(result[0]==null||!result[0].getBoolean("ok"))throw new IOException("invalid",new IOException(result[0]==null?"Validator disconnected":result[0].getString("detail","Native probe failed")));pack.sampleRate=result[0].getInt("sampleRate");pack.speakers=result[0].getInt("speakers");}finally{if(bound[0])context.unbindService(connection);}
    }
    void delete(String id){if(!begin(""))return;phase="delete";worker.execute(()->{try{File trash=new File(staging,UUID.randomUUID().toString());synchronized(this){if(leases.containsKey(id))throw new IOException("in_use");VoiceModel pack=models.get(id);if(pack==null)throw new IOException("invalid");if(!pack.directory.renameTo(trash))throw new IOException("storage");models.remove(id);}deleteTree(trash,staging);phase="deleted";error="";}catch(Exception failure){phase="failed";error=errorCode(failure);}finally{busy=false;notifyChanged();}});}
    static void deleteTree(File file,File parent)throws IOException{String root=parent.getCanonicalPath()+File.separator;if(!file.getCanonicalPath().startsWith(root))throw new IOException("storage");if(!file.exists())return;File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child,parent);if(!file.delete())throw new IOException("storage");}
}
