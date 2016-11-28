package net.maxsmr.mediaplayercontroller.playlist;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import net.maxsmr.commonutils.android.media.MetadataRetriever;
import net.maxsmr.commonutils.data.CompareUtils;
import net.maxsmr.commonutils.data.FileHelper;
import net.maxsmr.commonutils.data.MathUtils;
import net.maxsmr.commonutils.data.Observable;
import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;
import net.maxsmr.mediaplayercontroller.playlist.item.AbsPlaylistItem;
import net.maxsmr.mediaplayercontroller.playlist.item.DescriptorPlaylistItem;
import net.maxsmr.mediaplayercontroller.playlist.item.UriPlaylistItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

public class PlaylistManager<C extends BaseMediaPlayerController, T extends AbsPlaylistItem> {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistManager.class);

    public final static int NO_POSITION = -1;

    /**
     * empty -> accept all
     */
    private final Set<String> mAcceptableFileMimeTypePrefixes = new HashSet<>();

    {
        synchronized (mAcceptableFileMimeTypePrefixes) {
            for (BaseMediaPlayerController.PlayMode m : BaseMediaPlayerController.PlayMode.values()) {
                mAcceptableFileMimeTypePrefixes.addAll(m.getMimeTypeParts());
            }
        }
    }

    public void addAcceptableFileMimeTypePrefixes(String... prefixes) {
        synchronized (mAcceptableFileMimeTypePrefixes) {
            if (prefixes != null) {
                Collections.addAll(mAcceptableFileMimeTypePrefixes, prefixes);
            }
        }
    }

    public void removeAcceptableFileMimeTypePrefixes(String... parts) {
        synchronized (mAcceptableFileMimeTypePrefixes) {
            if (parts != null) {
                for (String part : parts) {
                    mAcceptableFileMimeTypePrefixes.remove(part);
                }
            }
        }
    }

    public void clearAcceptableFileMimeTypePrefixes() {
        synchronized (mAcceptableFileMimeTypePrefixes) {
            mAcceptableFileMimeTypePrefixes.clear();
        }
    }

    protected boolean isFileMimeTypeValid(@Nullable String mimeType) {
        if (mAcceptableFileMimeTypePrefixes.isEmpty()) {
            return true;
        }
        for (String part : mAcceptableFileMimeTypePrefixes) {
            if (mimeType != null && mimeType.toLowerCase().startsWith(part)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isTrackValid(@Nullable T item) {
        boolean isValid = false;
        if (item != null) {
            if (item instanceof UriPlaylistItem) {
                isValid = isTrackValid(((UriPlaylistItem) item).uri);
            } else if (item instanceof DescriptorPlaylistItem) {
                isValid = isTrackValid(((DescriptorPlaylistItem) item).descriptor);
            } else {
                throw new UnsupportedOperationException("incorrect item class: " + item.getClass());
            }
        }
        return isValid && isTrackPlayModeSupported(item);
    }

    protected boolean isTrackPlayModeSupported(@Nullable T item) {
        checkReleased();
        return item != null && mPlayerController.isPlayModeSupported(item.playMode);
    }

    /**
     * @param uriString may be path (if file) or full uri
     */
    protected boolean isTrackValid(@Nullable String uriString) {
        checkReleased();
        if (!TextUtils.isEmpty(uriString)) {
            Uri uri = Uri.parse(uriString);
            if (uri.isHierarchical()) {
                boolean isFile = TextUtils.isEmpty(uri.getScheme()) || uri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE);
                if (isFile) {
                    return !TextUtils.isEmpty(uri.getPath()) &&
                            FileHelper.isFileCorrect(new File(uri.getPath())) &&
                            isFileMimeTypeValid(HttpURLConnection.guessContentTypeFromName(uriString));
                } else
                    return uri.getScheme() != null && (uri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_CONTENT)
                            || uri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_ANDROID_RESOURCE)
                            || uri.getScheme().equalsIgnoreCase("http")
                            || uri.getScheme().equalsIgnoreCase("https"));
            }
        }
        return false;
    }

    protected boolean isTrackValid(@Nullable AssetFileDescriptor fd) {
        checkReleased();
        return fd != null && fd.getLength() > 0;
    }

    /**
     * @return uri with "file" scheme if it was empty or null if uriString url is incorrect
     */
    @Nullable
    protected String fixUrl(@Nullable String uriString) {
        String newUri = uriString;
        if (uriString != null && isTrackValid(uriString)) {
            Uri uri = Uri.parse(uriString);
            if (TextUtils.isEmpty(uri.getScheme())) {
                newUri = uri.buildUpon().scheme(ContentResolver.SCHEME_FILE).build().toString();
            } else {
                newUri = uri.toString();
            }
//            try {
//                newUri = URLEncoder.encode(newUri, "utf-8");
//            } catch (UnsupportedEncodingException e) {
//                e.printStackTrace();
//            }
        }
        return newUri;
    }

    @NonNull
    protected List<T> filterIncorrectTracks(@Nullable Collection<T> tracks) {
        List<T> incorrectTracks = new ArrayList<>();
        if (tracks != null) {
            Iterator<T> it = tracks.iterator();
            while (it.hasNext()) {
                T track = it.next();
                if (!isTrackValid(track)) {
                    logger.error("track " + track + " is not valid");
                    incorrectTracks.add(track);
                    it.remove();
                }
            }
        }
        return incorrectTracks;
    }


    public PlaylistManager(@NonNull C playerController, @NonNull Class<T> itemClass) {
        mPlayerController = playerController;
        mItemClass = itemClass;
        init();
    }

    private final Class<T> mItemClass;

    @SuppressWarnings("unchecked")
    public Class<C> getPlayerClass() {
        return (Class<C>) mPlayerController.getClass();
    }

    public Class<T> getItemClass() {
        return mItemClass;
    }

    private boolean mReleased = false;

    private C mPlayerController;

    private ScheduledFuture<?> mTrackResetFuture;

//    @NonNull
//    private PlayMode mPlayMode = DEFAULT_PLAY_MODE;

    private boolean mLoopPlaylist;

    private int mCurrentTrackIndex = NO_POSITION;

    @NonNull
    private TracksSwitchMode mTracksSwitchMode = TracksSwitchMode.CONSEQUENTIALLY;

    private final MediaControllerCallbacks mMediaControllerCallbacks = new MediaControllerCallbacks();

    /**
     * contains strings with urls or paths
     */
    @NonNull
    private final ArrayList<T> mTracks = new ArrayList<>();

    private final OnActiveTrackChangedObservable<T> mActiveTrackChangedObservable = new OnActiveTrackChangedObservable<>();

    private final OnTracksSetObservable<T> mTracksSetObservable = new OnTracksSetObservable<>();

    private final OnTrackAddedObservable<T> mTrackAddedObservable = new OnTrackAddedObservable<>();

    private final OnTrackSetObservable<T> mTrackSetObservable = new OnTrackSetObservable<>();

    private final OnTrackRemovedObservable<T> mTrackRemovedObservable = new OnTrackRemovedObservable<>();

    private final OnTracksClearedObservable mTracksClearedObservable = new OnTracksClearedObservable();

    private final Runnable mTrackResetRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("mTrackResetRunnable :: run()");
            synchronized (mTracks) {
                checkReleased();
                if (mPlayerController != null && !isTracksEmpty()) {
                    mPlayerController.postOnMediaHandler(new Runnable() {
                        @Override
                        public void run() {
                            logger.debug("timeouted, moving to next...");
                            mMediaControllerCallbacks.onCompletion(false);
                        }
                    });
                }
            }
        }
    };

    public boolean isReleased() {
        synchronized (mTracks) {
            return mReleased;
        }
    }

    private void checkReleased() {
        if (isReleased()) {
            throw new IllegalStateException(PlaylistManager.class.getSimpleName() + " was released");
        }
    }

    @SuppressWarnings("unchecked")
    private void init() {

        checkReleased();

        resetTrack();
        mPlayerController.getStateChangedObservable().registerObserver(mMediaControllerCallbacks);
        mPlayerController.getCompletionObservable().registerObserver(mMediaControllerCallbacks);
        mPlayerController.getErrorObservable().registerObserver(mMediaControllerCallbacks);
    }

    @SuppressWarnings("unchecked")
    public void release() {

        checkReleased();

        clearTracks();
        mPlayerController.getStateChangedObservable().unregisterObserver(mMediaControllerCallbacks);
        mPlayerController.getCompletionObservable().unregisterObserver(mMediaControllerCallbacks);
        mPlayerController.getErrorObservable().unregisterObserver(mMediaControllerCallbacks);

        mPlayerController = null;

        mReleased = true;
    }

    @NonNull
    public BaseMediaPlayerController getPlayerController() {
        checkReleased();
        return mPlayerController;
    }

    @NonNull
    public Looper getMediaLooper() {
        checkReleased();
        return mPlayerController.getMediaLooper();
    }

    @NonNull
    public BaseMediaPlayerController.State getCurrentState() {
        checkReleased();
        return mPlayerController.getCurrentState();
    }

    @NonNull
    public BaseMediaPlayerController.State getTargetState() {
        checkReleased();
        return mPlayerController.getTargetState();
    }

    @NonNull
    public TracksSwitchMode getTracksSwitchMode() {
        synchronized (mTracks) {
            checkReleased();
            return mTracksSwitchMode;
        }
    }

    public void setTracksSwitchMode(@NonNull TracksSwitchMode tracksSwitchMode) {
        synchronized (mTracks) {
            checkReleased();
            mTracksSwitchMode = tracksSwitchMode;
        }
    }

    public boolean isPlaylistLooping() {
        synchronized (mTracks) {
            checkReleased();
            return mLoopPlaylist;
        }
    }

    public void setLoopPlaylist(boolean toggle) {
        synchronized (mTracks) {
            checkReleased();
            mLoopPlaylist = toggle;
        }
    }

    public void toggleLoopPlaylist() {
        synchronized (mTracks) {
            checkReleased();
            mLoopPlaylist = !mLoopPlaylist;
        }
    }

    public boolean isPlaying() {
        synchronized (mTracks) {
            checkReleased();
            return isInPlaybackState() && mPlayerController.isPlaying();
        }
    }

    public boolean isPreparing() {
        synchronized (mTracks) {
            checkReleased();
            return mCurrentTrackIndex != NO_POSITION && mPlayerController.isPreparing();
        }
    }

    public boolean isInPlaybackState() {
        synchronized (mTracks) {
            checkReleased();
            return mCurrentTrackIndex != NO_POSITION && mPlayerController.isInPlaybackState();
        }
    }

    @Nullable
    public AssetFileDescriptor getCurrentTrackDescriptor() {
        synchronized (mTracks) {
            return mPlayerController.getContentAssetFileDescriptor();
        }
    }

    @Nullable
    public Uri getCurrentTrackUri() {
        synchronized (mTracks) {
            checkReleased();
            final Uri resourceUri = mPlayerController.getContentUri();
            return resourceUri != null ? Uri.parse(fixUrl(resourceUri.toString())) : null;
        }
    }

    @Nullable
    public String getCurrentTrackPath() {
        checkReleased();
        Uri trackUri = getCurrentTrackUri();
        return trackUri != null ? trackUri.getPath() : null;
    }

    public boolean hasCurrentTrack() {
        synchronized (mTracks) {
            checkReleased();
            return mCurrentTrackIndex != NO_POSITION;
        }
    }

    public int getCurrentTrackIndex() {
        synchronized (mTracks) {
            checkReleased();
            return mCurrentTrackIndex;
        }
    }

    @Nullable
    public T getCurrentTrack() {
        synchronized (mTracks) {
            return hasCurrentTrack() ? getTrack(mCurrentTrackIndex) : null;
        }
    }

    public void prepareTrack(@NonNull T track) {
        logger.debug("prepareTrack(), track=" + track);
        synchronized (mTracks) {
            prepareTrack(indexOf(track));
        }
    }

    public void prepareTrack(int at) throws IndexOutOfBoundsException {
        logger.debug("prepareTrack(), at=" + at + " / count=" + getTracksCount());
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                T previous = getCurrentTrack();
                prepareTrackInternal(getTrack(mCurrentTrackIndex = at));
                mActiveTrackChangedObservable.dispatchPrepare(getCurrentTrack(), previous);
            }
        }
    }

    public void prepareFirstTrack() {
        logger.debug("prepareFirstTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                prepareTrack(0);
            }
        }
    }

    public void prepareLastTrack() {
        logger.debug("prepareLastTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                prepareTrack(getTracksCount() - 1);
            }
        }
    }

    public void playTrack(@NonNull T track) {
        logger.debug("playTrack(), track=" + track);
        synchronized (mTracks) {
            playTrack(indexOf(track));
        }
    }

    public void playTrack(int at) throws IndexOutOfBoundsException {
        logger.debug("playTrack(), at=" + at + " / count=" + getTracksCount());
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                playTrackInternal(getTrack(mCurrentTrackIndex = at));
            }
        }
    }

    public void playFirstTrack() {
        logger.debug("playFirstTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                playTrack(0);
            }
        }
    }

    public void playLastTrack() {
        logger.debug("playLastTrack()");
        synchronized (mTracks) {
            playTrack(getTracksCount() - 1);
        }
    }

    public void playPreviousTrack() {
        logger.debug("playPreviousTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                if (mCurrentTrackIndex > 0) {
                    playTrack(mCurrentTrackIndex - 1);
                } else {
                    playTrack(0);
                }
            }
        }
    }

    public void playNextTrack() {
        logger.debug("playNextTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                if (mCurrentTrackIndex < getTracksCount() - 1) {
                    if (mCurrentTrackIndex == NO_POSITION) {
                        playFirstTrack();
                    } else if (mCurrentTrackIndex > NO_POSITION) {
                        playTrack(mCurrentTrackIndex + 1);
                    } else {
                        throw new IllegalStateException("incorrect current track index: " + mCurrentTrackIndex);
                    }
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
    }

    public void preparePreviousTrack() {
        logger.debug("preparePreviousTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                if (mCurrentTrackIndex > 0) {
                    prepareTrack(mCurrentTrackIndex - 1);
                } else {
                    prepareTrack(0);
                }
            }
        }
    }

    public void prepareNextTrack() {
        logger.debug("prepareNextTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                if (mCurrentTrackIndex < getTracksCount() - 1) {
                    if (mCurrentTrackIndex == NO_POSITION) {
                        prepareFirstTrack();
                    } else if (mCurrentTrackIndex > NO_POSITION) {
                        prepareTrack(mCurrentTrackIndex + 1);
                    } else {
                        throw new IllegalStateException("incorrect current track index: " + mCurrentTrackIndex);
                    }
                } else {
                    if (mLoopPlaylist) {
                        logger.debug("loop is enabled, playing from start...");
                        prepareFirstTrack();
                    } else {
                        logger.debug("loop is disabled, resetting...");
                        resetTrack();
                    }
                }
            }
        }
    }

    public void previousTrack() {
        logger.debug("previousTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                if (getTargetState() == BaseMediaPlayerController.State.PLAYING) {
                    playPreviousTrack();
                } else {
                    preparePreviousTrack();
                }
            }
        }
    }

    public void nextTrack() {
        logger.debug("nextTrack()");
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                if (getTargetState() == BaseMediaPlayerController.State.PLAYING) {
                    playNextTrack();
                } else {
                    prepareNextTrack();
                }
            }
        }
    }

    public void nextTrackByMode() {
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                boolean handled = false;
                switch (mTracksSwitchMode) {
                    case RANDOM:
                        if (hasCurrentTrack()) {
                            if (getTracksCount() > 1) {
                                int prevIndex = mCurrentTrackIndex;
                                int newIndex = MathUtils.randInt(0, getTracksCount() - 1);
                                if (newIndex != prevIndex) {
                                    if (getTargetState() == BaseMediaPlayerController.State.PLAYING) {
                                        playTrack(newIndex);
                                    } else {
                                        prepareTrack(newIndex);
                                    }
                                    handled = true;
                                }
                            }
                        }
                        break;
                }
                if (!handled) {
                    if (getTargetState() == BaseMediaPlayerController.State.PLAYING) {
                        playNextTrack();
                    } else {
                        prepareNextTrack();
                    }
                }
            }
        }
    }

    private void setTrackInternal(@NonNull T track) {

        checkReleased();

        cancelResetFuture();

//        mPlayerController.clearContent();

        if (track instanceof UriPlaylistItem) {
            mPlayerController.setContentUri(track.playMode, Uri.parse(fixUrl(((UriPlaylistItem) track).uri)));
        } else if (track instanceof DescriptorPlaylistItem) {
            mPlayerController.setContentFd(track.playMode, ((DescriptorPlaylistItem) track).descriptor);
        } else {
            throw new RuntimeException("unknown track class: " + track.getClass());
        }

        mPlayerController.setLooping(track.isLooping);
    }

    private void prepareTrackInternal(@NonNull T track) {
        logger.debug("prepareTrackInternal(), track=" + track);
        T previous = getCurrentTrack();
        setTrackInternal(track);
        mPlayerController.resume();
        boolean schedule = false;
        if (track.duration != AbsPlaylistItem.DURATION_NOT_SPECIFIED) {
            schedule = /*!mLoopPlaylist ||*/ getTracksCount() > 1;
            if (track.playMode != BaseMediaPlayerController.PlayMode.NONE && !track.playMode.isInfiniteMode) {
                if (track instanceof UriPlaylistItem) {
                    Uri uri = !TextUtils.isEmpty(((UriPlaylistItem) track).uri) ? Uri.parse(fixUrl(((UriPlaylistItem) track).uri)) : null;
                    if (uri != null && CompareUtils.stringsEqual(uri.getScheme(), ContentResolver.SCHEME_FILE, true) && !TextUtils.isEmpty(uri.getPath())) {
                        long actualDuration = MetadataRetriever.extractMediaDuration(new File(uri.getPath()));
                        schedule = actualDuration <= 0 || ((UriPlaylistItem) track).duration <= actualDuration - 1000;
                    }
                } else if (track instanceof DescriptorPlaylistItem) {
                    AssetFileDescriptor fd = ((DescriptorPlaylistItem) track).descriptor;
                    long actualDuration = MetadataRetriever.extractMediaDuration(fd.getFileDescriptor());
                    schedule = actualDuration <= 0 || ((DescriptorPlaylistItem) track).duration <= actualDuration - 1000;
                }
            }
        }
        if (schedule) {
            logger.debug("scheduling reset callback (play timeout) after " + track.duration + " ms");
            mTrackResetFuture = mPlayerController.scheduleOnExecutor(mTrackResetRunnable, track.duration);
        }
        mActiveTrackChangedObservable.dispatchPrepare(getCurrentTrack(), previous);
    }

    private void playTrackInternal(@NonNull T track) {
        logger.debug("playTrackInternal(), track=" + track);
        T previous = getCurrentTrack();
        prepareTrackInternal(track);
        mPlayerController.start();
        mActiveTrackChangedObservable.dispatchPlay(getCurrentTrack(), previous);
    }

    private void cancelResetFuture() {
        if (mTrackResetFuture != null) {
            if (!mTrackResetFuture.isCancelled() && !mTrackResetFuture.isDone()) {
//                logger.debug("cancelling reset callback (play timeout)...");
                mTrackResetFuture.cancel(true);
            }
            mTrackResetFuture = null;
        }
    }

    public void resetTrack() {
        logger.debug("resetTrack()");
        synchronized (mTracks) {
            checkReleased();

            cancelResetFuture();

            if (mCurrentTrackIndex != NO_POSITION) {

                T previous = getCurrentTrack();
                mCurrentTrackIndex = NO_POSITION;

                if (!mPlayerController.isReleased()) {
                    mPlayerController.stop();
                    if (mPlayerController.getContentUri() != null) {
                        mPlayerController.setContentUri(BaseMediaPlayerController.PlayMode.NONE, null);
                    }
                    if (mPlayerController.getContentAssetFileDescriptor() != null) {
                        mPlayerController.setContentFd(BaseMediaPlayerController.PlayMode.NONE, null);
                    }
                }

                if (previous != null) {
                    mActiveTrackChangedObservable.dispatchReset(previous);
                }
            }
        }
    }

    @NonNull
    public Observable<OnActiveTrackChangedListener<T>> getActiveTrackChangedObservable() {
        return mActiveTrackChangedObservable;
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

    protected final void rangeCheck(int position) throws IndexOutOfBoundsException {
        synchronized (mTracks) {
            if (position < 0 || position >= mTracks.size()) {
                throw new IndexOutOfBoundsException("incorrect position: " + position);
            }
        }
    }

    protected final void rangeCheckForAdd(int position) throws IndexOutOfBoundsException {
        synchronized (mTracks) {
            if (position < 0 || position > mTracks.size()) {
                throw new IndexOutOfBoundsException("incorrect add position: " + position);
            }
        }
    }

    @NonNull
    public final List<T> getTracks() {
        synchronized (mTracks) {
            return Collections.unmodifiableList(mTracks);
        }
    }

    @NonNull
    public final T getTrack(int at) throws IndexOutOfBoundsException {
        synchronized (mTracks) {
            checkReleased();
            rangeCheck(at);
            return mTracks.get(at);
        }
    }

    public final int indexOf(String trackUrl) {
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                trackUrl = fixUrl(trackUrl);
                for (int i = 0; i < mTracks.size(); i++) {
                    T curTrack = mTracks.get(i);
                    if (curTrack != null) {
                        if (isTrackValid(curTrack) && curTrack instanceof UriPlaylistItem) {
                            if (CompareUtils.stringsEqual(trackUrl, fixUrl(((UriPlaylistItem) curTrack).uri), true)) {
                                return i;
                            }
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
                trackUrl = isTrackValid(trackUrl) ? fixUrl(trackUrl) : trackUrl;
                for (int i = mTracks.size() - 1; i >= 0; i--) {
                    T curTrack = mTracks.get(i);
                    if (curTrack != null) {
                        if (isTrackValid(curTrack) && curTrack instanceof UriPlaylistItem) {
                            if (CompareUtils.stringsEqual(trackUrl, fixUrl(((UriPlaylistItem) curTrack).uri), true)) {
                                return i;
                            }
                        }
                    }
                }
            }
            return NO_POSITION;
        }
    }

    public final int indexOf(AssetFileDescriptor descriptor) {
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                for (int i = 0; i < mTracks.size(); i++) {
                    T curTrack = mTracks.get(i);
                    if (curTrack != null) {
                        if (isTrackValid(curTrack) && curTrack instanceof DescriptorPlaylistItem) {
                            if (CompareUtils.objectsEqual(descriptor, ((DescriptorPlaylistItem) curTrack).descriptor)) {
                                return i;
                            }
                        }
                    }
                }
            }
            return NO_POSITION;
        }
    }

    public final int lastIndexOf(AssetFileDescriptor descriptor) {
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                for (int i = mTracks.size() - 1; i >= 0; i--) {
                    T curTrack = mTracks.get(i);
                    if (curTrack != null) {
                        if (isTrackValid(curTrack) && curTrack instanceof DescriptorPlaylistItem) {
                            if (CompareUtils.objectsEqual(descriptor, ((DescriptorPlaylistItem) curTrack).descriptor)) {
                                return i;
                            }
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

    public <O extends AbsPlaylistItem.ItemSortOption> void sort(@NonNull AbsPlaylistItem.ItemComparator<O, ? super T> comparator) {
        synchronized (mTracks) {
            T previousTrack = getCurrentTrack();
            Collections.sort(mTracks, comparator);
            mCurrentTrackIndex = indexOf(previousTrack);
        }
    }

    public final void shuffleTracks() {
        synchronized (mTracks) {
            if (!isTracksEmpty()) {
                List<T> shuffledTracks = new ArrayList<>(mTracks);
                Collections.shuffle(shuffledTracks);
                if (hasCurrentTrack()) {
                    boolean wasPlaying = getTargetState() == BaseMediaPlayerController.State.PLAYING;
                    T currentTrack = getTrack(mCurrentTrackIndex);
                    int newIndex = shuffledTracks.indexOf(currentTrack);
                    setTracks(shuffledTracks);
                    if (!isTracksEmpty()) {
                        if (newIndex == NO_POSITION) {
                            newIndex = 0;
                        }
                        if (wasPlaying) {
                            playTrack(newIndex);
                        } else {
                            prepareTrack(newIndex);
                        }
                    }
                }
            }
        }
    }

    /**
     * @param tracks null or empty to reset playlist
     */
    public final boolean setTracks(@Nullable Collection<T> tracks) {
        synchronized (mTracks) {
            boolean result = true;
            clearTracks();
            if (tracks != null) {
                tracks = new ArrayList<>(tracks);
                List<T> incorrect = filterIncorrectTracks(tracks);
                if (!incorrect.isEmpty()) {
                    onTracksSetFailed(incorrect);
                    result = false;
                }
                if (!tracks.isEmpty()) {
                    this.mTracks.addAll(tracks);
                    onTracksSet();
                }
            }
            return result;
        }
    }

    public final boolean setTracks(T... tracks) {
        return setTracks(tracks != null ? Arrays.asList(tracks) : null);
    }

    public final boolean setTracksWithShuffle(@Nullable Collection<T> tracks) {
        List<T> tracksList = null;
        if (tracks != null) {
            tracksList = new ArrayList<>(tracks);
            Collections.shuffle(tracksList);
        }
        return setTracks(tracksList);
    }

    public final boolean setTracksWithShuffle(T... tracks) {
        return setTracksWithShuffle(tracks != null ? Arrays.asList(tracks) : null);
    }

    @CallSuper
    protected void onTracksSet() {
        logger.debug("onTracksSet()");
        mTracksSetObservable.dispatchSet(getTracks());
    }

    @CallSuper
    protected void onTracksSetFailed(@NonNull List<T> incorrectTracks) {
        logger.error("onTracksSetFailed(), incorrectTracks=" + incorrectTracks);
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
        logger.debug("onTracksCleared(), oldCount=" + oldCount);
        mTracksClearedObservable.dispatchCleared(oldCount);
    }

    public final boolean addTrack(int to, T track) throws IndexOutOfBoundsException {
        synchronized (mTracks) {
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
        logger.debug("onTrackAdded(), addedPosition=" + addedPosition + ", track=" + track);
        mTrackAddedObservable.dispatchAdded(addedPosition, track);
    }

    @CallSuper
    protected void onTrackAddFailed(int addedPosition, T track) {
        mTrackAddedObservable.dispatchAddFailed(addedPosition, track);
    }

    public final boolean setTrack(int in, T track) {
        synchronized (mTracks) {
            rangeCheck(in);
            if (isTrackValid(track)) {
                mTracks.set(in, track);
                if (in == mCurrentTrackIndex) {
                    resetTrack();
                    playTrack(in);
                }
                onTrackSet(in, track);
                return true;
            } else {
                onTrackSetFailed(in, track);
                return false;
            }
        }
    }

    @CallSuper
    protected void onTrackSet(int setPosition, T track) {
        logger.debug("onTrackSet(), setPosition=" + setPosition + ", track=" + track);
        mTrackSetObservable.dispatchSet(setPosition, track);
    }

    @CallSuper
    protected void onTrackSetFailed(int setPosition, T track) {
        logger.error("onTrackSetFailed(), setPosition=" + setPosition + ", track=" + track);
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
            if (isTracksEmpty()) {
                onTracksCleared(1);
            }
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
        logger.debug("onTrackRemoved(), removedPosition=" + removedPosition + ", track=" + track);
        mTrackRemovedObservable.dispatchRemoved(removedPosition, track);
    }

    @Override
    public String toString() {
        return "PlaylistManager{" +
                "mAcceptableFileMimeTypePrefixes=" + mAcceptableFileMimeTypePrefixes +
                ", mItemClass=" + mItemClass +
                ", mReleased=" + mReleased +
                ", mPlayerController=" + mPlayerController +
                ", mLoopPlaylist=" + mLoopPlaylist +
                ", mCurrentTrackIndex=" + mCurrentTrackIndex +
                ", mMediaControllerCallbacks=" + mMediaControllerCallbacks +
                ", mTracks=" + mTracks +
                ", mActiveTrackChangedObservable=" + mActiveTrackChangedObservable +
                ", mTracksSetObservable=" + mTracksSetObservable +
                ", mTrackAddedObservable=" + mTrackAddedObservable +
                ", mTrackSetObservable=" + mTrackSetObservable +
                ", mTrackRemovedObservable=" + mTrackRemovedObservable +
                ", mTracksClearedObservable=" + mTracksClearedObservable +
                '}';
    }

    private class MediaControllerCallbacks implements BaseMediaPlayerController.OnStateChangedListener, BaseMediaPlayerController.OnCompletionListener, BaseMediaPlayerController.OnErrorListener {

        @Override
        public void onBeforeOpenDataSource() {

        }

        @Override
        public void onCurrentStateChanged(@NonNull BaseMediaPlayerController.State currentState, @NonNull BaseMediaPlayerController.State previousState) {
            synchronized (mTracks) {
                if (!isTracksEmpty()) {

                    // по сути бесполезная хня: index по текущей uri может найтись, когда играет совсем другой трек, пусть даже из этого же листа

                    boolean contains = false;

                    Uri currentTrackUri = getCurrentTrackUri();
                    String currentTrackUriString = currentTrackUri != null ? currentTrackUri.toString() : null;
                    AssetFileDescriptor currentFd = getCurrentTrackDescriptor();

                    if (!TextUtils.isEmpty(currentTrackUriString)) {
                        contains = indexOf(currentTrackUriString) != NO_POSITION;
                    } else {
                        if (currentFd != null) {
                            contains = indexOf(currentFd) != NO_POSITION;
                        }
                    }

                    if (mPlayerController.isInPlaybackState() || mPlayerController.isPreparing()) {
                        if (!contains) {
                            logger.error("track (uri: " + currentTrackUriString + " / descriptor: " + currentFd + ") not found in playlist!");
                        }
                    }
//                    else {
//                        if (!contains) {
//                            mCurrentTrackIndex = NO_POSITION;
//                        }
//                    }
                }
            }
        }

        @Override
        public void onTargetStateChanged(@NonNull BaseMediaPlayerController.State targetState) {

        }

        @Override
        public void onCompletion(final boolean isTrackLooping) {
            logger.debug("onCompletion(), isTrackLooping=" + isTrackLooping);
            synchronized (mTracks) {

                cancelResetFuture();

                if (hasCurrentTrack()) {
                    mActiveTrackChangedObservable.dispatchCompleted(getCurrentTrack());
                }

                if (!isTrackLooping) {
                    nextTrackByMode();
                }
            }
        }

        @Override
        public void onError(@NonNull MediaError error) {
            logger.error("onError(), error=" + error + ", current track: " + getCurrentTrackIndex() + " / " + getCurrentTrack());
            synchronized (mTracks) {

                cancelResetFuture();

                if (hasCurrentTrack()) {
                    mActiveTrackChangedObservable.dispatchError(error, getCurrentTrack());
                }

                nextTrackByMode();
            }
        }
    }

    public interface OnActiveTrackChangedListener<T extends AbsPlaylistItem> {

        void onPrepare(@NonNull T track, @Nullable T previous);

        void onPlay(@NonNull T current, @Nullable T previous);

        void onReset(@NonNull T previous);

        void onCompleted(@NonNull T current);

        void onError(@NonNull BaseMediaPlayerController.OnErrorListener.MediaError error, @NonNull T current);
    }

    public interface OnTracksSetListener<T extends AbsPlaylistItem> {

        void onTracksSet(@NonNull List<T> newTracks);

        void onTracksNotSet(@NonNull List<T> incorrectTracks);
    }

    public interface OnTrackAddedListener<T extends AbsPlaylistItem> {

        void onTrackAdded(int to, T trackUrl);

        void onTrackAddFailed(int to, T trackUrl);
    }

    public interface OnTrackSetListener<T extends AbsPlaylistItem> {

        void onTrackSet(int in, T track);

        void onTrackSetFailed(int in, T track);
    }

    public interface OnTrackRemovedListener<T extends AbsPlaylistItem> {

        void onTrackRemoved(int from, T track);
    }

    public interface OnTracksClearedListener {

        void onTracksCleared(int oldCount);
    }

    private static class OnActiveTrackChangedObservable<T extends AbsPlaylistItem> extends Observable<OnActiveTrackChangedListener<T>> {

        private void dispatchPrepare(T current, T previous) {
            synchronized (mObservers) {
                for (OnActiveTrackChangedListener<T> l : copyOfObservers()) {
                    l.onPrepare(current, previous);
                }
            }
        }

        private void dispatchPlay(T current, T previous) {
            synchronized (mObservers) {
                for (OnActiveTrackChangedListener<T> l : copyOfObservers()) {
                    l.onPlay(current, previous);
                }
            }
        }

        private void dispatchReset(T previous) {
            synchronized (mObservers) {
                for (OnActiveTrackChangedListener<T> l : copyOfObservers()) {
                    l.onReset(previous);
                }
            }
        }

        private void dispatchCompleted(T current) {
            synchronized (mObservers) {
                for (OnActiveTrackChangedListener<T> l : copyOfObservers()) {
                    l.onCompleted(current);
                }
            }
        }

        private void dispatchError(BaseMediaPlayerController.OnErrorListener.MediaError error, T current) {
            synchronized (mObservers) {
                for (OnActiveTrackChangedListener<T> l : copyOfObservers()) {
                    l.onError(error, current);
                }
            }
        }
    }


    private static class OnTracksSetObservable<T extends AbsPlaylistItem> extends Observable<OnTracksSetListener<T>> {

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

    private static class OnTrackAddedObservable<T extends AbsPlaylistItem> extends Observable<OnTrackAddedListener<T>> {

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

    private static class OnTrackSetObservable<T extends AbsPlaylistItem> extends Observable<OnTrackSetListener<T>> {

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

    private static class OnTrackRemovedObservable<T extends AbsPlaylistItem> extends Observable<OnTrackRemovedListener<T>> {

        private void dispatchRemoved(int from, T track) {
            synchronized (mObservers) {
                for (OnTrackRemovedListener<T> l : copyOfObservers()) {
                    l.onTrackRemoved(from, track);
                }

            }
        }
    }

    private static class OnTracksClearedObservable extends Observable<OnTracksClearedListener> {

        private void dispatchCleared(int oldCount) {
            synchronized (mObservers) {
                for (OnTracksClearedListener l : copyOfObservers()) {
                    l.onTracksCleared(oldCount);
                }

            }
        }
    }

    public enum TracksSwitchMode {

        CONSEQUENTIALLY, RANDOM
    }

}
