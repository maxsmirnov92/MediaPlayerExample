package net.maxsmr.mediaplayercontroller.facades;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.LinkedHashMap;

import net.maxsmr.mediaplayercontroller.mpc.MediaPlayerController;

public final class MediaPlayerFacade {

    private static MediaPlayerFacade sInstance;

    public static MediaPlayerFacade initInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (MediaPlayerFacade.class) {
                sInstance = new MediaPlayerFacade(context);
            }
        }
        return sInstance;
    }

    public static MediaPlayerFacade getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("initInstance() was not called");
        }
        return sInstance;
    }

    public MediaPlayerFacade(@NonNull Context context) {
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
        MediaPlayerController mpc = mCached.remove(alias);
        if (mpc != null) {
            mpc.release();
        }
        return mpc;
    }

    public static void releaseInstance() {
        if (sInstance != null) {
            for (MediaPlayerController mpc : sInstance.mCached.values()) {
                mpc.release();
            }
            sInstance.mCached.clear();
        }
    }

}