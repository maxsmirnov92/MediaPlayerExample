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
import android.support.annotation.CallSuper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.MediaController;

import net.maxsmr.commonutils.android.media.MediaStoreInfoRetriever;
import net.maxsmr.commonutils.android.media.MetadataRetriever;
import net.maxsmr.commonutils.data.CompareUtils;
import net.maxsmr.commonutils.data.FileHelper;
import net.maxsmr.commonutils.data.Observable;
import net.maxsmr.mediaplayercontroller.mpc.nativeplayer.MediaPlayerController;
import net.maxsmr.mediaplayercontroller.mpc.receivers.AudioFocusChangeReceiver;
import net.maxsmr.mediaplayercontroller.mpc.receivers.HeadsetPlugBroadcastReceiver;
import net.maxsmr.mediaplayercontroller.mpc.receivers.NoisyAudioBroadcastReceiver;
import net.maxsmr.tasksutils.ScheduledThreadPoolExecutorManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public final static long DEFAULT_PREPARE_RESET_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);

    public final static long DEFAULT_NOTIFY_PLAYBACK_TIME_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);

    public static final int AUDIO_SESSION_EMPTY = -1;

    public static final int POSITION_NO = -1;
    public static final int POSITION_START = 0;

    protected BaseMediaPlayerController(@NonNull Context context) {
        mContext = context;
        init();
    }

    protected Context mContext;

    protected boolean mReleasingPlayer = false;

    protected int mSeekWhenPrepared = POSITION_NO;  // set the seek position after prepared

    private long mNotifyPlaybackTimeInterval = DEFAULT_NOTIFY_PLAYBACK_TIME_INTERVAL_MS;

    private final ScheduledThreadPoolExecutorManager mPlaybackTimeTask = new ScheduledThreadPoolExecutorManager("PlaybackTimeTask");

    protected boolean mLoopWhenPreparing = false; // set looping property while preparing

    private ScheduledExecutorService mExecutorService;

    protected Looper mMediaLooper;

    @NonNull
    protected State mCurrentState = State.IDLE;

    @NonNull
    protected State mTargetState = State.IDLE;

    protected int mCurrentBufferPercentage = 0;

    @NonNull
    protected PlayMode mPlayMode = PlayMode.NONE;

    protected boolean mNoCheckMediaContentType;

    @Nullable
    protected Uri mContentUri;

    @Nullable
    protected AssetFileDescriptor mContentFileDescriptor;

    @NonNull
    protected PlayMode mLastModeToOpen = PlayMode.NONE;

    @Nullable
    protected Uri mLastContentUriToOpen = null;

    @Nullable
    protected AssetFileDescriptor mLastAssetFileDescriptorToOpen = null;

    @NonNull
    protected Map<String, String> mContentHeaders = new LinkedHashMap<>();

    protected boolean mCanPause = true;

    protected boolean mCanSeekBack = false;

    protected boolean mCanSeekForward = false;

    protected float mVolumeLeftWhenPrepared = VOLUME_NOT_SET;
    protected float mVolumeRightWhenPrepared = VOLUME_NOT_SET;

    public static final float VOLUME_MAX = 1.0f;
    public static final float VOLUME_MIN = 0.0f;
    public static final int VOLUME_NOT_SET = -1;

    protected long mPrepareResetTimeoutMs = DEFAULT_PREPARE_RESET_TIMEOUT_MS;

    private Future<?> mResetFuture;

    private boolean mReactOnExternalEvents = true;

    private boolean mInterrupted = false;

    @NonNull
    private final AudioFocusChangeReceiver mAudioFocusChangeReceiver = new AudioFocusChangeReceiver();

    @NonNull
    private final HeadsetPlugBroadcastReceiver mHeadsetPlugBroadcastReceiver = new HeadsetPlugBroadcastReceiver();

    @NonNull
    private final NoisyAudioBroadcastReceiver mNoisyBroadcastReceiver = new NoisyAudioBroadcastReceiver();

    @NonNull
    protected final OnStateChangedObservable mStateChangedObservable = new OnStateChangedObservable();

    @NonNull
    protected final OnCompletionObservable mCompletionObservable = new OnCompletionObservable();

    @NonNull
    protected final OnBufferingUpdateObservable mBufferingUpdateObservable = new OnBufferingUpdateObservable();

    @NonNull
    protected final PlaybackTimeUpdateTimeObservable mPlaybackTimeUpdateTimeObservable = new PlaybackTimeUpdateTimeObservable();

    @NonNull
    protected final OnErrorObservable<E> mErrorObservable = new OnErrorObservable<>();

    @NonNull
    private final AudioFocusChangeReceiver.OnAudioFocusChangeListener mAudioFocusChangeListener = new AudioFocusChangeReceiver.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusGain() {
            logger.debug("onAudioFocusGain()");
            // resume playback
            if (mVolumeLeftWhenPrepared != VOLUME_NOT_SET && mVolumeRightWhenPrepared != VOLUME_NOT_SET) {
                setVolume(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
            }
            handleInterruptEventEnd();
        }

        @Override
        public void onAudioFocusLoss() {
            logger.debug("onAudioFocusLoss()");
            if (mReactOnExternalEvents) {
                // Lost focus for an unbounded amount of time: stop playback and release media player
                suspend();
            } else {
                logger.debug("reacting on external events is disabled");
            }
        }

        @Override
        public void onAudioFocusLossTransient() {
            logger.debug("onAudioFocusLossTransient()");
            if (mReactOnExternalEvents) {
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                handleInterruptEventStart();
            } else {
                logger.debug("reacting on external events is disabled");
            }
        }

        @Override
        public void onAudioFocusLossTransientCanDuck() {
            logger.debug("onAudioFocusLossTransientCanDuck()");
            if (mReactOnExternalEvents) {
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                float previousVolumeLeft = mVolumeLeftWhenPrepared != VOLUME_NOT_SET ? mVolumeLeftWhenPrepared : getAverageVolume();
                float previousVolumeRight = mVolumeRightWhenPrepared != VOLUME_NOT_SET ? mVolumeRightWhenPrepared : getAverageVolume();
                setVolume(0.1f, 0.1f);
                mVolumeLeftWhenPrepared = previousVolumeLeft;
                mVolumeRightWhenPrepared = previousVolumeRight;
            } else {
                logger.debug("reacting on external events is disabled");
            }
        }
    };

    @NonNull
    private final NoisyAudioBroadcastReceiver.OnNoisyAudioListener mNoisyAudioListener = new NoisyAudioBroadcastReceiver.OnNoisyAudioListener() {

        @Override
        public void onNoisyAudio() {
            logger.debug("onNoisyAudio()");
            if (mReactOnExternalEvents) {
                stop();
            } else {
                logger.debug("reacting on external events is disabled");
            }
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
            if (mReactOnExternalEvents) {
                handleInterruptEventStart();
            } else {
                logger.debug("reacting on external events is disabled");
            }
        }
    };

    @NonNull
    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            logger.debug("onCallStateChanged(), state=" + state + ", incomingNumber=" + incomingNumber);
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                if (mReactOnExternalEvents) {
                    handleInterruptEventStart();
                } else {
                    logger.debug("reacting on external events is disabled");
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

    @NonNull
    public final Looper getMediaLooper() {
        return mMediaLooper == null ? Looper.getMainLooper() : mMediaLooper;
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
        this.mPrepareResetTimeoutMs = prepareResetTimeoutMs;
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

    @NonNull
    public synchronized State getCurrentState() {
        return mCurrentState;
    }

    protected synchronized void setCurrentState(@NonNull State newState) {
        checkReleased();
        if (newState != mCurrentState) {
            State oldState = mCurrentState;
            mCurrentState = newState;
            logger.info("current state: " + mCurrentState);
            mStateChangedObservable.dispatchCurrentStateChanged(oldState);
        }
    }

    @NonNull
    public synchronized State getTargetState() {
        return mTargetState;
    }

    protected synchronized void setTargetState(@NonNull State newState) {
        checkReleased();
        if (newState != mTargetState) {
            mTargetState = newState;
            logger.info("target state: " + mTargetState);
            mStateChangedObservable.dispatchTargetStateChanged();
        }
    }

    @NonNull
    public synchronized Pair<Float, Float> getPreparedVolume() {
        return new Pair<>(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
    }

    @CallSuper
    public synchronized void setVolume(float left, float right) {
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

    public boolean isNoCheckMediaContentType() {
        return mNoCheckMediaContentType;
    }

    public void setNoCheckMediaContentType(boolean toggle) {
        mNoCheckMediaContentType = toggle;
    }

    @Override
    public synchronized int getBufferPercentage() {
        return mCurrentBufferPercentage;
    }

    public synchronized boolean isContentSpecified() {
        return isAudioSpecified() || isVideoSpecified() || isPictureSpecified() || isPageSpecified();
    }

    public synchronized boolean isAudioSpecified() {
        return mPlayMode == PlayMode.AUDIO && (mContentUri != null || mContentFileDescriptor != null);
    }

    public synchronized boolean isVideoSpecified() {
        return mPlayMode == PlayMode.VIDEO && (mContentUri != null || mContentFileDescriptor != null);
    }

    public synchronized boolean isPictureSpecified() {
        return mPlayMode == PlayMode.PICTURE && (mContentUri != null || mContentFileDescriptor != null);
    }

    public synchronized boolean isPageSpecified() {
        return mPlayMode == PlayMode.PAGE && (mContentUri != null || mContentFileDescriptor != null);
    }

    @Nullable
    public synchronized Uri getContentUri() {
        return mContentUri;
    }

    @Nullable
    public synchronized AssetFileDescriptor getContentAssetFileDescriptor() {
        return mContentFileDescriptor;
    }

    public void setContentFile(@NonNull PlayMode playMode, @Nullable File file) {
        if (file != null) {
            if (FileHelper.isFileCorrect(file)) {
                setContentUri(playMode, Uri.fromFile(file));
            }
        } else {
            setContentUri(playMode, null);
        }
    }

    public void setContentPath(@NonNull PlayMode playMode, @Nullable String path) {
        if (!TextUtils.isEmpty(path)) {
            setContentUri(playMode, Uri.parse(path));
        } else {
            setContentUri(PlayMode.NONE, null);
        }
    }

    public void setContentUriByMediaFileId(@NonNull PlayMode playMode, int id, boolean isExternal) {
        setContentUriByMediaFileInfo(playMode, new MediaStoreInfoRetriever.MediaFileInfo(id, isExternal));
    }

    public void setContentUriByMediaFileInfo(@NonNull PlayMode playMode, @Nullable MediaStoreInfoRetriever.MediaFileInfo info) {
        setContentUri(playMode, info != null ? info.getContentUri() : null);
    }

    public void setContentUri(@NonNull PlayMode playMode, @Nullable Uri contentUri) {
        setContentUri(playMode, contentUri, null);
    }

    public synchronized void setContentUri(@NonNull PlayMode playMode, @Nullable Uri contentUri, @Nullable Map<String, String> headers) {
        logger.debug("setContentUri(), playMode=" + playMode + ", contentUri=" + contentUri + ", headers=" + headers);

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

    public synchronized void setContentFd(@NonNull PlayMode playMode, @Nullable AssetFileDescriptor contentFd) {
        logger.debug("setContentFd(), playMode=" + playMode + ", contentFd=" + contentFd);

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

    public void setContentRawId(@NonNull Context context, @NonNull PlayMode playMode, @RawRes int contentRawResId) {
        if (contentRawResId > 0) {
            setContentFd(playMode, context.getResources().openRawResourceFd(contentRawResId));
        }
    }

    public void setContentAsset(@NonNull Context context, @NonNull PlayMode playMode, String assetName) {
        AssetFileDescriptor fd = null;
        try {
            fd = context.getAssets().openNonAssetFd(assetName);
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("can't open asset: " + assetName);
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
                (mContentFileDescriptor != null? MetadataRetriever.extractMetadata(mContentFileDescriptor.getFileDescriptor()) : null));
    }

    protected final void scheduleResetCallback(@NonNull Runnable runnable) {
        cancelResetCallback();
        mResetFuture = scheduleOnExecutor(runnable, mPrepareResetTimeoutMs);
    }

    protected final void cancelResetCallback() {
        if (mResetFuture != null) {
            if (!mResetFuture.isCancelled() && !mResetFuture.isDone()) {
//                logger.debug("cancelling reset callback (prepare timeout)...");
                mResetFuture.cancel(true);
            }
            mResetFuture = null;
        }
    }

    @CallSuper
    public synchronized void setLooping(boolean toggle) {
        logger.debug("setLooping(), toggle=" + toggle);
        mLoopWhenPreparing = toggle;
    }

    @NonNull
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

    @NonNull
    public final Observable<OnStateChangedListener> getStateChangedObservable() {
        return mStateChangedObservable;
    }

    @NonNull
    public final Observable<OnCompletionListener> getCompletionObservable() {
        return mCompletionObservable;
    }

    @NonNull
    public final Observable<OnBufferingUpdateListener> getBufferingUpdateObservable() {
        return mBufferingUpdateObservable;
    }

    @NonNull
    public Observable<OnPlaybackTimeUpdateTimeListener> getPlaybackTimeUpdateTimeObservable() {
        return mPlaybackTimeUpdateTimeObservable;
    }

    @NonNull
    public Observable<OnErrorListener<E>> getErrorObservable() {
        return mErrorObservable;
    }

    protected void checkReleased() {
        if (isReleased()) {
            throw new IllegalStateException(getClass().getSimpleName() + " was released");
        }
    }

    public boolean isReleased() {
        return mCurrentState == State.RELEASED;
    }

    public abstract boolean isPlayModeSupported(@NonNull PlayMode playMode);

    public abstract boolean isPlayerReleased();

    public synchronized final boolean isReleasingPlayer() {
        return mReleasingPlayer;
    }

    public abstract boolean isInPlaybackState();

    public abstract boolean isPreparing();

    public abstract boolean isPlaying();

    public abstract boolean isLooping();

    public abstract int getCurrentPosition();

    public abstract int getDuration();

    protected abstract void openDataSource();

    @CallSuper
    protected synchronized void beforeOpenDataSource() {
        logger.debug("beforeOpenDataSource()");
        checkReleased();
        onBufferingUpdate(0);
        setControlsToDefault();
        if (isContentSpecified()) {
            mLastContentUriToOpen = mContentUri != null? mContentUri : null;
            mLastAssetFileDescriptorToOpen = mContentFileDescriptor != null? mContentFileDescriptor : null;
            mLastModeToOpen = mPlayMode;
            mStateChangedObservable.dispatchBeforeOpenDataSource();
        }
    }

    public abstract void start();

    public abstract void stop();

    public abstract void pause();

    public final void resume() {
        logger.debug("resume()");
        if (isContentSpecified()) {
            if (!(isPreparing() || isInPlaybackState())) {
                openDataSource();
            }
        }
    }

    public final void suspend() {
        logger.debug("suspend()");
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
        checkReleased();
        onBufferingUpdate(0);
        setControlsToDefault();
    }

    @CallSuper
    public void release() {
        logger.debug("release()");
        if (isReleased()) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was already released");
        }
        releasePlayer(true);
        unregisterReceivers();
        stopExecutor();
    }

    /** @return false if resource not opened or reopened, true otherwise */
    @CallSuper
    protected synchronized boolean onPrepared() {
        logger.info("onPrepared(), content: " + (mContentUri != null? getContentUri() : getContentAssetFileDescriptor()));

        checkReleased();
        cancelResetCallback();

        if (!isContentSpecified()) {
            logger.error("can't open data source: content is not specified");
            releasePlayer(true);
            return false;
        }

        boolean reopen = false;

        if (mLastModeToOpen != PlayMode.NONE) {
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
                    return false;
                }
            } else {
                reopen = true;
            }
        } else {
            return false;
        }

        if (reopen) {
            if (mContentUri != null) {
                logger.warn("last uri (" + mLastContentUriToOpen + ") or descriptor (" + mLastAssetFileDescriptorToOpen + ") to open and target (" + mContentUri + ") don't match");
            } else if (mContentFileDescriptor != null) {
                logger.warn("last uri (" + mLastContentUriToOpen + ") or descriptor (" + mLastAssetFileDescriptorToOpen + ") to open and target (" + mContentFileDescriptor + ") don't match");
            }
            logger.debug("releasing and reopening...");
            releasePlayer(false);
            openDataSource();
            return false;
        }

        return true;

    }

    @CallSuper
    protected synchronized void onBufferingUpdate(int percent) {
        logger.debug("onBufferingUpdate(), percent=" + percent);
        checkReleased();
        if (percent != mCurrentBufferPercentage) {
            mCurrentBufferPercentage = percent;
            mBufferingUpdateObservable.dispatchOnOnBufferingUpdate(mCurrentBufferPercentage);
        }
    }

    @CallSuper
    protected synchronized void onCompletion() {
        logger.info("onCompletion()");
        checkReleased();
        mCompletionObservable.dispatchCompleted();
    }

    /** @return true if error was handled */
    @CallSuper
    protected synchronized boolean onError(@NonNull E error) {
        logger.error("onError(), error=" + error);
        checkReleased();
        mErrorObservable.dispatchError(error);
        return true;
    }

    public void setNotifyPlaybackTimeInterval(long intervalMs) {
        checkReleased();
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
                        onCompletion();
                    }

                    lastPositionMs = currentPosition;

                    postOnMediaHandler(new Runnable() {
                        @Override
                        public void run() {
                            mPlaybackTimeUpdateTimeObservable.dispatchPlaybackTimeUpdated();
                        }
                    });
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

    protected synchronized void handleInterruptEventStart() {
        logger.debug("handleInterruptEventStart()");
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

    protected synchronized void handleInterruptEventEnd() {
        logger.debug("handleInterruptEventEnd()");
        if (mInterrupted && mTargetState == State.PLAYING) {
            start();
        }
        mInterrupted = false;
    }

    public final void postOnMediaHandler(@NonNull Runnable r) {
        postOnMediaHandlerDelayed(r, 0);
    }

    public final void postOnMediaHandlerDelayed(@NonNull Runnable r, long delay) {
        new Handler(getMediaLooper()).postDelayed(r, delay);
    }

    public final void removeFromMediaHandler(@NonNull Runnable r) {
        new Handler(getMediaLooper()).removeCallbacks(r);
    }

    public final void runOnMediaThread(@NonNull Runnable r) {
        if (Looper.myLooper() == getMediaLooper()) {
            r.run();
        } else {
            postOnMediaHandler(r);
        }
    }

    public final void runOnMediaThreadSync(@NonNull final Runnable r) {
        if (Looper.myLooper() == getMediaLooper()) {
            r.run();
        } else {
            final CountDownLatch latch = new CountDownLatch(1);
            postOnMediaHandler(new Runnable() {
                @Override
                public void run() {
                    logger.debug("run()");
                    r.run();
                    latch.countDown();
                }
            });
            logger.debug("await start");
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            } finally {
                logger.debug("await end");
            }
        }
    }

    @NonNull
    public final Future<?> submitOnExecutor(@NonNull Runnable r) {
        if (!isExecutorRunning()) {
            throw new IllegalStateException("not running");
        }
        return mExecutorService.submit(r);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public final <T> Future<T> submitOnExecutor(@NonNull Callable<T> c) {
        if (!isExecutorRunning()) {
            throw new IllegalStateException("not running");
        }
        return mExecutorService.submit(c);
    }

    @NonNull
    public final ScheduledFuture<?> scheduleOnExecutor(@NonNull Runnable r, long delay) {
        if (!isExecutorRunning()) {
            throw new IllegalStateException("not running");
        }
        if (delay <= 0) {
            throw new IllegalArgumentException("delay <= 0");
        }
        return mExecutorService.schedule(r, delay, TimeUnit.MILLISECONDS);
    }

    @NonNull
    protected final <T> ScheduledFuture<T> scheduleOnExecutor(@NonNull Callable<T> c, long delay) {
        if (!isExecutorRunning()) {
            throw new IllegalStateException("not running");
        }
        return mExecutorService.schedule(c, delay, TimeUnit.MILLISECONDS);
    }

    public interface OnPlaybackTimeUpdateTimeListener {

        void onPlaybackTimeUpdateTime(int position, int duration);
    }

    protected class PlaybackTimeUpdateTimeObservable extends Observable<OnPlaybackTimeUpdateTimeListener> {

        void dispatchPlaybackTimeUpdated() {
            synchronized (mObservers) {
                for (OnPlaybackTimeUpdateTimeListener l : copyOfObservers()) {
                    l.onPlaybackTimeUpdateTime(getCurrentPosition(), getDuration());
                }
            }
        }
    }

    public interface OnBufferingUpdateListener {

        void onBufferingUpdate(int percentage);
    }

    protected static class OnBufferingUpdateObservable extends Observable<OnBufferingUpdateListener> {

        void dispatchOnOnBufferingUpdate(int percentage) {
            synchronized (mObservers) {
                for (OnBufferingUpdateListener l : copyOfObservers()) {
                    l.onBufferingUpdate(percentage);
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

    protected class OnCompletionObservable extends Observable<OnCompletionListener> {

        void dispatchCompleted() {
            synchronized (mObservers) {
                for (OnCompletionListener l : copyOfObservers()) {
                    l.onCompletion(isLooping());
                }
            }
        }
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

        void onError(@NonNull E error);
    }

    protected static class OnErrorObservable<E extends OnErrorListener.MediaError> extends Observable<OnErrorListener<E>> {

        void dispatchError(@NonNull E error) {
            synchronized (mObservers) {
                for (OnErrorListener<E> l : copyOfObservers()) {
                    l.onError(error);
                }
            }
        }
    }

    public interface OnStateChangedListener {

        void onBeforeOpenDataSource();

        void onCurrentStateChanged(@NonNull State currentState, @NonNull State previousState);

        void onTargetStateChanged(@NonNull State targetState);
    }

    protected class OnStateChangedObservable extends Observable<OnStateChangedListener> {

        void dispatchBeforeOpenDataSource() {
            synchronized (mObservers) {
                for (OnStateChangedListener l : copyOfObservers()) {
                    l.onBeforeOpenDataSource();
                }
            }
        }

        void dispatchCurrentStateChanged(@NonNull State previousState) {
            synchronized (mObservers) {
                for (OnStateChangedListener l : copyOfObservers()) {
                    l.onCurrentStateChanged(mCurrentState, previousState);
                }
            }
        }

        void dispatchTargetStateChanged() {
            synchronized (mObservers) {
                for (OnStateChangedListener l : copyOfObservers()) {
                    l.onTargetStateChanged(mTargetState);
                }
            }
        }
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

        @NonNull
        public final String mimeTypeParts;

        public final boolean isInfiniteMode;

        PlayMode(@NonNull String mimeTypeParts, boolean isInfiniteMode) {
            this.mimeTypeParts = mimeTypeParts;
            this.isInfiniteMode = isInfiniteMode;
        }

        @NonNull
        public List<String> getMimeTypeParts() {
            return Arrays.asList(mimeTypeParts.split(","));
        }

        @NonNull
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

}
