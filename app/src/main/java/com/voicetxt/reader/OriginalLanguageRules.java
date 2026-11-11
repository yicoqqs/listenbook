package com.voicetxt.reader;

import android.icu.lang.UScript;
import java.util.*;

/** A language guess must agree with the characters actually present in the original text. */
final class OriginalLanguageRules {
    static int letters(String text){return (int)text.codePoints().filter(c->Character.isLetter(c)&&Character.UnicodeScript.of(c)!=Character.UnicodeScript.COMMON&&Character.UnicodeScript.of(c)!=Character.UnicodeScript.INHERITED).count();}
    static boolean compatible(String text,String language){
        int[] scripts=UScript.getCode(Locale.forLanguageTag(VoiceLanguages.canonical(language)));if(scripts==null||scripts.length==0)return false;
        int total=0,matching=0;for(int point:text.codePoints().toArray()){if(!Character.isLetter(point))continue;Character.UnicodeScript kind=Character.UnicodeScript.of(point);if(kind==Character.UnicodeScript.COMMON||kind==Character.UnicodeScript.INHERITED)continue;total++;for(int script:scripts)if(UScript.hasScript(point,script)){matching++;break;}}
        return total>0&&matching>0&&matching*2>=total;
    }
    static String explicit(String text,String previous){
        int han=0,kana=0,hangul=0,thai=0,latin=0,other=0;for(int point:text.codePoints().toArray()){if(!Character.isLetter(point))continue;switch(Character.UnicodeScript.of(point)){case HAN:han++;break;case HIRAGANA:case KATAKANA:kana++;break;case HANGUL:hangul++;break;case THAI:thai++;break;case LATIN:latin++;break;case COMMON:case INHERITED:break;default:other++;}}
        if(kana>0&&han+kana>=latin+hangul+thai+other)return "ja";
        if(hangul>0&&hangul+han>=latin+kana+thai+other)return "ko";
        boolean cjkPrevious=Arrays.asList("zh","ja","yue").contains(previous);
        if(han>0&&kana==0&&hangul==0&&thai==0&&other==0&&(han>=latin||(cjkPrevious&&han>=3&&latin<=han*3)))return previous.equals("ja")||previous.equals("yue")?previous:"zh";
        if(thai>0&&thai>=han+kana+hangul+latin+other)return "th";
        // Isolated abbreviations such as CPU, APP, VIP are not a reason to switch narration language.
        if(latin>0&&latin<=8&&han+kana+hangul+thai+other==0&&text.trim().matches("[A-Z0-9\\s._+/#,!?():;-]+"))return previous;
        return "";
    }
}
