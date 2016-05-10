package ru.altarix.commonutils.data;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class ChecksumHelper {

    private final static Logger logger = LoggerFactory.getLogger(FileHelper.class);

    public static final int MD5_HASH_CHARS_COUNT = 32;

    public static String getMD5Hash(byte[] encData) {

        if (encData == null || encData.length == 0) {
            logger.error("data is null or empty");
            return null;
        }

        MessageDigest mdEnc = null;
        try {
            mdEnc = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            logger.error("a NoSuchAlgorithmException occurred during getInstance(): " + e.getMessage());
            return null;
        }
        mdEnc.update(encData, 0, encData.length);
        String md5 = new BigInteger(1, mdEnc.digest()).toString(16);
        while (md5.length() < 32) {
            md5 = "0" + md5;
        }
        return md5.toUpperCase(Locale.getDefault());
    }

    @NonNull
    public static String md5Custom(@NonNull String st) {
        MessageDigest messageDigest = null;
        byte[] digest = new byte[0];
        try {
            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.reset();
            messageDigest.update(st.getBytes());
            digest = messageDigest.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        BigInteger bigInt = new BigInteger(1, digest);
        String md5Hex = bigInt.toString(16);
        while (md5Hex.length() < 32) {
            md5Hex = "0" + md5Hex;
        }
        return md5Hex;
    }

    /**
     * NOT COMPATIBLE WITH PHP VERSION!
     */
    public static String getMD5Hash(String str) {

        if (TextUtils.isEmpty(str)) {
            logger.error("str is null or empty");
            return null;
        }

        try {
            MessageDigest dig = MessageDigest.getInstance("MD5");
            return new String(Base64.encode(dig.digest(str.getBytes("UTF-8")), 0), "UTF-8");
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            logger.error("an Exception occurred", e);
            return null;
        }
    }


    public static String getMD5Hash(File file) {

        if (!FileHelper.isFileCorrect(file)) {
            logger.error("incorrect file: " + file);
            return null;
        }

        return getMD5Hash(FileHelper.readBytesFromFile(file));
    }


}
