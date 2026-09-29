package com.example.ireminders;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * iReminder: lightweight WebView wrapper around the official
 * Apple Reminders web app (https://www.icloud.com/reminders).
 *
 * <p>Login/session handling is left entirely to Apple's web
 * authentication. This app only persists cookies / DOM storage
 * via the default WebView storage so an existing Apple session
 * is restored on relaunch while it remains valid.
 */
public class MainActivity extends AppCompatActivity {

    private static final String REMINDERS_URL = "https://www.icloud.com/reminders";

    private static final String PREFS = "iremember_prefs";
    private static final String KEY_THEME = "theme_mode"; // "system", "light", "dark"

    private WebView webView;

    @Override
    protected void attachBaseContext(Context newBase) {
        // Apply the saved choice before any view is created so WebView
        // darkening and the window background match from the very first frame.
        applyThemePreference(newBase);
        super.attachBaseContext(newBase);
    }

    /**
     * Maps the stored choice onto an AppCompat night mode. AppCompat's
     * override wins over the system setting, so an explicit "light" stays
     * light on a device that is in dark mode (and vice versa).
     */
    private static void applyThemePreference(Context context) {
        String mode = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_THEME, "system");
        int nightMode;
        if ("light".equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Must run after setContentView: the DecorView (needed for the
        // insets controller on API 30+) only exists once content is set.
        setupEdgeToEdge();
        webView = findViewById(R.id.webview);
        applySystemBarPadding(findViewById(R.id.root));
        setupThemeButton();
        configureWebView(webView);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(REMINDERS_URL);
        }
    }

    /**
     * Draws the app behind the system bars with a transparent status bar,
     * like modern apps. Page content is padded below the status bar via
     * {@link #applySystemBarPadding(View)} so nothing hides underneath it.
     */
    private void setupEdgeToEdge() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        // Dark status-bar icons on light content, white icons in dark mode.
        boolean night = isNightMode();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        night ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!night && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private boolean isNightMode() {
        int mode = getDelegate().getLocalNightMode();
        if (mode == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) {
            mode = AppCompatDelegate.getDefaultNightMode();
        }
        switch (mode) {
            case AppCompatDelegate.MODE_NIGHT_YES:
                return true;
            case AppCompatDelegate.MODE_NIGHT_NO:
                return false;
            default:
                return (getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    /**
     * Sun/moon theme toggle. The button always shows the icon of the mode
     * tapping will switch to (moon in light mode, sun in dark mode).
     * Tap swaps explicitly between Light and Dark; long-press returns to
     * following the system setting.
     */
    private void setupThemeButton() {
        ImageButton themeButton = findViewById(R.id.theme_button);
        updateThemeButtonIcon(themeButton);
        themeButton.setOnClickListener(v -> {
            boolean currentlyNight = isNightMode();
            saveThemePref(currentlyNight ? "light" : "dark");
            Toast.makeText(this,
                    currentlyNight ? "Light theme" : "Dark theme",
                    Toast.LENGTH_SHORT).show();
            // Spin while swapping, then recreate to apply the new night mode.
            // The Apple session is cookie-based so login is preserved.
            v.animate().rotationBy(180f).setDuration(250)
                    .withEndAction(this::recreate).start();
        });
        themeButton.setOnLongClickListener(v -> {
            saveThemePref("system");
            Toast.makeText(this, "Following system theme", Toast.LENGTH_SHORT).show();
            recreate();
            return true;
        });
    }

    private void updateThemeButtonIcon(ImageButton themeButton) {
        boolean night = isNightMode();
        themeButton.setImageResource(night ? R.drawable.ic_sun : R.drawable.ic_moon);
        themeButton.setContentDescription(
                night ? "Switch to light theme" : "Switch to dark theme");
    }

    private void saveThemePref(String mode) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME, mode).apply();
        // Persist + apply: an explicit choice overrides the system theme.
        int nightMode;
        if ("light".equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    /** Pads the root view by the system-bar insets so content sits below them. */
    private void applySystemBarPadding(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
    }

    @SuppressWarnings("deprecation")
    private void configureWebView(WebView view) {
        // Accept and persist cookies so the Apple login session survives restarts.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(view, true);
        }

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // iCloud auth flows may open popups / new windows.
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        // Darken the web content itself when the app is in night mode
        // (follows the System default / Light / Dark choice above).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.setAlgorithmicDarkeningAllowed(true);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings.setForceDark(WebSettings.FORCE_DARK_AUTO);
        }
        // Desktop-like UA is NOT forced: keep the default mobile UA so
        // iCloud serves its mobile web UI.

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView wv, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host == null) {
                    return false;
                }
                // Keep Apple / iCloud navigation inside the app.
                if (host.endsWith("icloud.com") || host.endsWith("apple.com")) {
                    return false;
                }
                // Anything else opens in the external browser.
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                startActivity(intent);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView wv, String url) {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host == null) {
                    return false;
                }
                if (host.endsWith("icloud.com") || host.endsWith("apple.com")) {
                    return false;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            }
        });
        view.setWebChromeClient(new WebChromeClient());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        // Flush cookies to storage so the session persists across restarts.
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Navigate WebView history first; only exit when at the start.
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
