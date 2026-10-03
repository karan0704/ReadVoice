package com.readvoice.app.ui.common

import com.readvoice.app.ui.common.RelativeTime.Span
import com.readvoice.app.ui.common.RelativeTime.Unit
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 1_000 * day

    @Test
    fun spansUseTheLargestFittingUnit() {
        assertEquals(Span(Unit.NOW, 0), RelativeTime.between(now, now - 30_000))
        assertEquals(Span(Unit.MINUTES, 5), RelativeTime.between(now, now - 5 * minute))
        assertEquals(Span(Unit.HOURS, 2), RelativeTime.between(now, now - 2 * hour))
        assertEquals(Span(Unit.DAYS, 3), RelativeTime.between(now, now - 3 * day))
        assertEquals(Span(Unit.WEEKS, 2), RelativeTime.between(now, now - 15 * day))
        assertEquals(Span(Unit.YEARS, 1), RelativeTime.between(now, now - 400 * day))
    }

    @Test
    fun aTimeInTheFutureCountsAsNow() {
        assertEquals(Span(Unit.NOW, 0), RelativeTime.between(now, now + hour))
    }
}
