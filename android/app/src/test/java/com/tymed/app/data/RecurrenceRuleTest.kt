package com.tymed.app.data

import com.tymed.app.data.entity.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun rule(
    recurrenceType: String = RecurrenceType.DAILY,
    daysOfWeek: List<Int>? = null,
    startDate: String? = null,
    endDate: String? = null,
) = RecurrenceRule(recurrenceType, daysOfWeek, startDate, endDate)

class RecurrenceRuleTest {

    @Test
    fun `daily is active on every date`() {
        assertTrue(RecurrenceRule.isActiveOn(rule(RecurrenceType.DAILY), "2026-09-20"))
        assertTrue(RecurrenceRule.isActiveOn(rule(RecurrenceType.DAILY), "2027-01-01"))
    }

    @Test
    fun `weekly matches only the selected weekdays`() {
        // 2026-09-20 is a Sunday.
        val mwf = rule(RecurrenceType.WEEKLY, daysOfWeek = listOf(1, 3, 5))
        assertTrue(RecurrenceRule.isActiveOn(mwf, "2026-09-21")) // Monday
        assertTrue(RecurrenceRule.isActiveOn(mwf, "2026-09-23")) // Wednesday
        assertTrue(RecurrenceRule.isActiveOn(mwf, "2026-09-25")) // Friday
        assertFalse(RecurrenceRule.isActiveOn(mwf, "2026-09-20")) // Sunday
        assertFalse(RecurrenceRule.isActiveOn(mwf, "2026-09-22")) // Tuesday
        assertFalse(RecurrenceRule.isActiveOn(mwf, "2026-09-26")) // Saturday
    }

    @Test
    fun `weekly is never active with no days selected`() {
        assertFalse(RecurrenceRule.isActiveOn(rule(RecurrenceType.WEEKLY, daysOfWeek = emptyList()), "2026-09-21"))
    }

    @Test
    fun `monthly fires on the same day-of-month as the start date`() {
        val r = rule(RecurrenceType.MONTHLY, startDate = "2026-09-15")
        assertTrue(RecurrenceRule.isActiveOn(r, "2026-09-15"))
        assertTrue(RecurrenceRule.isActiveOn(r, "2026-10-15"))
        assertFalse(RecurrenceRule.isActiveOn(r, "2026-09-14"))
        assertFalse(RecurrenceRule.isActiveOn(r, "2026-09-16"))
    }

    @Test
    fun `monthly clamps to the last day of shorter months when anchored on the 31st`() {
        val r = rule(RecurrenceType.MONTHLY, startDate = "2026-01-31")
        assertTrue(RecurrenceRule.isActiveOn(r, "2026-01-31"))
        assertTrue(RecurrenceRule.isActiveOn(r, "2026-02-28")) // Feb 2026 has 28 days
        assertTrue(RecurrenceRule.isActiveOn(r, "2026-03-31"))
        assertTrue(RecurrenceRule.isActiveOn(r, "2026-04-30")) // April has 30 days
    }

    @Test
    fun `monthly is never active without a start date`() {
        assertFalse(RecurrenceRule.isActiveOn(rule(RecurrenceType.MONTHLY, startDate = null), "2026-09-15"))
    }

    @Test
    fun `is inactive before the start date`() {
        val r = rule(RecurrenceType.DAILY, startDate = "2026-09-20")
        assertFalse(RecurrenceRule.isActiveOn(r, "2026-09-19"))
        assertTrue(RecurrenceRule.isActiveOn(r, "2026-09-20"))
    }

    @Test
    fun `is inactive after the end date (inclusive boundary)`() {
        val r = rule(RecurrenceType.DAILY, endDate = "2026-09-25")
        assertTrue(RecurrenceRule.isActiveOn(r, "2026-09-25"))
        assertFalse(RecurrenceRule.isActiveOn(r, "2026-09-26"))
    }

    @Test
    fun `equals treats identical rules as equal`() {
        val a = rule(RecurrenceType.WEEKLY, daysOfWeek = listOf(1, 3))
        val b = rule(RecurrenceType.WEEKLY, daysOfWeek = listOf(1, 3))
        assertTrue(RecurrenceRule.equals(a, b))
    }

    @Test
    fun `equals detects a changed field`() {
        val a = rule(RecurrenceType.WEEKLY, daysOfWeek = listOf(1, 3))
        assertFalse(RecurrenceRule.equals(a, a.copy(daysOfWeek = listOf(1, 4))))
        assertFalse(RecurrenceRule.equals(a, a.copy(endDate = "2026-12-01")))
        assertFalse(RecurrenceRule.equals(a, a.copy(recurrenceType = RecurrenceType.DAILY)))
    }
}
