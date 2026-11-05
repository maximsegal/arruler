package com.arruler.app

/** Screen-space point in pixels. */
data class ScreenPt(val x: Float, val y: Float)

/**
 * Immutable per-frame state produced on the GL thread and drawn by [OverlayView].
 * Lengths are in meters. Points that are behind the camera are null.
 */
data class Snapshot(
    val tracking: Boolean = false,
    val reticle: ScreenPt? = null,
    val reticleHit: Boolean = false,
    val points: List<ScreenPt?> = emptyList(),
    /** segments[i] is the distance between points[i] and points[i+1]. */
    val segments: List<Float> = emptyList(),
    /** Distance from the last placed point to the current reticle hit, if any. */
    val liveMeters: Float? = null,
    val totalMeters: Float = 0f,
)
