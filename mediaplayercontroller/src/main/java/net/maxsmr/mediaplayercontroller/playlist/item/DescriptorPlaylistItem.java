package net.maxsmr.mediaplayercontroller.playlist.item;


import android.content.res.AssetFileDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public class DescriptorPlaylistItem<I extends DescriptorPlaylistItem> extends AbsPlaylistItem<I> {

    @SuppressWarnings("unchecked")
    public static final ItemComparator defaultComparator;

    static {
        Map<ItemSortOption, Boolean> map = new LinkedHashMap<>();
        map.put(new DescriptorPlaylistItem.ItemSortOption(DescriptorPlaylistItem.ItemSortOption.PLAY_MODE), true);
        map.put(new DescriptorPlaylistItem.ItemSortOption(DescriptorPlaylistItem.ItemSortOption.DURATION), true);
        //noinspection unchecked
        defaultComparator = new ItemComparator<>(map);
    }

    public final AssetFileDescriptor descriptor;

    public DescriptorPlaylistItem(@NotNull BaseMediaPlayerController.PlayMode playMode, long duration , boolean isLooping, AssetFileDescriptor descriptor) {
        super(playMode, duration, isLooping);
        this.descriptor = descriptor;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        DescriptorPlaylistItem that = (DescriptorPlaylistItem) object;

        return descriptor != null ? descriptor.equals(that.descriptor) : that.descriptor == null;

    }

    @Override
    public int hashCode() {
        return descriptor != null ? descriptor.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "DescriptorPlaylistItem{" +
                "descriptor=" + descriptor +
                ", super=" + super.toString() +
                '}';
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    protected Comparator<I> getDefaultComparator() {
        return defaultComparator;
    }
}
