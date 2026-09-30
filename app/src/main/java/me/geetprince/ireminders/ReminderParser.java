package me.geetprince.ireminders;

import android.util.Log;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Reads reminder data out of the iCloud Reminders page that is already loaded
 * in the WebView, and hands plain {@link Reminder} objects to the scheduler.
 *
 * <p>One-way only: Java asks the page for JSON through
 * {@link WebView#evaluateJavascript}, and never exposes methods to the page
 * through {@code addJavascriptInterface}. The page cannot call back into the
 * app.
 *
 * <h3>Observed page structure</h3>
 * The Reminders UI renders inside a same-origin iframe titled "Reminders 2.0"
 * (selector {@code #early-child}), served from
 * {@code /applications/reminders2/current/en-us/}. Rows are
 * {@code .reminder-item} elements carrying:
 * <ul>
 *   <li>{@code id} — a stable reference, e.g.
 *       {@code reminder-item-<list-hash>:<list-name>:<reminder-hash>}</li>
 *   <li>{@code data-completed} — {@code "true"}/{@code "false"}</li>
 *   <li>{@code data-has-notes} — whether notes are present</li>
 *   <li>{@code aria-label} — the list name first, then flags</li>
 * </ul>
 * The title is in {@code .content-title} (its first line is the priority, e.g.
 * "High Priority"), and the due date and recurrence are in {@code .metadata}.
 *
 * <h3>Known fragility</h3>
 * The due date is <em>display text</em> such as "Today", "Tomorrow", a weekday
 * name, or "Sep 30", optionally followed by a time like "7:00 PM". There is no
 * machine-readable timestamp anywhere in the document, so parsing depends on
 * Apple's current wording and the page locale. Anything unrecognised is logged
 * and treated as undated, which produces no alarm rather than a wrong one.
 *
 * <p>This is scraping a private web UI, not an API. It can break whenever
 * Apple changes the page, which is exactly why every failure path is logged and
 * why a failed read must never disturb the existing schedule.
 */
final class ReminderParser {

    private static final String TAG = "ReminderParser";

    // Failure taxonomy, surfaced to the user and to the log.
    static final String OK = "OK";
    static final String PAGE_NOT_LOADED = "PAGE_NOT_LOADED";
    static final String SESSION_NOT_SIGNED_IN = "SESSION_NOT_SIGNED_IN";
    static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    static final String DOM_STRUCTURE_CHANGED = "DOM_STRUCTURE_CHANGED";
    static final String ZERO_REMINDERS = "ZERO_REMINDERS";
    static final String JS_EXECUTION_FAILED = "JS_EXECUTION_FAILED";
    static final String STILL_LOADING = "STILL_LOADING";
    static final String NO_SCHEDULED_REMINDERS = "NO_SCHEDULED_REMINDERS";

    /** Receives the outcome of a single parse attempt. */
    interface Callback {
        /** @param reason one of the constants above */
        void onResult(String reason, List<Reminder> reminders, String detail);
    }

    private ReminderParser() {
    }

    /**
     * Extracts reminders from the currently loaded page. Always calls back
     * exactly once, on the UI thread.
     */
    static void extract(WebView webView, Callback callback) {
        if (webView == null) {
            fail(callback, PAGE_NOT_LOADED, "WebView is null");
            return;
        }
        Log.i(TAG, "stage=extract requested");

        webView.evaluateJavascript(EXTRACT_JS, value -> {
            String raw = value == null ? "null" : value;
            // evaluateJavascript returns a JSON-encoded string, so the payload
            // arrives wrapped in quotes and with escapes intact.
            String json = unescape(raw);
            if (json == null || json.isEmpty() || json.startsWith("ERR:")) {
                fail(callback, JS_EXECUTION_FAILED,
                        "evaluateJavascript returned: " + truncate(raw, 300));
                return;
            }
            try {
                handlePayload(json, callback);
            } catch (Exception e) {
                fail(callback, DOM_STRUCTURE_CHANGED,
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private static void handlePayload(String json, Callback callback) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.has("error")) {
            String code = root.optString("error", DOM_STRUCTURE_CHANGED);
            String detail = root.optString("detail", "");
            Log.w(TAG, "stage=dom error=" + code + " detail=" + detail);
            fail(callback, code, detail);
            return;
        }
        JSONArray items = root.optJSONArray("items");
        if (items == null) {
            fail(callback, DOM_STRUCTURE_CHANGED, "no 'items' array in payload");
            return;
        }
        Log.i(TAG, "stage=dom rows=" + items.length()
                + " frame=" + root.optString("frame", "?")
                + " signedIn=" + root.optBoolean("signedIn", false));

        List<Reminder> parsed = new ArrayList<>();
        int skippedNoId = 0;
        int undated = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = item.optString("id", "");
            if (id.isEmpty()) {
                skippedNoId++;
                continue;
            }
            String title = cleanTitle(item.optString("title", ""));
            String list = item.optString("list", "");
            String meta = item.optString("meta", "");
            boolean completed = item.optBoolean("completed", false);
            boolean hasNotes = item.optBoolean("hasNotes", false);
            String url = item.optString("url", "");

            Due due = parseDueMeta(meta);
            if (due == null) {
                undated++;
                Log.d(TAG, "reminder undated meta=\"" + truncate(meta, 60)
                        + "\" title=\"" + truncate(title, 40) + "\"");
            }
            parsed.add(new Reminder(
                    id,
                    title,
                    due == null ? 0L : due.triggerAt,
                    completed,
                    list,
                    hasNotes ? "1" : "",
                    due == null ? "" : due.recurrence,
                    url));
            Log.i(TAG, "item id=" + truncate(id, 46)
                    + " title=\"" + truncate(title, 30) + "\""
                    + " list=\"" + truncate(list, 24) + "\""
                    + " meta=\"" + truncate(meta, 32) + "\""
                    + " triggerAt=" + (due == null ? "none" : due.triggerAt)
                    + " recurrence=\"" + (due == null ? "" : due.recurrence) + "\""
                    + " completed=" + completed
                    + " notes=" + hasNotes
                    + " url=" + (url.isEmpty() ? "none" : truncate(url, 40)));
        }

        if (skippedNoId > 0) {
            Log.w(TAG, "stage=validate skippedWithoutId=" + skippedNoId);
        }
        if (parsed.isEmpty()) {
            fail(callback, ZERO_REMINDERS, "rows found but none parseable");
            return;
        }

        int schedulable = 0;
        for (Reminder reminder : parsed) {
            if (reminder.isSchedulable()) {
                schedulable++;
            }
        }
        Log.i(TAG, "stage=validate parsed=" + parsed.size()
                + " schedulable=" + schedulable + " undated=" + undated
                + " completed=" + countCompleted(parsed));

        if (schedulable == 0) {
            // Not a failure: the page parsed fine, there is simply nothing with
            // a usable due time. Treated as success so "Last synced" advances
            // and the stale schedule is correctly cleared.
            Log.i(TAG, "stage=result reason=" + NO_SCHEDULED_REMINDERS);
            callback.onResult(OK, parsed, NO_SCHEDULED_REMINDERS);
            return;
        }
        Log.i(TAG, "stage=result reason=" + OK);
        callback.onResult(OK, parsed, "");
    }

    private static int countCompleted(List<Reminder> reminders) {
        int count = 0;
        for (Reminder reminder : reminders) {
            if (reminder.isCompleted()) {
                count++;
            }
        }
        return count;
    }

    private static void fail(Callback callback, String reason, String detail) {
        Log.w(TAG, "stage=result reason=" + reason + " detail=" + detail);
        callback.onResult(reason, null, detail);
    }

    // ------------------------------------------------------------------
    // Due-date interpretation
    // ------------------------------------------------------------------

    private static final class Due {
        final long triggerAt;
        final String recurrence;

        Due(long triggerAt, String recurrence) {
            this.triggerAt = triggerAt;
            this.recurrence = recurrence;
        }
    }

    private static final String[] RECURRENCE_WORDS = {
            "daily", "weekly", "monthly", "yearly", "annually",
            "every day", "every week", "every month", "every year"
    };

    private static final String[] WEEKDAYS = {
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    };

    /**
     * Reads a {@code .metadata} first line such as "Today, Daily",
     * "Tomorrow, 7:00 PM", "Every Monday", "Sep 30, 9:00 AM".
     *
     * @return null when no due time can be determined, so no alarm is created.
     */
    static Due parseDueMeta(String meta) {
        if (meta == null) {
            return null;
        }
        String firstLine = meta.split("\n")[0].trim();
        if (firstLine.isEmpty()) {
            return null;
        }
        String[] parts = firstLine.split(",");
        String recurrence = "";
        String dateToken = null;
        String timeToken = null;

        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.US);
            if (matchesAny(lower, RECURRENCE_WORDS) || matchesAny(lower, WEEKDAYS)
                    || lower.startsWith("every ")) {
                recurrence = part;
                continue;
            }
            if (timeToken == null && looksLikeTime(part)) {
                timeToken = part;
                continue;
            }
            if (dateToken == null) {
                dateToken = part;
            }
        }
        if (dateToken == null && recurrence.isEmpty()) {
            return null;
        }

        long triggerAt = resolveTrigger(dateToken, timeToken);
        if (triggerAt <= 0L) {
            return null;
        }
        return new Due(triggerAt, recurrence);
    }

    private static boolean matchesAny(String lower, String[] options) {
        for (String option : options) {
            if (lower.equals(option) || lower.startsWith(option + " ")) {
                return true;
            }
        }
        return false;
    }

    /** True for "7:00 PM", "19:00", "9 AM", "7pm". */
    private static boolean looksLikeTime(String value) {
        return value.matches("(?i).*\\d{1,2}:\\d{2}.*")
                || value.matches("(?i)\\d{1,2}\\s*(am|pm)");
    }

    /**
     * Converts a date token plus optional time into an absolute instant.
     * Returns 0 when the date cannot be understood.
     */
    private static long resolveTrigger(String dateToken, String timeToken) {
        Calendar calendar = Calendar.getInstance();
        // Seconds and milliseconds must be cleared as well. Leaving the current
        // second in place makes the computed instant differ on every parse, so
        // each sync would look like a time change and re-register every alarm.
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (dateToken != null) {
            String lower = dateToken.toLowerCase(Locale.US).trim();
            if (lower.equals("today") || lower.equals("overdue")) {
                // keep today
            } else if (lower.equals("tomorrow")) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            } else {
                Integer weekday = weekdayIndex(lower);
                if (weekday != null) {
                    int delta = (weekday - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7;
                    calendar.add(Calendar.DAY_OF_YEAR, delta);
                } else if (!applyExplicitDate(calendar, dateToken)) {
                    Log.w(TAG, "unrecognised date token \"" + truncate(dateToken, 40) + "\"");
                    return 0L;
                }
            }
        }
        // No date but a recurrence: treat as today, which is the next occurrence.
        if (!applyTime(calendar, timeToken)) {
            Log.d(TAG, "no time token for \"" + truncate(dateToken, 30) + "\", using 09:00");
            calendar.set(Calendar.HOUR_OF_DAY, 9);
            calendar.set(Calendar.MINUTE, 0);
        }
        return calendar.getTimeInMillis();
    }

    private static Integer weekdayIndex(String lower) {
        for (int i = 0; i < WEEKDAYS.length; i++) {
            String name = WEEKDAYS[i];
            if (lower.equals(name) || lower.startsWith(name + " ")
                    || lower.endsWith(" " + name)) {
                // Calendar.MONDAY == 2, WEEKDAYS[0] == Monday.
                return (i % 7) + 2;
            }
        }
        return null;
    }

    /** Handles "Sep 30", "30 Sep", "Sep 30, 2026", "2026-09-30", "9/30". */
    private static boolean applyExplicitDate(Calendar calendar, String value) {
        String[] patterns = {
                "MMM d, yyyy", "MMM d yyyy", "d MMM, yyyy", "d MMM yyyy",
                "MMM d", "d MMM", "yyyy-MM-dd", "M/d/yyyy", "M/d"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                java.util.Date parsed = format.parse(value.trim());
                Calendar parsedCalendar = Calendar.getInstance();
                parsedCalendar.setTime(parsed);
                calendar.set(parsedCalendar.get(Calendar.YEAR),
                        parsedCalendar.get(Calendar.MONTH),
                        parsedCalendar.get(Calendar.DAY_OF_MONTH));
                return true;
            } catch (ParseException ignored) {
                // try the next pattern
            }
        }
        return false;
    }

    /** Handles "7:00 PM", "19:00", "9 AM", "7pm", "7:30 am". */
    private static boolean applyTime(Calendar calendar, String value) {
        if (value == null) {
            return false;
        }
        // Collapse whitespace and case so "7:30 am" and "7:30AM" are identical.
        String s = value.trim().toUpperCase(Locale.US).replaceAll("\\s+", "");
        java.util.regex.Matcher meridiem =
                java.util.regex.Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?([AP]M)$").matcher(s);
        if (meridiem.matches()) {
            int hour = Integer.parseInt(meridiem.group(1));
            int minute = meridiem.group(2) != null ? Integer.parseInt(meridiem.group(2)) : 0;
            boolean pm = "PM".equals(meridiem.group(3));
            if (pm && hour < 12) {
                hour += 12;
            }
            if (!pm && hour == 12) {
                hour = 0;
            }
            return setTime(calendar, hour, minute, value);
        }
        java.util.regex.Matcher twentyFour =
                java.util.regex.Pattern.compile("^(\\d{1,2}):(\\d{2})$").matcher(s);
        if (twentyFour.matches()) {
            return setTime(calendar,
                    Integer.parseInt(twentyFour.group(1)),
                    Integer.parseInt(twentyFour.group(2)),
                    value);
        }
        Log.w(TAG, "unrecognised time token \"" + truncate(value, 30) + "\"");
        return false;
    }

    private static boolean setTime(Calendar calendar, int hour, int minute, String original) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            Log.w(TAG, "out-of-range time token \"" + truncate(original, 30) + "\"");
            return false;
        }
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        return true;
    }

    /** ".content-title" starts with the priority line, then the real title. */
    static String cleanTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String[] lines = raw.split("\n");
        // Drop a leading priority label such as "High Priority" or "Low Priority".
        if (lines.length > 1 && lines[0].toLowerCase(Locale.US).contains("priority")) {
            return lines[1].trim();
        }
        return raw.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "null";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /** evaluateJavascript hands back a JSON string literal, so unwrap it. */
    private static String unescape(String raw) {
        if (raw == null || raw.isEmpty() || "null".equals(raw)) {
            return null;
        }
        try {
            if (raw.length() >= 2 && raw.charAt(0) == '"') {
                return new JSONArray("[" + raw + "]").getString(0);
            }
            return raw;
        } catch (Exception e) {
            // Not a quoted literal; hand it back untouched.
            return raw;
        }
    }

    /**
     * Runs in the page. Reports a structured reason instead of throwing, so the
     * Java side can distinguish "signed out" from "Apple changed the DOM".
     */
    private static final String EXTRACT_JS =
            "(function(){try{"
            + "var signIn=document.querySelector('a[href*=\"signin\"],"
            + "input[type=\"password\"],form[action*=\"signin\"]');"
            + "var frame=document.querySelector('#early-child')"
            + "||Array.prototype.filter.call(document.querySelectorAll('iframe'),"
            + "function(f){return (f.title||'').indexOf('Reminders')>=0;})[0];"
            + "if(!frame||!frame.contentDocument||!frame.contentDocument.body){"
            + "return JSON.stringify({error: signIn ? '"
            + SESSION_NOT_SIGNED_IN + "' : '" + PAGE_NOT_LOADED + "',"
            + "detail: signIn ? 'sign-in form present, no Reminders frame' "
            + ": 'no Reminders iframe in document'});}"
            + "var d=frame.contentDocument;"
            + "var rows=d.querySelectorAll('.reminder-item');"
            + "var bodyLen=d.body?(d.body.innerText||'').trim().length:0;"
            + "if(!rows.length){"
            + "if(bodyLen<200){"
            + "return JSON.stringify({error:'" + STILL_LOADING + "',"
            + "detail:'frame rendered but still filling, bodyChars='+bodyLen});}"
            + "return JSON.stringify({error:'" + DOM_STRUCTURE_CHANGED + "',"
            + "detail:'frame has content (bodyChars='+bodyLen+') but no .reminder-item rows'});}"
            + "var items=[];"
            + "for(var i=0;i<rows.length;i++){var r=rows[i];"
            + "var t=r.querySelector('.content-title');"
            + "var metas=r.querySelectorAll('.metadata');"
            + "var meta=metas.length?(metas[0].innerText||''):'';"
            + "var url='';for(var m=1;m<metas.length;m++){"
            + "var cand=(metas[m].innerText||'').trim();"
            + "if(/^https?:\\/\\//.test(cand)){url=cand;break;}}"
            + "var aria=r.getAttribute('aria-label')||'';"
            + "items.push({id:r.id||'',"
            + "title:t?(t.innerText||''):'',"
            + "list:aria.split(',')[0].trim(),"
            + "meta:meta,"
            + "completed:(r.getAttribute('data-completed')==='true'),"
            + "hasNotes:r.getAttribute('data-has-notes')==='true',"
            + "url:url});}"
            + "return JSON.stringify({frame:frame.title||frame.id,items:items,"
            + "signedIn:!signIn});"
            + "}catch(e){return JSON.stringify({error:'" + JS_EXECUTION_FAILED
            + "',detail:String(e).slice(0,150)});}})();";
}
