package net.maxsmr.mediaplayercontroller.playlist.item;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.maxsmr.commonutils.data.CompareUtils;
import net.maxsmr.commonutils.data.sort.AbsOptionableComparator;
import net.maxsmr.commonutils.data.sort.ISortOption;
import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;

import java.util.Comparator;
import java.util.Map;

import static net.maxsmr.commonutils.data.CompareUtils.compareForNull;

public abstract class AbsPlaylistItem<I extends AbsPlaylistItem> implements Comparable<I> {

    public static final int DURATION_NOT_SPECIFIED = 0;

    @NonNull
    public final BaseMediaPlayerController.PlayMode playMode;

    /** in millis */
    public final long duration;

    public final boolean isLooping;

    protected AbsPlaylistItem(@NonNull BaseMediaPlayerController.PlayMode playMode, long duration, boolean isLooping) {
        if (playMode == BaseMediaPlayerController.PlayMode.NONE) {
            throw new IllegalArgumentException("playMode cannot be " + playMode);
        }
        if (duration < 0) {
            throw new IllegalArgumentException("duration < 0");
        }
        this.playMode = playMode;
        this.duration = duration;
        this.isLooping = isLooping;
    }

    @Nullable
    protected abstract Comparator<I> getDefaultComparator();

    @SuppressWarnings("unchecked")
    @Override
    public final int compareTo(@NonNull I another) {
        Comparator<I> comparator = getDefaultComparator();
        return comparator != null? comparator.compare((I) this, another) : 0;
    }

    @Override
    public String toString() {
        return "AbsPlaylistItem{" +
                "playMode=" + playMode +
                ", duration=" + duration +
                ", isLooping=" + isLooping +
                '}';
    }

    public static class ItemSortOption implements ISortOption {

        public static final String PLAY_MODE = "play_mode";

        public static final String DURATION = "duration";

        public final String name;

        public ItemSortOption(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ItemSortOption that = (ItemSortOption) o;

            return name != null ? name.equals(that.name) : that.name == null;

        }

        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "ItemSortOption{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    public static class ItemComparator<O extends ItemSortOption, I extends AbsPlaylistItem<I>> extends AbsOptionableComparator<O, I> {

        public ItemComparator(Map<O, Boolean> sortOptions) {
            super(sortOptions);
        }

        protected int compare(@Nullable I lhs, @Nullable I rhs, @NonNull O option, boolean ascending) {
            int result = compareForNull(lhs, rhs, ascending);
            if (result != 0) {
                return result;
            }
            switch (option.getName()) {
                case ItemSortOption.PLAY_MODE:
                    result = CompareUtils.compareStrings(lhs != null? lhs.playMode.toString() : null, rhs != null? rhs.playMode.toString() : null, ascending, true);
                    break;
                case ItemSortOption.DURATION:
                    result = CompareUtils.compareLongs(lhs != null? lhs.duration : null, rhs != null? rhs.duration : null, ascending);
                    break;
                default:
                    throw new RuntimeException("unknown option: " + option);
            }
            return result;
        }
    }
}
