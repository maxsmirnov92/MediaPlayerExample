package net.maxsmr.mediaplayercontroller.mpc.nativeplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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

    public MediaPlayerController(@NonNull Context context) {
        super(context);
    }

    private final static int EXECUTOR_CALL_TIMEOUT_S = 30;
//    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private MediaPlayer mMediaPlayer;

    private final Runnable mResetRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("mResetRunnable :: run()");
            postOnMediaHandler(new Runnable() {
                @Override
                public void run() {
                    if (isPreparing()) {
                        logger.debug("resetting by timeout...");
                        onError(new MediaError(MediaError.PREPARE_TIMEOUT_EXCEEDED, MediaError.UNKNOWN));
                    }
                }
            });
        }
    };

    private boolean isSurfaceCreated = false;

    @Nullable
    private SurfaceView mVideoView;

    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mSurfaceWidth = 0;
    private int mSurfaceHeight = 0;

    @Nullable
    private MediaController mMediaController;

    @Nullable
    private View mAnchorView;

    public boolean isPlayerReleased() {
        return mCurrentState == State.IDLE || mMediaPlayer == null || isReleased();
    }

    @NonNull
    private final OnVideoSizeChangedObservable mVideoSizeChangedObservable = new OnVideoSizeChangedObservable();

    private final MediaPlayer.OnVideoSizeChangedListener mVideoSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    logger.debug("onVideoSizeChanged(), width=" + width + ", height=" + height);
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
            new MediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    MediaPlayerController.this.onBufferingUpdate(percent);
                }
            };


    private final MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {

        @SuppressWarnings("ConstantConditions")
        @Override
        public void onPrepared(MediaPlayer mp) {
            MediaPlayerController.this.onPrepared();
        }
    };

    private final MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    MediaPlayerController.this.onCompletion();
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
                    return MediaPlayerController.this.onError(new MediaError(framework_err, impl_err));
                }
            };


    @NonNull
    public final Observable<OnVideoSizeChangedListener> getVideoSizeChangedObservable() {
        return mVideoSizeChangedObservable;
    }

    @Override
    public synchronized boolean isContentSpecified() {
        return isAudioSpecified() || isVideoSpecified();
    }

    @Override
    public synchronized void setVolume(float left, float right) {
        super.setVolume(left, right);
        if (isInPlaybackState()) {
            mMediaPlayer.setVolume(left, right);
        }
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
        return (mMediaPlayer != null && !isReleasingPlayer() &&
                mCurrentState != State.RELEASED &&
                mCurrentState != State.IDLE &&
                mCurrentState != State.PREPARING);
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
    public synchronized void setLooping(boolean toggle) {
        super.setLooping(toggle);
        if (mMediaPlayer != null) {
            mMediaPlayer.setLooping(toggle);
        }
    }

    @Override
    public boolean isPlayModeSupported(@NonNull PlayMode playMode) {
        return playMode == PlayMode.AUDIO || playMode == PlayMode.VIDEO;
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
        mVideoSizeChangedListener.onVideoSizeChanged(mMediaPlayer, 0, 0);

        if (mVideoView != null) {
            mVideoView.getHolder().setKeepScreenOn(true);
            mVideoView.getHolder().addCallback(mSHCallback);
        }
    }

    @Override
    public synchronized void seekTo(int msec) {
        logger.debug("seekTo(), msec=" + msec);
        try {
            seekToInternal(msec);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            logger.error("an IllegalArgumentException occurred during seekToInternal()", e);
        }
    }

    private synchronized void seekToInternal(int msec) {
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

    @Override
    public int getAudioSessionId() {
        return mMediaPlayer != null ? mMediaPlayer.getAudioSessionId() : AUDIO_SESSION_EMPTY;
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

    @Nullable
    public MediaController getMediaController() {
        return mMediaController;
    }

    /**
     * @param controller must already has parent
     */
    public void setMediaController(MediaController controller, View anchorView) {

        checkReleased();

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

    /**
     * events callbacks depends on thread, on which called this method
     */
    @Override
    protected synchronized void openDataSource() {
        logger.debug("openDataSource()");

        checkReleased();

        if (!isContentSpecified()) {
            logger.error("can't open data source: content is not specified");
            return;
        }

        if (!isPreparing()) {

            setCurrentState(State.PREPARING);

            if (mContentUri != null) {

                String contentType = HttpURLConnection.guessContentTypeFromName(mContentUri.toString());
                logger.info("uri content type: " + contentType);

                if (mNoCheckMediaContentType || TextUtils.isEmpty(contentType)) {
                    logger.error("empty uri content type");
                    onError(new MediaError(MediaError.PREPARE_EMPTY_CONTENT_TYPE, MediaError.UNKNOWN));
                    return;
                }
            }

            // we shouldn't clear the target state, because somebody might have
            // called start() previously
            suspend();

            if (!requestAudioFocus()) {
                logger.error("failed to request audio focus");
            }

            mMediaLooper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();

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
                    logger.debug("content data source: " + mContentUri);
                    mMediaPlayer.setDataSource(mContext, mContentUri, mContentHeaders);
                } else if (mContentFileDescriptor != null) {
                    logger.debug("content data source: " + mContentFileDescriptor);
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
                    logger.error("an Exception occurred during get()", e);
                    throw new RuntimeException(e);
                }

            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                ex.printStackTrace();
                logger.error("Unable to open content: " + (mContentUri != null ? mContentUri : mContentFileDescriptor), ex);
                result = false;
            }

            if (result) {
                logger.debug("media player preparing start success / time: " + (System.currentTimeMillis() - startPreparingTime) + " ms");
                // we don't set the target state here either, but preserve the
                // target state that was there before.
                attachMediaController();
                scheduleResetCallback(mResetRunnable);
            } else {
                logger.error("media player preparing start failed / time: " + (System.currentTimeMillis() - startPreparingTime) + " ms");
                onError(new MediaError(MediaError.PREPARE_UNKNOWN, MediaError.UNKNOWN));
            }
        }
    }

    @Override
    public synchronized void start() {
        logger.debug("start()");

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
                    logger.error("an Exception occurred during get()", e);
//                    onError(new MediaError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0));
                    result = false;
                }
                if (result) {
                    logger.debug("media player starting success / time: " + (System.currentTimeMillis() - startStartingTime) + " ms");
                    setCurrentState(State.PLAYING);
                    startPlaybackTimeTask();
                } else {
                    logger.error("media player starting failed / time: " + (System.currentTimeMillis() - startStartingTime) + " ms");
                    onError(new MediaError(MediaError.PLAY_UNKNOWN, MediaError.UNKNOWN));
                }
            }
        }
        setTargetState(State.PLAYING);
    }

    @Override
    public synchronized void pause() {
        logger.debug("pause()");

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
                    logger.error("an Exception occurred during get()", e);
//                    onError(new MediaError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0));
                    result = false;
                }
                if (result) {
                    logger.debug("media player pausing success / time: " + (System.currentTimeMillis() - startPausingTime) + " ms");
                    stopPlaybackTimeTask();
                    setCurrentState(State.PAUSED);
                } else {
                    logger.error("media player pausing failed / time: " + (System.currentTimeMillis() - startPausingTime) + " ms");
                    onError(new MediaError(MediaError.PAUSE_UNKNOWN, MediaError.UNKNOWN));
                }
            }
        }
        setTargetState(State.PAUSED);
    }

    public synchronized void stop() {
        logger.debug("stop()");

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
                    logger.error("an Exception occurred during get()", e);
//                    onError(new MediaError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0));
                    result = false;
                }
                if (result) {
                    logger.debug("media player stopping success / time: " + (System.currentTimeMillis() - startStoppingTime) + " ms");
                } else {
                    logger.error("media player stopping failed / time: " + (System.currentTimeMillis() - startStoppingTime) + " ms");
                    onError(new MediaError(MediaError.STOP_UNKNOWN, MediaError.UNKNOWN));
                }
            }
        }
        if (!isPlayerReleased()) {
            releasePlayer(true);
        }
    }

    @Override
    protected synchronized void releasePlayer(boolean clearTargetState) {
        logger.debug("releasePlayer(), clearTargetState=" + clearTargetState);

        checkReleased();

        if (!isReleasingPlayer() && !isPlayerReleased()) {

            mReleasingPlayer = true;
            super.releasePlayer(clearTargetState);

            cancelResetCallback();

            mLastContentUriToOpen = null;
            mLastAssetFileDescriptorToOpen = null;

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
                logger.error("an Exception occurred during get()", e);
//                onError(new MediaError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0));
                result = false;
            }

            if (result) {
                logger.debug("media player reset/release success / time: " + (System.currentTimeMillis() - startReleasingTime) + " ms");
            } else {
                logger.error("media player reset/release failed / time: " + (System.currentTimeMillis() - startReleasingTime) + " ms");
                onError(new MediaError(MediaError.RELEASE_UNKNOWN, MediaError.UNKNOWN));
            }

            stopPlaybackTimeTask();

            mMediaLooper = null;
            mMediaPlayer = null;

            setCurrentState(State.IDLE);
            if (clearTargetState) {
                setTargetState(State.IDLE);
            }

            if (!abandonAudioFocus()) {
                logger.error("failed to abandon audio focus");
            }

            mReleasingPlayer = false;
        } else if (isPlayerReleased()) {
            logger.debug("already released");
        } else if (isReleasingPlayer()) {
            logger.debug("already releasing now");
        }
    }

    /**
     * can be called at any state
     */
    public synchronized void release() {
        super.release();

        detachMediaController();

        setContentUri(PlayMode.NONE, null);
        setContentFd(PlayMode.NONE, null);

        setCurrentState(State.RELEASED);

        mContext = null;
    }

    @Override
    protected synchronized boolean onPrepared() {

        if (!super.onPrepared()) {
            return false;
        }

//                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
//                    logger.info("track info: " + Arrays.toString(mMediaPlayer.getTrackInfo()));
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
                logger.debug("prepared, url: " + mContentUri + ", descriptor: " + mContentFileDescriptor + ", target state: " + mTargetState);
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
            logger.debug("prepared, url: " + mContentUri + ", descriptor: " + mContentFileDescriptor + ", target state: " + mTargetState);
            if (mTargetState == State.PLAYING) {
                start();
            }
        }

        return true;
    }

    @Override
    protected synchronized void onCompletion() {
        if (!isLooping()) {
            setCurrentState(State.IDLE);
            if (mMediaController != null) {
                mMediaController.hide();
            }
        }
        super.onCompletion();
    }

    @Override
    protected synchronized boolean onError(@NonNull MediaError error) {
        if (!isReleasingPlayer()) {
            releasePlayer(true);
        }
        return super.onError(error);
    }

    public interface OnVideoSizeChangedListener {

        void onVideoSizeChanged(int width, int height);
    }

    private static class OnVideoSizeChangedObservable extends Observable<OnVideoSizeChangedListener> {

        private void dispatchOnVideoSizeChanged(int width, int height) {
            synchronized (mObservers) {
                for (OnVideoSizeChangedListener l : copyOfObservers()) {
                    l.onVideoSizeChanged(width, height);
                }
            }
        }
    }

    private final SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            logger.debug("surfaceChanged(), format=" + format + ", w=" + w + ", h=" + h);

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
            logger.debug("surfaceCreated()");

            isSurfaceCreated = true;

            if (isVideoSpecified()) {
                openDataSource();
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