package net.maxsmr.mediaplayercontroller;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import net.maxsmr.commonutils.android.media.MetadataRetriever;
import net.maxsmr.mediaplayercontroller.mpc.MediaPlayerController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrobblerHelper {

    private static final Logger logger = LoggerFactory.getLogger(ScrobblerHelper.class);

    public static ScrobblerHelper attach(@NonNull Context context, @NonNull MediaPlayerController mpc) {
        return new ScrobblerHelper(context, mpc);
    }

    private static boolean isScrobbleState(@NonNull MediaPlayerController.State state) {
        return state == MediaPlayerController.State.IDLE || state == MediaPlayerController.State.PAUSED || state == MediaPlayerController.State.PLAYING;
    }

    private ScrobblerHelper(@NonNull Context context, @NonNull MediaPlayerController mpc) {
        mContext = context;
        mMpc = mpc;
        mMpc.getStateChangedObservable().registerObserver(mCallbacks);
        mMpc.getCompletionObservable().registerObserver(mCallbacks);
        notifyScrobblerNonIdleStateChanged();
    }

    @NonNull
    private final Context mContext;

    private MediaPlayerController mMpc;

    @NonNull
    private final MediaPlayerCallbacks mCallbacks = new MediaPlayerCallbacks();

    private boolean mScrobblingEnabled = true;

    @NonNull
    private MediaPlayerController.State mLastState = MediaPlayerController.State.IDLE;

    private MetadataRetriever.MediaMetadata mLastMetadata;

    private static boolean checkMetadata(MetadataRetriever.MediaMetadata metadata) {
        return metadata != null && !TextUtils.isEmpty(metadata.artist) && !TextUtils.isEmpty(metadata.title) && metadata.durationMs > 0;
    }

    private synchronized void notifyScrobblerStateChanged(@NonNull MediaPlayerController.State newState, @NonNull MediaPlayerController.State oldState, @Nullable MetadataRetriever.MediaMetadata metadata, long position) {
        logger.debug("notifyScrobblerStateChanged(), newState=" + newState + ", oldState=" + oldState + ", metadata=" + metadata + ", position=" + position);

        if (mMpc == null) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " is not attached");
        }

        if (newState == MediaPlayerController.State.RELEASED || oldState == MediaPlayerController.State.RELEASED) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " was released");
        }

        if (isScrobbleState(newState)) {

            if (mScrobblingEnabled) {

                if (newState != mLastState) {

                    if (newState != MediaPlayerController.State.IDLE) {

                        if (checkMetadata(metadata)) {

                            Intent scrobbleIntent = new Intent();

                            switch (newState) {

                                case PAUSED:
                                    scrobbleIntent.setAction(BroadcastIntentStrings.ACTION_LASTFMAPI_PAUSERESUME);
                                    break;

                                case PLAYING:
                                    if (oldState == MediaPlayerController.State.PAUSED) {
                                        scrobbleIntent.setAction(BroadcastIntentStrings.ACTION_LASTFMAPI_PAUSERESUME);
                                        if (position < 0 || position > metadata.durationMs) {
                                            throw new IllegalArgumentException("incorrect track position: " + position);
                                        }
                                        scrobbleIntent.putExtra(BroadcastIntentStrings.EXTRA_POSITION, position);
                                    } else {
                                        scrobbleIntent.setAction(BroadcastIntentStrings.ACTION_LASTFMAPI_METACHANGED);
                                        scrobbleIntent.putExtra(BroadcastIntentStrings.EXTRA_TRACK, metadata.title);
                                        scrobbleIntent.putExtra(BroadcastIntentStrings.EXTRA_ARTIST, metadata.artist);
                                        scrobbleIntent.putExtra(BroadcastIntentStrings.EXTRA_ALBUM, metadata.album);
                                        scrobbleIntent.putExtra(BroadcastIntentStrings.EXTRA_DURATION, metadata.durationMs);
                                    }
                                    break;
                            }

                            mContext.sendBroadcast(scrobbleIntent);
                            mLastMetadata = metadata;
                        }

                    } else {
                        mContext.sendBroadcast(new Intent(BroadcastIntentStrings.ACTION_LASTFMAPI_STOP));
                        mLastMetadata = null;
                    }

                    mLastState = newState;
                }
            }
        }
    }

    private void notifyScrobblerNonIdleStateChanged() {

        if (mMpc == null) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " is not attached");
        }

        MediaPlayerController.State currentState = mMpc.getCurrentState();
        if (currentState != MediaPlayerController.State.IDLE) {
            notifyScrobblerStateChanged(currentState, mLastState, mMpc.getCurrentTrackMetatada(), mMpc.getCurrentPosition());
        }
    }

    private void notifyScrobblerIdleState() {
        notifyScrobblerStateChanged(MediaPlayerController.State.IDLE, mLastState, null, 0);
    }

    public void enableScrobbling() {

        if (mMpc == null) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " is not attached");
        }

        mScrobblingEnabled = true;
        notifyScrobblerNonIdleStateChanged();
    }

    public void disableScrobbling() {

        if (mMpc == null) {
            throw new IllegalStateException(MediaPlayerController.class.getSimpleName() + " is not attached");
        }

        if (mScrobblingEnabled) {
            notifyScrobblerIdleState();
        }
        mScrobblingEnabled = false;
    }

    public void toggleScrobbling() {
        if (mScrobblingEnabled) {
            disableScrobbling();
        } else {
            enableScrobbling();
        }
    }

    /** must be called when done with scrobbling */
    public void detach() {
        notifyScrobblerIdleState();
        disableScrobbling();
        mMpc.getStateChangedObservable().unregisterObserver(mCallbacks);
        mMpc.getCompletionObservable().unregisterObserver(mCallbacks);
        mMpc = null;
    }

    private interface BroadcastIntentStrings {
        String ACTION_LASTFMAPI_METACHANGED = "fm.last.android.metachanged";
        String ACTION_LASTFMAPI_PAUSERESUME = "fm.last.android.playbackpaused";
        String ACTION_LASTFMAPI_STOP = "fm.last.android.playbackcomplete";

        String EXTRA_TRACK = "track";
        String EXTRA_ARTIST = "artist";
        String EXTRA_ALBUM = "album";

        /**
         * millis
         */
        String EXTRA_DURATION = "duration";

        /**
         * for pause/resume, millis
         */
        String EXTRA_POSITION = "position";
    }

    private class MediaPlayerCallbacks implements MediaPlayerController.OnStateChangedListener, MediaPlayerController.OnCompletionListener {

        @Override
        public void onCompletion(boolean isLooping) {
            if (isLooping) {
                notifyScrobblerStateChanged(MediaPlayerController.State.IDLE, mLastState, null, 0);
                notifyScrobblerStateChanged(MediaPlayerController.State.PLAYING, mLastState, mMpc.getCurrentTrackMetatada(), mMpc.getCurrentPosition());
            }
        }

        @Override
        public void onCurrentStateChanged(@NonNull MediaPlayerController.State currentState, @NonNull MediaPlayerController.State previousState) {
            if (currentState == MediaPlayerController.State.RELEASED) {
                detach();
            } else {
                notifyScrobblerStateChanged(currentState, previousState, mMpc.getCurrentTrackMetatada(), mMpc.getCurrentPosition());
            }
        }

        @Override
        public void onTargetStateChanged(@NonNull MediaPlayerController.State targetState) {

        }
    }
}
