package com.example.signlearn

import kotlin.math.acos
import kotlin.math.sqrt

/** A 3D point (MediaPipe world landmark, in meters). */
data class P3(val x: Float, val y: Float, val z: Float)

/**
 * Rule-based recognizer for static ASL handshapes.
 *
 * MediaPipe gives us 21 points on the hand. From them we measure:
 *  - how straight each finger is (extended vs curled),
 *  - where the thumb is (sticking out, at the side, or across the palm),
 *  - how far apart the index and middle fingers are.
 * Then each letter is a simple rule, e.g. L = index extended + thumb out + others curled.
 *
 * KNOWN LIMITS (explain these as "semantic errors" in your video):
 *  - Letters that MOVE (J, Z) and most word signs can't be judged from a single frame.
 *  - Letters that differ only by the thumb's tiny position (A/S/E/M/N/T) look almost the same
 *    to the tracker, so we only check A among them.
 *  - Bad lighting or the hand at the edge of the frame makes the points jumpy.
 */
object SignClassifier {

    /** Signs the camera can check. */
    val SUPPORTED = setOf("A", "B", "D", "F", "I", "L", "U", "V", "W", "Y", "ILY")

    // ---- tuning knobs (change these if detection feels too strict / too loose) ----
    private const val EXTENDED = 0.80f      // finger straightness above this = extended
    private const val THUMB_OUT_SIDE = 1.55f  // thumb tip well outside the index finger (L, Y, 🤟)
    private const val THUMB_OUT_FAR = 0.50f   // ...and away from the index knuckle (× palm size)
    private const val THUMB_SIDE = 1.10f      // thumb resting on the side of the fist (A, not S)
    private const val TOUCH = 0.45f         // thumb tip touching index tip (for F)
    private const val U_MAX_DEG = 13f       // index/middle angle for U (together)
    private const val V_MIN_DEG = 17f       // index/middle angle for V (apart)

    data class Result(val label: String?, val debug: String)

    // MediaPipe landmark indexes
    private const val WRIST = 0
    private const val THUMB_TIP = 4
    private const val INDEX_MCP = 5; private const val INDEX_TIP = 8
    private const val MIDDLE_MCP = 9; private const val MIDDLE_TIP = 12
    private const val PINKY_MCP = 17

    private fun dist(a: P3, b: P3): Float {
        val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Straightness of a finger: 1.0 = perfectly straight, ~0.4 = fully curled. */
    private fun straightness(p: List<P3>, mcp: Int): Float {
        val chain = dist(p[mcp], p[mcp + 1]) + dist(p[mcp + 1], p[mcp + 2]) + dist(p[mcp + 2], p[mcp + 3])
        if (chain <= 0f) return 0f
        return dist(p[mcp], p[mcp + 3]) / chain
    }

    /** Angle in degrees between the index and middle fingers. */
    private fun spreadDeg(p: List<P3>): Float {
        val ax = p[INDEX_TIP].x - p[INDEX_MCP].x; val ay = p[INDEX_TIP].y - p[INDEX_MCP].y; val az = p[INDEX_TIP].z - p[INDEX_MCP].z
        val bx = p[MIDDLE_TIP].x - p[MIDDLE_MCP].x; val by = p[MIDDLE_TIP].y - p[MIDDLE_MCP].y; val bz = p[MIDDLE_TIP].z - p[MIDDLE_MCP].z
        val la = sqrt(ax * ax + ay * ay + az * az); val lb = sqrt(bx * bx + by * by + bz * bz)
        if (la == 0f || lb == 0f) return 0f
        val cos = ((ax * bx + ay * by + az * bz) / (la * lb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    fun classify(p: List<P3>): Result {
        if (p.size < 21) return Result(null, "no hand")
        val palm = dist(p[WRIST], p[MIDDLE_MCP]).takeIf { it > 0f } ?: return Result(null, "no hand")

        val idx = straightness(p, 5) > EXTENDED
        val mid = straightness(p, 9) > EXTENDED
        val rng = straightness(p, 13) > EXTENDED
        val pnk = straightness(p, 17) > EXTENDED

        val thumbFar = dist(p[THUMB_TIP], p[INDEX_MCP]) / palm
        // Thumb tip distance from the pinky knuckle, compared to the palm's width:
        // < 1 = thumb folded across the palm, ~1.2 = resting on the side, > 1.5 = sticking out
        val thumbSide = dist(p[THUMB_TIP], p[PINKY_MCP]) / dist(p[INDEX_MCP], p[PINKY_MCP]).coerceAtLeast(1e-4f)
        val thumbOut = thumbSide > THUMB_OUT_SIDE && thumbFar > THUMB_OUT_FAR
        val touch = dist(p[THUMB_TIP], p[INDEX_TIP]) / palm
        val spread = spreadDeg(p)

        val label: String? = when {
            // F: OK-circle + three fingers up
            touch < TOUCH && mid && rng && pnk -> "F"
            // all four fingers up
            idx && mid && rng && pnk -> if (!thumbOut) "B" else null
            // three fingers up
            idx && mid && rng && !pnk -> "W"
            // index + middle
            idx && mid && !rng && !pnk -> when {
                spread <= U_MAX_DEG -> "U"
                spread >= V_MIN_DEG -> "V"
                else -> null
            }
            // index + pinky (+ thumb) = I love you
            idx && !mid && !rng && pnk -> if (thumbOut) "ILY" else null
            // index only
            idx && !mid && !rng && !pnk -> if (thumbOut) "L" else "D"
            // pinky only
            !idx && !mid && !rng && pnk -> if (thumbOut) "Y" else "I"
            // fist
            !idx && !mid && !rng && !pnk -> if (thumbSide > THUMB_SIDE && !thumbOut) "A" else null
            else -> null
        }

        val dbg = "fingers ${b(idx)}${b(mid)}${b(rng)}${b(pnk)}  thumb: side=${f(thumbSide)} far=${f(thumbFar)} out=${b(thumbOut)}  " +
            "touch=${f(touch)}  spread=${spread.toInt()}°"
        return Result(label, dbg)
    }

    private fun b(v: Boolean) = if (v) "1" else "0"
    private fun f(v: Float) = String.format("%.2f", v)
}
