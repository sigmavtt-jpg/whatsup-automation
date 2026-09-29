package com.whatsup.automation

import com.whatsup.automation.data.engine.NodeRunner
import org.junit.Assert.assertFalse
import org.junit.Test

class NodeRunnerUnitTest {
    @Test
    fun testNodeRunnerNativeLoadedFlagOnHostJvm() {
        // وعلى بيئة الـ JVM العادية (بدون مكتبات native ثنائية)، يجب أن تكون قيمة isNativeLoaded هي false دون أن تتسبب في أي استثناء غير معالج.
        assertFalse(NodeRunner.isNativeLoaded)
    }
}
