package com.voicetxt.reader;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.Voice;
import android.view.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class VoicePickerRegressionTest extends Instrumentation {
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static View find(View view,String description){if(description.equals(String.valueOf(view.getContentDescription())))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=find(group.getChildAt(i),description);if(match!=null)return match;}}return null;}
    static int selectedCards(View view){int count=String.valueOf(view.getContentDescription()).startsWith("选择 ")&&view.isSelected()?1:0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=selectedCards(group.getChildAt(i));}return count;}
    @Override public void onStart(){Bundle result=new Bundle();SystemSpeaker system=new SystemSpeaker(getTargetContext());Activity activity=null;try{
        Voice cn=new Voice("same-name",Locale.CHINA,300,300,false,Collections.emptySet());Voice tw=new Voice("same-name",Locale.TAIWAN,300,300,false,Collections.emptySet());check(!SystemSpeaker.voiceKey("engine",cn).equals(SystemSpeaker.voiceKey("engine",tw)),"same name different locale collided");
        CountDownLatch ready=new CountDownLatch(1);system.init(()->{if(system.initialized)ready.countDown();});check(ready.await(30,TimeUnit.SECONDS),"system voices timed out");HashSet<String> unique=new HashSet<>();int honor=0;HashSet<String> honorNames=new HashSet<>();StringBuilder locales=new StringBuilder();
        for(SystemSpeaker.Option option:system.options){check(unique.add(option.key),"duplicate system key");check(system.find(option.key)==option,"canonical lookup mismatch");if(option.engine.contains("hihonor")){honor++;honorNames.add(option.voice.getName());locales.append(option.voice.getLocale().toLanguageTag()).append(' ');}}
        result.putInt("uniqueSystemVoices",unique.size());result.putInt("honorRecords",honor);result.putInt("honorDistinctNames",honorNames.size());result.putString("honorLocales",locales.toString());
        Intent intent=new Intent(getTargetContext(),ShelfActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=startActivitySync(intent);final Activity hostActivity=activity;
        AtomicInteger selections=new AtomicInteger(),previews=new AtomicInteger();AtomicReference<String> selected=new AtomicReference<>("local:1000");AtomicReference<Throwable> failure=new AtomicReference<>();ArrayList<VoicePicker.Item> items=new ArrayList<>();items.add(new VoicePicker.Item("local:1000","测试女声","离线","voice_avatar_female",0));items.add(new VoicePicker.Item("local:2010","测试男声","离线","voice_avatar_male1",0));
        final VoicePicker[] picker=new VoicePicker[1];runOnMainSync(()->{try{picker[0]=new VoicePicker(hostActivity,false,new VoicePicker.Host(){public List<VoicePicker.Item> items(){return items;}public String selectedKey(){return selected.get();}public void select(VoicePicker.Item item){selections.incrementAndGet();selected.set(item.key);}public void preview(VoicePicker.Item item){previews.incrementAndGet();}});picker[0].show();}catch(Throwable e){failure.set(e);}});waitForIdleSync();
        runOnMainSync(()->{try{View card=find(picker[0].panel,"选择 测试男声");check(card!=null,"selection card missing");card.performClick();check(selections.get()==1,"card did not select exactly once");check(previews.get()==0,"selection incorrectly started preview");check(selectedCards(picker[0].grid)==1,"more than one selected card");View tick=find(picker[0].panel,"当前音色");check(tick!=null,"selection mark missing");tick.performClick();check(previews.get()==0,"selection mark incorrectly previewed");check(selections.get()==2,"selection mark did not select");View play=find(picker[0].panel,"试听 测试女声");check(play!=null,"avatar preview button missing");play.performClick();check(previews.get()==1,"preview button failed");check(selections.get()==2,"preview changed selection");check("local:2010".equals(selected.get()),"preview replaced selected voice");check(selectedCards(picker[0].grid)==1,"preview corrupted selection");}catch(Throwable e){failure.set(e);}finally{if(picker[0]!=null)picker[0].dismiss();}});
        if(failure.get()!=null)throw new AssertionError("voice picker regression",failure.get());result.putString("result","PASS: unique system identities; single selection; selection is silent; independent avatar preview preserves selection");finish(-1,result);
    }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}finally{system.close();if(activity!=null){final Activity a=activity;runOnMainSync(a::finish);}}}
}
