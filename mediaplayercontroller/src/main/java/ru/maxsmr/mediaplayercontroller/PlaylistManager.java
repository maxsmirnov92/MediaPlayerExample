package ru.maxsmr.mediaplayercontroller;

import android.content.ContentResolver;
import android.database.Observable;
import android.net.Uri;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

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

public class PlaylistManager {

    public final static int NO_POSITION = -1;

    private static final Set<String> acceptableMimeTypesParts = new HashSet<>();

    static {
        acceptableMimeTypesParts.add("audio");
        acceptableMimeTypesParts.add("video");
    }

    protected static boolean isMimeTypeValid(@Nullable String mimeType) {
        for (String part : acceptableMimeTypesParts) {
            if (mimeType != null && mimeType.contains(part)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean isTrackValid(@Nullable String filePath) {
        if (!TextUtils.isEmpty(filePath) && FileHelper.isFileCorrect(new File(filePath))) {
            String mimeType = HttpsURLConnection.guessContentTypeFromName(Uri.fromFile(new File(filePath)).toString());
            return isMimeTypeValid(mimeType);
        }
        return false;
    }

    @NonNull
    protected static List<String> filterIncorrectTracks(@Nullable Collection<String> files) {
        List<String> incorrectTracks = new ArrayList<>();
        if (files != null) {
            Iterator<String> it = files.iterator();
            while (it.hasNext()) {
                String file = it.next();
                if (!isTrackValid(file)) {
                    incorrectTracks.add(file);
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

    private boolean mLoopPlaylist;

    private int mCurrentTrackIndex = NO_POSITION;

    private final MediaControllerCallbacks mediaControllerCallbacks = new MediaControllerCallbacks();

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
        mPlayerController.getStateChangedObservable().registerObserver(mediaControllerCallbacks);
        mPlayerController.getCompletionObservable().registerObserver(mediaControllerCallbacks);
    }

    public void release() {

        if (mReleased) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was already released");
        }

        mReleased = true;

        clearTracks();
        mPlayerController.getStateChangedObservable().unregisterObserver(mediaControllerCallbacks);
        mPlayerController.getCompletionObservable().unregisterObserver(mediaControllerCallbacks);
    }

    @NonNull
    public MediaPlayerController getPlayerController() {
        return mPlayerController;
    }

    @Nullable
    public String getCurrentTrack() {
        if (mPlayerController.isAudioSpecified()) {
            Uri audioUri = mPlayerController.getAudioUri();
            if (audioUri != null) {
                if (audioUri.getScheme() != null && !audioUri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) {
                    throw new UnsupportedOperationException("only " + ContentResolver.SCHEME_FILE + " scheme is supported, current: " + audioUri.getScheme());
                }
                return audioUri.getPath();
            }
        }
        return null;
    }

    public int getCurrentTrackIndex() {
        return mCurrentTrackIndex;
    }

    public void enableLoopPlaylist(boolean enable) {
        mLoopPlaylist = enable;
    }

    public void toggleLoopPlaylist() {
        mLoopPlaylist = !mLoopPlaylist;
    }

    public void playTrack(@Nullable String path) {
        playTrack(indexOf(path));
    }

    public void playTrack(int at) {
        playTrackInternal(getTrack(mCurrentTrackIndex = at));
    }

    public void playNextTrack() {
        if (!isTracksEmpty()) {
            if (mCurrentTrackIndex < getTracksCount() - 1) {
                playTrack(mCurrentTrackIndex + 1);
            } else {
                if (mLoopPlaylist) {
                    playTrack(0);
                } else {
                    resetTrack();
                }
            }
        }
    }

    public void playPreviousTrack() {
        if (!isTracksEmpty()) {
            if (mCurrentTrackIndex > 0) {
                playTrack(mCurrentTrackIndex - 1);
            } else {
                playTrack(0);
            }
        }
    }

    private void playTrackInternal(@Nullable String path) {

        if (mReleased) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was released");
        }

        mPlayerController.setAudioPath(path);
        mPlayerController.start();
        mPlayerController.resume();
    }

    public void resetTrack() {

        if (mReleased) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was released");
        }

        mCurrentTrackIndex = NO_POSITION;
        mPlayerController.stop();
        mPlayerController.setAudioUri(null);
        mPlayerController.setVideoUri(null);
    }

    public Observable<OnTracksSetListener> getTracksSetObservable() {
        return mTracksSetObservable;
    }

    public Observable<OnTrackAddedListener> getTrackAddedObservable() {
        return mTrackAddedObservable;
    }

    public Observable<OnTrackSetListener> getTrackSetObservable() {
        return mTrackSetObservable;
    }

    public Observable<OnTrackRemovedListener> getTrackRemovedObservable() {
        return mTrackRemovedObservable;
    }

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
    public String getTrack(int at) {
        rangeCheck(at);
        return mTracks.get(at);
    }

    public int indexOf(String track) {
        return mTracks.indexOf(track);
    }

    public int lastIndexOf(String track) {
        return mTracks.lastIndexOf(track);
    }

    public void sort(@NonNull Comparator<? super String> comparator) {
        Collections.sort(mTracks, comparator);
    }

    /**
     * @param tracks null for reset adapter
     */
    public final synchronized void setTracks(@Nullable Collection<String> tracks) {
        clearTracks();
        if (tracks != null) {
            List<String> incorrect = filterIncorrectTracks(tracks);
            if (!incorrect.isEmpty()) {
                onTracksSetFailed(incorrect);
            }
            if (!tracks.isEmpty()) {
                this.mTracks.addAll(tracks);
                onTracksSet();
            }
        }
    }

    @CallSuper
    protected void onTracksSet() {
        mTracksSetObservable.dispatchSet(getTracks());
    }

    @CallSuper
    protected void onTracksSetFailed(@NonNull List<String> incorrectTracks) {
        mTracksSetObservable.dispatchNotSet(incorrectTracks);
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

    public final synchronized boolean addTrack(int to, @Nullable String track) {
        rangeCheckForAdd(to);
        if (isTrackValid(track)) {
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

    public final synchronized void addTrack(@Nullable String track) {
        addTrack(getTracksCount(), track);
    }

    public final synchronized void addTracks(@NonNull List<String> tracks) {
        for (String track : tracks) {
            addTrack(track);
        }
    }

    @CallSuper
    protected void onTrackAdded(int addedPosition, @Nullable String track) {
        mTrackAddedObservable.dispatchAdded(addedPosition, track);
    }

    @CallSuper
    protected void onTrackAddFailed(int addedPosition, @Nullable String track) {
        mTrackAddedObservable.dispatchAddFailed(addedPosition, track);
    }

    public final synchronized void setTrack(int in, @Nullable String track) {
        rangeCheck(in);
        if (isTrackValid(track)) {
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

    @CallSuper
    protected void onTrackSet(int setPosition, @Nullable String track) {
        mTrackSetObservable.dispatchSet(setPosition, track);
    }

    @CallSuper
    protected void onTrackSetFailed(int setPosition, @Nullable String track) {
        mTrackSetObservable.dispatchSetFailed(setPosition, track);
    }

    @Nullable
    public final synchronized String removeTrack(@Nullable String track) {
        return removeTrack(indexOf(track));
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
    protected void onTrackRemoved(int removedPosition, @Nullable String track) {
        mTrackRemovedObservable.dispatchRemoved(removedPosition, track);
    }

    private class MediaControllerCallbacks implements MediaPlayerController.OnStateChangedListener, MediaPlayerController.OnCompletionListener  {

        @Override
        public void onCurrentStateChanged(@NonNull MediaPlayerController.State currentState) {
            if (!isTracksEmpty()) {
                String currentTrack = getCurrentTrack();
                if (!TextUtils.isEmpty(currentTrack)) {
                    if (indexOf(currentTrack) == NO_POSITION) {
                        throw new IllegalStateException("no track " + currentTrack + " in playlist");
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
        public void onCompletion() {
            playNextTrack();
        }
    }

    public interface OnTracksSetListener {
        void onTracksSet(@NonNull List<String> newTracks);

        void onTracksNotSet(@NonNull List<String> incorrectTracks);
    }

    public interface OnTrackAddedListener {
        void onTrackAdded(int to, String trackPath);

        void onTrackAddFailed(int to, String trackPath);
    }

    public interface OnTrackSetListener {
        void onTrackSet(int in, String trackPath);

        void onTrackSetFailed(int in, String trackPath);
    }

    public interface OnTrackRemovedListener {
        void onTrackRemoved(int from, String trackPath);
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

        private void dispatchAdded(int to, String trackPath) {
            for (OnTrackAddedListener l : mObservers) {
                l.onTrackAdded(to, trackPath);
            }
        }

        private void dispatchAddFailed(int to, String trackPath) {
            for (OnTrackAddedListener l : mObservers) {
                l.onTrackAddFailed(to, trackPath);
            }
        }
    }

    private static class OnTrackSetObservable extends Observable<OnTrackSetListener> {

        private void dispatchSet(int in, String trackPath) {
            for (OnTrackSetListener l : mObservers) {
                l.onTrackSet(in, trackPath);
            }
        }

        private void dispatchSetFailed(int in, String trackPath) {
            for (OnTrackSetListener l : mObservers) {
                l.onTrackSetFailed(in, trackPath);
            }
        }
    }

    private static class OnTrackRemovedObservable extends Observable<OnTrackRemovedListener> {

        private void dispatchRemoved(int from, String trackPath) {
            for (OnTrackRemovedListener l : mObservers) {
                l.onTrackRemoved(from, trackPath);
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


}
