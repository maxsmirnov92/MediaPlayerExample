package ru.maxsmr.commonutils.android.gui.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.maxsmr.commonutils.data.CompareUtils;

public class CustomFragmentStatePagerAdapter extends FragmentStatePagerAdapter {

    public interface FragmentStateListener {
        void onFragmentAttach(Fragment f);

        void onFragmentDetach(Fragment f);
    }

    public static class FragmentPair {

        @NonNull
        public final Fragment fragment;

        @Nullable
        public final String title;

        public FragmentPair(@NonNull Fragment fragment, @Nullable String title) {
            this.fragment = fragment;
            this.title = title;
        }

        @Override
        public String toString() {
            return "FragmentPair{" +
                    "fragment=" + fragment +
                    ", title='" + title + '\'' +
                    '}';
        }
    }

    @NonNull
    protected final Context context;

    private final List<FragmentPair> fragments = new ArrayList<>();
    private final Map<Fragment, Boolean> fragmentStateMap = new HashMap<>();

    private FragmentStateListener listener;

    private boolean needToNotify = true;

    public CustomFragmentStatePagerAdapter(@NonNull Context ctx, @NonNull FragmentManager fm) {
        super(fm);
        context = ctx;
    }

    public void setNeedToNotify(boolean needToNotify) {
        this.needToNotify = needToNotify;
    }

    public void setFragmentStateListener(FragmentStateListener listener) {
        this.listener = listener;
    }

    private void rangeCheck(int index) {
        if (index < 0 && index >= fragments.size()) {
            throw new IndexOutOfBoundsException("incorrect fragment index: " + index);
        }
    }

    @Nullable
    public Fragment findFragmentByTitle(String title) {
        for (FragmentPair f : fragments) {
            if (CompareUtils.stringsEqual(f.title, title, false)) {
                return f.fragment;
            }
        }
        return null;
    }

    /** @return {@link FragmentStatePagerAdapter#POSITION_NONE} if not found */
    public int fragmentIndexOf(Fragment targetFragment) {
        if (targetFragment != null) {
            for (int i = 0; i < getCount(); i++) {
                if (getFragmentInstance(i).equals(targetFragment)) {
                    return i;
                }
            }
        }
        return POSITION_NONE;
    }

    @NonNull
    public FragmentPair getFragmentInfo(int at) {
        rangeCheck(at);
        return fragments.get(at);
    }

    @NonNull
    public Fragment getFragmentInstance(int at) {
        return getFragmentInfo(at).fragment;
    }

    @Nullable
    public String getFragmentTitle(int at) {
        return getFragmentInfo(at).title;
    }

    public void addFragments(List<FragmentPair> pairs) {
        if (pairs != null) {
            fragments.addAll(pairs);
            notifyDataSetChanged();
        }
    }

    public void addFragment(FragmentPair p) {
        fragments.add(p);
        notifyDataSetChanged();
    }

    public void addFragment(Fragment fragment, String title) {
        addFragment(new FragmentPair(fragment, title));
    }

    public void addFragment(Fragment fragment, @StringRes int titleRes) {
        addFragment(fragment, context.getString(titleRes));
    }

    public Fragment removeFragment(int at) {
        rangeCheck(at);
        Fragment removed = fragments.get(at).fragment;
        fragments.remove(at);
        notifyDataSetChanged();
        return removed;
    }

    public void clear() {
        fragments.clear();
        notifyDataSetChanged();
    }

    public final boolean isEmpty() {
        return getCount() == 0;
    }

    @Override
    public final int getCount() {
        return fragments.size();
    }

    @Override
    public final Fragment getItem(int position) {
        return getFragmentInstance(position);
    }

    @Override
    public final CharSequence getPageTitle(int position) {
        return getFragmentTitle(position);
    }

    @Override
    public int getItemPosition(Object object) {
        return POSITION_NONE;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        fragmentStateMap.put(fragments.get(position).fragment, true);
        return super.instantiateItem(container, position);
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        fragmentStateMap.put(fragments.get(position).fragment, false);
        super.destroyItem(container, position, object);
    }

    @Override
    public void notifyDataSetChanged() {
        if (needToNotify)
            super.notifyDataSetChanged();
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        super.finishUpdate(container);

        if (listener != null) {
            for (FragmentPair p : fragments) {
                boolean attached = fragmentStateMap.get(p.fragment);
                if (attached) {
                    listener.onFragmentAttach(p.fragment);
                } else {
                    listener.onFragmentDetach(p.fragment);
                }
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        fragments.clear();
    }
}
