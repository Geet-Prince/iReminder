package me.geetprince.ireminders;

/**
 * Immutable snapshot of a single Apple Reminder, as read from the iCloud
 * Reminders page the last time iReminder was able to load it.
 *
 * <p>This is deliberately <em>not</em> an offline copy of the user's
 * library. It holds only what is required to decide whether an Android alarm
 * should exist for a reminder: a reference, a title, an absolute trigger
 * time, and whether the reminder is still outstanding.
 *
 * <p>{@link #triggerAt} is an absolute epoch-millisecond instant rather than a
 * wall-clock time, so an alarm fires at the moment the reminder was set for
 * regardless of the device's current time zone.
 */
public final class Reminder {

    private final String id;
    private final String title;
    private final long triggerAt;
    private final boolean completed;
    private final String list;
    private final String notes;
    private final String recurrence;
    private final String url;

    public Reminder(String id, String title, long triggerAt, boolean completed) {
        this(id, title, triggerAt, completed, "", "", "", "");
    }

    public Reminder(String id, String title, long triggerAt, boolean completed,
                    String list, String notes, String recurrence, String url) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Reminder id must not be empty");
        }
        this.id = id;
        this.title = title != null ? title : "";
        this.triggerAt = triggerAt;
        this.completed = completed;
        this.list = list != null ? list : "";
        this.notes = notes != null ? notes : "";
        this.recurrence = recurrence != null ? recurrence : "";
        this.url = url != null ? url : "";
    }

    /** Stable reference used to derive the deterministic Android alarm id. */
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    /** Absolute trigger instant in epoch milliseconds, UTC based. */
    public long getTriggerAt() {
        return triggerAt;
    }

    public boolean isCompleted() {
        return completed;
    }

    /** Name of the Apple Reminders list this reminder belongs to. */
    public String getList() {
        return list;
    }

    /** True when the reminder has notes; the notes text itself is not retained. */
    public boolean hasNotes() {
        return !notes.isEmpty();
    }

    /** Human-readable recurrence, e.g. "Daily" or "Every Monday". Empty if none. */
    public String getRecurrence() {
        return recurrence;
    }

    public boolean isRecurring() {
        return !recurrence.isEmpty();
    }

    /** First attached link, if the reminder has one. Used for a deep link. */
    public String getUrl() {
        return url;
    }

    /** True when this reminder should currently hold an Android alarm. */
    public boolean isSchedulable() {
        return !completed && triggerAt > 0L;
    }

    /**
     * Same reference and trigger instant, so the existing alarm is still
     * correct and must not be rescheduled or duplicated. A changed title is
     * deliberately ignored: it does not affect when the alarm fires, so a
     * rename alone should not disturb an already-registered alarm.
     */
    public boolean hasSameScheduleAs(Reminder other) {
        return other != null && triggerAt == other.triggerAt && completed == other.completed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reminder)) {
            return false;
        }
        Reminder other = (Reminder) o;
        return triggerAt == other.triggerAt
                && completed == other.completed
                && id.equals(other.id)
                && title.equals(other.title);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + (int) (triggerAt ^ (triggerAt >>> 32));
        result = 31 * result + (completed ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Reminder{id=" + id + ", title=" + title + ", triggerAt=" + triggerAt
                + ", completed=" + completed + ", list=" + list
                + ", recurrence=" + recurrence + ", hasNotes=" + hasNotes() + "}";
    }
}
