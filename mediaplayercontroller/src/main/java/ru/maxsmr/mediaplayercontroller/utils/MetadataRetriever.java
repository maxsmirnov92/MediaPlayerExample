package ru.maxsmr.mediaplayercontroller.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.util.Map;

import ru.maxsmr.commonutils.data.FileHelper;

public final class MetadataRetriever {

    private MetadataRetriever() {
        throw new UnsupportedOperationException("no instances.");
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

    @NonNull
    public static MediaMetadata extractMetaData(@Nullable Context context, @Nullable Uri resourceUri, @Nullable Map<String, String> headers) {

        MediaMetadata metadata = new MediaMetadata();

        MediaMetadataRetriever retriever = null;

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
                        retriever.setDataSource(resourceUri.toString(), headers);
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                return metadata;
            }

            metadata.duration = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_DURATION, Integer.class);
            metadata.cdTrackNumber = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, Integer.class);
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
            metadata.hasAudio = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO, Boolean.class);
            metadata.hasVideo = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, Boolean.class);
            metadata.videoWidth = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, Integer.class);
            metadata.videoHeight = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, Integer.class);
            metadata.bitrate = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_BITRATE, Integer.class);
            metadata.timedTextLanguages = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_BITRATE, String.class);
            metadata.isDrm = extractMetadataFieldNoThrow(retriever, 22, Boolean.class);
            metadata.location = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_LOCATION, String.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                metadata.videoRotation = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, Integer.class);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                metadata.captureFrameRate = extractMetadataFieldNoThrow(retriever, MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE, Integer.class);
            }
        }

        try {
            return metadata;
        } finally {
            if (retriever != null) {
                retriever.release();
            }
        }
    }

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
                    return !isEmpty ? (M) Long.valueOf(value) : defaultValue;
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return defaultValue;
                }
            } else if (clazz.isAssignableFrom(Integer.class)) {
                try {
                    return !isEmpty ? (M) Integer.valueOf(value) : defaultValue;
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return defaultValue;
                }
            } else if (clazz.isAssignableFrom(Boolean.class)) {
                return !isEmpty ? (M) Boolean.valueOf(value) : defaultValue;
            } else {
                throw new UnsupportedOperationException("incorrect class: " + clazz);
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
            return defaultValue;
        }
    }


    public static class MediaMetadata {

        long duration;

        int cdTrackNumber;

        String album;

        String artist;

        String author;

        String composer;

        String date;

        String genre;

        String title;

        int year;

        int numTracks;

        String writer;

        String mimeType;

        String albumArtist;

        String compilation;

        boolean hasAudio;

        boolean hasVideo;

        int videoWidth;

        int videoHeight;

        int bitrate;

        String timedTextLanguages;

        boolean isDrm;

        String location;

        int videoRotation;

        int captureFrameRate;

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
