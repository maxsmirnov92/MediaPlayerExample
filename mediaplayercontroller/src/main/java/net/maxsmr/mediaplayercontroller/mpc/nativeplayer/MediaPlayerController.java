package net.maxsmr.mediaplayercontroller.mpc.nativeplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.MediaController;

import net.maxsmr.commonutils.data.CompareUtils;
import net.maxsmr.commonutils.data.Observable;
import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class MediaPlayerController extends BaseMediaPlayerController<MediaPlayerController.MediaError> {

    public MediaPlayerController(@NotNull Context context, @Nullable Looper mediaLooper) {
        super(context, mediaLooper);
    }

    private final static int EXECUTOR_CALL_TIMEOUT_S = 30;

    private final Runnable mResetRunnable = () -> {
        logger.d("mResetRunnable :: run()");
        postOnMediaHandler(() -> {
            if (isPreparing()) {
                logger.d("resetting by timeout...");
                onError(new MediaError(MediaError.PREPARE_TIMEOUT_EXCEEDED, MediaError.UNKNOWN));
            }
        });
    };

    @NotNull
    private final OnVideoSizeChangedObservable mVideoSizeChangedObservable = new OnVideoSizeChangedObservable();

    private final MediaPlayer.OnVideoSizeChangedListener mVideoSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    logger.d("onVideoSizeChanged(), width=" + width + ", height=" + height);
                    if (mVideoView != null) {
                        mVideoWidth = mp != null ? mp.getVideoWidth() : width;
                        mVideoHeight = mp != null ? mp.getVideoHeight() : height;
                        if (mVideoWidth != width || mVideoHeight != height) {
                            throw new IllegalStateException("media player width/height does not match");
                        }
                        if (mVideoWidth > 0 && mVideoHeight > 0) {
                            mVideoView.getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                            mVideoView.requestLayout();
                        }
                    } else {
                        mVideoWidth = 0;
                        mVideoHeight = 0;
                    }
                    mVideoSizeChangedObservable.dispatchOnVideoSizeChanged(width, height);
                }
            };

    private final MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            (mp, percent) -> MediaPlayerController.this.onBufferingUpdate(percent);


    private final MediaPlayer.OnPreparedListener mPreparedListener = mp -> MediaPlayerController.this.onPrepared();

    private final MediaPlayer.OnCompletionListener mCompletionListener =
            mp -> MediaPlayerController.this.onCompletion();

    private final MediaPlayer.OnInfoListener mInfoListener = (mp, what, extra) -> {
        logger.i("onInfo(), what=" + what + ", extra=" + extra);
        return true;
    };

    private final MediaPlayer.OnErrorListener mErrorListener =
            (mp, framework_err, impl_err) -> {
                logger.e("onError(), framework_err=" + framework_err + ", impl_err=" + impl_err);
                return MediaPlayerController.this.onError(new MediaError(framework_err, impl_err));
            };

    private MediaPlayer mMediaPlayer;

    private boolean isSurfaceCreated = false;

    @Nullable
    private SurfaceView mVideoView;

    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mSurfaceWidth = 0;
    private int mSurfaceHeight = 0;

    @Nullable
    private MediaController mMediaController;

    private boolean mMediaControllerAttached;

    @Nullable
    private View mAnchorView;

    public boolean isPlayerReleased() {
        return mCurrentState == State.IDLE || mMediaPlayer == null || isReleased();
    }

    @NotNull
    @Override
    protected Runnable getResetRunnable() {
        return mResetRunnable;
    }


    @NotNull
    public final Observable<OnVideoSizeChangedListener> getVideoSizeChangedObservable() {
        return mVideoSizeChangedObservable;
    }

    @Override
    public boolean isContentSpecified() {
        return isAudioSpecified() || isVideoSpecified();
    }

    @Override
    public void setVolume(float left, float right) {
        synchronized (mLock) {
            super.setVolume(left, right);
            if (isInPlaybackState()) {
                mMediaPlayer.setVolume(left, right);
            }
        }
    }

    @Override
    public int getDuration() {
        synchronized (mLock) {
            if (isInPlaybackState()) {
                return mMediaPlayer.getDuration();
            }
            return 0;
        }
    }

    public int getCurrentPosition() {
        synchronized (mLock) {
            if (isInPlaybackState()) {
                return mMediaPlayer.getCurrentPosition();
            }
            return POSITION_NO;
        }
    }

    public boolean isInPlaybackState() {
        synchronized (mLock) {
            return (mMediaPlayer != null && !isReleasingPlayer() &&
                    mCurrentState != State.RELEASED &&
                    mCurrentState != State.IDLE &&
                    mCurrentState != State.PREPARING);
        }
    }

    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    public boolean isPreparing() {
        return mCurrentState == State.PREPARING;
    }

    public boolean isLooping() {
        return isInPlaybackState() && mMediaPlayer.isLooping();
    }

    @Override
    public void setLooping(boolean toggle) {
        synchronized (mLock) {
            super.setLooping(toggle);
            if (mMediaPlayer != null) {
                mMediaPlayer.setLooping(toggle);
            }
        }
    }

    @Override
    public boolean isPlayModeSupported(@NotNull PlayMode playMode) {
        return playMode == PlayMode.AUDIO || playMode == PlayMode.VIDEO;
    }

    /**
     * indicates whether surface is fully initialized
     */
    @MainThread
    public boolean isSurfaceCreated() {
        return (mVideoView != null && isSurfaceCreated && !mVideoView.getHolder().isCreating() && mVideoView.getHolder().getSurface() != null);
    }

    @Nullable
    public SurfaceView getVideoView() {
        return mVideoView;
    }

    @MainThread
    public void setVideoView(SurfaceView videoView) {

        if (videoView != mVideoView) {

            if (isVideoSpecified() && isInPlaybackState()) {
                stop();
            }

            if (mVideoView != null) {
                mVideoView.getHolder().removeCallback(mSHCallback);
            }

            mVideoView = videoView;
            mVideoSizeChangedListener.onVideoSizeChanged(mMediaPlayer, 0, 0);

            if (mVideoView != null) {
                mVideoView.getHolder().setKeepScreenOn(true);
                mVideoView.getHolder().addCallback(mSHCallback);
            }
        }
    }

    @Override
    public void seekTo(int msec) {
        synchronized (mLock) {
            logger.d("seekTo(), msec=" + msec);
            try {
                seekToInternal(msec);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                logger.e("an IllegalArgumentException occurred during seekToInternal()", e);
            }
        }
    }

    private void seekToInternal(int msec) {
        synchronized (mLock) {
            checkReleased();

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
    }

    @Override
    public int getAudioSessionId() {
        synchronized (mLock) {
            return mMediaPlayer != null ? mMediaPlayer.getAudioSessionId() : AUDIO_SESSION_EMPTY;
        }
    }

    @Nullable
    public final MediaController getMediaController() {
        return mMediaController;
    }

    /**
     * @param controller must already has parent
     */
    @MainThread
    public void setMediaController(MediaController controller, View anchorView) {

        checkReleased();

        if (mMediaController != null) {
            mMediaController.hide();
        }

        mMediaController = controller;
        mAnchorView = anchorView;

        attachMediaController();
    }

    @MainThread
    public boolean isMediaControllerAttached() {
        return mMediaController != null && mMediaControllerAttached;
    }

    @MainThread
    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            mMediaController.setAnchorView(mAnchorView);
            mMediaControllerAttached = true;
            toggleMediaControllerEnabled();
        }
    }

    @MainThread
    private void detachMediaController() {
        if (mMediaController != null) {
            mMediaController.hide();
            mMediaController.setAnchorView(null);
            mMediaControllerAttached = false;
        }
    }

    @SuppressWarnings("ConstantConditions")
    @MainThread
    public void toggleMediaControlsVisibility() {
        if (isMediaControllerAttached()) {
            if (mMediaController.isShowing()) {
                mMediaController.hide();
            } else {
                mMediaController.show();
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    @MainThread
    private void toggleMediaControllerEnabled() {
        if (isMediaControllerAttached()) {
            mMediaController.setEnabled(isInPlaybackState());
        }
    }

    /**
     * events callbacks depends on thread, on which called this method
     */
    @Override
    protected void openDataSource() {
        synchronized (mLock) {
            logger.d("openDataSource(), content: " + (mContentUri != null ? mContentUri : mContentFileDescriptor) + ", current state: " + mCurrentState);

            checkReleased();

            if (!isContentSpecified()) {
                logger.e("can't open data source: content is not specified");
                return;
            }

            if (!isPreparing()) {

                setCurrentState(State.PREPARING);

                if (mContentUri != null) {

                    String contentType = HttpURLConnection.guessContentTypeFromName(mContentUri.toString());
                    logger.i("uri content type: " + contentType);

                    if (!mNoCheckMediaContentType && TextUtils.isEmpty(contentType)) {
                        logger.e("empty uri content type");
                        onError(new MediaError(MediaError.PREPARE_EMPTY_CONTENT_TYPE, MediaError.UNKNOWN));
                        return;
                    }
                }

                // we shouldn't clear the target state, because somebody might have
                // called start() previously
                suspend();

                if (!requestAudioFocus()) {
                    logger.e("failed to request audio focus");
                }

                boolean result;
                final long startPreparingTime = System.currentTimeMillis();

                try {
                    mMediaPlayer = new MediaPlayer();

                    mMediaPlayer.setOnPreparedListener(mPreparedListener);
                    mMediaPlayer.setOnCompletionListener(mCompletionListener);
                    mMediaPlayer.setOnErrorListener(mErrorListener);
                    mMediaPlayer.setOnInfoListener(mInfoListener);
                    mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);

                    mCurrentBufferPercentage = 0;

                    if (mContentUri != null) {
                        logger.d("content data source: " + mContentUri);
                        mMediaPlayer.setDataSource(mContext, mContentUri, mContentHeaders);
                    } else if (mContentFileDescriptor != null) {
                        logger.d("content data source: " + mContentFileDescriptor);
                        mMediaPlayer.setDataSource(mContentFileDescriptor.getFileDescriptor(), mContentFileDescriptor.getStartOffset(), mContentFileDescriptor.getLength());
                    } else {
                        throw new AssertionError("content data source not specified");
                    }

                    if (mPlayMode == PlayMode.AUDIO) {

                        mMediaPlayer.setOnVideoSizeChangedListener(null);
                        mMediaPlayer.setDisplay(null);

                    } else if (mPlayMode == PlayMode.VIDEO) {

                        if (mVideoView == null || !isSurfaceCreated()) {
                            throw new IllegalStateException("surface was not created");
                        }

                        mMediaPlayer.setOnVideoSizeChangedListener(mVideoSizeChangedListener);
                        mMediaPlayer.setDisplay(mVideoView.getHolder());

                    } else {
                        throw new IllegalStateException("unsupported " + PlayMode.class.getSimpleName() + ": " + mPlayMode);
                    }

                    mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mMediaPlayer.setScreenOnWhilePlaying(mPlayMode == PlayMode.VIDEO);
                    mMediaPlayer.setLooping(mLoopWhenPreparing);

                    beforeOpenDataSource();

                    try {
                        result = submitOnExecutor(new Callable<Boolean>() {

                            @Override
                            public Boolean call() throws Exception {
                                mMediaPlayer.prepareAsync();
                                return true;
                            }
                        }).get(EXECUTOR_CALL_TIMEOUT_S, TimeUnit.SECONDS);

                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.e("an Exception occurred during get()", e);
                        throw new RuntimeException(e);
                    }

                } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                    ex.printStackTrace();
                    logger.e("Unable to open content: " + (mContentUri != null ? mContentUri : mContentFileDescriptor), ex);
                    result = false;
                }

                if (result) {
                    logger.d("media player preparing start success / time: " + (System.currentTimeMillis() - startPreparingTime) + " ms");
                    // we don't set the target state here either, but preserve the
                    // target state that was there before.
                    toggleMediaControllerEnabled();
                    scheduleResetCallback();
                } else {
                    logger.e("media player preparing start failed / time: " + (System.currentTimeMillis() - startPreparingTime) + " ms");
                    onError(new MediaError(MediaError.PREPARE_UNKNOWN, MediaError.UNKNOWN));
                }
            } else {
                logger.w("can't open data source: currently is preparing");
            }
        }
    }

    @Override
    public void start() {
        synchronized (mLock) {
            logger.d("start(), current state: " + mCurrentState);

            checkReleased();

            if (mCurrentState != State.PLAYING) {
                if (isInPlaybackState()) {
                    boolean result;
                    final long startStartingTime = System.currentTimeMillis();
                    try {
                        result = submitOnExecutor(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                mMediaPlayer.start();
                                return true;
                            }
                        }).get(EXECUTOR_CALL_TIMEOUT_S, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.e("an Exception occurred during get()", e);
//                    onError(new MediaError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0));
                        result = false;
                    }
                    if (result) {
                        logger.d("media player starting success / time: " + (System.currentTimeMillis() - startStartingTime) + " ms");
                        setCurrentState(State.PLAYING);
                        startPlaybackTimeTask();
                    } else {
                        logger.e("media player starting failed / time: " + (System.currentTimeMillis() - startStartingTime) + " ms");
                        onError(new MediaError(MediaError.PLAY_UNKNOWN, MediaError.UNKNOWN));
                    }
                }
            }
            setTargetState(State.PLAYING);
        }
    }

    @Override
    public void pause() {
        synchronized (mLock) {
            logger.d("pause(), current state: " + mCurrentState);

            checkReleased();

            if (mCurrentState != State.PAUSED) {
                if (isPlaying()) {
                    boolean result;
                    final long startPausingTime = System.currentTimeMillis();
                    try {
                        result = submitOnExecutor(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                mMediaPlayer.pause();
                                return true;
                            }
                        }).get(EXECUTOR_CALL_TIMEOUT_S, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.e("an Exception occurred during get()", e);
//                    onError(new MediaError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0));
                        result = false;
                    }
                    if (result) {
                        logger.d("media player pausing success / time: " + (System.currentTimeMillis() - startPausingTime) + " ms");
                        stopPlaybackTimeTask();
                        setCurrentState(State.PAUSED);
                    } else {
                        logger.e("media player pausing failed / time: " + (System.currentTimeMillis() - startPausingTime) + " ms");
                        onError(new MediaError(MediaError.PAUSE_UNKNOWN, MediaError.UNKNOWN));
                    }
                }
            }
            setTargetState(State.PAUSED);
        }
    }

    public void stop() {
        synchronized (mLock) {
            logger.d("stop(), current state: " + mCurrentState);

            checkReleased();

            if (mCurrentState != State.IDLE) {
                if (isInPlaybackState()) {
                    boolean result;
                    final long startStoppingTime = System.currentTimeMillis();
                    try {
                        result = submitOnExecutor(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                mMediaPlayer.stop();
                                return true;
                            }
                        }).get(EXECUTOR_CALL_TIMEOUT_S, TimeUnit.SECONDS);

                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.e("an Exception occurred during get()", e);
//                    onError(new MediaError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0));
                        result = false;
                    }
                    if (result) {
                        logger.d("media player stopping success / time: " + (System.currentTimeMillis() - startStoppingTime) + " ms");
                    } else {
                        logger.e("media player stopping failed / time: " + (System.currentTimeMillis() - startStoppingTime) + " ms");
                        onError(new MediaError(MediaError.STOP_UNKNOWN, MediaError.UNKNOWN));
                    }
                }
            }
            if (!isPlayerReleased()) {
                releasePlayer(true);
            }
        }
    }

    @Override
    protected void releasePlayer(boolean clearTargetState) {
        synchronized (mLock) {
            logger.d("releasePlayer(), clearTargetState=" + clearTargetState + ", current state: " + mCurrentState);

            checkReleased();

            if (!isReleasingPlayer() && !isPlayerReleased()) {

                mReleasingPlayer = true;
                super.releasePlayer(clearTargetState);

                cancelResetCallback();

                mLastContentUriToOpen = null;
                mLastAssetFileDescriptorToOpen = null;
                mLastModeToOpen = PlayMode.NONE;

                boolean result;
                final long startReleasingTime = System.currentTimeMillis();
                try {
                    result = submitOnExecutor(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            mMediaPlayer.reset();
                            mMediaPlayer.release();
                            return true;
                        }
                    }).get(EXECUTOR_CALL_TIMEOUT_S, TimeUnit.SECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.e("an Exception occurred during get()", e);
//                onError(new MediaError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0));
                    result = false;
                }

                if (result) {
                    logger.d("media player reset/release success / time: " + (System.currentTimeMillis() - startReleasingTime) + " ms");
                } else {
                    logger.e("media player reset/release failed / time: " + (System.currentTimeMillis() - startReleasingTime) + " ms");
                    onError(new MediaError(MediaError.RELEASE_UNKNOWN, MediaError.UNKNOWN));
                }

                stopPlaybackTimeTask();

                mMediaPlayer = null;

                setCurrentState(State.IDLE);
                if (clearTargetState) {
                    setTargetState(State.IDLE);
                }

                if (!abandonAudioFocus()) {
                    logger.e("failed to abandon audio focus");
                }

                mReleasingPlayer = false;
            } else if (isPlayerReleased()) {
                logger.d("already released");
            } else if (isReleasingPlayer()) {
                logger.d("already releasing now");
            }
        }
    }

    /**
     * can be called at any state
     */
    public void release() {
        synchronized (mLock) {
            super.release();
            detachMediaController();
            setContentUri(PlayMode.NONE, null);
            setContentFd(PlayMode.NONE, null);
            setCurrentState(State.RELEASED);
            mContext = null;
        }
    }

    @Override
    @CallSuper
    protected boolean onPrepared() {

        synchronized (mLock) {

            if (!super.onPrepared()) {
                return false;
            }

//                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
//                    logger.i("track info: " + Arrays.toString(mMediaPlayer.getTrackInfo()));
//                }


            Uri resourceUri = getContentUri();

            setCurrentState(State.PREPARED);

            // TODO extract from meta
            if (resourceUri == null || TextUtils.isEmpty(resourceUri.getScheme()) || resourceUri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) {

                mCanPause = true;
                mCanSeekBack = true;
                mCanSeekForward = true;

            }
//            else {
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
//            }

            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }

            final int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call

            if (seekToPosition != POSITION_NO) {
                seekTo(seekToPosition);
            }
            if (CompareUtils.compareFloats(mVolumeLeftWhenPrepared, (float) VOLUME_NOT_SET, true) != 0 && CompareUtils.compareFloats(mVolumeLeftWhenPrepared, (float) VOLUME_NOT_SET, true) != 0) {
                setVolume(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
            }

            mVideoWidth = mMediaPlayer.getVideoWidth();
            mVideoHeight = mMediaPlayer.getVideoHeight();

            if (mPlayMode == PlayMode.AUDIO || mVideoWidth != 0 && mVideoHeight != 0) {
                if (mPlayMode == PlayMode.VIDEO) {
                    if (mVideoView != null) {
                        mVideoView.getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                        mVideoView.requestLayout();
                    }
                }
                if (mPlayMode == PlayMode.AUDIO || mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                    // We didn't actually change the size (it was already at the size
                    // we need), so we won't get a "surface changed" callback, so
                    // start the video here instead of in the callback.
                    logger.d("prepared, url: " + mContentUri + ", descriptor: " + mContentFileDescriptor + ", target state: " + mTargetState);
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

                if (mPlayMode != PlayMode.VIDEO) {
                    throw new IllegalStateException("unsupported " + PlayMode.class.getSimpleName() + ": " + mPlayMode);
                }

                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                logger.d("prepared, url: " + mContentUri + ", descriptor: " + mContentFileDescriptor + ", target state: " + mTargetState);
                if (mTargetState == State.PLAYING) {
                    start();
                }
            }

            return true;
        }
    }

    @Override
    protected void onCompletion() {
        synchronized (mLock) {
            if (!isLooping()) {
                setCurrentState(State.IDLE);
                if (mMediaController != null) {
                    mMediaController.hide();
                }
            }
            super.onCompletion();
        }
    }

    @Override
    protected boolean onError(@NotNull MediaError error) {
        synchronized (mLock) {
            logger.e("target content: " + (mContentUri != null ? mContentUri : mContentFileDescriptor) + " / mode: " + mPlayMode);
            logger.e("last content: " + (mLastContentUriToOpen != null ? mLastContentUriToOpen : mLastAssetFileDescriptorToOpen) + " / mode: " + mLastModeToOpen);

            if (!isPlayerReleased()) {
                releasePlayer(true);
            }
            return super.onError(error);
        }
    }

    public interface OnVideoSizeChangedListener {

        void onVideoSizeChanged(int width, int height);
    }

    private static class OnVideoSizeChangedObservable extends Observable<OnVideoSizeChangedListener> {

        private void dispatchOnVideoSizeChanged(int width, int height) {
            synchronized (observers) {
                for (OnVideoSizeChangedListener l : copyOfObservers()) {
                    l.onVideoSizeChanged(width, height);
                }
            }
        }
    }

    private final SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            logger.d("surfaceChanged(), format=" + format + ", w=" + w + ", h=" + h);

            mSurfaceWidth = w;
            mSurfaceHeight = h;

            boolean isValidState = (mTargetState == State.PLAYING) && isVideoSpecified();
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);

            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != POSITION_NO) {
                    seekTo(mSeekWhenPrepared);
                }
                if (CompareUtils.compareFloats(mVolumeLeftWhenPrepared, (float) VOLUME_NOT_SET, true) != 0 && CompareUtils.compareFloats(mVolumeLeftWhenPrepared, (float) VOLUME_NOT_SET, true) != 0) {
                    setVolume(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
                }
                start();
            }
        }

        public void surfaceCreated(SurfaceHolder holder) {
            logger.d("surfaceCreated()");

            isSurfaceCreated = true;

            if (isVideoSpecified()) {
                openDataSource();
            }
        }

        // after we return from this we can't use the surface any more
        public void surfaceDestroyed(SurfaceHolder holder) {
            logger.d("surfaceDestroyed()");

            mSurfaceWidth = 0;
            mSurfaceHeight = 0;

            if (!isReleased()) {
                stop();
            }
        }
    };

    public static class MediaError extends BaseMediaPlayerController.OnErrorListener.MediaError {

        @ErrorDef
        public final int what;

        public final int extra;

        public MediaError(@ErrorDef int what, int extra) {
            this.what = what;
            this.extra = extra;
        }

        @Override
        public String toString() {
            return "MediaError{" +
                    "what=" + what +
                    ", extra=" + extra +
                    '}';
        }
    }
}