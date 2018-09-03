package net.maxsmr.mediaplayercontroller.mpc.jsplayer;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import net.maxsmr.commonutils.logger.BaseLogger;
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder;


public interface ScriptCallback {

    @JavascriptInterface
    void onScriptInsertDone();

//    @JavascriptInterface
//    void onMediaElementsInsertDone();

    final class Executor {

        private static final BaseLogger logger = BaseLoggerHolder.getInstance().getLogger(Executor.class);

        @NonNull
        private final WebView webView;

//        private final String interfaceName;

        public Executor(@NonNull WebView webView/*, String interfaceName*/) {
//            if (TextUtils.isEmpty(interfaceName)) {
//                throw new IllegalArgumentException("no interface name defined");
//            }
            this.webView = webView;
//            this.interfaceName = interfaceName;
        }

        /**
         * @param script must be already declared JS function or JS code (without function() {} or <script><script/>)
         *               //         * @param postCommand optional command to run
         */
        public void execute(final String script/*, @Nullable final Runnable postCommand*/) {

            if (TextUtils.isEmpty(script)) {
                logger.e("script is empty");
                return;
            }

            final Runnable run = new Runnable() {
                @Override
                public void run() {
//                    if (allowLogging) {
//                        logger.d("adding interface " + interfaceName  + "...");
//                    }
//                    webView.addJavascriptInterface(new ScriptCallback() {
//                        @Override
//                        @JavascriptInterface
//                        public void onDone() {
//                            if (allowLogging) {
//                                logger.d("onDone() callback received");
//                            }
//                            webView.post(new Runnable() {
//                                @Override
//                                public void run() {
//                                    if (allowLogging) {
//                                        logger.d("removing interface " + interfaceName  + "...");
//                                    }
//                                    webView.removeJavascriptInterface(interfaceName);
//                                    if (postCommand != null) {
//                                        if (allowLogging) {
//                                            logger.d("running post command...");
//                                        }
//                                        postCommand.run();
//                                    }
//                                }
//                            });
//                        }
//                    }, interfaceName);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        logger.d("executing script via evaluateJavascript()...");
                        webView.evaluateJavascript(script, null);
                    } else {
                        logger.d("executing script via loadUrl()...");
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
