package com.voicetxt.reader;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Bounded original-text detection with script checks and context-aware caching. */
final class OriginalVoicePlanner {
    static final class Batch {final String language;final int end;Batch(String language,int end){this.language=language;this.end=end;}}
    static final class Guess {final String language;final float confidence;Guess(String language,float confidence){this.language=language;this.confidence=confidence;}}
    interface Detector {List<Guess> identify(String text)throws Exception;}
    final Detector detector;
    final Map<String,String> cache=new LinkedHashMap<String,String>(64,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>256;}};
    OriginalVoicePlanner(LanguageIdentifier identifier){this(text->{List<Guess> values=new ArrayList<>();for(IdentifiedLanguage value:Tasks.await(identifier.identifyPossibleLanguages(text),8,TimeUnit.SECONDS))values.add(new Guess(value.getLanguageTag(),value.getConfidence()));return values;});}
    OriginalVoicePlanner(Detector detector){this.detector=detector;}
    String detect(String text,String previous)throws Exception{
        previous=VoiceLanguages.canonical(previous);String key=previous+"\u0000"+text;String cached=cache.get(key);if(cached!=null)return cached;
        if(OriginalLanguageRules.letters(text)==0)return previous;
        String direct=OriginalLanguageRules.explicit(text,previous);if(!direct.isEmpty()){cache.put(key,direct);return direct;}
        String best="";float confidence=0;for(Guess value:detector.identify(text)){String code=VoiceLanguages.canonical(value.language);if(VoiceLanguages.known(code)&&OriginalLanguageRules.compatible(text,code)&&value.confidence>confidence){best=code;confidence=value.confidence;}}
        boolean previousFits=OriginalLanguageRules.compatible(text,previous);
        if(previousFits&&(best.isEmpty()||confidence<.65f||(OriginalLanguageRules.letters(text)<12&&!best.equals(previous)&&confidence<.9f)))best=previous;
        if(best.isEmpty())throw new java.io.IOException("unknown_original_language");cache.put(key,best);return best;
    }
    Batch plan(List<String> lines,int start,String previous,BooleanSupplier current)throws Exception{
        if(!current.getAsBoolean())throw new CancellationException();String first=detect(lines.get(start),previous);int end=start+1;
        while(end<lines.size()&&end-start<24){if(!current.getAsBoolean())throw new CancellationException();if(!detect(lines.get(end),first).equals(first))break;end++;}
        return new Batch(first,end);
    }
}
