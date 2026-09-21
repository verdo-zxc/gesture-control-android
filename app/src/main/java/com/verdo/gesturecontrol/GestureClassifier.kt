package com.verdo.gesturecontrol

import kotlin.math.abs
import kotlin.math.hypot

/** Fast, deterministic classifier. It uses landmark geometry instead of a second ML model. */
object GestureClassifier {
    enum class Pose { NONE, POINT, VICTORY, FIST, OPEN }
    enum class Swipe { NONE, LEFT, RIGHT, UP, DOWN }

    data class Result(val pose: Pose, val swipe: Swipe, val confidence: Float, val x: Float, val y: Float)

    fun classify(lm: List<LandmarkPoint>, previousX: Float?, previousY: Float?, dtMs: Long): Result {
        if (lm.size < 21) return Result(Pose.NONE, Swipe.NONE, 0f, 0f, 0f)
        val scale = maxOf(distance(lm[0], lm[9]), 0.05f)
        fun up(tip: Int, pip: Int): Boolean = lm[tip].y < lm[pip].y - 0.08f
        fun curled(tip: Int, pip: Int): Boolean = lm[tip].y > lm[pip].y - 0.01f

        val index = up(8, 6)
        val middle = up(12, 10)
        val ring = up(16, 14)
        val pinky = up(20, 18)
        val fingers = listOf(index, middle, ring, pinky).count { it }
        val pose = when {
            index && middle && !ring && !pinky -> Pose.VICTORY
            index && !middle && !ring && !pinky -> Pose.POINT
            fingers == 0 && curled(8, 6) && curled(12, 10) && curled(16, 14) && curled(20, 18) -> Pose.FIST
            fingers >= 3 -> Pose.OPEN
            else -> Pose.NONE
        }

        val cx = (lm[0].x + lm[5].x + lm[9].x + lm[13].x + lm[17].x) / 5f
        val cy = (lm[0].y + lm[5].y + lm[9].y + lm[13].y + lm[17].y) / 5f
        var swipe = Swipe.NONE
        if (pose == Pose.OPEN && previousX != null && previousY != null && dtMs in 1..250) {
            val vx = (cx - previousX) / (dtMs / 1000f)
            val vy = (cy - previousY) / (dtMs / 1000f)
            val speed = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            if (speed > 0.75f && abs(vx) > abs(vy) * 1.35f) swipe = if (vx < 0) Swipe.LEFT else Swipe.RIGHT
            else if (speed > 0.75f && abs(vy) > abs(vx) * 1.35f) swipe = if (vy < 0) Swipe.UP else Swipe.DOWN
        }

        val confidence = when (pose) {
            Pose.VICTORY -> if (index && middle && !ring && !pinky) 0.98f else 0.7f
            Pose.POINT -> 0.96f
            Pose.FIST -> 0.95f
            Pose.OPEN -> 0.93f
            Pose.NONE -> 0.2f
        }
        return Result(pose, swipe, confidence, cx, cy)
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
}

data class LandmarkPoint(val x: Float, val y: Float, val z: Float = 0f)
