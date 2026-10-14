package com.tripplanner.app.model

import org.junit.Assert.*
import org.junit.Test

class TripEditorSnapshotTest {
    private val item = TripObjectDraft(1, TripObjectType.HOTEL, "Hotel", 1,
        mapOf(TripObjectAttribute.NOTES to "Late arrival"), emptySet())
    private val saved = TripEditorSnapshot("London", "2026-10-14", "2026-10-16", listOf(item))

    @Test fun unchangedDraftMatchesSavedSnapshot() {
        assertEquals(saved, saved.copy(items = listOf(item.copy())))
    }

    @Test fun editsToDatesNotesOrderLinksAndMembershipAreUnsaved() {
        assertNotEquals(saved, saved.copy(startDate = "2026-10-15"))
        assertNotEquals(saved, saved.copy(destination = "Bath"))
        assertNotEquals(saved, saved.copy(items = emptyList()))
        assertNotEquals(saved, saved.copy(items = listOf(item.copy(priorityOrder = 2))))
        assertNotEquals(saved, saved.copy(items = listOf(item.copy(relatedObjectIds = setOf(2)))))
        assertNotEquals(saved, saved.copy(items = listOf(item.copy(attributes = emptyMap()))))
    }

    @Test fun saveRequiresDestinationAndValidDateRange() {
        assertTrue(saved.canSave)
        assertFalse(saved.copy(destination = " ").canSave)
        assertFalse(saved.copy(endDate = "2026-10-13").canSave)
        assertFalse(saved.copy(startDate = "invalid").canSave)
    }
}
