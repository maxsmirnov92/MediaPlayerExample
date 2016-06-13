package ru.maxsmr.commonutils.data.sort;

public class SortOptionPair<O extends ISortOption> {

    public final O option;
    public final boolean ascending;

    public SortOptionPair(O option, boolean ascending) {
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