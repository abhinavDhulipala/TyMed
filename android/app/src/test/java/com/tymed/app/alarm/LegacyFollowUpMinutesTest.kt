package com.tymed.app.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tymed.app.data.entity.DEFAULT_PROFILE_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Guards the upgrade path from before multi-profile: the follow-up interval was mirrored under
 * one unscoped key, which AlarmActivity no longer reads. */
@RunWith(RobolectricTestRunner::class)
class LegacyFollowUpMinutesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val defaultProfileKey = prefKeyFollowUpMinutes(DEFAULT_PROFILE_ID.toInt())

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `moves the legacy value onto the default profile and drops the old key`() {
        prefs.edit().putInt("follow_up_minutes", 15).commit()

        migrateLegacyFollowUpMinutes(context)

        assertEquals(15, prefs.getInt(defaultProfileKey, -1))
        assertFalse(prefs.contains("follow_up_minutes"))
    }

    @Test
    fun `never overwrites a value already saved under the per-profile key`() {
        prefs.edit().putInt("follow_up_minutes", 15).putInt(defaultProfileKey, 20).commit()

        migrateLegacyFollowUpMinutes(context)

        assertEquals(20, prefs.getInt(defaultProfileKey, -1))
        assertFalse(prefs.contains("follow_up_minutes"))
    }

    @Test
    fun `does nothing on an install that never had the legacy key`() {
        migrateLegacyFollowUpMinutes(context)

        assertFalse(prefs.contains(defaultProfileKey))
    }
}
