package ru.maxsmr.mediaplayercontroller.playlist;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import ru.maxsmr.commonutils.android.SynchronizedObservable;
import ru.maxsmr.commonutils.data.CompareUtils;
import ru.maxsmr.commonutils.data.FileHelper;
import ru.maxsmr.mediaplayercontroller.mpc.MediaPlayerController;

public class PlaylistManager<T extends PlaylistItem> {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistManager.class);

    public static final PlayMode DEFAULT_PLAY_MODE = PlayMode.AUDIO;

    public final static int NO_POSITION = -1;

    public static final String[] DEFAULT_ACCEPTABLE_FILE_MIME_TYPES_PARTS = new String[]{"audio", "video"};

    private final Set<String> acceptableFileMimeTypesParts = new HashSet<>();

    {
        for (String defPart : DEFAULT_ACCEPTABLE_FILE_MIME_TYPES_PARTS) {
            synchronized (acceptableFileMimeTypesParts) {
                acceptableFileMimeTypesParts.add(defPart);
            }
        }
    }

    public void addAcceptableFileMimeTypeParts(String... parts) {
        synchronized (acceptableFileMimeTypesParts) {
            if (parts != null) {
                for (String part : parts) {
                    acceptableFileMimeTypesParts.add(part);
                }
            }
        }
    }

    public void removeAcceptableFileMimeTypeParts(String... parts) {
        synchronized (acceptableFileMimeTypesParts) {
            if (parts != null) {
                for (String part : parts) {
                    acceptableFileMimeTypesParts.remove(part);
                }
            }
        }
    }

    public void ignoreAcceptableFileMimeTypeParts() {
        synchronized (acceptableFileMimeTypesParts) {
            acceptableFileMimeTypesParts.clear();
        }
    }

    protected boolean isFileMimeTypeValid(@Nullable String mimeType) { // , @NonNull Set<String> acceptableFileMimeTypesParts
//        acceptableFileMimeTypesParts = new LinkedHashSet<>(acceptableFileMimeTypesParts);
        if (acceptableFileMimeTypesParts.isEmpty()) {
            return true;
        }
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
    protected boolean isTrackValid(@Nullable String uriString) {
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

    /**
     * @return uri with "file" scheme if it was empty or null if uriString url is incorrect
     */
    @Nullable
    protected String fixUrlScheme(@Nullable String uriString) {
        if (uriString != null && isTrackValid(uriString)) {
            Uri uri = Uri.parse(uriString);
            if (TextUtils.isEmpty(uri.getScheme())) {
                return uri.buildUpon().scheme(ContentResolver.SCHEME_FILE).build().toString();
            } else {
                return uriString;
            }
        }
        return null;
    }

    @NonNull
    protected List<T> filterIncorrectTracks(@Nullable Collection<T> tracks) {
        List<T> incorrectTracks = new ArrayList<>();
        if (tracks != null) {
            Iterator<T> it = tracks.iterator();
            while (it.hasNext()) {
                T track = it.next();
                if (track != null && !isTrackValid(track.track)) {
                    incorrectTracks.add(track);
                    it.remove();
                }
            }
        }
        return incorrectTracks;
    }


    public PlaylistManager(MediaPlayerController playerController, Class<T> itemClass) {
        if (playerController == null) {
            throw new NullPointerException("playerController is null");
        }
        if (itemClass == null) {
            throw new NullPointerException("itemClass is null");
        }
        this.mPlayerController = playerController;
        this.mItemClass = itemClass;
        this.init();
    }

    private final Class<T> mItemClass;

    private boolean mReleased = false;

    private final MediaPlayerController mPlayerController;

    @NonNull
    private PlayMode mPlayMode = DEFAULT_PLAY_MODE;

    private boolean mLoopPlaylist;

    private int mCurrentTrackIndex = NO_POSITION;

    private final MediaControllerCallbacks mMediaControllerCallbacks = new MediaControllerCallbacks();

    /**
     * contains strings with urls or paths
     */
    @NonNull
    private final ArrayList<T> mTracks = new ArrayList<>();

    private final OnTracksSetObservable<T> mTracksSetObservable = new OnTracksSetObservable<>();

    private final OnTrackAddedObservable<T> mTrackAddedObservable = new OnTrackAddedObservable<>();

    private final OnTrackSetObservable<T> mTrackSetObservable = new OnTrackSetObservable<>();

    private final OnTrackRemovedObservable<T> mTrackRemovedObservable = new OnTrackRemovedObservable<>();

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

    public Class<T> getItemClass() {
        return mItemClass;
    }

    @NonNull
    public MediaPlayerController getPlayerController() {
        return mPlayerController;
    }

    public PlayMode getPlayMode() {
        return mPlayMode;
    }

    /**
     * tracks will be cleared if different
     */
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

    public boolean isPlaying() {
        return isInPlaybackState() && mPlayerController.isPlaying();
    }

    public boolean isPreparing() {
        return mCurrentTrackIndex != NO_POSITION && mPlayerController.isPreparing();
    }

    public boolean isInPlaybackState() {
        return mCurrentTrackIndex != NO_POSITION && mPlayerController.isInPlaybackState();
    }

    @Nullable
    public Uri getCurrentTrackUri() {
        final Uri resourceUri;
        if (mPlayerController.isAudioSpecified() && mPlayerController.getAudioUri() != null) {
            resourceUri = mPlayerController.getAudioUri();
        } else if (mPlayerController.isVideoSpecified() && mPlayerController.getVideoUri() != null) {
            resourceUri = mPlayerController.getVideoUri();
        } else {
            resourceUri = null;
        }
        return resourceUri != null ? Uri.parse(fixUrlScheme(resourceUri.toString())) : null;
    }

    @Nullable
    public String getCurrentTrackPath() {
        Uri trackUri = getCurrentTrackUri();
        return trackUri != null ? trackUri.getPath() : null;
    }

    public int getCurrentTrackIndex() {
        return mCurrentTrackIndex;
    }

    @Nullable
    public T getCurrentTrack() {
        return mCurrentTrackIndex != NO_POSITION ? getTrack(mCurrentTrackIndex) : null;
    }

    public void playTrack(T track) {
        logger.debug("playTrack(), track=" + track);
        playTrack(indexOf(track));
    }

    public void playTrack(int at) throws IndexOutOfBoundsException {
        logger.debug("playTrack(), at=" + at + " / count=" + getTracksCount());
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

    private void playTrackInternal(T track) {
        logger.debug("playTrackInternal(), track=" + track);

        if (mReleased) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was released");
        }

        if (track == null) {
            throw new NullPointerException("track is null");
        }

        switch (mPlayMode) {
            case AUDIO:
                mPlayerController.setAudioPath(fixUrlScheme(track.track));
                break;
            case VIDEO:
                mPlayerController.setVideoPath(fixUrlScheme(track.track));
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
    public Observable<OnTracksSetListener<T>> getTracksSetObservable() {
        return mTracksSetObservable;
    }

    @NonNull
    public Observable<OnTrackAddedListener<T>> getTrackAddedObservable() {
        return mTrackAddedObservable;
    }

    @NonNull
    public Observable<OnTrackSetListener<T>> getTrackSetObservable() {
        return mTrackSetObservable;
    }

    @NonNull
    public Observable<OnTrackRemovedListener<T>> getTrackRemovedObservable() {
        return mTrackRemovedObservable;
    }

    @NonNull
    public Observable<OnTracksClearedListener> getTracksClearedObservable() {
        return mTracksClearedObservable;
    }

    public final boolean isTracksEmpty() {
        return getTracksCount() == 0;
    }

    public final int getTracksCount() {
        synchronized (mTracks) {
            return mTracks.size();
        }
    }

    protected final void rangeCheck(int position) {
        synchronized (mTracks) {
            if (position < 0 || position >= mTracks.size()) {
                throw new IndexOutOfBoundsException("incorrect position: " + position);
            }
        }
    }

    protected final void rangeCheckForAdd(int position) {
        synchronized (mTracks) {
            if (position < 0 || position > mTracks.size()) {
                throw new IndexOutOfBoundsException("incorrect add position: " + position);
            }
        }
    }

    @NonNull
    public final ArrayList<T> getTracks() {
        synchronized (mTracks) {
            return new ArrayList<>(mTracks);
        }
    }

    @NonNull
    public final T getTrack(int at) throws IndexOutOfBoundsException {
        synchronized (mTracks) {
            rangeCheck(at);
            return mTracks.get(at);
        }
    }

    public final int indexOf(String trackUrl) {
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                trackUrl = isTrackValid(trackUrl) ? fixUrlScheme(trackUrl) : trackUrl;
                for (int i = 0; i < mTracks.size(); i++) {
                    T curTrack = mTracks.get(i);
                    if (curTrack != null) {
                        String curTrackUrl = isTrackValid(curTrack.track) ? fixUrlScheme(curTrack.track) : curTrack.track;
                        if (CompareUtils.stringsEqual(trackUrl, curTrackUrl, true)) {
                            return i;
                        }
                    }
                }
            }
            return NO_POSITION;
        }
    }

    public final int lastIndexOf(String trackUrl) {
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                trackUrl = isTrackValid(trackUrl) ? fixUrlScheme(trackUrl) : trackUrl;
                for (int i = mTracks.size() - 1; i >= 0; i--) {
                    T curTrack = mTracks.get(i);
                    if (curTrack != null) {
                        String curTrackUrl = isTrackValid(curTrack.track) ? fixUrlScheme(curTrack.track) : curTrack.track;
                        if (CompareUtils.stringsEqual(trackUrl, curTrackUrl, true)) {
                            return i;
                        }
                    }
                }
            }
            return NO_POSITION;
        }
    }

    public final int indexOf(T track) {
        synchronized (mTracks) {
            return mTracks.indexOf(track);
        }
    }

    public final int lastIndexOf(T track) {
        synchronized (mTracks) {
            return mTracks.lastIndexOf(track);
        }
    }

    public void sort(@NonNull Comparator<? super T> comparator) {
        synchronized (mTracks) {
            Collections.sort(mTracks, comparator);
        }
    }

    /**
     * @param tracks null for reset playlist
     */
    public final void setTracks(@Nullable Collection<T> tracks) {
        synchronized (mTracks) {
            clearTracks();
            if (tracks != null) {
                tracks = new ArrayList<>(tracks);
                List<T> incorrect = filterIncorrectTracks(tracks);
                if (!incorrect.isEmpty()) {
                    onTracksSetFailed(incorrect);
                }
                if (!tracks.isEmpty()) {
                    this.mTracks.addAll(tracks);
                    onTracksSet();
                }
            }
        }
    }

    /**
     * @param tracks null for reset playlist
     */
    public final void setTracks(T... tracks) {
        setTracks(tracks != null ? Arrays.asList(tracks) : null);
    }

    @CallSuper
    protected void onTracksSet() {
        mTracksSetObservable.dispatchSet(getTracks());
    }

    @CallSuper
    protected void onTracksSetFailed(@NonNull List<T> incorrectTracks) {
        mTracksSetObservable.dispatchNotSet(incorrectTracks);
    }

    public final void clearTracks() {
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                resetTrack();
                int oldCount = mTracks.size();
                mTracks.clear();
                onTracksCleared(oldCount);
            }
        }
    }

    @CallSuper
    protected void onTracksCleared(int oldCount) {
        mTracksClearedObservable.dispatchCleared(oldCount);
    }

    public final boolean addTrack(int to, T track) throws IndexOutOfBoundsException {
        synchronized (mTracks) {
            rangeCheckForAdd(to);
            if (track != null && isTrackValid(track.track)) {
                if (to == mCurrentTrackIndex) {
                    mCurrentTrackIndex++;
                }
                mTracks.add(to, track);
                onTrackAdded(to, track);
                return true;
            }
            onTrackAddFailed(to, track);
            return false;
        }
    }

    public final void addTrack(T track) {
        addTrack(getTracksCount(), track);
    }

    public final void addTracks(@NonNull List<T> tracks) {
        for (T track : tracks) {
            addTrack(track);
        }
    }

    @CallSuper
    protected void onTrackAdded(int addedPosition, T track) {
        mTrackAddedObservable.dispatchAdded(addedPosition, track);
    }

    @CallSuper
    protected void onTrackAddFailed(int addedPosition, T track) {
        mTrackAddedObservable.dispatchAddFailed(addedPosition, track);
    }

    public final void setTrack(int in, T track) {
        synchronized (mTracks) {
            rangeCheck(in);
            if (track != null && isTrackValid(track.track)) {
                mTracks.set(in, track);
                if (in == mCurrentTrackIndex) {
                    resetTrack();
                    playTrack(in);
                }
                onTrackSet(in, track);
            } else {
                onTrackSetFailed(in, track);
            }
        }
    }

    @CallSuper
    protected void onTrackSet(int setPosition, T track) {
        mTrackSetObservable.dispatchSet(setPosition, track);
    }

    @CallSuper
    protected void onTrackSetFailed(int setPosition, T track) {
        mTrackSetObservable.dispatchSetFailed(setPosition, track);
    }

    public final T removeTrack(T track) {
        return removeTrack(indexOf(track));
    }

    public final T removeTrack(int from) {
        synchronized (mTracks) {
            rangeCheck(from);
            if (from == mCurrentTrackIndex) {
                resetTrack();
            }
            T removedTrack = getTrack(from);
            mTracks.remove(from);
            onTrackRemoved(from, removedTrack);
            return removedTrack;
        }
    }

    @NonNull
    public final List<T> removeTracksRange(int from, int to) {
        rangeCheck(from);
        rangeCheck(to);
        List<T> removed = new ArrayList<>();
        for (int pos = from; pos <= to; pos++) {
            removed.add(removeTrack(pos));
        }
        return removed;
    }

    public final void removeAllTracks() {
        synchronized (mTracks) {
            for (T track : mTracks) {
                removeTrack(track);
            }
        }
    }

    @CallSuper
    protected void onTrackRemoved(int removedPosition, T track) {
        mTrackRemovedObservable.dispatchRemoved(removedPosition, track);
    }

    private class MediaControllerCallbacks implements MediaPlayerController.OnStateChangedListener, MediaPlayerController.OnCompletionListener {

        @Override
        public void onCurrentStateChanged(@NonNull MediaPlayerController.State currentState, @NonNull MediaPlayerController.State previousState) {
            if (!isTracksEmpty()) {

                boolean contains = false;

                Uri currentTrackUri = getCurrentTrackUri();
                String currentTrackUriString = currentTrackUri != null ? currentTrackUri.toString() : null;

                if (!TextUtils.isEmpty(currentTrackUriString)) {
                    contains = indexOf(currentTrackUriString) != NO_POSITION;
                }
//                else {
//                    contains = false;
//                    throw new IllegalStateException(MediaPlayerController.class.getName() + " is in playback state, but resource uri is empty");
//                }

                if (mPlayerController.isInPlaybackState() || mPlayerController.isPreparing()) {
                    if (!contains) {
                        throw new IllegalStateException("track " + currentTrackUriString + " not found in playlist!");
                    }
                } else {
                    if (!contains) {
                        mCurrentTrackIndex = NO_POSITION;
                    }
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

    public interface OnTracksSetListener<T extends PlaylistItem> {
        void onTracksSet(@NonNull List<T> newTracks);

        void onTracksNotSet(@NonNull List<T> incorrectTracks);
    }

    public interface OnTrackAddedListener<T extends PlaylistItem> {
        void onTrackAdded(int to, T trackUrl);

        void onTrackAddFailed(int to, T trackUrl);
    }

    public interface OnTrackSetListener<T extends PlaylistItem> {
        void onTrackSet(int in, T track);

        void onTrackSetFailed(int in, T track);
    }

    public interface OnTrackRemovedListener<T extends PlaylistItem> {
        void onTrackRemoved(int from, T track);
    }

    public interface OnTracksClearedListener {
        void onTracksCleared(int oldCount);
    }

    private static class OnTracksSetObservable<T extends PlaylistItem> extends SynchronizedObservable<OnTracksSetListener<T>> {

        private void dispatchSet(@NonNull List<T> newTracks) {
            synchronized (mObservers) {
                for (OnTracksSetListener<T> l : copyOfObservers()) {
                    l.onTracksSet(newTracks);
                }
            }
        }

        private void dispatchNotSet(@NonNull List<T> incorrectTracks) {
            if (!incorrectTracks.isEmpty()) {
                synchronized (mObservers) {
                    for (OnTracksSetListener<T> l : copyOfObservers()) {
                        l.onTracksNotSet(incorrectTracks);
                    }
                }
            }
        }
    }

    private static class OnTrackAddedObservable<T extends PlaylistItem> extends SynchronizedObservable<OnTrackAddedListener<T>> {

        private void dispatchAdded(int to, T track) {
            synchronized (mObservers) {
                for (OnTrackAddedListener<T> l : copyOfObservers()) {
                    l.onTrackAdded(to, track);
                }
            }
        }

        private void dispatchAddFailed(int to, T track) {
            synchronized (mObservers) {
                for (OnTrackAddedListener<T> l : copyOfObservers()) {
                    l.onTrackAddFailed(to, track);
                }
            }
        }
    }

    private static class OnTrackSetObservable<T extends PlaylistItem> extends SynchronizedObservable<OnTrackSetListener<T>> {

        private void dispatchSet(int in, T track) {
            synchronized (mObservers) {
                for (OnTrackSetListener<T> l : copyOfObservers()) {
                    l.onTrackSet(in, track);
                }
            }
        }

        private void dispatchSetFailed(int in, T track) {
            synchronized (mObservers) {
                for (OnTrackSetListener<T> l : copyOfObservers()) {
                    l.onTrackSetFailed(in, track);
                }
            }
        }
    }

    private static class OnTrackRemovedObservable<T extends PlaylistItem> extends SynchronizedObservable<OnTrackRemovedListener<T>> {

        private void dispatchRemoved(int from, T track) {
            synchronized (mObservers) {
                for (OnTrackRemovedListener<T> l : copyOfObservers()) {
                    l.onTrackRemoved(from, track);
                }

            }
        }
    }

    private static class OnTracksClearedObservable extends SynchronizedObservable<OnTracksClearedListener> {

        private void dispatchCleared(int oldCount) {
            synchronized (mObservers) {
                for (OnTracksClearedListener l : copyOfObservers()) {
                    l.onTracksCleared(oldCount);
                }

            }
        }
    }

    public enum PlayMode {
        AUDIO, VIDEO
    }

}
