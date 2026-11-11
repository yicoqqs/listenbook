package com.voicetxt.reader;

/** Predicts whether queued audio can cover the next generation, instead of always waiting for 3 clips. */
final class SpeechStartupBuffer {
    private double workPerCharacter,realTimeRatio;
    private long pendingAt,firstReadyAt;
    private int pendingCharacters=-1;
    private boolean pending;
    synchronized void begin(long now){pending=true;pendingAt=now;pendingCharacters=-1;}
    synchronized void textReady(int characters){pendingCharacters=Math.max(1,characters);}
    synchronized void prepared(int characters,long workMs,long audioMs,long now){
        double perCharacter=(double)Math.max(1,workMs)/Math.max(1,characters),ratio=(double)Math.max(1,workMs)/Math.max(1,audioMs);
        workPerCharacter=workPerCharacter==0?perCharacter:workPerCharacter*.65+perCharacter*.35;
        realTimeRatio=realTimeRatio==0?ratio:realTimeRatio*.65+ratio*.35;
        if(firstReadyAt==0)firstReadyAt=now;pending=false;
    }
    synchronized boolean ready(int clips,long audioMs,boolean finished,long now){
        if(clips==0)return false;
        if(finished||clips>=3)return true;
        // A model that cannot synthesize faster than playback keeps the conservative start buffer.
        if(realTimeRatio>=.9)return false;
        if(clips>=2&&now-firstReadyAt>=2500)return true;
        // Unknown translated text must not be fetched on the audio thread.
        if(!pending||pendingCharacters<0)return false;
        double nextMs=Math.max(100,workPerCharacter*pendingCharacters-(now-pendingAt));
        return audioMs>=nextMs*1.6+250;
    }
}
