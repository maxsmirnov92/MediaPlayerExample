package ru.maxsmr.tasksutils;

import android.content.Context;
import android.support.annotation.MainThread;

public abstract class AsyncTaskLoader<D> extends android.support.v4.content.AsyncTaskLoader<D> {

    public AsyncTaskLoader(Context context) {
        super(context);
    }

    @Override
    protected void onForceLoad() {
        super.onForceLoad();
        if (startLoadingListener != null) {
            startLoadingListener.onStartLoading();
        }
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
//        if (takeContentChanged()) {
            forceLoad();
//        }
    }

//    @Override
//    protected void onStopLoading() {
//        cancelLoad();
//    }

//    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
//    @Override
//    public void stopLoading() {
//        if (isStarted()) {
//            ((ThreadPoolExecutor) AsyncTask.THREAD_POOL_EXECUTOR).shutdown();
//        }
//        super.stopLoading();
//    }

    private OnStartLoadingListener startLoadingListener;

    public void registerOnStartLoadingListener(OnStartLoadingListener listener) {
        if (startLoadingListener != null) {
            throw new RuntimeException("listener is already registered");
        }
        if (listener == null) {
            throw new NullPointerException("listener is null");
        }
        startLoadingListener = listener;
    }

    public void unregisterOnStartLoadingListener() {
        startLoadingListener = null;
    }

    public interface OnStartLoadingListener {

        @MainThread
        void onStartLoading();
    }


}