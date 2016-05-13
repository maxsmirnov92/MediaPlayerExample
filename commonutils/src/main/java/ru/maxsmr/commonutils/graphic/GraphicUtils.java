package ru.maxsmr.commonutils.graphic;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.TypedValue;
import android.view.WindowManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ru.maxsmr.commonutils.data.FileHelper;

public class GraphicUtils {

    private static final Logger logger = LoggerFactory.getLogger(GraphicUtils.class);

    public static String getFileExtByCompressFormat(Bitmap.CompressFormat compressFormat) {

        if (compressFormat == null) {
            logger.error("compressFormat is null");
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && compressFormat == Bitmap.CompressFormat.WEBP) {
            return "webp";
        }

        switch (compressFormat) {
            case PNG:
                return "png";
            case JPEG:
                return "jpg";
            default:
                return null;
        }
    }

    public static long getVideoDuration(File videoFile) {

        if (!(FileHelper.isFileCorrect(videoFile) && FileHelper.isVideo(FileHelper.getFileExtension(videoFile.getName())))) {
            logger.error("incorrect video file: " + videoFile);
            return 0;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        // AssetFileDescriptor afd = null;

        try {
            // afd = ctx.getAssets().openFd(videoFile.getAbsolutePath());
            // retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            retriever.setDataSource(videoFile.getAbsolutePath());

            try {
                return Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

            } catch (NumberFormatException e) {
                logger.error("a NumberFormatException occurred during parseInt(): " + e.getMessage());
                return 0;
            }

        } catch (IllegalArgumentException e) {
            logger.error("an IllegalArgumentException occurred: " + e.getMessage());
        } catch (RuntimeException e) {
            logger.error("a RuntimeException occurred: " + e.getMessage());
        } finally {

            try {
                retriever.release();
                retriever = null;
            } catch (RuntimeException e) {
                logger.debug("a RuntimeException occurred during release(): " + e.getMessage());
            }

        }

        logger.error("can't retrieve duration of video file " + videoFile + " (size: " + videoFile.length() / 1024 + " kB)");
        return 0;
    }

    public static Bitmap getVideoFrameAtPosition(File videoFile, long positionMs) {
        // logger.debug("getVideoFrameAtPosition(), positionMs=" + positionMs);

        if (!(FileHelper.isFileCorrect(videoFile) && FileHelper.isVideo(FileHelper.getFileExtension(videoFile.getName())))) {
            logger.error("incorrect video file: " + videoFile);
            return null;
        }

        if (positionMs <= 0 || positionMs > getVideoDuration(videoFile)) {
            logger.error("incorrect position: " + positionMs);
            return null;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        // AssetFileDescriptor afd = null;

        try {
            // afd = ctx.getAssets().openFd(videoFile.getAbsolutePath());
            // retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

            retriever.setDataSource(videoFile.getAbsolutePath());

            return retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

        } catch (IllegalArgumentException e) {
            logger.error("an IllegalArgumentException occurred: " + e.getMessage());
        } catch (RuntimeException e) {
            logger.error("a RuntimeException occurred: " + e.getMessage());
            // } catch (IOException e) {
            // logger.error("an IOException occurred: " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException e) {
                logger.error("a RuntimeException occurred during release(): " + e.getMessage());
            }
        }

        logger.error("can't retrieve frame from video " + videoFile + " at " + positionMs + " ms");
        return null;
    }

    public static List<Pair<Long, Bitmap>> getVideoFrames(File videoFile, int framesCount) {
        // logger.debug("getVideoFrames(), videoFile=" + videoFile + ", framesCount=" + framesCount);

        if (framesCount <= 0) {
            logger.error("incorrect framesCount: " + framesCount);
            return null;
        }

        final long duration = getVideoDuration(videoFile);
        // logger.debug("video duration: " + duration + " ms");

        if (duration == 0) {
            logger.error("duration of video file " + videoFile + " is 0");
            return null;
        }

        final long interval = duration / framesCount;
        // logger.debug("interval between frames: " + interval + " ms");
        long lastPosition = 1;

        ArrayList<Pair<Long, Bitmap>> videoFrames = new ArrayList<>(framesCount);

        while (lastPosition <= duration) { // (duration - interval)

            // logger.debug("getting frame at position: " + lastPosition + " ms");
            final Pair<Long, Bitmap> frame = new Pair<>(lastPosition, getVideoFrameAtPosition(videoFile, lastPosition));
            videoFrames.add(frame);

            lastPosition += interval;
            // logger.debug("next position: " + lastPosition + " ms");
        }

        return videoFrames;
    }

    public static Bitmap writeTextOnBitmap(Bitmap bitmap, String text, int textColor) {
        return writeTextOnBitmap(bitmap, text, textColor, 0, null);
    }

    public static Bitmap writeTextOnBitmap(Bitmap bitmap, String text, @ColorInt int textColor, int fontSize, Point textPos) {

        if (!isBitmapCorrect(bitmap)) {
            logger.error("incorrect bitmap");
            return bitmap;
        }

        if (!bitmap.isMutable()) {
            logger.error("bitmap is immutable, cannot pass to canvas");
            return bitmap;
        }

        if (TextUtils.isEmpty(text)) {
            logger.error("text is null or empty");
            return bitmap;
        }

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(textColor);
        paint.setTypeface(Typeface.create("", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        Canvas canvas = new Canvas(bitmap);

        paint.setTextSize(fixFontSize(fontSize, text, paint, bitmap));

        final int xPos;
        final int yPos;

        if (!(textPos != null && textPos.x >= 0 && textPos.y >= 0)) {
            xPos = (int) (canvas.getWidth() * 0.05);
            yPos = canvas.getHeight() - (int) (canvas.getHeight() * 0.01);
        } else {
            xPos = textPos.x;
            yPos = textPos.y;
        }

        canvas.drawText(text, xPos, yPos, paint);
        return bitmap;
    }

    public static int fixFontSize(int fontSize, String text, Paint paint, Bitmap bitmap) {

        if (!isBitmapCorrect(bitmap)) {
            logger.error("incorrect bitmap");
            return fontSize;
        }

        if (paint == null) {
            logger.error("paint is null");
            return fontSize;
        }

        if (TextUtils.isEmpty(text)) {
            logger.error("text is empty");
            return fontSize;
        }

        Rect textRect = new Rect();
        paint.getTextBounds(text, 0, text.length(), textRect);

        if (textRect.width() >= (new Canvas(bitmap).getWidth() - 4)) {
            return fontSize <= 0 ? (int) (bitmap.getWidth() * 0.01) / 2 : fontSize / 2;
        } else {
            return fontSize <= 0 ? (int) (bitmap.getWidth() * 0.01) : fontSize;
        }
    }

    public static Bitmap makePreviewFromVideoFile(File videoFile, int gridSize, boolean writeDuration) {
        logger.debug("makePreviewFromVideoFile, videoFile=" + videoFile + ", gridSize=" + gridSize);

        if (!(FileHelper.isFileCorrect(videoFile) && FileHelper.isVideo(FileHelper.getFileExtension(videoFile.getName())))) {
            logger.error("incorrect video file");
            return null;
        }

        if (gridSize <= 0) {
            logger.error("incorrect grid size");
            return null;
        }

        final List<Pair<Long, Bitmap>> videoFramesList = getVideoFrames(videoFile, gridSize * gridSize);
        if (videoFramesList == null || videoFramesList.isEmpty()) {
            logger.error("videoFramesList is null or empty");
            return null;
        }
        ArrayList<Bitmap> bitmapsList = new ArrayList<>(videoFramesList.size());

        for (Pair<Long, Bitmap> videoFrame : videoFramesList) {
            bitmapsList.add(videoFrame.second);
        }

        final Bitmap resultImage = combineImagesToOne(bitmapsList, gridSize);

        for (Bitmap chunk : bitmapsList) {
            if (chunk != null) {
                chunk.recycle();
            }
        }

        if (writeDuration) {
            return writeTextOnBitmap(resultImage, "duration: " + getVideoDuration(videoFile) + " ms", Color.WHITE);
        }

        return resultImage;
    }

    /**
     * @return null if failed
     */
    public static File writeCompressedBitmapToFile(File file, Bitmap data, Bitmap.CompressFormat format) {

        if (!isBitmapCorrect(data)) {
            logger.error("incorrect bitmap");
            return null;
        }

        String ext = getFileExtByCompressFormat(format);

        if (ext == null) {
            logger.error("file ext is null");
            return null;
        }

        file = FileHelper.createNewFile(file.getName() + "." + ext, file.getParent());

        if (file == null) {
            logger.error("file was not created");
            return null;
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            data.compress(format, 100, fos);
            fos.flush();
            fos.close();
            return file;
        } catch (IOException e) {
            logger.error("an IOException occurred : " + e.getMessage());
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                logger.error("an IOException occurred during close: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * @param gridSize number of width or height chunks in result image
     */
    public static Bitmap combineImagesToOne(List<Bitmap> chunkImagesList, int gridSize) {

        if (chunkImagesList == null || chunkImagesList.size() == 0) {
            logger.error("chunkImagesList is null or empty");
            return null;
        }

        if (gridSize <= 0) {
            logger.error("incorrect gridSize: " + gridSize);
            return null;
        }

        if (gridSize * gridSize < chunkImagesList.size()) {
            // logger.warn("grid dimension is less than number of chunks, removing excessive chunks...");
            for (int i = chunkImagesList.size() - 1; i > gridSize * gridSize - 1; i--) {
                // logger.debug(" _ remove chunk with index " + i);
                chunkImagesList.remove(i);
            }
        }

        int chunkWidth = 0;
        int chunkHeight = 0;

        for (int i = 0; i < chunkImagesList.size(); i++) {
            if (chunkImagesList.get(i) != null) {

                // logger.debug("chunk at index " + i + ": " + chunkImagesList.get(i).getWidth() + "x" +
                // chunkImagesList.get(i).getHeight());

                if (chunkWidth > 0 && chunkHeight > 0) {

                    if (chunkImagesList.get(i).getWidth() != chunkWidth || chunkImagesList.get(i).getHeight() != chunkHeight) {
                        logger.error("chunk images in list have different dimensions, previous: " + chunkWidth + "x" + chunkHeight
                                + ", current: " + chunkImagesList.get(i).getWidth() + "x" + chunkImagesList.get(i).getHeight());
                        return null;
                    }

                } else {
                    chunkWidth = chunkImagesList.get(i).getWidth();
                    chunkHeight = chunkImagesList.get(i).getHeight();
                }
            } else {
                logger.error("chunk at index " + i + " is null");
                // chunkImagesList.remove(i);
            }
        }

        logger.debug("chunk: " + chunkWidth + " x " + chunkHeight);

        if (chunkWidth == 0 || chunkHeight == 0) {
            logger.error("incorrect chunk dimensions");
            return null;
        }

        // create a bitmap of a size which can hold the complete image after merging
        Bitmap resultBitmap = null;

        try {
            resultBitmap = Bitmap.createBitmap(chunkWidth * gridSize, chunkHeight * gridSize, Bitmap.Config.RGB_565);
        } catch (OutOfMemoryError e) {
            logger.error("an OutOfMemoryError error occurred during createBitmap()", e);
            return null;
        }

        // create a canvas for drawing all those small images
        Canvas canvas = new Canvas(resultBitmap);
        int counter = 0;
        for (int rows = 0; rows < gridSize; rows++) {
            for (int cols = 0; cols < gridSize; cols++) {
                if (counter >= chunkImagesList.size() || chunkImagesList.get(counter) == null) {
                    logger.error("chunk with index " + counter + " is null");
                    counter++;
                    continue;
                }
                canvas.drawBitmap(chunkImagesList.get(counter), chunkWidth * cols, chunkHeight * rows, null);
                counter++;
            }
        }

        return resultBitmap;
    }

    /**
     * Converts pixel value to dp value
     */
    public static int pxToDp(int px, Context context) {
        return (int) ((float) px / context.getResources().getDisplayMetrics().density);
    }

    public static int dpToPx(int dp, Context ctx) {

        // OR simply px = dp * density

        if (dp <= 0 || ctx == null) {
            return 0;
        }

        // getResources().getDisplayMetrics()
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displayMetrics);
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics);
    }

    public static Bitmap cropBitmap(Bitmap srcBitmap, int fromX, int fromY, int toX, int toY) {

        if (!isBitmapCorrect(srcBitmap)) {
            logger.error("incorrect bitmap");
            return srcBitmap;
        }

        if (fromX < 0 || fromY < 0 || toX <= 0 || toY <= 0) {
            logger.error("incorrect coords (1)");
            return srcBitmap;
        }

        if (toX <= fromX || toY <= fromY) {
            logger.error("incorrect coords (2)");
            return srcBitmap;
        }

        final int rectWidth = toX - fromX;
        final int rectHeight = toY - fromY;

        Bitmap bmOverlay = Bitmap.createBitmap(rectWidth, rectHeight, Bitmap.Config.RGB_565);

        Paint p = new Paint();
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        Canvas c = new Canvas(bmOverlay);
        c.drawBitmap(srcBitmap, 0, 0, null);
        c.drawRect(fromX, fromY, toX, toY, p);

        return bmOverlay;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidthPx, int reqHeightPx) {

        if (reqWidthPx <= 0 || reqHeightPx <= 0) {
            return 0;
        }

        if (options == null) {
            return 0;
        }

        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeightPx || width > reqWidthPx) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeightPx);
            final int widthRatio = Math.round((float) width / (float) reqWidthPx);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        return inSampleSize;
    }

    public static Bitmap createBitmapFromResource(@DrawableRes int resId, int scale, Context context) {

        if (resId <= 0) {
            return null;
        }

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resId, options);

        final int widthPx = scale > 1 ? options.outWidth / scale : options.outWidth;
        final int heightPx = scale > 1 ? options.outHeight / scale : options.outHeight;

        options.inSampleSize = calculateInSampleSize(options, widthPx, heightPx);
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        try {
            return BitmapFactory.decodeResource(context.getResources(), resId, options);
        } catch (OutOfMemoryError e) {
            logger.error("an OutOfMemoryError error occurred during decodeResource()", e);
            return null;
        }
    }

    public static Bitmap createResizedBitmap(Bitmap bm, int newWidth) {

        if (bm == null) {
            return null;
        }

        if (newWidth <= 0) {
            return null;
        }

        int width = bm.getWidth();
        int height = bm.getHeight();

//        float aspectRatio;
//        aspectRatio = (float) (width / height);
//        int newHeight;
//        newHeight = Math.round((float) newWidth / aspectRatio);

        float scale = (float) newWidth / (float) width;
        int newHeight = (int) ((float) height * scale);

        return Bitmap.createScaledBitmap(bm, newWidth, newHeight, false);
    }

    public static boolean canDecodeImage(File file) {
        if (!FileHelper.isFileCorrect(file) || !FileHelper.isPicture(FileHelper.getFileExtension(file.getName()))) {
            return false;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return options.outWidth > -1 && options.outHeight > -1;
    }

    public static boolean canDecodeVideo(File file) {
        if (!FileHelper.isFileCorrect(file) || !FileHelper.isVideo(FileHelper.getFileExtension(file.getName()))) {
            return false;
        }
        return getVideoDuration(file) > 0;
    }

    public static Bitmap createBitmapFromStream(InputStream is, int scale) {
        return createBitmapByByteArray(FileHelper.readBytesFromInputStream(is), scale);
    }

    public static Bitmap createBitmapFromUri(@NonNull Context context, Uri uri, int scale) {

        if (!(uri != null && (uri.getScheme() == null || uri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)))) {
            logger.error("incorrect resource uri: " + uri);
            return null;
        }

        try {
            return createBitmapFromStream((context.getContentResolver().openInputStream(uri)), scale);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static Bitmap createBitmapFromFile(File file, int scale) {

        if (!FileHelper.isFileCorrect(file) || !FileHelper.isPicture(FileHelper.getFileExtension(file.getName()))) {
            logger.error("incorrect file: " + file);
            return null;
        }

        return createBitmapByByteArray(FileHelper.readBytesFromFile(file), scale);
    }

    public static Bitmap createBitmapByByteArray(byte[] data, int scale) {

        if (data == null || data.length == 0) {
            logger.error("data is null or empty");
            return null;
        }

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        final int widthPx = scale > 1 ? options.outWidth / scale : options.outWidth;
        final int heightPx = scale > 1 ? options.outHeight / scale : options.outHeight;

        options.inSampleSize = calculateInSampleSize(options, widthPx, heightPx);
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (OutOfMemoryError e) {
            logger.error("an OutOfMemoryError error occurred during decodeByteArray()", e);
            return null;
        }
    }

    public static Bitmap createBitmapFromDrawable(Drawable d, Bitmap.Config config, int widthPixels, int heightPixels) {

        if (d == null || config == null) {
            return null;
        }

        widthPixels = widthPixels > 0 ? widthPixels : d.getIntrinsicWidth();
        heightPixels = heightPixels > 0 ? heightPixels : d.getIntrinsicHeight();

        if (widthPixels <= 0 || heightPixels <= 0) {
            logger.error("incorrect bounds: " + widthPixels + "x" + heightPixels);
            return null;
        }

        Bitmap mutableBitmap = Bitmap.createBitmap(widthPixels, heightPixels, config);
        Canvas canvas = new Canvas(mutableBitmap);
        d.setBounds(0, 0, widthPixels, heightPixels);
        d.draw(canvas);
        return mutableBitmap;
    }

    /**
     * Определяет поворот картинки
     *
     * @param context
     * @param photoUri
     * @return
     */
    public static int getOrientation(Context context, Uri photoUri) {
    /* it's on the external media. */
        Cursor cursor = context.getContentResolver().query(photoUri,
                new String[]{MediaStore.Images.ImageColumns.ORIENTATION}, null, null, null);

        if (cursor == null || cursor.getCount() != 1) {
            return -1;
        }

        cursor.moveToFirst();
        return cursor.getInt(0);
    }

    /**
     * Определяем угол для поворота http://sylvana.net/jpegcrop/exif_orientation.html
     *
     * @param orientation
     * @return
     */
    public static int getRotateAngleByOrientation(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return 0;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    public static Bitmap getCorrectlyOrientedImage(Context context, Uri uri, Bitmap sourceBitmap, boolean recycleSource) {
        if (isBitmapCorrect(sourceBitmap)) {
            ExifInterface exif;
            try {
                exif = new ExifInterface(FileHelper.getPath(context, uri));
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                int rotateAngle = getRotateAngleByOrientation(orientation);
                return rotateBitmap(sourceBitmap, rotateAngle, recycleSource);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static Bitmap getCorrectlyOrientedImage(Context context, Uri photoUri, final int MAX_IMAGE_DIMENSION) throws IOException {
        InputStream is = context.getContentResolver().openInputStream(photoUri);
        BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, dbo);

        if (is != null)
            is.close();

        int rotatedWidth, rotatedHeight;
        int orientation = getOrientation(context, photoUri);

        if (orientation == 90 || orientation == 270) {
            rotatedWidth = dbo.outHeight;
            rotatedHeight = dbo.outWidth;
        } else {
            rotatedWidth = dbo.outWidth;
            rotatedHeight = dbo.outHeight;
        }

        Bitmap srcBitmap;
        is = context.getContentResolver().openInputStream(photoUri);
        if (rotatedWidth > MAX_IMAGE_DIMENSION || rotatedHeight > MAX_IMAGE_DIMENSION) {
            float widthRatio = ((float) rotatedWidth) / ((float) MAX_IMAGE_DIMENSION);
            float heightRatio = ((float) rotatedHeight) / ((float) MAX_IMAGE_DIMENSION);
            float maxRatio = Math.max(widthRatio, heightRatio);

            // Create the bitmap from file
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = (int) maxRatio;
            srcBitmap = BitmapFactory.decodeStream(is, null, options);
        } else {
            srcBitmap = BitmapFactory.decodeStream(is);
        }
        if (is != null) {
            is.close();
        }

    /*
     * if the orientation is not 0 (or -1, which means we don't know), we
     * have to do a rotation.
     */
        if (orientation > 0) {
            return rotateBitmap(srcBitmap, orientation, true);
        }

        return srcBitmap;
    }

    public static Bitmap rotateBitmap(Bitmap sourceBitmap, int angle, boolean recycleSource) {

        if (!isBitmapCorrect(sourceBitmap)) {
            logger.error("incorrect bitmap: " + sourceBitmap);
            return null;
        }

        if (angle < 0 || angle > 360) {
            logger.error("incorrect angle: " + angle);
            return null;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        try {
            return Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(),
                    sourceBitmap.getHeight(), matrix, true);
        } finally {
            if (recycleSource) {
                sourceBitmap.recycle();
            }
        }
    }

    public static Bitmap mirrorBitmap(Bitmap sourceBitmap, boolean recycleSource) {

        if (!isBitmapCorrect(sourceBitmap)) {
            logger.error("incorrect bitmap: " + sourceBitmap);
            return null;
        }

        Matrix matrix = new Matrix();
        matrix.preScale(-1, 1);
        try {
            return Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(),
                    sourceBitmap.getHeight(), matrix, true);
        } finally {
            if (recycleSource) {
                sourceBitmap.recycle();
            }
        }
    }

    public static boolean isBitmapCorrect(Bitmap b) {
        return (b != null && !b.isRecycled() && getBitmapByteCount(b) > 0);
    }

    @SuppressLint("NewApi")
    public static int getBitmapByteCount(Bitmap b) {

        if (b == null)
            return 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return b.getByteCount();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return b.getAllocationByteCount();
        } else {
            return 0;
        }
    }

    public static byte[] getBitmapData(Bitmap b) {
        if (!isBitmapCorrect(b)) {
            logger.error("incorrect bitmap");
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(b.getHeight() * b.getRowBytes());
        b.copyPixelsToBuffer(buffer);
        return buffer.hasArray() ? buffer.array() : null;
    }

    public static Bitmap setBitmapData(byte[] data, int width, int height, Bitmap.Config config) {

        if (data == null || data.length == 0) {
            logger.error("data is null or empty");
            return null;
        }

        if (width <= 0 || height <= 0) {
            logger.error("incorrect image size: " + width + "x" + height);
            return null;
        }

        if (config == null) {
            logger.error("config is null");
            return null;
        }

        Bitmap b = Bitmap.createBitmap(width, height, config);
        ByteBuffer frameBuffer = ByteBuffer.wrap(data);
        b.copyPixelsFromBuffer(frameBuffer);

        return b;
    }

    /**
     * creating new bitmap using specified source bitmap
     *
     * @param b can be immutable
     * @return newly created bitmap with given config
     */
    public static Bitmap createBitmap(Bitmap b, Bitmap.Config c) {

        if (!isBitmapCorrect(b)) {
            logger.error("incorrect bitmap");
            return b;
        }

        if (c == null) {
            logger.error("config is null");
            return b;
        }

        Bitmap convertedBitmap = null;

        try {
            convertedBitmap = Bitmap.createBitmap(b.getWidth(), b.getHeight(), c);
        } catch (OutOfMemoryError e) {
            logger.error("an OutOfMemoryError error occurred during createBitmap()", e);
            return b;
        }

        Canvas canvas = new Canvas(convertedBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        canvas.drawBitmap(b, 0, 0, paint);
        return convertedBitmap;
    }

    /**
     * @param b must be mutable
     * @return
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static Bitmap reconfigureBitmap(Bitmap b, Bitmap.Config c) {
        if (b == null || c == null)
            return b;

        if (!b.isMutable()) {
            logger.error("given bitmap is immutable!");
            return null;
        }

        if (b.getConfig() == c)
            return b;

        b.reconfigure(b.getWidth(), b.getHeight(), c);
        return b;
    }

    public static byte[] convertYuvToJpeg(byte[] data, int format, int width, int height) {
        logger.debug("convertYuvToJpeg(), data (length)=" + (data != null ? data.length : 0) + ", format=" + format + ", width=" + width
                + ", height=" + height);

        if (data == null || data.length == 0) {
            logger.error("data is null or emty");
            return null;
        }

        if (!(format == ImageFormat.NV16 || format == ImageFormat.NV21 || format == ImageFormat.YV12 || format == ImageFormat.YUY2)) {
            logger.error("incorrect image format: " + format);
            return null;
        }

        if (width <= 0 || height <= 0) {
            logger.error("incorrect resolution: " + width + "x" + height);
            return null;
        }

        YuvImage yuvImg = new YuvImage(data, format, width, height, null);
        ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
        yuvImg.compressToJpeg(new Rect(0, 0, width, height), 100, byteOutStream);
        return byteOutStream.toByteArray();
    }

    public static byte[] convertRgbToYuv420SP(int[] aRGB, int width, int height) {
        // logger.debug("convertRgbToYuv420SP(), width=" + width + ", height=" + height);

        if (aRGB == null || aRGB.length == 0) {
            logger.error("data is null or empty");
            return null;
        }

        if (width <= 0 || height <= 0) {
            logger.error("incorrect image size");
            return null;
        }

        final int frameSize = width * height;
        final int chromaSize = frameSize / 4;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + chromaSize;
        byte[] yuv = new byte[width * height * 3 / 2];

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                // a = (aRGB[index] & 0xff000000) >> 24; //not using it right now
                R = (aRGB[index] & 0xff0000) >> 16;
                G = (aRGB[index] & 0xff00) >> 8;
                B = (aRGB[index] & 0xff) >> 0;

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));

                if (j % 2 == 0 && index % 2 == 0) {
                    yuv[uIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv[vIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }

                index++;
            }
        }
        return yuv;
    }

    public static int generateRandomColor(int color) {
        Random random = new Random();
        int red = random.nextInt(256);
        int green = random.nextInt(256);
        int blue = random.nextInt(256);
        return Color.rgb((red + (Color.red(color))) / 2, (green + (Color.green(color))) / 2, (blue + (Color.blue(color))) / 2);
    }

    public static int[] toIntArray(byte[] byteArray, ByteOrder order) {
        // ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.nativeOrder());
        // final int[] ret = new int[buf.length / 4];
        // buffer.asIntBuffer().put(ret);
        // buffer = null;
        // return ret;

        IntBuffer intBuf = ByteBuffer.wrap(byteArray).order(order).asIntBuffer();

        int[] intArray = new int[intBuf.remaining()];
        intBuf.get(intArray);

        return intArray;
    }

    public static byte[] toByteArray(int[] intArray, ByteOrder order) {
        final ByteBuffer buf = ByteBuffer.allocate(intArray.length * 4).order(order);
        buf.asIntBuffer().put(intArray);
        return buf.array();
    }


    public enum Swatch {
        VIBRANT,
        LIGHT_VIBRANT,
        DARK_VIBRANT,
        MUTED,
        LIGHT_MUTED,
        DARK_MUTED
    }

    public static class PaletteColors {
        @ColorInt
        public int background = Color.WHITE;
        @ColorInt
        public int title = Color.BLACK;
        @ColorInt
        public int body = Color.BLACK;

        public PaletteColors() {
        }

        public PaletteColors(@ColorInt int background, @ColorInt int title, @ColorInt int body) {
            this.background = background;
            this.title = title;
            this.body = body;
        }
    }

    public interface OnPaletteColorsGeneratedListener {
        void onPaletteColorsGenerated(PaletteColors colors);
    }

    private static PaletteColors makePaletteColors(Palette palette, @ColorInt final int defaultColor, final Swatch sw) {
        final PaletteColors colors = new PaletteColors();
        if (palette != null && sw != null) {
            switch (sw) {
                case MUTED:
                    colors.background = palette.getMutedColor(defaultColor);
                    colors.title = palette.getMutedSwatch() != null ? palette.getMutedSwatch().getTitleTextColor() : defaultColor;
                    colors.body = palette.getMutedSwatch() != null ? palette.getMutedSwatch().getBodyTextColor() : defaultColor;
                    break;
                case LIGHT_MUTED:
                    colors.background = palette.getLightMutedColor(defaultColor);
                    colors.title = palette.getLightMutedSwatch() != null ? palette.getLightMutedSwatch().getTitleTextColor() : defaultColor;
                    colors.body = palette.getLightMutedSwatch() != null ? palette.getLightMutedSwatch().getBodyTextColor() : defaultColor;
                    break;
                case DARK_MUTED:
                    colors.background = palette.getDarkMutedColor(defaultColor);
                    colors.title = palette.getDarkMutedSwatch() != null ? palette.getDarkMutedSwatch().getTitleTextColor() : defaultColor;
                    colors.body = palette.getDarkMutedSwatch() != null ? palette.getDarkMutedSwatch().getBodyTextColor() : defaultColor;
                    break;
                case VIBRANT:
                    colors.background = palette.getVibrantColor(defaultColor);
                    colors.title = palette.getVibrantSwatch() != null ? palette.getVibrantSwatch().getTitleTextColor() : defaultColor;
                    colors.body = palette.getVibrantSwatch() != null ? palette.getVibrantSwatch().getBodyTextColor() : defaultColor;
                    break;
                case LIGHT_VIBRANT:
                    colors.background = palette.getLightVibrantColor(defaultColor);
                    colors.title = palette.getLightVibrantSwatch() != null ? palette.getLightVibrantSwatch().getTitleTextColor() : defaultColor;
                    colors.body = palette.getLightVibrantSwatch() != null ? palette.getLightVibrantSwatch().getBodyTextColor() : defaultColor;
                    break;
                case DARK_VIBRANT:
                    colors.background = palette.getDarkVibrantColor(defaultColor);
                    colors.title = palette.getDarkVibrantSwatch() != null ? palette.getDarkVibrantSwatch().getTitleTextColor() : defaultColor;
                    colors.body = palette.getDarkVibrantSwatch() != null ? palette.getDarkVibrantSwatch().getBodyTextColor() : defaultColor;
                    break;
                default:
                    break;
            }
        }
        return colors;
    }

    public static PaletteColors generateColorByBitmap(Bitmap bm, @ColorInt final int defaultColor, final Swatch sw) {
        if (isBitmapCorrect(bm) && sw != null) {
            return makePaletteColors(new Palette.Builder(bm).generate(), defaultColor, sw);
        }
        return null;
    }

    public static void generateColorByBitmapAsync(Bitmap bm, @ColorInt final int defaultColor, final Swatch sw, final OnPaletteColorsGeneratedListener listener) {
        if (isBitmapCorrect(bm) && sw != null && listener != null) {
            new Palette.Builder(bm).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    listener.onPaletteColorsGenerated(makePaletteColors(palette, defaultColor, sw));
                }
            });
        }
    }
}
