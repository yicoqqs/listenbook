package com.voicetxt.reader;

import android.content.*;
import android.media.*;
import android.os.*;

/** Notification duck requests do not pause speech; calls and exclusive focus still pause. */
final class AudioGuard implements AutoCloseable {
    final Context context; final AudioManager manager; final java.util.function.Consumer<String> interrupted;
    boolean held,registered;
    final AudioFocusRequest request;
    final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction()))lost(context.getString(R.string.ui_0));}};
    AudioGuard(Context c,java.util.function.Consumer<String> callback){context=c;manager=c.getSystemService(AudioManager.class);interrupted=callback;request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)lost(context.getString(R.string.ui_1));},new Handler(Looper.getMainLooper())).build();}
    boolean acquire(){if(held)return true;held=manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(held&&!registered){IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(receiver,filter);registered=true;}return held;}
    void lost(String reason){if(!held)return;close();interrupted.accept(reason);}
    @Override public void close(){held=false;if(registered){context.unregisterReceiver(receiver);registered=false;}manager.abandonAudioFocusRequest(request);}
}
