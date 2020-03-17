package org.edx.mobile.view;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.SwipeRefreshLayout;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.inject.Inject;

import org.edx.mobile.R;
import org.edx.mobile.logger.Logger;
import org.edx.mobile.model.course.CourseComponent;
import org.edx.mobile.model.course.HtmlBlockModel;
import org.edx.mobile.module.download.IDownloadManager;
import org.edx.mobile.module.prefs.UserPrefs;
import org.edx.mobile.util.links.WebViewLink;
import org.edx.mobile.services.ViewPagerDownloadManager;
import org.edx.mobile.view.custom.AuthenticatedWebView;
import org.edx.mobile.view.custom.URLInterceptorWebViewClient;

import java.io.File;

import roboguice.inject.InjectView;

public class CourseUnitWebViewFragment extends CourseUnitFragment {
    protected final Logger logger = new Logger(getClass().getName());

    @InjectView(R.id.auth_webview)
    private AuthenticatedWebView authWebView;

    @InjectView(R.id.swipe_container)
    protected SwipeRefreshLayout swipeContainer;

    @Inject
    private UserPrefs pref;

    @Inject
    private IDownloadManager dm;

    private String resourceUrl;
    private final int REQUEST_CODE = 1;

    public static CourseUnitWebViewFragment newInstance(HtmlBlockModel unit) {
        CourseUnitWebViewFragment fragment = new CourseUnitWebViewFragment();
        Bundle args = new Bundle();
        args.putSerializable(Router.EXTRA_COURSE_UNIT, unit);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_authenticated_webview, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeContainer.setEnabled(false);
        Configuration config = getResources().getConfiguration();
        if(config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            view.setRotationY(180);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && !resourceUrl.isEmpty()) {
                    final String fileName = resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1);
                    dm.addDownload(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), resourceUrl, pref.isDownloadOverWifiOnly(), fileName);
                }
                return;
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        authWebView.initWebView(getActivity(), true, false);
        authWebView.getWebViewClient().setActionListener(new URLInterceptorWebViewClient.ActionListener() {

            @Override
            public void onLinkRecognized(@NonNull WebViewLink helper) {

            }

            @Override
            public void downloadResource(String strUrl) {
                resourceUrl = strUrl;
                if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
                } else {
                    final String fileName = strUrl.substring(strUrl.lastIndexOf("/") + 1);
                    dm.addDownload(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), strUrl, pref.isDownloadOverWifiOnly(), fileName);
                    Toast toast = Toast.makeText(getContext(),"Downloading File", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            }
        });
        authWebView.getWebViewClient().setPageStatusListener(new URLInterceptorWebViewClient.IPageStatusListener() {
            @Override
            public void onPageStarted() {
            }

            @Override
            public void onPageFinished() {
                ViewPagerDownloadManager.instance.done(CourseUnitWebViewFragment.this, true);
            }

            @Override
            public void onPageLoadError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.equals(view.getUrl())) {
                    ViewPagerDownloadManager.instance.done(CourseUnitWebViewFragment.this, false);
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.M)
            public void onPageLoadError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse,
                                        boolean isMainRequestFailure) {
                if (isMainRequestFailure) {
                    ViewPagerDownloadManager.instance.done(CourseUnitWebViewFragment.this, false);
                }
            }

            @Override
            public void onPageLoadProgressChanged(WebView view, int progress) {
            }
        });

        if (ViewPagerDownloadManager.USING_UI_PRELOADING) {
            if (ViewPagerDownloadManager.instance.inInitialPhase(unit)) {
                ViewPagerDownloadManager.instance.addTask(this);
            } else {
                authWebView.loadUrl(true, unit.getBlockUrl());
            }
        }
    }

    @Override
    public void run() {
        if (this.isRemoving() || this.isDetached()) {
            ViewPagerDownloadManager.instance.done(this, false);
        } else {
            authWebView.loadUrl(true, unit.getBlockUrl());
        }
    }

    //the problem with viewpager is that it loads this fragment
    //and calls onResume even it is not visible.
    //which breaks the normal behavior of activity/fragment
    //lifecycle.
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (ViewPagerDownloadManager.USING_UI_PRELOADING) {
            return;
        }
        if (ViewPagerDownloadManager.instance.inInitialPhase(unit)) {
            return;
        }
        if (isVisibleToUser) {
            if (authWebView != null) {
                authWebView.loadUrl(false, unit.getBlockUrl());
            }
        } else if (authWebView != null) {
            authWebView.tryToClearWebView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        authWebView.onResume();
        if (hasComponentCallback != null) {
            final CourseComponent component = hasComponentCallback.getComponent();
            if (component != null && component.equals(unit)) {
                authWebView.loadUrl(false, unit.getBlockUrl());
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        authWebView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        authWebView.onDestroy();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        authWebView.onDestroyView();
    }
}
