package com.verdo.gesturecontrol

import kotlin.math.abs

/** Android port of the working Macaly Gesture Control Demo classifier. */
object GestureClassifier {
    enum class Pose { NONE, POINT, VICTORY, FIST, OPEN }
    enum class Swipe { NONE, LEFT, RIGHT, UP, DOWN }

    data class Result(
        val pose: Pose,
        val swipe: Swipe,
        val confidence: Float,
        val x: Float,
        val y: Float,
        val tapX: Float,
        val tapY: Float
    )

    private const val TIP_INDEX = 8
    private const val TIP_MIDDLE = 12
    private const val TIP_RING = 16
    private const val TIP_PINKY = 20
    private const val PIP_INDEX = 6
    private const val PIP_MIDDLE = 10
    private const val PIP_RING = 14
    private const val PIP_PINKY = 18

    private fun isExtended(lm: List<LandmarkPoint>, tip: Int, pip: Int): Boolean =
        lm[tip].y < lm[pip].y - 0.02f

    /**
     * Same classification rule as Macaly:
     * index+middle up = VICTORY; index only = POINT; none = FIST;
     * 3+ fingers = OPEN. Ambiguous poses are not invented.
     */
    fun classify(lm: List<LandmarkPoint>, previousX: Float?, previousY: Float?, dtMs: Long): Result {
        if (lm.size < 21) return empty()

        val index = isExtended(lm, TIP_INDEX, PIP_INDEX)
        val middle = isExtended(lm, TIP_MIDDLE, PIP_MIDDLE)
        val ring = isExtended(lm, TIP_RING, PIP_RING)
        val pinky = isExtended(lm, TIP_PINKY, PIP_PINKY)
        val extendedCount = listOf(index, middle, ring, pinky).count { it }

        val pose = when {
            index && middle && !ring && !pinky -> Pose.VICTORY
            index && !middle && !ring && !pinky -> Pose.POINT
            !index && !middle && !ring && !pinky -> Pose.FIST
            extendedCount >= 3 -> Pose.OPEN
            else -> Pose.NONE
        }

        // Macaly uses wrist + MCP landmarks as the palm-center proxy.
        val cx = (lm[0].x + lm[5].x + lm[9].x + lm[13].x + lm[17].x) / 5f
        val cy = (lm[0].y + lm[5].y + lm[9].y + lm[13].y + lm[17].y) / 5f
        val tapX = lm[TIP_INDEX].x.coerceIn(0f, 1f)
        val tapY = lm[TIP_INDEX].y.coerceIn(0f, 1f)

        var swipe = Swipe.NONE
        if (pose == Pose.OPEN && previousX != null && previousY != null && dtMs in 20..300) {
            val dx = cx - previousX
            val dy = cy - previousY
            val dt = dtMs / 1000f
            val vx = dx / dt
            val vy = dy / dt
            // Macaly's 220ms wrist-history velocity threshold is represented here
            // by requiring the same normalized displacement/speed per result interval.
            if (abs(vy) > 1.0f && abs(vy) > abs(vx)) {
                swipe = if (vy < 0f) Swipe.UP else Swipe.DOWN
            } else if (abs(vx) > 1.0f && abs(vx) > abs(vy)) {
                // Preserve the Macaly action direction convention.
                swipe = if (vx > 0f) Swipe.LEFT else Swipe.RIGHT
            }
        }

        return Result(pose, swipe, confidence(pose, index, middle, ring, pinky), cx, cy, tapX, tapY)
    }

    private fun confidence(pose: Pose, index: Boolean, middle: Boolean, ring: Boolean, pinky: Boolean): Float {
        // Confidence is diagnostic only; pose meaning remains exactly the Macaly rule.
        return when (pose) {
            Pose.VICTORY, Pose.POINT, Pose.FIST, Pose.OPEN -> 0.90f
            Pose.NONE -> 0f
        }
    }

    private fun empty() = Result(Pose.NONE, Swipe.NONE, 0f, 0f, 0f, 0f, 0f)
}

data class LandmarkPoint(val x: Float, val y: Float, val z: Float = 0f)
