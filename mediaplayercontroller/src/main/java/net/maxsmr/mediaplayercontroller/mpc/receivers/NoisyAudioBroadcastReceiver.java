package net.maxsmr.mediaplayercontroller.mpc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.support.annotation.NonNull;

import net.maxsmr.commonutils.data.Observable;

public class NoisyAudioBroadcastReceiver extends BroadcastReceiver {

    @NonNull
    private final OnNoisyAudioObservable noisyAudioObservable = new OnNoisyAudioObservable();

    @NonNull
    public Observable<OnNoisyAudioListener> getNoisyAudioObservable() {
        return noisyAudioObservable;
    }

    public void register(@NonNull Context context) {
        context.registerReceiver(this, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    public void unregister(@NonNull Context context) {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if ((intent.getAction().equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY))) {
            noisyAudioObservable.dispatchNoisyAudio();
        }
    }

    private static class OnNoisyAudioObservable extends Observable<OnNoisyAudioListener> {

        private void dispatchNoisyAudio() {
            for (OnNoisyAudioListener l : observers) {
                l.onNoisyAudio();
            }
        }
    }

    public interface OnNoisyAudioListener {
        void onNoisyAudio();
    }
}
