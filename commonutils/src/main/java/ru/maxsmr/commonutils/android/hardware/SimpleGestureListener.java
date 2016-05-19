package ru.maxsmr.commonutils.android.hardware;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class SimpleGestureListener implements ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {

    private static final Logger logger = LoggerFactory.getLogger(SimpleGestureListener.class);

    public interface ScaleFactorChangeListener {
        void onScaleFactorChanged(float from, float to);
    }

    public static final float MIN_SCALE_FACTOR = 1.0f;

    private final List<ScaleFactorChangeListener> listeners = new LinkedList<>();

    private final ScaleGestureDetector scaleGestureDetector;

    private float thresholdValue = MIN_SCALE_FACTOR;

    private float previousScaleFactor = MIN_SCALE_FACTOR, currentScaleFactor = previousScaleFactor;

    private boolean isScaling = false;

    public SimpleGestureListener(@NonNull Context ctx, float thresholdValue) {
        scaleGestureDetector = new ScaleGestureDetector(ctx, this);
        setThresholdValue(thresholdValue);
    }

    public void addScaleFactorChangeListener(@NonNull ScaleFactorChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeScaleFactorChangeListener(@NonNull ScaleFactorChangeListener listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    public float getThresholdValue() {
        return thresholdValue;
    }

    /**
     * @param thresholdValue if {@link #MIN_SCALE_FACTOR} set, threshold will be ignored
     */
    public boolean setThresholdValue(float thresholdValue) {
        if (!isScaling) {
            if (thresholdValue < MIN_SCALE_FACTOR) {
                throw new IllegalArgumentException("incorrect threshold value: " + thresholdValue);
            }
            if (Float.compare(previousScaleFactor, thresholdValue) > 0 || Float.compare(currentScaleFactor, thresholdValue) > 0) {
                this.previousScaleFactor = this.currentScaleFactor = MIN_SCALE_FACTOR;
            }
            this.thresholdValue = thresholdValue;
            return true;
        }
        return false;
    }

    /**
     * @return {previous : current} pair
     */
    @NonNull
    public Pair<Float, Float> getScaleFactors() {
        return new Pair<>(previousScaleFactor, currentScaleFactor);
    }

    public boolean setTo(float scale) {
        if (!isScaling) {
            if (Float.compare(scale, MIN_SCALE_FACTOR) >= 0 && (Float.compare(thresholdValue, MIN_SCALE_FACTOR) == 0 || Float.compare(scale, thresholdValue) <= 0)) {
                previousScaleFactor = currentScaleFactor = scale;
            } else {
                throw new IllegalArgumentException("incorrect scale factor value: " + scale);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        logger.debug("onScale()");

        float newScaleFactor;
        newScaleFactor = currentScaleFactor * detector.getScaleFactor();
        newScaleFactor = (newScaleFactor < MIN_SCALE_FACTOR ? MIN_SCALE_FACTOR : newScaleFactor); // prevent our view from becoming too small //
        newScaleFactor = ((float) ((int) (newScaleFactor * 100f))) / 100f; // Change precision to help with jitter when user just rests their fingers

        if (Float.compare(newScaleFactor, thresholdValue) <= 0) {
            previousScaleFactor = currentScaleFactor;
            currentScaleFactor = newScaleFactor;

            synchronized (listeners) {
                for (ScaleFactorChangeListener l : listeners) {
                    l.onScaleFactorChanged(previousScaleFactor, currentScaleFactor);
                }
            }
        }

        isScaling = false;
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return isScaling = true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        logger.debug("onScaleEnd()");
//            logger.debug("previous span: " + detector.getPreviousSpan() + ", current span: " + detector.getCurrentSpan() + ", scale factor: " + detector.getScaleFactor());
        logger.debug("current scale factor: " + currentScaleFactor + ", previous scale factor: " + previousScaleFactor);
        isScaling = false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        return true;
    }
}