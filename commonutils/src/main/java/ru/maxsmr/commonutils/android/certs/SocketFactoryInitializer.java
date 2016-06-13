package ru.maxsmr.commonutils.android.certs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public final class SocketFactoryInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SocketFactoryInitializer.class);

    public SocketFactoryInitializer() {
        throw new AssertionError("no instances.");
    }

    public static void init(@NonNull Context context, @NonNull Set<Pair<String, Integer>> certificateResIds) throws RuntimeException {
        Set<Pair<String, Certificate>> certificatePairs = new LinkedHashSet<>();
        certificatePairs.addAll(CertificateHelper.retrieveKeyStoreCertificates(CertificateHelper.KeyStores.KEYSTORE_ANDROID));
        for (Pair<String, Integer> certificateResIdPair : certificateResIds) {
            certificatePairs.add(new Pair<>(certificateResIdPair.first, CertificateHelper.generateCertificate(context, certificateResIdPair.second)));
        }
        initSocketFactory(certificatePairs, null, "SSL");
    }

    private static void initSocketFactory(@NonNull Set<Pair<String, Certificate>> certificatePairs, @Nullable String keystoreType, String protocol) throws RuntimeException {
        TrustManagerFactory tmf = CertificateHelper.getTrustManagerFactory(keystoreType, certificatePairs);
        HttpsURLConnection.setDefaultSSLSocketFactory(generateSocketFactory(protocol, tmf.getTrustManagers()));
    }

    private static SSLSocketFactory generateSocketFactory(String protocol, TrustManager[] trustManagers) throws RuntimeException {
        SSLContext sc;
        if (trustManagers != null && trustManagers.length > 0) {
            try {
                sc = SSLContext.getInstance(protocol);
                sc.init(null, trustManagers, null);
                return sc.getSocketFactory();
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                e.printStackTrace();
                throw new RuntimeException("can't initialize SSLContext", e);
            }
        } else {
            throw new IllegalArgumentException("trustManagers is null or empty");
        }
    }
}
