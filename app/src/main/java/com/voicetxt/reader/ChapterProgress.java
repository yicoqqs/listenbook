package com.voicetxt.reader;

/** Source offsets shared by the reader slider and lock-screen chapter controls. */
final class ChapterProgress {
    static float fraction(int position,int start,int end){return Math.max(0,Math.min(1,(float)(position-start)/Math.max(1,end-start-1)));}
    static int position(float fraction,int start,int end){return start+Math.round(Math.max(0,Math.min(1,fraction))*Math.max(0,end-start-1));}
}
