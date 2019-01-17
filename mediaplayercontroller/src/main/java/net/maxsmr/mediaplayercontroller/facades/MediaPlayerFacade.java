package net.maxsmr.mediaplayercontroller.facades;

import android.content.Context;
import android.os.Looper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.maxsmr.mediaplayercontroller.mpc.nativeplayer.MediaPlayerController;

import java.util.LinkedHashMap;

public final class MediaPlayerFacade {

    private static MediaPlayerFacade sInstance;

    public static MediaPlayerFacade initInstance(@NotNull Context context) {
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

    public MediaPlayerFacade(@NotNull Context context) {
        mContext = context;
    }

    @NotNull
    private final Context mContext;

    @NotNull
    private LinkedHashMap<String, MediaPlayerController> mCached = new LinkedHashMap<>();

    @NotNull
    public MediaPlayerController getOrCreate(String alias) {
        MediaPlayerController mpc = get(alias);
        if (mpc == null || mpc.isReleased()) {
            mpc = new MediaPlayerController(mContext, Looper.getMainLooper());
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
