package ru.altarix.commonutils.graphic;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;

import ru.altarix.commonutils.data.FileHelper;

public final class ViewScreenshotMaker {

    private final static Logger logger = LoggerFactory.getLogger(ViewScreenshotMaker.class);

    private ViewScreenshotMaker() {
    }

    @Nullable
    public static File makeScreenshot(String folderName, String fileName, @NonNull Bitmap.CompressFormat format, @NonNull Window window) {

        final File destFile = FileHelper.createNewFile(fileName, folderName);

        if (destFile == null) {
            logger.error("can't create file: " + destFile);
            return null;
        }

        try {
            // create bitmap screen capture
            View v1 = window.getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
            v1.setDrawingCacheEnabled(false);

            FileOutputStream outputStream = new FileOutputStream(destFile);
            int quality = 100;
            bitmap.compress(format, quality, outputStream);
            outputStream.flush();
            outputStream.close();

            return destFile;

        } catch (Throwable e) {
            logger.error("an Exception occurred", e);
            e.printStackTrace();
        }

        return null;
    }
}
