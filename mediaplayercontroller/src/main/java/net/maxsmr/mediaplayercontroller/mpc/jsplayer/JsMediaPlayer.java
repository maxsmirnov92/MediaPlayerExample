package net.maxsmr.mediaplayercontroller.mpc.jsplayer;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import net.maxsmr.commonutils.data.CompareUtils;
import net.maxsmr.commonutils.data.Observable;
import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;

import java.net.HttpURLConnection;

public abstract class JsMediaPlayer extends BaseMediaPlayerController<JsMediaPlayer.MediaError> implements ScriptCallback {

    @NonNull
    protected final WebView mWebView;

    @NonNull
    protected final ScriptCallback.Executor mScriptExecutor;

    @NonNull
    protected final PageLoadSuccessObservable mPageLoadSuccessObservable = new PageLoadSuccessObservable();

    private final Runnable mResetRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("mResetRunnable :: run()");
            postOnMediaHandler(new Runnable() {
                @Override
                public void run() {
                    if (isPageLoaded() && !isPlayerReleased()) {
                        if (isPreparing()) {
                            logger.debug("resetting by timeout...");
                            onError(new MediaError(OnErrorListener.MediaError.PREPARE_TIMEOUT_EXCEEDED));
                        }
                    }
                }
            });
        }
    };


    /**
     * runnable to run after insert scripts is successful
     */
    @Nullable
    protected Runnable mInsertDoneRunnable;

    /**
     * for cases when page not loaded
     */
    protected boolean mScheduleOpenDataSource = false;

    protected boolean mPageLoaded = false;

    public JsMediaPlayer(@NonNull WebView webView) {
        super(webView.getContext());
        mScriptExecutor = new ScriptCallback.Executor(this.mWebView = webView);
        mMediaLooper = Looper.getMainLooper();
    }

    @NonNull
    public Observable<OnPageLoadSuccessListener> getPageLoadSuccessObservable() {
        return mPageLoadSuccessObservable;
    }

    private void checkPageUrlEmpty() {
        if (isPageUrlEmpty()) {
            throw new IllegalStateException("page url is empty");
        }
    }

    private void checkPageNotLoaded() {
        if (!isPageLoaded()) {
            throw new IllegalStateException("page is not loaded");
        }
    }

    @MainThread
    public boolean isPageUrlEmpty() {
        return TextUtils.isEmpty(mWebView.getUrl());
    }

    @MainThread
    public boolean isPageLoaded() {
        boolean urlEmpty = isPageUrlEmpty();
        if (urlEmpty || isReleased()) {
            mPageLoaded = false;
        }
        return mPageLoaded;
    }

    protected abstract String makeScriptForUrl(String loadedUrl);

    @CallSuper
    public void onPageStarted(String url, Bitmap favicon) {
        synchronized (mLock) {
            logger.debug("onPageStarted(), url=" + url);
            mPageLoaded = false;
        }
    }

    @CallSuper
    public void onPageFinished(String url) {
        synchronized (mLock) {
            logger.debug("onPageFinished(), url=" + url);

            final String loadedUrl = mWebView.getUrl();

            if (!TextUtils.isEmpty(loadedUrl)) {
                logger.debug("page loaded, making script...");
                String script = makeScriptForUrl(loadedUrl);
                if (!TextUtils.isEmpty(script)) {
                    mInsertDoneRunnable = new Runnable() {
                        @Override
                        public void run() {
                            mScriptExecutor.execute("addCallbacks()");
                            mPageLoaded = true;
                            if (!isReleased()) {
                                clearDataSource(false);
                                if (mScheduleOpenDataSource) {
                                    mScheduleOpenDataSource = false;
                                    logger.debug("openDataSource() was scheduled (after page loaded), opening...");
                                    openDataSource();
                                    mPageLoadSuccessObservable.dispatchPageWithScriptsReady(loadedUrl, true);
                                } else {
                                    mPageLoadSuccessObservable.dispatchPageWithScriptsReady(loadedUrl, false);
                                }
                            }
                        }
                    };
                    mScriptExecutor.execute(script);
                } else {
                    logger.error("can't make script, is empty");
                }
            } else {
                logger.warn("page loaded, url empty");
                postOnMediaHandler(new Runnable() {
                    @Override
                    public void run() {
                        if (!isPlayerReleased()) {
                            releasePlayer(true);
                        }
                    }
                });
            }
        }
    }

    @JavascriptInterface
    @CallSuper
    @Override
    public void onScriptInsertDone() {
        logger.debug("onScriptInsertDone(), time: " + System.currentTimeMillis());
        if (mInsertDoneRunnable != null) {
            postOnMediaHandler(mInsertDoneRunnable);
            mInsertDoneRunnable = null;
        }
    }

//        @JavascriptInterface
//        @Override
//        public void onMediaElementsInsertDone() {
//            logger.debug("onMediaElementsInsertDone(), time: " + System.currentTimeMillis());
//        }

//        @JavascriptInterface
//        public void onAbort() {
//            logger.debug("onAbort()");
//            postOnMediaHandler(new Runnable() {
//                @Override
//                public void run() {
//                    checkPageNotLoaded();
//                    setCurrentState(State.IDLE);
//                    setTargetState(State.IDLE);
//                }
//            });
//        }

    @JavascriptInterface
    @CallSuper
    public void onEnded() {
        logger.debug("onEnded()");
        postOnMediaHandler(new Runnable() {
            @Override
            public void run() {
                if (isPageLoaded() && !isPlayerReleased()) {
                    onCompletion();
                } else {
                    logger.warn("ended, but page is not loaded or player released");
                }
            }
        });
    }

    @JavascriptInterface
    @CallSuper
    public void onPlay() {
        logger.debug("onPlay()");
    }

    @JavascriptInterface
    @CallSuper
    public void onPlaying() {
        logger.debug("onPlaying()");
        postOnMediaHandler(new Runnable() {
            @Override
            public void run() {
                if (isPageLoaded() && !isPlayerReleased()) {
                    setCurrentState(State.PLAYING);
                    setTargetState(State.PLAYING);
//                  startPlaybackTimeTask();
                } else {
                    logger.warn("playing, but page is not loaded or player released");
                }
            }
        });
    }

    @JavascriptInterface
    @CallSuper
    public void onPaused() {
        logger.debug("onPaused()");
        postOnMediaHandler(new Runnable() {
            @Override
            public void run() {
                if (isPageLoaded() && !isPlayerReleased()) {
//                    stopPlaybackTimeTask();
                    setCurrentState(State.PAUSED);
                    setTargetState(State.PAUSED);
                } else {
                    logger.warn("paused, but page is not loaded or player released");
                }
            }
        });
    }

    @JavascriptInterface
    @CallSuper
    public void onDataPrepared() {
        logger.debug("onDataPrepared()");
        postOnMediaHandler(new Runnable() {
            @Override
            public void run() {
                if (isPageLoaded()) {
                    onPrepared();
                } else {
                    logger.warn("data prepared, but page is not loaded");
                }
            }
        });
    }

    @JavascriptInterface
    @CallSuper
    public void onError() {
        logger.error("onError()");
        postOnMediaHandler(new Runnable() {
            @Override
            public void run() {
                if (isPageLoaded()) {
                    onError(new MediaError(OnErrorListener.MediaError.UNKNOWN));
                } else {
                    logger.warn("error, but page is not loaded");
                }
            }
        });
    }

    @MainThread
    @Override
    @CallSuper
    protected boolean onPrepared() {

        synchronized (mLock) {

            checkPageNotLoaded();

            if (!super.onPrepared()) {
                return false;
            }

            setCurrentState(State.PREPARED);

            if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO) {

                final int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
                if (seekToPosition != POSITION_NO) {
                    seekTo(seekToPosition);
                }

                setLooping(mLoopWhenPreparing);
                if (CompareUtils.compareFloats(mVolumeLeftWhenPrepared, (float) VOLUME_NOT_SET, true) != 0 && CompareUtils.compareFloats(mVolumeLeftWhenPrepared, (float) VOLUME_NOT_SET, true) != 0) {
                    setVolume(mVolumeLeftWhenPrepared, mVolumeRightWhenPrepared);
                }

                if (mTargetState == State.PAUSED) {
                    mTargetState = State.PLAYING;
                }

                logger.debug("prepared, url: " + mContentUri + ", target state: " + mTargetState);
                if (mTargetState == State.PLAYING) {
                    start();
                }
            }

            return true;
        }
    }

    @MainThread
    @Override
    protected void onCompletion() {
        synchronized (mLock) {
            checkPageNotLoaded();
            if (!isLooping()) {
                setCurrentState(State.IDLE);
                if (mTargetState == State.PAUSED) {
                    setTargetState(State.PLAYING);
                }
//            setTargetState(State.IDLE);
            }
            super.onCompletion();
        }
    }

    @Override
    @MainThread
    protected boolean onError(@NonNull MediaError error) {
        synchronized (mLock) {
            logger.error("target content: " + (mContentUri != null ? mContentUri : mContentFileDescriptor) + " / mode: " + mPlayMode);
            logger.error("last content: " + (mLastContentUriToOpen != null ? mLastContentUriToOpen : mLastAssetFileDescriptorToOpen) + " / mode: " + mLastModeToOpen);

            checkPageNotLoaded();

            boolean scheduleOpenDataSource = mScheduleOpenDataSource;
            if (!isPlayerReleased()) {
                releasePlayer(false); // or true?
            }
            mScheduleOpenDataSource = scheduleOpenDataSource;

            return super.onError(error);
        }
    }

    @NonNull
    @Override
    protected Runnable getResetRunnable() {
        return mResetRunnable;
    }

    @Override
    public boolean isPlayerReleased() {
        synchronized (mLock) {
            if (!isPageLoaded()) {
                setCurrentState(State.IDLE);
            }
            return mCurrentState == State.IDLE || isReleased();
        }
    }

    @Override
    public boolean isInPlaybackState() {
        synchronized (mLock) {
            if (!isPageLoaded()) {
                setCurrentState(State.IDLE);
            }
            return (!isReleasingPlayer() &&
                    mCurrentState != State.RELEASED &&
                    mCurrentState != State.IDLE &&
                    mCurrentState != State.PREPARING);
        }
    }

    @Override
    public boolean isPreparing() {
        synchronized (mLock) {
            if (!isPageLoaded()) {
                setCurrentState(State.IDLE);
            }
            return mCurrentState == State.PREPARING;
        }
    }

    @Override
    public boolean isPlaying() {
        synchronized (mLock) {
            if (!isPageLoaded()) {
                setCurrentState(State.IDLE);
            }
            return isVideoSpecified() ? mCurrentState == State.PLAYING : mCurrentState == State.PREPARED;
        }
    }

    @Override
    public int getAudioSessionId() {
        throw new UnsupportedOperationException("getAudioSessionId() is not supported");
    }

    @Override
    public boolean isLooping() {
        synchronized (mLock) {
            return mLoopWhenPreparing;
        }
    }

    @Override
    @MainThread
    public void setLooping(boolean toggle) {
        synchronized (mLock) {
            super.setLooping(toggle);
            if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO) {
                if (isInPlaybackState()) {
                    mScriptExecutor.execute("setLoop(" + mLoopWhenPreparing + ")");
                }
            }
        }
    }

    @Override
    @MainThread
    public void setVolume(float left, float right) {
        synchronized (mLock) {
            super.setVolume(left, right);
            if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO) {
                if ((left == VOLUME_NOT_SET || left == VOLUME_MIN || left == VOLUME_MAX) && (right == VOLUME_NOT_SET || right == VOLUME_MIN || right == VOLUME_MAX)) {
                    if (isInPlaybackState()) {
                        float volume = Math.min(left, right);
                        if (Float.compare(volume, VOLUME_MIN) == 0) {
                            mScriptExecutor.execute("setMute(" + true + ")");
                        } else if (Float.compare(volume, VOLUME_MAX) == 0) {
                            mScriptExecutor.execute("setMute(" + false + ")");
                        }
                    }
                }
            }
        }
    }

    @Override
    @MainThread
    public void seekTo(int msec) {
        synchronized (mLock) {
            logger.debug("seekTo(), msec=" + msec);

            checkReleased();

            if (msec < POSITION_START && msec != POSITION_NO)
                throw new IllegalArgumentException("incorrect seek position: " + msec);


            if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO) {
                if (isInPlaybackState()) {
                    if (msec >= POSITION_START) {
                        mScriptExecutor.execute("seekTo(" + msec + ")");
                    }
                    mSeekWhenPrepared = POSITION_NO;
                } else {
                    mSeekWhenPrepared = msec;
                }
            }
        }
    }

    @Override
    public boolean isPlayModeSupported(@NonNull PlayMode playMode) {
        return playMode == PlayMode.AUDIO || playMode == PlayMode.VIDEO || playMode == PlayMode.PICTURE || playMode == PlayMode.PAGE;
    }

    @Override
    public int getCurrentPosition() {
        throw new UnsupportedOperationException("getCurrentPosition() is not supported");
    }

    @Override
    public int getDuration() {
        throw new UnsupportedOperationException("getDuration() is not supported");
    }

    @Override
    public void setContentFd(@NonNull PlayMode playMode, @Nullable AssetFileDescriptor contentFd) {
        throw new UnsupportedOperationException("setContentFd() is not supported");
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    @MainThread
    protected void openDataSource() {
        synchronized (mLock) {
            logger.debug("openDataSource(), content: " + (mContentUri != null ? mContentUri : mContentFileDescriptor) + ", current state: " + mCurrentState);

            checkReleased();

            if (!isContentSpecified()) {
                logger.error("can't open data source: content is not specified");
                return;
            }

            if (!isPreparing()) {

                if (isPageLoaded()) {

                    Uri uri = mContentUri;

                    if (uri != null) {

                        setCurrentState(State.PREPARING);

                        String contentType = HttpURLConnection.guessContentTypeFromName(uri.toString());
                        logger.info("uri content type: " + contentType);

                        if (mNoCheckMediaContentType || !TextUtils.isEmpty(contentType)) {
                            beforeOpenDataSource();
                            if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO) {
                                if (!requestAudioFocus()) {
                                    logger.error("failed to request audio focus");
                                }
                                mScriptExecutor.execute("openDataSource('" + uri.toString() + "', '" + contentType + "')");
                            } else {
                                if (!abandonAudioFocus()) {
                                    logger.error("failed to abandon audio focus");
                                }
                                switch (mPlayMode) {
                                    case PICTURE:
                                        mScriptExecutor.execute("openImage('" + uri.toString() + "')");
                                        break;
                                    case PAGE:
                                        mScriptExecutor.execute("openPage('" + uri.toString() + "')");
                                        break;
                                    default:
                                        throw new IllegalStateException("unsupported " + PlayMode.class.getSimpleName() + ": " + mPlayMode);
                                }
                            }
                            scheduleResetCallback();
                        } else {
                            logger.error("empty uri content type");
                            onError(new MediaError(MediaError.PREPARE_EMPTY_CONTENT_TYPE));
                        }

                    } else {
                        clearDataSource(true);
                    }

                } else {
                    logger.warn("page is currently not loaded, openDataSource() scheduled");
                    mScheduleOpenDataSource = true;
                }
            } else {
                logger.warn("can't open data source: currently is preparing");
                mScheduleOpenDataSource = true;
            }
        }
    }

    @MainThread
    private void clearDataSource(final boolean clearTargetState) {
        synchronized (mLock) {
            logger.debug("clearDataSource(), clearTargetState=" + clearTargetState);
            cancelResetCallback();
//          stopPlaybackTimeTask();
            mScriptExecutor.execute("clearDataSource()");
            mScriptExecutor.execute("clearImage()");
            mScriptExecutor.execute("clearPage()");
            mLastContentUriToOpen = null;
            mLastAssetFileDescriptorToOpen = null;
            mLastModeToOpen = PlayMode.NONE;
            setCurrentState(State.IDLE);
            if (clearTargetState) {
                setTargetState(State.IDLE);
            }
        }
    }

    @Override
    @MainThread
    public void start() {
        synchronized (mLock) {
            logger.debug("start(), current state: " + mCurrentState);
            checkReleased();
            if (!isPlaying()) {
                if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO && isInPlaybackState()) {
                    mScriptExecutor.execute("play()");
//                  startPlaybackTimeTask();
                }
            }
            setTargetState(State.PLAYING);
        }
    }

    @Override
    @MainThread
    public void stop() {
        synchronized (mLock) {
            logger.debug("stop(), current state: " + mCurrentState);
            checkReleased();
            if (mCurrentState != State.IDLE) {
                if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO && isInPlaybackState()) {
                    mScriptExecutor.execute("stop()");
                }
            }
            if (!isPlayerReleased()) {
                releasePlayer(true);
            }
        }
    }

    @Override
    @MainThread
    public void pause() {
        synchronized (mLock) {
            logger.debug("pause(), current state: " + mCurrentState);
            checkReleased();
            if (mCurrentState != State.PAUSED) {
                if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO && isInPlaybackState()) {
                    mScriptExecutor.execute("pause()");
                }
            }
            setTargetState(State.PAUSED);
        }
    }

    @Override
    @MainThread
    protected void releasePlayer(boolean clearTargetState) {
        logger.debug("releasePlayer(), clearTargetState=" + clearTargetState + ", current state: " + mCurrentState);

        synchronized (mLock) {

            checkReleased();

            if (!isReleasingPlayer() && !isPlayerReleased()) {

                mReleasingPlayer = true;
                super.releasePlayer(clearTargetState);

                if (mPlayMode == PlayMode.AUDIO || mPlayMode == PlayMode.VIDEO) {
                    if (!abandonAudioFocus()) {
                        logger.error("failed to abandon audio focus");
                    }
                }

                mScheduleOpenDataSource = false;
                clearDataSource(clearTargetState);
                mReleasingPlayer = false;

            } else if (isPlayerReleased()) {
                logger.debug("already released");
            } else if (isReleasingPlayer()) {
                logger.debug("already releasing now");
            }
        }
    }

    @Override
    @MainThread
    public void release() {
        synchronized (mLock) {
            super.release();
            setContentUri(PlayMode.NONE, null);
            setCurrentState(State.RELEASED);
            mContext = null;
        }
    }

    public class MediaError extends BaseMediaPlayerController.OnErrorListener.MediaError {

        @ErrorDef
        public final int errorCode;

        public MediaError(@ErrorDef int errorCode) {
            this.errorCode = errorCode;
        }

        @Override
        public String toString() {
            return "MediaError{" +
                    "errorCode=" + errorCode +
                    '}';
        }
    }

    public interface OnPageLoadSuccessListener {

        @MainThread
        void onPageWithScriptsReady(String uri, boolean isOpenResourceScheduled);
    }

    protected static class PageLoadSuccessObservable extends Observable<OnPageLoadSuccessListener> {

        private void dispatchPageWithScriptsReady(String uri, boolean isOpenResourceScheduled) {
            synchronized (mObservers) {
                for (OnPageLoadSuccessListener l : copyOfObservers()) {
                    l.onPageWithScriptsReady(uri, isOpenResourceScheduled);
                }
            }
        }
    }

}