/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch")

// Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJsDce
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.io.File

class KotlinJsDcePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val kotlinExtension =
            project.multiplatformExtensionOrNull
                ?: project.extensions.getByName("kotlin") as? KotlinSingleTargetExtension
                ?: run {
                    project.pluginManager.apply(Kotlin2JsPluginWrapper::class.java)
                    project.kotlinExtension as KotlinSingleTargetExtension
                }

        fun forEachJsTarget(action: (KotlinTarget) -> Unit) {
            when (kotlinExtension) {
                is KotlinSingleTargetExtension -> action(kotlinExtension.target)
                is KotlinMultiplatformExtension ->
                    kotlinExtension.targets
                        .matching { it.platformType == KotlinPlatformType.js }
                        .all { action(it) }
            }
        }

        forEachJsTarget {
            it.compilations.all { processCompilation(project, it) }
        }
    }

    private fun processCompilation(project: Project, kotlinCompilation: KotlinCompilation<*>) {
        val kotlinTaskName = kotlinCompilation.compileKotlinTaskName
        val kotlinTask = project.tasks.findByName(kotlinTaskName) as? Kotlin2JsCompile ?: return
        val dceTaskName = getKotlinJsDceTaskName(kotlinCompilation)
        val dceTask = project.tasks.create(dceTaskName, KotlinJsDce::class.java).also {
            it.dependsOn(kotlinTask)
            project.tasks.findByName("build")!!.dependsOn(it)
        }

        project.afterEvaluate {
            val outputDir = project.buildDir
                .resolve(DEFAULT_OUT_DIR)
                .resolve(kotlinCompilation.target.disambiguationClassifier?.let { "$it/" }.orEmpty() + kotlinCompilation.name)

            val configuration = project.configurations.getByName(kotlinCompilation.compileDependencyConfigurationName)

            with(dceTask) {
                classpath = configuration
                destinationDir = dceTask.dceOptions.outputDirectory?.let { File(it) } ?: outputDir
                source(kotlinTask.outputFile)
            }
        }
    }

    companion object {
        private const val TASK_SUFFIX = "kotlinJs"
        private const val MPP_TASK_SUFFIX = "kotlin"
        private const val DCE_TASK_PREFIX = "runDce"
        private const val DEFAULT_OUT_DIR = "kotlin-js-min"

        fun apply(project: Project, compilation: KotlinCompilation<*>): KotlinJsDce {
            project.plugins.apply(KotlinJsDcePlugin::class.java)
            return project.tasks.getByName(getKotlinJsDceTaskName(compilation)) as KotlinJsDce
        }

        fun getKotlinJsDceTaskName(compilation: KotlinCompilation<*>): String {
            return lowerCamelCaseName(
                DCE_TASK_PREFIX,
                compilation.target.disambiguationClassifier,
                compilation.name.takeIf { it != KotlinCompilation.MAIN_COMPILATION_NAME },
                if (compilation.target is KotlinWithJavaTarget<*>) TASK_SUFFIX else MPP_TASK_SUFFIX
            )
        }
    }
}