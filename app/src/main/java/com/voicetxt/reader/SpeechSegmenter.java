package com.voicetxt.reader;

/** Punctuation-aware chunks, retaining exact offsets into the original text. */
final class SpeechSegmenter {
    static final int SOFT=80,HARD=160;
    /** Prefer a nearby natural clause for the first clip; never cut a word just to hit a length. */
    static int firstEnd(String text,int start,int limit){
        int normal=end(text,start,limit),cap=Math.min(normal,start+48);
        for(int i=start;i<cap;i++){
            char c=text.charAt(i);
            boolean boundary=terminal(text,i,start,limit)||weak(text,i,limit)||"،؛؟।॥".indexOf(c)>=0;
            if(!boundary)continue;int at=tail(text,i+1,limit,start);
            if(at-start>=12&&at<=normal&&at-start<=48)return at;
        }
        return normal;
    }
    static int end(String text,int start,int limit){
        limit=Math.min(limit,text.length());if(start>=limit)return limit;
        int cap=Math.min(limit,start+HARD),strong=-1,before=-1,after=-1;
        if(cap<limit&&Character.isHighSurrogate(text.charAt(cap-1)))cap--;
        for(int i=start;i<cap;i++){
            char c=text.charAt(i);
            if(terminal(text,i,start,limit)){strong=tail(text,i+1,limit,start);break;}
            if(weak(text,i,limit)){int boundary=tail(text,i+1,limit,start);if(boundary-start<=SOFT)before=boundary;else if(after<0)after=boundary;}
        }
        // A complete sentence just beyond the old 80-character cutoff is much
        // better than reading one or two characters of its last clause alone.
        if(strong>0&&strong-start<=SOFT+24)return strong;
        if(strong<0&&limit-start<=SOFT+24)return limit;
        if(before-start>=24)return before;
        if(after>0&&(strong<0||after<strong))return after;
        if(strong>0)return strong;
        if(cap==limit)return limit;
        // With no punctuation available, respect English word boundaries.
        for(int i=Math.min(cap,start+SOFT)-1;i>start+24;i--)if(Character.isWhitespace(text.charAt(i)))return i+1;
        for(int i=Math.min(cap,start+SOFT);i<cap;i++)if(Character.isWhitespace(text.charAt(i)))return i+1;
        int boundary=cap;
        while(boundary<Math.min(limit,start+HARD+SOFT)&&latinWord(text.charAt(boundary-1))&&latinWord(text.charAt(boundary)))boundary++;
        return boundary;
    }
    static boolean latinWord(char c){return c<128&&(Character.isLetterOrDigit(c)||c=='\''||c=='_');}
    static boolean terminal(String text,int i,int start,int limit){char c=text.charAt(i);
        if("。！？!?；;\n\r｡".indexOf(c)>=0)return true;
        if(c=='…')return true;
        if(c!='.'&&c!='．')return false;
        if(i>0&&i+1<limit&&Character.isDigit(text.charAt(i-1))&&Character.isDigit(text.charAt(i+1)))return false;
        if(i+2<limit&&text.charAt(i+1)=='.'&&text.charAt(i+2)=='.')return true;
        if(i+1<limit&&text.charAt(i+1)=='.')return false;
        if(i+1<limit&&latinWord(text.charAt(i+1)))return false;
        int token=i;while(token>start&&((text.charAt(token-1)>='A'&&text.charAt(token-1)<='Z')||(text.charAt(token-1)>='a'&&text.charAt(token-1)<='z')||text.charAt(token-1)=='.'))token--;
        String word=text.substring(token,i).toLowerCase(java.util.Locale.ROOT);
        if(i+1<limit&&("mr mrs ms dr prof sr jr st vs e.g i.e".contains(word)&&!word.isEmpty())){
            for(String abbreviation:new String[]{"mr","mrs","ms","dr","prof","sr","jr","st","vs","e.g","i.e"})if(word.equals(abbreviation))return false;
        }
        if(i-token==1&&Character.isUpperCase(text.charAt(token))&&i+1<limit)return false;
        return true;
    }
    static boolean weak(String text,int i,int limit){char c=text.charAt(i);if("，,、：:".indexOf(c)<0)return false;
        if(i>0&&i+1<limit&&Character.isDigit(text.charAt(i-1))&&Character.isDigit(text.charAt(i+1)))return false;
        return !(c==':'&&i+1<limit&&text.charAt(i+1)=='/');
    }
    static int tail(String text,int end,int limit,int start){while(end<limit){char c=text.charAt(end);if("。！？!?；;…，,、”’」』）》】〕〉".indexOf(c)>=0){end++;continue;}if(c=='.'&&end>0&&text.charAt(end-1)=='.'){end++;continue;}
            if(c=='"'||c=='\''){int count=0;for(int i=start;i<end;i++)if(text.charAt(i)==c)count++;if((count&1)!=0||end+1==limit||Character.isWhitespace(text.charAt(end+1))){end++;continue;}}
            break;}return end;}
}
