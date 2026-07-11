package com.tripplanner.app.data.google

import android.content.Context
import com.tripplanner.app.BuildConfig
import java.time.YearMonth
import java.time.ZoneId

enum class GoogleApiSku(
    val key: String,
    val displayName: String
) {
    DYNAMIC_MAPS("dynamic_maps", "Dynamic Maps"),
    PLACES_AUTOCOMPLETE("places_autocomplete", "Places Autocomplete"),
    PLACES_DETAILS("places_details", "Places Details")
}

data class GoogleApiUsageDecision(
    val allowed: Boolean,
    val message: String,
    val used: Int,
    val limit: Int
)

class GoogleApiUsageLimiter(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun tryConsume(
        sku: GoogleApiSku,
        amount: Int = 1
    ): GoogleApiUsageDecision {
        require(amount > 0) { "Usage amount must be positive" }

        val limit = sku.monthlyLimit()
        val used = preferences.getInt(usageKey(sku), 0)
        if (used + amount > limit) {
            return GoogleApiUsageDecision(
                allowed = false,
                message = "${sku.displayName} is paused. Monthly free limit reached ($used/$limit).",
                used = used,
                limit = limit
            )
        }

        val newUsed = used + amount
        preferences.edit()
            .putInt(usageKey(sku), newUsed)
            .apply()

        return GoogleApiUsageDecision(
            allowed = true,
            message = "${sku.displayName}: $newUsed/$limit monthly free calls used.",
            used = newUsed,
            limit = limit
        )
    }

    @Synchronized
    fun status(sku: GoogleApiSku): GoogleApiUsageDecision {
        val limit = sku.monthlyLimit()
        val used = preferences.getInt(usageKey(sku), 0)
        return GoogleApiUsageDecision(
            allowed = used < limit,
            message = "${sku.displayName}: $used/$limit monthly free calls used.",
            used = used,
            limit = limit
        )
    }

    private fun usageKey(sku: GoogleApiSku): String {
        val month = YearMonth.now(ZoneId.systemDefault())
        return "${month}_${sku.key}"
    }

    private fun GoogleApiSku.monthlyLimit(): Int {
        return when (this) {
            GoogleApiSku.DYNAMIC_MAPS -> BuildConfig.GOOGLE_DYNAMIC_MAPS_MONTHLY_LIMIT
            GoogleApiSku.PLACES_AUTOCOMPLETE -> BuildConfig.GOOGLE_PLACES_AUTOCOMPLETE_MONTHLY_LIMIT
            GoogleApiSku.PLACES_DETAILS -> BuildConfig.GOOGLE_PLACES_DETAILS_MONTHLY_LIMIT
        }.coerceAtLeast(0)
    }

    private companion object {
        const val PREFERENCES_NAME = "trip_planner_google_api_usage"
    }
}
