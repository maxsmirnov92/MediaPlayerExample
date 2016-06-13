package ru.maxsmr.commonutils.android.gui.progressable;

import android.support.annotation.MainThread;

public interface Progressable {

    Progressable STUB = new Progressable() {
        public void onStart() {
        }

        public void onStop() {
        }
    };


    @MainThread
    void onStart();

    @MainThread
    void onStop();

}
