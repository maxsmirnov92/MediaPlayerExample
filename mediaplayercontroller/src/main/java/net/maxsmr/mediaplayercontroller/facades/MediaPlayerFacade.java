package net.maxsmr.mediaplayercontroller.facades;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.maxsmr.mediaplayercontroller.mpc.nativeplayer.MediaPlayerController;

import java.util.LinkedHashMap;

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

    public static void releaseInstance() {
        if (sInstance != null) {
            for (String alias : sInstance.mCached.keySet()) {
                sInstance.remove(alias);
            }
            sInstance.mCached.clear();
        }
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
        if (mpc == null || mpc.isReleased()) {
            mpc = new MediaPlayerController(mContext);
        }
        mCached.put(alias, mpc);
        return mpc;
    }

    @Nullable
    public MediaPlayerController get(String alias) {
        MediaPlayerController c = mCached.get(alias);
        if (c != null && c.isReleased()) {
            c = null;
        }
        return c;
    }

    @Nullable
    public MediaPlayerController remove(String alias) {
        MediaPlayerController mpc = mCached.remove(alias);
        if (mpc != null && !mpc.isReleased()) {
            mpc.release();
        }
        return mpc;
    }



}
