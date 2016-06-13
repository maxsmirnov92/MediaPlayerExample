package ru.maxsmr.mediaplayercontroller.mpc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Observable;
import android.support.annotation.NonNull;

public class HeadsetPlugBroadcastReceiver extends BroadcastReceiver {

    @NonNull
    private final OnHeadsetStateChangedObservable headsetStateChangedObservable = new OnHeadsetStateChangedObservable();

    @NonNull
    public Observable<OnHeadsetStateChangedListener> getHeadsetStateChangedObservable() {
        return headsetStateChangedObservable;
    }

    public void register(@NonNull Context context) {
        context.registerReceiver(this, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    }

    public void unregister(@NonNull Context context) {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if ((intent.getAction().equals(Intent.ACTION_HEADSET_PLUG))) {
            int headSetState = intent.getIntExtra("state", 0);
            if (headSetState == 0) {
                headsetStateChangedObservable.dispatchHeadphonesUnplugged();
            } else {
                int hasMicrophone = intent.getIntExtra("microphone", 0);
                headsetStateChangedObservable.dispatchHeadphonesPlugged(hasMicrophone != 0);
            }
        }
    }

    private static class OnHeadsetStateChangedObservable extends Observable<OnHeadsetStateChangedListener> {

        private void dispatchHeadphonesPlugged(boolean hasMicrophone) {
            for (OnHeadsetStateChangedListener l : mObservers) {
                l.onHeadphonesPlugged(hasMicrophone);
            }
        }

        private void dispatchHeadphonesUnplugged() {
            for (OnHeadsetStateChangedListener l : mObservers) {
                l.onHeadphonesUnplugged();
            }
        }
    }

    public interface OnHeadsetStateChangedListener {

        void onHeadphonesPlugged(boolean hasMicrophone);

        void onHeadphonesUnplugged();
    }
}
