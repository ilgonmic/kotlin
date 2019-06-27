/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.time

import kotlin.random.Random
import kotlin.test.*
import kotlin.time.*

class MeasureTimeTest {

    private fun longRunningCalc(): String = buildString {
        repeat(10) {
            while (Random.nextDouble() >= 0.001);
            append(('a'..'z').random())
        }
    }

    @Test
    fun measureTimeOfCalc() {
        val someResult: String

        val elapsed = measureTime {
            someResult = longRunningCalc()
        }

        println("elapsed: $elapsed")

        assertEquals(10, someResult.length)
        assertTrue(elapsed > Duration.ZERO)
    }

    @Test
    fun measureTimeAndResult() {
        val someResult: String

        val measured: DurationMeasured<String> = withMeasureTime { longRunningCalc().also { someResult = it } }
        println("measured: $measured")

        val (result, elapsed) = measured

        assertEquals(someResult, result)
        assertTrue(elapsed > Duration.ZERO)
    }
}