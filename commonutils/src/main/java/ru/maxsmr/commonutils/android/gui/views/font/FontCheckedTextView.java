package ru.maxsmr.commonutils.android.gui.views.font;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.support.v7.widget.AppCompatCheckedTextView;
import android.util.AttributeSet;

import ru.maxsmr.commonutils.R;
import ru.maxsmr.commonutils.android.gui.fonts.FontsHolder;


public class FontCheckedTextView extends AppCompatCheckedTextView implements ITypefaceChangeable {

    public FontCheckedTextView(Context context) {
        this(context, null);
    }

    public FontCheckedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FontCheckedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!isInEditMode()) {
            setTypefaceByName(getTypeFaceNameFromAttrs(attrs));
        }
    }

    private String getTypeFaceNameFromAttrs(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.FontCheckedTextView, 0, 0);
            try {
                return a.getString(R.styleable.FontCheckedTextView_custom_typeface);
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
