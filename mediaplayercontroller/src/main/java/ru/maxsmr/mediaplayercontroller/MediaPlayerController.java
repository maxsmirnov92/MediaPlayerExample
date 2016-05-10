package ru.maxsmr.mediaplayercontroller;

import android.content.Context;
import android.database.Observable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.MediaController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import ru.altarix.commonutils.data.FileHelper;

public final class MediaPlayerController implements MediaController.MediaPlayerControl, HeadsetPlugBroadcastReceiver.OnHeadsetStateChangedListener {

    private static final Logger logger = LoggerFactory.getLogger(MediaPlayerController.class);

    public MediaPlayerController(@NonNull Context context) {
        mContext = context;
        init();
    }

    @NonNull
    private final Context mContext;

    private State mCurrentState = State.IDLE;

    private State mTargetState = State.IDLE;

    private MediaPlayer mMediaPlayer;

    private int mCurrentBufferPercentage = 0;

    @Nullable
    private Uri mAudioUri;

    @NonNull
    private Map<String, String> mHeaders = new LinkedHashMap<>();

    private boolean mCanPause = false;
    private boolean mCanSeekBack = false;
    private boolean mCanSeekForward = false;
    private int mSeekWhenPrepared;  // recording the seek position while preparing

    public static final int AUDIO_SESSION_EMPTY = -1;

    public static final int POSITION_EMPTY = -1;
    public static final int POSITION_START = 0;

    public static final int DURATION_EMPTY = -1;

    private MediaController mMediaController;
    private View mAnchorView;

    private void init() {
        mHeadsetPlugBroadcastReceiver.register(mContext);
        mHeadsetPlugBroadcastReceiver.getHeadsetStateChangedObservable().registerObserver(this);
    }

    private void setCurrentState(@NonNull State newState) {
        if (newState != mCurrentState) {
            mCurrentState = newState;
            logger.info("current state: " + mCurrentState);
            mStateChangedObservable.dispatchCurrentStateChanged();
        }
    }

    private void setTargetState(@NonNull State newState) {
        if (newState != mTargetState) {
            mTargetState = newState;
            logger.info("target state: " + mTargetState);
            mStateChangedObservable.dispatchTargetStateChanged();
        }
    }

    @NonNull
    private final HeadsetPlugBroadcastReceiver mHeadsetPlugBroadcastReceiver = new HeadsetPlugBroadcastReceiver();

    @NonNull
    private final OnStateChangedObservable mStateChangedObservable = new OnStateChangedObservable();

    public Observable<OnStateChangedListener> getStateChangedObservable() {
        return mStateChangedObservable;
    }

    private final MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                }
            };


    private final MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {

        @Override
        public void onPrepared(MediaPlayer mp) {

            if (mAudioUri == null) {
                throw new IllegalStateException("audio uri is null, wtf?");
            }

            setCurrentState(State.PREPARED);

            if (mAudioUri.getScheme().equalsIgnoreCase("file")) {
                mCanPause = true;
                mCanSeekBack = true;
                mCanSeekForward = true;

            } else {

                // Get the capabilities of the player for this stream
//            Metadata data = mp.getMetadata(MediaPlayer.METADATA_ALL,
//                    MediaPlayer.BYPASS_METADATA_FILTER);
//
//            if (data != null) {
//                mCanPause = !data.has(Metadata.PAUSE_AVAILABLE)
//                        || data.getBoolean(Metadata.PAUSE_AVAILABLE);
//                mCanSeekBack = !data.has(Metadata.SEEK_BACKWARD_AVAILABLE)
//                        || data.getBoolean(Metadata.SEEK_BACKWARD_AVAILABLE);
//                mCanSeekForward = !data.has(Metadata.SEEK_FORWARD_AVAILABLE)
//                        || data.getBoolean(Metadata.SEEK_FORWARD_AVAILABLE);
//            } else {
//                mCanPause = mCanSeekBack = mCanSeekForward = true;
//            }

            }

            int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            seekTo(seekToPosition);

            if (mTargetState == State.PLAYING) {
                start();
                if (mMediaController != null) {
                    mMediaController.show();
                }
            } else if (!isPlaying() && (seekToPosition >= POSITION_START || getCurrentPosition() > POSITION_START)) {
                if (mMediaController != null) {
                    // Show the media controls when we're paused into a video and make 'em stick.
                    mMediaController.show(0);
                }
            }

        }
    };

    private final MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    logger.info("onCompletion()");
                    setCurrentState(State.IDLE);
                    setTargetState(State.IDLE);
                    if (mMediaController != null) {
                        mMediaController.hide();
                    }
                }
            };

    private final MediaPlayer.OnInfoListener mInfoListener = new MediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            logger.info("onInfo(), what=" + what + ", extra=" + extra);
            return true;
        }
    };

    private final MediaPlayer.OnErrorListener mErrorListener =
            new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
                    logger.error("onError(), framework_err=" + framework_err + ", impl_err=" + impl_err);

                    setCurrentState(State.IDLE);

                    mErrorObservable.dispatchError(framework_err, impl_err);

                    releasePlayer(true);
                    return true;
                }
            };

    @NonNull
    private final OnErrorObservable mErrorObservable = new OnErrorObservable();

    public Observable<MediaPlayer.OnErrorListener> getErrorObservable() {
        return mErrorObservable;
    }

    @Override
    public int getBufferPercentage() {
        return mCurrentBufferPercentage;
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }
        return DURATION_EMPTY;
    }

    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return POSITION_EMPTY;
    }

    public boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != State.RELEASED &&
                mCurrentState != State.IDLE &&
                mCurrentState != State.PREPARING);
    }

    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Nullable
    public Uri getAudioUri() {
        return mAudioUri;
    }

    public void setAudioFile(File file) {
        if (FileHelper.isFileCorrect(file)) {
            setAudioUri(Uri.fromFile(file));
        }
    }

    public void setAudioPath(String path) {
        setAudioUri(Uri.parse(path));
    }

    public void setAudioUri(@Nullable Uri audioUri) {
        setAudioUri(audioUri, null);
    }

    public void setAudioUri(@Nullable Uri audioUri, @Nullable Map<String, String> headers) {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was released");
        }

        if ((audioUri != null ? !audioUri.equals(this.mAudioUri) : this.mAudioUri != null) || (headers == null || !headers.equals(this.mHeaders))) {

            this.mAudioUri = audioUri;
            this.mHeaders = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<String, String>();

            if (mCurrentState != State.IDLE) {
                openAudio();
            }
        }
    }

    @NonNull
    public Map<String, String> getHeaders() {
        return new LinkedHashMap<>(mHeaders);
    }

    public void seekTo(int msec) {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was released");
        }

        if (msec < POSITION_START && msec != POSITION_EMPTY)
            throw new IllegalArgumentException("incorrect seek position: " + msec);

        if (isInPlaybackState()) {
            if (msec >= POSITION_START && msec <= mMediaPlayer.getDuration()) {
                mMediaPlayer.seekTo(msec);
                mSeekWhenPrepared = POSITION_EMPTY;
            }
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public int getAudioSessionId() {
        return mMediaPlayer != null ? mMediaPlayer.getAudioSessionId() : AUDIO_SESSION_EMPTY;
    }

    @Override
    public boolean canPause() {
        return false;
    }

    @Override
    public boolean canSeekBackward() {
        return false;
    }

    @Override
    public boolean canSeekForward() {
        return false;
    }

    public void toggleMediaControlsVisibility() {

        if (mMediaController == null) {
            throw new IllegalStateException("mediaController was not specified");
        }

        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    public MediaController getMediaController() {
        return mMediaController;
    }

    public void setMediaController(MediaController controller, View anchorView) {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was released");
        }

        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        mAnchorView = anchorView;
        attachMediaController();
    }

    public boolean isMediaControllerAttached() {
        return mMediaController != null && mCurrentState != State.IDLE && mCurrentState != State.RELEASED;
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            mMediaController.setAnchorView(mAnchorView);
            mMediaController.setEnabled(isInPlaybackState());
        }
    }

    private void openAudio() {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was released");
        }

        if (mAudioUri == null) {
            throw new IllegalStateException("audio uri is not specified");
        }

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        releasePlayer(false);

        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        try {
            mMediaPlayer = new MediaPlayer();

            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mAudioUri, mHeaders);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(false);
            mMediaPlayer.prepareAsync();

            // we don't set the target state here either, but preserve the
            // target state that was there before.
            setCurrentState(State.PREPARING);
            attachMediaController();

        } catch (IOException | IllegalArgumentException ex) {
            logger.error("Unable to open content: " + mAudioUri, ex);
            setCurrentState(State.IDLE);
            setTargetState(State.IDLE);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    @Override
    public void start() {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was released");
        }

        if (isInPlaybackState()) {
            mMediaPlayer.start();
            setCurrentState(State.PLAYING);
        }
        setTargetState(State.PLAYING);
    }

    @Override
    public void pause() {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was released");
        }

        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                setCurrentState(State.PAUSED);
            }
        }
        setTargetState(State.PAUSED);
    }

    public void stop() {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was released");
        }

        if (isInPlaybackState()) {
            mMediaPlayer.stop();
        }
        releasePlayer(true);
    }

    public void resume() {
        openAudio();
    }

    public void suspend() {
        releasePlayer(false);
    }

    /*
 * release the media player in any state
*/
    private void releasePlayer(boolean cleartargetstate) {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was released");
        }

        if (mMediaPlayer != null) {

            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;

            mCurrentBufferPercentage = 0;

            mCanPause = false;
            mCanSeekBack = false;
            mCanSeekForward = false;

            setCurrentState(State.IDLE);
            if (cleartargetstate) {
                setTargetState(State.IDLE);
            }

            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    public void release() {

        if (mCurrentState == State.RELEASED) {
            throw new IllegalStateException("MediaPlayerController was already released");
        }

        releasePlayer(true);

        mHeadsetPlugBroadcastReceiver.getHeadsetStateChangedObservable().unregisterObserver(this);
        mHeadsetPlugBroadcastReceiver.unregister(mContext);

        setCurrentState(State.RELEASED);
    }

    @Override
    public void onHeadphonesPlugged(boolean hasMicrophone) {
        if (mTargetState == State.PLAYING) {
            start();
        }
    }

    @Override
    public void onHeadphonesUnplugged() {
        if (isPlaying()) {
            pause();
        }
    }

    public enum State {
        IDLE, PREPARING, PREPARED, PLAYING, PAUSED, RELEASED
    }

    public interface OnStateChangedListener {

        void onCurrentStateChanged(@NonNull State currentState);

        void onTargetStateChanged(@NonNull State targetState);
    }

    private class OnStateChangedObservable extends Observable<OnStateChangedListener> {

        private void dispatchCurrentStateChanged() {
            for (OnStateChangedListener l : mObservers) {
                l.onCurrentStateChanged(mCurrentState);
            }
        }

        private void dispatchTargetStateChanged() {
            for (OnStateChangedListener l : mObservers) {
                l.onTargetStateChanged(mTargetState);
            }
        }
    }

    private class OnErrorObservable extends Observable<MediaPlayer.OnErrorListener> {

        private void dispatchError(int what, int extra) {

            if (mMediaPlayer == null)
                throw new IllegalStateException("mediaPlayer was not initialized");

            for (MediaPlayer.OnErrorListener l : mObservers) {
                l.onError(mMediaPlayer, what, extra);
            }
        }
    }
}
