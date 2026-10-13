package com.tripplanner.app.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun TripObjectDraft.scheduledDateTime(): LocalDateTime? {
    val attribute = when (type) {
        TripObjectType.FAMILY_MEMBER -> return null
        TripObjectType.HOTEL -> TripObjectAttribute.CHECK_IN
        TripObjectType.TRANSPORTATION -> TripObjectAttribute.DEPARTURE_DATE_TIME
        else -> TripObjectAttribute.PLANNED_VISIT_DATE_TIME
    }
    return parseTripDateTime(attributes[attribute].orEmpty())
}

fun parseTripDateTime(value: String): LocalDateTime? {
    val normalized = value.trim().replace(' ', 'T')
    return runCatching { LocalDateTime.parse(normalized) }.getOrNull()
        ?: runCatching { LocalDate.parse(normalized).atStartOfDay() }.getOrNull()
}

fun formatTripDateTime(value: LocalDateTime): String =
    value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

fun validTripDates(start: String, end: String): Boolean {
    if (start.isBlank() && end.isBlank()) return true
    val first = runCatching { LocalDate.parse(start) }.getOrNull() ?: return false
    val last = runCatching { LocalDate.parse(end) }.getOrNull() ?: return false
    return !last.isBefore(first)
}

fun agendaDates(start: String, end: String, items: List<TripObjectDraft>): List<LocalDate> {
    val scheduled = items.mapNotNull { it.scheduledDateTime()?.toLocalDate() }
    val first = runCatching { LocalDate.parse(start) }.getOrNull()
    val last = runCatching { LocalDate.parse(end) }.getOrNull()
    val range = if (first != null && last != null && !last.isBefore(first)) {
        // Cap the generated selector; scheduled dates outside the range remain reachable.
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.take(366).toList()
    } else emptyList()
    return (range + scheduled).distinct().sorted()
}

fun orderedAgenda(items: List<TripObjectDraft>): List<TripObjectDraft> =
    items.filter { it.type != TripObjectType.FAMILY_MEMBER }
        .sortedWith(compareBy<TripObjectDraft> { it.scheduledDateTime() ?: LocalDateTime.MAX }
            .thenBy { it.priorityOrder })

fun moveTripItem(items: List<TripObjectDraft>, itemId: Long, direction: Int): List<TripObjectDraft> {
    val ordered = items.sortedBy { it.priorityOrder }
    val index = ordered.indexOfFirst { it.id == itemId }
    val next = index + direction
    if (index < 0 || next !in ordered.indices || direction !in listOf(-1, 1)) return items
    val current = ordered[index]
    val neighbor = ordered[next]
    return items.map {
        when (it.id) {
            current.id -> it.copy(priorityOrder = neighbor.priorityOrder)
            neighbor.id -> it.copy(priorityOrder = current.priorityOrder)
            else -> it
        }
    }
}

fun removeTripItem(items: List<TripObjectDraft>, itemId: Long): List<TripObjectDraft> =
    items.filterNot { it.id == itemId }
        .map { it.copy(relatedObjectIds = it.relatedObjectIds - itemId) }
