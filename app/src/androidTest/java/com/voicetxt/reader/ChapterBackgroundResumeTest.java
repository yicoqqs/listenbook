package com.voicetxt.reader;

import android.app.*;import android.content.*;import android.os.*;import android.text.Spanned;import android.text.style.ForegroundColorSpan;
import java.io.*;import java.util.*;import java.util.concurrent.atomic.*;

/** Real audio across chapters and repeated media-session resume while Home/screen-off. */
public class ChapterBackgroundResumeTest extends Instrumentation {
    android.content.SharedPreferences voicePrefs;String voicePreference,oldPreference;boolean hadPreference;
    void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    void ui(Runnable task){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable failure){error.set(failure);}});if(error.get()!=null)throw new AssertionError(error.get());}
    void await(java.util.function.BooleanSupplier test,String message,long timeout)throws Exception{long until=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<until){AtomicBoolean ready=new AtomicBoolean();ui(()->ready.set(test.getAsBoolean()));if(ready.get())return;Thread.sleep(80);}throw new AssertionError(message);}
    void shell(String command)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))){byte[] bytes=new byte[512];while(in.read(bytes)!=-1){}}Thread.sleep(400);}
    void progress(String message){Bundle b=new Bundle();b.putString("stage",message);sendStatus(1,b);}
    String fixture(){StringBuilder text=new StringBuilder("第一章 出发\n山间微风吹来。\n第二章 山谷\n");for(int i=0;i<35;i++)text.append("我们沿着小路走进山谷，静静听着远处的流水声。\n");return text.toString();}
    void book(MainActivity a,String key){a.stop();a.readingLanguage="";a.systemVoiceKey=key;voicePrefs.edit().putString(voicePreference,key).commit();a.text=fixture();a.chapters=MainActivity.parse(a.text);check(a.chapters.size()==2,"Fixture chapters");a.position=a.chapters.get(0).start;a.loopPlayback=false;a.rate=1.8f;a.resetReadingFollow();a.showPosition(true);a.startReading();check(key.equals(a.systemVoiceKey),"Saved preference replaced requested test voice");}
    void pauseResume(MainActivity a,boolean screenOff)throws Exception{
        shell("input keyevent 3");if(screenOff)shell("input keyevent 223");
        await(()->!a.readerVisible&&a.playing,"Background playback missing",5000);
        final PlaybackService service=PlaybackService.active;ui(()->service.session.getController().getTransportControls().pause());
        await(()->!a.playing&&PlaybackService.active==service,"Media pause failed",5000);int paused=a.position;
        Thread.sleep(500);ui(()->{check(a.position==paused,"Paused position advanced");a.releaseIdleModel();});
        ui(()->service.session.getController().getTransportControls().play());
        await(()->a.playing&&PlaybackService.active!=null&&PlaybackService.active.audioGuard.held,"Background resume failed",12000);
        await(()->a.position>paused,"Resumed audio did not advance",40000);
        ui(()->check(!a.destroyed&&!a.readerVisible,"Resume destroyed/opened reader"));
        
        progress(screenOff?"screen-off pause/resume passed":"Home pause/resume passed");
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();MainActivity activity=null;boolean success=false;try{
        shell("input keyevent 224");shell("wm dismiss-keyguard");check(getTargetContext().getSystemService(PowerManager.class).isInteractive(),"Screen must be awake for chapter test");check(!getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked(),"Unlock phone before test");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("book_id","chapter_background_qa").putExtra("book_name","章节与后台恢复验证").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final MainActivity a=activity;
        await(()->a.system!=null&&a.system.initialized,"System voices not ready",30000);
        voicePrefs=a.readingPrefs;voicePreference=MainActivity.readingVoicePreference(VoiceLanguages.systemLanguage(a));hadPreference=voicePrefs.contains(voicePreference);oldPreference=voicePrefs.getString(voicePreference,"");
        // Expired scrolling state must not turn the automatic chapter reset into a fresh user scroll.
        ui(()->{StringBuilder text=new StringBuilder("第一章 浏览\n");for(int i=0;i<60;i++)text.append("山间微风吹过，小路沿着溪流向前延伸。\n");text.append("第二章 前行\n我们继续走进山谷。\n");a.text=text.toString();a.chapters=MainActivity.parse(a.text);a.position=0;a.showPosition(true);});waitForIdleSync();
        ui(()->{a.scroll.scrollTo(0,a.dp(400));check(a.scroll.getScrollY()>0,"Fixture cannot scroll");a.scroll.manualMotion=true;a.readingFollow.resumeAt=SystemClock.elapsedRealtime()-1;a.position=a.chapters.get(1).start;a.showPosition(false);check(a.renderedChapter==1,"Expired follow did not render next chapter");check(a.readingFollow.resumeAt==0&&!a.scroll.manualMotion,"Automatic chapter reset rearmed manual-scroll wait");});
        // If rendering was deferred, its own timer must restore the latest chapter without another speech callback.
        ui(()->{a.position=0;a.showPosition(true);a.playing=true;a.readingFollow.resumeAt=SystemClock.elapsedRealtime()+250;a.position=a.chapters.get(1).start;a.showPosition(false);check(a.renderedChapter==0,"Browsing chapter changed too early");});
        await(()->a.renderedChapter==1&&a.readingFollow.resumeAt==0,"Deferred chapter never resumed",3000);ui(a::stop);progress("expired scroll reset and deferred chapter timer passed");
        ArrayList<String> keys=new ArrayList<>();SystemSpeaker.Option system=a.system.offlineVoice("zh","");for(VoiceModel pack:VoicePackages.get(a).list("zh")){keys.add(pack.key(pack.name.contains("eula")&&pack.speakers>73?73:0));break;}if(system!=null)keys.add(system.key);
        check(!keys.isEmpty(),"No Chinese voices");for(String key:keys){ui(()->book(a,key));await(()->a.chapter==1&&a.latestHighlightStart>=a.chapters.get(1).start,"Natural chapter transition missing",45000);
            ui(()->{check(a.renderedChapter==1,"Spoken chapter did not render");check(a.content.getText().toString().equals(a.text.substring(a.chapters.get(1).start,a.chapters.get(1).end)),"Stale chapter content");Spanned text=(Spanned)a.content.getText();check(text.getSpans(0,text.length(),ForegroundColorSpan.class).length>0,"New chapter highlight missing");check(PlaybackService.active.state.chapterIndex==1,"Media chapter stale");});progress("natural chapter/display/highlight passed: "+(VoicePackages.isKey(key)?"model":"system"));
            pauseResume(a,false);
            for(ActivityManager.AppTask task:getTargetContext().getSystemService(ActivityManager.class).getAppTasks())if(task.getTaskInfo().taskId==a.getTaskId())task.moveToFront();
            await(()->a.readerVisible,"Reader did not return from Home",6000);ui(a::stop);
        }
        // Lock only after foreground checks, so secure keyguard cannot block later foreground tests.
        for(String key:keys){ui(()->book(a,key));await(()->a.position>a.chapters.get(0).start,"Audio not started before screen-off",40000);pauseResume(a,true);}
        result.putString("result","PASS: expired manual scroll cannot rearm on automatic chapter reset; deferred chapter timer renders latest chapter; real system/imported speech crosses chapter with correct body/highlight/media state; Home and screen-off media-session pause/resume after releasing model advances audio without crash or opening reader");success=true;
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));if(activity!=null)result.putString("state","playing="+activity.playing+", chapter="+activity.chapter+", rendered="+activity.renderedChapter+", visible="+activity.readerVisible+", interactive="+getTargetContext().getSystemService(PowerManager.class).isInteractive()+", touching="+activity.scroll.touching+", followAt="+activity.readingFollow.resumeAt);}
    finally{try{shell("input keyevent 224");}catch(Exception ignored){}if(activity!=null){final MainActivity a=activity;ui(()->{a.stop();a.finish();});}if(voicePrefs!=null){android.content.SharedPreferences.Editor edit=voicePrefs.edit();if(hadPreference)edit.putString(voicePreference,oldPreference);else edit.remove(voicePreference);edit.commit();}getTargetContext().getSharedPreferences("reader_chapter_background_qa",0).edit().clear().commit();new File(getTargetContext().getFilesDir(),"book_chapter_background_qa.txt.position").delete();}
    finish(success?-1:0,result);}
}
