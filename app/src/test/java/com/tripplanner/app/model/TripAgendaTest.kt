package com.tripplanner.app.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TripAgendaTest {
    private fun item(id: Long, order: Int, time: String? = null) = TripObjectDraft(
        id, TripObjectType.FREE_ATTRACTION, "Place $id", order,
        time?.let { mapOf(TripObjectAttribute.PLANNED_VISIT_DATE_TIME to it) }.orEmpty(),
        setOf(1L, 2L, 3L) - id
    )

    @Test fun acceptsLegacyAndIsoDateTimes() {
        assertEquals(parseTripDateTime("2026-10-14 09:30"), parseTripDateTime("2026-10-14T09:30"))
        assertNull(parseTripDateTime("tomorrow"))
        assertEquals(LocalDate.of(2026, 10, 14), parseTripDateTime("2026-10-14")?.toLocalDate())
    }

    @Test fun validatesBothDatesAndOrdering() {
        assertTrue(validTripDates("", ""))
        assertTrue(validTripDates("2026-10-14", "2026-10-14"))
        assertFalse(validTripDates("2026-10-15", "2026-10-14"))
        assertFalse(validTripDates("2026-02-30", "2026-03-01"))
        assertFalse(validTripDates("2026-10-14", ""))
    }

    @Test fun includesEmptyDaysAndOutOfRangeActivities() {
        val dates = agendaDates("2026-10-14", "2026-10-16", listOf(item(1, 1, "2026-10-18 12:00")))
        assertEquals(listOf(14, 15, 16, 18), dates.map { it.dayOfMonth })
    }

    @Test fun chronologicallyOrdersActivitiesAndKeepsUnscheduledLast() {
        assertEquals(listOf(3L, 2L, 1L), orderedAgenda(listOf(
            item(1, 1), item(2, 2, "2026-10-14 12:00"), item(3, 3, "2026-10-14 09:00")
        )).map { it.id })
    }

    @Test fun reorderingSwapsUniqueOrdersWithoutChangingLinks() {
        val original = listOf(item(1, 2), item(2, 7), item(3, 11))
        val moved = moveTripItem(original, 2, -1)
        assertEquals(listOf(7, 2, 11), moved.map { it.priorityOrder })
        assertEquals(original.map { it.relatedObjectIds }, moved.map { it.relatedObjectIds })
        assertEquals(original, moveTripItem(original, 1, -1))
    }

    @Test fun removingItemCleansDanglingLinks() {
        val remaining = removeTripItem(listOf(item(1, 1), item(2, 2), item(3, 3)), 2)
        assertEquals(listOf(1L, 3L), remaining.map { it.id })
        assertTrue(remaining.none { 2L in it.relatedObjectIds })
    }
}
