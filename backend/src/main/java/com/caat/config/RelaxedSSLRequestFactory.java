package com.caat.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * 用于访问证书不被 JVM 默认信任的 HTTPS 服务（如部分第三方 API）。
 * 仅用于 TimeStore 等明确需要的适配器，请勿用于敏感请求。
 */
public class RelaxedSSLRequestFactory extends SimpleClientHttpRequestFactory {

    private static final SSLContext RELAXED_SSL_CONTEXT;

    static {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }
                }
            };
            RELAXED_SSL_CONTEXT = SSLContext.getInstance("TLS");
            RELAXED_SSL_CONTEXT.init(null, trustAll, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("无法创建放宽 SSL 的上下文", e);
        }
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(RELAXED_SSL_CONTEXT.getSocketFactory());
            ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
        }
        super.prepareConnection(connection, httpMethod);
    }
}
