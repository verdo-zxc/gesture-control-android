package com.verdo.gesturecontrol

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max

/**
 * Conservative geometry classifier.
 * Uses both PIP/DIP joint angles and finger extension relative to the palm.
 * Ambiguous poses are deliberately returned as NONE instead of being guessed.
 */
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

    private data class FingerState(val score: Float, val extended: Boolean, val folded: Boolean)

    fun classify(lm: List<LandmarkPoint>, previousX: Float?, previousY: Float?, dtMs: Long): Result {
        if (lm.size < 21) return empty()

        val wrist = lm[0]
        val palm = max(distance(lm[0], lm[9]), 0.001f)

        fun finger(mcp: Int, pip: Int, dip: Int, tip: Int): FingerState {
            val pipAngle = jointAngle(lm[mcp], lm[pip], lm[dip])
            val dipAngle = jointAngle(lm[pip], lm[dip], lm[tip])
            val pipStraight = ((pipAngle - 145f) / 35f).coerceIn(0f, 1f)
            val dipStraight = ((dipAngle - 145f) / 35f).coerceIn(0f, 1f)
            val reach = (distance(lm[mcp], lm[tip]) / max(distance(lm[mcp], lm[pip]), 0.001f))
            val reachScore = ((reach - 1.32f) / 0.55f).coerceIn(0f, 1f)
            val wristReach = (distance(wrist, lm[tip]) / palm)
            val wristScore = ((wristReach - 1.35f) / 1.05f).coerceIn(0f, 1f)

            val score = (0.35f * pipStraight + 0.25f * dipStraight + 0.25f * reachScore + 0.15f * wristScore).coerceIn(0f, 1f)
            return FingerState(score, score >= 0.68f, score <= 0.42f)
        }

        val index = finger(5, 6, 7, 8)
        val middle = finger(9, 10, 11, 12)
        val ring = finger(13, 14, 15, 16)
        val pinky = finger(17, 18, 19, 20)
        val fingers = listOf(index, middle, ring, pinky)

        // If any finger sits in the uncertain band, avoid an immediate gesture guess.
        val ambiguous = fingers.any { !it.extended && !it.folded }

        val candidate = when {
            !ambiguous && index.extended && middle.extended && ring.folded && pinky.folded ->
                Pose.VICTORY to confidence(index, middle, ring, pinky)
            !ambiguous && index.extended && middle.folded && ring.folded && pinky.folded ->
                Pose.POINT to confidence(index, middle, ring, pinky)
            !ambiguous && index.folded && middle.folded && ring.folded && pinky.folded ->
                Pose.FIST to confidence(index, middle, ring, pinky)
            !ambiguous && index.extended && middle.extended && ring.extended && pinky.extended ->
                Pose.OPEN to confidence(index, middle, ring, pinky)
            else -> Pose.NONE to 0f
        }

        val cx = (lm[0].x + lm[5].x + lm[9].x + lm[13].x + lm[17].x) / 5f
        val cy = (lm[0].y + lm[5].y + lm[9].y + lm[13].y + lm[17].y) / 5f
        val tapX = lm[8].x.coerceIn(0f, 1f)
        val tapY = lm[8].y.coerceIn(0f, 1f)

        var swipe = Swipe.NONE
        if (candidate.first == Pose.OPEN && previousX != null && previousY != null && dtMs in 20..300) {
            val dx = cx - previousX
            val dy = cy - previousY
            val distanceMoved = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val speed = distanceMoved / (dtMs / 1000f)
            if (speed > 0.65f && abs(dx) > 0.14f && abs(dx) > abs(dy) * 1.8f) {
                swipe = if (dx < 0f) Swipe.LEFT else Swipe.RIGHT
            } else if (speed > 0.65f && abs(dy) > 0.14f && abs(dy) > abs(dx) * 1.8f) {
                swipe = if (dy < 0f) Swipe.UP else Swipe.DOWN
            }
        }

        return Result(candidate.first, swipe, candidate.second, cx, cy, tapX, tapY)
    }

    private fun confidence(vararg fingers: FingerState): Float {
        val scores = fingers.map { if (it.extended) it.score else 1f - it.score }
        val mean = scores.average().toFloat()
        val worst = scores.minOrNull() ?: 0f
        val spread = (mean - worst).coerceAtLeast(0f)
        return (0.58f + 0.30f * mean - 0.08f * spread).coerceIn(0f, 0.99f)
    }

    private fun jointAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Float {
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val denom = max(hypot(abx.toDouble(), aby.toDouble()) * hypot(cbx.toDouble(), cby.toDouble()), 1e-6)
        val cos = ((abx * cbx + aby * cby) / denom).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos)).toFloat()
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun empty() = Result(Pose.NONE, Swipe.NONE, 0f, 0f, 0f, 0f, 0f)
}

data class LandmarkPoint(val x: Float, val y: Float, val z: Float = 0f)
