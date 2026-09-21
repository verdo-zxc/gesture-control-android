package com.verdo.gesturecontrol

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Lightweight geometric classifier designed for stable real-time hand poses. */
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

    fun classify(
        lm: List<LandmarkPoint>,
        previousX: Float?,
        previousY: Float?,
        dtMs: Long
    ): Result {
        if (lm.size < 21) return Result(Pose.NONE, Swipe.NONE, 0f, 0f, 0f, 0f, 0f)

        val wrist = lm[0]
        fun wristDistance(i: Int) = distance(lm[i], wrist)

        // Finger extension is based on radial distance from the wrist rather than raw Y.
        // This is much more stable when the hand is rotated or held at an angle.
        fun extended(tip: Int, pip: Int): Boolean {
            val tipD = wristDistance(tip)
            val pipD = wristDistance(pip)
            return tipD > pipD * 1.20f
        }

        fun folded(tip: Int, pip: Int): Boolean {
            val tipD = wristDistance(tip)
            val pipD = wristDistance(pip)
            return tipD < pipD * 1.10f
        }

        val index = extended(8, 6)
        val middle = extended(12, 10)
        val ring = extended(16, 14)
        val pinky = extended(20, 18)

        val foldedCount = listOf(index, middle, ring, pinky).count { !it }
        val pose = when {
            index && middle && !ring && !pinky -> Pose.VICTORY
            index && !middle && !ring && !pinky -> Pose.POINT
            !index && !middle && !ring && !pinky &&
                folded(8, 6) && folded(12, 10) && folded(16, 14) && folded(20, 18) -> Pose.FIST
            index && middle && ring && pinky -> Pose.OPEN
            foldedCount >= 3 -> Pose.NONE
            else -> Pose.NONE
        }

        val cx = (lm[0].x + lm[5].x + lm[9].x + lm[13].x + lm[17].x) / 5f
        val cy = (lm[0].y + lm[5].y + lm[9].y + lm[13].y + lm[17].y) / 5f
        val swipeX = cx
        val swipeY = cy
        val tapX = min(1f, maxOf(0f, lm[8].x))
        val tapY = min(1f, maxOf(0f, lm[8].y))

        var swipe = Swipe.NONE
        if (pose == Pose.OPEN && previousX != null && previousY != null && dtMs in 1..250) {
            val vx = (swipeX - previousX) / (dtMs / 1000f)
            val vy = (swipeY - previousY) / (dtMs / 1000f)
            val speed = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            if (speed > 0.90f && abs(vx) > abs(vy) * 1.50f) {
                swipe = if (vx < 0f) Swipe.LEFT else Swipe.RIGHT
            } else if (speed > 0.90f && abs(vy) > abs(vx) * 1.50f) {
                swipe = if (vy < 0f) Swipe.UP else Swipe.DOWN
            }
        }

        // This is a classifier confidence heuristic, not ML probability.
        val confidence = when (pose) {
            Pose.VICTORY -> if (index && middle && !ring && !pinky) 0.96f else 0.60f
            Pose.POINT -> if (index && !middle && !ring && !pinky) 0.95f else 0.60f
            Pose.FIST -> if (!index && !middle && !ring && !pinky) 0.94f else 0.60f
            Pose.OPEN -> if (index && middle && ring && pinky) 0.93f else 0.60f
            Pose.NONE -> 0.10f
        }

        return Result(pose, swipe, confidence, cx, cy, tapX, tapY)
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
}

data class LandmarkPoint(val x: Float, val y: Float, val z: Float = 0f)
