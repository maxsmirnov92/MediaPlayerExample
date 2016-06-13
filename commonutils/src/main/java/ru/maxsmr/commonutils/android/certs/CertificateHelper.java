package ru.maxsmr.commonutils.android.certs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.text.TextUtils;
import android.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.net.ssl.TrustManagerFactory;


public class CertificateHelper {

    private static final Logger logger = LoggerFactory.getLogger(CertificateHelper.class);

    public static Certificate generateCertificate(@NonNull Context context, @RawRes int certResId) throws RuntimeException {
        return generateCertificate(context.getResources().openRawResource(certResId));
    }

    public static Certificate generateCertificate(File file) throws RuntimeException {
        try {
            return generateCertificate(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("can't open file: " + file);
        }
    }

    @Nullable
    private static Certificate generateCertificate(@NonNull InputStream in) throws RuntimeException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate certificate;
            certificate = cf.generateCertificate(in);
            in.close();
            logger.debug("generated certificate: " + ((X509Certificate) certificate).getSubjectDN());
            return certificate;
        } catch (CertificateException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException("can't generate certificate", e);
        }
    }

    /**
     * @param keyStoreType type of keystore to initialize on
     * @param certificates first - alias, second - generated certificate
     * @return factory initialized specified keystore with initialized certificate entries
     */
    public static TrustManagerFactory getTrustManagerFactory(@Nullable String keyStoreType, @NonNull Set<Pair<String, Certificate>> certificates) throws RuntimeException {
        return getTrustManagerFactory(keyStoreType, null, null, certificates);
    }

    /**
     * @param keyStoreType               type of keystore to initialize on
     * @param keyStoreInitStream         input stream to initialize from
     * @param keyStoreInitStreamPassword password for keystore (if present)
     * @param certificates               first - alias, second - generated certificate
     * @return factory initialized specified keystore with initialized certificate entries
     */
    public static TrustManagerFactory getTrustManagerFactory(@Nullable String keyStoreType, @Nullable InputStream keyStoreInitStream, @Nullable String keyStoreInitStreamPassword, @NonNull Set<Pair<String, Certificate>> certificates) throws RuntimeException {
        try {

            keyStoreType = TextUtils.isEmpty(keyStoreType) ? KeyStore.getDefaultType() : keyStoreType;
            logger.debug("keyStoreType=" + keyStoreType);
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(keyStoreInitStream, !TextUtils.isEmpty(keyStoreInitStreamPassword) ? keyStoreInitStreamPassword.toCharArray() : null);

            for (Pair<String, Certificate> certificate : certificates) {
                if (certificate.second == null) {
                    throw new IllegalArgumentException("certificate with alias " + certificate.first + " is null");
                }
                // throwing UnsupportedOperationException if writing is not supported
                keyStore.setCertificateEntry(certificate.first, certificate.second);
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            // Initialise the TMF as you normally would, for example:
            tmf.init(keyStore);

            return tmf;

        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | UnsupportedOperationException e) {
            e.printStackTrace();
            throw new RuntimeException("can't initialize TrustManagerFactory", e);
        }
    }

    @NonNull
    public static Set<Pair<String, Certificate>> retrieveKeyStoreCertificates(String keystoreType) throws RuntimeException {
        return retrieveKeyStoreCertificates(keystoreType, null, null);
    }

    @NonNull
    public static Set<Pair<String, Certificate>> retrieveKeyStoreCertificates(String keystoreType, @Nullable InputStream keyStoreInitStream, @Nullable String keyStoreInitStreamPassword) throws RuntimeException {

        if (TextUtils.isEmpty(keystoreType)) {
            throw new IllegalArgumentException("keystoreType is null or empty");
        }

        Set<Pair<String, Certificate>> certificates = new LinkedHashSet<>();
        try {
            KeyStore ks = KeyStore.getInstance(keystoreType);
            ks.load(keyStoreInitStream, !TextUtils.isEmpty(keyStoreInitStreamPassword) ? keyStoreInitStreamPassword.toCharArray() : null);
            Enumeration aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = (String) aliases.nextElement();
                Certificate certificate = ks.getCertificate(alias);
                certificates.add(new Pair<>(alias, certificate));
                if (certificate instanceof X509Certificate) {
                    logger.debug("retrieved cert > type: " + certificate.getType() + " | name: " + ((X509Certificate) certificate).getIssuerDN().getName());
                } else {
                    logger.debug("retrieved cert > type: " + certificate.getType());
                }
            }
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            e.printStackTrace();
            throw new RuntimeException("can't retrieve certificate list from store type " + keystoreType, e);
        }
        return certificates;
    }

    public static boolean isCertificateExist(Set<Pair<String, Certificate>> certificates, String x509Name) throws RuntimeException {
        for (Pair<String, Certificate> cert : certificates) {
            if (cert.second instanceof X509Certificate) {
                if (((X509Certificate) cert.second).getIssuerDN().getName().contains(x509Name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public interface KeyStores {

        /**
         * adding to this store is not supported
         */
        String KEYSTORE_ANDROID = "AndroidCAStore";
    }
}
