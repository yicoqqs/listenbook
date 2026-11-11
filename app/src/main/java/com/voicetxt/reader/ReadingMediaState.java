package com.voicetxt.reader;
final class ReadingMediaState {
    final String book,chapter;final int chapterIndex;final float fraction;final long durationMs;final boolean playing,previous,next;
    ReadingMediaState(String book,String chapter,int index,float fraction,long duration,boolean playing,boolean previous,boolean next){this.book=book;this.chapter=chapter;chapterIndex=index;this.fraction=Math.max(0,Math.min(1,fraction));durationMs=Math.max(1000,duration);this.playing=playing;this.previous=previous;this.next=next;}
    long positionMs(){return Math.min(durationMs,Math.round(fraction*durationMs));}
    static long estimateDuration(String text,int start,int end,float rate){int characters=0,words=0,pauses=0;boolean latin=false;for(int at=start;at<end;){int c=text.codePointAt(at);at+=Character.charCount(c);boolean letter=c<128&&Character.isLetterOrDigit(c);if(letter){if(!latin)words++;latin=true;}else{latin=false;if(Character.isLetterOrDigit(c))characters++;if("。！？!?，,；;\n".indexOf(c)>=0)pauses++;}}return Math.max(1000,Math.round((characters/5.0+words/2.5+pauses*.15)*1000/Math.max(.5f,rate)));}
}
