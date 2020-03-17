package org.edx.mobile.http.interceptor;

import android.content.Context;
import android.support.annotation.NonNull;

import org.edx.mobile.logger.Logger;
import org.edx.mobile.module.prefs.LoginPrefs;
import org.edx.mobile.util.Config;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import roboguice.RoboGuice;

/**
 * Injects OAuth token - if present - into Authorization header
 **/
public final class OauthHeaderRequestInterceptor implements Interceptor {
    protected final Logger logger = new Logger(getClass().getName());

    @NonNull
    private final LoginPrefs loginPrefs;

    private  final Config config;

    public OauthHeaderRequestInterceptor(@NonNull Context context) {
        loginPrefs = RoboGuice.getInjector(context).getInstance(LoginPrefs.class);
        config = RoboGuice.getInjector(context).getInstance(Config.class);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        final Request.Builder builder = chain.request().newBuilder();
        final String token = loginPrefs.getAuthorizationHeader();
        final String cookies = loginPrefs.getUserCookies();
        if (token != null) {
            builder.addHeader("Authorization", token);
        } else if (cookies != null) {
            builder.addHeader("Cookie", cookies);
            builder.addHeader("X-CSRFToken", getCsrfToken(cookies));
            builder.addHeader("Referer", config.getApiHostURL());
        }
        return chain.proceed(builder.build());
    }

    private String getCsrfToken(String cookies){
        cookies = cookies.substring(cookies.indexOf("csrftoken=") + 10);
        return cookies.substring(0, cookies.indexOf(";"));

    }

}
