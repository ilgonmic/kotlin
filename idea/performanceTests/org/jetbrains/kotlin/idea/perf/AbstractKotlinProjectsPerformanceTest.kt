/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.exists
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File
import java.nio.file.Paths

fun commitAllDocuments() {
    ProjectManagerEx.getInstanceEx().openProjects.forEach { project ->
        val psiDocumentManagerBase = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase

        EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
            psiDocumentManagerBase.clearUncommittedDocuments()
            psiDocumentManagerBase.commitAllDocuments()
        })
    }
}

abstract class AbstractKotlinProjectsPerformanceTest : UsefulTestCase() {

    // myProject is not required for all potential perf test cases
    private var myProject: Project? = null
    private lateinit var jdk18: Sdk
    private lateinit var myApplication: IdeaTestApplication

    override fun setUp() {
        super.setUp()

        myApplication = IdeaTestApplication.getInstance()
        ApplicationManager.getApplication().runWriteAction {
            val jdkTableImpl = JavaAwareProjectJdkTableImpl.getInstanceEx()
            val homePath = if (jdkTableImpl.internalJdk.homeDirectory!!.name == "jre") {
                jdkTableImpl.internalJdk.homeDirectory!!.parent.path
            } else {
                jdkTableImpl.internalJdk.homePath!!
            }

            val javaSdk = JavaSdk.getInstance()
            jdk18 = javaSdk.createJdk("1.8", homePath)
            val internal = javaSdk.createJdk("IDEA jdk", homePath)

            val jdkTable = ProjectJdkTable.getInstance()
            jdkTable.addJdk(jdk18, testRootDisposable)
            jdkTable.addJdk(internal, testRootDisposable)
            KotlinSdkType.setUpIfNeeded()
        }
        InspectionProfileImpl.INIT_INSPECTIONS = true
    }

    override fun tearDown() {
        super.tearDown()

        if (myProject != null) {
            val projectManagerEx = ProjectManagerEx.getInstanceEx()

            val closeAndDispose = projectManagerEx.closeAndDispose(myProject!!)
            if (!closeAndDispose) {
                println("x".repeat(40))
                println("x $myProject is ${if (projectManagerEx.isProjectOpened(myProject!!)) "opened" else "closed"}")
                println("x".repeat(40))

            }
            myProject = null
        }
    }

    private fun getTempDirFixture(): TempDirTestFixture =
        LightTempDirTestFixtureImpl(true)

    private fun simpleFilename(fileName: String): String {
        val lastIndexOf = fileName.lastIndexOf('/')
        return if (lastIndexOf >= 0) fileName.substring(lastIndexOf + 1) else fileName
    }

    protected fun perfChangeDocument(fileName: String, stats: Stats, note: String = "", block: (document: Document) -> Unit) =
        perfChangeDocument(myProject!!, stats, fileName, note, block)

    private fun perfChangeDocument(
        project: Project,
        stats: Stats,
        fileName: String,
        nameOfChange: String,
        block: (document: Document) -> Unit
    ) {
        val manager = PsiDocumentManager.getInstance(project)
        stats.perfTest(
            testName = "changing doc: $nameOfChange: ${simpleFilename(fileName)}",
            setUp = {
                val openFileInEditor = openFileInEditor(project, fileName)
                openFileInEditor
            },
            test = { editorFile ->
                val document = editorFile!!.document
                CommandProcessor.getInstance().executeCommand(
                    project, {
                        ApplicationManager.getApplication().runWriteAction {
                            block(document)
                        }
                    }, "change doc $fileName $nameOfChange", ""
                )
                document
            },
            tearDown =
            { document ->
                manager.commitDocument(document!!)
            })

        manager.commitAllDocuments()
    }

    protected fun perfOpenProject(name: String, stats: Stats, path: String = "idea/testData/perfTest") {
        myProject = innerPerfOpenProject(name, stats, path = path, note = "")
    }

    protected fun innerPerfOpenProject(
        name: String,
        stats: Stats,
        note: String,
        path: String = "idea/testData/perfTest"
    ): Project {
        val projectPath = "$path/$name"

        val warmUpIterations = 1
        val iterations = 10
        val projectManagerEx = ProjectManagerEx.getInstanceEx()

        var lastProject: Project? = null
        var counter = 0

        stats.perfTest<Project, Project>(
            warmUpIterations = warmUpIterations,
            iterations = iterations,
            testName = "open project${if (note.isNotEmpty()) " $note" else ""}",
            test = {
                val project = projectManagerEx.loadAndOpenProject(projectPath)!!
                if (!Paths.get(projectPath, ".idea").exists()) {
                    initKotlinProject(project, projectPath, name)
                }

                projectManagerEx.openTestProject(project)

                val changeListManagerImpl = ChangeListManager.getInstance(project) as ChangeListManagerImpl
                changeListManagerImpl.waitUntilRefreshed()

                project
            },
            tearDown = { project ->
                lastProject = project
                val prj = project!!

                // close all project but last - we're going to return and use it further
                if (counter < warmUpIterations + iterations - 1) {
                    //LightPlatformTestCase.doTearDown(prj, myApplication)
                    val closeAndDispose = projectManagerEx.closeAndDispose(prj)
                    println("$prj is closed $closeAndDispose")
                }
                counter++
            }
        )

        return lastProject!!
    }

    private fun initKotlinProject(
        project: Project,
        projectPath: String,
        name: String
    ) {
        val modulePath = "$projectPath/$name${ModuleFileType.DOT_DEFAULT_EXTENSION}"
        val projectFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectPath))!!
        val srcFile = projectFile.findChild("src")!!
        val module = ApplicationManager.getApplication().runWriteAction(Computable<Module> {
            val projectRootManager = ProjectRootManager.getInstance(project)
            with(projectRootManager) {
                projectSdk = jdk18
            }
            val moduleManager = ModuleManager.getInstance(project)
            val module = moduleManager.newModule(modulePath, ModuleTypeId.JAVA_MODULE)
            PsiTestUtil.addSourceRoot(module, srcFile)
            module
        })
        ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, jdk18)
    }

    protected fun perfHighlightFile(name: String, stats: Stats): List<HighlightInfo> =
        perfHighlightFile(myProject!!, name, stats)


    protected fun perfHighlightFile(
        project: Project,
        fileName: String,
        stats: Stats,
        note: String = ""
    ): List<HighlightInfo> {
        var highlightInfos: List<HighlightInfo> = emptyList()
        stats.perfTest(
            testName = "highlighting ${if (note.isNotEmpty()) "$note " else ""}${simpleFilename(fileName)}",
            setUp = {
                val fileInEditor = openFileInEditor(project, fileName)
                fileInEditor.psiFile
            },
            test = { file ->
                highlightFile(file!!)
            },
            tearDown = {
                highlightInfos = it!!
            }
        )
        return highlightInfos
    }

    protected fun perfAutoCompletion(
        name: String,
        stats: Stats,
        before: String,
        suggestions: Array<String>,
        type: String,
        after: String
    ) {
        data class Result(val fixture: CodeInsightTestFixture, val complete: Array<LookupElement>)

        stats.perfTest(
            testName = name,
            setUp = {
                val factory = IdeaTestFixtureFactory.getFixtureFactory()
                val fixtureBuilder = factory.createLightFixtureBuilder(KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE)
                val tempDirFixture = getTempDirFixture()
                val fixture = factory.createCodeInsightFixture(fixtureBuilder.fixture, tempDirFixture)

                with(fixture) {
                    setUp()
                    configureByText(KotlinFileType.INSTANCE, before)
                }
                fixture
            },
            test = {
                Result(it!!, it.complete(CompletionType.BASIC))
            },
            tearDown = { result ->
                val fixture = result!!.fixture
                val complete = result.complete
                val actualSuggestions = complete.map { it.lookupString }.toList()
                assertTrue(actualSuggestions.containsAll(suggestions.toList()))

                try {
                    with(fixture) {
                        type(type)
                        checkResult(after)
                    }
                } finally {
                    commitAllDocuments()
                    fixture.tearDown()
                }
            }
        )
    }

    private fun highlightFile(psiFile: PsiFile): List<HighlightInfo> {
        val document = FileDocumentManager.getInstance().getDocument(psiFile.virtualFile)!!
        val editor = EditorFactory.getInstance().getEditors(document).first()
        return CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, intArrayOf(), false)
    }

    data class EditorFile(val psiFile: PsiFile, val document: Document)

    private fun openFileInEditor(project: Project, name: String): EditorFile {
        val psiFile = projectFileByName(project, name)
        val vFile = psiFile.virtualFile
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(vFile, true)
        val document = FileDocumentManager.getInstance().getDocument(vFile)!!
        assertNotNull(EditorFactory.getInstance().getEditors(document))
        disposeOnTearDown(Disposable { fileEditorManager.closeFile(vFile) })
        return EditorFile(psiFile = psiFile, document = document)
    }

    private fun projectFileByName(project: Project, name: String): PsiFile {
        val fileManager = VirtualFileManager.getInstance()
        val url = "file://${File("${project.basePath}/$name").absolutePath}"
        val virtualFile = fileManager.refreshAndFindFileByUrl(url)
        return virtualFile!!.toPsiFile(project)!!
    }
}