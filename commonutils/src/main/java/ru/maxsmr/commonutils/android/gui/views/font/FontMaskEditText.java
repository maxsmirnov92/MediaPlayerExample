package ru.maxsmr.commonutils.android.gui.views.font;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;

import ru.maxsmr.commonutils.R;
import ru.maxsmr.commonutils.android.gui.fonts.FontsHolder;
import ru.maxsmr.commonutils.android.gui.views.maskedettext.MaskedEditText;


public class FontMaskEditText extends MaskedEditText implements ITypefaceChangeable {

    public FontMaskEditText(Context context) {
        this(context, null);
    }

    public FontMaskEditText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FontMaskEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!isInEditMode()) {
            setTypefaceByName(getTypeFaceNameFromAttrs(attrs));
        }
    }

    private String getTypeFaceNameFromAttrs(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.FontMaskEditText, 0, 0);
            try {
                return a.getString(R.styleable.FontMaskEditText_custom_typeface);
            } finally {
                a.recycle();
            }
        }
        return null;
    }

    public void setTypefaceByName(String name) {
        Typeface typeface = FontsHolder.getInstance().getFont(name);
        if (typeface != null) {
            setTypeface(typeface);
        }
    }

}
