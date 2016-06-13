package ru.maxsmr.commonutils.android;

import android.database.Observable;

import java.util.ArrayList;

public abstract class SynchronizedObservable<T> extends Observable<T> {

    public void registerObserver(T observer) {
        synchronized (mObservers) {
            super.registerObserver(observer);
        }
    }

    @Override
    public void unregisterObserver(T observer) {
        synchronized (mObservers) {
            super.unregisterObserver(observer);
        }
    }

    @Override
    public void unregisterAll() {
        synchronized (mObservers) {
            super.unregisterAll();
        }
    }

    protected ArrayList<T> copyOfObservers() {
        synchronized (mObservers) {
            return new ArrayList<>(mObservers);
        }
    }
}
