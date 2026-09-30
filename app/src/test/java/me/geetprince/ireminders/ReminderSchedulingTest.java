package me.geetprince.ireminders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the pure scheduling logic that keeps repeated syncs idempotent.
 *
 * <p>These deliberately avoid {@code Context} and {@code AlarmManager} so they
 * run on the JVM in milliseconds. The framework-level behaviour they protect
 * is that a reminder whose schedule has not changed must not be re-registered,
 * which is what stops a 19:00 alarm from being pushed later every time the
 * user opens the app.
 */
public class ReminderSchedulingTest {

    private static final long SEVEN_PM = 1_700_000_000_000L;
    private static final long EIGHT_PM = SEVEN_PM + 3_600_000L;

    // --- ReminderScheduler.alarmIdFor: determinism is what makes sync idempotent ---

    @Test
    public void alarmIdIsStableForSameReference() {
        assertEquals(ReminderScheduler.alarmIdFor("reminder-a"),
                ReminderScheduler.alarmIdFor("reminder-a"));
    }

    @Test
    public void alarmIdDiffersForDifferentReferences() {
        assertNotEquals(ReminderScheduler.alarmIdFor("reminder-a"),
                ReminderScheduler.alarmIdFor("reminder-b"));
    }

    @Test
    public void alarmIdIsIndependentOfCallOrder() {
        // A second sync must recompute the same ids, so storing the id and
        // recomputing it later can never disagree.
        int first = ReminderScheduler.alarmIdFor("reminder-c");
        ReminderScheduler.alarmIdFor("something-else");
        assertEquals(first, ReminderScheduler.alarmIdFor("reminder-c"));
    }

    // --- Reminder.hasSameScheduleAs: the unchanged / changed decision ---

    @Test
    public void identicalScheduleCountsAsUnchanged() {
        Reminder a = new Reminder("r1", "Study DSA", SEVEN_PM, false);
        Reminder b = new Reminder("r1", "Study DSA", SEVEN_PM, false);
        assertTrue(a.hasSameScheduleAs(b));
    }

    @Test
    public void movedTimeCountsAsChanged() {
        Reminder before = new Reminder("r1", "Study DSA", SEVEN_PM, false);
        Reminder after = new Reminder("r1", "Study DSA", EIGHT_PM, false);
        assertFalse(before.hasSameScheduleAs(after));
    }

    @Test
    public void completedStateCountsAsChanged() {
        Reminder open = new Reminder("r1", "Study DSA", SEVEN_PM, false);
        Reminder done = new Reminder("r1", "Study DSA", SEVEN_PM, true);
        assertFalse(open.hasSameScheduleAs(done));
    }

    @Test
    public void renamingDoesNotCountAsChanged() {
        // A pure rename must not disturb an alarm that is already correct.
        Reminder before = new Reminder("r1", "Study DSA", SEVEN_PM, false);
        Reminder after = new Reminder("r1", "Study DSA (rev 2)", SEVEN_PM, false);
        assertTrue(before.hasSameScheduleAs(after));
    }

    @Test
    public void nullIsNeverUnchanged() {
        Reminder a = new Reminder("r1", "Study DSA", SEVEN_PM, false);
        assertFalse(a.hasSameScheduleAs(null));
    }

    // --- Reminder.isSchedulable: completed and undated reminders hold no alarm ---

    @Test
    public void completedReminderIsNotSchedulable() {
        assertFalse(new Reminder("r1", "Study DSA", SEVEN_PM, true).isSchedulable());
    }

    @Test
    public void undatedReminderIsNotSchedulable() {
        // Parsing a reminder whose due date could not be read must not create
        // an alarm at the epoch, which would fire immediately.
        assertFalse(new Reminder("r1", "Study DSA", 0L, false).isSchedulable());
    }

    @Test
    public void completedUndatedReminderIsNotSchedulable() {
        assertFalse(new Reminder("r1", "", 0L, true).isSchedulable());
    }

    @Test
    public void openDatedReminderIsSchedulable() {
        assertTrue(new Reminder("r1", "Study DSA", SEVEN_PM, false).isSchedulable());
    }

    // --- Reminder construction guards ---

    @Test(expected = IllegalArgumentException.class)
    public void emptyIdIsRejected() {
        new Reminder("", "Study DSA", SEVEN_PM, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullIdIsRejected() {
        new Reminder(null, "Study DSA", SEVEN_PM, false);
    }

    @Test
    public void nullTitleBecomesEmptyRatherThanCrashing() {
        Reminder reminder = new Reminder("r1", null, SEVEN_PM, false);
        assertEquals("", reminder.getTitle());
    }

    @Test
    public void equalRemindersCompareEqual() {
        assertEquals(new Reminder("r1", "Study DSA", SEVEN_PM, false),
                new Reminder("r1", "Study DSA", SEVEN_PM, false));
    }

    @Test
    public void differentRemindersDoNotCompareEqual() {
        assertNotEquals(new Reminder("r1", "Study DSA", SEVEN_PM, false),
                new Reminder("r2", "Study DSA", SEVEN_PM, false));
    }
}
