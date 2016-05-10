package ru.maxsmr.mediaplayerexample.app;

import android.os.Environment;
import android.support.annotation.NonNull;

import java.io.File;

public final class Paths {

    private Paths() {
    }

    public static final boolean ALLOW_USE_EXTERNAL_CARD = true;

    public static final String PACKAGE_NAME = "ru.maxsmr.mediaplayerexample";

    public static final String APP_DATA_INTERNAL_PATH = Environment.getDataDirectory() /*ctx.getFilesDir()*/ + File.separator + "data"
            + File.separator + PACKAGE_NAME + File.separator + "files";

    public static final String APP_DATA_EXTERNAL_PATH = Environment.getExternalStorageDirectory() /*"/storage/emulated/0/Android"*/ + File.separator + "Android"
            + File.separator + "data" + File.separator + PACKAGE_NAME;


    public static boolean isExternalStorageMounted() {
        return Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED);
    }

    @NonNull
    public static File getDefaultWorkingDir() {
        return ALLOW_USE_EXTERNAL_CARD && Paths.isExternalStorageMounted() ? new File(Paths.APP_DATA_EXTERNAL_PATH) : new File(Paths.APP_DATA_INTERNAL_PATH);  // return ctx.getFilesDir().getAbsolutePath();
    }

    public static final String LOG_DIR_NAME = "log";

    @NonNull
    public static File makeLogDirPath(@NonNull File workingDir) {
        return new File(workingDir, LOG_DIR_NAME);
    }

    @NonNull
    public static File getDefaultLogDirPath() {
        return makeLogDirPath(getDefaultWorkingDir());
    }

    public static final String LOG_FILENAME = "OpenCvDetectorExample.log";
    ;

    @NonNull
    public static File makeLogFilePath(@NonNull File workingDir) {
        return new File(makeLogDirPath(workingDir), LOG_FILENAME);
    }

    @NonNull
    public static File getDefaultLogFilePath() {
        return makeLogFilePath(getDefaultLogDirPath());
    }
}
