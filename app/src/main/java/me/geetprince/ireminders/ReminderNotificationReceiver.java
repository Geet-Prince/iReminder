package me.geetprince.ireminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receives a due reminder alarm and shows a local notification.
 *
 * <p>Intentionally the lightest component in the app. It does not touch the
 * WebView, does not load iCloud, and performs no network or disk I/O beyond
 * reading two extras out of the intent. On Android this runs as a short-lived
 * broadcast, so there is no background service and nothing survives the call.
 */
public class ReminderNotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderNotification";

    static final String ACTION_REMINDER_DUE = "me.geetprince.ireminders.action.REMINDER_DUE";
    static final String EXTRA_TITLE = "me.geetprince.ireminders.extra.TITLE";
    static final String EXTRA_NOTIFICATION_ID = "me.geetprince.ireminders.extra.NOTIFICATION_ID";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_REMINDER_DUE.equals(intent.getAction())) {
            return;
        }
        String title = intent.getStringExtra(EXTRA_TITLE);
        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        Log.i(TAG, "Reminder alarm fired: " + title);
        NotificationHelper.showReminder(context, title, notificationId);
    }
}
