package me.geetprince.ireminders;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * Creates the notification channel and renders a reminder notification.
 *
 * <p>No network, no WebView, no iCloud access happens here. This is purely
 * local presentation of a title iReminder already read from the page.
 */
final class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    static final String CHANNEL_ID = "reminder_notifications";

    private NotificationHelper() {
    }

    /**
     * Idempotently creates the single "Reminder notifications" channel.
     * Safe to call on every launch; the platform ignores a repeat create with
     * the same id, and a user's own channel tweaks are never overwritten.
     */
    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                // HIGH so a reminder is visible immediately at its due time.
                // The user can lower this in system settings at any time.
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Shows one reminder notification. The notification id is the reminder's
     * deterministic alarm id, so a re-fired or re-registered alarm updates the
     * existing notification rather than stacking a duplicate on top of it.
     */
    static void showReminder(Context context, String title, int notificationId) {
        if (!canPostNotifications(context)) {
            Log.i(TAG, "Notification permission not granted; skipping notification");
            return;
        }
        ensureChannel(context);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        // Tapping the notification opens iReminder.
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, open, flags);

        String displayTitle = title == null || title.trim().isEmpty()
                ? context.getString(R.string.notification_untitled_reminder)
                : title.trim();

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(displayTitle)
                .setSubText(context.getString(R.string.notification_due_now))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        try {
            manager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // The permission can be revoked between the check above and here.
            Log.w(TAG, "Not allowed to post reminder notification", e);
        }
    }
}
