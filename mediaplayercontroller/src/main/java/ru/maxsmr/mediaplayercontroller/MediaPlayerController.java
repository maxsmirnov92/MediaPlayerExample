package ru.maxsmr.mediaplayercontroller;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Observable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.MediaController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ru.maxsmr.commonutils.data.FileHelper;

public final class MediaPlayerController implements MediaController.MediaPlayerControl {

    private static final Logger logger = LoggerFactory.getLogger(MediaPlayerController.class);

    public MediaPlayerController(@NonNull Context context) {
        mContext = context;
        init();
    }

    private final static int EXECUTOR_CALL_TIMEOUT = 10;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @NonNull
    private final Context mContext;

    @NonNull
    private State mCurrentState = State.IDLE;

    @NonNull
    private State mTargetState = State.IDLE;

    private MediaPlayer mMediaPlayer;

    private int mCurrentBufferPercentage = 0;

    @Nullable
    private Uri mAudioUri;

    @Nullable
    private Uri mVideoUri;

    @NonNull
    private Map<String, String> mHeaders = new LinkedHashMap<>();

    private boolean isSurfaceCreated = false;
    @Nullable
    private SurfaceView mVideoView;

    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mSurfaceWidth = 0;
    private int mSurfaceHeight = 0;

    private boolean mCanPause = true;
    private boolean mCanSeekBack = false;
    private boolean mCanSeekForward = false;

    private int mSeekWhenPrepared = POSITION_NO;  // set the seek position after prepared

    private boolean mLoopWhenPreparing = false; // set looping property while preparing

    public static final int AUDIO_SESSION_EMPTY = -1;

    public static final int POSITION_NO = -1;
    public static final int POSITION_START = 0;

    @Nullable
    private MediaController mMediaController;
    @Nullable
    private View mAnchorView;

    @NonNull
    private final HeadsetPlugBroadcastReceiver mHeadsetPlugBroadcastReceiver = new HeadsetPlugBroadcastReceiver();


    public boolean isReleased() {
        return mCurrentState == State.RELEASED;
    }

    private void init() {
        mHeadsetPlugBroadcastReceiver.register(mContext);
        mHeadsetPlugBroadcastReceiver.getHeadsetStateChangedObservable().registerObserver(mHeadsetStateChangeListener);
    }

    @NonNull
    public State getCurrentState() {
        return mCurrentState;
    }

    private void setCurrentState(@NonNull State newState) {
        if (newState != mCurrentState) {
            mCurrentState = newState;
            logger.info("current state: " + mCurrentState);
            mStateChangedObservable.dispatchCurrentStateChanged();
        }
    }

    @NonNull
    public State getTargetState() {
        return mTargetState;
    }

    private void setTargetState(@NonNull State newState) {
        if (newState != mTargetState) {
            mTargetState = newState;
            logger.info("target state: " + mTargetState);
            mStateChangedObservable.dispatchTargetStateChanged();
        }
    }

    @NonNull
    private final OnStateChangedObservable mStateChangedObservable = new OnStateChangedObservable();

    @NonNull
    public Observable<OnStateChangedListener> getStateChangedObservable() {
        return mStateChangedObservable;
    }

    private final OnVideoSizeChangedObservable mVideoSizeChangedObservable = new OnVideoSizeChangedObservable();

    @NonNull
    public Observable<OnVideoSizeChangedListener> getVideoSizeChangedObservable() {
        return mVideoSizeChangedObservable;
    }

    private final MediaPlayer.OnVideoSizeChangedListener mVideoSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    logger.debug("onVideoSizeChanged(), width=" + width + ", height=" + height);
                    if (mVideoView != null) {
                        mVideoWidth = mp.getVideoWidth();
                        mVideoHeight = mp.getVideoHeight();
                        if (mVideoWidth != 0 && mVideoHeight != 0) {
                            mVideoView.getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                            mVideoView.requestLayout();
                        }
                        mVideoSizeChangedObservable.dispatchOnVideoSizeChanged(width, height);
                    }
                }
            };

    private final MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
//                    logger.debug("onBufferingUpdate(), percent=" + percent);
                    mCurrentBufferPercentage = percent;
                }
            };


    private final MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {

        @SuppressWarnings("ConstantConditions")
        @Override
        public void onPrepared(MediaPlayer mp) {
            logger.debug("onPrepared()");

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                logger.info("track info: " + Arrays.toString(mMediaPlayer.getTrackInfo()));
            }

            if (mAudioUri == null && mVideoUri == null) {
                throw new IllegalStateException("audio/video uri is null, wtf?");
            }

            boolean isAudio = isAudioSpecified();
            Uri resourceUri = isAudio ? mAudioUri : mVideoUri;

            setCurrentState(State.PREPARED);

            if (resourceUri.getScheme() == null || resourceUri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) {

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

            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }

            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            final int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != POSITION_NO) {
                seekTo(seekToPosition);
            }

            if (isAudio || mVideoWidth != 0 && mVideoHeight != 0) {
                if (!isAudio) {
                    mVideoView.getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                    mVideoView.requestLayout();
                }
                if (isAudio || mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                    // We didn't actually change the size (it was already at the size
                    // we need), so we won't get a "surface changed" callback, so
                    // start the video here instead of in the callback.
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
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == State.PLAYING) {
                    start();
                }
            }

        }
    };

    private final OnCompletionObservable completionObservable = new OnCompletionObservable();

    @NonNull
    public final Observable<OnCompletionListener> getCompletionObservable() {
        return completionObservable;
    }

    private final MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    logger.info("onCompletion()");

                    setCurrentState(State.IDLE);
                    setTargetState(State.IDLE);

                    completionObservable.dispatchCompleted();

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

    @NonNull
    public Observable<OnErrorListener> getErrorObservable() {
        return mErrorObservable;
    }

    @NonNull
    private final HeadsetPlugBroadcastReceiver.OnHeadsetStateChangedListener mHeadsetStateChangeListener = new HeadsetPlugBroadcastReceiver.OnHeadsetStateChangedListener() {

        @Override
        public void onHeadphonesPlugged(boolean hasMicrophone) {
            if (mTargetState == State.PLAYING) {
                start();
            }
        }

        @Override
        public void onHeadphonesUnplugged() {
            pause();
        }
    };

    @Override
    public int getBufferPercentage() {
        return mCurrentBufferPercentage;
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }
        return 0;
    }

    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return POSITION_NO;
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

    public boolean isLooping() {
        return mCurrentState == State.PREPARED || isInPlaybackState() && mMediaPlayer != null && mMediaPlayer.isLooping();
    }

    public void setLooping(boolean toggle) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setLooping(toggle);
        }
        mLoopWhenPreparing = toggle;
    }

    public boolean isAudioSpecified() {
        return mAudioUri != null;
    }

    @Nullable
    public Uri getAudioUri() {
        return mAudioUri;
    }

    public void setAudioFile(@Nullable File file) {
        if (file != null) {
            if (FileHelper.isFileCorrect(file)) {
                setAudioUri(Uri.fromFile(file));
            }
        } else {
            setAudioUri(null);
        }
    }

    public void setAudioPath(@Nullable String path) {
        if (!TextUtils.isEmpty(path)) {
            setAudioUri(Uri.parse(path));
        } else {
            setAudioUri(null);
        }
    }

    public void setAudioUri(@Nullable Uri audioUri) {
        setAudioUri(audioUri, null);
    }

    public synchronized void setAudioUri(@Nullable Uri audioUri, @Nullable Map<String, String> headers) {
        logger.debug("setAudioUri(), audioUri=" + audioUri + ", headers=" + headers);

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if ((audioUri != null ? !audioUri.equals(this.mAudioUri) : this.mAudioUri != null) || (headers == null || !headers.equals(this.mHeaders))) {

            this.mVideoUri = null;
            this.mAudioUri = audioUri;
            this.mHeaders = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<String, String>();

            if (mCurrentState != State.IDLE) {
                if (isAudioSpecified()) {
                    openUri();
                } else {
                    stop();
                }
            }
        }
    }

    public boolean isVideoSpecified() {
        return mVideoUri != null;
    }

    @Nullable
    public Uri getVideoUri() {
        return mVideoUri;
    }

    public void setVideoFile(@Nullable File file) {
        if (file != null) {
            if (FileHelper.isFileCorrect(file)) {
                setVideoUri(Uri.fromFile(file));
            }
        } else {
            setVideoUri(null);
        }
    }

    public void setVideoPath(@Nullable String path) {
        if (!TextUtils.isEmpty(path)) {
            setVideoUri(Uri.parse(path));
        } else {
            setVideoUri(null);
        }
    }

    public void setVideoUri(@Nullable Uri videoUri) {
        setVideoUri(videoUri, null);
    }

    public synchronized void setVideoUri(@Nullable Uri videoUri, @Nullable Map<String, String> headers) {
        logger.debug("setVideoUri(), videoUri=" + videoUri + ", headers=" + headers);

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if ((videoUri != null ? !videoUri.equals(this.mVideoUri) : this.mVideoUri != null) || (headers == null || !headers.equals(this.mHeaders))) {

            this.mAudioUri = null;
            this.mVideoUri = videoUri;
            this.mHeaders = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<String, String>();

            if (mCurrentState != State.IDLE) {
                if (isVideoSpecified()) {
                    openUri();
                } else {
                    stop();
                }
            }
        }
    }

    @NonNull
    public Map<String, String> getHeaders() {
        return new LinkedHashMap<>(mHeaders);
    }

    /**
     * indicates whether surface is fully initialized
     */
    public boolean isSurfaceCreated() {
        return (mVideoView != null && isSurfaceCreated && !mVideoView.getHolder().isCreating() && mVideoView.getHolder().getSurface() != null);
    }

    public void setVideoView(SurfaceView videoView) {

        if (mVideoView != null) {
            mVideoView.getHolder().removeCallback(mSHCallback);
        }

        mVideoView = videoView;
        mVideoWidth = 0;
        mVideoHeight = 0;

        mVideoSizeChangedObservable.dispatchOnVideoSizeChanged(mVideoWidth, mVideoHeight);

        if (mVideoView != null) {
            mVideoView.getHolder().setKeepScreenOn(true);
            mVideoView.getHolder().addCallback(mSHCallback);
        }
    }

    @Override
    public void seekTo(int msec) {
        try {
            seekToInternal(msec);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            logger.error("an IllegalArgumentException occurred during seekToInternal()", e);
        }
    }

    private void seekToInternal(int msec) {
        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (msec < POSITION_START && msec != POSITION_NO)
            throw new IllegalArgumentException("incorrect seek position: " + msec);

        if (isInPlaybackState()) {
            if (msec >= POSITION_START && msec <= mMediaPlayer.getDuration()) {
                mMediaPlayer.seekTo(msec);
            }
            mSeekWhenPrepared = POSITION_NO;
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
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
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

    /**
     * @param controller must already has parent
     */
    public void setMediaController(MediaController controller, View anchorView) {

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
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

    private void detachMediaController() {
        if (mMediaController != null) {
            mMediaController.hide();
            mMediaController.setAnchorView(null);
        }
    }

    private synchronized void openUri() {
        logger.debug("openUri()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mAudioUri == null && mVideoUri == null) {
            throw new IllegalStateException("audio/video uri is not specified");
        }

        if (mCurrentState != State.PREPARING) {

            boolean isAudio = isAudioSpecified();

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

                if (isAudio) {

                    logger.debug("audio data source: " + mAudioUri);
                    mMediaPlayer.setDataSource(mContext, mAudioUri, mHeaders);
                    mMediaPlayer.setOnVideoSizeChangedListener(null);
                    mMediaPlayer.setDisplay(null);

                } else {

                    if (mVideoView == null || !isSurfaceCreated()) {
                        throw new IllegalStateException("surface was not created");
                    }

                    logger.debug("video data source: " + mVideoUri);
                    mMediaPlayer.setDataSource(mContext, mVideoUri, mHeaders);
                    mMediaPlayer.setOnVideoSizeChangedListener(mVideoSizeChangedListener);
                    mMediaPlayer.setDisplay(mVideoView.getHolder());
                }

                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setScreenOnWhilePlaying(!isAudio);
                mMediaPlayer.setLooping(mLoopWhenPreparing);

                final boolean result;

                try {
                    result = mExecutor.submit(new Callable<Boolean>() {

                        @Override
                        public Boolean call() throws Exception {

                            final long startPreparingTime = System.currentTimeMillis();
                            mMediaPlayer.prepareAsync();
                            logger.debug("media player preparing time: " + (System.currentTimeMillis() - startPreparingTime) + " ms");
                            return true;
                        }
                    }).get(EXECUTOR_CALL_TIMEOUT, TimeUnit.SECONDS);

                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("an Exception occurred during get()", e);
                    throw new RuntimeException(e);
                }

                if (result) {
                    // we don't set the target state here either, but preserve the
                    // target state that was there before.
                    setCurrentState(State.PREPARING);
                    attachMediaController();
                }

            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                logger.error("Unable to open content: " + (isAudioSpecified() ? mAudioUri : mVideoUri), ex);
                setCurrentState(State.IDLE);
                setTargetState(State.IDLE);
                mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            }
        }
    }

    @Override
    public void start() {
        logger.debug("start()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mCurrentState != State.PLAYING) {
            boolean result = true;
            if (isInPlaybackState()) {
                try {
                    result = mExecutor.submit(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            final long startStartingTime = System.currentTimeMillis();
                            mMediaPlayer.start();
                            logger.debug("media player starting time: " + (System.currentTimeMillis() - startStartingTime) + " ms");
                            setCurrentState(State.PLAYING);
                            return true;
                        }
                    }).get(EXECUTOR_CALL_TIMEOUT, TimeUnit.SECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("an Exception occurred during get()", e);
                    mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                }
            }
            if (result) {
                setTargetState(State.PLAYING);
            } else {
                releasePlayer(true);
            }
        }
    }

    @Override
    public void pause() {
        logger.debug("pause()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mCurrentState != State.PAUSED) {
            boolean result = true;
            if (isInPlaybackState()) {
                if (mMediaPlayer.isPlaying()) {
                    try {
                        result = mExecutor.submit(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                final long startPausingTime = System.currentTimeMillis();
                                mMediaPlayer.pause();
                                logger.debug("media player pausing time: " + (System.currentTimeMillis() - startPausingTime) + " ms");
                                setCurrentState(State.PAUSED);
                                return true;
                            }
                        }).get(EXECUTOR_CALL_TIMEOUT, TimeUnit.SECONDS);

                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.error("an Exception occurred during get()", e);
                        mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                    }
                }
            }
            if (result) {
                setTargetState(State.PAUSED);
            } else {
                releasePlayer(true);
            }
        }
    }

    public void stop() {
        logger.debug("stop()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mCurrentState != State.IDLE) {
            if (isInPlaybackState()) {
                try {
                    mExecutor.submit(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            final long startStoppingTime = System.currentTimeMillis();
                            mMediaPlayer.stop();
                            logger.debug("media player stopping time: " + (System.currentTimeMillis() - startStoppingTime) + " ms");
                            return true;
                        }
                    }).get(EXECUTOR_CALL_TIMEOUT, TimeUnit.SECONDS);

                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("an Exception occurred during get()", e);
                    mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                }
            }
            releasePlayer(true);
        }
    }

    public void resume() {
        logger.debug("resume()");
        openUri();
    }

    public void suspend() {
        logger.debug("suspend()");
        releasePlayer(false);
    }

    /*
     * release the media player in any state
     */
    private void releasePlayer(boolean clearTargetState) {
        logger.debug("releasePlayer(), clearTargetState=" + clearTargetState);

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mMediaPlayer != null) {

            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;

            mCurrentBufferPercentage = 0;

            mCanPause = true;
            mCanSeekBack = false;
            mCanSeekForward = false;

            setCurrentState(State.IDLE);
            if (clearTargetState) {
                setTargetState(State.IDLE);
            }

            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);

            detachMediaController();
        }
    }

    public void release() {

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was already released");
        }

        releasePlayer(true);

        mHeadsetPlugBroadcastReceiver.getHeadsetStateChangedObservable().unregisterObserver(mHeadsetStateChangeListener);
        mHeadsetPlugBroadcastReceiver.unregister(mContext);

        mExecutor.shutdown();

        setCurrentState(State.RELEASED);
    }

    /**
     * mpc instance state
     */
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

    public interface OnCompletionListener {
        void onCompletion();
    }

    private class OnCompletionObservable extends Observable<OnCompletionListener> {

        private void dispatchCompleted() {
            for (OnCompletionListener l : mObservers) {
                l.onCompletion();
            }
        }
    }

    public interface OnErrorListener {

        void onError(int what, int extra);
    }

    private class OnErrorObservable extends android.database.Observable<OnErrorListener> {

        private void dispatchError(int what, int extra) {
            for (OnErrorListener l : mObservers) {
                l.onError(what, extra);
            }
        }
    }

    public interface OnVideoSizeChangedListener {

        void onVideoSizeChanged(int width, int height);
    }

    private class OnVideoSizeChangedObservable extends Observable<OnVideoSizeChangedListener> {

        private void dispatchOnVideoSizeChanged(int width, int height) {
            for (OnVideoSizeChangedListener l : mObservers) {
                l.onVideoSizeChanged(width, height);
            }
        }
    }

    private final SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format,
                                   int w, int h) {
            logger.debug("surfaceChanged(), format=" + format + ", w=" + w + ", h=" + h);

            mSurfaceWidth = w;
            mSurfaceHeight = h;

            boolean isValidState = (mTargetState == State.PLAYING) && isVideoSpecified();
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);

            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != POSITION_NO) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        public void surfaceCreated(SurfaceHolder holder) {
            logger.debug("surfaceCreated()");

            isSurfaceCreated = true;

            if (isVideoSpecified()) {
                openUri();
            }
        }

        // after we return from this we can't use the surface any more
        public void surfaceDestroyed(SurfaceHolder holder) {
            logger.debug("surfaceDestroyed()");

            mSurfaceWidth = 0;
            mSurfaceHeight = 0;

            if (!isReleased()) {
                stop();
            }
        }
    };
}
