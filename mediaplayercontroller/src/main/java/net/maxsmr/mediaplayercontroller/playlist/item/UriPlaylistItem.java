package net.maxsmr.mediaplayercontroller.playlist.item;

import net.maxsmr.commonutils.data.CompareUtils;
import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.maxsmr.commonutils.data.CompareUtils.compareForNull;

public class UriPlaylistItem<I extends UriPlaylistItem> extends BasePlaylistItem<I> {

    @SuppressWarnings("unchecked")
    public static final UriItemComparator defaultComparator;

    static {
        Map<UriItemSortOption, Boolean> map = new LinkedHashMap<>();
        map.put(new UriItemSortOption(UriItemSortOption.URI), true);
        map.put(new UriItemSortOption(UriItemSortOption.PLAY_MODE), true);
        map.put(new UriItemSortOption(UriItemSortOption.DURATION), true);
        defaultComparator = new UriItemComparator(map);
    }

    /**
     * may be full uri or file path
     */
    public final String uri;

    public UriPlaylistItem(@NotNull BaseMediaPlayerController.PlayMode playMode, long duration, boolean isLooping, String uri) {
        super(playMode, duration, isLooping);
//        if (StringUtils.isEmpty(track)) {
//            throw new IllegalArgumentException("empty track: " + track);
//        }
        this.uri = uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UriPlaylistItem that = (UriPlaylistItem) o;

        return uri != null ? uri.equals(that.uri) : that.uri == null;

    }

    @Override
    public int hashCode() {
        return uri != null ? uri.hashCode() : 0;
    }

    @NotNull
    @Override
    public String toString() {
        return "UriPlaylistItem{" +
                "track='" + uri + '\'' +
                ", super=" + super.toString() +
                '}';
    }

    @Nullable
    @Override
    protected Comparator<I> getDefaultComparator() {
        return defaultComparator;
    }

    public static class UriItemSortOption extends ItemSortOption {

        public static final String URI = "URI";

        public UriItemSortOption(String name) {
            super(name);
        }
    }

    public static class UriItemComparator<O extends UriItemSortOption, I extends UriPlaylistItem<I>> extends ItemComparator<O, I> {

        protected UriItemComparator(Map<O, Boolean> sortOptionMap) {
            super(sortOptionMap);
        }

        @Override
        protected int compare(I lhs, I rhs, @NotNull O option, boolean ascending) {
            int result = compareForNull(lhs, rhs, ascending);
            if (result != 0) {
                return result;
            }
            switch (option.getName()) {
                case UriItemSortOption.URI:
                    result = CompareUtils.compareStrings(lhs != null && lhs.uri != null ? lhs.uri : null, rhs != null && rhs.uri != null ? rhs.uri : null, ascending, true);
                    break;
                default:
                    result = super.compare(lhs, rhs, option, ascending);
                    break;
            }
            return result;
        }
    }
}
