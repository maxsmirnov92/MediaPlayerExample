package net.maxsmr.mediaplayerexample.app;

import android.app.Application;

import net.maxsmr.commonutils.logger.BaseLogger;
import net.maxsmr.commonutils.logger.LogcatLogger;
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder;
import net.maxsmr.mediaplayercontroller.facades.MediaPlayerFacade;
import net.maxsmr.mediaplayercontroller.facades.PlaylistManagerFacade;
import net.maxsmr.mediaplayercontroller.playlist.item.UriPlaylistItem;

import org.jetbrains.annotations.NotNull;

public class MediaPlayerExampleApp extends Application {

    public static final String PLAYER_ALIAS = "main";

    static {
        BaseLoggerHolder.initInstance(() -> new BaseLoggerHolder(true) {
            @Override
            protected BaseLogger createLogger(@NotNull String className) {
                return new LogcatLogger(className);
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MediaPlayerFacade.initInstance(this);
        MediaPlayerFacade.initInstance(this);
        MediaPlayerFacade.getInstance().getOrCreate(PLAYER_ALIAS);
        PlaylistManagerFacade.getInstance().create(PLAYER_ALIAS, MediaPlayerFacade.getInstance().get(PLAYER_ALIAS), UriPlaylistItem.class);
//        PlaylistManagerFacade.getInstance().get(PLAYER_ALIAS).addAcceptableFileMimeTypeParts("application/ogg");
    }

}
