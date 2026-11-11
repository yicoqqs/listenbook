package com.voicetxt.reader;

/** JNI boundary for the pinned offline HY-MT model. No networking or text logging. */
final class HunyuanBridge {
    static {System.loadLibrary("hunyuan-jni");}
    static native long load(String path,int threads);
    static native String generate(long handle,String prompt,int maxTokens,long epoch);
    static native void cancel(long handle,long epoch);
    static native void release(long handle);
}
