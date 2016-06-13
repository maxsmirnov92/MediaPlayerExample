package ru.maxsmr.tasksutils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutorCaller {

    private static ExecutorCaller sInstance;

    public static void initInstance() {
        if (sInstance == null) {
            synchronized (ExecutorCaller.class) {
                sInstance = new ExecutorCaller();
            }
        }
    }

    public static ExecutorCaller getInstance() {
        initInstance();
        return sInstance;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final int DEFAULT_EXECUTOR_CALL_TIMEOUT = 5;

    public <T> T getCallResult(int timeoutSec, @NonNull Callable<T> c, @Nullable Runnable callFinally) {

        if (timeoutSec <= 0) {
            throw new IllegalArgumentException("incorrect timeoutSec: " + timeoutSec);
        }

        try {
            Future<T> e = executor.submit(c);
            return e.get(timeoutSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        } finally {
            if (callFinally != null) {
                callFinally.run();
            }
        }
    }

    public <T> T getCallResult(@NonNull Callable<T> c, @Nullable Runnable callFinally) {
        return getCallResult(DEFAULT_EXECUTOR_CALL_TIMEOUT, c, callFinally);
    }

}
