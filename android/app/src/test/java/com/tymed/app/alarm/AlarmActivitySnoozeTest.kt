package com.tymed.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Drives the *real* [AlarmActivity] (not a hand-mirrored copy of its request-code math) to
 * guard against the snooze-request-code regression: AndroidAlarmSchedulerSnoozeTest proves
 * AndroidAlarmScheduler cleans up correctly *given* a particular request-code convention, but
 * says nothing about whether AlarmActivity itself still follows that convention. These tests
 * click the actual Snooze button and call the actual (private) cancelSnoozeChain(), so a future
 * edit to AlarmActivity that reintroduces requestCode-based (rather than scheduleId-based)
 * offsets fails here even if AndroidAlarmSchedulerSnoozeTest's helpers still "pass".
 */
@RunWith(RobolectricTestRunner::class)
class AlarmActivitySnoozeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun ringIntent(scheduleId: Int, requestCode: Int): Intent =
        Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, 1)
            putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, "Aspirin")
            putExtra(AlarmReceiver.EXTRA_DOSAGE, "81mg")
        }

    private fun findButton(root: View, predicate: (String) -> Boolean): Button {
        if (root is Button && predicate(root.text.toString())) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findButtonOrNull(root.getChildAt(i), predicate)?.let { return it }
            }
        }
        error("No button matching predicate found")
    }

    private fun findButtonOrNull(root: View, predicate: (String) -> Boolean): Button? = try {
        findButton(root, predicate)
    } catch (e: IllegalStateException) {
        null
    }

    private fun pendingBroadcastExists(requestCode: Int): Boolean =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null

    @Test
    fun `tapping Snooze on a already-snoozed ring arms the follow-up at scheduleId plus OFFSET, not requestCode plus OFFSET`() {
        val scheduleId = 41
        // Simulate viewing the *second* ring (the first snooze's follow-up): the activity's own
        // requestCode extra is already scheduleId + OFFSET here, exactly as AlarmReceiver passes
        // it through when Chain B fires.
        val thisRingRequestCode = scheduleId + SNOOZE_REQUEST_CODE_OFFSET

        val controller = Robolectric.buildActivity(AlarmActivity::class.java, ringIntent(scheduleId, thisRingRequestCode))
        val activity = controller.create().start().resume().get()

        val snoozeButton = findButton(activity.window.decorView) { it.startsWith("Snooze") }
        snoozeButton.performClick()

        val buggyCompoundedRequestCode = thisRingRequestCode + SNOOZE_REQUEST_CODE_OFFSET // scheduleId + 2*OFFSET
        val fixedRequestCode = scheduleId + SNOOZE_REQUEST_CODE_OFFSET // scheduleId + OFFSET (replaces itself)

        assertEquals(
            "exactly one alarm should be armed by a single Snooze tap",
            1,
            shadowOf(alarmManager).scheduledAlarms.size,
        )
        assertTrue(
            "armSnooze() armed the compounded requestCode+OFFSET slot instead of scheduleId+OFFSET " +
                "— re-snoozing will create an ever-deeper alarm that Taken can never find",
            pendingBroadcastExists(fixedRequestCode),
        )
        assertFalse(
            "armSnooze() should never arm the compounded (requestCode + OFFSET) slot",
            pendingBroadcastExists(buggyCompoundedRequestCode),
        )

        controller.destroy()
    }

    @Test
    fun `AlarmActivity's private cancelSnoozeChain cancels scheduleId plus OFFSET even when viewing an offset ring`() {
        val scheduleId = 43
        val snoozeRequestCode = scheduleId + SNOOZE_REQUEST_CODE_OFFSET

        // A snooze follow-up is already pending (as if the user snoozed once already).
        armAlarm(
            context,
            AlarmSchedule(
                triggerAtMillis = System.currentTimeMillis() + 5 * 60_000L,
                requestCode = snoozeRequestCode,
                scheduleId = scheduleId,
                medicationId = 1,
                profileId = 1,
                medicationName = "Aspirin",
                dosage = "81mg",
                isPrimary = false,
                hour = 0,
                minute = 0,
                recurrenceType = "daily",
                daysOfWeek = "",
                startDate = "",
                endDate = "",
            ),
        )
        assertTrue(pendingBroadcastExists(snoozeRequestCode))

        // View this activity *as the snooze-follow-up ring itself* (requestCode == scheduleId +
        // OFFSET, same as AlarmReceiver would pass for Chain B) — the scenario that exposed the
        // original bug, where cancelSnoozeChain() used to compute requestCode + OFFSET instead
        // of scheduleId + OFFSET.
        val controller = Robolectric.buildActivity(AlarmActivity::class.java, ringIntent(scheduleId, snoozeRequestCode))
        val activity = controller.create().get()

        val method = AlarmActivity::class.java.getDeclaredMethod("cancelSnoozeChain").apply { isAccessible = true }
        method.invoke(activity)

        assertFalse(
            "cancelSnoozeChain() did not cancel the live snooze at scheduleId + OFFSET",
            pendingBroadcastExists(snoozeRequestCode),
        )

        controller.destroy()
    }
}
