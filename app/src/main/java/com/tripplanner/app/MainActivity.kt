package com.tripplanner.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.tripplanner.app.data.google.GooglePlaceDetails
import com.tripplanner.app.data.google.GooglePlacesRepository
import com.tripplanner.app.data.TripRepository
import com.tripplanner.app.model.TripObjectAttribute
import com.tripplanner.app.model.TripObjectAttributeInputKind
import com.tripplanner.app.model.TripObjectDraft
import com.tripplanner.app.model.TripObjectType
import com.tripplanner.app.ui.map.TripMapView
import com.tripplanner.app.util.GoogleMapsIntents
import com.tripplanner.app.util.isOnline
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TripPlannerApp()
        }
    }
}

private enum class Screen {
    MainMenu,
    PlanNewTrip
}

private enum class AppSkin(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark")
}

@Composable
private fun TripPlannerApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.MainMenu) }
    var appSkin by rememberSaveable { mutableStateOf(AppSkin.System) }

    TripPlannerTheme(appSkin = appSkin) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (screen) {
                Screen.MainMenu -> MainMenuScreen(
                    appSkin = appSkin,
                    onAppSkinChange = { appSkin = it },
                    onPlanNewTrip = { screen = Screen.PlanNewTrip }
                )

                Screen.PlanNewTrip -> PlanNewTripScreen(
                    appSkin = appSkin,
                    onAppSkinChange = { appSkin = it },
                    onBack = { screen = Screen.MainMenu }
                )
            }
        }
    }
}

@Composable
private fun MainMenuScreen(
    appSkin: AppSkin,
    onAppSkinChange: (AppSkin) -> Unit,
    onPlanNewTrip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Trip Planner",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            SkinSelector(
                modifier = Modifier.fillMaxWidth(),
                selectedSkin = appSkin,
                onSkinSelected = onAppSkinChange
            )
            Spacer(modifier = Modifier.height(36.dp))
            PrimaryMenuButton(
                label = "Plan a new trip",
                onClick = onPlanNewTrip
            )
            Spacer(modifier = Modifier.height(14.dp))
            SecondaryMenuButton(label = "Open existing trip")
            Spacer(modifier = Modifier.height(14.dp))
            SecondaryMenuButton(label = "Trips archive")
        }
    }
}

@Composable
private fun PrimaryMenuButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SecondaryMenuButton(label: String) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = {},
        enabled = false,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanNewTripScreen(
    appSkin: AppSkin,
    onAppSkinChange: (AppSkin) -> Unit,
    onBack: () -> Unit
) {
    var destination by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var selectedObjectType by rememberSaveable { mutableStateOf(TripObjectType.FAMILY_MEMBER) }
    var objectName by rememberSaveable { mutableStateOf("") }
    var priorityOrder by rememberSaveable { mutableStateOf("1") }
    var editingObjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var nextObjectId by rememberSaveable { mutableLongStateOf(1L) }
    var saveStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var googleFetchStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var isFetchingGoogleDetails by remember { mutableStateOf(false) }
    val attributeValues = remember { mutableStateMapOf<TripObjectAttribute, String>() }
    val relatedObjectIds = remember { mutableStateListOf<Long>() }
    val tripObjects = remember { mutableStateListOf<TripObjectDraft>() }
    val context = LocalContext.current
    val database = (context.applicationContext as TripPlannerApplication).database
    val tripRepository = remember(database) { TripRepository(database) }
    val googlePlacesRepository = remember(database) {
        GooglePlacesRepository(
            context = context.applicationContext,
            cacheDao = database.googlePlaceCacheDao()
        )
    }
    val coroutineScope = rememberCoroutineScope()

    fun nextAvailablePriority(): Int {
        val usedOrders = tripObjects.map { it.priorityOrder }.toSet()
        return generateSequence(1) { it + 1 }
            .first { it !in usedOrders }
    }

    fun resetObjectForm() {
        selectedObjectType = TripObjectType.FAMILY_MEMBER
        objectName = ""
        priorityOrder = nextAvailablePriority().toString()
        editingObjectId = null
        attributeValues.clear()
        relatedObjectIds.clear()
        googleFetchStatus = null
    }

    fun editObject(tripObject: TripObjectDraft) {
        selectedObjectType = tripObject.type
        objectName = tripObject.name
        priorityOrder = tripObject.priorityOrder.toString()
        editingObjectId = tripObject.id
        attributeValues.clear()
        attributeValues.putAll(tripObject.attributes)
        relatedObjectIds.clear()
        relatedObjectIds.addAll(tripObject.relatedObjectIds)
        googleFetchStatus = null
    }

    fun applyGooglePlaceDetails(details: GooglePlaceDetails) {
        if (objectName.isBlank()) {
            details.displayName?.let { objectName = it }
        }

        fun setAttribute(attribute: TripObjectAttribute, value: String?) {
            if (attribute in selectedObjectType.attributes && !value.isNullOrBlank()) {
                attributeValues[attribute] = value
            }
        }

        setAttribute(TripObjectAttribute.GOOGLE_PLACE_ID, details.placeId)
        setAttribute(TripObjectAttribute.ADDRESS, details.formattedAddress)
        setAttribute(TripObjectAttribute.LATITUDE, details.latitude?.toString())
        setAttribute(TripObjectAttribute.LONGITUDE, details.longitude?.toString())
        setAttribute(TripObjectAttribute.PHONE_NUMBER, details.phoneNumber)
        setAttribute(TripObjectAttribute.WEBSITE_URL, details.websiteUrl)
        setAttribute(TripObjectAttribute.GOOGLE_MAPS_URL, details.googleMapsUri)
        setAttribute(TripObjectAttribute.WORKING_HOURS, details.openingHours)
    }

    val parsedPriorityOrder = priorityOrder.toIntOrNull()
    val priorityError = when {
        priorityOrder.isBlank() -> null
        parsedPriorityOrder == null -> "Enter a number"
        parsedPriorityOrder < 1 -> "Use 1 or higher"
        tripObjects.any {
            it.priorityOrder == parsedPriorityOrder && it.id != editingObjectId
        } -> "Order number already used"
        else -> null
    }
    val canSaveObject = objectName.isNotBlank() &&
        parsedPriorityOrder != null &&
        priorityError == null
    val googlePlaceId = attributeValues[TripObjectAttribute.GOOGLE_PLACE_ID].orEmpty().trim()
    val canFetchGoogleDetails = selectedObjectType.supportsGooglePlaceDetails() &&
        googlePlaceId.isNotBlank() &&
        !isFetchingGoogleDetails

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan a new trip") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Trip basics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            SkinSelector(
                modifier = Modifier.fillMaxWidth(),
                selectedSkin = appSkin,
                onSkinSelected = onAppSkinChange
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("Destination") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        )
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start date") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End date") },
                        singleLine = true
                    )
                }
            }
            Text(
                text = "Trip objects",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripObjectType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedObjectType == type,
                                onClick = { selectedObjectType = type },
                                label = { Text(type.displayName) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = objectName,
                        onValueChange = { objectName = it },
                        label = { Text("${selectedObjectType.displayName} name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        )
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = priorityOrder,
                        onValueChange = { priorityOrder = it.filter(Char::isDigit) },
                        label = { Text("Priority / order") },
                        singleLine = true,
                        isError = priorityError != null,
                        supportingText = priorityError?.let { error ->
                            { Text(error) }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                    selectedObjectType.attributes.forEach { attribute ->
                        TripAttributeInput(
                            attribute = attribute,
                            value = attributeValues[attribute].orEmpty(),
                            onValueChange = { attributeValues[attribute] = it }
                        )
                    }
                    if (selectedObjectType.supportsGooglePlaceDetails()) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            onClick = {
                                coroutineScope.launch {
                                    isFetchingGoogleDetails = true
                                    googleFetchStatus = null
                                    val cachedOnly = !context.isOnline()
                                    runCatching {
                                        if (cachedOnly) {
                                            googlePlacesRepository.getCachedPlace(googlePlaceId)
                                                ?: error("No cached Google details for this place ID")
                                        } else {
                                            googlePlacesRepository.fetchAndCachePlace(googlePlaceId)
                                        }
                                    }.onSuccess { details ->
                                        applyGooglePlaceDetails(details)
                                        googleFetchStatus = if (cachedOnly) {
                                            "Cached Google details applied"
                                        } else {
                                            "Google details fetched"
                                        }
                                    }.onFailure { error ->
                                        googleFetchStatus = error.message ?: "Google details unavailable"
                                    }
                                    isFetchingGoogleDetails = false
                                }
                            },
                            enabled = canFetchGoogleDetails,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (isFetchingGoogleDetails) {
                                    "Fetching Google details"
                                } else {
                                    "Fetch Google details"
                                }
                            )
                        }
                        googleFetchStatus?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val relatedObjectCandidates = tripObjects
                        .filter { it.id != editingObjectId }
                        .sortedBy { it.priorityOrder }
                    if (relatedObjectCandidates.isNotEmpty()) {
                        Text(
                            text = "Related objects",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            relatedObjectCandidates.forEach { tripObject ->
                                val isRelated = tripObject.id in relatedObjectIds
                                FilterChip(
                                    selected = isRelated,
                                    onClick = {
                                        if (isRelated) {
                                            relatedObjectIds.remove(tripObject.id)
                                        } else {
                                            relatedObjectIds.add(tripObject.id)
                                        }
                                    },
                                    label = {
                                        Text("${tripObject.priorityOrder}. ${tripObject.name}")
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            onClick = {
                                val objectId = editingObjectId ?: nextObjectId++
                                val savedObject = TripObjectDraft(
                                    id = objectId,
                                    type = selectedObjectType,
                                    name = objectName.trim(),
                                    priorityOrder = parsedPriorityOrder ?: nextAvailablePriority(),
                                    attributes = selectedObjectType.attributes
                                        .associateWith { attributeValues[it].orEmpty().trim() }
                                        .filterValues { it.isNotBlank() },
                                    relatedObjectIds = relatedObjectIds.toSet()
                                )

                                upsertTripObject(
                                    tripObjects = tripObjects,
                                    savedObject = savedObject
                                )
                                resetObjectForm()
                            },
                            enabled = canSaveObject,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (editingObjectId == null) "Add object" else "Save object")
                        }
                        if (editingObjectId != null) {
                            OutlinedButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                onClick = { resetObjectForm() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                    if (tripObjects.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tripObjects
                                .sortedBy { it.priorityOrder }
                                .forEach { tripObject ->
                                    TripObjectRow(
                                        tripObject = tripObject,
                                        relatedObjects = tripObjects
                                            .filter { it.id in tripObject.relatedObjectIds }
                                            .sortedBy { it.priorityOrder },
                                        onEdit = { editObject(tripObject) }
                                    )
                                }
                        }
                    }
                }
            }
            Text(
                text = "Trip map",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            TripMapView(tripObjects = tripObjects)
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = {
                    val mapIntent = GoogleMapsIntents.openMapIntent(tripObjects)
                    if (mapIntent == null) {
                        saveStatus = "Add coordinates before opening Google Maps"
                    } else {
                        try {
                            context.startActivity(mapIntent)
                        } catch (_: ActivityNotFoundException) {
                            saveStatus = "Google Maps app is not available"
                        }
                    }
                },
                enabled = tripObjects.isNotEmpty(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Open map in Google Maps")
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                onClick = {
                    coroutineScope.launch {
                        val result = tripRepository.createTrip(
                            destination = destination,
                            startDate = startDate,
                            endDate = endDate,
                            objects = tripObjects
                        )
                        saveStatus = buildString {
                            append("Trip saved locally #${result.tripId}")
                            result.privateMapPresetId?.let { presetId ->
                                append(". Private map preset #$presetId")
                            }
                        }
                    }
                },
                enabled = destination.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Create trip")
            }
            saveStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripAttributeInput(
    attribute: TripObjectAttribute,
    value: String,
    onValueChange: (String) -> Unit
) {
    if (attribute.options.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = attribute.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attribute.options.forEach { option ->
                    FilterChip(
                        selected = value == option,
                        onClick = { onValueChange(option) },
                        label = { Text(option) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
        return
    }

    val isLongText = attribute.inputKind == TripObjectAttributeInputKind.LONG_TEXT ||
        attribute.inputKind == TripObjectAttributeInputKind.MAP ||
        attribute.inputKind == TripObjectAttributeInputKind.IMAGE

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(attribute.displayName) },
        singleLine = !isLongText,
        minLines = if (isLongText) 3 else 1,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalizationFor(attribute.inputKind),
            keyboardType = keyboardTypeFor(attribute.inputKind)
        )
    )
}

private fun keyboardTypeFor(inputKind: TripObjectAttributeInputKind): KeyboardType {
    return when (inputKind) {
        TripObjectAttributeInputKind.URL,
        TripObjectAttributeInputKind.IMAGE,
        TripObjectAttributeInputKind.MAP -> KeyboardType.Uri
        TripObjectAttributeInputKind.PHONE -> KeyboardType.Phone
        TripObjectAttributeInputKind.EMAIL -> KeyboardType.Email
        TripObjectAttributeInputKind.NUMBER -> KeyboardType.Number
        TripObjectAttributeInputKind.MONEY -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
}

private fun capitalizationFor(
    inputKind: TripObjectAttributeInputKind
): KeyboardCapitalization {
    return when (inputKind) {
        TripObjectAttributeInputKind.EMAIL,
        TripObjectAttributeInputKind.URL,
        TripObjectAttributeInputKind.IMAGE,
        TripObjectAttributeInputKind.MAP -> KeyboardCapitalization.None
        else -> KeyboardCapitalization.Sentences
    }
}

private fun upsertTripObject(
    tripObjects: SnapshotStateList<TripObjectDraft>,
    savedObject: TripObjectDraft
) {
    val existingIndex = tripObjects.indexOfFirst { it.id == savedObject.id }
    if (existingIndex >= 0) {
        tripObjects[existingIndex] = savedObject
    } else {
        tripObjects.add(savedObject)
    }

    tripObjects.indices.forEach { index ->
        val tripObject = tripObjects[index]
        if (tripObject.id == savedObject.id) return@forEach

        val nextRelatedObjectIds = if (tripObject.id in savedObject.relatedObjectIds) {
            tripObject.relatedObjectIds + savedObject.id
        } else {
            tripObject.relatedObjectIds - savedObject.id
        }

        if (nextRelatedObjectIds != tripObject.relatedObjectIds) {
            tripObjects[index] = tripObject.copy(
                relatedObjectIds = nextRelatedObjectIds
            )
        }
    }
}

private fun TripObjectType.supportsGooglePlaceDetails(): Boolean {
    return this in setOf(
        TripObjectType.HOTEL,
        TripObjectType.FOOD_PLACE,
        TripObjectType.SHOP,
        TripObjectType.PAID_ATTRACTION,
        TripObjectType.FREE_ATTRACTION
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkinSelector(
    selectedSkin: AppSkin,
    onSkinSelected: (AppSkin) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppSkin.entries.forEach { skin ->
            FilterChip(
                selected = selectedSkin == skin,
                onClick = { onSkinSelected(skin) },
                label = { Text(skin.label) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun TripObjectRow(
    tripObject: TripObjectDraft,
    relatedObjects: List<TripObjectDraft>,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${tripObject.priorityOrder}. ${tripObject.type.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = tripObject.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
            }
            if (tripObject.attributes.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tripObject.attributes.forEach { (attribute, value) ->
                        Text(
                            text = "${attribute.displayName}: $value",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (relatedObjects.isNotEmpty()) {
                Text(
                    text = "Related: ${relatedObjects.joinToString { "${it.priorityOrder}. ${it.name}" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun TripPlannerTheme(
    appSkin: AppSkin = AppSkin.System,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val useDarkSkin = when (appSkin) {
        AppSkin.System -> systemIsDark
        AppSkin.Light -> false
        AppSkin.Dark -> true
    }
    val colorScheme = if (useDarkSkin) {
        androidx.compose.material3.darkColorScheme(
            primary = Color(0xFF8BD8C5),
            onPrimary = Color(0xFF00382F),
            secondary = Color(0xFFE0C4B4),
            background = Color(0xFF101916),
            surface = Color(0xFF18231F),
            surfaceVariant = Color(0xFF25332E),
            onSurface = Color(0xFFE7EFEB),
            onSurfaceVariant = Color(0xFFB8C8C1)
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF176B5B),
            onPrimary = Color.White,
            secondary = Color(0xFF6C584C),
            background = Color(0xFFF7FAF8),
            surface = Color.White,
            surfaceVariant = Color(0xFFE7ECE9),
            onSurface = Color(0xFF1F2A25),
            onSurfaceVariant = Color(0xFF5D6862)
        )
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            view.systemUiVisibility = if (useDarkSkin) {
                0
            } else {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}

@Preview(showBackground = true)
@Composable
private fun MainMenuPreview() {
    TripPlannerTheme {
        MainMenuScreen(
            appSkin = AppSkin.System,
            onAppSkinChange = {},
            onPlanNewTrip = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlanNewTripPreview() {
    TripPlannerTheme {
        PlanNewTripScreen(
            appSkin = AppSkin.System,
            onAppSkinChange = {},
            onBack = {}
        )
    }
}
