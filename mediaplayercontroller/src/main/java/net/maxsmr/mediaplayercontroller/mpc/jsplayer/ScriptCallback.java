package net.maxsmr.mediaplayercontroller.mpc.jsplayer;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ScriptCallback {

    @JavascriptInterface
    void onScriptInsertDone();

//    @JavascriptInterface
//    void onMediaElementsInsertDone();

    final class Executor {

        private static final Logger logger = LoggerFactory.getLogger(Executor.class);

        @NonNull
        private final WebView webView;

//        private final String interfaceName;

        private boolean allowLogging = false;

        public Executor(@NonNull WebView webView/*, String interfaceName*/) {
//            if (TextUtils.isEmpty(interfaceName)) {
//                throw new IllegalArgumentException("no interface name defined");
//            }
            this.webView = webView;
//            this.interfaceName = interfaceName;
        }

        public void setAllowLogging(boolean allowLogging) {
            this.allowLogging = allowLogging;
        }

        /**
         *
         * @param script must be already declared JS function or JS code (without function() {} or <script><script/>)
//         * @param postCommand optional command to run
         */
        public void execute(final String script/*, @Nullable final Runnable postCommand*/) {

            if (TextUtils.isEmpty(script)) {
                if (allowLogging) {
                    logger.error("script is empty");
                }
                return;
            }

            final Runnable run = new Runnable() {
                @Override
                public void run() {
//                    if (allowLogging) {
//                        logger.debug("adding interface " + interfaceName  + "...");
//                    }
//                    webView.addJavascriptInterface(new ScriptCallback() {
//                        @Override
//                        @JavascriptInterface
//                        public void onDone() {
//                            if (allowLogging) {
//                                logger.debug("onDone() callback received");
//                            }
//                            webView.post(new Runnable() {
//                                @Override
//                                public void run() {
//                                    if (allowLogging) {
//                                        logger.debug("removing interface " + interfaceName  + "...");
//                                    }
//                                    webView.removeJavascriptInterface(interfaceName);
//                                    if (postCommand != null) {
//                                        if (allowLogging) {
//                                            logger.debug("running post command...");
//                                        }
//                                        postCommand.run();
//                                    }
//                                }
//                            });
//                        }
//                    }, interfaceName);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        if (allowLogging) {
                            logger.debug("executing script via evaluateJavascript()...");
                        }
                        webView.evaluateJavascript(script, null);
                    } else {
                        if (allowLogging) {
                            logger.debug("executing script via loadUrl()...");
                        }
                        webView.loadUrl("javascript:" + script);
                    }

                }
            };
            if (Looper.getMainLooper() == Looper.myLooper()) {
                run.run();
            } else {
                new Handler(Looper.getMainLooper()).post(run);
            }
        }
    }
}
