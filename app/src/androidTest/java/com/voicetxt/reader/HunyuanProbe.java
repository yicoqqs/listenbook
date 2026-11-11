package com.voicetxt.reader;
import android.app.Instrumentation;import android.os.*;
public class HunyuanProbe extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();long model=0;try{long began=SystemClock.elapsedRealtime();model=HunyuanBridge.load(HunyuanModels.get(getTargetContext()).file.getAbsolutePath(),3);result.putLong("loadMs",SystemClock.elapsedRealtime()-began);began=SystemClock.elapsedRealtime();result.putString("fixture",HunyuanBridge.generate(model,"将以下文本翻译为中文，注意只需要输出翻译后的结果，不要额外解释：\n\nThe little horse is reading a book.",64,1));result.putLong("generateMs",SystemClock.elapsedRealtime()-began);}catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}finally{if(model!=0)HunyuanBridge.release(model);}finish(-1,result);}
}
