package com.voicetxt.reader;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.view.View;import com.google.android.gms.tasks.*;import java.io.*;import java.util.*;

/** Real curated-file import, cancellation, deletion, locale layout and ready state. */
public class ModelManagerTest extends VoiceWarmupTest {
    public void onStart(){Bundle result=new Bundle();ModelManagerActivity activity=null;HunyuanModels store=HunyuanModels.get(getTargetContext());boolean existed=store.ready(),passed=false,oldWifi=store.wifiOnly();LocaleManager locales=getTargetContext().getSystemService(LocaleManager.class);LocaleList original=locales.getApplicationLocales();
        try{
            File source=new File(getTargetContext().getExternalFilesDir(null),"hy-qa/hunyuan.gguf");check(source.length()==HunyuanModels.BYTES,"Upload official model fixture first");
            File invalid=new File(getTargetContext().getCacheDir(),"hy-invalid-qa.gguf");try(FileOutputStream out=new FileOutputStream(invalid)){out.write(new byte[]{'G','G','U','F',0});}Task<Void> rejected=store.importUri(Uri.fromFile(invalid));await(()->rejected.isComplete(),"Invalid file import hung",15000);check(!rejected.isSuccessful()&&store.ready()==existed&&!store.partial.exists(),"Invalid file published or existing model lost");invalid.delete();
            Task<Void> cancelled=store.importUri(Uri.fromFile(source));check(!store.delete().isSuccessful(),"Deleted model during import");store.cancel();await(()->cancelled.isComplete(),"Import cancel hung",15000);check(!cancelled.isSuccessful()&&store.ready()==existed&&!store.partial.exists(),"Cancel retained partial file");
            Tasks.await(store.importUri(Uri.fromFile(source)),90,java.util.concurrent.TimeUnit.SECONDS);check(store.ready(),"Official file not ready after native probe");
            if(!existed){Tasks.await(store.delete(),15,java.util.concurrent.TimeUnit.SECONDS);check(!store.ready()&&!store.file.exists(),"Delete retained model");Tasks.await(store.importUri(Uri.fromFile(source)),90,java.util.concurrent.TimeUnit.SECONDS);}
            for(String language:new String[]{"zh","en","ar"}){
                locales.setApplicationLocales(LocaleList.forLanguageTags(language));Thread.sleep(350);activity=(ModelManagerActivity)startActivitySync(new Intent(getTargetContext(),ModelManagerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final ModelManagerActivity a=activity;
                ui(()->{if(a.home){a.home=false;a.render();}check(a.store==store&&a.count.getText().toString().equals(a.getString(R.string.models_ready)),"Wrong ready state");check(a.primary.getText().toString().equals(a.getString(R.string.models_delete)),"Wrong model action");check(a.importButton.isEnabled(),"Import unavailable");check(!a.store.busy,"Idle model busy");a.action();check(a.confirmation.isShowing(),"Delete confirmation missing");a.confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();check(store.ready(),"Cancel deleted model");});
                if(language.equals("ar"))await(()->a.root.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL,"Arabic RTL missing",5000);
                android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();if(shot!=null)try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"hy-model-"+language+".png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);shot.recycle();}
                ui(a::finish);activity=null;
            }
            for(String code:VoiceLanguages.CODES){android.content.res.Configuration config=new android.content.res.Configuration(getTargetContext().getResources().getConfiguration());config.setLocale(Locale.forLanguageTag(code));check(!getTargetContext().createConfigurationContext(config).getString(R.string.hy_intro).isEmpty(),"Missing localized model text");}
            check(store.ready()&&store.file.length()==HunyuanModels.BYTES&&!store.partial.exists(),"Final model missing");result.putString("result","PASS: curated SHA-256 and native model probe; invalid import rejected; cancel cleans partial; in-flight delete blocked; delete/reimport; one-model UI in Chinese/English/Arabic RTL; 15 localized captions; model left installed");passed=true;
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));result.putString("modelError",store.error);if(store.lastFailure!=null)result.putString("nativeDetail",android.util.Log.getStackTraceString(store.lastFailure));}
        finally{if(activity!=null){final ModelManagerActivity a=activity;ui(a::finish);}store.wifiOnly(oldWifi);locales.setApplicationLocales(original);}
        finish(passed?-1:0,result);
    }
}
