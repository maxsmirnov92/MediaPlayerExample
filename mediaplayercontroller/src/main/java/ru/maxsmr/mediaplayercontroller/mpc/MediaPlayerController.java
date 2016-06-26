package ru.maxsmr.mediaplayercontroller.mpc;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Observable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.MediaController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ru.maxsmr.commonutils.android.SynchronizedObservable;
import ru.maxsmr.commonutils.android.media.MediaStoreInfoRetriever;
import ru.maxsmr.commonutils.android.media.MetadataRetriever;
import ru.maxsmr.commonutils.data.CompareUtils;
import ru.maxsmr.commonutils.data.FileHelper;
import ru.maxsmr.mediaplayercontroller.mpc.receivers.AudioFocusChangeReceiver;
import ru.maxsmr.mediaplayercontroller.mpc.receivers.HeadsetPlugBroadcastReceiver;
import ru.maxsmr.mediaplayercontroller.mpc.receivers.NoisyAudioBroadcastReceiver;
import ru.maxsmr.tasksutils.ScheduledThreadPoolExecutorManager;

public final class MediaPlayerController implements MediaController.MediaPlayerControl {

    private static final Logger logger = LoggerFactory.getLogger(MediaPlayerController.class);

    private final static int EXECUTOR_CALL_TIMEOUT_S = 30;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public final static long DEFAULT_PREPARE_RESET_TIMEOUT_MS = 20000;
    private long mPrepareResetTimeoutMs = DEFAULT_PREPARE_RESET_TIMEOUT_MS;

    public final static long DEFAULT_NOTIFY_PLAYBACK_TIME_INTERVAL_MS = 1000;
    private long mNotifyPlaybackTimeInterval = DEFAULT_NOTIFY_PLAYBACK_TIME_INTERVAL_MS;
    private final ScheduledThreadPoolExecutorManager mPlaybackTimeTask = new ScheduledThreadPoolExecutorManager("PlaybackTimeTask");

    @NonNull
    private final Context mContext;

    @NonNull
    private State mCurrentState = State.IDLE;

    @NonNull
    private State mTargetState = State.IDLE;

    private MediaPlayer mMediaPlayer;

    private Looper mMediaLooper;

    private final Runnable resetRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPreparing()) {
                releasePlayer(true);
            }
        }
    };

    private int mCurrentBufferPercentage = 0;

    @Nullable
    private Uri mAudioUri;

    @Nullable
    private AssetFileDescriptor mAudioFileDescriptor;

    @Nullable
    private Uri mVideoUri;

    @Nullable
    private AssetFileDescriptor mVideoFileDescriptor;

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

    private float mVolumeLeftWhenPrepared = VOLUME_NOT_SET;
    private float mVolumeRightWhenPrepared = VOLUME_NOT_SET;

    public static final float VOLUME_MAX = 1.0f;
    public static final float VOLUME_MIN = 0.0f;
    public static final int VOLUME_NOT_SET = -1;

    public static final int AUDIO_SESSION_EMPTY = -1;

    public static final int POSITION_NO = -1;
    public static final int POSITION_START = 0;

    public static float getAverageVolume() {
        return (VOLUME_MAX + VOLUME_MIN) / 2f;
    }

    @Nullable
    private MediaController mMediaController;
    @Nullable
    private View mAnchorView;

    @NonNull
    private final AudioFocusChangeReceiver mAudioFocusChangeReceiver = new AudioFocusChangeReceiver();

    @NonNull
    private final HeadsetPlugBroadcastReceiver mHeadsetPlugBroadcastReceiver = new HeadsetPlugBroadcastReceiver();

    @NonNull
    private final NoisyAudioBroadcastReceiver mNoisyBroadcastReceiver = new NoisyAudioBroadcastReceiver();

    private boolean mReleasingPlayer = false;

    public boolean isReleasingPlayer() {
        return mReleasingPlayer;
    }

    public boolean isReleased() {
        return mCurrentState == State.RELEASED;
    }

    public MediaPlayerController(@NonNull Context context) {
        mContext = context;
        init();
    }

    private void init() {
        registerReceivers();
    }

    public void registerReceivers() {
        mNoisyBroadcastReceiver.register(mContext);
        mNoisyBroadcastReceiver.getNoisyAudioObservable().registerObserver(mNoisyAudioListener);
        mHeadsetPlugBroadcastReceiver.register(mContext);
        mHeadsetPlugBroadcastReceiver.getHeadsetStateChangedObservable().registerObserver(mHeadsetStateChangeListener);

        final TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Activity.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    public void unregisterReceivers() {
        mNoisyBroadcastReceiver.unregister(mContext);
        mNoisyBroadcastReceiver.getNoisyAudioObservable().unregisterObserver(mNoisyAudioListener);
        mHeadsetPlugBroadcastReceiver.getHeadsetStateChangedObservable().unregisterObserver(mHeadsetStateChangeListener);
        mHeadsetPlugBroadcastReceiver.unregister(mContext);
        final TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Activity.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    public long getPrepareResetTimeoutMs() {
        return mPrepareResetTimeoutMs;
    }

    public void setPrepareResetTimeoutMs(long prepareResetTimeoutMs) {
        if (prepareResetTimeoutMs <= 0) {
            throw new IllegalArgumentException("incorrect prepareResetTimeoutMs: " + prepareResetTimeoutMs);
        }
        this.mPrepareResetTimeoutMs = prepareResetTimeoutMs;
    }

    @NonNull
    public State getCurrentState() {
        return mCurrentState;
    }

    private void setCurrentState(@NonNull State newState) {
        if (newState != mCurrentState) {
            State oldState = mCurrentState;
            mCurrentState = newState;
            logger.info("current state: " + mCurrentState);
            mStateChangedObservable.dispatchCurrentStateChanged(oldState);
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

    private final OnBufferingUpdateObservable mBufferingUpdateObservable = new OnBufferingUpdateObservable();

    @NonNull
    public Observable<OnBufferingUpdateListener> getBufferingUpdateObservable() {
        return mBufferingUpdateObservable;
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
//                    logger.debug("onBufferingUpdate(), percent=" + percent);
                    mCurrentBufferPercentage = percent;
                    mBufferingUpdateObservable.dispatchOnOnBufferingUpdate(mCurrentBufferPercentage);
                }
            };


    private final MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {

        @SuppressWarnings("ConstantConditions")
        @Override
        public void onPrepared(MediaPlayer mp) {
            synchronized (MediaPlayerController.this) {
                logger.debug("onPrepared()");

                new Handler(mMediaLooper).removeCallbacks(resetRunnable);

//                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
//                    logger.info("track info: " + Arrays.toString(mMediaPlayer.getTrackInfo()));
//                }

                if (!isAudioSpecified() && !isVideoSpecified()) {
                    throw new IllegalStateException("audio/video uri or file descriptor is null, wtf?");
                }

                boolean isAudio = isAudioSpecified();
                Uri resourceUri = isAudio ? mAudioUri : mVideoUri;

                setCurrentState(State.PREPARED);

                if (resourceUri == null || TextUtils.isEmpty(resourceUri.getScheme()) || resourceUri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) {

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

                if (CompareUtils.compareFloats(mVolumeLeftWhenPrepared, (float) VOLUME_NOT_SET, true) != 0 && CompareUtils.compareFloats(mVolumeLeftWhenPrepared, (float) VOLUME_NOT_SET, true) != 0) {
                    setVolume(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
                }
            }
        }
    };

    private final PlaybackTimeUpdateTimeObservable playbackTimeUpdateTimeObservable = new PlaybackTimeUpdateTimeObservable();

    public Observable<OnPlaybackTimeUpdateTimeListener> getPlaybackTimeUpdateTimeObservable() {
        return playbackTimeUpdateTimeObservable;
    }

    private final OnCompletionObservable completionObservable = new OnCompletionObservable();

    @NonNull
    public final Observable<OnCompletionListener> getCompletionObservable() {
        return completionObservable;
    }

    private final MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    synchronized (MediaPlayerController.this) {
                        logger.info("onCompletion()");

                        completionObservable.dispatchCompleted();

                        if (!isLooping()) {
                            setCurrentState(State.IDLE);
                            setTargetState(State.IDLE);

                            if (mMediaController != null) {
                                mMediaController.hide();
                            }
                        }
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

                    mErrorObservable.dispatchError(framework_err, impl_err);

                    setCurrentState(State.IDLE);

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
    private final AudioFocusChangeReceiver.OnAudioFocusChangeListener mAudioFocusChangleListener = new AudioFocusChangeReceiver.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusGain() {
            logger.debug("onAudioFocusGain()");
            // resume playback
            handleInterruptEventEnd();
            if (mVolumeLeftWhenPrepared != VOLUME_NOT_SET && mVolumeRightWhenPrepared != VOLUME_NOT_SET) {
                setVolume(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
            }
        }

        @Override
        public void onAudioFocusLoss() {
            logger.debug("onAudioFocusLoss()");
            // Lost focus for an unbounded amount of time: stop playback and release media player
            suspend();
        }

        @Override
        public void onAudioFocusLossTransient() {
            logger.debug("onAudioFocusLossTransient()");
            // Lost focus for a short time, but we have to stop
            // playback. We don't release the media player because playback
            // is likely to resume
            handleInterruptEventStart();
        }

        @Override
        public void onAudioFocusLossTransientCanDuck() {
            logger.debug("onAudioFocusLossTransientCanDuck()");
            // Lost focus for a short time, but it's ok to keep playing
            // at an attenuated level
            float previousVolumeLeft = mVolumeLeftWhenPrepared != VOLUME_NOT_SET ? mVolumeLeftWhenPrepared : getAverageVolume();
            float previousVolumeRight = mVolumeRightWhenPrepared != VOLUME_NOT_SET ? mVolumeRightWhenPrepared : getAverageVolume();
            setVolume(0.1f, 0.1f);
            mVolumeLeftWhenPrepared = previousVolumeLeft;
            mVolumeRightWhenPrepared = previousVolumeRight;
        }
    };

    @NonNull
    private final NoisyAudioBroadcastReceiver.OnNoisyAudioListener mNoisyAudioListener = new NoisyAudioBroadcastReceiver.OnNoisyAudioListener() {

        @Override
        public void onNoisyAudio() {
            logger.debug("onNoisyAudio()");
            stop();
        }
    };

    @NonNull
    private final HeadsetPlugBroadcastReceiver.OnHeadsetStateChangedListener mHeadsetStateChangeListener = new HeadsetPlugBroadcastReceiver.OnHeadsetStateChangedListener() {

        @Override
        public void onHeadphonesPlugged(boolean hasMicrophone) {
            logger.debug("onHeadphonesPlugged(), hasMicrophone=" + hasMicrophone);
            handleInterruptEventEnd();
        }

        @Override
        public void onHeadphonesUnplugged() {
            logger.debug("onHeadphonesUnplugged()");
            handleInterruptEventStart();
        }
    };

    @NonNull
    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            logger.debug("onCallStateChanged(), state=" + state + ", incomingNumber=" + incomingNumber);
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                handleInterruptEventStart();
            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                handleInterruptEventEnd();
            } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            }
        }
    };

    private void handleInterruptEventStart() {
        if (isPreparing() || isPlaying()) {
            State previousState = getCurrentState();
            State previousTargetState = getTargetState();
            pause();
            if (previousState == State.PLAYING || previousTargetState == State.PLAYING) {
                setTargetState(State.PLAYING);
            }
        }
    }

    private void handleInterruptEventEnd() {
        if (mTargetState == State.PLAYING) {
            start();
        }
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

    public void setLooping(boolean toggle) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setLooping(toggle);
        }
        mLoopWhenPreparing = toggle;
    }

    @NonNull
    public Pair<Float, Float> getPreparedVolume() {
        return new Pair<>(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
    }

    public void setVolume(float left, float right) {
        if (left < VOLUME_MIN || left > VOLUME_MAX) {
            throw new IllegalArgumentException("incorrect left volume: " + left);
        }

        if (right < VOLUME_MIN || right > VOLUME_MAX) {
            throw new IllegalArgumentException("incorrect right volume: " + left);
        }

        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(left, right);
        }
        mVolumeLeftWhenPrepared = left;
        mVolumeRightWhenPrepared = right;
    }

    @Nullable
    public Uri getContentUri() {
        return isAudioSpecified() ? getAudioUri() : getVideoUri();
    }

    @Nullable
    public AssetFileDescriptor getContentAssetFileDescriptor() {
        return isAudioSpecified() ? getAudioFileDescriptor() : getVideoFileDescriptor();
    }

    public boolean isAudioSpecified() {
        return mAudioUri != null || mAudioFileDescriptor != null;
    }

    @Nullable
    public Uri getAudioUri() {
        return mAudioUri;
    }

    @Nullable
    public AssetFileDescriptor getAudioFileDescriptor() {
        return mAudioFileDescriptor;
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

    public void setAudioUriByMediaFileId(int id, boolean isExternal) {
        setAudioUriByMediaFileInfo(new MediaStoreInfoRetriever.MediaFileInfo(id, isExternal));
    }

    public void setAudioUriByMediaFileInfo(@Nullable MediaStoreInfoRetriever.MediaFileInfo info) {
        setAudioUri(info != null ? info.getContentUri() : null);
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

            this.mVideoFileDescriptor = null;
            this.mVideoUri = null;
            this.mAudioFileDescriptor = null;
            this.mAudioUri = audioUri;
            this.mHeaders = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<String, String>();

            if (mCurrentState != State.IDLE) {
                if (isAudioSpecified()) {
                    openDataSource();
                } else {
                    stop();
                }
            }
        }
    }

    public void setAudioRawId(@RawRes int audioRawResId) {
        if (audioRawResId > 0) {
            setAudioFd(mContext.getResources().openRawResourceFd(audioRawResId));
        }
    }

    public void setAudioAsset(String assetName) {
        AssetFileDescriptor fd = null;
        try {
            fd = mContext.getAssets().openNonAssetFd(assetName);
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("can't open asset: " + assetName);
        }
        if (fd != null) {
            setAudioFd(fd);
        }
    }

    public synchronized void setAudioFd(@Nullable AssetFileDescriptor audioFd) {

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (audioFd != null ? !audioFd.equals(this.mAudioFileDescriptor) : this.mAudioFileDescriptor != null) {

            this.mVideoFileDescriptor = null;
            this.mVideoUri = null;
            this.mAudioFileDescriptor = audioFd;
            this.mAudioUri = null;
            this.mHeaders = new LinkedHashMap<>();

            if (mCurrentState != State.IDLE) {
                if (isAudioSpecified()) {
                    openDataSource();
                } else {
                    stop();
                }
            }
        }
    }

    public boolean isVideoSpecified() {
        return mVideoUri != null || mVideoFileDescriptor != null;
    }

    @Nullable
    public Uri getVideoUri() {
        return mVideoUri;
    }

    @Nullable
    public AssetFileDescriptor getVideoFileDescriptor() {
        return mVideoFileDescriptor;
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

    public void setVideoUriByMediaFileId(int id, boolean isExternal) {
        setVideoUriByMediaFileInfo(new MediaStoreInfoRetriever.MediaFileInfo(id, isExternal));
    }

    public void setVideoUriByMediaFileInfo(@Nullable MediaStoreInfoRetriever.MediaFileInfo info) {
        setVideoUri(info != null ? info.getContentUri() : null);
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

            this.mAudioFileDescriptor = null;
            this.mAudioUri = null;
            this.mVideoFileDescriptor = null;
            this.mVideoUri = videoUri;
            this.mHeaders = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<String, String>();

            if (mCurrentState != State.IDLE) {
                if (isVideoSpecified()) {
                    openDataSource();
                } else {
                    stop();
                }
            }
        }
    }

    public void setVideoRawId(@RawRes int videoRawResId) {
        if (videoRawResId > 0) {
            setVideoFd(mContext.getResources().openRawResourceFd(videoRawResId));
        }
    }

    public void setVideoAsset(String assetName) {
        AssetFileDescriptor fd = null;
        try {
            fd = mContext.getAssets().openNonAssetFd(assetName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (fd != null) {
            setVideoFd(fd);
        }
    }

    public synchronized void setVideoFd(@Nullable AssetFileDescriptor videoFd) {

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (videoFd != null ? !videoFd.equals(this.mVideoFileDescriptor) : this.mVideoFileDescriptor != null) {

            this.mAudioFileDescriptor = null;
            this.mAudioUri = null;
            this.mVideoFileDescriptor = videoFd;
            this.mVideoUri = null;
            this.mHeaders = new LinkedHashMap<>();

            if (mCurrentState != State.IDLE) {
                if (isVideoSpecified()) {
                    openDataSource();
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
        mVideoSizeChangedListener.onVideoSizeChanged(mMediaPlayer, 0, 0);

        if (mVideoView != null) {
            mVideoView.getHolder().setKeepScreenOn(true);
            mVideoView.getHolder().addCallback(mSHCallback);
        }
    }

    @Override
    public void seekTo(int msec) {
        logger.debug("seekTo(), msec=" + msec);
        try {
            seekToInternal(msec);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            logger.error("an IllegalArgumentException occurred during seekToInternal()", e);
        }
    }

    private synchronized void seekToInternal(int msec) {
        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }
        if (msec < POSITION_START && msec != POSITION_NO)
            throw new IllegalArgumentException("incorrect seek position: " + msec);

        if (isInPlaybackState()) {
            if (msec >= POSITION_START && msec <= mMediaPlayer.getDuration()) {
                mMediaPlayer.seekTo(msec);
            } else if (msec != POSITION_NO) {
                throw new IllegalArgumentException("incorrect seek position: " + msec);
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

    /**
     * events callbacks depends on thread, on which called this method
     */
    private synchronized void openDataSource() {
        logger.debug("openDataSource()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (!isAudioSpecified() && !isVideoSpecified()) {
            throw new IllegalStateException("audio/video uri or file descriptor is not specified");
        }

        if (!isPreparing()) {

            boolean isAudio = isAudioSpecified();

            // we shouldn't clear the target state, because somebody might have
            // called start() previously
            suspend();

//            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
//            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            mAudioFocusChangeReceiver.requestFocus(mContext);

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

                if (isAudio) {

                    if (mAudioUri != null) {
                        logger.debug("audio data source: " + mAudioUri);
                        mMediaPlayer.setDataSource(mContext, mAudioUri, mHeaders);
                    } else if (mAudioFileDescriptor != null) {
                        logger.debug("audio data source: " + mAudioFileDescriptor);
                        mMediaPlayer.setDataSource(mAudioFileDescriptor.getFileDescriptor(), mAudioFileDescriptor.getStartOffset(), mAudioFileDescriptor.getLength());
                    } else {
                        throw new AssertionError("audio data source not specified");
                    }

                    mMediaPlayer.setOnVideoSizeChangedListener(null);
                    mMediaPlayer.setDisplay(null);

                } else {

                    if (mVideoView == null || !isSurfaceCreated()) {
                        throw new IllegalStateException("surface was not created");
                    }

                    if (mVideoUri != null) {
                        logger.debug("video data source: " + mVideoUri);
                        mMediaPlayer.setDataSource(mContext, mVideoUri, mHeaders);
                    } else if (mVideoFileDescriptor != null) {
                        logger.debug("video data source: " + mVideoFileDescriptor);
                        mMediaPlayer.setDataSource(mVideoFileDescriptor.getFileDescriptor(), mVideoFileDescriptor.getStartOffset(), mVideoFileDescriptor.getLength());
                    } else {
                        throw new AssertionError("video data source not specified");
                    }

                    mMediaPlayer.setOnVideoSizeChangedListener(mVideoSizeChangedListener);
                    mMediaPlayer.setDisplay(mVideoView.getHolder());
                }

                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setScreenOnWhilePlaying(!isAudio);
                mMediaPlayer.setLooping(mLoopWhenPreparing);

                try {
                    result = mExecutor.submit(new Callable<Boolean>() {

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
                logger.error("Unable to open content: " + (isAudioSpecified() ? mAudioUri : mVideoUri), ex);
                mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                result = false;
            }

            if (result) {
                logger.debug("media player preparing time: " + (System.currentTimeMillis() - startPreparingTime) + " ms");
                // we don't set the target state here either, but preserve the
                // target state that was there before.
                setCurrentState(State.PREPARING);
                attachMediaController();
                new Handler(mMediaLooper).postDelayed(resetRunnable, mPrepareResetTimeoutMs);
            } else {
                releasePlayer(true);
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Nullable
    public MetadataRetriever.MediaMetadata getCurrentTrackMetatada() {
        return isAudioSpecified() && (TextUtils.isEmpty(mAudioUri.getScheme()) || mAudioUri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) ?
                MetadataRetriever.extractMetaData(mContext, mAudioUri) : (
                isVideoSpecified() && (TextUtils.isEmpty(mVideoUri.getScheme()) || mVideoUri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) ?
                        MetadataRetriever.extractMetaData(mContext, mVideoUri) : null
        );
    }

    @Override
    public synchronized void start() {
        logger.debug("start()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mCurrentState != State.PLAYING) {
            if (isInPlaybackState()) {
                boolean result;
                final long startStartingTime = System.currentTimeMillis();
                try {
                    result = mExecutor.submit(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            mMediaPlayer.start();
                            return true;
                        }
                    }).get(EXECUTOR_CALL_TIMEOUT_S, TimeUnit.SECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("an Exception occurred during get()", e);
                    mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                    result = false;
                }
                if (result) {
                    logger.debug("media player starting time: " + (System.currentTimeMillis() - startStartingTime) + " ms");
                    setCurrentState(State.PLAYING);
                    startPlaybackTimeTask();
                } else {
                    releasePlayer(true);
                }
            }
            setTargetState(State.PLAYING);
        }
    }

    @Override
    public synchronized void pause() {
        logger.debug("pause()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mCurrentState != State.PAUSED) {
            if (isPlaying()) {
                boolean result;
                final long startPausingTime = System.currentTimeMillis();
                try {
                    result = mExecutor.submit(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            mMediaPlayer.pause();
                            return true;
                        }
                    }).get(EXECUTOR_CALL_TIMEOUT_S, TimeUnit.SECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("an Exception occurred during get()", e);
                    mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                    result = false;
                }
                if (result) {
                    logger.debug("media player pausing time: " + (System.currentTimeMillis() - startPausingTime) + " ms");
                    stopPlaybackTimeTask();
                    setCurrentState(State.PAUSED);
                } else {
                    releasePlayer(true);
                }
            }
            setTargetState(State.PAUSED);
        }
    }

    public synchronized void stop() {
        logger.debug("stop()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mCurrentState != State.IDLE) {
            if (isInPlaybackState()) {
                boolean result;
                final long startStoppingTime = System.currentTimeMillis();
                try {
                    result = mExecutor.submit(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            mMediaPlayer.stop();
                            return true;
                        }
                    }).get(EXECUTOR_CALL_TIMEOUT_S, TimeUnit.SECONDS);

                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("an Exception occurred during get()", e);
                    mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                    result = false;
                }
                if (result) {
                    logger.debug("media player stopping time: " + (System.currentTimeMillis() - startStoppingTime) + " ms");
                }
            }
            releasePlayer(true);
        }
    }

    public void resume() {
        logger.debug("resume()");
        if (!(isPreparing() || isInPlaybackState())) {
            openDataSource();
        }
    }

    public void suspend() {
        logger.debug("suspend()");
        releasePlayer(false);
    }

    public void setNotifyPlaybackTimeInterval(long intervalMs) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("incorrect intervalMs: " + intervalMs);
        }
        if (intervalMs != mNotifyPlaybackTimeInterval) {
            mNotifyPlaybackTimeInterval = intervalMs;
            if (mPlaybackTimeTask.getIntervalMs() != intervalMs && isPlaying()) {
                restartPlaybackTimeTask();
            }
        }
    }

    private boolean isPlaybackTimeTaskRunning() {
        return mPlaybackTimeTask.isRunning();
    }

    private void restartPlaybackTimeTask() {

        stopPlaybackTimeTask();

        mPlaybackTimeTask.addRunnableTask(new Runnable() {

            private volatile AssetFileDescriptor lastFd;
            private volatile Uri lastUri;
            private volatile long lastPositionMs = 0;

            @Override
            public void run() {
                doUpdate();
            }

            private void doUpdate() {
//                logger.debug("doUpdate()");
                if (isPlaying()) {

                    final long currentDuration = getDuration();
                    final long currentPosition = getCurrentPosition();

                    final Uri currentUri = getContentUri();
                    final AssetFileDescriptor currentFd = getContentAssetFileDescriptor();

                    if (currentUri != null && !currentUri.equals(lastUri) || currentFd != null && !currentFd.equals(lastFd)) {
                        lastUri = currentUri;
                        lastFd = currentFd;
                        lastPositionMs = 0;
                    }

                    if (isLooping() && (currentPosition < lastPositionMs || currentPosition >= currentDuration)) {
                        mCompletionListener.onCompletion(mMediaPlayer);
                    }

                    lastPositionMs = currentPosition;

                    new Handler(mMediaLooper).post(new Runnable() {
                        @Override
                        public void run() {
                            playbackTimeUpdateTimeObservable.dispatchPlaybackTimeUpdated();
                        }
                    });
                }
            }
        });
        mPlaybackTimeTask.start(mNotifyPlaybackTimeInterval);
    }

    private void startPlaybackTimeTask() {
        if (!isPlaybackTimeTaskRunning()) {
            restartPlaybackTimeTask();
        }
    }

    private void stopPlaybackTimeTask() {
        if (isPlaybackTimeTaskRunning()) {
            mPlaybackTimeTask.stop(false, 0);
            mPlaybackTimeTask.removeAllRunnableTasks();
        }
    }

    /*
     * release the media player in any state
     */
    private synchronized void releasePlayer(boolean clearTargetState) {
        logger.debug("releasePlayer(), clearTargetState=" + clearTargetState);

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (mMediaPlayer != null) {

            mReleasingPlayer = true;

            boolean result;
            final long startReleasingTime = System.currentTimeMillis();
            try {
                result = mExecutor.submit(new Callable<Boolean>() {
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
                mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                result = false;
            }
            if (result) {
                logger.debug("media player reset/release time: " + (System.currentTimeMillis() - startReleasingTime) + " ms");
            }

            stopPlaybackTimeTask();

            mMediaLooper = null;
            mMediaPlayer = null;

            mCurrentBufferPercentage = 0;

            mCanPause = true;
            mCanSeekBack = false;
            mCanSeekForward = false;

            setCurrentState(State.IDLE);
            if (clearTargetState) {
                setTargetState(State.IDLE);
            }

//            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
//            am.abandonAudioFocus(null);
            mAudioFocusChangeReceiver.abandonFocus(mContext);

            mReleasingPlayer = false;
        }
    }

    /**
     * can be called in any state
     */
    public synchronized void release() {
        logger.debug("release()");

        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was already released");
        }

        releasePlayer(true);
        detachMediaController();

        unregisterReceivers();

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

        void onCurrentStateChanged(@NonNull State currentState, @NonNull State previousState);

        void onTargetStateChanged(@NonNull State targetState);
    }

    private class OnStateChangedObservable extends SynchronizedObservable<OnStateChangedListener> {

        private void dispatchCurrentStateChanged(@NonNull State previousState) {
            synchronized (mObservers) {
                for (OnStateChangedListener l : copyOfObservers()) {
                    l.onCurrentStateChanged(mCurrentState, previousState);
                }
            }
        }

        private void dispatchTargetStateChanged() {
            synchronized (mObservers) {
                for (OnStateChangedListener l : copyOfObservers()) {
                    l.onTargetStateChanged(mTargetState);
                }
            }
        }
    }

    public interface OnPlaybackTimeUpdateTimeListener {

        /**
         * called not from main thread
         */
        void onPlaybackTimeUpdateTime(int position, int duration);
    }

    private class PlaybackTimeUpdateTimeObservable extends SynchronizedObservable<OnPlaybackTimeUpdateTimeListener> {

        private void dispatchPlaybackTimeUpdated() {
//            logger.debug("dispatchPlaybackTimeUpdated(), position=" + getCurrentPosition() + ", duration=" + getDuration());
            synchronized (mObservers) {
                for (OnPlaybackTimeUpdateTimeListener l : copyOfObservers()) {
                    l.onPlaybackTimeUpdateTime(getCurrentPosition(), getDuration());
                }
            }
        }
    }

    public interface OnCompletionListener {

        /**
         * @param isLooping if track looping mode set
         */
        void onCompletion(boolean isLooping);
    }

    private class OnCompletionObservable extends SynchronizedObservable<OnCompletionListener> {

        private void dispatchCompleted() {
            synchronized (mObservers) {
                for (OnCompletionListener l : copyOfObservers()) {
                    l.onCompletion(isLooping());
                }
            }
        }

    }

    public interface OnErrorListener {

        void onError(int what, int extra);
    }

    private static class OnErrorObservable extends SynchronizedObservable<OnErrorListener> {

        private void dispatchError(int what, int extra) {
            synchronized (mObservers) {
                for (OnErrorListener l : copyOfObservers()) {
                    l.onError(what, extra);
                }
            }
        }
    }

    public interface OnBufferingUpdateListener {

        void onBufferingUpdate(int percentage);
    }

    private static class OnBufferingUpdateObservable extends SynchronizedObservable<OnBufferingUpdateListener> {

        private void dispatchOnOnBufferingUpdate(int percentage) {
            synchronized (mObservers) {
                for (OnBufferingUpdateListener l : copyOfObservers()) {
                    l.onBufferingUpdate(percentage);
                }
            }
        }
    }

    public interface OnVideoSizeChangedListener {

        void onVideoSizeChanged(int width, int height);
    }

    private static class OnVideoSizeChangedObservable extends SynchronizedObservable<OnVideoSizeChangedListener> {

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
}
