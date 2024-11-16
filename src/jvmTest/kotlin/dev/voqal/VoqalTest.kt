package dev.voqal

import io.vertx.junit5.VertxTestContext
import java.util.concurrent.TimeUnit

interface VoqalTest {
    fun errorOnTimeout(testContext: VertxTestContext, waitTime: Long = 60) {
        if (testContext.awaitCompletion(waitTime, TimeUnit.SECONDS)) {
            if (testContext.failed()) {
                throw testContext.causeOfFailure()
            }
        } else {
            throw RuntimeException("Test timed out")
        }
    }

    fun successOnTimeout(testContext: VertxTestContext, waitTime: Long = 30) {
        if (testContext.awaitCompletion(waitTime, TimeUnit.SECONDS)) {
            if (testContext.failed()) {
                throw testContext.causeOfFailure()
            }
        } else {
            testContext.completeNow()
        }
    }
}