package com.voicetxt.reader;

import android.os.SystemClock;
import org.json.JSONObject;

/** Local diagnostic timings only. Never records book text or sends telemetry. */
final class SpeechStartupTiming {
    final long requestedAt=SystemClock.elapsedRealtime();
    volatile long modelInitMs,modelWaitMs,planningMs,translationMs,firstChunkMs,firstSynthesisMs,firstTextMs;
    volatile long firstPreparedAt,audioStartedAt,initialAudioMs,synthesisMs,preparedAudioMs,gapMs;
    volatile int initialClips,preparedClips,gapCount,platformUnderruns;
    volatile boolean warmModel,completed,cancelled;
    long firstAudioMs(){return audioStartedAt==0?0:audioStartedAt-requestedAt;}
    JSONObject json(){JSONObject value=new JSONObject();try{value.put("warmModel",warmModel).put("modelInitMs",modelInitMs).put("modelWaitMs",modelWaitMs).put("planningMs",planningMs).put("translationMs",translationMs).put("firstChunkMs",firstChunkMs).put("firstSynthesisMs",firstSynthesisMs).put("firstTextMs",firstTextMs).put("bufferWaitMs",audioStartedAt==0||firstPreparedAt==0?0:audioStartedAt-firstPreparedAt).put("firstAudioMs",firstAudioMs()).put("initialClips",initialClips).put("initialAudioMs",initialAudioMs).put("preparedClips",preparedClips).put("synthesisMs",synthesisMs).put("preparedAudioMs",preparedAudioMs).put("gapCount",gapCount).put("gapMs",gapMs).put("platformUnderruns",platformUnderruns).put("completed",completed).put("cancelled",cancelled);}catch(Exception ignored){}return value;}
}
