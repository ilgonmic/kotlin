/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.subtargets

import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.plugin.whenEvaluated
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmResolverPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.tasks.createOrRegisterTask
import org.jetbrains.kotlin.gradle.testing.internal.configureConventions
import org.jetbrains.kotlin.gradle.testing.internal.kotlinTestRegistry
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

abstract class KotlinJsSubTarget(
    val target: KotlinJsTarget,
    private val disambiguationClassifier: String
) : KotlinJsSubTargetDsl {
    val project get() = target.project
    private val nodeJs = NodeJsRootPlugin.apply(project.rootProject)

    val runTaskName = disambiguateCamelCased("run")
    val testTaskName = disambiguateCamelCased("test")

    fun configure() {
        NpmResolverPlugin.apply(project)

        configureTests()
        configureRun()
        configureDistribution()

        target.compilations.all {
            val npmProject = it.npmProject
            it.compileKotlinTask.kotlinOptions.outputFile = npmProject.dir.resolve(npmProject.main).canonicalPath
        }
    }

    protected fun disambiguateCamelCased(name: String): String =
        lowerCamelCaseName(target.disambiguationClassifier, disambiguationClassifier, name)

    private fun configureTests() {
        configureAction(
            action = ::configureTests,
            compilationPredicate = TEST_COMPILATION_PREDICATE
        )
    }

    abstract val testTaskDescription: String

    private fun configureTests(compilation: KotlinJsCompilation) {
        val testJs = project.createOrRegisterTask<KotlinJsTest>(testTaskName) { testJs ->
            val compileTask = compilation.compileKotlinTask

            testJs.group = LifecycleBasePlugin.VERIFICATION_GROUP
            testJs.description = testTaskDescription

            testJs.dependsOn(nodeJs.npmInstallTask, compileTask, nodeJs.nodeJsSetupTask)

            testJs.onlyIf {
                compileTask.outputFile.exists()
            }

            testJs.compilation = compilation
            testJs.targetName = listOfNotNull(target.disambiguationClassifier, disambiguationClassifier)
                .takeIf { it.isNotEmpty() }
                ?.joinToString()

            testJs.configureConventions()
        }

        target.project.kotlinTestRegistry.registerTestTask(testJs, target.testTask.doGetTask())

        project.whenEvaluated {
            testJs.configure {
                if (it.testFramework == null) {
                    configureDefaultTestFramework(it)
                }
            }
        }
    }

    protected abstract fun configureDefaultTestFramework(it: KotlinJsTest)

    private fun configureRun() {
        configureAction(
            action = ::configureRun,
            compilationPredicate = MAIN_COMPILATION_PREDICATE
        )
    }

    protected abstract fun configureRun(compilation: KotlinJsCompilation)

    private fun configureDistribution() {
        configureAction(
            action = ::configureDistribution,
            compilationPredicate = MAIN_COMPILATION_PREDICATE
        )
    }

    protected abstract fun configureDistribution(compilation: KotlinJsCompilation)

    private fun configureAction(
        action: (compilation: KotlinJsCompilation) -> Unit,
        compilationPredicate: (compilation: KotlinJsCompilation) -> Boolean
    ) {
        target.compilations.all { compilation ->
            if (compilationPredicate(compilation)) {
                action(compilation)
            }
        }
    }

    override fun testTask(body: KotlinJsTest.() -> Unit) {
        (project.tasks.getByName(testTaskName) as KotlinJsTest).body()
    }

    companion object {
        private val MAIN_COMPILATION_PREDICATE: (KotlinJsCompilation) -> Boolean = { it.name == KotlinCompilation.MAIN_COMPILATION_NAME }
        private val TEST_COMPILATION_PREDICATE: (KotlinJsCompilation) -> Boolean = { it.name == KotlinCompilation.TEST_COMPILATION_NAME }
    }
}