package ru.maxsmr.commonutils.android.gui.drawables;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v4.view.ViewPager;

/**
 * Yay, indicator.
 * <p/>
 * Created at 1:17 on 30.12.14
 *
 * @author cab404
 */
class PagerIndicatorDrawable extends Drawable implements ViewPager.OnPageChangeListener {

    public static final int DEFAULT_NORMAL_COLOR = Color.WHITE;
    public static final int DEFAULT_SELECTED_COLOR = 0xff23369a;

    private int position = 0;
    private float positionOffset = 0;
    private int count = 0;

    private final Paint paint = new Paint();

    @ColorInt
    private int selectedColor = DEFAULT_SELECTED_COLOR;

    @ColorInt
    private int normalColor = DEFAULT_NORMAL_COLOR;

    public void setSelectedColor(@ColorInt int selectedColor) {
        if (selectedColor != this.selectedColor) {
            this.selectedColor = selectedColor;
            invalidateSelf();
        }
    }

    public void setNormalColor(@ColorInt int normalColor) {
        if (normalColor != this.normalColor) {
            this.normalColor = normalColor;
            invalidateSelf();
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        this.position = position;
        this.positionOffset = positionOffset;
        invalidateSelf();
    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    public void setPageCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("incorrect count: " + count);
        }
        if (count != this.count) {
            this.count = count;
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        int r = ch / 2;
        int dst = r / 2;
        int c = count;
        int start = cw / 2 - (r * 2 * c + dst * (c - 1)) / 2 + r;
        float psize = 0.7f;

        paint.setColor(normalColor);
        for (int i = 0; i < c; i++)
            canvas.drawCircle(start + (r * 2 * i + dst * (i - 1)), r, r, paint);
        paint.setColor(selectedColor);
        canvas.drawCircle(start + (r * 2 * position + dst * (position - 1)) + ((r * 2 + dst) * positionOffset), r, r * psize, paint);
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
