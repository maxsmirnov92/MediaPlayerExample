package ru.maxsmr.commonutils.android.gui.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.DimenRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import ru.maxsmr.commonutils.R;

/**
 * @author maxsmirnov
 */
public class ViewPagerIndicator extends LinearLayout implements ViewPager.OnPageChangeListener {

    private ViewPager mViewPager;

    private Drawable mNormalDrawable = new ColorDrawable(Color.WHITE);
    private Drawable mSelectedDrawable = new ColorDrawable(Color.BLACK);

    private int mIndicatorMarginPx = 0;

    private boolean mAllowDisplaySingle = true;

    // TODO Animation

    public ViewPagerIndicator(Context context) {
        super(context);
        if (!isInEditMode()) {
            init(null);
        }
    }

    public ViewPagerIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            init(attrs);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public ViewPagerIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!isInEditMode()) {
            init(attrs);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ViewPagerIndicator(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (!isInEditMode()) {
            init(attrs);
        }
    }

    private void init(AttributeSet attrs) {
        if (attrs != null) {

            Drawable normalDrawable = null;
            Drawable selectedDrawable = null;
            int indicatorMarginPx = 0;
            boolean allowDisplaySingle = false;

            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ViewPagerIndicator, 0, 0);
            try {
                normalDrawable = a.getDrawable(R.styleable.ViewPagerIndicator_normal_drawable);
                selectedDrawable = a.getDrawable(R.styleable.ViewPagerIndicator_selected_drawable);
                indicatorMarginPx = a.getDimensionPixelSize(R.styleable.ViewPagerIndicator_margin_size, 0);
                allowDisplaySingle = a.getBoolean(R.styleable.ViewPagerIndicator_allow_display_single, false);
            } finally {
                a.recycle();
            }

            if (normalDrawable != null) {
                setNormalDrawable(normalDrawable);
            }
            if (selectedDrawable != null) {
                setSelectedDrawable(selectedDrawable);
            }
            if (indicatorMarginPx > 0) {
                setIndicatorMarginPx(indicatorMarginPx);
            }
            setAllowDisplaySingle(allowDisplaySingle);
        }
    }

    public void setNormalDrawableRes(@DrawableRes int normalDrawableId) {
        setNormalDrawable(ContextCompat.getDrawable(getContext(), normalDrawableId));
    }

    public void setNormalDrawable(Drawable normalDrawable) {
        if (normalDrawable != this.mNormalDrawable) {
            if (mNormalDrawable == null) {
                throw new NullPointerException("drawable can't be null");
            }
            this.mNormalDrawable = normalDrawable;
            if (mViewPager != null) {
                notifyDataSetChanged();
            }
        }
    }

    public void setSelectedDrawableRes(@DrawableRes int selectedDrawableId) {
        setSelectedDrawable(ContextCompat.getDrawable(getContext(), selectedDrawableId));
    }

    public void setSelectedDrawable(Drawable selectedDrawable) {
        if (selectedDrawable != this.mSelectedDrawable) {
            if (selectedDrawable == null) {
                throw new NullPointerException("drawable can't be null");
            }
            this.mSelectedDrawable = selectedDrawable;
            if (mViewPager != null) {
                notifyDataSetChanged();
            }
        }
    }

    public void setIndicatorMarginDimenRes(@DimenRes int indicatorMarginResId) {
        setIndicatorMarginPx(getResources().getDimensionPixelSize(indicatorMarginResId));
    }

    public void setIndicatorMarginPx(int indicatorMarginPx) {
        if (indicatorMarginPx != mIndicatorMarginPx) {
            if (indicatorMarginPx < 0) {
                throw new IllegalArgumentException("incorrect indicatorMarginPx: " + indicatorMarginPx);
            }
            this.mIndicatorMarginPx = indicatorMarginPx;
            if (mViewPager != null) {
                notifyDataSetChanged();
            }
        }
    }

    public void setAllowDisplaySingle(boolean toggle) {
        if (mAllowDisplaySingle != toggle) {
            mAllowDisplaySingle = toggle;
            if (mViewPager != null) {
                notifyDataSetChanged();
            }
        }
    }

    public void setViewPager(@Nullable ViewPager viewPager) {
        if (viewPager != mViewPager) {
            if (mViewPager != null) {
                mViewPager.removeOnPageChangeListener(this);
            }
            mViewPager = viewPager;
            if (mViewPager != null) {
                mViewPager.addOnPageChangeListener(this);
                notifyDataSetChanged();
            } else {
                clear();
            }
        }
    }

    private void clear() {
        if (getChildCount() > 0) {
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).setOnClickListener(null);
            }
        }
        removeAllViews();
    }

    private void populate() {

        if (mViewPager == null) {
            throw new IllegalStateException("no " + ViewPager.class.getSimpleName() + " specified");
        }

        clear();

        final int adapterCount = mViewPager.getAdapter().getCount();
        if (adapterCount > 1 || mAllowDisplaySingle) {
            for (int i = 0; i < adapterCount; i++) {
                ImageView iv = new ImageView(getContext());
                LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int margin = mIndicatorMarginPx; // getResources().getDimensionPixelSize(R.dimen.space_16);
                lp.setMargins(margin, margin, margin, margin);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iv.setLayoutParams(lp);
                final int itemPosition = i;
                iv.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mViewPager.setCurrentItem(itemPosition);
                    }
                });
                addView(iv, lp);
            }
        }
        invalidateByCurrentItem();
    }

    private void invalidateByCurrentItem() {

        if (mViewPager == null) {
            throw new IllegalStateException("no " + ViewPager.class.getSimpleName() + " specified");
        }

        final int adapterCount = mViewPager.getAdapter().getCount();
        final int childCount = getChildCount();

        if (!mAllowDisplaySingle && adapterCount == 1 && childCount == 0) {
            return;
        }

        if (childCount != mViewPager.getAdapter().getCount()) {
            throw new IllegalStateException("notifyDataSetChanged() was not called");
        }

        for (int i = 0; i < childCount; i++) {
            boolean selected = mViewPager.getCurrentItem() == i;
            ImageView imageView = (ImageView) getChildAt(i);
            imageView.setImageDrawable(selected ? mSelectedDrawable : mNormalDrawable);
        }
    }

    public void notifyDataSetChanged() {
        populate();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        invalidateByCurrentItem();
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        setViewPager(null);
    }
}
