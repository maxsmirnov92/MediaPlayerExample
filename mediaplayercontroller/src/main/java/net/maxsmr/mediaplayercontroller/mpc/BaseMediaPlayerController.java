package net.maxsmr.mediaplayercontroller.mpc;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.MediaController;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.RawRes;
import androidx.core.content.ContextCompat;

import net.maxsmr.commonutils.android.media.MediaStoreInfoRetriever;
import net.maxsmr.commonutils.android.media.MetadataRetriever;
import net.maxsmr.commonutils.data.CompareUtils;
import net.maxsmr.commonutils.data.FileHelper;
import net.maxsmr.commonutils.data.Observable;
import net.maxsmr.commonutils.logger.BaseLogger;
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder;
import net.maxsmr.mediaplayercontroller.mpc.receivers.AudioFocusChangeReceiver;
import net.maxsmr.mediaplayercontroller.mpc.receivers.HeadsetPlugBroadcastReceiver;
import net.maxsmr.mediaplayercontroller.mpc.receivers.NoisyAudioBroadcastReceiver;
import net.maxsmr.tasksutils.ScheduledThreadPoolExecutorManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class BaseMediaPlayerController<E extends BaseMediaPlayerController.OnErrorListener.MediaError> implements MediaController.MediaPlayerControl {

    protected final BaseLogger logger = BaseLoggerHolder.getInstance().getLogger(getClass());

    public final static long DEFAULT_PREPARE_RESET_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15);

    public final static long DEFAULT_NOTIFY_PLAYBACK_TIME_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);

    public static final int AUDIO_SESSION_EMPTY = -1;

    public static final int POSITION_NO = -1;
    public static final int POSITION_START = 0;

    public static final float VOLUME_MAX = 1.0f;
    public static final float VOLUME_MIN = 0.0f;
    public static final int VOLUME_NOT_SET = -1;


    @NotNull
    protected final Object mLock = new Object();

    protected Context mContext;

    protected boolean mReleasingPlayer = false;

    protected int mSeekWhenPrepared = POSITION_NO;  // set the seek position after prepared

    private long mNotifyPlaybackTimeInterval = DEFAULT_NOTIFY_PLAYBACK_TIME_INTERVAL_MS;

    private final ScheduledThreadPoolExecutorManager mPlaybackTimeTask = new ScheduledThreadPoolExecutorManager(ScheduledThreadPoolExecutorManager.ScheduleMode.FIXED_RATE, "PlaybackTimeTask");

    private ScheduledExecutorService mExecutorService;

    protected boolean mLoopWhenPreparing = false; // set looping property while preparing

    @NotNull
    protected final Looper mMediaLooper;

    @NotNull
    protected final Handler mMediaHandler;

    @NotNull
    protected State mCurrentState = State.IDLE;

    @NotNull
    protected State mTargetState = State.IDLE;

    protected int mCurrentBufferPercentage = 0;

    @NotNull
    protected PlayMode mPlayMode = PlayMode.NONE;

    protected boolean mNoCheckMediaContentType;

    @Nullable
    protected Uri mContentUri;

    @Nullable
    protected AssetFileDescriptor mContentFileDescriptor;

    @NotNull
    protected PlayMode mLastModeToOpen = PlayMode.NONE;

    @Nullable
    protected Uri mLastContentUriToOpen = null;

    @Nullable
    protected AssetFileDescriptor mLastAssetFileDescriptorToOpen = null;

    @NotNull
    protected Map<String, String> mContentHeaders = new LinkedHashMap<>();

    protected boolean mCanPause = true;

    protected boolean mCanSeekBack = false;

    protected boolean mCanSeekForward = false;

    protected float mVolumeLeftWhenPrepared = VOLUME_NOT_SET;
    protected float mVolumeRightWhenPrepared = VOLUME_NOT_SET;

    protected long mPrepareResetTimeoutMs = DEFAULT_PREPARE_RESET_TIMEOUT_MS;

    private Future<?> mResetFuture;

    private boolean mReactOnExternalEvents = true;

    private boolean mInterrupted = false;

    @NotNull
    private final AudioFocusChangeReceiver mAudioFocusChangeReceiver = new AudioFocusChangeReceiver();

    @NotNull
    private final HeadsetPlugBroadcastReceiver mHeadsetPlugBroadcastReceiver = new HeadsetPlugBroadcastReceiver();

    @NotNull
    private final NoisyAudioBroadcastReceiver mNoisyBroadcastReceiver = new NoisyAudioBroadcastReceiver();

    @NotNull
    protected final OnStateChangedObservable mStateChangedObservable = new OnStateChangedObservable();

    @NotNull
    protected final OnCompletionObservable mCompletionObservable = new OnCompletionObservable();

    @NotNull
    protected final OnBufferingUpdateObservable mBufferingUpdateObservable = new OnBufferingUpdateObservable();

    @NotNull
    protected final PlaybackTimeUpdateTimeObservable mPlaybackTimeUpdateTimeObservable = new PlaybackTimeUpdateTimeObservable();

    @NotNull
    protected final OnErrorObservable<E> mErrorObservable = new OnErrorObservable<>();

    protected BaseMediaPlayerController(@NotNull Context context, @Nullable Looper mediaLooper) {
        this.mContext = context;
        this.mMediaLooper = mediaLooper == null? Looper.getMainLooper() : mediaLooper;
        this.mMediaHandler = new Handler(this.mMediaLooper);
        this.init();
    }

    @NotNull
    private final AudioFocusChangeReceiver.OnAudioFocusChangeListener mAudioFocusChangeListener = new AudioFocusChangeReceiver.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusGain() {
            logger.d("onAudioFocusGain()");
            // resume playback
            if (mVolumeLeftWhenPrepared != VOLUME_NOT_SET && mVolumeRightWhenPrepared != VOLUME_NOT_SET) {
                setVolume(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
            }
            handleInterruptEventEnd();
        }

        @Override
        public void onAudioFocusLoss() {
            logger.d("onAudioFocusLoss()");
            if (mReactOnExternalEvents) {
                // Lost focus for an unbounded amount of time: stop playback and release media player
                suspend();
            } else {
                logger.d("reacting on external events is disabled");
            }
        }

        @Override
        public void onAudioFocusLossTransient() {
            logger.d("onAudioFocusLossTransient()");
            if (mReactOnExternalEvents) {
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                handleInterruptEventStart();
            } else {
                logger.d("reacting on external events is disabled");
            }
        }

        @Override
        public void onAudioFocusLossTransientCanDuck() {
            logger.d("onAudioFocusLossTransientCanDuck()");
            if (mReactOnExternalEvents) {
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                float previousVolumeLeft = mVolumeLeftWhenPrepared != VOLUME_NOT_SET ? mVolumeLeftWhenPrepared : getAverageVolume();
                float previousVolumeRight = mVolumeRightWhenPrepared != VOLUME_NOT_SET ? mVolumeRightWhenPrepared : getAverageVolume();
                setVolume(0.1f, 0.1f);
                mVolumeLeftWhenPrepared = previousVolumeLeft;
                mVolumeRightWhenPrepared = previousVolumeRight;
            } else {
                logger.d("reacting on external events is disabled");
            }
        }
    };

    @NotNull
    private final NoisyAudioBroadcastReceiver.OnNoisyAudioListener mNoisyAudioListener = new NoisyAudioBroadcastReceiver.OnNoisyAudioListener() {

        @Override
        public void onNoisyAudio() {
            logger.d("onNoisyAudio()");
            if (mReactOnExternalEvents) {
                stop();
            } else {
                logger.d("reacting on external events is disabled");
            }
        }
    };

    @NotNull
    private final HeadsetPlugBroadcastReceiver.OnHeadsetStateChangedListener mHeadsetStateChangeListener = new HeadsetPlugBroadcastReceiver.OnHeadsetStateChangedListener() {

        @Override
        public void onHeadphonesPlugged(boolean hasMicrophone) {
            logger.d("onHeadphonesPlugged(), hasMicrophone=" + hasMicrophone);
            handleInterruptEventEnd();
        }

        @Override
        public void onHeadphonesUnplugged() {
            logger.d("onHeadphonesUnplugged()");
            if (mReactOnExternalEvents) {
                handleInterruptEventStart();
            } else {
                logger.d("reacting on external events is disabled");
            }
        }
    };

    @NotNull
    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            logger.d("onCallStateChanged(), state=" + state + ", incomingNumber=" + incomingNumber);
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                if (mReactOnExternalEvents) {
                    handleInterruptEventStart();
                } else {
                    logger.d("reacting on external events is disabled");
                }
            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                handleInterruptEventEnd();
            }
//            else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
//            }
        }
    };

    @CallSuper
    protected void init() {
        startExecutor();
        registerReceivers();
    }

    private void registerReceivers() {
        mAudioFocusChangeReceiver.getAudioFocusChangeObservable().registerObserver(mAudioFocusChangeListener);
        mNoisyBroadcastReceiver.register(mContext);
        mNoisyBroadcastReceiver.getNoisyAudioObservable().registerObserver(mNoisyAudioListener);
        mHeadsetPlugBroadcastReceiver.register(mContext);
        mHeadsetPlugBroadcastReceiver.getHeadsetStateChangedObservable().registerObserver(mHeadsetStateChangeListener);
        if (ContextCompat.checkSelfPermission(mContext, "android.permission.READ_PHONE_STATE") == PackageManager.PERMISSION_GRANTED) {
            final TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Activity.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
        }
    }

    private void unregisterReceivers() {
        mAudioFocusChangeReceiver.getAudioFocusChangeObservable().unregisterObserver(mAudioFocusChangeListener);
        mNoisyBroadcastReceiver.getNoisyAudioObservable().unregisterObserver(mNoisyAudioListener);
        mNoisyBroadcastReceiver.unregister(mContext);
        mHeadsetPlugBroadcastReceiver.getHeadsetStateChangedObservable().unregisterObserver(mHeadsetStateChangeListener);
        mHeadsetPlugBroadcastReceiver.unregister(mContext);
        if (ContextCompat.checkSelfPermission(mContext, "android.permission.READ_PHONE_STATE") == PackageManager.PERMISSION_GRANTED) {
            final TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Activity.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        }
    }

    @NotNull
    public final Context getContext() {
        checkReleased();
        return mContext;
    }

    @NotNull
    public final Looper getMediaLooper() {
        checkReleased();
        return mMediaLooper;
    }

    @NotNull
    public final Handler getMediaHandler() {
        checkReleased();
        return mMediaHandler;
    }

    public static float getAverageVolume() {
        return (VOLUME_MAX + VOLUME_MIN) / 2f;
    }

    public long getPrepareResetTimeoutMs() {
        return mPrepareResetTimeoutMs;
    }

    public void setPrepareResetTimeoutMs(long prepareResetTimeoutMs) {
        checkReleased();
        if (prepareResetTimeoutMs <= 0) {
            throw new IllegalArgumentException("incorrect prepareResetTimeoutMs: " + prepareResetTimeoutMs);
        }
        if (prepareResetTimeoutMs != mPrepareResetTimeoutMs) {
            this.mPrepareResetTimeoutMs = prepareResetTimeoutMs;
            if (isPreparing()) {
                scheduleResetCallback();
            }
        }
    }

    public void setReactOnExternalEvents(boolean reactOnExternalEvents) {
        checkReleased();
        this.mReactOnExternalEvents = reactOnExternalEvents;
    }

    protected final boolean requestAudioFocus() {
        checkReleased();
        return mAudioFocusChangeReceiver.requestFocus(mContext);
    }

    protected final boolean abandonAudioFocus() {
        checkReleased();
        return mAudioFocusChangeReceiver.abandonFocus(mContext);
    }

    @NotNull
    public State getCurrentState() {
        synchronized (mLock) {
            return mCurrentState;
        }
    }

    protected void setCurrentState(@NotNull State newState) {
        synchronized (mLock) {
            checkReleased();
            if (newState != mCurrentState) {
                State oldState = mCurrentState;
                mCurrentState = newState;
                logger.i("current state: " + mCurrentState);
                mStateChangedObservable.dispatchCurrentStateChanged(mCurrentState, oldState);
            }
        }
    }

    @NotNull
    public State getTargetState() {
        synchronized (mLock) {
            return mTargetState;
        }
    }

    protected void setTargetState(@NotNull State newState) {
        synchronized (mLock) {
            checkReleased();
            if (newState != mTargetState) {
                mTargetState = newState;
                logger.i("target state: " + mTargetState);
                mStateChangedObservable.dispatchTargetStateChanged(mTargetState);
            }
        }
    }

    @NotNull
    public Pair<Float, Float> getPreparedVolume() {
        synchronized (mLock) {
            return new Pair<>(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
        }
    }

    @CallSuper
    public void setVolume(float left, float right) {
        synchronized (mLock) {
            checkReleased();
            if (left != VOLUME_NOT_SET) {
                if (left < VOLUME_MIN || left > VOLUME_MAX) {
                    throw new IllegalArgumentException("incorrect left volume: " + left);
                }
            }
            if (right != VOLUME_NOT_SET) {
                if (right < VOLUME_MIN || right > VOLUME_MAX) {
                    throw new IllegalArgumentException("incorrect right volume: " + left);
                }
            }
            mVolumeLeftWhenPrepared = left;
            mVolumeRightWhenPrepared = right;
        }
    }

    public boolean isNoCheckMediaContentType() {
        return mNoCheckMediaContentType;
    }

    public void setNoCheckMediaContentType(boolean toggle) {
        synchronized (mLock) {
            checkReleased();
            mNoCheckMediaContentType = toggle;
        }
    }

    @Override
    public int getBufferPercentage() {
        synchronized (mLock) {
            return mCurrentBufferPercentage;
        }
    }

    public boolean isContentSpecified() {
        return isAudioSpecified() || isVideoSpecified() || isPictureSpecified() || isPageSpecified();
    }

    public boolean isAudioSpecified() {
        synchronized (mLock) {
            return mPlayMode == PlayMode.AUDIO && (mContentUri != null || mContentFileDescriptor != null);
        }
    }

    public boolean isVideoSpecified() {
        synchronized (mLock) {
            return mPlayMode == PlayMode.VIDEO && (mContentUri != null || mContentFileDescriptor != null);
        }
    }

    public boolean isPictureSpecified() {
        synchronized (mLock) {
            return mPlayMode == PlayMode.PICTURE && (mContentUri != null || mContentFileDescriptor != null);
        }
    }

    public boolean isPageSpecified() {
        synchronized (mLock) {
            return mPlayMode == PlayMode.PAGE && (mContentUri != null || mContentFileDescriptor != null);
        }
    }

    @Nullable
    public Uri getContentUri() {
        synchronized (mLock) {
            return mContentUri;
        }
    }

    @Nullable
    public AssetFileDescriptor getContentAssetFileDescriptor() {
        synchronized (mLock) {
            return mContentFileDescriptor;
        }
    }

    public void setContentFile(@NotNull PlayMode playMode, @Nullable File file) {
        if (file != null) {
            if (FileHelper.isFileCorrect(file)) {
                setContentUri(playMode, Uri.fromFile(file));
            }
        } else {
            setContentUri(playMode, null);
        }
    }

    public void setContentPath(@NotNull PlayMode playMode, @Nullable String path) {
        if (!TextUtils.isEmpty(path)) {
            setContentUri(playMode, Uri.parse(path));
        } else {
            setContentUri(PlayMode.NONE, null);
        }
    }

    public void setContentUriByMediaFileId(@NotNull PlayMode playMode, int id, boolean isExternal) {
        setContentUriByMediaFileInfo(playMode, new MediaStoreInfoRetriever.MediaFileInfo(id, isExternal));
    }

    public void setContentUriByMediaFileInfo(@NotNull PlayMode playMode, @Nullable MediaStoreInfoRetriever.MediaFileInfo info) {
        setContentUri(playMode, info != null ? info.getContentUri() : null);
    }

    public void setContentUri(@NotNull PlayMode playMode, @Nullable Uri contentUri) {
        setContentUri(playMode, contentUri, null);
    }

    public void setContentUri(@NotNull PlayMode playMode, @Nullable Uri contentUri, @Nullable Map<String, String> headers) {
        synchronized (mLock) {
            logger.d("setContentUri(), playMode=" + playMode + ", contentUri=" + contentUri + ", headers=" + headers);

            checkReleased();

            if (playMode != PlayMode.NONE) {
                if (!isPlayModeSupported(playMode)) {
                    throw new IllegalArgumentException("playMode " + playMode + " is not supported");
                }
            }

            if (mPlayMode != playMode || !CompareUtils.objectsEqual(contentUri, mContentUri) || !CompareUtils.objectsEqual(headers, mContentHeaders)) {

                if (mContentFileDescriptor != null) {
                    try {
                        mContentFileDescriptor.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mContentFileDescriptor = null;
                }
                mContentUri = contentUri;
                mContentHeaders = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<String, String>();

                if (mContentUri != null || mContentFileDescriptor != null) {
                    mPlayMode = playMode;
                } else {
                    mPlayMode = PlayMode.NONE;
                }

                if (mCurrentState != State.IDLE) {
                    if (isContentSpecified()) {
                        openDataSource();
                    } else {
                        stop();
                    }
                }
            }
        }
    }

    public void setContentFd(@NotNull PlayMode playMode, @Nullable AssetFileDescriptor contentFd) {
        synchronized (mLock) {
            logger.d("setContentFd(), playMode=" + playMode + ", contentFd=" + contentFd);

            checkReleased();

            if (playMode != PlayMode.NONE) {
                if (!isPlayModeSupported(playMode)) {
                    throw new IllegalArgumentException("playMode " + playMode + " is not supported");
                }
            }

            if (mPlayMode != playMode || !CompareUtils.objectsEqual(contentFd, mContentFileDescriptor)) {

                if (mContentUri != null || mContentFileDescriptor != null) {
                    mPlayMode = playMode;
                } else {
                    mPlayMode = PlayMode.NONE;
                }

                if (mContentFileDescriptor != null) {
                    try {
                        mContentFileDescriptor.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                mContentFileDescriptor = contentFd;
                mContentUri = null;
                mContentHeaders = new LinkedHashMap<>();

                if (mCurrentState != State.IDLE) {
                    if (isContentSpecified()) {
                        openDataSource();
                    } else {
                        stop();
                    }
                }
            }
        }
    }

    public void setContentRawId(@NotNull Context context, @NotNull PlayMode playMode, @RawRes int contentRawResId) {
            setContentFd(playMode, context.getResources().openRawResourceFd(contentRawResId));
    }

    public void setContentAsset(@NotNull Context context, @NotNull PlayMode playMode, String assetName) {
        AssetFileDescriptor fd = null;
        try {
            fd = context.getAssets().openNonAssetFd(assetName);
        } catch (IOException e) {
            e.printStackTrace();
            logger.e("can't open asset: " + assetName);
        }
        if (fd != null) {
            setContentFd(playMode, fd);
        }
    }

    public void clearContent() {
        setContentUri(PlayMode.NONE, null);
    }


    @SuppressWarnings("ConstantConditions")
    @Nullable
    public MetadataRetriever.MediaMetadata getCurrentTrackMetatada() {
        return (mContentUri != null && (TextUtils.isEmpty(mContentUri.getScheme()) || mContentUri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) ?
                MetadataRetriever.extractMetadata(mContext, mContentUri) :
                (mContentFileDescriptor != null ? MetadataRetriever.extractMetadata(mContentFileDescriptor.getFileDescriptor()) : null));
    }

    protected final void scheduleResetCallback() {
        cancelResetCallback();
        mResetFuture = scheduleOnExecutor(getResetRunnable(), mPrepareResetTimeoutMs);
    }

    protected final void cancelResetCallback() {
        if (mResetFuture != null) {
            if (!mResetFuture.isCancelled() && !mResetFuture.isDone()) {
//                logger.d("cancelling reset callback (prepare timeout)...");
                mResetFuture.cancel(true);
            }
            mResetFuture = null;
        }
    }

    @CallSuper
    public void setLooping(boolean toggle) {
        synchronized (mLock) {
            logger.d("setLooping(), toggle=" + toggle);
            mLoopWhenPreparing = toggle;
        }
    }

    @NotNull
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(mContentHeaders);
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

    @NotNull
    public final Observable<OnStateChangedListener> getStateChangedObservable() {
        return mStateChangedObservable;
    }

    @NotNull
    public final Observable<OnCompletionListener> getCompletionObservable() {
        return mCompletionObservable;
    }

    @NotNull
    public final Observable<OnBufferingUpdateListener> getBufferingUpdateObservable() {
        return mBufferingUpdateObservable;
    }

    @NotNull
    public Observable<OnPlaybackTimeUpdateTimeListener> getPlaybackTimeUpdateTimeObservable() {
        return mPlaybackTimeUpdateTimeObservable;
    }

    @NotNull
    public Observable<OnErrorListener<E>> getErrorObservable() {
        return mErrorObservable;
    }

    protected void checkReleased() {
        if (isReleased()) {
            throw new IllegalStateException(getClass().getSimpleName() + " was released");
        }
    }

    public final boolean isReleased() {
        synchronized (mLock) {
            return mCurrentState == State.RELEASED;
        }
    }

    public abstract boolean isPlayerReleased();

    public final boolean isReleasingPlayer() {
        synchronized (mLock) {
            return mReleasingPlayer;
        }
    }

    public abstract boolean isInPlaybackState();

    public abstract boolean isPreparing();

    public abstract boolean isPlaying();

    public abstract boolean isLooping();

    public abstract boolean isPlayModeSupported(@NotNull PlayMode playMode);

    public abstract int getCurrentPosition();

    public abstract int getDuration();

    @NotNull
    protected abstract Runnable getResetRunnable();

    protected abstract void openDataSource();

    @CallSuper
    protected void beforeOpenDataSource() {
        synchronized (mLock) {
            logger.d("beforeOpenDataSource()");
            checkReleased();
            onBufferingUpdate(0);
            setControlsToDefault();
            if (isContentSpecified()) {
                mLastContentUriToOpen = mContentUri != null ? mContentUri : null;
                mLastAssetFileDescriptorToOpen = mContentFileDescriptor != null ? mContentFileDescriptor : null;
                mLastModeToOpen = mPlayMode;
                mStateChangedObservable.dispatchBeforeOpenDataSource();
            }
        }
    }

    public abstract void start();

    public abstract void stop();

    public abstract void pause();

    public final void resume() {
        logger.d("resume()");
        if (isContentSpecified()) {
            if (!(isPreparing() || isInPlaybackState())) {
                openDataSource();
            }
        }
    }

    public final void suspend() {
        logger.d("suspend()");
        releasePlayer(false);
    }

    public abstract void seekTo(int msec);

    protected void setControlsToDefault() {
        checkReleased();
        mCanPause = true;
        mCanSeekBack = false;
        mCanSeekForward = false;
    }

    /*
    * release the media player at any state
    */
    @CallSuper
    protected void releasePlayer(boolean clearTargetState) {
        synchronized (mLock) {
            checkReleased();
            onBufferingUpdate(0);
            setControlsToDefault();
        }
    }

    @CallSuper
    public void release() {
        synchronized (mLock) {
            logger.d("release()");
            if (isReleased()) {
                throw new IllegalStateException(BaseMediaPlayerController.class.getSimpleName() + " was already released");
            }
            releasePlayer(true);
            unregisterReceivers();
            stopExecutor();
        }
    }

    /**
     * @return false if resource not opened or reopened, true otherwise
     */
    @CallSuper
    protected boolean onPrepared() {
        synchronized (mLock) {
            logger.i("onPrepared(), " +
                    "target content: " + (mContentUri != null ? mContentUri : mContentFileDescriptor) + ", opened content: " + (mLastContentUriToOpen != null ? mLastContentUriToOpen : mLastAssetFileDescriptorToOpen) +
                    " | target play mode: " + mPlayMode + ", opened play mode: " + mLastModeToOpen);

            checkReleased();
            cancelResetCallback();

            boolean result = true;

            if (!isContentSpecified()) {
                logger.e("can't open data source: content is not specified");
                releasePlayer(true);
                result = false;
            }

            if (result) {
                boolean reopen = false;

                if (mPlayMode == mLastModeToOpen) {
                    if (mLastContentUriToOpen != null) {
                        if (!CompareUtils.objectsEqual(mLastContentUriToOpen, mContentUri)) {
                            reopen = true;
                        }
                    } else if (mLastAssetFileDescriptorToOpen != null) {
                        if (!CompareUtils.objectsEqual(mLastAssetFileDescriptorToOpen, mContentFileDescriptor)) {
                            reopen = true;
                        }
                    } else {
                        result = false;
                    }
                } else {
                    reopen = true;
                }

                if (reopen) {
                    if (mContentUri != null) {
                        logger.w("last uri (" + mLastContentUriToOpen + ") or descriptor (" + mLastAssetFileDescriptorToOpen + ") to open and target (" + mContentUri + ") don't match");
                    } else if (mContentFileDescriptor != null) {
                        logger.w("last uri (" + mLastContentUriToOpen + ") or descriptor (" + mLastAssetFileDescriptorToOpen + ") to open and target (" + mContentFileDescriptor + ") don't match");
                    }
                    logger.d("releasing and reopening...");
                    releasePlayer(false);
                    openDataSource();
                    result = false;
                }
            }

            return result;
        }
    }

    @CallSuper
    protected void onBufferingUpdate(int percent) {
        synchronized (mLock) {
            logger.d("onBufferingUpdate(), percent=" + percent);
            checkReleased();
            if (percent != mCurrentBufferPercentage) {
                mCurrentBufferPercentage = percent;
                mBufferingUpdateObservable.dispatchOnOnBufferingUpdate(mCurrentBufferPercentage);
            }
        }
    }

    @CallSuper
    protected void onCompletion() {
        synchronized (mLock) {
            logger.i("onCompletion()");
            checkReleased();
            mCompletionObservable.dispatchCompleted();
        }
    }

    /**
     * @return true if error was handled
     */
    @CallSuper
    protected boolean onError(@NotNull E error) {
        synchronized (mLock) {
            logger.e("onError(), error=" + error);
            checkReleased();
            mErrorObservable.dispatchError(error);
            return true;
        }
    }

    public void setNotifyPlaybackTimeInterval(long intervalMs) {
        synchronized (mLock) {
            checkReleased();
            if (intervalMs <= 0) {
                throw new IllegalArgumentException("incorrect intervalMs: " + intervalMs);
            }
            if (intervalMs != mNotifyPlaybackTimeInterval) {
                mNotifyPlaybackTimeInterval = intervalMs;
                if (isPlaying()) {
                    restartPlaybackTimeTask();
                }
            }
        }
    }

    protected final boolean isExecutorRunning() {
        checkReleased();
        return mExecutorService != null && !mExecutorService.isShutdown() && !mExecutorService.isTerminated();
    }

    private void startExecutor() {
        if (!isExecutorRunning()) {
            restartExecutor();
        }
    }

    private void restartExecutor() {
        checkReleased();
        stopExecutor();
        mExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    private void stopExecutor() {
        checkReleased();
        if (isExecutorRunning()) {
            mExecutorService.shutdown();
            mExecutorService = null;
        }
    }

    protected final boolean isPlaybackTimeTaskRunning() {
        return mPlaybackTimeTask.isRunning();
    }

    protected final void restartPlaybackTimeTask() {

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
//                logger.d("doUpdate()");
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
                        onCompletion();
                    }

                    lastPositionMs = currentPosition;

                    postOnMediaHandler(mPlaybackTimeUpdateTimeObservable::dispatchPlaybackTimeUpdated);
                }
            }
        });
        mPlaybackTimeTask.restart(mNotifyPlaybackTimeInterval);
    }

    protected final void startPlaybackTimeTask() {
        if (!isPlaybackTimeTaskRunning()) {
            restartPlaybackTimeTask();
        }
    }

    protected final void stopPlaybackTimeTask() {
        if (isPlaybackTimeTaskRunning()) {
            mPlaybackTimeTask.stop(false, 0);
            mPlaybackTimeTask.removeAllRunnableTasks();
        }
    }

    protected void handleInterruptEventStart() {
        synchronized (mLock) {
            logger.d("handleInterruptEventStart()");
            if (isPreparing() || isPlaying()) {
                State previousState = getCurrentState();
                State previousTargetState = getTargetState();
                pause();
                if (previousState == State.PLAYING || previousTargetState == State.PLAYING) {
                    setTargetState(State.PLAYING);
                }
                mInterrupted = true;
            }
        }
    }

    protected void handleInterruptEventEnd() {
        synchronized (mLock) {
            logger.d("handleInterruptEventEnd()");
            if (mInterrupted && mTargetState == State.PLAYING) {
                start();
            }
            mInterrupted = false;
        }
    }

    public final void postOnMediaHandler(@NotNull Runnable r) {
        postOnMediaHandlerDelayed(r, 0);
    }

    public final void postOnMediaHandlerDelayed(@NotNull Runnable r, long delay) {
       mMediaHandler.postDelayed(r, delay);
    }

    public final void removeFromMediaHandler(@NotNull Runnable r) {
        mMediaHandler.removeCallbacks(r);
    }

    public final void runOnMediaThread(@NotNull Runnable r) {
        if (Looper.myLooper() == getMediaLooper()) {
            r.run();
        } else {
            postOnMediaHandler(r);
        }
    }

    public final void runOnMediaThreadSync(@NotNull final Runnable r) {
        if (Looper.myLooper() == getMediaLooper()) {
            r.run();
        } else {
            final CountDownLatch latch = new CountDownLatch(1);
            postOnMediaHandler(() -> {
                logger.d("run()");
                r.run();
                latch.countDown();
            });
            logger.d("await start");
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            } finally {
                logger.d("await end");
            }
        }
    }

    @NotNull
    public final Future<?> submitOnExecutor(@NotNull Runnable r) {
        if (!isExecutorRunning()) {
            throw new IllegalStateException("not running");
        }
        return mExecutorService.submit(r);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public final <T> Future<T> submitOnExecutor(@NotNull Callable<T> c) {
        if (!isExecutorRunning()) {
            throw new IllegalStateException("not running");
        }
        return mExecutorService.submit(c);
    }

    @NotNull
    public final ScheduledFuture<?> scheduleOnExecutor(@NotNull Runnable r, long delay) {
        if (!isExecutorRunning()) {
            throw new IllegalStateException("not running");
        }
        if (delay <= 0) {
            throw new IllegalArgumentException("delay <= 0");
        }
        return mExecutorService.schedule(r, delay, TimeUnit.MILLISECONDS);
    }

    @NotNull
    protected final <T> ScheduledFuture<T> scheduleOnExecutor(@NotNull Callable<T> c, long delay) {
        if (!isExecutorRunning()) {
            throw new IllegalStateException("not running");
        }
        return mExecutorService.schedule(c, delay, TimeUnit.MILLISECONDS);
    }

    public interface OnPlaybackTimeUpdateTimeListener {

        void onPlaybackTimeUpdateTime(int position, int duration);
    }

    public interface OnBufferingUpdateListener {

        void onBufferingUpdate(int percentage);
    }

    public interface OnCompletionListener {

        /**
         * @param isLooping if track looping mode set
         */
        void onCompletion(boolean isLooping);
    }

    public interface OnErrorListener<E extends OnErrorListener.MediaError> {

        abstract class MediaError {

            public static final int UNKNOWN = MediaPlayer.MEDIA_ERROR_UNKNOWN;
            public static final int PREPARE_TIMEOUT_EXCEEDED = 100 << 0;
            public static final int PREPARE_EMPTY_CONTENT_TYPE = 100 << 1;
            public static final int PREPARE_UNKNOWN = 100 << 2;
            public static final int PLAY_UNKNOWN = 100 << 3;
            public static final int PAUSE_UNKNOWN = 100 << 4;
            public static final int STOP_UNKNOWN = 100 << 5;
            public static final int RELEASE_UNKNOWN = 100 << 6;

            @IntDef({UNKNOWN, PREPARE_TIMEOUT_EXCEEDED, PREPARE_EMPTY_CONTENT_TYPE, PREPARE_UNKNOWN, PLAY_UNKNOWN, PAUSE_UNKNOWN, STOP_UNKNOWN, RELEASE_UNKNOWN})
            @Retention(RetentionPolicy.SOURCE)
            public @interface ErrorDef {
            }
        }

        void onError(@NotNull E error);
    }


    public interface OnStateChangedListener {

        void onBeforeOpenDataSource();

        void onCurrentStateChanged(@NotNull State currentState, @NotNull State previousState);

        void onTargetStateChanged(@NotNull State targetState);
    }

    /**
     * mpc instance state
     */
    public enum State {
        IDLE, PREPARING, PREPARED, PLAYING, PAUSED, RELEASED
    }

    public enum PlayMode {

        NONE("", false),
        AUDIO("audio", false),
        VIDEO("video", false),
        PICTURE("image", true),
        PAGE("page,html", true);

        @NotNull
        public final String mimeTypeParts;

        public final boolean isInfiniteMode;

        PlayMode(@NotNull String mimeTypeParts, boolean isInfiniteMode) {
            this.mimeTypeParts = mimeTypeParts;
            this.isInfiniteMode = isInfiniteMode;
        }

        @NotNull
        public List<String> getMimeTypeParts() {
            return Arrays.asList(mimeTypeParts.split(","));
        }

        @NotNull
        public static PlayMode fromContentType(@Nullable String type) {
            if (type != null) {
                for (PlayMode m : values()) {
                    if (m != NONE) {
                        for (String part : m.getMimeTypeParts()) {
                            if (!TextUtils.isEmpty(part)) {
                                if (type.toLowerCase().contains(part.toLowerCase().trim())) {
                                    return m;
                                }
                            }
                        }
                    }
                }
            }
            return NONE;
        }
    }

    protected class PlaybackTimeUpdateTimeObservable extends Observable<OnPlaybackTimeUpdateTimeListener> {

        void dispatchPlaybackTimeUpdated() {
            synchronized (observers) {
                for (OnPlaybackTimeUpdateTimeListener l : copyOfObservers()) {
                    l.onPlaybackTimeUpdateTime(getCurrentPosition(), getDuration());
                }
            }
        }
    }

    protected static class OnBufferingUpdateObservable extends Observable<OnBufferingUpdateListener> {

        void dispatchOnOnBufferingUpdate(int percentage) {
            synchronized (observers) {
                for (OnBufferingUpdateListener l : copyOfObservers()) {
                    l.onBufferingUpdate(percentage);
                }
            }
        }
    }

    protected class OnCompletionObservable extends Observable<OnCompletionListener> {

        void dispatchCompleted() {
            synchronized (observers) {
                for (OnCompletionListener l : copyOfObservers()) {
                    l.onCompletion(isLooping());
                }
            }
        }
    }

    protected static class OnErrorObservable<E extends OnErrorListener.MediaError> extends Observable<OnErrorListener<E>> {

        void dispatchError(@NotNull E error) {
            synchronized (observers) {
                for (OnErrorListener<E> l : copyOfObservers()) {
                    l.onError(error);
                }
            }
        }
    }

    protected static class OnStateChangedObservable extends Observable<OnStateChangedListener> {

        void dispatchBeforeOpenDataSource() {
            synchronized (observers) {
                for (OnStateChangedListener l : copyOfObservers()) {
                    l.onBeforeOpenDataSource();
                }
            }
        }

        void dispatchCurrentStateChanged(@NotNull State currentState, @NotNull State previousState) {
            synchronized (observers) {
                for (OnStateChangedListener l : copyOfObservers()) {
                    l.onCurrentStateChanged(currentState, previousState);
                }
            }
        }

        void dispatchTargetStateChanged(@NotNull State targetState) {
            synchronized (observers) {
                for (OnStateChangedListener l : copyOfObservers()) {
                    l.onTargetStateChanged(targetState);
                }
            }
        }
    }
}
