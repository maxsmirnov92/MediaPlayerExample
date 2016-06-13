package ru.maxsmr.commonutils.android.gui.drawables;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.maxsmr.commonutils.android.gui.fonts.FontsHolder;
import ru.maxsmr.commonutils.graphic.GraphicUtils;

public class NumberDrawable extends BitmapDrawable {

    private static final Logger logger = LoggerFactory.getLogger(NumberDrawable.class);

    private final Context mContext;

    public interface Defaults {
        Typeface DEFAULT_FONT = Typeface.create((String) null, Typeface.BOLD);
        int DEFAULT_TEXT_COLOR = Color.BLACK;
        int DEFAULT_TEXT_ALPHA = 0xBB;
        int DEFAULT_TEXT_SIZE_DP = 18;
    }

    public interface NumberSetter {
        int getNumber();
    }

    private NumberSetter mSetter;

    public NumberDrawable setNumberSetter(NumberSetter setter) {
        mSetter = setter;
        return this;
    }

    public NumberDrawable(Context ctx, @DrawableRes int sourceDrawableResId, @ColorInt int textColor, int textAlpha, String fontAlias) {
        this(ctx, GraphicUtils.createBitmap(GraphicUtils.createBitmapFromResource(sourceDrawableResId, 1, ctx), Bitmap.Config.ARGB_8888), textColor, textAlpha, fontAlias);
    }

    public NumberDrawable(Context ctx, Bitmap sourceBitmap, @ColorInt int textColor, int textAlpha, String fontAlias) {
        super(ctx.getResources(), sourceBitmap);
        mContext = ctx;
        needInvalidate = false;
        setSource(sourceBitmap);
        setTextColor(textColor);
        setTextAlpha(textAlpha);
        setFontByAlias(fontAlias);
        needInvalidate = true;
    }

    private boolean needInvalidate = true;

    private Bitmap mSource = null;

    /**
     * @param source may be immutable
     */
    private void setSource(Bitmap source) {
        if (mSource != source) {
            if (GraphicUtils.isBitmapCorrect(source)) {
                mSource = source;
                if (needInvalidate)
                    invalidateSelf();
            } else {
                logger.error("incorrect source bitmap: " + source);
            }
        }
    }


    private Typeface mFont = Defaults.DEFAULT_FONT;

    public void setFontByAlias(String alias) {
        if (!TextUtils.isEmpty(alias)) {
            Typeface font = FontsHolder.getInstance().getFont(alias);
            if (!mFont.equals(font)) {
                if (font != null) {
                    mFont = font;
                    if (needInvalidate)
                        invalidateSelf();
                } else {
                    logger.error("no loaded font with alias: " + alias);
                }
            }
        }
    }

    public void setFont(Typeface font) {
        if (mFont != font) {
            if (font != null) {
                mFont = font;
                if (needInvalidate)
                    invalidateSelf();
            } else {
                logger.error("font is null");
            }
        }
    }

    private int mTextColor = Defaults.DEFAULT_TEXT_COLOR;

    private void setTextColor(@ColorInt int textColor) {
        if (mTextColor != textColor) {
            mTextColor = textColor;
            if (needInvalidate)
                invalidateSelf();
        }
    }

    private int mTextAlpha = Defaults.DEFAULT_TEXT_ALPHA;

    private void setTextAlpha(int textAlpha) {
        if (mTextAlpha != textAlpha) {
            if (textAlpha >= 0 && textAlpha <= 0xFF) {
                mTextAlpha = textAlpha;
                if (needInvalidate)
                    invalidateSelf();
            }
        }
    }

    private int mNumber = 0;

    public void setNumber(int number) {
        logger.debug("setNumber(), number=" + number);
        if (mNumber != number) {
            mNumber = number;
            if (needInvalidate)
                invalidateSelf();
        }
    }

    private int mTextSizeDP = Defaults.DEFAULT_TEXT_SIZE_DP;

    public void setTextSizeDp(int textSizeDP) {
        if (mTextSizeDP != textSizeDP) {
            if (textSizeDP > 0) {
                mTextSizeDP = textSizeDP;
                invalidateSelf();
            } else {
                logger.error("incorrect textSizeDP: " + textSizeDP);
            }
        }
    }

    private int getTextSizeByDensity() {
//        DisplayMetrics displayMetrics = new DisplayMetrics();
//        ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displayMetrics);
//        int dpi = (int) (displayMetrics.density * DisplayMetrics.DENSITY_MEDIUM);
        return GraphicUtils.dpToPx(mTextSizeDP, mContext);
    }

    private final Paint mPaint = new Paint();

    {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public int getAlpha() {
        return super.getAlpha();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (GraphicUtils.isBitmapCorrect(mSource)) {
//            canvas.drawBitmap(mSource, 0, 0, mPaint);
            mPaint.setColor(mTextColor);
            mPaint.setAlpha(mTextAlpha);
            mPaint.setTypeface(mFont);
            mPaint.setTextSize(GraphicUtils.fixFontSize(getTextSizeByDensity(), String.valueOf(mNumber), mPaint, mSource)); // getBitmap()
            mNumber = mSetter != null ? mSetter.getNumber() : mNumber;
//            logger.debug("drawing text " + String.valueOf(mNumber) + "...");
            canvas.drawText(String.valueOf(mNumber), 0, 0, mPaint);
        } else {
            throw new RuntimeException("can't draw: source bitmap is incorrect");
        }
    }

//    @Override
//    public void setColorFilter(ColorFilter colorFilter) {
//        mPaint.setColorFilter(colorFilter);
//        invalidateSelf();
//    }

}
