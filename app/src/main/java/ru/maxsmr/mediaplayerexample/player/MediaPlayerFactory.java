package ru.maxsmr.mediaplayerexample.player;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.LinkedHashMap;

import ru.maxsmr.mediaplayercontroller.mpc.MediaPlayerController;

public class MediaPlayerFactory {

    private static MediaPlayerFactory sInstance;

    public static MediaPlayerFactory initInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (MediaPlayerFactory.class) {
                sInstance = new MediaPlayerFactory(context);
            }
        }
        return sInstance;
    }

    public static MediaPlayerFactory getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("initInstance() was not called");
        }
        return sInstance;
    }

    public MediaPlayerFactory(@NonNull Context context) {
        mContext = context;
    }

    @NonNull
    private final Context mContext;

    @NonNull
    private LinkedHashMap<String, MediaPlayerController> mCached = new LinkedHashMap<>();

    @NonNull
    public MediaPlayerController create(String alias) {
        MediaPlayerController mpc = get(alias);
        if (mpc == null) {
            mpc = new MediaPlayerController(mContext);
        }
        mCached.put(alias, mpc);
        return mpc;
    }

    @Nullable
    public MediaPlayerController get(String alias) {
        return mCached.get(alias);
    }

    @Nullable
    public MediaPlayerController remove(String alias) {
        return mCached.remove(alias);
    }

}
