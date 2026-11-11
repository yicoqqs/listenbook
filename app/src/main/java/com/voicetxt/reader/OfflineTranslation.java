package com.voicetxt.reader;

import android.content.Context;
import com.google.android.gms.tasks.*;


import com.google.mlkit.nl.languageid.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Direct HY-MT translation; source detection, inference and caching stay on device. */
final class OfflineTranslation implements AutoCloseable {
    final Context context;final File cache;
    final LanguageIdentifier identifier=LanguageIdentification.getClient(new LanguageIdentificationOptions.Builder().setConfidenceThreshold(.25f).build());

    final ExecutorService analysis=Executors.newSingleThreadExecutor();
    volatile Set<String> installed=Collections.emptySet();volatile boolean ready,closed;
    static final Object CACHE_LOCK=new Object();
    static final java.util.concurrent.atomic.AtomicInteger translating=new java.util.concurrent.atomic.AtomicInteger();
    static final class MissingModels extends IOException {final Set<String> languages;MissingModels(Set<String> languages){super("Missing offline translation models: "+languages);this.languages=Collections.unmodifiableSet(new LinkedHashSet<>(languages));}}
    OfflineTranslation(Context context){this.context=context.getApplicationContext();cache=new File(context.getCacheDir(),"translations-hymt15-stq1-v1");cache.mkdirs();}
    Task<Boolean> available(){ready=HunyuanModels.get(context).ready();installed=ready?TranslationText.SUPPORTED:Collections.emptySet();return Tasks.forResult(ready);}
    interface DownloadProgress {void update(int done,int total,String language);}
    Task<Void> prepare(){return prepare(Collections.singleton("zh"));}
    Task<Void> prepare(Set<String> languages){return prepare(languages,()->!closed,(done,total,language)->{});}
    Task<Void> prepare(Set<String> languages,java.util.function.BooleanSupplier continuing,DownloadProgress progress){if(closed||!continuing.getAsBoolean())return Tasks.forException(new CancellationException());progress.update(0,1,"HY-MT1.5");return HunyuanModels.get(context).download().continueWith(task->{if(!task.isSuccessful())throw failure(task);progress.update(1,1,"HY-MT1.5");available();return null;});}
    static Exception failure(Task<?> task){Exception error=task.getException();return error==null?new CancellationException():error;}
    static String normalize(String code){return code==null?"und":VoiceLanguages.canonical(code);}

    Task<String> translate(String source,String target,String text){return translate(source,target,text,this,()->!closed);}
    Task<String> translate(String source,String target,String text,Object owner,java.util.function.BooleanSupplier continuing){if(closed)return Tasks.forException(new CancellationException());translating.incrementAndGet();Task<String> task=HunyuanEngine.get(context).translate(source,target,text,owner,continuing);task.addOnCompleteListener(Runnable::run,result->translating.decrementAndGet());return task;}
    Session session(List<String> source,String language){return new Session(source,language,null,null);}
    Session session(List<String> source,String language,String original,List<Integer> offsets){return new Session(source,language,original,offsets);}
    interface Inspection {void ready();void failed(Throwable error);}
    void inspect(Session session,Inspection callback){analysis.execute(()->{try{session.await(available());Set<String> missing=session.missingUpcoming(1600);if(!missing.isEmpty())throw new MissingModels(missing);if(!session.cancelled&&!closed)callback.ready();}catch(Throwable error){if(!session.cancelled&&!closed)callback.failed(error);}});}
    static final class Unit {final String text,source,heading;Unit(String text,String source,String heading){this.text=text;this.source=source;this.heading=heading;}}
    final class Session extends AbstractList<String> {
        final List<String> source;final String target,original;final List<Integer> offsets;volatile boolean cancelled;
        final Map<Integer,List<Unit>> plans=Collections.synchronizedMap(new LinkedHashMap<Integer,List<Unit>>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<Integer,List<Unit>> entry){return size()>512;}});
        final Map<String,String> detected=Collections.synchronizedMap(new LinkedHashMap<String,String>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>256;}});
        Session(List<String> source,String target,String original,List<Integer> offsets){this.source=source;this.target=target;this.original=original;this.offsets=offsets;}
        public int size(){return source.size();}
        void check(){if(cancelled||closed||Thread.currentThread().isInterrupted())throw new CancellationException();}
        <T> T await(Task<T> task)throws Exception{CountDownLatch complete=new CountDownLatch(1);task.addOnCompleteListener(Runnable::run,result->complete.countDown());long deadline=android.os.SystemClock.elapsedRealtime()+60000;while(!task.isComplete()){check();if(android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException(context.getString(R.string.ui_136));complete.await(200,TimeUnit.MILLISECONDS);}check();if(!task.isSuccessful()){Exception error=task.getException();throw error==null?new IOException(context.getString(R.string.ui_138)):error;}return task.getResult();}
        String[] context(int index){String input=source.get(index);if(original==null||offsets==null)return new String[]{input,""};int offset=offsets.get(index);int start=original.lastIndexOf('\n',Math.max(0,offset-1))+1;int end=original.indexOf('\n',offset);if(end<0)end=original.length();String paragraph=original.substring(start,Math.min(end,start+1800));String hint="";int before=start-1;for(int line=0;line<4&&before>0;line++){int previous=original.lastIndexOf('\n',before-1)+1;String heading=original.substring(previous,before).trim();if(!heading.isEmpty()){TranslationText.Heading known=TranslationText.heading(heading);if(known!=null)hint=known.code;break;}before=previous-1;}return new String[]{paragraph,hint};}
        String identify(String text,String paragraph,String hint,int group)throws Exception{
            String sample=TranslationText.onlyGroup(paragraph,group).trim();if(sample.length()<text.trim().length())sample=text.trim();if(sample.length()>1800)sample=sample.substring(0,1800);String explicit=LanguageRules.explicit(group,sample,hint);if(!explicit.isEmpty())return explicit;String key=group+":"+sample;String saved=detected.get(key);if(saved!=null)return saved;
            List<IdentifiedLanguage> candidates=await(identifier.identifyPossibleLanguages(sample));String language="und";float confidence=0;for(IdentifiedLanguage candidate:candidates){String code=normalize(candidate.getLanguageTag());if(LanguageRules.compatible(group,code)&&candidate.getConfidence()>confidence){language=code;confidence=candidate.getConfidence();}}
            if(language.equals("und")){language=LanguageRules.fallback(group,sample);if(language.isEmpty())throw new IOException(context.getString(R.string.translation_unknown));}
            if(!TranslationText.SUPPORTED.contains(language))throw new IOException(context.getString(R.string.translation_unsupported,Locale.forLanguageTag(language).getDisplayLanguage(AppLanguage.locale(context))));
            // A mixed paragraph cannot make a Latin span Chinese or vice versa.
            if(group==2&&(language.equals("zh")||language.equals("ja")||language.equals("ko")))throw new IOException(context.getString(R.string.translation_unknown));
            detected.put(key,language);return language;
        }
        List<Unit> plan(int index)throws Exception{check();List<Unit> saved=plans.get(index);if(saved!=null)return saved;String input=source.get(index);TranslationText.Heading heading=TranslationText.heading(input);ArrayList<Unit> result=new ArrayList<>();if(heading!=null){result.add(new Unit(input,heading.code,heading.translated(target)));}else{
                String[] surrounding=context(index);for(TranslationText.Span span:TranslationText.split(input)){if(span.group==0){result.add(new Unit(span.text,target,null));continue;}String code=identify(span.text,surrounding[0],surrounding[1],span.group);result.add(new Unit(span.text,code,null));}
            }plans.put(index,result);return result;}
        Set<String> required(List<Unit> units){Set<String> needed=new LinkedHashSet<>();for(Unit unit:units)if(unit.heading==null&&!unit.source.equals(target)){if(!unit.source.equals("en"))needed.add(unit.source);if(!target.equals("en"))needed.add(target);}needed.removeAll(installed);return needed;}
        Set<String> missingUpcoming(int budget)throws Exception{try(CpuWork lease=new CpuWork(context)){Set<String> needed=new LinkedHashSet<>();int length=0;for(int i=0;i<size()&&(i==0||length<budget);i++){check();needed.addAll(required(plan(i)));length+=source.get(i).length();}return needed;}}
        public String get(int index){try(CpuWork lease=new CpuWork(context)){check();List<Unit> units=plan(index);Set<String> missing=required(units);if(!missing.isEmpty())throw new MissingModels(missing);StringBuilder output=new StringBuilder();for(Unit unit:units){check();if(unit.heading!=null){output.append(unit.heading);continue;}if(unit.source.equals(target)||!TranslationText.speakable(unit.text)){output.append(unit.text);continue;}String key=hash(HunyuanModels.REVISION+"\n"+unit.source+"\n"+target+"\n"+unit.text);File entry=new File(cache,key);String translated=readCache(entry);if(translated!=null)try{validate(unit.text,translated,target);}catch(IOException invalid){translated=null;}if(translated==null){translated=await(translate(unit.source,target,unit.text,this,()->!cancelled&&!closed));validate(unit.text,translated,target);check();writeCache(entry,translated);}if(target.equals("en")&&output.length()>0&&!translated.isEmpty()&&Character.isLetter(output.charAt(output.length()-1))&&Character.isLetter(translated.charAt(0)))output.append(' ');output.append(translated);}
                String result=output.toString();if(result.trim().isEmpty())throw new IOException(context.getString(R.string.ui_138));return result;
            }catch(CancellationException error){throw error;}catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}}
        void validate(String input,String output,String target)throws IOException{if(output==null||output.trim().isEmpty())throw new IOException(context.getString(R.string.ui_138));long letters=input.codePoints().filter(Character::isLetter).count();if(letters>=24&&(input.trim().equalsIgnoreCase(output.trim())||((target.equals("zh")||target.equals("ja"))&&!output.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN||(target.equals("ja")&&(Character.UnicodeScript.of(c)==Character.UnicodeScript.HIRAGANA||Character.UnicodeScript.of(c)==Character.UnicodeScript.KATAKANA))))))throw new IOException(context.getString(R.string.translation_unchanged));}
        void cancel(){cancelled=true;HunyuanEngine.get(context).cancel(this);}
    }
    static MissingModels missing(Throwable error){for(Throwable item=error;item!=null;item=item.getCause())if(item instanceof MissingModels)return (MissingModels)item;return null;}
    static String readCache(File entry){synchronized(CACHE_LOCK){try{return new String(new android.util.AtomicFile(entry).readFully(),StandardCharsets.UTF_8);}catch(IOException ignored){return null;}}}
    static void writeCache(File entry,String text){synchronized(CACHE_LOCK){android.util.AtomicFile file=new android.util.AtomicFile(entry);FileOutputStream out=null;try{out=file.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(IOException ignored){if(out!=null)file.failWrite(out);}}}
    static String hash(String text)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    @Override public synchronized void close(){closed=true;analysis.shutdownNow();identifier.close();HunyuanEngine.get(context).releaseIdle();}
}
