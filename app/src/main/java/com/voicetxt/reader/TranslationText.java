package com.voicetxt.reader;

import java.util.*;

/** Script boundaries and language headings; no guessed Latin-language defaults. */
final class TranslationText {
    static final Set<String> SUPPORTED=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("zh","en","ja","ko","fr","de","es","pt","ru","ar","hi","id","vi","th","tr","it","ms","tl","pl","cs","nl","km","my","fa","gu","ur","te","mr","he","bn","ta","uk","bo","kk","mn","ug","yue")));
    static final class Heading {
        final String code,zh,en;
        Heading(String code,String zh,String en){this.code=code;this.zh=zh;this.en=en;}
        String translated(String target){return "ja".equals(target)?(en.equals("Simplified Chinese")?"簡体字中国語":en.equals("Traditional Chinese")?"繁体字中国語":Locale.forLanguageTag(code).getDisplayLanguage(Locale.JAPANESE)):"zh".equals(target)?zh:en;}
    }
    static final Map<String,Heading> HEADINGS=new HashMap<>();
    static {heading("zh","简体中文","Simplified Chinese","简体中文","简体中文","中文","Chinese");heading("zh","繁体中文","Traditional Chinese","繁体中文","繁體中文","Traditional Chinese");heading("en","英语","English","English","英文","英语");heading("ja","日语","Japanese","日本語","日语","Japanese");heading("ko","韩语","Korean","한국어","韩语","Korean");heading("fr","法语","French","Français","法语","French");heading("de","德语","German","Deutsch","德语","German");heading("es","西班牙语","Spanish","Español","西班牙语","Spanish");heading("pt","葡萄牙语","Portuguese","Português","葡萄牙语","Portuguese");heading("ru","俄语","Russian","Русский","俄语","Russian");heading("ar","阿拉伯语","Arabic","العربية","阿拉伯语","Arabic");heading("hi","印地语","Hindi","हिन्दी","印地语","Hindi");heading("id","印尼语","Indonesian","Bahasa Indonesia","印尼语","Indonesian");heading("vi","越南语","Vietnamese","Tiếng Việt","越南语","Vietnamese");heading("th","泰语","Thai","ไทย","泰语","Thai");}
    static void heading(String code,String zh,String en,String... aliases){Heading item=new Heading(code,zh,en);for(String alias:aliases)HEADINGS.put(alias.toLowerCase(Locale.ROOT),item);}
    static Heading heading(String value){String key=value.trim().replaceAll("^[#*_`\\s]+|[#*_`\\s:：]+$","").trim().toLowerCase(Locale.ROOT);return HEADINGS.get(key);}
    static int group(int cp){if(cp==0x30fc||cp==0xff70)return 1;if(!Character.isLetter(cp))return 0;Character.UnicodeScript script=Character.UnicodeScript.of(cp);switch(script){case COMMON:case INHERITED:return 0;case HAN:case HIRAGANA:case KATAKANA:return 1;case LATIN:return 2;case HANGUL:return 3;case CYRILLIC:return 4;case ARABIC:return 5;case DEVANAGARI:return 6;case THAI:return 7;default:return 8;}}
    static String fallback(int group,String context){switch(group){case 1:return context.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HIRAGANA||Character.UnicodeScript.of(c)==Character.UnicodeScript.KATAKANA)?"ja":"zh";case 3:return "ko";case 7:return "th";default:return "";}}
    static final class Span {final String text;final int group;Span(String text,int group){this.text=text;this.group=group;}}
    static List<Span> split(String value){ArrayList<Span> parts=new ArrayList<>();int start=0,current=0;for(int i=0;i<value.length();){int cp=value.codePointAt(i),group=group(cp);if(group!=0){if(current!=0&&current!=group){parts.add(new Span(value.substring(start,i),current));start=i;}current=group;}i+=Character.charCount(cp);}if(start<value.length())parts.add(new Span(value.substring(start),current));return parts;}
    static String onlyGroup(String context,int group){StringBuilder result=new StringBuilder();for(int i=0;i<context.length();){int cp=context.codePointAt(i);int kind=group(cp);result.appendCodePoint(kind==0||kind==group?cp:' ');i+=Character.charCount(cp);}return result.toString();}
    static int end(String text,int start,int limit){int newline=text.indexOf('\n',start);if(newline>=start&&newline<limit)limit=newline+1;return SpeechSegmenter.end(text,start,limit);}
    // Preserve UTF-16 offsets so spoken ranges still map to the original book.
    static String speechText(String text){
        StringBuilder out=new StringBuilder(text.length());
        for(int at=0;at<text.length();){int cp=text.codePointAt(at),width=Character.charCount(cp),type=Character.getType(cp);
            boolean skip=type==Character.CONTROL||type==Character.FORMAT||type==Character.PRIVATE_USE||type==Character.SURROGATE||type==Character.UNASSIGNED||type==Character.OTHER_SYMBOL||cp==0xfffd;
            if(skip){for(int n=0;n<width;n++)out.append(' ');}else out.appendCodePoint(cp);at+=width;
        }return out.toString();
    }
    static boolean speakable(String text){return text.codePoints().anyMatch(Character::isLetterOrDigit);}
}
