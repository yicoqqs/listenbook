package com.voicetxt.reader;

import java.util.*;

/** A statistical language guess must agree with the actual writing system. */
final class LanguageRules {
    static final Set<String> LATIN=new HashSet<>(Arrays.asList("en","fr","de","es","pt","id","vi","it","tr","ms","tl","pl","cs","nl"));
    static boolean compatible(int group,String language){
        if(language.equals("und"))return false;
        switch(group){
            case 1:return language.equals("zh")||language.equals("ja")||language.equals("yue");
            case 3:return language.equals("ko");
            case 4:return Arrays.asList("ru","uk","bg","be","mk","sr","kk","ky","mn","tg","bs").contains(language);
            case 5:return Arrays.asList("ar","fa","ur","ps","sd","ug","ku").contains(language);
            case 6:return Arrays.asList("hi","ne","mr","sa","bho","mai","gom","kok").contains(language);
            case 7:return language.equals("th");
            case 2:return LATIN.contains(language)||!Arrays.asList("zh","ja","ko","ru","uk","bg","be","mk","sr","kk","ky","mn","tg","ar","fa","ur","ps","sd","ug","hi","ne","mr","sa","bho","mai","my","th","lo","km","bn","ta","te","kn","ml","gu","pa","he","yi","el","hy","ka","am","si").contains(language);
            default:return true;
        }
    }
    static boolean kana(String text){return text.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HIRAGANA||Character.UnicodeScript.of(c)==Character.UnicodeScript.KATAKANA);}
    static String explicit(int group,String sample,String hint){
        if(group==1){if(hint.equals("zh")||hint.equals("ja"))return hint;if(kana(sample))return "ja";}
        if(group==2&&LATIN.contains(hint))return hint;
        if(group==3)return "ko";
        if(group==7)return "th";
        return "";
    }
    static String fallback(int group,String sample){String language=TranslationText.fallback(group,sample);return language;}
}
