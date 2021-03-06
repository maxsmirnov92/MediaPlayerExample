package net.maxsmr.mediaplayercontroller.mpc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import org.jetbrains.annotations.NotNull;

import net.maxsmr.commonutils.data.Observable;

public class HeadsetPlugBroadcastReceiver extends BroadcastReceiver {

    @NotNull
    private final OnHeadsetStateChangedObservable headsetStateChangedObservable = new OnHeadsetStateChangedObservable();

    @NotNull
    public Observable<OnHeadsetStateChangedListener> getHeadsetStateChangedObservable() {
        return headsetStateChangedObservable;
    }

    public void register(@NotNull Context context) {
        context.registerReceiver(this, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    }

    public void unregister(@NotNull Context context) {
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
            for (OnHeadsetStateChangedListener l : observers) {
                l.onHeadphonesPlugged(hasMicrophone);
            }
        }

        private void dispatchHeadphonesUnplugged() {
            for (OnHeadsetStateChangedListener l : observers) {
                l.onHeadphonesUnplugged();
            }
        }
    }

    public interface OnHeadsetStateChangedListener {

        void onHeadphonesPlugged(boolean hasMicrophone);

        void onHeadphonesUnplugged();
    }
}
