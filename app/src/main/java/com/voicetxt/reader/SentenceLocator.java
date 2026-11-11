package com.voicetxt.reader;

/** Logical sentence selection, independent of synthesis buffer/chunk lengths. */
final class SentenceLocator {
    static final java.util.Set<String> PREFIX=new java.util.HashSet<>(java.util.Arrays.asList("mr","mrs","ms","dr","prof","sr","jr","st","m","mme","mlle","sra","dra","ing","nr","abs","art","ts","pgs","gs","ths","tp","bpk","sdr","jl","д","г","ул","им","рис","стр","см","док","डॉ","श्री","सुश्री","د","أ","دร","ดร","พ.ศ","น.ส","จ","อ","ต"));
    static final java.util.Set<String> MIDDLE=new java.util.HashSet<>(java.util.Arrays.asList("e.g","i.e","p.ex","z.b","d.h","u.a","bzw","ca","т.е","v.d","a.m","p.m"));
    static final java.util.Set<String> END_ABBREVIATION=new java.util.HashSet<>(java.util.Arrays.asList("etc","usw","т.д","т.п","v.v","dll","dsb"));
    static final class Range {final int start,end;Range(int start,int end){this.start=start;this.end=end;}}
    static Range find(String text,int offset,int lower,int upper){
        lower=Math.max(0,Math.min(lower,text.length()));upper=Math.max(lower,Math.min(upper,text.length()));
        if(lower==upper)return new Range(lower,upper);
        int target=Math.max(lower,Math.min(offset,upper-1));
        if(target>lower&&Character.isLowSurrogate(text.charAt(target))&&Character.isHighSurrogate(text.charAt(target-1)))target--;
        while(target<upper-1&&space(text.charAt(target)))target++;
        int start=skipSpace(text,lower,upper);Range previous=new Range(lower,upper);
        if(start==upper)return previous;
        for(int i=start;i<upper;i++){
            if(!boundary(text,i,start,upper))continue;
            int end=tail(text,i+1,start,upper);
            Range sentence=new Range(start,end);
            if(target<end)return containing(text,target,sentence);
            previous=sentence;start=skipSpace(text,end,upper);i=start-1;
            if(start==upper)return containing(text,target,previous);
        }
        return containing(text,target,start<upper?new Range(start,upper):previous);
    }
    static Range containing(String text,int target,Range sentence){if(sentence.end-sentence.start<=320)return sentence;int at=sentence.start;while(at<sentence.end){int end=SpeechSegmenter.end(text,at,sentence.end);if(target<end||end==sentence.end)return new Range(at,end);at=skipSpace(text,end,sentence.end);}return sentence;}
    static boolean boundary(String text,int at,int start,int upper){char c=text.charAt(at);if((c=='.'||c=='．')&&abbreviation(text,at,start,upper))return false;return "؟۔।॥\u2028\u2029".indexOf(c)>=0||SpeechSegmenter.terminal(text,at,start,upper);}
    static boolean token(char c){int type=Character.getType(c);return Character.isLetter(c)||c=='.'||c=='．'||type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK;}
    static String word(String text,int start,int end){return text.substring(start,end).replace('．','.').replaceAll("\\.+$","").toLowerCase(java.util.Locale.ROOT);}
    static boolean abbreviation(String text,int at,int start,int upper){int begin=at;while(begin>start&&token(text.charAt(begin-1)))begin--;int next=at+1;while(next<upper&&space(text.charAt(next))&&text.charAt(next)!='\n'&&text.charAt(next)!='\r')next++;if(next>=upper||text.charAt(next)=='\n'||text.charAt(next)=='\r')return false;String current=word(text,begin,at);int last=current.lastIndexOf('.');String lastPart=last<0?current:current.substring(last+1);
        if(at+1<upper&&token(text.charAt(at+1))){int finish=at+1;while(finish<upper&&token(text.charAt(finish)))finish++;String full=word(text,begin,finish);if(PREFIX.contains(full)||MIDDLE.contains(full)||END_ABBREVIATION.contains(full))return true;}
        if(current.equals("no"))return Character.isDigit(text.charAt(next));
        if(current.equals("г")){int previous=begin-1;while(previous>=start&&space(text.charAt(previous)))previous--;if(previous>=start&&Character.isDigit(text.charAt(previous))&&Character.isUpperCase(text.charAt(next)))return false;}
        return PREFIX.contains(current)||PREFIX.contains(lastPart)||MIDDLE.contains(current)||END_ABBREVIATION.contains(current)&&!Character.isUpperCase(text.charAt(next));}
    static int tail(String text,int end,int start,int upper){end=SpeechSegmenter.tail(text,end,upper,start);while(end<upper){char c=text.charAt(end);boolean extra="؟۔।॥".indexOf(c)>=0||c=='»'&&opened(text,'«','»',start,end)||c=='›'&&opened(text,'‹','›',start,end)||c=='“'&&opened(text,'„','“',start,end)||c=='«'&&opened(text,'»','«',start,end);if(!extra)break;end=SpeechSegmenter.tail(text,end+1,upper,start);}return end;}
    static boolean opened(String text,char open,char close,int start,int end){int balance=0;for(int i=start;i<end;i++){if(text.charAt(i)==open)balance++;else if(text.charAt(i)==close)balance--;}return balance>0;}
    static int skipSpace(String text,int at,int upper){while(at<upper&&space(text.charAt(at)))at++;return at;}
    static boolean space(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
}

