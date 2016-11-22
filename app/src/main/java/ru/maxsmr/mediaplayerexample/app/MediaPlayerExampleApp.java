package ru.maxsmr.mediaplayerexample.app;

import android.app.Application;

import net.maxsmr.mediaplayercontroller.facades.MediaPlayerFacade;
import net.maxsmr.mediaplayercontroller.facades.PlaylistManagerFacade;
import net.maxsmr.mediaplayercontroller.playlist.item.UriPlaylistItem;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.maxsmr.mediaplayerexample.app.logger.ConfigureLog4J;
import ru.maxsmr.mediaplayerexample.player.MediaPlayerFactory;

public class MediaPlayerExampleApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MediaPlayerExampleApp.class);

    public static final String PLAYER_ALIAS = "main";

    public final static boolean LOG_USE_FILE = true;
    public final static long LOG_MAX_FILE_SIZE = 5 * 1024 * 1024;
    public final static int LOG_MAX_BACKUP_SIZE = 2;
    public final static int LOG_LEVEL = Level.TRACE_INT;

    public void applyLog4JConf() {
        ConfigureLog4J.getInstance().configure(Level.toLevel(LOG_LEVEL), LOG_USE_FILE, LOG_MAX_FILE_SIZE, LOG_MAX_BACKUP_SIZE);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logger.debug("onCreate()");
        applyLog4JConf();
        MediaPlayerFactory.initInstance(this);

        MediaPlayerFacade.getInstance().create(PLAYER_ALIAS);
        PlaylistManagerFacade.getInstance().create(PLAYER_ALIAS, MediaPlayerFacade.getInstance().get(PLAYER_ALIAS), UriPlaylistItem.class);
//        PlaylistManagerFacade.getInstance().get(PLAYER_ALIAS).addAcceptableFileMimeTypeParts("application/ogg");
    }

}
