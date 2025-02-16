/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.subtargets

import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.TaskHolder
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBrowserDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode.DEVELOPMENT
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJsDce
import org.jetbrains.kotlin.gradle.tasks.createOrRegisterTask
import java.io.File

class KotlinBrowserJs(target: KotlinJsTarget) :
    KotlinJsSubTarget(target, "browser"),
    KotlinJsBrowserDsl {

    override val testTaskDescription: String
        get() = "Run all ${target.name} tests inside browser using karma and webpack"

    private val webpackTaskName = disambiguateCamelCased("webpack")

    private val runDceTaskName = disambiguateCamelCased("runDce")
    private val webpackDceTaskName = disambiguateCamelCased("webpackDce")

    override fun configureDefaultTestFramework(it: KotlinJsTest) {
        it.useKarma {
            useChromeHeadless()
        }
    }

    override fun runTask(body: KotlinWebpack.() -> Unit) {
        (project.tasks.getByName(runTaskName) as KotlinWebpack).body()
    }

    override fun webpackTask(body: KotlinWebpack.() -> Unit) {
        (project.tasks.getByName(webpackTaskName) as KotlinWebpack).body()
    }

    override fun runDceTask(body: KotlinJsDce.() -> Unit) {
        (project.tasks.getByName(runDceTaskName) as KotlinJsDce).body()
    }

    override fun webpackDceTask(body: KotlinJsDce.() -> Unit) {
        (project.tasks.getByName(webpackDceTaskName) as KotlinJsDce).body()
    }

    override fun configureRun(compilation: KotlinJsCompilation) {
        val project = compilation.target.project
        val nodeJs = NodeJsRootPlugin.apply(project.rootProject)

        val distributionName = "run"

        val compileKotlinTask = compilation.compileKotlinTask
        val kotlinJsDce = applyDce(project, compilation, distributionName)

        val run = project.createOrRegisterTask<KotlinWebpack>(disambiguateCamelCased(distributionName)) {
            it.dependsOn(
                nodeJs.npmInstallTask,
                compileKotlinTask,
                target.project.tasks.getByName(compilation.processResourcesTaskName)
            )
            it.compilation = compilation
            it.mode = DEVELOPMENT
            it.dceEnabled = false
            it.bin = "webpack-dev-server/bin/webpack-dev-server.js"
            it.description = "start webpack dev server"

            it.devServer = KotlinWebpackConfig.DevServer(
                open = true,
                contentBase = listOf(compilation.output.resourcesDir.canonicalPath)
            )

            it.outputs.upToDateWhen { false }
        }

        target.runTask.dependsOn(run.getTaskOrProvider())

        project.afterEvaluate {
            run.activateDce(
                compileKotlinTask = compileKotlinTask,
                dceTask = kotlinJsDce
            )
        }
    }

    override fun configureDistribution(compilation: KotlinJsCompilation) {
        val project = compilation.target.project
        val nodeJs = NodeJsRootPlugin.apply(project.rootProject)

        val distributionName = "webpack"

        val compileKotlinTask = compilation.compileKotlinTask
        val kotlinJsDce = applyDce(project, compilation, distributionName)

        val webpack = project.createOrRegisterTask<KotlinWebpack>(disambiguateCamelCased(distributionName)) {
            it.dependsOn(
                nodeJs.npmInstallTask,
                compileKotlinTask
            )

            it.compilation = compilation
            it.description = "build webpack bundle"

            project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(it)
        }

        project.afterEvaluate {
            webpack.activateDce(
                compileKotlinTask = compileKotlinTask,
                dceTask = kotlinJsDce
            )
        }
    }

    private fun applyDce(
        project: Project,
        kotlinCompilation: KotlinJsCompilation,
        name: String
    ): KotlinJsDce {
        val kotlinTask = kotlinCompilation.compileKotlinTask
        val dceTask = project.createOrRegisterTask<KotlinJsDce>(disambiguateCamelCased(name + "Dce")) {
            it.dependsOn(kotlinTask)
            project.tasks.findByName("build")!!.dependsOn(it)
        }.doGetTask()

        val npmProject = kotlinCompilation.npmProject
        dceTask.destinationDir = npmProject.dir.resolve(KotlinJsDce.DEFAULT_OUT_DIR)

        project.afterEvaluate {

            val configuration = project.configurations.getByName(kotlinCompilation.compileDependencyConfigurationName)

            with(dceTask) {
                classpath = configuration
                source(kotlinTask.outputFile)
            }
        }

        return dceTask
    }

    private fun TaskHolder<KotlinWebpack>.activateDce(
        compileKotlinTask: Kotlin2JsCompile,
        dceTask: KotlinJsDce
    ) {
        configure {
            if (it.dceEnabled) {
                it.entry = dceTask.destinationDir.path
                    .let(::File)
                    .resolve(compileKotlinTask.outputFile.name)

                it.dependsOn.remove(compileKotlinTask)
                it.dependsOn(dceTask)
            }
        }
    }
}