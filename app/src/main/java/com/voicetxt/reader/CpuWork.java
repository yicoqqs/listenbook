package com.voicetxt.reader;
import android.content.Context;import android.os.*;import java.util.concurrent.atomic.*;
/** Only protects active computation, never an entire listening session. */
final class CpuWork implements AutoCloseable {
    static final AtomicInteger active=new AtomicInteger();
    static final AtomicLong heldMillis=new AtomicLong();
    final PowerManager.WakeLock lock;final long began=SystemClock.elapsedRealtime();boolean closed;
    CpuWork(Context context){lock=context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"voicetxt:processing");lock.setReferenceCounted(false);lock.acquire(120000);active.incrementAndGet();}
    public void close(){if(closed)return;closed=true;if(lock.isHeld())lock.release();active.decrementAndGet();heldMillis.addAndGet(SystemClock.elapsedRealtime()-began);}
}
