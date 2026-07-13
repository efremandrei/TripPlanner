import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
fun configuredValue(name: String): String {
    return localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: ""
}

fun configuredIntValue(name: String, defaultValue: Int): Int {
    return configuredValue(name).toIntOrNull() ?: defaultValue
}

fun String.escapedForBuildConfig(): String {
    return replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

val mapsApiKey = configuredValue("MAPS_API_KEY")
val placesApiKey = configuredValue("PLACES_API_KEY")
val googleWebClientId = configuredValue("GOOGLE_WEB_CLIENT_ID")
val allowGoogleRelease = configuredValue("ALLOW_GOOGLE_RELEASE").equals("true", ignoreCase = true)
val googleDynamicMapsMonthlyLimit = configuredIntValue("GOOGLE_DYNAMIC_MAPS_MONTHLY_LIMIT", 10_000)
val googlePlacesAutocompleteMonthlyLimit = configuredIntValue("GOOGLE_PLACES_AUTOCOMPLETE_MONTHLY_LIMIT", 10_000)
val googlePlacesDetailsMonthlyLimit = configuredIntValue("GOOGLE_PLACES_DETAILS_MONTHLY_LIMIT", 10_000)
val releaseMapsApiKey = if (allowGoogleRelease) mapsApiKey else ""
val releasePlacesApiKey = if (allowGoogleRelease) placesApiKey else ""
val releaseGoogleWebClientId = if (allowGoogleRelease) googleWebClientId else ""

if (allowGoogleRelease) {
    val missingReleaseGoogleConfig = listOf(
        "MAPS_API_KEY" to mapsApiKey,
        "PLACES_API_KEY" to placesApiKey,
        "GOOGLE_WEB_CLIENT_ID" to googleWebClientId
    ).filter { (_, value) -> value.isBlank() }

    check(missingReleaseGoogleConfig.isEmpty()) {
        "ALLOW_GOOGLE_RELEASE=true requires: ${
            missingReleaseGoogleConfig.joinToString { it.first }
        }"
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tripplanner.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tripplanner.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"${mapsApiKey.escapedForBuildConfig()}\"")
        buildConfigField("String", "PLACES_API_KEY", "\"${placesApiKey.escapedForBuildConfig()}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.escapedForBuildConfig()}\"")
        buildConfigField("int", "GOOGLE_DYNAMIC_MAPS_MONTHLY_LIMIT", googleDynamicMapsMonthlyLimit.toString())
        buildConfigField("int", "GOOGLE_PLACES_AUTOCOMPLETE_MONTHLY_LIMIT", googlePlacesAutocompleteMonthlyLimit.toString())
        buildConfigField("int", "GOOGLE_PLACES_DETAILS_MONTHLY_LIMIT", googlePlacesDetailsMonthlyLimit.toString())
        buildConfigField("boolean", "PREPOPULATE_MOCK_DB", "false")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        getByName("release") {
            manifestPlaceholders["MAPS_API_KEY"] = releaseMapsApiKey
            buildConfigField("String", "MAPS_API_KEY", "\"${releaseMapsApiKey.escapedForBuildConfig()}\"")
            buildConfigField("String", "PLACES_API_KEY", "\"${releasePlacesApiKey.escapedForBuildConfig()}\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${releaseGoogleWebClientId.escapedForBuildConfig()}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.google.play.services.maps)
    implementation(libs.google.id)
    implementation(libs.google.places)
    ksp(libs.androidx.room.compiler)
}
