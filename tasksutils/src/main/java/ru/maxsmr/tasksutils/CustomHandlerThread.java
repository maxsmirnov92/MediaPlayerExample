package ru.maxsmr.tasksutils;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class CustomHandlerThread extends HandlerThread {

    private static final Logger logger = LoggerFactory.getLogger(CustomHandlerThread.class);

    private Handler handler = null;

    public boolean isLooperPrepared() {
        return handler != null && getLooper() != null;
    }

    public CustomHandlerThread(String name) {
        super(name);
    }

    @Override
    protected void onLooperPrepared() {
        handler = new Handler(getLooper());
    }

    private final LinkedBlockingDeque<Runnable> runnableQueue = new LinkedBlockingDeque<>();

    public synchronized void removeTask(@NonNull Runnable r) {
        if (runnableQueue.contains(r)) {

            if (!isLooperPrepared())
                throw new RuntimeException("Looper was not prepared()");

            if (!isAlive())
                throw new RuntimeException("Thread " + getName() + " is not alive");

            if (isInterrupted())
                throw new RuntimeException("Thread " + getName() + " interrupted");

            handler.removeCallbacks(r);
            runnableQueue.remove(r);
        }
    }

    public synchronized void removeLastTask() {
        removeTask(runnableQueue.peekLast());
    }

    public synchronized void addNewTask(@NonNull Runnable run, final long delay) {

        if (!isLooperPrepared())
            throw new RuntimeException("Looper was not prepared()");

        if (!isAlive())
            throw new RuntimeException("Thread " + getName() + " is not alive");

        if (isInterrupted())
            throw new RuntimeException("Thread " + getName() + " interrupted");

        if (!handler.postDelayed(run, delay >= 0 ? delay : 0)) {
            throw new RuntimeException("can't post handler message");
        }

        runnableQueue.add(run);
    }

    @Override
    public boolean quit() {
        handler = null;
        interrupt();
        return super.quit();
    }

    /**
     * join() call does not release reliably after that
     */
    @Override
    public boolean quitSafely() {
        handler = null;
        interrupt();
        return super.quitSafely();
    }

    public static void awaitLatch(CountDownLatch latch, int milliseconds, Runnable callbackWhenNormal, Runnable callbackWhenExpired) {
        if (latch != null && milliseconds >= 0) {
            synchronized (latch) {
                long startTime = System.currentTimeMillis();
                try {
                    latch.await(milliseconds, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    logger.error("an InterruptedException occurred during await()", e);
                    if (callbackWhenExpired != null) {
                        callbackWhenExpired.run();
                    }
                }
                if (milliseconds > 0 && System.currentTimeMillis() - startTime >= milliseconds) {
                    if (callbackWhenExpired != null) {
                        callbackWhenExpired.run();
                    }
                } else {
                    if (callbackWhenNormal != null) {
                        callbackWhenNormal.run();
                    }
                }
            }
        }
    }

}
