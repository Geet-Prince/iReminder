package me.geetprince.ireminders;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import android.annotation.TargetApi;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
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

    private static final int REQUEST_POST_NOTIFICATIONS = 1001;

    private static final String TAG = "iReminderSync";

    /** Reminders render well after onPageFinished, so parsing is retried. */
    private static final int SYNC_MAX_ATTEMPTS = 12;
    private static final long SYNC_RETRY_DELAY_MILLIS = 3000L;

    private WebView webView;

    /**
     * True once the WebView reports a finished page load. Guards the
     * notification sync so reminder extraction is only ever attempted against a
     * fully loaded document, never a half-rendered one.
     */
    private boolean pageLoadFinished;

    /** Set while a sync is in flight, to avoid re-entrant parses. */
    private boolean syncingReminders;

    /** Held so a completed sync can refresh the visible "Last synced" value. */
    private AlertDialog settingsDialog;

    private int syncAttempt;
    private boolean syncUserInitiated;

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
        setupNotificationButton();
        configureWebView(webView);
        NotificationHelper.ensureChannel(this);

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
            // saveThemePref() calls setDefaultNightMode(), which already
            // recreates this activity. Calling recreate() again here would
            // destroy the WebView twice; the second teardown can SIGSEV the
            // still-live renderer, which Crashpad escalates into a full app
            // crash. The Apple session is cookie-based so login survives.
            v.animate().rotationBy(180f).setDuration(250).start();
        });
        themeButton.setOnLongClickListener(v -> {
            saveThemePref("system");
            Toast.makeText(this, "Following system theme", Toast.LENGTH_SHORT).show();
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

    // ---------------------------------------------------------------------
    // Local reminder notifications
    //
    // Everything below is additive: it adds a second corner button and a
    // settings dialog, and leaves the WebView, theme handling, navigation and
    // Apple sign-in flow exactly as they were.
    // ---------------------------------------------------------------------

    private void setupNotificationButton() {
        ImageButton button = findViewById(R.id.notification_button);
        button.setOnClickListener(v -> showNotificationSettings());
    }

    private void showNotificationSettings() {
        View content = getLayoutInflater().inflate(R.layout.dialog_notification_settings, null);
        androidx.appcompat.widget.SwitchCompat toggle = content.findViewById(R.id.notification_switch);        TextView lastSynced = content.findViewById(R.id.last_synced);
        Button syncNow = content.findViewById(R.id.sync_now);
        Button testButton = content.findViewById(R.id.test_notification);

        boolean enabled = ReminderStore.areNotificationsEnabled(this);
        toggle.setChecked(enabled);
        // Reflects the state right now; refreshing is always an explicit tap.
        lastSynced.setText(formatLastSynced(ReminderStore.getLastSyncAt(this)));

        toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                onNotificationsToggled(isChecked));

        syncNow.setOnClickListener(v -> {
            Toast.makeText(this, R.string.notifications_sync_started,
                    Toast.LENGTH_SHORT).show();
            syncReminders(true);
        });

        // Debug only, so the shipping UI stays as small as the spec asks.
        // Read from the installed app's flags rather than BuildConfig, which
        // this project does not generate.
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            testButton.setVisibility(View.VISIBLE);
            testButton.setOnClickListener(v -> {
                ReminderScheduler.scheduleTestReminder(
                        this, getString(R.string.notifications_test_title), 30_000L);
                Toast.makeText(this, R.string.notifications_test_scheduled,
                        Toast.LENGTH_LONG).show();
            });
        }

        settingsDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.notifications_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        settingsDialog.setOnDismissListener(d -> settingsDialog = null);
        settingsDialog.show();
    }

    private void onNotificationsToggled(boolean enabled) {
        ReminderStore.setNotificationsEnabled(this, enabled);
        if (enabled) {
            // Ask in context, at the moment the user opts in, never at startup.
            requestPostNotificationsIfNeeded();
            requestExactAlarmIfNeeded();
            Toast.makeText(this, R.string.notifications_enabled_toast,
                    Toast.LENGTH_SHORT).show();
        } else {
            ReminderScheduler.cancelAll(this);
            Toast.makeText(this, R.string.notifications_disabled_toast,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (NotificationHelper.canPostNotifications(this)) {
            return;
        }
        // Platform API rather than ActivityCompat, so no extra dependency is
        // needed. minSdk 24 is above the API 23 requirement for this.
        requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS);
    }

    /**
     * Exact timing is optional: without it Android batches alarms to a time of
     * its choosing, so the reminder still arrives, just less punctually. Only
     * worth asking for once, and only when notifications are actually wanted.
     */
    private void requestExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (ReminderScheduler.canScheduleExact(this)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.notifications_exact_alarm_rationale)
                .setPositiveButton(R.string.notifications_exact_alarm_grant, (d, w) -> {
                    try {
                        startActivity(new Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (ActivityNotFoundException e) {
                        // Some builds expose no such screen. Inexact alarms
                        // still work, so this is not worth interrupting for.
                    }
                })
                .setNegativeButton(R.string.notifications_exact_alarm_skip, null)
                .show();
    }

    /**
     * Reconciles local alarms against reminder data read from the iCloud page.
     *
     * <p>iCloud Reminders is a single-page app: {@code onPageFinished} fires
     * long before any reminder row exists, so a first attempt is expected to
     * report {@link ReminderParser#STILL_LOADING}. Attempts are therefore
     * retried on a backoff until the rows appear or the budget runs out.
     *
     * <p>Failure is always isolated: if the page is not signed in, has not
     * rendered, or the DOM has changed, the previously scheduled alarms are left
     * exactly as they were and "Last synced" keeps its old value. Only a
     * successful parse is allowed to touch the schedule.
     */
    private void syncReminders(boolean userInitiated) {
        if (webView == null) {
            finishSync(ReminderParser.PAGE_NOT_LOADED, "WebView is null", userInitiated);
            return;
        }
        if (!pageLoadFinished) {
            finishSync(ReminderParser.PAGE_NOT_LOADED, "page load has not completed", userInitiated);
            return;
        }
        if (syncingReminders) {
            android.util.Log.i(TAG, "sync=skipped reason=alreadyRunning");
            return;
        }
        syncingReminders = true;
        syncAttempt = 0;
        syncUserInitiated = userInitiated;
        android.util.Log.i(TAG, "sync=start userInitiated=" + userInitiated
                + " url=" + webView.getUrl());
        runSyncAttempt();
    }

    /** One parse attempt; retries itself while the page is still rendering. */
    private void runSyncAttempt() {
        syncAttempt++;
        WebView current = webView;
        if (current == null) {
            finishSync(ReminderParser.PAGE_NOT_LOADED, "WebView went away", syncUserInitiated);
            return;
        }
        ReminderParser.extract(current, (reason, reminders, detail) -> {
            android.util.Log.i(TAG, "sync=attempt=" + syncAttempt
                    + "/" + SYNC_MAX_ATTEMPTS + " reason=" + reason + " detail=" + detail);

            if (ReminderParser.STILL_LOADING.equals(reason) && syncAttempt < SYNC_MAX_ATTEMPTS) {
                if (syncAttempt == 1 && !syncUserInitiated) {
                    // Automatic pass after page load: stay quiet, the user is
                    // not waiting on a toast.
                }
                new android.os.Handler(getMainLooper()).postDelayed(
                        this::runSyncAttempt, SYNC_RETRY_DELAY_MILLIS);
                return;
            }
            if (ReminderParser.STILL_LOADING.equals(reason)) {
                finishSync(ReminderParser.PAGE_NOT_LOADED,
                        "reminders did not render after " + syncAttempt + " attempts", syncUserInitiated);
                return;
            }
            if (!ReminderParser.OK.equals(reason) || reminders == null) {
                // Keep the existing cache and alarms untouched.
                finishSync(reason, detail, syncUserInitiated);
                return;
            }

            int schedulable = 0;
            for (Reminder reminder : reminders) {
                if (reminder.isSchedulable()) {
                    schedulable++;
                }
            }
            android.util.Log.i(TAG, "sync=parsed count=" + reminders.size()
                    + " schedulable=" + schedulable);
            if (reminders.isEmpty()) {
                finishSync(ReminderParser.ZERO_REMINDERS, "no rows", syncUserInitiated);
                return;
            }
            int registered = ReminderScheduler.reconcile(this, reminders);
            finishSync(ReminderParser.OK,
                    registered + " reminder(s) scheduled from " + reminders.size()
                            + " read",
                    syncUserInitiated);
        });
    }

    /**
     * Single exit point for a sync attempt: logs the reason, updates the
     * visible state, and reports to the user when they asked for it.
     */
    private void finishSync(String reason, String detail, boolean userInitiated) {
        syncingReminders = false;
        boolean ok = ReminderParser.OK.equals(reason);
        android.util.Log.i(TAG, "sync=finish reason=" + reason + " detail=" + detail
                + " lastSync=" + ReminderStore.getLastSyncAt(this));
        if (!userInitiated) {
            return;
        }
        if (ok) {
            Toast.makeText(this, getString(R.string.notifications_sync_ok, detail),
                    Toast.LENGTH_LONG).show();
            // The dialog may be showing a stale timestamp.
            refreshSettingsDialogIfOpen();
        } else {
            Toast.makeText(this, getString(R.string.notifications_sync_failed, reason, detail),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Keeps an open settings dialog in step with a completed sync. */
    private void refreshSettingsDialogIfOpen() {
        if (settingsDialog == null || !settingsDialog.isShowing()) {
            return;
        }
        TextView lastSynced = settingsDialog.findViewById(R.id.last_synced);
        if (lastSynced != null) {
            lastSynced.setText(formatLastSynced(ReminderStore.getLastSyncAt(this)));
        }
    }

    private String formatLastSynced(long lastSyncAt) {
        if (lastSyncAt <= 0L) {
            return getString(R.string.notifications_last_synced_never);
        }
        java.text.DateFormat format = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        return getString(R.string.notifications_last_synced, format.format(lastSyncAt));
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
            /**
             * Marks the document as ready and takes the opportunity to refresh
             * the local alarm schedule. This is the only automatic sync point;
             * nothing is scraped in the background while the app is closed.
             */
            @Override
            public void onPageFinished(WebView wv, String url) {
                super.onPageFinished(wv, url);
                pageLoadFinished = true;
                if (ReminderStore.areNotificationsEnabled(MainActivity.this)) {
                    syncReminders(false);
                }            }

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

            /**
             * The renderer process died (out-of-memory, or a WebView bug).
             * Returning false would leave the app with a dead WebView, and
             * letting it propagate lets Crashpad abort the whole process.
             * Build a fresh WebView so a renderer crash costs the user a page
             * reload, not the entire app.
             */
            @Override
            @TargetApi(Build.VERSION_CODES.O)
            public boolean onRenderProcessGone(WebView wv, RenderProcessGoneDetail detail) {
                ViewGroup parent = wv.getParent() instanceof ViewGroup
                        ? (ViewGroup) wv.getParent() : null;
                ViewGroup.LayoutParams params = wv.getLayoutParams();
                if (parent != null) {
                    parent.removeView(wv);
                }
                wv.destroy();
                if (webView == wv) {
                    webView = null;
                }
                if (parent == null) {
                    return true;
                }
                // findViewById() would return the instance just destroyed, so
                // construct a new one. It keeps the same id and is added at
                // index 0 to stay behind the theme button.
                WebView replacement = new WebView(MainActivity.this);
                replacement.setId(R.id.webview);
                parent.addView(replacement, 0, params);
                webView = replacement;
                configureWebView(webView);
                webView.loadUrl(REMINDERS_URL);
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
        // Returning to the app is the main chance to pick up reminders created
        // or changed elsewhere. No WebView is kept alive in the background, so
        // this only runs while the app is actually in front of the user.
        if (pageLoadFinished && ReminderStore.areNotificationsEnabled(this)) {
            syncReminders(false);
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
