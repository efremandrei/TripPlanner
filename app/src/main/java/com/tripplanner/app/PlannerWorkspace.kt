package com.tripplanner.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripplanner.app.data.TripRepository
import com.tripplanner.app.data.local.entity.TripEntity
import com.tripplanner.app.data.local.entity.PoolItemEntity
import com.tripplanner.app.model.*
import com.tripplanner.app.util.GoogleMapsIntents
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun ActionIcon(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
}

internal fun TripObjectType.icon(): ImageVector = when (this) {
    TripObjectType.FAMILY_MEMBER -> Icons.Outlined.People
    TripObjectType.HOTEL -> Icons.Outlined.Hotel
    TripObjectType.FOOD_PLACE -> Icons.Outlined.Restaurant
    TripObjectType.TRANSPORTATION -> Icons.Outlined.DirectionsTransit
    TripObjectType.SHOP -> Icons.Outlined.ShoppingBag
    TripObjectType.PAID_ATTRACTION -> Icons.Outlined.ConfirmationNumber
    TripObjectType.FREE_ATTRACTION -> Icons.Outlined.Park
}

internal fun TripObjectType.tint(): Color = when (this) {
    TripObjectType.FAMILY_MEMBER -> Color(0xFF8B6BC1)
    TripObjectType.HOTEL -> Color(0xFF4189CF)
    TripObjectType.FOOD_PLACE -> Color(0xFFD98238)
    TripObjectType.TRANSPORTATION -> Color(0xFF3CA797)
    TripObjectType.SHOP -> Color(0xFFB86D96)
    TripObjectType.PAID_ATTRACTION -> Color(0xFFC8A136)
    TripObjectType.FREE_ATTRACTION -> Color(0xFF64A773)
}

@Composable
internal fun ItemSymbol(type: TripObjectType) {
    Box(Modifier.size(40.dp).background(type.tint().copy(alpha = .14f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center) {
        Icon(type.icon(), null, tint = type.tint(), modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun SectionHeading(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@Composable
internal fun EmptyState(title: String, subtitle: String, icon: ImageVector = Icons.Outlined.Luggage) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private enum class HubTab { Home, Library, Account }

@Composable
internal fun PlannerHub(
    trips: List<TripEntity>, header: @Composable () -> Unit,
    onPlan: () -> Unit, onTravel: () -> Unit, onOpenTrip: (Long) -> Unit, onEditTrip: (Long) -> Unit,
    library: @Composable () -> Unit, account: @Composable ColumnScope.() -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(HubTab.Home) }
    BackHandler(tab != HubTab.Home) { tab = HubTab.Home }
    Scaffold(topBar = { Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) { header() } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                HubTab.entries.forEach { destination ->
                    NavigationBarItem(selected = tab == destination, onClick = { tab = destination },
                        icon = { Icon(when (destination) {
                            HubTab.Home -> Icons.Outlined.Home
                            HubTab.Library -> Icons.Outlined.Bookmarks
                            HubTab.Account -> Icons.Outlined.PersonOutline
                        }, null) }, label = { Text(destination.name) })
                }
            }
        }) { padding ->
        when (tab) {
            HubTab.Home -> LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { SectionHeading("Your journeys", trips.size.toString() + " saved trips") }
                item { HubAction("Add new / edit trip", "Plan your next journey", Icons.Outlined.EditCalendar, onPlan) }
                item { HubAction("Use existing trip", "Open your itinerary", Icons.Outlined.Explore, onTravel) }
                item { HorizontalDivider() }
                item { SectionHeading("Recent trips", action = { TextButton(onClick = onPlan) { Text("View all") } }) }
                if (trips.isEmpty()) item {
                    Image(painterResource(R.drawable.travel_cover), null, Modifier.fillMaxWidth().height(128.dp),
                        contentScale = ContentScale.Crop)
                    EmptyState("A new journey awaits", "Your saved trips will appear here.")
                }
                items(trips.take(4), key = { it.id }) { trip ->
                    TripEntry(trip, onClick = { onOpenTrip(trip.id) }, actions = {
                        ActionIcon("Edit trip", Icons.Outlined.Edit) { onEditTrip(trip.id) }
                    })
                }
            }
            HubTab.Library -> Box(Modifier.fillMaxSize().padding(padding)) { library() }
            HubTab.Account -> Column(Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp), content = account)
        }
    }
}

@Composable
private fun HubAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TripEntry(trip: TripEntity, onClick: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Card(onClick = onClick, shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Luggage, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(trip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(readableDates(trip.startDate, trip.endDate), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
    }
}

internal fun readableDates(start: String, end: String): String {
    fun pretty(value: String) = runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy")) }.getOrDefault(value)
    return if (start.isBlank() && end.isBlank()) "Dates not set" else pretty(start) + " - " + pretty(end)
}

@Composable
internal fun TripBrowser(trips: List<TripEntity>, archived: List<TripEntity>, header: @Composable () -> Unit,
    status: String?, onNew: () -> Unit, onImport: () -> Unit, onEdit: (Long) -> Unit,
    onExport: (Long) -> Unit, onArchive: (TripEntity) -> Unit) {
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val source = if (showArchive) archived else trips
    val visible = source.filter { it.title.contains(query, true) || it.destination.contains(query, true) }
    Scaffold(topBar = header) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("New Trip") } }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(!showArchive, { showArchive = false }, { Text("Active (" + trips.size + ")") })
                Spacer(Modifier.width(8.dp))
                FilterChip(showArchive, { showArchive = true }, { Text("Archive (" + archived.size + ")") })
                Spacer(Modifier.weight(1f))
                ActionIcon("Import trip", Icons.Outlined.FileDownload, onClick = onImport)
            } }
            item { SearchField(query, { query = it }, "Search trips") }
            status?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }
            if (visible.isEmpty()) item { EmptyState(if (query.isBlank()) "No trips here yet" else "No matching trips",
                if (showArchive) "Archived trips will appear here." else "Create a trip or import a saved plan.") }
            items(visible, key = { it.id }) { trip ->
                TripEntry(trip, { onEdit(trip.id) }) {
                    ActionIcon("Export " + trip.title, Icons.Outlined.FileUpload) { onExport(trip.id) }
                    ActionIcon(if (showArchive) "Restore " + trip.title else "Archive " + trip.title,
                        if (showArchive) Icons.Outlined.Unarchive else Icons.Outlined.Archive) { onArchive(trip) }
                }
            }
        }
    }
}

@Composable
internal fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(query, onChange, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) ActionIcon("Clear search", Icons.Outlined.Close) { onChange("") } },
        shape = RoundedCornerShape(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceTabs(labels: List<String>, selected: String, onSelect: (String) -> Unit) {
    ScrollableTabRow(selectedTabIndex = labels.indexOf(selected).coerceAtLeast(0),
        edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background) {
        labels.forEach { label -> Tab(selected == label, { onSelect(label) }, text = { Text(label, maxLines = 1) }) }
    }
}

@Composable
internal fun ItemTypePicker(selected: TripObjectType, onSelect: (TripObjectType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Icon(selected.icon(), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(selected.displayName, Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, "Select item type")
        }
        DropdownMenu(expanded, { expanded = false }) {
            TripObjectType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.displayName) }, leadingIcon = { Icon(type.icon(), null) },
                    onClick = { onSelect(type); expanded = false })
            }
        }
    }
}

@Composable
internal fun DateTimeInput(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val parsed = parseTripDateTime(value)
    val initial = parsed ?: java.time.LocalDateTime.now().withSecond(0).withNano(0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                android.app.DatePickerDialog(context, { _, year, month, date ->
                    val selectedDate = LocalDate.of(year, month + 1, date)
                    android.app.TimePickerDialog(context, { _, hour, minute ->
                        onChange(formatTripDateTime(selectedDate.atTime(hour, minute)))
                    }, initial.hour, initial.minute, true).show()
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (value.isBlank()) "Choose date & time" else value, maxLines = 2)
            }
            if (value.isNotBlank()) ActionIcon("Clear " + label, Icons.Outlined.Close) { onChange("") }
        }
    }
}

@Composable
internal fun TypeFilters(selected: TripObjectType?, onSelect: (TripObjectType?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected == null, { onSelect(null) }, { Text("All") })
        TripObjectType.entries.forEach { type ->
            FilterChip(selected == type, { onSelect(if (selected == type) null else type) },
                { Text(type.displayName) }, leadingIcon = { Icon(type.icon(), null, Modifier.size(18.dp)) })
        }
    }
}

@Composable
internal fun GeneralLibrary(trips: List<TripEntity>, repository: TripRepository) {
    val context = LocalContext.current
    val database = (context.applicationContext as TripPlannerApplication).database
    var poolTripId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf<TripObjectType?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<TripObjectDraft?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val allGeneral by database.poolItemDao().observeGeneralPoolItems().collectAsState(emptyList())
    val pool by remember(database, poolTripId) {
        poolTripId?.let { database.poolItemDao().observeTripPoolItems(it) }
            ?: database.poolItemDao().observeGeneralPoolItems()
    }.collectAsState(emptyList())
    BackHandler(editor != null) { editor = null }
    if (editor != null) {
        LibraryEditor(editor!!, candidates = allGeneral, onCancel = { editor = null }, onSave = { draft ->
            scope.launch {
                runCatching { repository.saveGeneralItem(draft) }.onSuccess {
                    editor = null; status = draft.name + " saved to General"
                }.onFailure { status = it.message }
            }
        }, status = status)
        return
    }
    val visible = pool.filter { (filter == null || it.type == filter) && it.name.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading("Item library", pool.size.toString() + " reusable items", action = {
            ActionIcon("New library item", Icons.Outlined.Add) {
                status = null; editor = TripObjectDraft(-1, TripObjectType.FREE_ATTRACTION, "", 0, emptyMap(), emptySet())
            }
        }) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(poolTripId == null, { poolTripId = null }, { Text("General") })
            trips.forEach { trip -> FilterChip(poolTripId == trip.id, { poolTripId = trip.id }, { Text(trip.title) }) }
        } }
        item { SearchField(query, { query = it }, "Search saved items") }
        item { TypeFilters(filter) { filter = it } }
        status?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        if (visible.isEmpty()) item { EmptyState("No items found", "Add an item or choose another pool.", Icons.Outlined.Bookmarks) }
        items(visible, key = { it.id }) { entry ->
            Row(Modifier.fillMaxWidth().clickable { scope.launch {
                editor = repository.getPoolItemDraft(entry.id); status = null
            } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemSymbol(entry.type)
                Column(Modifier.weight(1f)) {
                    Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.type.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, "Edit item", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun LibraryEditor(draft: TripObjectDraft, candidates: List<PoolItemEntity>, onCancel: () -> Unit, onSave: (TripObjectDraft) -> Unit, status: String?) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var type by remember(draft.id) { mutableStateOf(draft.type) }
    var priority by remember(draft.id) { mutableStateOf(draft.priorityOrder.takeIf { it > 0 }?.toString().orEmpty()) }
    var related by remember(draft.id) { mutableStateOf(draft.relatedObjectIds) }
    var linkedSearch by remember { mutableStateOf("") }
    val invalidPriority = priority.isNotBlank() && (priority.toIntOrNull() ?: 0) < 1
    val attributes = remember(draft.id) { mutableStateMapOf<TripObjectAttribute, String>().apply { putAll(draft.attributes) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIcon("Back to library", Icons.AutoMirrored.Outlined.ArrowBack, onClick = onCancel)
            Text(if (draft.id > 0) "Edit library item" else "New library item", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text("General pool", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (draft.id <= 0) ItemTypePicker(type) { type = it }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") })
        OutlinedTextField(priority, { priority = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
            label = { Text("Priority / order") }, placeholder = { Text("Automatic") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        AttributeSections(type, { attributes[it].orEmpty() }, { key, value -> attributes[key] = value }, includeNotes = true)
        ExpandableSection("Linked items (" + related.size + ")") {
            SearchField(linkedSearch, { linkedSearch = it }, "Search linked items")
            candidates.filter { it.id != draft.id && it.name.contains(linkedSearch, true) }.forEach { candidate ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(candidate.id in related, { checked ->
                        related = if (checked) related + candidate.id else related - candidate.id
                    })
                    Text(candidate.name, Modifier.weight(1f))
                }
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(draft.copy(type = type, name = name, priorityOrder = priority.toIntOrNull() ?: 0, relatedObjectIds = related,
            attributes = type.attributes.associateWith { attributes[it].orEmpty().trim() }.filterValues { it.isNotBlank() })) },
            enabled = name.isNotBlank() && !invalidPriority, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(8.dp)); Text("Save item")
        }
    }
}

@Composable
internal fun AttributeSections(type: TripObjectType, value: (TripObjectAttribute) -> String,
    onChange: (TripObjectAttribute, String) -> Unit, includeNotes: Boolean = false) {
    val advanced = setOf(TripObjectAttribute.LATITUDE, TripObjectAttribute.LONGITUDE,
        TripObjectAttribute.DEPARTURE_LATITUDE, TripObjectAttribute.DEPARTURE_LONGITUDE,
        TripObjectAttribute.ARRIVAL_LATITUDE, TripObjectAttribute.ARRIVAL_LONGITUDE,
        TripObjectAttribute.GOOGLE_PLACE_ID, TripObjectAttribute.GOOGLE_MAPS_URL,
        TripObjectAttribute.ICONIC_PICTURE, TripObjectAttribute.ROUTE_MAP, TripObjectAttribute.LOCATION_MAP_PICTURE)
    val contact = setOf(TripObjectAttribute.WEBSITE_URL, TripObjectAttribute.EMAIL_ADDRESS,
        TripObjectAttribute.PHONE_NUMBER, TripObjectAttribute.TICKET_URL, TripObjectAttribute.CHECK_IN_URL,
        TripObjectAttribute.SCHEDULE_URL, TripObjectAttribute.LIVE_STATUS_URL)
    val fields = type.attributes.filterNot { it == TripObjectAttribute.NOTES }
    val groups = listOf("Details" to fields.filterNot { it in advanced || it in contact },
        "Contact & links" to fields.filter { it in contact }, "Photos & map details" to fields.filter { it in advanced })
    groups.filter { it.second.isNotEmpty() }.forEach { (title, entries) ->
        ExpandableSection(title, initiallyExpanded = title == "Details") {
            entries.forEach { field -> TripAttributeInput(field, value(field)) { onChange(field, it) } }
        }
    }
    if (includeNotes) TripAttributeInput(TripObjectAttribute.NOTES, value(TripObjectAttribute.NOTES)) { onChange(TripObjectAttribute.NOTES, it) }
}

@Composable
internal fun ExpandableSection(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse $title" else "Expand $title")
        }
        if (expanded) content()
        HorizontalDivider()
    }
}

@Composable
internal fun DayAgenda(items: List<TripObjectDraft>, start: String, end: String, onOpen: (TripObjectDraft) -> Unit) {
    var day by rememberSaveable { mutableStateOf("All") }
    val dates = agendaDates(start, end, items)
    val visible = orderedAgenda(items).filter {
        when (day) { "All" -> true; "Unscheduled" -> it.scheduledDateTime() == null
            else -> it.scheduledDateTime()?.toLocalDate()?.toString() == day }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Itinerary", visible.size.toString() + " stops")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(day == "All", { day = "All" }, { Text("All days") })
            dates.forEach { date -> FilterChip(day == date.toString(), { day = date.toString() },
                { Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM"))) }) }
            FilterChip(day == "Unscheduled", { day = "Unscheduled" }, { Text("Unscheduled") })
        }
        if (visible.isEmpty()) EmptyState("No stops scheduled", "Your activities for this day will appear here.", Icons.Outlined.CalendarMonth)
        var previousDate: String? = null
        visible.forEach { entry ->
            val dateText = entry.scheduledDateTime()?.toLocalDate()?.format(DateTimeFormatter.ofPattern("EEEE, d MMM")) ?: "Unscheduled"
            if (day == "All" && previousDate != dateText) {
                Text(dateText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                previousDate = dateText
            }
            Row(Modifier.fillMaxWidth().clickable { onOpen(entry) }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Text(entry.scheduledDateTime()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",
                    Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
                ItemSymbol(entry.type)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.name, fontWeight = FontWeight.SemiBold)
                    Text(entry.attributes[TripObjectAttribute.ADDRESS] ?: entry.attributes[TripObjectAttribute.DEPARTURE_LOCATION] ?: entry.type.displayName,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    entry.attributes[TripObjectAttribute.NOTES]?.takeIf { it.isNotBlank() }?.let {
                        Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun EditableTripRow(item: TripObjectDraft, onEdit: () -> Unit, onUp: () -> Unit, onDown: () -> Unit,
    canUp: Boolean, canDown: Boolean, onRemove: () -> Unit) {
    var confirmRemoval by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemSymbol(item.type)
            Column(Modifier.weight(1f)) {
                Text("#" + item.priorityOrder + "  " + item.name, fontWeight = FontWeight.SemiBold)
                Text(item.type.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ActionIcon("Edit " + item.name, Icons.Outlined.Edit, onClick = onEdit)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ActionIcon("Move " + item.name + " up", Icons.Outlined.ArrowUpward, canUp, onUp)
            ActionIcon("Move " + item.name + " down", Icons.Outlined.ArrowDownward, canDown, onDown)
            ActionIcon("Remove " + item.name + " from trip", Icons.Outlined.LinkOff) { confirmRemoval = true }
        }
        HorizontalDivider()
    }
    if (confirmRemoval) AlertDialog(onDismissRequest = { confirmRemoval = false },
        title = { Text("Remove from this trip?") }, text = { Text("Saved library items remain in the General pool.") },
        confirmButton = { TextButton(onClick = { confirmRemoval = false; onRemove() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text("Cancel") } })
}

@Composable
internal fun ReadOnlyItem(item: TripObjectDraft, related: List<TripObjectDraft>, onBack: () -> Unit) {
    val context = LocalContext.current
    var actionError by remember { mutableStateOf<String?>(null) }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { actionError = "No app is available for this action." } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIcon("Back to itinerary", Icons.AutoMirrored.Outlined.ArrowBack, onClick = onBack)
            ItemSymbol(item.type); Spacer(Modifier.width(12.dp))
            Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row {
            GoogleMapsIntents.openMapIntent(listOf(item))?.let { intent -> ActionIcon("Open in Google Maps", Icons.Outlined.Map) { open(intent) } }
            item.attributes[TripObjectAttribute.PHONE_NUMBER]?.let { number ->
                ActionIcon("Call", Icons.Outlined.Phone) { open(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
            }
            item.attributes[TripObjectAttribute.WEBSITE_URL]?.let { url ->
                ActionIcon("Open website", Icons.AutoMirrored.Outlined.OpenInNew) { open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
        actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        item.attributes.filterValues { it.isNotBlank() }.forEach { (field, value) ->
            if (field != TripObjectAttribute.GOOGLE_PLACE_ID && field != TripObjectAttribute.ICONIC_PICTURE) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(field.displayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }
        ExpandableSection("Linked items (" + related.size + ")") { related.forEach { Text(it.name, style = MaterialTheme.typography.bodyMedium) } }
    }
}
