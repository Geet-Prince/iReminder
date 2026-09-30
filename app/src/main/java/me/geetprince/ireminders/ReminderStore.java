package me.geetprince.ireminders;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal local record of which Android alarms iReminder has registered.
 *
 * <p>This is scheduling metadata and nothing more. It exists so that
 * {@link ReminderScheduler} can cancel alarms for reminders that were deleted,
 * completed, or rescheduled, and so that {@link BootReceiver} can rebuild
 * alarms after a reboot wipes them.
 *
 * <p>It deliberately does <em>not</em> hold a copy of the user's Reminders
 * library: no due dates, notes, lists, or attachments are retained beyond the
 * title needed to render a notification, and nothing is ever transmitted off
 * the device. Android forbids an app from reading another app's preferences, so
 * the cookie jar holding the Apple session is not reachable from here.
 */
final class ReminderStore {

    private static final String TAG = "ReminderStore";

    private static final String PREFS = "iremember_scheduled";
    private static final String KEY_SCHEDULED = "scheduled";
    private static final String KEY_LAST_SYNC = "last_sync_at";
    private static final String KEY_ENABLED = "notifications_enabled";

    private ReminderStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean areNotificationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static void setNotificationsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Epoch millis of the last successful reconciliation, or 0 if never. */
    static long getLastSyncAt(Context context) {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L);
    }

    static void setLastSyncAt(Context context, long when) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, when).apply();
    }

    static void clearLastSyncAt(Context context) {
        prefs(context).edit().remove(KEY_LAST_SYNC).apply();
    }

    /**
     * Replaces the stored schedule with {@code reminders} in a single atomic
     * write, so an interrupted sync cannot leave a half-updated record.
     */
    static void replaceAll(Context context, List<Reminder> reminders) {
        JSONArray array = new JSONArray();
        for (Reminder reminder : reminders) {
            if (!reminder.isSchedulable()) {
                continue;
            }
            try {
                JSONObject object = new JSONObject();
                object.put("id", reminder.getId());
                object.put("title", reminder.getTitle());
                object.put("triggerAt", reminder.getTriggerAt());
                object.put("alarmId", ReminderScheduler.alarmIdFor(reminder.getId()));
                array.put(object);
            } catch (JSONException e) {
                // Absurd for these field types, but never let one bad record
                // abort the sync and leave the previous schedule half-written.
                Log.w(TAG, "Skipping unserialisable reminder " + reminder.getId(), e);
            }
        }
        prefs(context).edit().putString(KEY_SCHEDULED, array.toString()).apply();
    }

    /** Every reminder that currently has an alarm registered for it. */
    static List<Reminder> loadAll(Context context) {
        String raw = prefs(context).getString(KEY_SCHEDULED, null);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Reminder> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String id = object.optString("id", null);
                if (id == null || id.isEmpty()) {
                    continue;
                }
                result.add(new Reminder(
                        id,
                        object.optString("title", ""),
                        object.optLong("triggerAt", 0L),
                        false));
            }
        } catch (JSONException e) {
            // Corrupt record. Keep the file rather than throwing, so a bad
            // write cannot brick the feature; the next successful sync heals.
            Log.w(TAG, "Could not read stored schedule", e);
        }
        return result;
    }

    static void clearAll(Context context) {
        prefs(context).edit().remove(KEY_SCHEDULED).apply();
    }
}
