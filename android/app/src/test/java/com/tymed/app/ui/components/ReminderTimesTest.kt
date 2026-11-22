package com.tymed.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the duplicate-slot data-loss bug: with an existing 08:00 reminder, "+ Add time" used to
 * add a second "08:00", and editing either slot (matched by value) changed both — saving then
 * deleted the original schedule and its dose history. */
class ReminderTimesTest {
    @Test
    fun `editing one of two identical slots changes only that slot`() {
        assertEquals(listOf("08:00", "13:30"), listOf("08:00", "08:00").withTimeAt(1, "13:30"))
    }

    @Test
    fun `removing one of two identical slots keeps the other`() {
        assertEquals(listOf("08:00"), listOf("08:00", "08:00").withoutTimeAt(0))
    }

    @Test
    fun `a new slot starts an hour after the last one`() {
        assertEquals("09:00", listOf("08:00").nextNewTime())
        assertEquals("21:15", listOf("08:00", "20:15").nextNewTime())
    }

    @Test
    fun `a new slot skips times already in the list and wraps past midnight`() {
        assertEquals("10:00", listOf("09:00", "08:00").nextNewTime())
        assertEquals("00:30", listOf("23:30").nextNewTime())
    }

    @Test
    fun `adding a slot never duplicates an existing time`() {
        var times = listOf("08:00")
        repeat(23) { times = times + times.nextNewTime() }
        assertEquals(24, times.distinct().size)
    }

    @Test
    fun `the first slot defaults to 08 00`() {
        assertEquals("08:00", emptyList<String>().nextNewTime())
    }
}
