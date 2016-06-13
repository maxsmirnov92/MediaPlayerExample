package ru.maxsmr.mediaplayercontroller.facades;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import ru.maxsmr.commonutils.data.CompareUtils;
import ru.maxsmr.mediaplayercontroller.mpc.MediaPlayerController;
import ru.maxsmr.mediaplayercontroller.playlist.PlaylistItem;
import ru.maxsmr.mediaplayercontroller.playlist.PlaylistManager;

public class PlaylistManagerFacade {

    private static PlaylistManagerFacade sInstance;

    public static PlaylistManagerFacade getInstance() {
        if (sInstance == null) {
            synchronized (PlaylistManagerFacade.class) {
                sInstance = new PlaylistManagerFacade();
            }
        }
        return sInstance;
    }

    @NonNull
    private LinkedHashMap<String, PlaylistManager<?>> mCached = new LinkedHashMap<>();

    @NonNull
    public <T extends PlaylistItem> PlaylistManager<T> create(String alias, MediaPlayerController mpc, Class<T> clazz) {
        PlaylistManager<T> manager = get(alias);
        if (manager == null) {
            manager = new PlaylistManager<>(mpc, clazz);
            manager.ignoreAcceptableFileMimeTypeParts();
        }
        mCached.put(alias, manager);
        return manager;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends PlaylistItem> PlaylistManager<T> get(String alias) throws ClassCastException {
        return (PlaylistManager<T>) mCached.get(alias);
    }

    @Nullable
    public PlaylistManager<?> remove(String alias) {
        PlaylistManager<?> manager = mCached.remove(alias);
        if (manager != null) {
            manager.release();
        }
        return manager;
    }

    /**
     *
     * @return null if no managers in "playback" state
     */
    @Nullable
    private Pair<String, ? extends PlaylistManager<?>> findFirstManagerInPlaybackState() {
        for (Map.Entry<String, PlaylistManager<?>> entry : mCached.entrySet()) {
            if (entry.getValue().isInPlaybackState()) {
                return new Pair<>(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    @Nullable
    private Pair<String, ? extends PlaylistManager<?>> findManagerInPlaybackStateByAlias(String alias) {
        for (Map.Entry<String, PlaylistManager<?>> entry : mCached.entrySet()) {
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
    private Pair<String, ? extends PlaylistManager<?>> findFirstManagerInState(@NonNull MediaPlayerController.State state) {
        for (Map.Entry<String, PlaylistManager<?>> entry : mCached.entrySet()) {
            if (entry.getValue().getPlayerController().getCurrentState() == state) {
                return new Pair<>(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    @Nullable
    private Pair<String, ? extends PlaylistManager<?>> findFirstManagerInStateByAlias(@NonNull MediaPlayerController.State state, String alias) {
        for (Map.Entry<String, PlaylistManager<?>> entry : mCached.entrySet()) {
            if (CompareUtils.stringsEqual(entry.getKey(), alias, true) && entry.getValue().getPlayerController().getCurrentState() == state) {
                return new Pair<>(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    private void clearAllTracks() {
        for (PlaylistManager<?> manager : mCached.values()) {
            manager.clearTracks();
        }
    }

    public void clearAllTracksByAlias(String... aliases) {
        if (aliases != null) {
            for (Map.Entry<String, PlaylistManager<?>> entry : mCached.entrySet()) {
                 if (Arrays.binarySearch(aliases, entry.getKey()) > -1) {
                    entry.getValue().clearTracks();
                }
            }
        }
    }

    public void release() {
        for (PlaylistManager<?> manager : mCached.values()) {
            manager.release();
        }
        mCached.clear();
    }
}
