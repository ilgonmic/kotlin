/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.caches.project.LibraryModuleInfo
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.createContainerForLazyResolve
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.js.resolve.JsPlatformCompilerServices
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService
import org.jetbrains.kotlin.serialization.js.KotlinJavascriptSerializationUtil
import org.jetbrains.kotlin.serialization.js.createKotlinJavascriptPackageFragmentProvider
import org.jetbrains.kotlin.utils.KotlinJavascriptMetadataUtils

object JsResolverForModuleFactory : ResolverForModuleFactory() {
    override fun <M : ModuleInfo> createResolverForModule(
        moduleDescriptor: ModuleDescriptorImpl,
        moduleContext: ModuleContext,
        moduleContent: ModuleContent<M>,
        platformParameters: PlatformAnalysisParameters,
        targetEnvironment: TargetEnvironment,
        resolverForProject: ResolverForProject<M>,
        languageVersionSettings: LanguageVersionSettings
    ): ResolverForModule {
        val (moduleInfo, syntheticFiles, moduleContentScope) = moduleContent
        val project = moduleContext.project
        val declarationProviderFactory = DeclarationProviderFactoryService.createDeclarationProviderFactory(
            project,
            moduleContext.storageManager,
            syntheticFiles,
            moduleContentScope,
            moduleInfo
        )

        val container = createContainerForLazyResolve(
            moduleContext,
            declarationProviderFactory,
            BindingTraceContext(),
            DefaultBuiltInPlatforms.jsPlatform,
            JsPlatformCompilerServices,
            targetEnvironment,
            languageVersionSettings
        )
        var packageFragmentProvider = container.get<ResolveSession>().packageFragmentProvider

        if (moduleInfo is LibraryModuleInfo && moduleInfo.platform.isJs()) {
            val providers = moduleInfo.getLibraryRoots()
                .flatMap { KotlinJavascriptMetadataUtils.loadMetadata(it) }
                .filter { it.version.isCompatible() }
                .map { metadata ->
                    val (header, packageFragmentProtos) =
                            KotlinJavascriptSerializationUtil.readModuleAsProto(metadata.body, metadata.version)
                    createKotlinJavascriptPackageFragmentProvider(
                        moduleContext.storageManager, moduleDescriptor, header, packageFragmentProtos, metadata.version,
                        container.get(), LookupTracker.DO_NOTHING
                    )
                }

            if (providers.isNotEmpty()) {
                packageFragmentProvider = CompositePackageFragmentProvider(listOf(packageFragmentProvider) + providers)
            }
        }

        return ResolverForModule(packageFragmentProvider, container)
    }
}
