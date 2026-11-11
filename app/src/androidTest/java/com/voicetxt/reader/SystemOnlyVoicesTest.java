package com.voicetxt.reader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.tts.Voice;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class SystemOnlyVoicesTest extends Instrumentation {
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void ui(Runnable action){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
    void await(java.util.function.BooleanSupplier condition,String message,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){AtomicBoolean ok=new AtomicBoolean();ui(()->ok.set(condition.getAsBoolean()));if(ok.get())return;Thread.sleep(75);}throw new AssertionError(message);}
    void restore(SharedPreferences prefs,Map<String,?> saved){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);}edit.commit();}
    public void onCreate(Bundle state){super.onCreate(state);start();}
    public void onStart(){Bundle result=new Bundle();MainActivity activity=null;OfflineVoicesActivity manager=null;Context context=getTargetContext();SharedPreferences prefs=context.getSharedPreferences("reading_settings",0);Map<String,?> saved=new HashMap<>(prefs.getAll());boolean passed=false;
        try{
            check(!context.getSystemService(KeyguardManager.class).isKeyguardLocked(),"Phone must be unlocked");
            check(!SystemSpeaker.allowedEngine("com.google.android.tts"),"Google engine allowed");
            check(SystemSpeaker.allowedEngine("com.hihonor.speechengine"),"Honor engine excluded");
            for(String asset:context.getAssets().list(""))check(!asset.equals("fastmale")&&!asset.equals("matcha"),"Bundled models still packaged");
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).putExtra("book_id","system_voice_upgrade_qa").putExtra("book_name","系统音色验证").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final MainActivity a=activity;
            await(()->a.system!=null&&a.system.initialized,"System engines not ready",30000);
            ui(()->{
                check(a.offline==null&&!a.offlineReady,"Native voice initialized");
                check(!a.system.engineLabels.isEmpty(),"No OEM engine discovered on Honor");
                for(SystemSpeaker.Option option:a.system.options)check(SystemSpeaker.allowedEngine(option.engine),"Google voice leaked");
                a.readingLanguage="";a.systemVoiceKey="com.google.android.tts|stale";
                check(a.voiceLanguage().equals(VoiceLanguages.systemLanguage(a)),"Original mode does not follow system language");
                check(a.ensureReadingVoice(),"System-language voice missing on test phone");
                check(VoiceLanguages.matches(a.voiceLanguage(),a.system.find(a.systemVoiceKey).voice.getLocale()),"Default language mismatch");
                a.chooseVoice();check(a.voicePicker.tabs.isEmpty(),"Categorized tabs remain");
                for(VoicePicker.Item item:a.voicePicker.host.items())check(item.key.startsWith("system:")&&!item.key.contains("google"),"Non-system voice listed");
                VoicePicker.Item item=a.voicePicker.host.items().get(0);a.voicePicker.host.select(item);check(a.previewKey.isEmpty()&&!a.playing,"Selection started audio");
                a.voicePicker.host.preview(item);check(a.previewKey.equals(item.key),"Preview not started");a.voicePicker.host.preview(item);check(a.previewKey.isEmpty(),"Preview did not pause");
                a.voicePicker.dismiss();
            });
            // Verify all supported language filters with synthetic metadata, without speaking fake voices.
            ui(()->{
                ArrayList<SystemSpeaker.Option> original=new ArrayList<>(a.system.options);SystemSpeaker.Option source=original.get(0);
                try{
                    for(String language:VoiceLanguages.CODES)a.system.options.add(new SystemSpeaker.Option(source.engine,source.engineLabel,new Voice("qa-"+language,Locale.forLanguageTag(language.equals("zh")?"zh-CN":language),300,300,false,Collections.emptySet()),source.tts,99));
                    for(String language:VoiceLanguages.CODES){a.readingLanguage=language;a.systemVoiceKey="com.google.android.tts|stale";check(a.ensureReadingVoice(),"No fixture voice for "+language);a.chooseVoice();check(a.voicePicker.tabs.isEmpty(),"Tabs returned");check(!a.voicePicker.host.items().isEmpty(),"Empty fixture list: "+language);for(VoicePicker.Item item:a.voicePicker.host.items()){SystemSpeaker.Option option=a.system.find(item.key.substring(7));check(VoiceLanguages.matches(language,option.voice.getLocale()),"Wrong-language card in "+language);}a.voicePicker.dismiss();}
                }finally{a.system.options.clear();a.system.options.addAll(original);}
                a.readingLanguage="ja";a.systemVoiceKey="com.google.android.tts|stale";
                boolean hasJapanese=a.system.offlineVoice("ja","")!=null;check(a.ensureReadingVoice()==hasJapanese,"Missing language silently fell back");
                a.readingLanguage="zh";a.systemVoiceKey="";check(a.ensureReadingVoice(),"Honor Chinese unavailable");
                a.text="第一章 测试\n微风轻轻吹过山间，书页慢慢翻动。我们沿着小路前行，欣赏四周的风景。";a.chapters=MainActivity.parse(a.text);a.position=0;a.readingLanguage="";a.loopPlayback=false;a.rate=1;a.showPosition(true);a.startReading();
            });
            await(()->a.playing&&a.latestHighlightEnd>0&&a.latestStatus.startsWith(a.getString(R.string.ui_74)),"Actual OEM reading did not start",20000);
            ui(()->{
                String language=a.readingLanguage,key=a.systemVoiceKey;int generation=a.generation;
                Map<String,?> before=new HashMap<>(prefs.getAll());
                LinkedHashMap<String,android.speech.tts.TextToSpeech> clients=new LinkedHashMap<>(a.system.engineClients);
                try{
                    a.system.engineClients.clear(); // Simulate absent voice packages, without deleting user data.
                    for(String target:new String[]{"zh","en","ja"}){
                        a.selectReadingLanguage(target,true);
                        check(language.equals(a.readingLanguage)&&key.equals(a.systemVoiceKey),"Missing voice changed language/voice: "+target);
                        check(a.playing&&a.generation==generation,"Rejected choice interrupted playback");
                        check(before.equals(prefs.getAll()),"Rejected choice modified preferences");
                    }
                    a.chooseReadingLanguage();a.languagePicker.choices.get("ja").performClick();
                    check(a.languagePicker!=null&&a.languagePicker.isShowing(),"Rejected choice dismissed picker");
                    check(a.languagePicker.choices.get(language).isSelected(),"Rejected choice changed selection mark");
                    a.languagePicker.dismiss();
                    a.system.initialized=false;a.selectReadingLanguage("en",true);
                    check(language.equals(a.readingLanguage)&&a.playing&&a.generation==generation,"Initializing engine changed state");
                }finally{a.system.initialized=true;a.system.engineClients.putAll(clients);a.system.refreshOptions();}
                // Available Chinese package must still be selectable after a failed attempt.
                a.selectReadingLanguage("zh",false);
                check(a.readingLanguage.equals("zh")&&prefs.getString("readingLanguage","").equals("zh"),"Available language could not be selected");
                check(VoiceLanguages.matches("zh",a.system.find(a.systemVoiceKey).voice.getLocale()),"Accepted language has wrong voice");
            });
            ui(()->{check(a.system.find(a.systemVoiceKey)!=null,"Reading voice missing");a.stop();a.chooseVoice();});
            Thread.sleep(400);android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();if(shot!=null){try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"system-only-picker.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
            ui(()->{a.voicePicker.dismiss();a.finish();});activity=null;
            manager=(OfflineVoicesActivity)startActivitySync(new Intent(context,OfflineVoicesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final OfflineVoicesActivity m=manager;
            await(()->m.system!=null&&m.system.initialized,"Manager not ready",30000);
            ui(()->{for(String language:VoiceLanguages.CODES)for(OfflineVoicesActivity.Item item:m.items(language))check(item.option!=null&&SystemSpeaker.allowedEngine(item.option.engine),"Manager exposes removed voice");check(m.offline==null,"Manager initialized native voice");});
            check(!new File(context.getFilesDir(),"icefall-aishell3-v1").exists()&&!new File(context.getFilesDir(),"matcha-baker-v1").exists(),"Old extracted models not cleaned");
            result.putString("result","PASS: no packaged models; Google excluded before binding; OEM discovery; system-language default; 15 language filters; one unified picker; silent selection; preview pause; missing-language no fallback; actual Honor speech/highlight; manager excludes native/Google; old extracted models cleaned; unavailable zh/en/ja rejected before changing language, voice or preferences; rejected choice preserves playing state and open picker; initializing engine rejected; available Chinese accepted after rejection");passed=true;
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null){final MainActivity a=activity;ui(()->{a.stop();a.finish();});}if(manager!=null){final OfflineVoicesActivity m=manager;ui(m::finish);}waitForIdleSync();restore(prefs,saved);context.getSharedPreferences("reader_system_voice_upgrade_qa",0).edit().clear().commit();new File(context.getFilesDir(),"book_system_voice_upgrade_qa.txt.position").delete();}
        finish(passed?-1:0,result);
    }
}
