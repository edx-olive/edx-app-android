package org.edx.mobile.view;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.inject.Inject;

import org.edx.mobile.R;
import org.edx.mobile.authentication.LoginAPI;
import org.edx.mobile.base.BaseFragmentActivity;
import org.edx.mobile.databinding.ActivitySamlWebViewBinding;
import org.edx.mobile.http.HttpStatus;
import org.edx.mobile.http.HttpStatusException;
import org.edx.mobile.model.api.ProfileModel;
import org.edx.mobile.module.analytics.AnalyticsRegistry;
import org.edx.mobile.module.prefs.LoginPrefs;
import org.edx.mobile.task.Task;
import org.edx.mobile.util.Config;
import org.edx.mobile.util.IntentFactory;
import org.edx.mobile.util.images.ErrorUtils;

import static org.edx.mobile.http.constants.ApiConstants.SAML_PROVIDER_URL;


public class SamlWebViewActivity extends BaseFragmentActivity {

    @Inject
    private Config config;

    @Inject
    LoginPrefs loginPrefs;

    @Inject
    protected AnalyticsRegistry analyticsRegistry;

    private ActivitySamlWebViewBinding activitySamlWebViewBinding;

    private String authEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activitySamlWebViewBinding = DataBindingUtil.setContentView(this, R.layout.activity_saml_web_view);
        setToolbarAsActionBar();
        String idpSlug = config.getSamlConfig().getSamlIdpSlug();
        WebView myWebView = activitySamlWebViewBinding.samlWebView;
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        setTitle(config.getSamlConfig().getSamlName());
        myWebView.setWebViewClient(new MyWebViewClient());
        authEntry = getIntent().getStringExtra("AUTH_ENTRY");
        String url = config.getApiHostURL() + SAML_PROVIDER_URL.replace("{idpSlug}", idpSlug).replace("{authEntry}", authEntry);
        myWebView.loadUrl(url);
    }

    @NonNull
    public static Intent newIntent() {
        return IntentFactory.newIntentForComponent(SamlWebViewActivity.class);
    }

    private class MyWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {

            boolean isPlatform = url.contains(config.getApiHostURL());

            if (isPlatform) {
                view.setVisibility(View.GONE);
                activitySamlWebViewBinding.webViewProgress.loadingIndicator.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.VISIBLE);
                activitySamlWebViewBinding.webViewProgress.loadingIndicator.setVisibility(View.GONE);
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {

            String cookies = CookieManager.getInstance().getCookie(url);
            if (url.contains(config.getApiHostURL()) && cookies != null) {
                loginPrefs.storeUserCookies(cookies);
                ProfileTask profileTask = new ProfileTask(getApplicationContext(), view);
                profileTask.execute();
            }
        }
    }

    private class ProfileTask extends Task<ProfileModel> {

        @Inject
        private LoginAPI loginAPI;

        private WebView view;

        public ProfileTask(@NonNull Context context, WebView view) {
            super(context);
            this.view = view;
        }

        @Override
        public void onSuccess(@NonNull ProfileModel profile) {
            onUserLoginSuccess(profile);
        }

        @Override
        public void onException(Exception ex) {
            if (ex instanceof HttpStatusException &&
                    ((HttpStatusException) ex).getStatusCode() == HttpStatus.UNAUTHORIZED) {
                if (authEntry.equals("login")){
                    loginPrefs.clear();
                    Toast toast = Toast.makeText(this.getContext(), getString(R.string.login_failed),Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    finish();
                } else {
                    view.setVisibility(View.VISIBLE);
                    activitySamlWebViewBinding.webViewProgress.loadingIndicator.setVisibility(View.GONE);
                }
            }
        }

        @Override
        @NonNull
        public ProfileModel call() throws Exception {
            return loginAPI.getProfile();
        }
    }

    public void onUserLoginSuccess(@NonNull ProfileModel profile) {
        analyticsRegistry.identifyUser(
                profile.id.toString(),
                profile.email,
                profile.username);
        setResult(RESULT_OK);
        finish();
        environment.getRouter().showMainDashboard(this);
    }
}
