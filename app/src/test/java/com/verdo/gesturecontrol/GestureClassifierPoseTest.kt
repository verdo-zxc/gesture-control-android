package com.verdo.gesturecontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureClassifierPoseTest {

    private fun hand(
        indexExtended: Boolean = false,
        middleExtended: Boolean = false,
        ringExtended: Boolean = false,
        pinkyExtended: Boolean = false
    ): List<LandmarkPoint> {
        val points = MutableList(21) { LandmarkPoint(0f, 0f, 0f) }
        points[0] = LandmarkPoint(0f, 0f, 0f)

        fun setFinger(pip: Int, tip: Int, extended: Boolean) {
            points[pip] = LandmarkPoint(0f, 1f, 0f)
            // MediaPipe y grows downward. Macaly's rule is TIP.y < PIP.y - 0.02.
            points[tip] = LandmarkPoint(0f, if (extended) 0.20f else 1.20f, 0f)
        }

        setFinger(6, 8, indexExtended)
        setFinger(10, 12, middleExtended)
        setFinger(14, 16, ringExtended)
        setFinger(18, 20, pinkyExtended)
        return points
    }

    @Test
    fun recognizesVictory() {
        val result = GestureClassifier.classify(hand(indexExtended = true, middleExtended = true), null, null, 16)
        assertEquals(GestureClassifier.Pose.VICTORY, result.pose)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun recognizesPoint() {
        val result = GestureClassifier.classify(hand(indexExtended = true), null, null, 16)
        assertEquals(GestureClassifier.Pose.POINT, result.pose)
    }

    @Test
    fun recognizesFist() {
        val result = GestureClassifier.classify(hand(), null, null, 16)
        assertEquals(GestureClassifier.Pose.FIST, result.pose)
    }

    @Test
    fun recognizesOpenPalm() {
        val result = GestureClassifier.classify(
            hand(
                indexExtended = true,
                middleExtended = true,
                ringExtended = true,
                pinkyExtended = true
            ),
            null, null, 16
        )
        assertEquals(GestureClassifier.Pose.OPEN, result.pose)
    }
}
