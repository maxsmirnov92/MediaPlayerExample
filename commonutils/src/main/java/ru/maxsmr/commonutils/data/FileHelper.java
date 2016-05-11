package ru.maxsmr.commonutils.data;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import ru.maxsmr.commonutils.R;

public class FileHelper {

    private final static Logger logger = LoggerFactory.getLogger(FileHelper.class);

    public static int getPartitionTotalSpaceKb(String path) {
        return isDirExists(path) ? (int) (new File(path).getTotalSpace() / 1024L) : 0;
    }

    public static int getPartitionFreeSpaceKb(String path) {
        return isDirExists(path) ? (int) (new File(path).getFreeSpace() / 1024L) : 0;
    }

    public static boolean isSizeCorrect(File file) {
        return (file != null && file.length() > 0);
    }

    public static boolean isFileCorrect(File file) {
        return (file != null && file.isFile() && isSizeCorrect(file));
    }

    public static boolean isFileExists(String fileName, String parentPath) {
        // logger.debug("isFileExists(), fileName=" + fileName + ", parentPath=" + parentPath);

        if (fileName == null || fileName.length() == 0 || fileName.contains("/")) {
            return false;
        }

        if (parentPath == null || parentPath.length() == 0) {
            return false;
        }

        File parentDir = new File(parentPath);

        if (!(parentDir.exists() && parentDir.isDirectory())) {
            logger.debug("directory " + parentDir.getAbsolutePath() + " not exists or not directory");
            return false;
        }

        File f = new File(parentDir, fileName);

        // logger.debug("f: " + f + ", exists: " + f.exists() + ", file: " + f.isFile());

        return (f.exists() && f.isFile());
    }

    public static boolean isFileExists(String filePath) {
        if (filePath == null || filePath.length() == 0) {
            return false;
        }
        File f = new File(filePath);
        return (f.exists() && f.isFile());
    }

    @Nullable
    public static FileLock lockFileChannel(File f, boolean blocking) {

        if (f == null || !f.isFile() || !f.exists()) {
            logger.error("incorrect file: " + f);
            return null;
        }

        RandomAccessFile randomAccFile = null;
        FileChannel channel = null;

        try {
            randomAccFile = new RandomAccessFile(f, "rw");
            channel = randomAccFile.getChannel();

            try {
                return !blocking ? channel.tryLock() : channel.lock();

            } catch (IOException e) {
                logger.error("an IOException occurred during tryLock()", e);
            } catch (OverlappingFileLockException e) {
                logger.error("an OverlappingFileLockException occurred during tryLock()", e);
            }

        } catch (FileNotFoundException e) {
            logger.error("a FileNotFoundException occurred during new RandomAccessFile()", e);

        } finally {
            try {
                if (channel != null)
                    channel.close();
                if (randomAccFile != null)
                    randomAccFile.close();
            } catch (IOException e) {
                logger.error("an IOException occurred during close()", e);
            }
        }

        return null;
    }

    public static boolean releaseLockNoThrow(@Nullable FileLock lock) {
        try {
            if (lock != null) {
                lock.release();
                return true;
            }
        } catch (IOException e) {
//            e.printStackTrace();
//            logger.error("an IOException occurred during release()", e);
        }
        return false;
    }

    public static boolean isFileLocked(File f) {
        final FileLock l = lockFileChannel(f, false);
        try {
            return l == null;
        } finally {
            releaseLockNoThrow(l);
        }
    }

    public static boolean isDirExists(String dirPath) {

        if (dirPath == null || dirPath.length() == 0) {
            return false;
        }

        File dir = new File(dirPath);

        return (dir.exists() && dir.isDirectory());
    }

    public static boolean testFileNoThrow(@Nullable File file) {
        return file != null && (file.exists() && file.isFile() || createNewFile(file.getName(), file.getParent()) != null);
    }

    public static void testFile(@Nullable File file) {
        if (!testFileNoThrow(file)) {
            throw new IllegalArgumentException("incorrect file: " + file);
        }
    }

    public static boolean testFileNoThrow(@Nullable String file) {
        return !TextUtils.isEmpty(file) && testFileNoThrow(new File(file));
    }

    public static void testFile(@Nullable String file) {
        if (!testFileNoThrow(file)) {
            throw new IllegalArgumentException("incorrect file: " + file);
        }
    }

    public static boolean testDirNoThrow(@Nullable String dirPath) {
        if (!isDirExists(dirPath)) {
            if (createNewDir(dirPath) == null) {
                return false;
            }
        }
        return true;
    }

    public static void testDir(@Nullable String dirPath) {
        if (!testDirNoThrow(dirPath)) {
            throw new IllegalArgumentException("incorrect directory path: " + dirPath);
        }
    }

    public static File testPathNoThrow(String parent, String fileName) {
        if (testDirNoThrow(parent)) {
            if (!TextUtils.isEmpty(fileName)) {
                File f = new File(parent, fileName);
                if (testFileNoThrow(f)) {
                    return f;
                }
            }
        }
        return null;
    }

    public static File testPath(String parent, String fileName) {
        File f = testPathNoThrow(parent, fileName);
        if (f == null) {
            throw new IllegalArgumentException("incorrect path: " + parent + File.separator + fileName);
        }
        return f;
    }

    public static File createNewFile(String fileName, String parentPath) {
        // logger.debug("createNewFile(), fileName=" + fileName + ", parentPath=" + parentPath);

        if (fileName == null || fileName.length() == 0 || fileName.contains("/")) {
            return null;
        }

        if (parentPath == null || parentPath.length() == 0) {
            return null;
        }

        File parentDir = new File(parentPath);
        if (!parentDir.mkdirs()) {
            // logger.debug("no directories created to: " + parentDir.getAbsolutePath());
        }

        File newFile = null;

        if (parentDir.exists() && parentDir.isDirectory()) {
            // logger.debug("directory " + parentDir.getAbsolutePath() + " exists");

            newFile = new File(parentDir, fileName);

            if (newFile.exists() && newFile.isFile()) {
                // logger.debug("file " + newFile.getName() + "already exists, deleting..");
                if (!newFile.delete()) {
                    logger.error("can't delete");
                    return null;
                }
            }

            try {
                if (!newFile.createNewFile()) {
                    logger.error("can't create new file: " + newFile);
                    return null;
                }
            } catch (IOException e) {
                logger.error("an IOException occurred during createNewFile()", e);
                return null;
            }
        } else {
            logger.error("can't create directory: " + parentDir);
        }

        return newFile;
    }

    public static File createNewDir(String dirPath) {

        if (dirPath == null || dirPath.length() == 0)
            return null;

        File dir = new File(dirPath);

        if (dir.isDirectory() && dir.exists())
            return dir;

        if (dir.mkdirs())
            return dir;
        else
            return null;

    }

    public static boolean isBinaryFile(File f) throws FileNotFoundException, IOException {
        FileInputStream in = new FileInputStream(f);
        int size = in.available();
        if (size > 1024)
            size = 1024;
        byte[] data = new byte[size];
        in.read(data);
        in.close();

        int ascii = 0;
        int other = 0;

        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b < 0x09)
                return true;

            if (b == 0x09 || b == 0x0A || b == 0x0C || b == 0x0D)
                ascii++;
            else if (b >= 0x20 && b <= 0x7E)
                ascii++;
            else
                other++;
        }

        if (other == 0)
            return false;

        return 100 * other / (ascii + other) > 95;
    }

    public static boolean revectorStream(InputStream in, OutputStream out) {

        if (in == null || out == null)
            return false;

        try {
            byte[] buff = new byte[256];

            int len;
            while ((len = in.read(buff)) > 0)
                out.write(buff, 0, len);

            in.close();
            out.close();

        } catch (IOException e) {
            logger.error("an IOException occurred", e);
            return false;
        }

        return true;

    }

    public static byte[] readBytesFromFile(File file) {

        if (!isFileCorrect(file)) {
            logger.error("incorrect file: " + file);
            return null;
        }

        FileInputStream inStream = null;

        try {
            inStream = new FileInputStream(file);

            byte[] data = new byte[inStream.available()];
            int readByteCount = 0;
            do {
                readByteCount = inStream.read(data, 0, data.length);
            } while (readByteCount > 0);

            return data;

        } catch (Exception e) {
            logger.error("an Exception occurred", e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    logger.error("an IOException occurred during close()", e);
                }
                inStream = null;
            }
        }
        return null;
    }

    public static List<String> readLinesFromFile(File file) {

        if (!isFileCorrect(file)) {
            logger.error("incorrect file: " + file);
            return null;
        }

        FileReader reader = null;
        try {
            reader = new FileReader(file);

        } catch (FileNotFoundException e) {
            logger.debug("a FileNotFoundException occurred", e);
            return null;
        }

        BufferedReader br = new BufferedReader(reader);

        List<String> linesList = new ArrayList<String>();
        String line;

        try {

            while ((line = br.readLine()) != null) {
                linesList.add(line);
            }

            return linesList;

        } catch (IOException e) {
            logger.error("an IOException occurred during readLine()", e);

        } finally {
            try {
                br.close();
                reader.close();
            } catch (IOException e) {
                logger.error("an IOException occurred during close()", e);
            }
        }

        return null;
    }

    public static File writeBytesToFile(byte[] data, String fileName, String parentPath, boolean append) {
        logger.debug("writeBytesToFile(), data=" + data + ", fileName=" + fileName + ", parentPath=" + parentPath + ", append=" + append);

        if (data == null || data.length == 0) {
            logger.error("data is null or empty");
            return null;
        }

        final File file;

        if (!append) {
            file = createNewFile(fileName, parentPath);
        } else {
            if (!isFileExists(fileName, parentPath))
                file = createNewFile(fileName, parentPath);
            else
                file = new File(parentPath, fileName);
        }

        if (file == null) {
            logger.error("can't create file: " + parentPath + File.separator + fileName);
            return null;
        }

        FileOutputStream fos = null;

        try {

            fos = new FileOutputStream(file, append);
            fos.write(data);
            fos.flush();
            fos.close();
            return file;

        } catch (IOException e) {
            logger.error("an IOException occurred ", e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                logger.error("an IOException occurred during close", e);
            }
        }

        return null;
    }

    public static File writeStringToFile(String data, String fileName, String parentPath, boolean append) {
        // logger.debug("writeStringToFile(), data=" + data + ", fileName=" + fileName + ", parentPath=" + parentPath);

        if (data == null || data.isEmpty()) {
            logger.error("data is null or empty");
            return null;
        }

        final File file;

        if (!append) {
            file = createNewFile(fileName, parentPath);
        } else {
            if (!isFileExists(fileName, parentPath))
                file = createNewFile(fileName, parentPath);
            else
                file = new File(parentPath, fileName);
        }

        if (file == null) {
            logger.error("can't create file: " + parentPath + File.separator + fileName);
            return null;
        }

        FileWriter writer = null;

        try {
            writer = new FileWriter(file, append);
            writer.write(data);
            writer.flush();
            return file;

        } catch (IOException e) {
            logger.error("an IOException occurred during close()", e);

        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    logger.error("an IOException occurred during close()", e);
                }
            }
        }

        return null;
    }

    public final static String FILE_EXT_ZIP = "zip";

    public static File compressFilesToZip(File[] srcFiles, String destZipName) {

        if (srcFiles == null || srcFiles.length == 0) {
            logger.error("srcFiles is null or empty");
            return null;
        }

        if (destZipName == null || destZipName.length() == 0) {
            logger.error("destZipName is null or empty");
            return null;
        }

        try {
            OutputStream os = new FileOutputStream(destZipName);
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));

            try {
                int zippedFiles = 0;

                for (File srcFile : srcFiles) {

                    if (!isFileCorrect(srcFile)) {
                        logger.error("incorrect file to zip: " + srcFile);
                        continue;
                    }

                    byte[] bytes = readBytesFromFile(srcFile);
                    ZipEntry entry = new ZipEntry(srcFile.getName());
                    zos.putNextEntry(entry);
                    zos.write(bytes);
                    zos.closeEntry();

                    zippedFiles++;
                }

                return zippedFiles > 0 ? new File(destZipName) : null;

            } catch (Exception e) {
                logger.error("an Exception occurred", e);

            } finally {

                try {
                    if (zos != null) {
                        zos.close();
                    }
                    if (os != null) {
                        os.close();
                    }
                } catch (IOException e) {
                    logger.error("an IOException occurred during close()", e);
                }

            }

        } catch (IOException e) {
            logger.error("an IOException occurred", e);
        }

        return null;
    }

    public static boolean unzipFile(File zipFile, File destPath, boolean saveDirHierarchy) {

        if (!isFileCorrect(zipFile)) {
            logger.error("incorrect zip file: " + zipFile);
            return false;
        }

        if (destPath == null) {
            logger.error("destPath is null");
            return false;
        }

        ZipFile zip = null;

        InputStream zis = null;
        OutputStream fos = null;

        try {
            zip = new ZipFile(zipFile);

            for (ZipEntry e : Collections.list(zip.entries())) {

                if (e.isDirectory() && !saveDirHierarchy) {
                    continue;
                }

                final String[] parts = e.getName().split(File.separator);
                final String entryName = !saveDirHierarchy && parts.length > 0 ? parts[parts.length - 1] : e.getName();

                final File path = new File(destPath, entryName);

                if (e.isDirectory()) {
                    if (!isDirExists(path.getAbsolutePath()) && !path.mkdirs()) {
                        logger.error("can't create directory: " + path.toString());
                        return false;
                    }

                } else {
                    if (createNewFile(path.getName(), path.getParent()) == null) {
                        logger.error("can't create new file: " + path.toString());
                        return false;
                    }

                    zis = zip.getInputStream(e);
                    fos = new FileOutputStream(path);

                    if (!revectorStream(zis, fos)) {
                        logger.error("revectorStream() failed");
                        return false;
                    }

                    zis.close();
                    fos.close();
                }
            }

        } catch (IOException e) {
            logger.error("an IOException occurred", e);
            return false;

        } finally {

            try {
                if (zip != null) {
                    zip.close();
                }
                if (zis != null) {
                    zis.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                logger.error("an IOException occurred during close()", e);
            }
        }

        return true;
    }

    public final static String[] IMAGES_EXTENSIONS = {"bmp", "jpg", "jpeg", "png"};

    public static boolean isPicture(String ext) {
        for (String pictureExt : IMAGES_EXTENSIONS) {
            if (pictureExt.equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }

    public final static String[] VIDEO_EXTENSIONS = {"3gp", "mp4", "mov", "mpg"};

    public static boolean isVideo(String ext) {
        for (String videoExt : VIDEO_EXTENSIONS) {
            if (videoExt.equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }

    public static String getFileExtension(String fileName) {
        String[] fileNameSplit = fileName.split("\\.");
        if (fileNameSplit.length == 0) {
            return null;
        }
        return fileNameSplit[fileNameSplit.length - 1];
    }

    public static String removeExtension(String fileName) {
        if (!TextUtils.isEmpty(fileName)) {
            String ext = getFileExtension(fileName);
            if (!TextUtils.isEmpty(ext)) {
                int startIndex = fileName.lastIndexOf('.');
                if (startIndex >= 0) {
                    fileName = StringUtils.replace(fileName, startIndex, fileName.length(), "");
                }
                return fileName;
            }
        }
        return fileName;
    }

    public static class FileComparator implements Comparator<File> {

        public enum SortOption {
            NAME, SIZE, LAST_MODIFIED;
        }

        public static class SortOptionPair {

            public final SortOption option;
            public final boolean ascending;

            public SortOptionPair(SortOption option, boolean ascending) {
                this.option = option;
                this.ascending = ascending;
            }

            @Override
            public String toString() {
                return "SortOptionPair{" +
                        "option=" + option +
                        ", ascending=" + ascending +
                        '}';
            }
        }

        private final SortOptionPair[] sortOptions;

        public FileComparator(@Nullable SortOptionPair... sortOptions) {
            this.sortOptions = sortOptions;
        }

        @Override
        public int compare(File lhs, File rhs) {

            if (lhs == null || rhs == null) {
                return lhs == null ? (lhs == rhs ? 0 : -1) : (lhs == rhs ? 0 : 1);
            }

            if (sortOptions != null) {
                for (SortOptionPair pair : sortOptions) {

                    int result;

                    switch (pair.option) {
                        case NAME:

                            result = CompareUtils.compareStrings(lhs.getAbsolutePath(), rhs.getAbsolutePath(), pair.ascending, true);
                            if (result == 0) {
                                continue;
                            }
                            return result;

                        case SIZE:

                            result = CompareUtils.compareLongs(lhs.length(), rhs.length(), pair.ascending);
                            if (result == 0) {
                                continue;
                            }
                            return result;

                        case LAST_MODIFIED:

                            result = CompareUtils.compareLongs(lhs.lastModified(), rhs.lastModified(), pair.ascending);
                            if (result == 0) {
                                continue;
                            }
                            return result;
                    }
                }
            }

            return 0;
        }
    }

    /**
     * @return same list or newly created sorted list
     */
    public static List<File> sortFiles(List<File> filesList, boolean allowModifyList, @NonNull Comparator<? super File> comparator) {

        if (filesList == null || filesList.isEmpty()) {
            logger.error("filesList is null or empty");
            return filesList;
        }

        if (allowModifyList) {
            Collections.sort(filesList, comparator);
            return filesList;
        } else {
            File[] filesArray = filesList.toArray(new File[filesList.size()]);
            Arrays.sort(filesArray, comparator);
            return new ArrayList<>(Arrays.asList(filesArray));
        }
    }

    public static List<File> sortFilesByName(List<File> filesList, boolean ascending, boolean allowModifyList) {
        return sortFiles(filesList, allowModifyList, new FileComparator(new FileComparator.SortOptionPair(FileComparator.SortOption.NAME, ascending)));
    }

    public static List<File> sortFilesBySize(List<File> filesList, boolean ascending, boolean allowModifyList) {
        return sortFiles(filesList, allowModifyList, new FileComparator(new FileComparator.SortOptionPair(FileComparator.SortOption.SIZE, ascending)));
    }

    public static List<File> sortFilesByLastModified(List<File> filesList, boolean ascending, boolean allowModifyList) {
        return sortFiles(filesList, allowModifyList, new FileComparator(new FileComparator.SortOptionPair(FileComparator.SortOption.LAST_MODIFIED, ascending)));
    }

    /**
     * @return found file or directory (first entry) with specified name
     */
    @Nullable
    public static File searchByName(File dir, String name, boolean isAbsolute, boolean isFile, boolean recursiveSearch) {

        if (dir == null || !isDirExists(dir.getAbsolutePath())) {
            logger.error("directory " + dir + " not exists");
            return null;
        }

        File[] files = dir.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                if (!isFile) {
                    if (isAbsolute && f.getAbsolutePath().equalsIgnoreCase(name) || f.getName().equalsIgnoreCase(name)) {
                        return f;
                    }
                }
                if (recursiveSearch) {
                    final File found = searchByName(f, name, isAbsolute, isFile, true);
                    if (found != null) {
                        return found;
                    }
                }
            } else {
                if (isFile) {
                    if (isAbsolute && f.getAbsolutePath().equalsIgnoreCase(name) || f.getName().equalsIgnoreCase(name)) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    @NonNull
    public static List<File> getFiles(@NonNull File fromDir, boolean recursive, @Nullable Comparator<? super File> comparator) {

        final List<File> collected = new ArrayList<>();

        if (!isDirExists(fromDir.getAbsolutePath())) {
            logger.error("directory " + fromDir + " not exists");
            return collected;
        }

        File[] files = fromDir.listFiles();

        for (File f : files) {
            if (f.isDirectory() && recursive) {
                collected.addAll(getFiles(f, true, comparator));
            } else if (f.isFile()) {
                collected.add(f);
            }
        }

        if (comparator != null) {
            Collections.sort(collected, comparator);
        }

        return collected;
    }

    @NonNull
    public static List<File> getFolders(@NonNull File fromDir, boolean recursive, @Nullable Comparator<? super File> comparator) {
        final List<File> collected = new ArrayList<>();

        if (!isDirExists(fromDir.getAbsolutePath())) {
            logger.error("directory " + fromDir + " not exists");
            return collected;
        }

        File[] files = fromDir.listFiles();

        for (File f : files) {
            if (f.isDirectory()) {
                collected.add(f);
                if (recursive) {
                    collected.addAll(getFolders(f, true, comparator));
                }
            }
        }

        if (comparator != null) {
            Collections.sort(collected, comparator);
        }

        return collected;

    }

    public static boolean deleteDir(File dir) {
        return dir != null && dir.isDirectory() && (dir.listFiles() == null || dir.listFiles().length == 0) && dir.delete();
    }

    public static boolean deleteFile(File file) {
        return file != null && file.isFile() && file.exists() && file.delete();
    }

    public static boolean deleteFile(String fileName, String parentPath) {
        if (isFileExists(fileName, parentPath)) {
            File f = new File(parentPath, fileName);
            return f.delete();
        }
        return false;
    }

    public static boolean deleteFile(String filePath) {
        if (isFileExists(filePath)) {
            File f = new File(filePath);
            return f.delete();
        }
        return false;
    }

    /**
     * ! use with caution
     *
     * @return number of deleted files
     */
    public static int delete(@NonNull File fromDir, boolean deleteEmptyDirs, boolean countDeletedDirs, boolean recursiveDelete) {

        if (!isDirExists(fromDir.getAbsolutePath())) {
            logger.error("directory " + fromDir + " not exists");
            return 0;
        }

        int count = 0;

        File[] files = fromDir.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                if (recursiveDelete) {
                    count += delete(f, deleteEmptyDirs, countDeletedDirs, true);
                }
                if (deleteEmptyDirs && deleteDir(f) && countDeletedDirs) {
                    count++;
                }
            } else {
                if (deleteFile(f)) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * @param howMuch if 0 - deletes all
     * @return deleted FILES count
     */
    public static int deleteFilesInList(List<File> filesList, boolean fromStart, int howMuch) {
        logger.debug("deleteFilesInList(), filesList=" + filesList + ", fromStart=" + fromStart + ", howMuch=" + howMuch);

        if (howMuch < 0) {
            throw new IllegalArgumentException("incorrect howMuch: " + howMuch);
        }

        if (filesList == null || filesList.size() == 0) {
            throw new IllegalArgumentException("filesList is null or empty");
        }

        int deletedFiles = 0;

        int from = fromStart ? 0 : filesList.size();
        int to = fromStart ? filesList.size() : 0;

        for (int i = from; i < to; i++) {
            File file = filesList.get(i);
            if (file != null) {
                final double size = (double) file.length() / (double) 1024;
                if (file.exists() && file.isFile() && file.delete()) {
                    logger.info("file " + file + " (size: " + size + " kB) deleted!");
                    deletedFiles++;
                    if (deletedFiles == howMuch) {
                        break;
                    }
                }
            }
        }

        return deletedFiles;
    }

    /**
     * @param howMuch if 0 - deletes all
     * @return deleted FILES count
     */
    public static int deleteFoldersInList(List<File> filesList, boolean fromStart, int howMuch) {
        logger.debug("deleteFoldersInList(), filesList=" + filesList + ", fromStart=" + fromStart + ", howMuch=" + howMuch);

        if (howMuch < 0) {
            throw new IllegalArgumentException("incorrect howMuch: " + howMuch);
        }

        if (filesList == null || filesList.size() == 0) {
            throw new IllegalArgumentException("filesList is null or empty");
        }

        int deletedFolders = 0;
        int deletedFiles = 0;

        int from = fromStart ? 0 : filesList.size();
        int to = fromStart ? filesList.size() : 0;

        for (int i = from; i < to; i++) {
            File file = filesList.get(i);
            if (file != null && file.exists() && file.isDirectory()) {
                deletedFiles += delete(file, true, true, true);
                if (file.delete()) {
                    deletedFolders++;
                    if (deletedFolders == howMuch) {
                        break;
                    }
                }
            }
        }

        return deletedFiles;
    }

    public static void deleteFilesFromDirExclude(File targetDir, Collection<File> excludeFiles, boolean recursive) {
        deleteFilesFromListExclude(getFiles(targetDir, recursive, null), excludeFiles);
    }

    public static void deleteFilesFromListExclude(Collection<File> targetFiles, Collection<File> excludeFiles) {
//        if (excludeFiles != null && !excludeFiles.isEmpty()) {
            for (File f : targetFiles) {
                if (!excludeFiles.contains(f)) {
                    if (!FileHelper.deleteFile(f)) {
                        logger.error("can't delete file: " + f);
                    }
                }
            }
//        }
    }

    /**
     * This function will return size in form of bytes
     * реккурсивно подсчитывает размер папки в байтах
     *
     * @param f файл или папка
     * @return
     */
    public static long getFolderSize(File f) {
        long size = 0;
        if (f.isDirectory()) {
            for (File file : f.listFiles()) {
                size += getFolderSize(file);
            }
        } else {
            size = f.length();
        }
        return size;
    }

    public static String getFolderSizeWithValue(Context context, File file) {
        String value;
        long fileSize = getFolderSize(file) / 1024;//call function and convert bytes into Kb
        if (fileSize >= 1024)
            value = fileSize / 1024 + " " + context.getString(R.string.mb);
        else
            value = fileSize + " " + context.getString(R.string.kb);
        return value;
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    /** users have read and run access to file; owner can modify */
    public final static String FILE_PERMISSIONS_ALL = "755";

    /** only owner has r/w/x access to file */
    public final static String FILE_PERMISSIONS_OWNER = "700";

    /**
     * Copies a raw resource file, given its ID to the given location
     *
     * @param ctx context
     * @param mode file permissions (E.g.: "755")
     * @throws IOException on error
     * @throws InterruptedException when interrupted
     */
    public static boolean copyRawFile(Context ctx, @RawRes int resId, File destFile, String mode) throws IOException, InterruptedException {
        logger.debug("copyRawFile(), resId=" + resId + ", destFile=" + destFile + ", mode=" + mode);

        if (mode == null || mode.length() == 0)
            mode = FILE_PERMISSIONS_ALL;

        final String destFilePath = destFile.getAbsolutePath();

        final FileOutputStream out = new FileOutputStream(destFile);
        final InputStream is = ctx.getResources().openRawResource(resId);

        byte buf[] = new byte[1024];
        int len;

        while ((len = is.read(buf)) > 0) {
            out.write(buf, 0, len);
        }

        out.close();
        is.close();

        return (Runtime.getRuntime().exec("chmod " + mode + " " + destFilePath).waitFor() == 0);
    }

    /**
     *
     * @return dest file
     */
    public static File copyFile(File sourceFile, String destDir) {

        if (!isFileCorrect(sourceFile)) {
            throw new IllegalArgumentException("incorrect source file: "+ sourceFile);
        }

        File destFile = createNewFile(sourceFile.getName(), destDir);

        if (destFile == null) {
            throw new IllegalArgumentException("can't create dest file");
        }

        destFile = writeBytesToFile(readBytesFromFile(sourceFile), destFile.getName(), destFile.getParent(), false);

        if (destFile == null) {
            throw new IllegalArgumentException("can't write to dest file");
        }

        return destFile;
    }

}
