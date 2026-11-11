package com.voicetxt.reader;

/** Ten seconds measured from the last manual movement, including fling movement. */
final class ReadingFollowState {
    static final long DELAY_MS=10000;
    long resumeAt;
    void manualMovement(long now){resumeAt=now+DELAY_MS;}
    boolean blocked(long now,boolean touching){return touching||now<resumeAt;}
    long remaining(long now){return Math.max(0,resumeAt-now);}
    void clear(){resumeAt=0;}
}
