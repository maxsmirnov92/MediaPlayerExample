package ru.altarix.commonutils.data.gson;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GsonHelper {

    private static final Logger logger = LoggerFactory.getLogger(GsonHelper.class);

    @Nullable
    public static <T extends Serializable> T fromJsonObjectString(@Nullable String jsonString, @NonNull Class<T> type) {
        logger.debug("fromJsonObjectString(), jsonString=" + jsonString + ", type=" + type);

        final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        try {
            return gson.fromJson(jsonString, type);
        } catch (Exception e) {
            logger.error("an Exception occurred during fromJson(): " + e.getMessage());
            return null;
        }
    }

    @NonNull
    public static <T extends Serializable> List<T> fromJsonArrayString(@Nullable String jsonString, @NonNull Class<T[]> type) {
        logger.debug("fromJsonArrayString(), jsonString=" + jsonString + ", type=" + type);

        final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        try {
            return new ArrayList<>(Arrays.asList(gson.fromJson(jsonString, type)));
        } catch (Exception e) {
            logger.error("an Exception occurred during fromJson(): " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @NonNull
    public static <T extends Serializable> String toJsonString(T... what) {
        logger.debug("toJsonString(), what=" + Arrays.toString(what));

        final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        try {
            return gson.toJson(what);
        } catch (Exception e) {
            logger.error("an Exception occurred during toJson(): " + e.getMessage());
            return "null";
        }
    }
}
