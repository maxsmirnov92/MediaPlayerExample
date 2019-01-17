package net.maxsmr.mediaplayercontroller.mpc.jsplayer;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.jetbrains.annotations.NotNull;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import net.maxsmr.commonutils.logger.BaseLogger;
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder;

import org.jetbrains.annotations.NotNull;


public interface ScriptCallback {

    @JavascriptInterface
    void onScriptInsertDone();

    final class Executor {

        private static final BaseLogger logger = BaseLoggerHolder.getInstance().getLogger(Executor.class);

        @NotNull
        private final WebView webView;

        public Executor(@NotNull WebView webView/*, String interfaceName*/) {
            this.webView = webView;
        }

        /**
         * @param script must be already declared JS function or JS code (without function() {} or <script><script/>)
         */
        public void execute(final String script) {

            if (TextUtils.isEmpty(script)) {
                logger.e("script is empty");
                return;
            }

            final Runnable run = () -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    logger.d("executing script via evaluateJavascript()...");
                    webView.evaluateJavascript(script, null);
                } else {
                    logger.d("executing script via loadUrl()...");
                    webView.loadUrl("javascript:" + script);
                }

            };
            final Looper looper = Looper.getMainLooper();
            if (looper == Looper.myLooper()) {
                run.run();
            } else {
                new Handler(looper).post(run);
            }
        }
    }
}
