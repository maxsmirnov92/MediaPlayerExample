package ru.maxsmr.commonutils.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

import ru.maxsmr.commonutils.graphic.GraphicUtils;

public final class MetadataRetriever {

    private static final Logger logger = LoggerFactory.getLogger(MetadataRetriever.class);

    private MetadataRetriever() {
        throw new UnsupportedOperationException("no instances.");
    }

    @NonNull
    private static MediaMetadataRetriever createMediaMetadataRetriever(@Nullable Context context, @Nullable Uri resourceUri, @Nullable Map<String, String> headers) throws RuntimeException {

        MediaMetadataRetriever retriever;

        if (resourceUri != null && !resourceUri.toString().isEmpty()) {

            retriever = new MediaMetadataRetriever();

            try {
                if (TextUtils.isEmpty(resourceUri.getScheme()) || resourceUri.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) {
                    retriever.setDataSource(resourceUri.getPath());
                } else {
                    if (context == null && (headers == null || headers.isEmpty())) {
                        throw new NullPointerException("scheme is not empty or " + ContentResolver.SCHEME_FILE + " and context was not specified");
                    }
                    if (headers == null || headers.isEmpty()) {
                        retriever.setDataSource(context, resourceUri);
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            retriever.setDataSource(resourceUri.toString(), headers);
                        } else {
                            retriever.setDataSource(context, resourceUri);
                        }
                    }
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("can't set data source", e);
            }
        } else {
            throw new IllegalArgumentException("resourceUri is empty");
        }

        return retriever;
    }

    @Nullable
    private static MediaMetadataRetriever createMediaMetadataRetrieverNoThrow(@Nullable Context context, @Nullable Uri resourceUri, @Nullable Map<String, String> headers) {
        try {
            return createMediaMetadataRetriever(context, resourceUri, headers);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static Bitmap extractFrame(@NonNull Context context, @Nullable Uri resourceUri, long positionMs) {
        return extractFrame(context, resourceUri, null, positionMs);
    }

    @Nullable
    public static Bitmap extractFrame(@Nullable File file, long positionMs) {
        return FileHelper.isFileCorrect(file) ? extractFrame(file.getAbsolutePath(), positionMs) : null;
    }

    @Nullable
    public static Bitmap extractFrame(@Nullable String filePath, long positionMs) {
        return !TextUtils.isEmpty(filePath) ? extractFrame(null, Uri.parse(filePath), null, positionMs) : null;
    }

    @Nullable
    public static Bitmap extractFrame(@Nullable String url, @Nullable Map<String, String> headers, long positionMs) {
        return !TextUtils.isEmpty(url) ? extractFrame(null, Uri.parse(url), headers, positionMs) : null;
    }

    @SuppressWarnings("ConstantConditions")
    @Nullable
    public static Bitmap extractFrame(@Nullable Context context, @Nullable Uri resourceUri, @Nullable Map<String, String> headers, long positionMs) {
        MediaMetadataRetriever retriever = createMediaMetadataRetrieverNoThrow(context, resourceUri, headers);
        if (retriever == null) {
            return null;
        }
        try {
            return retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } finally {
            retriever.release();
        }
    }

    @Nullable
    public static <M> M extractMetaDataField(@NonNull Context context, @Nullable Uri resourceUri, int keyCode, @NonNull Class<M> clazz) {
        return extractMetaDataField(context, resourceUri, keyCode, clazz, null);
    }

    @Nullable
    public static <M> M extractMetaDataField(@NonNull Context context, @Nullable Uri resourceUri, int keyCode, @NonNull Class<M> clazz, @Nullable M defaultValue) {
        return extractMetaDataField(context, resourceUri, null, keyCode, clazz, defaultValue);
    }

    @Nullable
    public static <M> M extractMetaDataField(@Nullable File file, int keyCode, @NonNull Class<M> clazz) {
        return extractMetaDataField(file, keyCode, clazz, null);
    }

    @Nullable
    public static <M> M extractMetaDataField(@Nullable File file, int keyCode, @NonNull Class<M> clazz, @Nullable M defaultValue) {
        return FileHelper.isFileCorrect(file) ? extractMetaDataField(file.getAbsolutePath(), keyCode, clazz, defaultValue) : null;
    }

    @Nullable
    public static <M> M extractMetaDataField(@Nullable String filePath, int keyCode, @NonNull Class<M> clazz) {
        return extractMetaDataField(filePath, keyCode, clazz, null);
    }

    @Nullable
    public static <M> M extractMetaDataField(@Nullable String filePath, int keyCode, @NonNull Class<M> clazz, @Nullable M defaultValue) {
        return !TextUtils.isEmpty(filePath) ? extractMetaDataField(null, Uri.parse(filePath), null, keyCode, clazz, defaultValue) : null;
    }

    @Nullable
    public static <M> M extractMetaDataField(@Nullable String url, @Nullable Map<String, String> headers, int keyCode, @NonNull Class<M> clazz) {
        return extractMetaDataField(url, headers, keyCode, clazz, null);
    }

    @Nullable
    public static <M> M extractMetaDataField(@Nullable String url, @Nullable Map<String, String> headers, int keyCode, @NonNull Class<M> clazz, @Nullable M defaultValue) {
        return !TextUtils.isEmpty(url) ? extractMetaDataField(null, Uri.parse(url), headers, keyCode, clazz, defaultValue) : null;
    }

    @SuppressWarnings("ConstantConditions")
    @Nullable
    public static <M> M extractMetaDataField(@Nullable Context context, @Nullable Uri resourceUri, @Nullable Map<String, String> headers, int keyCode, @NonNull Class<M> clazz) {
        MediaMetadataRetriever retriever = createMediaMetadataRetrieverNoThrow(context, resourceUri, headers);
        if (retriever == null) {
            return null;
        }
        try {
            return extractMetadataFieldNoThrow(retriever, keyCode, clazz, null);
        } finally {
            retriever.release();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Nullable
    public static <M> M extractMetaDataField(@Nullable Context context, @Nullable Uri resourceUri, @Nullable Map<String, String> headers, int keyCode, @NonNull Class<M> clazz, @Nullable M defaultValue) {
        MediaMetadataRetriever retriever = createMediaMetadataRetrieverNoThrow(context, resourceUri, headers);
        if (retriever == null) {
            return null;
        }
        return extractMetadataFieldNoThrow(retriever, keyCode, clazz, defaultValue);
    }

    @NonNull
    public static MediaMetadata extractMetaData(@NonNull Context context, @Nullable Uri resourceUri) {
        return extractMetaData(context, resourceUri, null);
    }

    @NonNull
    public static MediaMetadata extractMetaData(@Nullable File file) {
        return FileHelper.isFileCorrect(file) ? extractMetaData(file.getAbsolutePath()) : new MediaMetadata();
    }

    @NonNull
    public static MediaMetadata extractMetaData(@Nullable String filePath) {
        return !TextUtils.isEmpty(filePath) ? extractMetaData(null, Uri.parse(filePath), null) : new MediaMetadata();
    }

    @NonNull
    public static MediaMetadata extractMetaData(@Nullable String url, @Nullable Map<String, String> headers) {
        return !TextUtils.isEmpty(url) ? extractMetaData(null, Uri.parse(url), headers) : new MediaMetadata();
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    public static MediaMetadata extractMetaData(@Nullable Context context, @Nullable Uri resourceUri, @Nullable Map<String, String> headers) {

        MediaMetadata metadata = new MediaMetadata();

        MediaMetadataRetriever retriever = createMediaMetadataRetrieverNoThrow(context, resourceUri, headers);

        if (retriever == null) {
            return metadata;
        }

        metadata.duration = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_DURATION, Integer.class);
        metadata.cdTrackNumber = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, String.class);
        metadata.album = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUM, String.class);
        metadata.artist = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_ARTIST, String.class);
        metadata.author = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_AUTHOR, String.class);
        metadata.composer = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_COMPOSER, String.class);
        metadata.date = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_DATE, String.class);
        metadata.genre = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_GENRE, String.class);
        metadata.title = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_TITLE, String.class);
        metadata.year = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_YEAR, Integer.class);
        metadata.numTracks = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS, Integer.class);
        metadata.writer = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_WRITER, String.class);
        metadata.mimeType = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_MIMETYPE, String.class);
        metadata.albumArtist = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, String.class);
        metadata.compilation = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_COMPILATION, String.class);
        metadata.isDrm = extractMetadataFieldNoThrow(retriever, 22, Boolean.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            metadata.hasAudio = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO, Boolean.class);
            metadata.hasVideo = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, Boolean.class);
            metadata.videoWidth = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, Integer.class);
            metadata.videoHeight = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, Integer.class);
            metadata.bitrate = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_BITRATE, Integer.class);
            metadata.timedTextLanguages = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_BITRATE, String.class);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            metadata.location = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_LOCATION, String.class);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            metadata.videoRotation = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, Integer.class);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            metadata.captureFrameRate = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE, Integer.class);
        }

        retriever.release();
        return metadata;
    }

    @Nullable
    private static <M> M extractMetadataFieldNoThrow(@NonNull MediaMetadataRetriever retriever, int keyCode, @NonNull Class<M> clazz) {
        return extractMetadataFieldNoThrow(retriever, keyCode, clazz, null);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <M> M extractMetadataFieldNoThrow(@NonNull MediaMetadataRetriever retriever, int keyCode, @NonNull Class<M> clazz, @Nullable M defaultValue) {
        final String value = retriever.extractMetadata(keyCode);
        final boolean isEmpty = TextUtils.isEmpty(value);
        try {
            if (clazz.isAssignableFrom(String.class)) {
                return !isEmpty ? (M) value : defaultValue;
            } else if (clazz.isAssignableFrom(Long.class)) {
                try {
                    return !isEmpty ? (M) Long.valueOf(value) : (defaultValue != null ? defaultValue : (M) Long.valueOf(0));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return defaultValue;
                }
            } else if (clazz.isAssignableFrom(Integer.class)) {
                try {
                    return !isEmpty ? (M) Integer.valueOf(value) : (defaultValue != null ? defaultValue : (M) Integer.valueOf(0));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return defaultValue;
                }
            } else if (clazz.isAssignableFrom(Boolean.class)) {
                return !isEmpty ? (M) Boolean.valueOf(value) : (defaultValue != null ? defaultValue : (M) Boolean.valueOf(false));
            } else {
                throw new UnsupportedOperationException("incorrect class: " + clazz);
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
            logger.error("value " + value + " cannot be casted to " + clazz);
            return defaultValue;
        }
    }

    @Nullable
    public static Bitmap extractAlbumArt(@NonNull Context context, @Nullable Uri resourceUri) {

        if (resourceUri == null) {
            return null;
        }

        if (!ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(resourceUri.getScheme())) {
            throw new IllegalArgumentException("incorrect uri scheme: " + resourceUri.getScheme() + ", must be " + ContentResolver.SCHEME_CONTENT);
        }

        String[] projections = {MediaStore.Audio.Media.ALBUM_ID};

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(resourceUri, projections, null, null, null);
            if (cursor != null && cursor.isClosed() && cursor.getCount() > 0) {
                Long albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
                cursor.moveToFirst();
                Uri coverUri = Uri.parse("content://media/external/audio/albumart");
                Uri trackCoverUri = ContentUris.withAppendedId(coverUri, albumId);
                return GraphicUtils.createBitmapFromUri(context, trackCoverUri, 1);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }


    public static class MediaMetadata {

        public long duration;

        public String cdTrackNumber;

        public String album;

        public String artist;

        public String author;

        public String composer;

        public String date;

        public String genre;

        public String title;

        public int year;

        public int numTracks;

        public String writer;

        public String mimeType;

        public String albumArtist;

        public String compilation;

        public boolean hasAudio;

        public boolean hasVideo;

        public int videoWidth;

        public int videoHeight;

        public int bitrate;

        public String timedTextLanguages;

        public boolean isDrm;

        public String location;

        public int videoRotation;

        public int captureFrameRate;

        @Override
        public String toString() {
            return "MediaMetadata{" +
                    "duration=" + duration +
                    ", cdTrackNumber=" + cdTrackNumber +
                    ", album='" + album + '\'' +
                    ", artist='" + artist + '\'' +
                    ", author='" + author + '\'' +
                    ", composer='" + composer + '\'' +
                    ", date='" + date + '\'' +
                    ", genre='" + genre + '\'' +
                    ", title='" + title + '\'' +
                    ", year=" + year +
                    ", numTracks=" + numTracks +
                    ", writer='" + writer + '\'' +
                    ", mimeType='" + mimeType + '\'' +
                    ", albumArtist='" + albumArtist + '\'' +
                    ", compilation='" + compilation + '\'' +
                    ", hasAudio=" + hasAudio +
                    ", hasVideo=" + hasVideo +
                    ", videoWidth=" + videoWidth +
                    ", videoHeight=" + videoHeight +
                    ", bitrate=" + bitrate +
                    ", timedTextLanguages='" + timedTextLanguages + '\'' +
                    ", isDrm=" + isDrm +
                    ", location='" + location + '\'' +
                    ", videoRotation=" + videoRotation +
                    ", captureFrameRate=" + captureFrameRate +
                    '}';
        }
    }
}