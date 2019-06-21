/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.openapi.project.ex.ProjectManagerEx
import org.junit.AfterClass
import org.junit.BeforeClass

class KotlinProjectsPerformanceTest : AbstractKotlinProjectsPerformanceTest() {

    companion object {

        @JvmStatic
        var warmedUp: Boolean = false

        @JvmStatic
        val hwStats: Stats = Stats("helloWorld project")

        @BeforeClass
        @JvmStatic
        fun setup() {
            // things to execute once and keep around for the class
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            hwStats.close()
        }
    }

    override fun setUp() {
        super.setUp()
        // warm up: open simple small project
        if (!warmedUp) {
            val project = innerPerfOpenProject("helloKotlin", hwStats, "warm-up")
            val perfHighlightFile = perfHighlightFile(project, "src/HelloMain.kt", hwStats, "warm-up")
            assertTrue("kotlin project has been not imported properly", perfHighlightFile.isNotEmpty())
            ProjectManagerEx.getInstanceEx().forceCloseProject(project, true)

            warmedUp = true
        }
    }

    override fun tearDown() {
        commitAllDocuments()
        super.tearDown()
    }

    fun testHelloWorldProject() {
        tcSuite("Hello world project") {
            perfOpenProject("helloKotlin", hwStats)

            // highlight
            perfHighlightFile("src/HelloMain.kt", hwStats)
            perfHighlightFile("src/HelloMain2.kt", hwStats)

            // change document
            perfChangeDocument("src/HelloMain2.kt", hwStats, "type a single char") { doc ->
                val text = doc.text
                val offset = text.indexOf("println")

                doc.insertString(offset, "p\n")
            }

            perfChangeDocument("src/HelloMain.kt", hwStats, "type val expression") { doc ->
                val text = doc.text
                val offset = text.indexOf("println")

                doc.insertString(offset, "val s =\n")
            }
        }
    }

    fun testAutoCompletion() {
        tcSuite("Auto Completion") {

            val stats = Stats("autocompletion")
            stats.use {
                perfAutoCompletion(
                    "inside of method: println",
                    stats = it,
                    before = """
                fun bar() {
                    print<caret>
                }
                """,
                    suggestions = arrayOf("println", "print"),
                    type = "l\r",
                    after = """
                fun bar() {
                    println()
                }
                """
                )

                perfAutoCompletion(
                    "outside of method: arrayListOf",
                    stats = it,
                    before = """
                val f: List<String> = array<caret>

                fun bar(){ }
                """,
                    type = "\n",
                    suggestions = arrayOf("arrayOf", "arrayOfNulls", "emptyArray"),
                    after = """
                val f: List<String> = arrayListOf()

                fun bar(){ }
                """
                )
            }

        }
    }

    fun testKotlinProject() {
        tcSuite("Kotlin project") {
            val stats = Stats("kotlin project")
            stats.use {
                perfOpenProject("perfTestProject", stats = it, path = "..")

                perfHighlightFile("compiler/psi/src/org/jetbrains/kotlin/psi/KtFile.kt", stats = it)

                perfHighlightFile("compiler/psi/src/org/jetbrains/kotlin/psi/KtElement.kt", stats = it)
            }
        }
    }

}