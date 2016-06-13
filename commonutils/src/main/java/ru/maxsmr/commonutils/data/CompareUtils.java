package ru.maxsmr.commonutils.data;

import android.support.annotation.Nullable;

import java.util.Date;

public class CompareUtils {

    public static <O extends Object> boolean objectsEqual(@Nullable O one, @Nullable O another) {
        return one != null ? one.equals(another) : another == null;
    }

    public static boolean charsEqual(@Nullable Character one, @Nullable Character another, boolean ignoreCase) {
        if (ignoreCase) {
            one = one != null ? Character.toUpperCase(one) : null;
            another = another != null ? Character.toUpperCase(another) : null;
        }
        return one != null ? one.equals(another) : another == null;
    }

    public static boolean stringsEqual(@Nullable String one, @Nullable String another, boolean ignoreCase) {
        return one != null ? (!ignoreCase ? one.equals(another) : one.equalsIgnoreCase(another)) : another == null;
    }

    public static int compareInts(@Nullable Integer one, @Nullable Integer another, boolean ascending) {
        return one != null ? (another != null ? !ascending ? (int) Math.signum(another - one) : (int) Math.signum(one - another) : 1) : -1;
    }

    public static int compareLongs(@Nullable Long one, @Nullable Long another, boolean ascending) {
        return one != null ? (another != null ? !ascending ? (int) Math.signum(another - one) : (int) Math.signum(one - another) : 1) : -1;
    }

    public static int compareFloats(@Nullable Float one, @Nullable Float another, boolean ascending) {
        return one != null ? (another != null ? !ascending ? (int) Math.signum(another - one) : (int) Math.signum(one - another) : 1) : -1;
    }

    public static int compareDouble(@Nullable Double one, @Nullable Double another, boolean ascending) {
        return one != null ? (another != null ? !ascending ? (int) Math.signum(another - one) : (int) Math.signum(one - another) : 1) : -1;
    }

    public static int compareChars(@Nullable Character one, @Nullable Character another, boolean ascending, boolean ignoreCase) {
        if (charsEqual(one, another, ignoreCase)) {
            return 0;
        }
        return one != null ? (another != null ? !ascending ? (int) Math.signum(another - one) : (int) Math.signum(one - another) : 1) : -1;
    }

    public static int compareStrings(@Nullable String one, @Nullable String another, boolean ascending, boolean ignoreCase) {
        if (stringsEqual(one, another, ignoreCase)) {
            return 0;
        }
        return one != null ? (another != null ? !ascending ? one.compareTo(another) : another.compareTo(one) : 1) : -1;
    }

    public static int compareDates(@Nullable Date one, @Nullable Date another, boolean ascending) {
        return compareLongs(one != null ? one.getTime() : 0, another != null ? another.getTime() : 0, ascending);
    }



}
