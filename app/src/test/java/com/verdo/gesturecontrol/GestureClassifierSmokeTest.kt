package com.verdo.gesturecontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureClassifierSmokeTest {
    @Test
    fun rejectsIncompleteLandmarks() {
        val result = GestureClassifier.classify(emptyList(), null, null, 16)
        assertEquals(GestureClassifier.Pose.NONE, result.pose)
        assertEquals(GestureClassifier.Swipe.NONE, result.swipe)
        assertEquals(0f, result.confidence, 0f)
    }
}
