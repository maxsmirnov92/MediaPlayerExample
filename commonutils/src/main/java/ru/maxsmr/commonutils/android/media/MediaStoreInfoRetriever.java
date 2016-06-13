package ru.maxsmr.commonutils.android.media;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class MediaStoreInfoRetriever {

    private static final Logger logger = LoggerFactory.getLogger(MediaStoreInfoRetriever.class);

    private MediaStoreInfoRetriever() {
        throw new AssertionError("no instances.");
    }

    @NonNull
    public static List<MediaFileInfo> queryMedia(@NonNull Context context, boolean isExternal) {
        List<MediaFileInfo> infos = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(isExternal ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.INTERNAL_CONTENT_URI, null, null, null, null);
        try {
            if (cursor == null) {
                throw new RuntimeException("can't read from " + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
            } else if (cursor.moveToFirst()) {
                do {
                    MediaFileInfo info = new MediaFileInfo(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)), isExternal);
                    info.title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                    info.displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME));
                    info.mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        int widthColumnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.WIDTH);
                        int heightColumnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.HEIGHT);
                        info.videoSize = new Point(widthColumnIndex > 0? cursor.getInt(widthColumnIndex) : 0, heightColumnIndex > 0? cursor.getInt(heightColumnIndex) : 0);
                    }
                    info.dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED));
                    info.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED));
                    info.fileSize = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
                    infos.add(info);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return infos;
    }

    public static class MediaFileInfo {

        public static final int ID_NOT_SET = -1;

        public final boolean isExternal;

        public final long id;

        public String title;

        public String displayName;

        public String mimeType;

        public Point videoSize;

        public long dateAdded;

        public long dateModified;

        public int fileSize;

        public MediaFileInfo(long id, boolean isExternal) {
            this.id = id > 0? id : ID_NOT_SET;
            this.isExternal = isExternal;
        }

        public Uri getContentUri() {
            if (id == ID_NOT_SET) {
                throw new RuntimeException("id was not set");
            }
            return ContentUris.withAppendedId(
                    isExternal? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.INTERNAL_CONTENT_URI, id);
        }

        @Override
        public String toString() {
            return "MediaFileInfo{" +
                    "contentUri=" + getContentUri() +
                    ", id=" + id +
                    ", title='" + title + '\'' +
                    ", displayName='" + displayName + '\'' +
                    ", mimeType='" + mimeType + '\'' +
                    ", videoSize=" + videoSize +
                    ", dateAdded=" + dateAdded +
                    ", dateModified=" + dateModified +
                    ", fileSize=" + fileSize +
                    '}';
        }
    }
}
