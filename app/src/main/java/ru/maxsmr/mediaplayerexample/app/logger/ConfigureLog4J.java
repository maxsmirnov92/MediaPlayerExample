package ru.maxsmr.mediaplayerexample.app.logger;

import org.apache.log4j.Level;

import de.mindpipe.android.logging.log4j.LogConfigurator;
import ru.maxsmr.mediaplayerexample.app.Paths;

public class ConfigureLog4J {

    private static ConfigureLog4J mInstance = null;

    public static final long MIN_FILE_SIZE = 1024 * 1024;

    private ConfigureLog4J() {
    }

    public static void initInstance() {
        if (mInstance == null) {
            synchronized (ConfigureLog4J.class) {
                mInstance = new ConfigureLog4J();
            }
        }
    }

    public static ConfigureLog4J getInstance() {
        initInstance();
        return mInstance;
    }

    /**
     * @param maxFileSize   log file size in bytes
     * @param maxBackupSize number of log backups
     * @param level         minimum logging level
     */
    public void configure(Level level, boolean useFile, long maxFileSize, int maxBackupSize) {

        if (level == null)
            throw new NullPointerException("level is null");

        if (useFile && maxFileSize < MIN_FILE_SIZE)
            throw new IllegalArgumentException("incorrect maxFileSize: " + maxFileSize);

        if (useFile && maxBackupSize < 0)
            throw new IllegalArgumentException("incorrect maxBackupSize: " + maxBackupSize);

        LogConfigurator logConfigurator = new LogConfigurator();

        if (useFile) {
            logConfigurator.setFileName(Paths.getDefaultLogFilePath().getAbsolutePath());
            logConfigurator.setMaxFileSize(maxFileSize);
            logConfigurator.setMaxBackupSize(maxBackupSize);
            logConfigurator.setFilePattern("%d{dd/MM/yyyy HH:mm:ss,SSS} %5p %c:%L - %m%n");
        } else {
            logConfigurator.setUseFileAppender(false);
        }

        logConfigurator.setRootLevel(level);

        logConfigurator.configure();
    }

}
