package ru.maxsmr.commonutils.data.sort;

import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbsOptionableComparator<O extends ISortOption, T> implements Comparator<T> {

    @NonNull
    private final Set<SortOptionPair<O>> sortOptionPairs;

    @SafeVarargs
    protected AbsOptionableComparator(SortOptionPair<O>... sortOptionPairs) {
        this.sortOptionPairs = sortOptionPairs != null? new LinkedHashSet<>(Arrays.asList(sortOptionPairs)) : new LinkedHashSet<SortOptionPair<O>>();
    }

    @NonNull
    public Set<SortOptionPair<O>> getSortOptionPairs() {
        return new LinkedHashSet<>(sortOptionPairs);
    }
}
