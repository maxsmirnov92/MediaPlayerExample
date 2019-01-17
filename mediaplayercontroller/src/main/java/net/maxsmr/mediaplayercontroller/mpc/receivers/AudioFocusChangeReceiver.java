package net.maxsmr.mediaplayercontroller.mpc.receivers;


import android.content.Context;
import android.media.AudioManager;
import org.jetbrains.annotations.NotNull;

import net.maxsmr.commonutils.data.Observable;

import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;

public class AudioFocusChangeReceiver implements AudioManager.OnAudioFocusChangeListener {

    private boolean isRequested = false;

    @NotNull
    private final OnAudioFocusChangedObservable audioFocusChangedObservable = new OnAudioFocusChangedObservable();

    @NotNull
    public Observable<OnAudioFocusChangeListener> getAudioFocusChangeObservable() {
        return audioFocusChangedObservable;
    }

    public boolean requestFocus(@NotNull Context context) {
        if (isRequested) {
            abandonFocus(context);
        }
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return isRequested = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AUDIOFOCUS_REQUEST_FAILED;
    }

    public boolean abandonFocus(@NotNull Context context) {
        if (isRequested) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            try {
                return audioManager.abandonAudioFocus(this) != AUDIOFOCUS_REQUEST_FAILED;
            } finally {
                isRequested = false;
            }
        }
        return true;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        audioFocusChangedObservable.dispatchAudioFocusChanged(focusChange);
    }

    private static class OnAudioFocusChangedObservable extends Observable<OnAudioFocusChangeListener> {

        private void dispatchAudioFocusChanged(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    synchronized (observers) {
                        for (OnAudioFocusChangeListener l : observers) {
                            l.onAudioFocusGain();
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    synchronized (observers) {
                        for (OnAudioFocusChangeListener l : observers) {
                            l.onAudioFocusGain();
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    synchronized (observers) {
                        for (OnAudioFocusChangeListener l : observers) {
                            l.onAudioFocusLoss();
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    synchronized (observers) {
                        for (OnAudioFocusChangeListener l : observers) {
                            l.onAudioFocusLossTransient();
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    synchronized (observers) {
                        for (OnAudioFocusChangeListener l : observers) {
                            l.onAudioFocusLossTransientCanDuck();
                        }
                    }
                    break;
            }
        }
    }

    public interface OnAudioFocusChangeListener {

        void onAudioFocusGain();

        void onAudioFocusLoss();

        void onAudioFocusLossTransient();

        void onAudioFocusLossTransientCanDuck();
    }
}
