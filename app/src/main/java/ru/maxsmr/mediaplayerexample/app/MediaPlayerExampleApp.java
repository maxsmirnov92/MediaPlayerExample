package ru.maxsmr.mediaplayerexample.app;

import android.app.Application;
import org.jetbrains.annotations.NotNull;

import net.maxsmr.commonutils.logger.BaseLogger;
import net.maxsmr.commonutils.logger.LogcatLogger;
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder;
import net.maxsmr.commonutils.logger.holder.ILoggerHolderProvider;
import net.maxsmr.mediaplayercontroller.facades.MediaPlayerFacade;
import net.maxsmr.mediaplayercontroller.facades.PlaylistManagerFacade;
import net.maxsmr.mediaplayercontroller.playlist.item.UriPlaylistItem;

public class MediaPlayerExampleApp extends Application {

    public static final String PLAYER_ALIAS = "main";

    static {
        BaseLoggerHolder.initInstance(() -> new BaseLoggerHolder(true) {
            @Override
            protected BaseLogger createLogger(@NotNull Class<?> clazz) {
                return new LogcatLogger(clazz.getSimpleName());
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MediaPlayerFacade.initInstance(this);
        MediaPlayerFacade.initInstance(this);
        MediaPlayerFacade.getInstance().create(PLAYER_ALIAS);
        PlaylistManagerFacade.getInstance().create(PLAYER_ALIAS, MediaPlayerFacade.getInstance().get(PLAYER_ALIAS), UriPlaylistItem.class);
//        PlaylistManagerFacade.getInstance().get(PLAYER_ALIAS).addAcceptableFileMimeTypeParts("application/ogg");
    }

}
