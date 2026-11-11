package com.voicetxt.reader;

import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.*;import java.util.concurrent.atomic.*;

/** Regression: Chinese clauses cannot become Burmese because of a statistical guess. */
public class OriginalLanguageMisclassificationTest extends VoiceWarmupTest {
 boolean brokenMyanmar;
 @Override public void onCreate(Bundle args){
  brokenMyanmar=args!=null&&"true".equals(args.getString("brokenMyanmar"));
  super.onCreate(args);
 }
 void verifySynthesisSkip() throws Exception {
  OfflineSpeaker speaker=new OfflineSpeaker(getTargetContext()){
   @Override com.k2fsa.sherpa.onnx.GeneratedAudio generate(String value,int voice,float rate,CancelCallback callback){
    if(value.equals("unsupported"))throw new IllegalArgumentException("QA synthesis failure");
    return new com.k2fsa.sherpa.onnx.GeneratedAudio(new float[8000],8000);
   }
  };
  java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
  AtomicReference<Throwable> failure=new AtomicReference<>();
  List<Integer> played=new ArrayList<>();
  try{
   speaker.speakSequence(Arrays.asList("first","unsupported","last"),0,1f,new OfflineSpeaker.SequenceListener(){
    public void buffering(int ready){}
    public void sentence(int index){played.add(index);}
    public void done(){done.countDown();}
    public void failed(Throwable error){failure.set(error);done.countDown();}
   });
   check(done.await(15,java.util.concurrent.TimeUnit.SECONDS)&&failure.get()==null
       &&played.equals(Arrays.asList(0,2)),"Synthesis failure interrupted playback: "+failure.get()+" / "+played);
  }finally{speaker.close();}
 }
 public void onStart(){Bundle result=new Bundle();boolean success=false;OfflineTranslation detection=null;MainActivity reader=null;SharedPreferences prefs=getTargetContext().getSharedPreferences("reading_settings",0);Map<String,?> saved=new HashMap<>(prefs.getAll());try{
  AtomicInteger guesses=new AtomicInteger();OriginalVoicePlanner falseMyanmar=new OriginalVoicePlanner((OriginalVoicePlanner.Detector)text->{guesses.incrementAndGet();return Arrays.asList(new OriginalVoicePlanner.Guess("my",.999f),new OriginalVoicePlanner.Guess("en",.85f));});
  String[] chinese={"嗯。","哼！","啊……","姜夫人，别来无恙。","夜幕低垂，群山间的雷鸣轰响终于渐渐平息。","第1007章 姜夫人，别来无恙","三道庞大的妖魔虚影悄无声息地悬在少女的身后。","CPU 性能不错，APP 阅读也很流畅。","𠀀这个字属于扩展汉字。","繁體中文也應該穩定朗讀。","12345，……","CPU!"};for(String line:chinese)check(falseMyanmar.detect(line,"zh").equals("zh"),"Chinese/neutral clause switched language: "+line);check(guesses.get()==0,"Unambiguous Chinese was sent to statistical detection");
  check(falseMyanmar.detect("道","ja").equals("ja"),"Japanese context lost");check(falseMyanmar.detect("道","zh").equals("zh"),"Context-sensitive cache leaked Japanese into Chinese");check(falseMyanmar.detect("The weather is beautiful today and we are reading a wonderful book together.","zh").equals("en"),"Script-compatible candidate not selected");
  OriginalVoicePlanner realMyanmar=new OriginalVoicePlanner((OriginalVoicePlanner.Detector)text->Collections.singletonList(new OriginalVoicePlanner.Guess("my",.99f)));String burmese="မြန်မာစာကို ဖတ်ရှုနေပါသည်။";check(realMyanmar.detect(burmese,"zh").equals("my"),"Actual Burmese was disguised as Chinese");check(!OriginalLanguageRules.compatible("中文内容","my")&&!OriginalLanguageRules.compatible("English words","my")&&OriginalLanguageRules.compatible(burmese,"my"),"Myanmar script guard invalid");
  OriginalVoicePlanner uncertainLatin=new OriginalVoicePlanner((OriginalVoicePlanner.Detector)text->Collections.singletonList(new OriginalVoicePlanner.Guess("sv",.35f)));check(uncertainLatin.detect("Hello.","en").equals("en"),"Weak short-language guess overrode context");
  detection=new OfflineTranslation(getTargetContext());OriginalVoicePlanner real=new OriginalVoicePlanner(detection.identifier);Map<String,String> detected=new LinkedHashMap<>();for(String code:VoiceLanguages.CODES){String actual=real.detect(VoiceLanguages.sample(code),"zh");detected.put(code,actual);check(actual.equals(code),"Real language detection regressed for "+code+": "+actual);}result.putString("languages",detected.toString());check(real.detect("繁體中文同樣可以正常閱讀，不會被誤認為其他語言。","zh").equals("zh"),"Traditional Chinese misidentified");check(real.detect("Det är vackert väder idag. Vi läser en intressant bok tillsammans.","en").equals("sv"),"Swedish detection regressed");
  prefs.edit().putString("readingLanguage","").commit();reader=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("book_id","original_language_guard_qa").putExtra("book_name","中文短句连续朗读验证").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final MainActivity a=reader;await(()->a.system!=null&&a.system.initialized,"System voice initialization failed",30000);ui(()->{a.stop();a.readingLanguage="";a.originalVoiceLanguage="zh";a.originalPlanner=falseMyanmar;a.text="第一章 测试\n嗯。\n姜夫人，别来无恙。\n夜幕低垂，群山间的雷鸣终于渐渐平息。";a.chapters=MainActivity.parse(a.text);a.position=0;a.rate=2f;a.loopPlayback=false;a.showPosition(true);a.startReading();});await(()->a.completedBooks>0,"Chinese reading stopped before completion: "+a.latestStatus,45000);ui(()->{check(!a.originalVoiceLanguage.equals("my"),"Playback selected Myanmar");check(a.originalPlanner==falseMyanmar,"Unexpected planner");a.stop();});
  String separator="----------";
  check(!TranslationText.speakable(TranslationText.speechText(separator)),"Separator reached TTS");
  String inline="风里带着腥气----------破败的荒野。";
  check(TranslationText.speechText(inline).equals(inline.replace('-', ' ')),"Inline separator changed text offsets");
  check(TranslationText.speechText("well-known 1-2 他说——走吧").equals("well-known 1-2 他说——走吧"),
      "Ordinary hyphens and dashes were removed");
  ui(()->{
   a.text="彦祖亦菲打卡处。\n\n"+separator+"\n\n风里带着腥气。\n"+inline;
   a.chapters=MainActivity.parse(a.text);
   a.position=0;
   a.completedBooks=0;
   a.showPosition(true);
   a.startReading();
  });
  await(()->a.completedBooks==1,"Dash separator interrupted reading: "+a.latestStatus,45000);
  check(a.startupTiming.preparedClips==3,"Unexpected audio segment count around separator");
  result.putBoolean("dashSeparatorPlaybackVerified",true);
  AtomicBoolean missingMyanmar=new AtomicBoolean();
  ui(()->{
   a.originalVoiceLanguage="my";
   a.systemVoiceKey="";
   missingMyanmar.set(!a.ensureReadingVoice());
   if(brokenMyanmar)check(!missingMyanmar.get(),"Broken Burmese model fixture is missing");
   if(!missingMyanmar.get()&&!brokenMyanmar)return;
   a.originalPlanner=realMyanmar;
   a.text=burmese+"။\n这句话应该在跳过缅甸语后继续朗读。";
   a.chapters=MainActivity.parse(a.text);
   a.position=0;
   a.completedBooks=0;
   a.showPosition(true);
   a.startReading();
  });
  if(missingMyanmar.get()||brokenMyanmar)await(()->a.completedBooks==1&&"zh".equals(a.originalVoiceLanguage),
      "Playback did not continue after the missing Burmese voice: "+a.latestStatus,45000);
  result.putBoolean("missingVoicePlaybackVerified",missingMyanmar.get());
  result.putBoolean("brokenVoicePlaybackVerified",brokenMyanmar);
  verifySynthesisSkip();
  result.putBoolean("synthesisFailureSkipVerified",true);
  result.putString("result","PASS: forced high-confidence Burmese guesses cannot override Chinese; short/neutral/extended/traditional Chinese continuous playback completes; context-aware cache; actual Burmese preserved; Latin weak guesses retain context; all 14 canonical UI voice languages plus traditional Chinese and Swedish detected");success=true;
 }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}finally{if(reader!=null){final MainActivity a=reader;ui(()->{a.stop();a.finish();});}waitForIdleSync();restore(prefs,saved);if(detection!=null)detection.close();getTargetContext().getSharedPreferences("reader_original_language_guard_qa",0).edit().clear().commit();new File(getTargetContext().getFilesDir(),"book_original_language_guard_qa.txt.position").delete();}finish(success?-1:0,result);}
}
