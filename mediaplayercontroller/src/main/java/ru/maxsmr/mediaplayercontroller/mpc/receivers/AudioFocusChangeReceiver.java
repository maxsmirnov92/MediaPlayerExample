package ru.maxsmr.mediaplayercontroller.mpc.receivers;


import android.content.Context;
import android.database.Observable;
import android.media.AudioManager;
import android.support.annotation.NonNull;

public class AudioFocusChangeReceiver implements AudioManager.OnAudioFocusChangeListener {

    @NonNull
    private final OnAudioFocusChangedObservable audioFocusChangedObservable = new OnAudioFocusChangedObservable();

    @NonNull
    public Observable<OnAudioFocusChangeListener> getAudioFocusChangeListener() {
        return audioFocusChangedObservable;
    }

    public void requestFocus(@NonNull Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    public void abandonFocus(@NonNull Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(this);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        audioFocusChangedObservable.dispatchAudioFocusChanged(focusChange);
    }

    private static class OnAudioFocusChangedObservable extends Observable<OnAudioFocusChangeListener> {

        private void dispatchAudioFocusChanged(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    for (OnAudioFocusChangeListener l : mObservers) {
                        l.onAudioFocusGain();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    for (OnAudioFocusChangeListener l : mObservers) {
                        l.onAudioFocusLoss();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    for (OnAudioFocusChangeListener l : mObservers) {
                        l.onAudioFocusLossTransient();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    for (OnAudioFocusChangeListener l : mObservers) {
                        l.onAudioFocusLossTransientCanDuck();
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
