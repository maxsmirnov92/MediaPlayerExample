package ru.maxsmr.mediaplayerexample.app;

import android.app.Application;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.maxsmr.mediaplayerexample.app.logger.ConfigureLog4J;

public class MediaPlayerExampleApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MediaPlayerExampleApp.class);

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
    }

}
