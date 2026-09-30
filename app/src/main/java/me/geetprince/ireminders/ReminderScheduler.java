package me.geetprince.ireminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reconciles the alarms registered with {@link AlarmManager} against the set of
 * reminders most recently read from the iCloud page.
 *
 * <p>Alarms are only ever registered as the difference between the previous
 * state and the new one, never blanket re-registered. Re-registering an
 * unchanged alarm would reset its trigger, and doing so on every sync would
 * mean an alarm set for 19:00 could be pushed later each time the user opened
 * the app.
 */
final class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    /**
     * Android cannot hold unbounded numbers of alarms for one app, and every
     * alarm costs a PendingIntent slot. Reminders are sorted by due time and
     * only the soonest {@link #MAX_SCHEDULED_ALARMS} are kept, so the nearest
     * reminders are the ones that always notify.
     */
    private static final int MAX_SCHEDULED_ALARMS = 200;

    private ReminderScheduler() {
    }

    /**
     * Deterministic notification and alarm id for a reminder reference.
     *
     * <p>Derived from the id rather than allocated sequentially so that the
     * same reminder always maps to the same id, which is what makes repeated
     * syncs idempotent. Two different reminders colliding is harmless here:
     * reconciliation compares trigger times per id and re-registers whichever
     * one changed, and the id is stored alongside the record so cancellation
     * is always exact.
     */
    static int alarmIdFor(String reminderId) {
        return reminderId.hashCode();
    }

    /**
     * Replaces the registered alarms so they match {@code incoming}.
     *
     * <p>Called with the full current view of the user's reminders, not a
     * delta. Anything missing from {@code incoming} is treated as deleted.
     *
     * <p>If {@code incoming} is null the schedule is left completely untouched.
     * A failed or empty read must never cancel valid alarms: the previous
     * schedule stays until real data can be obtained again.
     *
     * @return how many alarms are registered for this reminder set afterwards.
     */
    static int reconcile(Context context, List<Reminder> incoming) {
        if (incoming == null) {
            Log.w(TAG, "Reconcile called with no data; leaving existing alarms intact");
            return ReminderStore.loadAll(context).size();
        }

        List<Reminder> stored = ReminderStore.loadAll(context);
        Set<String> incomingIds = new HashSet<>();
        for (Reminder reminder : incoming) {
            incomingIds.add(reminder.getId());
        }

        // Drop alarms for reminders that vanished, were completed, or no
        // longer parse as schedulable.
        for (Reminder previous : stored) {
            boolean stillPresent = incomingIds.contains(previous.getId());
            if (!stillPresent) {
                cancel(context, previous.getId());
            }
        }

        // Keep the soonest N so a large library cannot exhaust alarm slots.
        List<Reminder> schedulable = new ArrayList<>();
        for (Reminder reminder : incoming) {
            if (reminder.isSchedulable() && reminder.getTriggerAt() > System.currentTimeMillis()) {
                schedulable.add(reminder);
            }
        }
        schedulable.sort((a, b) -> Long.compare(a.getTriggerAt(), b.getTriggerAt()));
        if (schedulable.size() > MAX_SCHEDULED_ALARMS) {
            int dropped = schedulable.size() - MAX_SCHEDULED_ALARMS;
            schedulable = new ArrayList<>(schedulable.subList(0, MAX_SCHEDULED_ALARMS));
            Log.i(TAG, "Capped at " + MAX_SCHEDULED_ALARMS
                    + " alarms; " + dropped + " later reminders will not notify");
        }

        Set<String> keptIds = new HashSet<>();
        int scheduled = 0;
        int unchanged = 0;
        for (Reminder reminder : schedulable) {
            keptIds.add(reminder.getId());
            Reminder previous = findById(stored, reminder.getId());
            if (previous != null && previous.hasSameScheduleAs(reminder)) {
                // Same reference, same instant: the existing alarm is already
                // correct. Re-registering would restart its countdown.
                unchanged++;
                continue;
            }
            if (previous != null) {
                Log.i(TAG, "reschedule " + truncate(previous.getId(), 40)
                        + " trigger " + previous.getTriggerAt() + " -> " + reminder.getTriggerAt());
                cancel(context, previous.getId());
            } else {
                Log.i(TAG, "new " + truncate(reminder.getId(), 40)
                        + " trigger=" + reminder.getTriggerAt());
            }
            schedule(context, reminder);
            scheduled++;
        }

        // Anything previously scheduled but now past, uncapped, or filtered out.
        for (Reminder previous : stored) {
            if (!keptIds.contains(previous.getId())) {
                cancel(context, previous.getId());
            }
        }

        ReminderStore.replaceAll(context, schedulable);
        ReminderStore.setLastSyncAt(context, System.currentTimeMillis());
        Log.i(TAG, "Reconciled: " + scheduled + " scheduled, " + unchanged
                + " already current, " + stored.size() + " previously stored");
        return schedulable.size();
    }

    /**
     * Re-registers every stored alarm. Used after a reboot, which clears all
     * alarms, and after a time zone or clock change.
     */
    static void restoreAll(Context context) {
        List<Reminder> stored = ReminderStore.loadAll(context);
        if (stored.isEmpty()) {
            return;
        }
        int restored = 0;
        for (Reminder reminder : stored) {
            if (reminder.getTriggerAt() > System.currentTimeMillis()) {
                schedule(context, reminder);
                restored++;
            }
        }
        Log.i(TAG, "Restored " + restored + " of " + stored.size() + " stored alarms");
    }

    /** Cancels every registered alarm and forgets the schedule. */
    static void cancelAll(Context context) {
        for (Reminder reminder : ReminderStore.loadAll(context)) {
            cancel(context, reminder.getId());
        }
        ReminderStore.clearAll(context);
        Log.i(TAG, "Cancelled all reminder alarms");
    }

    /**
     * Registers a one-off alarm that is deliberately kept out of
     * {@link ReminderStore}, so exercising the notification path cannot disturb
     * the user's real schedule. Uses its own id namespace and is overwritten on
     * each call rather than accumulating.
     */
    static void scheduleTestReminder(Context context, String title, long delayMillis) {
        Reminder test = new Reminder(
                TEST_REMINDER_ID, title, System.currentTimeMillis() + delayMillis, false);
        schedule(context, test);
        Log.i(TAG, "Test reminder armed for " + delayMillis + "ms");
    }

    private static final String TEST_REMINDER_ID = "__ireminder_test__";

    private static String truncate(String value, int max) {
        return value == null ? "null" : (value.length() <= max ? value : value.substring(0, max) + "\u2026");
    }

    private static Reminder findById(List<Reminder> reminders, String id) {
        for (Reminder reminder : reminders) {
            if (reminder.getId().equals(id)) {
                return reminder;
            }
        }
        return null;
    }

    /**
     * Registers one exact alarm when the platform allows it, otherwise an
     * inexact one.
     *
     * <p>From Android 12 exact alarms need a user-granted permission. When it
     * is absent the alarm still fires, just with the system's own batching
     * delay, which is far better than dropping the notification or throwing.
     */
    private static void schedule(Context context, Reminder reminder) {
        AlarmManager manager = alarmManager(context);
        if (manager == null) {
            return;
        }
        int alarmId = alarmIdFor(reminder.getId());
        PendingIntent operation = buildPendingIntent(context, reminder, alarmId,
                PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (canScheduleExact(manager)) {
                manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, reminder.getTriggerAt(), operation);
            } else {
                // Inexact fallback: still delivered, at a time of the system's
                // choosing within a normal batching window.
                manager.setWindow(
                        AlarmManager.RTC_WAKEUP, reminder.getTriggerAt(),
                        INEXACT_WINDOW_MILLIS, operation);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Alarm permission denied; falling back to inexact", e);
            try {
                manager.set(AlarmManager.RTC_WAKEUP, reminder.getTriggerAt(), operation);
            } catch (SecurityException fatal) {
                Log.e(TAG, "Unable to register alarm for " + reminder.getId(), fatal);
            }
        }
    }

    /**
     * Builds the PendingIntent for an alarm.
     *
     * <p>{@code FLAG_UPDATE_CURRENT} keeps the stored extras in step with the
     * newest title. The intent's data URI carries the alarm id, which is what
     * makes each reminder's PendingIntent distinct and therefore individually
     * cancellable.
     */
    private static PendingIntent buildPendingIntent(Context context, Reminder reminder,
                                                   int alarmId, int extraFlags) {
        Intent intent = new Intent(context, ReminderNotificationReceiver.class);
        intent.setAction(ReminderNotificationReceiver.ACTION_REMINDER_DUE);
        intent.putExtra(ReminderNotificationReceiver.EXTRA_TITLE, reminder.getTitle());
        intent.putExtra(ReminderNotificationReceiver.EXTRA_NOTIFICATION_ID, alarmId);
        intent.setData(android.net.Uri.parse("ireminder://reminder/" + reminder.getId()));

        int flags = extraFlags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, alarmId, intent, flags);
    }

    private static void cancel(Context context, String reminderId) {
        AlarmManager manager = alarmManager(context);
        if (manager == null) {
            return;
        }
        int alarmId = alarmIdFor(reminderId);
        // FLAG_NO_CREATE: build the same identity without creating anything, so
        // cancelling an alarm that was never registered is a no-op.
        PendingIntent existing = buildPendingIntent(context,
                new Reminder(reminderId, "", 0L, false), alarmId,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (existing == null) {
            return;
        }
        manager.cancel(existing);
        existing.cancel();
    }

    private static AlarmManager alarmManager(Context context) {
        return (AlarmManager) context.getApplicationContext()
                .getSystemService(Context.ALARM_SERVICE);
    }

    /** True when this OS lets the app register an exact alarm right now. */
    static boolean canScheduleExact(Context context) {
        AlarmManager manager = alarmManager(context);
        return manager != null && canScheduleExact(manager);
    }

    private static boolean canScheduleExact(AlarmManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return manager.canScheduleExactAlarms();
    }

    /**
     * Batching window used when exact alarms are not permitted.
     *
     * <p>Kept short deliberately. {@code setWindow} lets Android deliver
     * anywhere inside the window, so a wide one would let a reminder drift far
     * past the time the user set. A few minutes is enough to give the platform
     * room to batch without making a 19:00 reminder arrive at 19:08.
     */
    private static final long INEXACT_WINDOW_MILLIS = 2 * 60 * 1000L;
}
