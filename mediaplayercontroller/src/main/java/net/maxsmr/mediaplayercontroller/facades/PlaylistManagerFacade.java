package net.maxsmr.mediaplayercontroller.facades;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import android.util.Pair;

import net.maxsmr.commonutils.data.CompareUtils;
import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;
import net.maxsmr.mediaplayercontroller.playlist.PlaylistManager;
import net.maxsmr.mediaplayercontroller.playlist.item.AbsPlaylistItem;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlaylistManagerFacade {

    private static PlaylistManagerFacade sInstance;

    public static PlaylistManagerFacade getInstance() {
        if (sInstance == null) {
            synchronized (PlaylistManagerFacade.class) {
                sInstance = new PlaylistManagerFacade();
            }
        }
        return sInstance;
    }

    public static void releaseInstance() {
        if (sInstance != null) {
            for (String alias : sInstance.mCached.keySet()) {
                sInstance.remove(alias);
            }
            sInstance.mCached.clear();
        }
    }

    @NotNull
    private LinkedHashMap<String, PlaylistManager<?, ?>> mCached = new LinkedHashMap<>();

    @NotNull
    public <C extends BaseMediaPlayerController, I extends AbsPlaylistItem> PlaylistManager<C, I> create(String alias, C mpc, Class<I> itemClass) {
        PlaylistManager<C, I> manager = get(alias);
        if (manager == null) {
            manager = new PlaylistManager<>(mpc, itemClass);
            manager.clearAcceptableFileMimeTypePrefixes();
        }
        mCached.put(alias, manager);
        return manager;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <C extends BaseMediaPlayerController, I extends AbsPlaylistItem> PlaylistManager<C, I> get(String alias) throws ClassCastException {
        PlaylistManager<?, ?> manager = mCached.get(alias);
        if (manager!= null && manager.isReleased()) {
            manager = null;
        }
        return (PlaylistManager<C, I>) manager;
    }

    @Nullable
    public PlaylistManager<?, ?> remove(String alias) {
        PlaylistManager<?, ?> manager = mCached.remove(alias);
        if (manager != null && !manager.isReleased()) {
            manager.release();
        }
        return manager;
    }

    /**
     *
     * @return null if no managers in "playback" state
     */
    @Nullable
    private Pair<String, ? extends PlaylistManager<?, ?>> findFirstManagerInPlaybackState() {
        for (Map.Entry<String, PlaylistManager<?, ?>> entry : mCached.entrySet()) {
            if (entry.getValue().isInPlaybackState()) {
                return new Pair<>(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    @Nullable
    private Pair<String, ? extends PlaylistManager<?, ?>> findManagerInPlaybackStateByAlias(String alias) {
        for (Map.Entry<String, PlaylistManager<?, ?>> entry : mCached.entrySet()) {
            if (CompareUtils.stringsEqual(entry.getKey(), alias, true) && entry.getValue().isInPlaybackState()) {
                return new Pair<>(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    /**
     *
     * @return null if no managers in specified state
     */
    @Nullable
    private Pair<String, ? extends PlaylistManager<?, ?>> findFirstManagerInState(@NotNull BaseMediaPlayerController.State state) {
        for (Map.Entry<String, PlaylistManager<?, ?>> entry : mCached.entrySet()) {
            if (entry.getValue().getPlayerController().getCurrentState() == state) {
                return new Pair<>(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    @Nullable
    private Pair<String, ? extends PlaylistManager<?, ?>> findFirstManagerInStateByAlias(@NotNull BaseMediaPlayerController.State state, String alias) {
        for (Map.Entry<String, PlaylistManager<?, ?>> entry : mCached.entrySet()) {
            if (CompareUtils.stringsEqual(entry.getKey(), alias, true) && entry.getValue().getPlayerController().getCurrentState() == state) {
                return new Pair<>(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    private void clearAllTracks() {
        for (PlaylistManager<?, ?> manager : mCached.values()) {
            manager.clearTracks();
        }
    }

    public void clearAllTracksByAlias(String... aliases) {
        if (aliases != null) {
            for (Map.Entry<String, PlaylistManager<?, ?>> entry : mCached.entrySet()) {
                 if (Arrays.binarySearch(aliases, entry.getKey()) > -1) {
                    entry.getValue().clearTracks();
                }
            }
        }
    }
}
