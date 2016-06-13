package ru.maxsmr.commonutils.android.gui.progressable;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.KeyEvent;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class DialogProgressable implements Progressable, DialogInterface.OnKeyListener, DialogInterface.OnDismissListener, DialogInterface.OnCancelListener {

    protected static final Logger logger = LoggerFactory.getLogger(DialogProgressable.class);

    @NonNull
    private final Context context;

    private ProgressDialog progressDialog;

//    @LayoutRes
//    private int customLayoutId = 0;

    //    private final String defaultTitle;
    private String title;

    //    private final String defaultMessage;
    private String message;


    private boolean indeterminate = true;
    private int max = 0;

    private boolean cancelable = false;

    private OnBackPressedListener backPressedListener;
    private DialogInterface.OnCancelListener cancelListener;
    private DialogInterface.OnDismissListener dismissListener;


    public DialogProgressable(@NonNull Context context) {
        this(context, null, null, true, 0, false);
    }

//    public DialogProgressable(@NonNull Context context, boolean indeterminate, int max, boolean cancelable) {
//        this(context, null, context.getString(R.string.loading), indeterminate, max, cancelable);
//    }

    public DialogProgressable(@NonNull Context context/*, @LayoutRes int customLayoutId, */, @Nullable String title, @Nullable String message, boolean indeterminate, int max, boolean cancelable) {
        this.context = context;
//        setCustomLayoutId(customLayoutId);
        setTitle(title);
        setMessage(message);
        setIndeterminate(indeterminate);
        setMax(max);
        setCancelable(cancelable);
    }

    public boolean isStarted() {
        return progressDialog != null && progressDialog.isShowing();
    }

    @MainThread
    private void begin() {
        if (!isStarted()) {
            progressDialog = new ProgressDialog(context);
            if (!indeterminate) {
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            } else {
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            }
            progressDialog.setIndeterminate(indeterminate);
            progressDialog.setMax(max);
            progressDialog.setCancelable(cancelable);
            progressDialog.setOnKeyListener(this);
            progressDialog.setOnCancelListener(this);
            progressDialog.setOnDismissListener(this);
            progressDialog.setTitle(title);
            progressDialog.setMessage(message);
            progressDialog.show();
        }
    }

    @MainThread
    private void end() {
        dismiss();
    }

    public void notifyProgress(int progress) {
        logger.debug("notifyProgress(), progress=" + progress);
        if (isStarted()) {
            if (!progressDialog.isIndeterminate()) {
                if (progress >= 0 && progress <= progressDialog.getMax()) {
                    progressDialog.setProgress(progress);
                } else {
                    throw new IllegalArgumentException("incorrect progress value: " + progress + " (max: " + progressDialog.getMax());
                }
            }
        }
    }

    @Nullable
    public ProgressBar getProgressBar() {
        if (!isStarted()) {
            throw new IllegalStateException("dialog was not started");
        }

        Field f = null;
        try {
            f = progressDialog.getClass().getDeclaredField("mProgress");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        if (f != null) {
            f.setAccessible(true);
            try {
                return (ProgressBar) f.get(progressDialog);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    @Nullable
    public TextView getMessageView() {

        if (!isStarted()) {
            throw new IllegalStateException("dialog was not started");
        }

        Field f = null;
        try {
            f = progressDialog.getClass().getDeclaredField("mMessageView");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        if (f != null) {
            f.setAccessible(true);
            try {
                return (TextView) f.get(progressDialog);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public int getMax() {
        return max;
    }

    public DialogProgressable setIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
        if (isStarted()) {
            progressDialog.setIndeterminate(indeterminate);
        }
        return this;
    }

    public DialogProgressable setMax(int max) {
        this.max = max < 0 ? 0 : max;
        if (isStarted()) {
            logger.debug("setting max: " + max);
            progressDialog.setMax(this.max);
        }
        return this;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public DialogProgressable setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        if (isStarted()) {
            progressDialog.setCancelable(cancelable);
        }
        return this;
    }

    public boolean cancel() {
        if (isStarted()) {
            if (cancelable) {
                progressDialog.cancel();
                return true;
            }
        }
        return false;
    }

    public String getTitle() {
        return title;
    }

    public DialogProgressable setTitle(String title) {
        this.title = title;
        if (isStarted()) {
            progressDialog.setTitle(title);
        }
        return this;
    }

    public String getMessage() {
        return message;
    }

    public DialogProgressable setMessage(String message) {
        this.message = message;
        if (isStarted()) {
            progressDialog.setMessage(message);
        }
        return this;
    }

    public DialogProgressable setOnBackPressedListener(OnBackPressedListener listener) {
        this.backPressedListener = listener;
        return this;
    }

    public DialogProgressable setOnCancelListener(DialogInterface.OnCancelListener listener) {
        this.cancelListener = listener;
        return this;
    }

    public DialogProgressable setOnDismissListener(DialogInterface.OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    private void dismiss() {
        if (isStarted()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @Override
    @MainThread
    public void onStart() {
        begin();
    }

    @Override
    @MainThread
    public void onStop() {
        end();
    }

    public interface OnBackPressedListener {
        void onBackPressed(DialogInterface dialog);
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        logger.debug("onKey(), dialog=" + dialog + ", keyCode=" + keyCode + ", event=" + event);
        if (keyCode == KeyEvent.KEYCODE_BACK && dialog != null) {
            if (backPressedListener != null) {
                backPressedListener.onBackPressed(dialog);
            }
        }
        return false;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        logger.debug("onDismiss(), dialog=" + dialog);
        if (dismissListener != null) {
            dismissListener.onDismiss(dialog);
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        logger.debug("onCancel(), dialog=" + dialog);
        if (cancelListener != null) {
            cancelListener.onCancel(dialog);
        }
    }
}
