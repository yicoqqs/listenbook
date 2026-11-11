package com.voicetxt.reader;

import android.app.*;import android.content.*;import android.os.*;import android.speech.tts.*;import java.util.*;

/** Availability filtering, legacy Chinese compatibility and live UI refresh. */
public class AvailableVoiceLanguagesTest extends VoiceWarmupTest {
    public void onStart(){Bundle result=new Bundle();OfflineVoicesActivity activity=null;ReadingLanguagePicker[] picker={null};VoicePackages store=VoicePackages.get(getTargetContext());String fixtureId="availability_qa_not_installed";SharedPreferences migration=getTargetContext().getSharedPreferences("voice_language_migration_qa",0);boolean success=false;
        try{
            check(VoiceLanguages.CODES.length==14,"Duplicate Chinese category");
            for(String code:VoiceLanguages.CODES){check(VoiceModel.languages(code).contains(code),"Metadata code "+code);}
            check(VoiceModel.languages("Chinese English Japanese Korean French German Spanish Portuguese Russian Arabic Hindi Indonesian Vietnamese Thai").size()==14,"Metadata name mapping shifted");
            check(VoiceModel.languages("zh_Hant zh_TW zh_Hans zh_CN").equals(Collections.singleton("zh")),"Chinese metadata not merged");
            VoiceModel legacy=new VoiceModel();legacy.languages.add("zh-Hant");check(legacy.supports("zh")&&legacy.supports("zh-Hant"),"Existing traditional pack unusable");check(VoiceLanguages.matches("zh",Locale.TAIWAN)&&VoiceLanguages.matches("zh",Locale.CHINA),"System Chinese locales not merged");
            migration.edit().clear().putString("readingLanguage","zh-Hant").putString("voice_zh-Hant","pack:old:3").putString("chineseVoiceKey","old-choice").putInt("pack_speaker_old_zh-Hant",3).commit();VoiceLanguages.migrate(migration);check(migration.getString("readingLanguage","").equals("zh")&&migration.getString("chineseVoiceKey","").equals("pack:old:3")&&migration.getInt("pack_speaker_old_zh",-1)==3,"Migration lost exact selected speaker");migration.edit().putString("chineseVoiceKey","new-choice").commit();VoiceLanguages.migrate(migration);check(migration.getString("chineseVoiceKey","").equals("new-choice"),"Migration repeated");
            check(!SystemSpeaker.offlineUsable(new Voice("network",Locale.FRENCH,300,300,true,Collections.emptySet())),"Online voice admitted");check(!SystemSpeaker.offlineUsable(new Voice("missing",Locale.FRENCH,300,300,false,Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))),"Not-installed voice admitted");
            activity=(OfflineVoicesActivity)startActivitySync(new Intent(getTargetContext(),OfflineVoicesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final OfflineVoicesActivity a=activity;
            await(()->a.system!=null&&a.system.initialized,"System voices not initialized",25000);
            ui(()->{
                List<String> available=VoiceLanguages.available(a.system,store);check(a.headers.keySet().equals(new LinkedHashSet<>(available)),"Management shows empty groups");check(!a.headers.containsKey("zh-Hant"),"Traditional group remains");for(String code:available)check(!a.items(code).isEmpty(),"Empty language "+code);
                picker[0]=new ReadingLanguagePicker(a,false,new ReadingLanguagePicker.Host(){public String selected(){return "";}public boolean modelsReady(){return true;}public List<String> languages(){return VoiceLanguages.available(a.system,store);}public void select(String code){}public void help(){}});picker[0].show();List<String> expected=new ArrayList<>();expected.add("");expected.addAll(available);check(new ArrayList<>(picker[0].choices.keySet()).equals(expected),"Reading targets differ from available voices");
                String absent=null;for(String code:VoiceLanguages.CODES)if(!available.contains(code)){absent=code;break;}check(absent!=null,"No absent language for fixture");final String language=absent;VoiceModel fixture=new VoiceModel();fixture.id=fixtureId;fixture.name="Availability QA";fixture.speakers=1;fixture.languages.add(language);synchronized(store){check(!store.models.containsKey(fixtureId),"Fixture collision");store.models.put(fixtureId,fixture);}try{a.render();picker[0].refresh();check(a.headers.containsKey(language)&&picker[0].choices.containsKey(language),"Imported language not added");}finally{synchronized(store){store.models.remove(fixtureId);}}a.render();picker[0].refresh();check(!a.headers.containsKey(language)&&!picker[0].choices.containsKey(language),"Deleted final pack language remains");check(picker[0].choices.containsKey(""),"Original reading missing");result.putString("availableLanguages",available.toString());picker[0].dismiss();picker[0]=null;
            });
            result.putString("result","PASS: offline system/import union; unavailable groups hidden; online/missing voices excluded; live add/remove targets; one Chinese category; legacy packs and exact selected speaker preserved; original reading retained");success=true;
        }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{ui(()->{if(picker[0]!=null)picker[0].dismiss();synchronized(store){store.models.remove(fixtureId);}});if(activity!=null){final Activity a=activity;ui(a::finish);}migration.edit().clear().commit();}
        finish(success?-1:0,result);
    }
}
