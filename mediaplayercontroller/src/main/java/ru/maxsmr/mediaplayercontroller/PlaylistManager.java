package ru.maxsmr.mediaplayercontroller;

import android.content.ContentResolver;
import android.database.Observable;
import android.net.Uri;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import ru.maxsmr.commonutils.data.FileHelper;
import ru.maxsmr.mediaplayercontroller.mpc.MediaPlayerController;

public class PlaylistManager {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistManager.class);

    public static final PlayMode DEFAULT_PLAY_MODE = PlayMode.AUDIO;

    public final static int NO_POSITION = -1;

    public static final String[] DEFAULT_ACCEPTABLE_FILE_MIME_TYPES_PARTS = new String[] {"audio", "video"};

    private static final Set<String> acceptableFileMimeTypesParts = new HashSet<>();

    static {
        for (String defPart : DEFAULT_ACCEPTABLE_FILE_MIME_TYPES_PARTS) {
            acceptableFileMimeTypesParts.add(defPart);
        }
    }

    public static void addAcceptableFileMimeTypeParts(String... parts) {
        if (parts != null) {
            for (String part : parts) {
                acceptableFileMimeTypesParts.add(part);
            }
        }
    }

    public static void removeAcceptableFileMimeTypeParts(String... parts) {
        if (parts != null) {
            for (String part : parts) {
                acceptableFileMimeTypesParts.remove(part);
            }
        }
    }

    /**  */
    protected static boolean isFileMimeTypeValid(@Nullable String mimeType) {
        for (String part : acceptableFileMimeTypesParts) {
            if (mimeType != null && mimeType.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param uriString may be path (if file) or full uri
     */
    protected static boolean isTrackValid(@Nullable String uriString) {
        if (!TextUtils.isEmpty(uriString)) {
            Uri uri = Uri.parse(uriString);
            boolean isFile = TextUtils.isEmpty(uri.getScheme()) || uri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE);
            if (isFile) {
                return FileHelper.isFileCorrect(new File(uri.getPath())) && isFileMimeTypeValid(HttpsURLConnection.guessContentTypeFromName(uriString));
            } else
                return uri.getScheme() != null && (uri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_CONTENT)
                        || uri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        || uri.getScheme().equalsIgnoreCase("http")
                        || uri.getScheme().equalsIgnoreCase("https"));
        }
        return false;
    }

    @NonNull
    protected static List<String> filterIncorrectTracks(@Nullable Collection<String> trackUrls) {
        List<String> incorrectTracks = new ArrayList<>();
        if (trackUrls != null) {
            Iterator<String> it = trackUrls.iterator();
            while (it.hasNext()) {
                String url = it.next();
                if (!isTrackValid(url)) {
                    incorrectTracks.add(url);
                    it.remove();
                }
            }
        }
        return incorrectTracks;
    }

    public PlaylistManager(@NonNull MediaPlayerController playerController) {
        this.mPlayerController = playerController;
        this.init();
    }

    private boolean mReleased = false;

    @NonNull
    private final MediaPlayerController mPlayerController;

    @NonNull
    private PlayMode mPlayMode = DEFAULT_PLAY_MODE;

    private boolean mLoopPlaylist;

    private int mCurrentTrackIndex = NO_POSITION;

    private final MediaControllerCallbacks mMediaControllerCallbacks = new MediaControllerCallbacks();

    /** contains strings with urls or paths */
    @NonNull
    private final ArrayList<String> mTracks = new ArrayList<>();

    private final OnTracksSetObservable mTracksSetObservable = new OnTracksSetObservable();

    private final OnTrackAddedObservable mTrackAddedObservable = new OnTrackAddedObservable();

    private final OnTrackSetObservable mTrackSetObservable = new OnTrackSetObservable();

    private final OnTrackRemovedObservable mTrackRemovedObservable = new OnTrackRemovedObservable();

    private final OnTracksClearedObservable mTracksClearedObservable = new OnTracksClearedObservable();

    private void init() {

        if (mReleased) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was released");
        }

        resetTrack();
        mPlayerController.getStateChangedObservable().registerObserver(mMediaControllerCallbacks);
        mPlayerController.getCompletionObservable().registerObserver(mMediaControllerCallbacks);
    }

    public void release() {

        if (mReleased) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was already released");
        }

        clearTracks();
        mPlayerController.getStateChangedObservable().unregisterObserver(mMediaControllerCallbacks);
        mPlayerController.getCompletionObservable().unregisterObserver(mMediaControllerCallbacks);

        mReleased = true;
    }

    @NonNull
    public MediaPlayerController getPlayerController() {
        return mPlayerController;
    }

    public PlayMode getPlayMode() {
        return mPlayMode;
    }

    /** tracks will be cleared */
    public void setPlayMode(@NonNull PlayMode playMode) {
        if (playMode != mPlayMode) {
            clearTracks();
            mPlayMode = playMode;
        }
    }

    public boolean isPlaylistLooping() {
        return mLoopPlaylist;
    }

    public void enableLoopPlaylist(boolean enable) {
        mLoopPlaylist = enable;
    }

    public void toggleLoopPlaylist() {
        mLoopPlaylist = !mLoopPlaylist;
    }

    @Nullable
    public Uri getCurrentTrackUri() {
        final Uri resourceUri;
        if (mPlayerController.isAudioSpecified()) {
            resourceUri = mPlayerController.getAudioUri();
        } else if (mPlayerController.isVideoSpecified()) {
            resourceUri = mPlayerController.getVideoUri();
        } else {
            resourceUri = null;
        }
        return resourceUri;
    }

    public String getCurrentTrackPath() {
        Uri trackUri = getCurrentTrackUri();
        return trackUri != null? trackUri.getPath() : null;
    }

    public int getCurrentTrackIndex() {
        return mCurrentTrackIndex;
    }

    public void playTrack(@Nullable String path) {
        logger.debug("playTrack(), path=" + path);
        playTrack(indexOf(path));
    }

    public void playTrack(int at) throws IndexOutOfBoundsException {
        logger.debug("playTrack(), at=" + at);
        if (!isTracksEmpty()) {
            playTrackInternal(getTrack(mCurrentTrackIndex = at));
        }
    }

    public void playFirstTrack() {
        logger.debug("playFirstTrack()");
        playTrack(0);
    }

    public void playLastTrack() {
        logger.debug("playLastTrack()");
        playTrack(getTracksCount() - 1);
    }

    public void playNextTrack() {
        logger.debug("playNextTrack()");
        if (!isTracksEmpty()) {
            if (mCurrentTrackIndex < getTracksCount() - 1) {
                playTrack(mCurrentTrackIndex + 1);
            } else {
                if (mLoopPlaylist) {
                    logger.debug("loop is enabled, playing from start...");
                    playFirstTrack();
                } else {
                    logger.debug("loop is disabled, resetting...");
                    resetTrack();
                }
            }
        }
    }

    public void playPreviousTrack() {
        logger.debug("playPreviousTrack()");
        if (!isTracksEmpty()) {
            if (mCurrentTrackIndex > 0) {
                playTrack(mCurrentTrackIndex - 1);
            } else {
                playTrack(0);
            }
        }
    }

    private void playTrackInternal(@Nullable String path) {
        logger.debug("playTrackInternal(), path=" + path);

        if (mReleased) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was released");
        }

        switch (mPlayMode) {
            case AUDIO:
                mPlayerController.setAudioPath(path);
                break;
            case VIDEO:
                mPlayerController.setVideoPath(path);
                break;
            default:
                throw new IllegalArgumentException("unknown playMode: " + mPlayMode);
        }

        mPlayerController.start();
        mPlayerController.resume();
    }

    public void resetTrack() {
        logger.debug("resetTrack()");

        if (mReleased) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was released");
        }

        mCurrentTrackIndex = NO_POSITION;
        mPlayerController.stop();
        mPlayerController.setAudioUri(null);
        mPlayerController.setVideoUri(null);
    }

    @NonNull
    public Observable<OnTracksSetListener> getTracksSetObservable() {
        return mTracksSetObservable;
    }

    @NonNull
    public Observable<OnTrackAddedListener> getTrackAddedObservable() {
        return mTrackAddedObservable;
    }

    @NonNull
    public Observable<OnTrackSetListener> getTrackSetObservable() {
        return mTrackSetObservable;
    }

    @NonNull
    public Observable<OnTrackRemovedListener> getTrackRemovedObservable() {
        return mTrackRemovedObservable;
    }

    @NonNull
    public Observable<OnTracksClearedListener> getTracksClearedObservable() {
        return mTracksClearedObservable;
    }

    public boolean isTracksEmpty() {
        return getTracksCount() == 0;
    }

    public int getTracksCount() {
        return mTracks.size();
    }

    protected void rangeCheck(int position) {
        if (position < 0 || position >= mTracks.size()) {
            throw new IndexOutOfBoundsException("incorrect position: " + position);
        }
    }

    protected void rangeCheckForAdd(int position) {
        if (position < 0 || position > mTracks.size()) {
            throw new IndexOutOfBoundsException("incorrect add position: " + position);
        }
    }

    @NonNull
    public ArrayList<String> getTracks() {
        return new ArrayList<>(mTracks);
    }

    @Nullable
    public String getTrack(int at) throws IndexOutOfBoundsException {
        rangeCheck(at);
        return mTracks.get(at);
    }

    public int indexOf(String trackUrl) {
        return mTracks.indexOf(trackUrl);
    }

    public int lastIndexOf(String trackUrl) {
        return mTracks.lastIndexOf(trackUrl);
    }

    public void sort(@NonNull Comparator<? super String> comparator) {
        Collections.sort(mTracks, comparator);
    }

    /**
     * @param trackUrls null for reset playlist
     */
    public final synchronized void setTracks(@Nullable Collection<String> trackUrls) {
        clearTracks();
        if (trackUrls != null) {
            List<String> incorrect = filterIncorrectTracks(trackUrls);
            if (!incorrect.isEmpty()) {
                onTracksSetFailed(incorrect);
            }
            if (!trackUrls.isEmpty()) {
                this.mTracks.addAll(trackUrls);
                onTracksSet();
            }
        }
    }

    @CallSuper
    protected void onTracksSet() {
        mTracksSetObservable.dispatchSet(getTracks());
    }

    @CallSuper
    protected void onTracksSetFailed(@NonNull List<String> incorrectTrackUrls) {
        mTracksSetObservable.dispatchNotSet(incorrectTrackUrls);
    }

    public final synchronized void clearTracks() {
        if (!isTracksEmpty()) {
            resetTrack();
            int oldCount = mTracks.size();
            mTracks.clear();
            onTracksCleared(oldCount);
        }
    }

    @CallSuper
    protected void onTracksCleared(int oldCount) {
        mTracksClearedObservable.dispatchCleared(oldCount);
    }

    public final synchronized boolean addTrack(int to, @Nullable String trackUrl) throws IndexOutOfBoundsException {
        rangeCheckForAdd(to);
        if (isTrackValid(trackUrl)) {
            if (to == mCurrentTrackIndex) {
                mCurrentTrackIndex++;
            }
            mTracks.add(to, trackUrl);
            onTrackAdded(to, trackUrl);
            return true;
        }
        onTrackAddFailed(to, trackUrl);
        return false;
    }

    public final synchronized void addTrack(@Nullable String trackUrl) {
        addTrack(getTracksCount(), trackUrl);
    }

    public final synchronized void addTracks(@NonNull List<String> trackUrls) {
        for (String track : trackUrls) {
            addTrack(track);
        }
    }

    @CallSuper
    protected void onTrackAdded(int addedPosition, @Nullable String trackUrl) {
        mTrackAddedObservable.dispatchAdded(addedPosition, trackUrl);
    }

    @CallSuper
    protected void onTrackAddFailed(int addedPosition, @Nullable String trackUrl) {
        mTrackAddedObservable.dispatchAddFailed(addedPosition, trackUrl);
    }

    public final synchronized void setTrack(int in, @Nullable String trackUrl) {
        rangeCheck(in);
        if (isTrackValid(trackUrl)) {
            mTracks.set(in, trackUrl);
            if (in == mCurrentTrackIndex) {
                resetTrack();
                playTrack(in);
            }
            onTrackSet(in, trackUrl);
        } else {
            onTrackSetFailed(in, trackUrl);
        }
    }

    @CallSuper
    protected void onTrackSet(int setPosition, @Nullable String trackUrl) {
        mTrackSetObservable.dispatchSet(setPosition, trackUrl);
    }

    @CallSuper
    protected void onTrackSetFailed(int setPosition, @Nullable String trackUrl) {
        mTrackSetObservable.dispatchSetFailed(setPosition, trackUrl);
    }

    @Nullable
    public final synchronized String removeTrack(@Nullable String trackUrl) {
        return removeTrack(indexOf(trackUrl));
    }

    public final synchronized String removeTrack(int from) {
        rangeCheck(from);
        if (from == mCurrentTrackIndex) {
            resetTrack();
        }
        String removedTrack = getTrack(from);
        mTracks.remove(from);
        onTrackRemoved(from, removedTrack);
        return removedTrack;
    }

    @NonNull
    public final synchronized List<String> removeTracksRange(int from, int to) {
        rangeCheck(from);
        rangeCheck(to);
        List<String> removed = new ArrayList<>();
        for (int pos = from; pos <= to; pos++) {
            removed.add(removeTrack(pos));
        }
        return removed;
    }
    public final synchronized void removeAllTracks() {
        for (String track : mTracks) {
            removeTrack(track);
        }
    }

    @CallSuper
    protected void onTrackRemoved(int removedPosition, @Nullable String trackUrl) {
        mTrackRemovedObservable.dispatchRemoved(removedPosition, trackUrl);
    }

    private class MediaControllerCallbacks implements MediaPlayerController.OnStateChangedListener, MediaPlayerController.OnCompletionListener {

        @Override
        public void onCurrentStateChanged(@NonNull MediaPlayerController.State currentState, @NonNull MediaPlayerController.State previousState) {
            if (!isTracksEmpty()) {
                Uri currentTrackUri = getCurrentTrackUri();
                String currentTrackUriString = currentTrackUri != null? currentTrackUri.toString() : null;
                if (!TextUtils.isEmpty(currentTrackUriString)) {
                    if (indexOf(currentTrackUriString) == NO_POSITION) {
                        throw new IllegalStateException("track " + currentTrackUriString + " not found in playlist!");
                    }
                } else {
                    mCurrentTrackIndex = NO_POSITION;
                }
            }
        }

        @Override
        public void onTargetStateChanged(@NonNull MediaPlayerController.State targetState) {

        }

        @Override
        public void onCompletion(boolean isLooping) {
            if (!isLooping) {
                playNextTrack();
            }
        }
    }

    public interface OnTracksSetListener {
        void onTracksSet(@NonNull List<String> newTracks);

        void onTracksNotSet(@NonNull List<String> incorrectTracks);
    }

    public interface OnTrackAddedListener {
        void onTrackAdded(int to, String trackUrl);

        void onTrackAddFailed(int to, String trackUrl);
    }

    public interface OnTrackSetListener {
        void onTrackSet(int in, String trackUrl);

        void onTrackSetFailed(int in, String trackUrl);
    }

    public interface OnTrackRemovedListener {
        void onTrackRemoved(int from, String trackUrl);
    }

    public interface OnTracksClearedListener {
        void onTracksCleared(int oldCount);
    }

    private static class OnTracksSetObservable extends Observable<OnTracksSetListener> {

        private void dispatchSet(@NonNull List<String> newTracks) {
            for (OnTracksSetListener l : mObservers) {
                l.onTracksSet(newTracks);
            }
        }

        private void dispatchNotSet(@NonNull List<String> incorrectTracks) {
            if (!incorrectTracks.isEmpty()) {
                for (OnTracksSetListener l : mObservers) {
                    l.onTracksNotSet(incorrectTracks);
                }
            }
        }
    }

    private static class OnTrackAddedObservable extends Observable<OnTrackAddedListener> {

        private void dispatchAdded(int to, String trackUrl) {
            for (OnTrackAddedListener l : mObservers) {
                l.onTrackAdded(to, trackUrl);
            }
        }

        private void dispatchAddFailed(int to, String trackUrl) {
            for (OnTrackAddedListener l : mObservers) {
                l.onTrackAddFailed(to, trackUrl);
            }
        }
    }

    private static class OnTrackSetObservable extends Observable<OnTrackSetListener> {

        private void dispatchSet(int in, String trackUrl) {
            for (OnTrackSetListener l : mObservers) {
                l.onTrackSet(in, trackUrl);
            }
        }

        private void dispatchSetFailed(int in, String trackUrl) {
            for (OnTrackSetListener l : mObservers) {
                l.onTrackSetFailed(in, trackUrl);
            }
        }
    }

    private static class OnTrackRemovedObservable extends Observable<OnTrackRemovedListener> {

        private void dispatchRemoved(int from, String trackUrl) {
            for (OnTrackRemovedListener l : mObservers) {
                l.onTrackRemoved(from, trackUrl);
            }
        }
    }

    private static class OnTracksClearedObservable extends Observable<OnTracksClearedListener> {

        private void dispatchCleared(int oldCount) {
            for (OnTracksClearedListener l : mObservers) {
                l.onTracksCleared(oldCount);
            }
        }
    }

    public enum PlayMode {
        AUDIO, VIDEO
    }

}
