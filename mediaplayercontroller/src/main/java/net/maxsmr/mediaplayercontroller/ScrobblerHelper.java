package net.maxsmr.mediaplayercontroller;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import net.maxsmr.commonutils.android.media.MetadataRetriever;
import net.maxsmr.commonutils.logger.BaseLogger;
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder;
import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;

public final class ScrobblerHelper {

    private static final BaseLogger logger = BaseLoggerHolder.getInstance().getLogger(ScrobblerHelper.class);

    public static ScrobblerHelper attach(@NonNull Context context, @NonNull BaseMediaPlayerController<?> mpc) {
        logger.d("attach(), mpc=" + mpc);
        return new ScrobblerHelper(context, mpc);
    }

    private static boolean isScrobbleState(@NonNull BaseMediaPlayerController.State state) {
        return state == BaseMediaPlayerController.State.IDLE || state == BaseMediaPlayerController.State.PAUSED || state == BaseMediaPlayerController.State.PLAYING;
    }

    private ScrobblerHelper(@NonNull Context context, @NonNull BaseMediaPlayerController<?> mpc) {
        mContext = context;
        mMpc = mpc;
        mMpc.getStateChangedObservable().registerObserver(mCallbacks);
        mMpc.getCompletionObservable().registerObserver(mCallbacks);
        if (mMpc.getCurrentState() != BaseMediaPlayerController.State.RELEASED) {
            notifyScrobblerIdleState();
        } else {
            detach();
        }
    }

    @NonNull
    private final Context mContext;

    private BaseMediaPlayerController<?> mMpc;

    @NonNull
    private final MediaPlayerCallbacks mCallbacks = new MediaPlayerCallbacks();

    private boolean mScrobblingEnabled = true;

    @NonNull
    private BaseMediaPlayerController.State mLastState = BaseMediaPlayerController.State.IDLE;

    private MetadataRetriever.MediaMetadata mLastMetadata;

    private static boolean checkMetadata(MetadataRetriever.MediaMetadata metadata) {
        return metadata != null && !TextUtils.isEmpty(metadata.artist) && !TextUtils.isEmpty(metadata.title) && metadata.durationMs > 0;
    }

    private synchronized void notifyScrobblerStateChanged(@NonNull BaseMediaPlayerController.State newState, @NonNull BaseMediaPlayerController.State oldState, @Nullable MetadataRetriever.MediaMetadata metadata, long position) {
        logger.d("notifyScrobblerStateChanged(), newState=" + newState + ", oldState=" + oldState + ", metadata=" + metadata + ", position=" + position);

        if (mMpc == null) {
            throw new IllegalStateException(BaseMediaPlayerController.class.getSimpleName() + " is not attached");
        }

        if (newState == BaseMediaPlayerController.State.RELEASED || oldState == BaseMediaPlayerController.State.RELEASED) {
            throw new IllegalStateException(BaseMediaPlayerController.class.getSimpleName() + " was released");
        }

        if (isScrobbleState(newState)) {

            if (mScrobblingEnabled) {

                if (newState != mLastState) {

                    if (newState != BaseMediaPlayerController.State.IDLE) {

                        if (checkMetadata(metadata)) {

                            Intent scrobbleIntent = new Intent();

                            switch (newState) {

                                case PAUSED:
                                    scrobbleIntent.setAction(BroadcastIntentActions.ACTION_LASTFMAPI_PAUSERESUME);
                                    break;

                                case PLAYING:
                                    if (oldState == BaseMediaPlayerController.State.PAUSED) {
                                        scrobbleIntent.setAction(BroadcastIntentActions.ACTION_LASTFMAPI_PAUSERESUME);
                                        if (position < 0 || position > metadata.durationMs) {
                                            throw new IllegalArgumentException("incorrect track position: " + position);
                                        }
                                        scrobbleIntent.putExtra(BroadcastIntentExtras.EXTRA_POSITION, position);
                                    } else {
                                        scrobbleIntent.setAction(BroadcastIntentActions.ACTION_LASTFMAPI_METACHANGED);
                                        scrobbleIntent.putExtra(BroadcastIntentExtras.EXTRA_TRACK, metadata.title);
                                        scrobbleIntent.putExtra(BroadcastIntentExtras.EXTRA_ARTIST, metadata.artist);
                                        scrobbleIntent.putExtra(BroadcastIntentExtras.EXTRA_ALBUM, metadata.album);
                                        scrobbleIntent.putExtra(BroadcastIntentExtras.EXTRA_DURATION, metadata.durationMs);
                                    }
                                    break;
                            }

                            mContext.sendBroadcast(scrobbleIntent);
                            mLastMetadata = metadata;
                        }

                    } else {
                        mContext.sendBroadcast(new Intent(BroadcastIntentActions.ACTION_LASTFMAPI_STOP));
                        mLastMetadata = null;
                    }

                    mLastState = newState;
                }
            }
        }
    }

    private void notifyScrobblerNonIdleStateChanged() {

        if (mMpc == null) {
            throw new IllegalStateException(BaseMediaPlayerController.class.getSimpleName() + " is not attached");
        }

        BaseMediaPlayerController.State currentState = mMpc.getCurrentState();
        if (currentState != BaseMediaPlayerController.State.IDLE) {
            notifyScrobblerStateChanged(currentState, mLastState, mMpc.getCurrentTrackMetatada(), mMpc.getCurrentPosition());
        }
    }

    private void notifyScrobblerIdleState() {
        notifyScrobblerStateChanged(BaseMediaPlayerController.State.IDLE, mLastState, null, 0);
    }

    public void enableScrobbling() {

        if (mMpc == null) {
            throw new IllegalStateException(BaseMediaPlayerController.class.getSimpleName() + " is not attached");
        }

        mScrobblingEnabled = true;
        notifyScrobblerNonIdleStateChanged();
    }

    public void disableScrobbling() {

        if (mMpc == null) {
            throw new IllegalStateException(BaseMediaPlayerController.class.getSimpleName() + " is not attached");
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
        logger.d("detach()");
        notifyScrobblerIdleState();
        disableScrobbling();
        mMpc.getStateChangedObservable().unregisterObserver(mCallbacks);
        mMpc.getCompletionObservable().unregisterObserver(mCallbacks);
        mMpc = null;
    }

    protected interface BroadcastIntentActions {

        String ACTION_LASTFMAPI_METACHANGED = "fm.last.android.metachanged";
        String ACTION_LASTFMAPI_PAUSERESUME = "fm.last.android.playbackpaused";
        String ACTION_LASTFMAPI_STOP = "fm.last.android.playbackcomplete";
    }

    protected interface BroadcastIntentExtras {

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

    private class MediaPlayerCallbacks implements BaseMediaPlayerController.OnStateChangedListener, BaseMediaPlayerController.OnCompletionListener {

        @Override
        public void onCompletion(boolean isLooping) {
            if (isLooping) {
                notifyScrobblerStateChanged(BaseMediaPlayerController.State.IDLE, mLastState, null, 0);
                notifyScrobblerStateChanged(BaseMediaPlayerController.State.PLAYING, mLastState, mMpc.getCurrentTrackMetatada(), mMpc.getCurrentPosition());
            }
        }

        @Override
        public void onBeforeOpenDataSource() {

        }

        @Override
        public void onCurrentStateChanged(@NonNull BaseMediaPlayerController.State currentState, @NonNull BaseMediaPlayerController.State previousState) {
            if (currentState == BaseMediaPlayerController.State.RELEASED) {
                detach();
            } else {
                notifyScrobblerStateChanged(currentState, previousState, mMpc.getCurrentTrackMetatada(), mMpc.getCurrentPosition());
            }
        }

        @Override
        public void onTargetStateChanged(@NonNull BaseMediaPlayerController.State targetState) {

        }
    }
}
