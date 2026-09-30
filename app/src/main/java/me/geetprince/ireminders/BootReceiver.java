package me.geetprince.ireminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Rebuilds scheduled alarms after events that clear them.
 *
 * <p>Android drops every {@code AlarmManager} alarm on reboot, so without this
 * the user's reminders would silently stop notifying until they next opened
 * iReminder. The time and time zone broadcasts are handled for the same
 * reason: a clock jump can leave a registered alarm pointing at an instant that
 * no longer matches the reminder.
 *
 * <p>Alarms are stored as absolute instants, so re-registering restores exactly
 * the same fire times. A reminder is not silently shifted to a different local
 * time by a time zone change; only the system's own delivery of it is affected.
 *
 * <p>No WebView is started and no network request is made here. All this does
 * is re-register alarms from {@link ReminderStore}.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            return;
        }
        Log.i(TAG, "Restoring alarms after " + action);
        NotificationHelper.ensureChannel(context);
        ReminderScheduler.restoreAll(context);
    }
}
