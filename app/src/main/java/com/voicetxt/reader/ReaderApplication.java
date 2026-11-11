package com.voicetxt.reader;

import android.app.Application;

public final class ReaderApplication extends Application {
    @Override public void onCreate(){super.onCreate();PlaybackBubble.install(this);}
}
