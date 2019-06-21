/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.spec.tasks

import org.jetbrains.kotlin.checkers.AbstractDiagnosticsTestSpec
import org.jetbrains.kotlin.codegen.AbstractBlackBoxCodegenTestSpec
import org.jetbrains.kotlin.generators.tests.generator.testGroup
import org.jetbrains.kotlin.parsing.AbstractParsingTestSpec
import org.jetbrains.kotlin.spec.models.LinkedSpecTest
import org.jetbrains.kotlin.spec.parsers.CommonParser
import org.jetbrains.kotlin.spec.utils.GeneralConfiguration.TESTDATA_PATH
import org.jetbrains.kotlin.spec.utils.GeneralConfiguration.TEST_PATH
import java.io.File
import java.lang.StringBuilder

data class TestReferences(var testRelevants: MutableSet<String>? = null, var testNumber: Int = 0)

typealias TestsBySections = MutableMap<String, TestsByParagraph>
typealias TestsByParagraph = MutableMap<Int, TestsByType>
typealias TestsByType = MutableMap<String, TestsBySentence>
typealias TestsBySentence = MutableMap<Int, TestReferences>

object TestsMapGenerator {
    private const val TESTS_MAP_FILE_HEADER = """/*
 * This file is generated by {@link org.jetbrains.kotlin.spec.tasks.generateTests}. DO NOT MODIFY MANUALLY.
 * This file is used in the HTML version of the Kotlin Specification (https://kotlin.github.io/kotlin-spec) to show tests coverage for sentences.
 *
 * Content format:
 *
 * {paragraphNumber}
 *     {testType: neg|pos}: {sentenceNumber}-{numberOfTests|testPathToAnotherSection}, ...
 */
"""
    private const val LINKED_TESTS_PATH = "$TESTDATA_PATH/diagnostics/linked"
    private const val TESTS_MAP_FILENAME = "testsMap.txt"
    private val lineBreak = System.lineSeparator()

    private fun createObjectsPath(specTest: LinkedSpecTest, testsMap: TestsBySections): TestReferences {
        val sections = specTest.place.sections.joinToString("/")
        val testsBySection = testsMap.getOrPut(sections) { mutableMapOf() }
        val testsByParagraph = testsBySection.getOrPut(specTest.place.paragraphNumber) { mutableMapOf() }
        val testsByType = testsByParagraph.getOrPut(specTest.testType.type) { mutableMapOf() }

        return testsByType.getOrPut(specTest.place.sentenceNumber) { TestReferences() }
    }

    private fun appendRelevantTests(specTest: LinkedSpecTest, testsMap: TestsBySections) {
        if (specTest.relevantPlaces == null) return

        specTest.relevantPlaces.forEach {
            val sections = it.sections.joinToString("/")
            val testsBySection = testsMap.getOrPut(sections) { mutableMapOf() }
            val testsByParagraph = testsBySection.getOrPut(it.paragraphNumber) { mutableMapOf() }
            val testsByType = testsByParagraph.getOrPut(specTest.testType.type) { mutableMapOf() }
            val testReferences = testsByType.getOrPut(it.sentenceNumber) { TestReferences() }

            if (testReferences.testRelevants == null) {
                testReferences.testRelevants = mutableSetOf()
            }
            testReferences.testRelevants!!.add(
                "${specTest.sections.joinToString("/")}/${specTest.place.paragraphNumber}/${specTest.testType.type}/${specTest.place.sentenceNumber}.${specTest.testNumber}.kt"
            )
        }
    }

    private fun buildTestsMapAsText(testsMap: TestsByParagraph): String {
        val testsMapAsText = StringBuilder()

        testsMap.forEach { (paragraphNumber, testsByType) ->
            testsMapAsText.append("$paragraphNumber$lineBreak")

            testsByType.forEach { (testType, testsBySentence) ->
                val sentenceTests = mutableListOf<String>()

                testsMapAsText.append("    $testType: ")
                testsBySentence.forEach { (sentenceNumber, tests) ->
                    if (tests.testNumber != 0) {
                        sentenceTests.add("$sentenceNumber-${tests.testNumber}")
                    }
                    if (tests.testRelevants != null) {
                        tests.testRelevants!!.forEach {
                            sentenceTests.add("$sentenceNumber-$it")
                        }
                    }
                }
                testsMapAsText.append(sentenceTests.joinToString(", ") + lineBreak)
            }
        }

        return testsMapAsText.toString()
    }

    fun buildTestsMapPerSection() {
        val testsMap: TestsBySections = mutableMapOf()

        File(LINKED_TESTS_PATH).walkTopDown().forEach {
            if (!it.isFile || it.extension != "kt") return@forEach

            val (specTest, _) = CommonParser.parseSpecTest(it.canonicalPath, mapOf("main.kt" to it.readText()))

            if (specTest is LinkedSpecTest) {
                val testReferences = createObjectsPath(specTest, testsMap)
                testReferences.testNumber++
                appendRelevantTests(specTest, testsMap)
            }
        }
        testsMap.forEach { (sections, testsByParagraph) ->
            val testsMapFile = File("$LINKED_TESTS_PATH/$sections/$TESTS_MAP_FILENAME")
            val testsMapAsText = buildTestsMapAsText(testsByParagraph)

            testsMapFile.writeText(TESTS_MAP_FILE_HEADER + lineBreak + testsMapAsText)
        }
    }
}

fun generateTests() {
//    TestsMapGenerator.buildTestsMapPerSection()
    testGroup(TEST_PATH, TESTDATA_PATH) {
        testClass<AbstractDiagnosticsTestSpec> {
            model("diagnostics", excludeDirs = listOf("helpers"))
        }
        testClass<AbstractParsingTestSpec> {
            model("psi", testMethod = "doParsingTest", excludeDirs = listOf("helpers", "templates"))
        }
        testClass<AbstractBlackBoxCodegenTestSpec> {
            model("codegen/box", excludeDirs = listOf("helpers", "templates"))
        }
    }
}

fun main() = generateTests()
