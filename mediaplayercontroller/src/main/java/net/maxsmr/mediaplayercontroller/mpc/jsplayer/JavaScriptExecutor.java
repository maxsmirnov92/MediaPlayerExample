package net.maxsmr.mediaplayercontroller.mpc.jsplayer;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import net.maxsmr.commonutils.logger.BaseLogger;
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;

public class JavaScriptExecutor {

    private static final BaseLogger logger = BaseLoggerHolder.getInstance().getLogger(Executor.class);

    public static void execute(@NotNull WebView webView, final String script) {
        execute(webView, script, null, false);
    }

    /**
     * @param script must be already declared JS function or JS code (without function() {} or <script><script/>)
     */
    public static void execute(@NotNull WebView webView, @Nullable final String script, @Nullable ValueCallback<String> callback, boolean useLoadUr) {

        if (TextUtils.isEmpty(script)) {
            logger.e("script is empty");
            return;
        }

        final Runnable run = () -> {
            if (!useLoadUr && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                logger.d("executing script via evaluateJavascript()...");
                webView.evaluateJavascript(script, callback);
            } else {
                logger.d("executing script via loadUrl()...");
                webView.loadUrl("javascript:" + script);
            }

        };
        final Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == Looper.myLooper()) {
            run.run();
        } else {
            new Handler(mainLooper).post(run);
        }
    }
}
