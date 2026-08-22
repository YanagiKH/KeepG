package com.yanagikh.keepg.smart

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionClassifierTest {
    @Test fun expressionThresholdsAreDeterministic() {
        assertTrue(ExpressionClassifier.matches("smiling", 0.7f, 0.9f, 0.8f, 0.8f))
        assertTrue(ExpressionClassifier.matches("eyes_closed", 0.3f, 0.1f, 0.1f, 0.2f))
        assertFalse(ExpressionClassifier.matches("smiling", 0.7f, 0.4f, 0.8f, 0.8f))
    }
}
