package com.arruler.app

import java.util.Locale

enum class UnitSystem(val label: String) {
    METRIC("cm"),
    IMPERIAL("in");

    fun next() = if (this == METRIC) IMPERIAL else METRIC

    /** Formats a length given in meters. */
    fun format(meters: Float): String = when (this) {
        METRIC -> {
            val cm = meters * 100f
            if (cm < 100f) String.format(Locale.US, "%.1f cm", cm)
            else String.format(Locale.US, "%.2f m", meters)
        }
        IMPERIAL -> {
            val inches = meters / 0.0254f
            if (inches < 12f) String.format(Locale.US, "%.1f in", inches)
            else {
                var feet = (inches / 12f).toInt()
                var rem = inches - feet * 12f
                if (rem >= 11.95f) { feet += 1; rem = 0f }
                String.format(Locale.US, "%d ft %.1f in", feet, rem)
            }
        }
    }
}
