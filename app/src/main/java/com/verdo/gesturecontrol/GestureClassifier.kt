package com.verdo.gesturecontrol

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max

/** Rotation-invariant hand-pose classifier using joint angles and relative lengths. */
object GestureClassifier {
    enum class Pose { NONE, POINT, VICTORY, FIST, OPEN }
    enum class Swipe { NONE, LEFT, RIGHT, UP, DOWN }

    data class Result(val pose: Pose, val swipe: Swipe, val confidence: Float, val x: Float, val y: Float, val tapX: Float, val tapY: Float)

    fun classify(lm: List<LandmarkPoint>, previousX: Float?, previousY: Float?, dtMs: Long): Result {
        if (lm.size < 21) return Result(Pose.NONE, Swipe.NONE, 0f, 0f, 0f, 0f, 0f)

        fun extensionScore(mcp: Int, pip: Int, dip: Int, tip: Int): Float {
            val angle = jointAngle(lm[mcp], lm[pip], lm[dip])
            val tipFromMcp = distance(lm[mcp], lm[tip])
            val pipFromMcp = max(distance(lm[mcp], lm[pip]), 0.001f)
            val straight = ((angle - 125f) / 45f).coerceIn(0f, 1f)
            val length = ((tipFromMcp / pipFromMcp - 1.30f) / 0.60f).coerceIn(0f, 1f)
            return 0.65f * straight + 0.35f * length
        }

        val index = extensionScore(5, 6, 7, 8)
        val middle = extensionScore(9, 10, 11, 12)
        val ring = extensionScore(13, 14, 15, 16)
        val pinky = extensionScore(17, 18, 19, 20)
        val extended = { s: Float -> s >= 0.62f }
        val ei = extended(index); val em = extended(middle); val er = extended(ring); val ep = extended(pinky)

        val candidates = mutableListOf<Pair<Pose, Float>>()
        if (ei && em && !er && !ep) candidates += Pose.VICTORY to confidence(index, middle, 1f - ring, 1f - pinky)
        if (ei && !em && !er && !ep) candidates += Pose.POINT to confidence(index, 1f - middle, 1f - ring, 1f - pinky)
        if (!ei && !em && !er && !ep) candidates += Pose.FIST to confidence(1f - index, 1f - middle, 1f - ring, 1f - pinky)
        if (ei && em && er && ep) candidates += Pose.OPEN to confidence(index, middle, ring, pinky)
        val best = candidates.maxByOrNull { it.second } ?: (Pose.NONE to 0.15f)

        val cx = (lm[0].x + lm[5].x + lm[9].x + lm[13].x + lm[17].x) / 5f
        val cy = (lm[0].y + lm[5].y + lm[9].y + lm[13].y + lm[17].y) / 5f
        val tapX = lm[8].x.coerceIn(0f, 1f)
        val tapY = lm[8].y.coerceIn(0f, 1f)

        var swipe = Swipe.NONE
        if (best.first == Pose.OPEN && previousX != null && previousY != null && dtMs in 10..350) {
            val dx = cx - previousX
            val dy = cy - previousY
            val speed = hypot(dx.toDouble(), dy.toDouble()) / (dtMs / 1000.0)
            if (speed > 0.55 && abs(dx) > 0.12f && abs(dx) > abs(dy) * 1.7f) swipe = if (dx < 0) Swipe.LEFT else Swipe.RIGHT
            else if (speed > 0.55 && abs(dy) > 0.12f && abs(dy) > abs(dx) * 1.7f) swipe = if (dy < 0) Swipe.UP else Swipe.DOWN
        }
        return Result(best.first, swipe, best.second, cx, cy, tapX, tapY)
    }

    private fun confidence(vararg values: Float): Float {
        val mean = values.average().toFloat()
        val worst = values.minOrNull() ?: 0f
        return (0.55f + 0.30f * mean + 0.15f * worst).coerceIn(0f, 0.99f)
    }

    private fun jointAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Float {
        val abx = a.x - b.x; val aby = a.y - b.y
        val cbx = c.x - b.x; val cby = c.y - b.y
        val denom = max(hypot(abx.toDouble(), aby.toDouble()) * hypot(cbx.toDouble(), cby.toDouble()), 1e-6)
        val cos = ((abx * cbx + aby * cby) / denom).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos)).toFloat()
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
}

data class LandmarkPoint(val x: Float, val y: Float, val z: Float = 0f)
