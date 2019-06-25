/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.elements

import com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.asJava.classes.KtUltraLightSimpleAnnotation
import org.jetbrains.kotlin.asJava.classes.KtUltraLightSupport
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.EnumValue

private fun extractAnnotationFqName(sourceAnnotation: PsiAnnotation, attributeName: String): String? {
    val valueTargetLiteral =
        (sourceAnnotation.attributes.firstOrNull { it.attributeName == attributeName } as? KtLightPsiNameValuePair)
            ?.value as? KtLightPsiLiteral

    val valueTarget = valueTargetLiteral?.value as? Pair<*, *>

    val classId = valueTarget?.first as? ClassId
    val name = valueTarget?.second as? Name

    return if (classId === null || name === null) null else
        "${classId.asSingleFqName().asString()}.${name.identifier}"
}


private fun extractAnnotationFqNames(sourceAnnotation: PsiAnnotation, attributeName: String): List<String>? =
    sourceAnnotation.attributes
        .firstOrNull { it.attributeName == attributeName }
        ?.attributeValue
        ?.let { (it.sourceElement as? KtLightPsiArrayInitializerMemberValue) }
        ?.let { arrayInitializer ->
            arrayInitializer.initializers.filterIsInstance<KtLightPsiLiteral>()
                .map { it.value }
                .filterIsInstance<Pair<ClassId, Name>>()
                .map { "${it.first.asSingleFqName().asString()}.${it.second.identifier}" }
        }

private val javaAnnotationElementTypeId = ClassId.fromString("java.lang.annotation.ElementType")
private val targetMapping = hashMapOf(
    "kotlin.annotation.AnnotationTarget.CLASS" to EnumValue(javaAnnotationElementTypeId, Name.identifier("TYPE")),
    "kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS" to EnumValue(javaAnnotationElementTypeId, Name.identifier("ANNOTATION_TYPE")),
    "kotlin.annotation.AnnotationTarget.FIELD" to EnumValue(javaAnnotationElementTypeId, Name.identifier("FIELD")),
    "kotlin.annotation.AnnotationTarget.LOCAL_VARIABLE" to EnumValue(javaAnnotationElementTypeId, Name.identifier("LOCAL_VARIABLE")),
    "kotlin.annotation.AnnotationTarget.VALUE_PARAMETER" to EnumValue(javaAnnotationElementTypeId, Name.identifier("PARAMETER")),
    "kotlin.annotation.AnnotationTarget.CONSTRUCTOR" to EnumValue(javaAnnotationElementTypeId, Name.identifier("CONSTRUCTOR")),
    "kotlin.annotation.AnnotationTarget.FUNCTION" to EnumValue(javaAnnotationElementTypeId, Name.identifier("METHOD")),
    "kotlin.annotation.AnnotationTarget.PROPERTY_GETTER" to EnumValue(javaAnnotationElementTypeId, Name.identifier("METHOD")),
    "kotlin.annotation.AnnotationTarget.PROPERTY_SETTER" to EnumValue(javaAnnotationElementTypeId, Name.identifier("METHOD"))
)

internal fun PsiAnnotation.tryConvertAsTarget(support: KtUltraLightSupport): KtLightAbstractAnnotation? {

    if ("kotlin.annotation.Target" != qualifiedName) return null

    val attributeValues = extractAnnotationFqNames(this, "allowedTargets")

    val convertedValues = if (attributeValues !== null) {
        attributeValues.mapNotNull { targetMapping[it] }
    } else {
        extractAnnotationFqName(this, "value")
            ?.let { targetMapping[it] }
            ?.let { listOf(it) }
            ?: emptyList()
    }

    val targetAttributeValue = ArrayValue(convertedValues) { module -> module.builtIns.array.defaultType }

    return KtUltraLightSimpleAnnotation(
        "java.lang.annotation.Target",
        listOf("value" to targetAttributeValue),
        support,
        parent
    )
}

private val javaAnnotationRetentionPolicyId = ClassId.fromString("java.lang.annotation.RetentionPolicy")
private val retentionMapping = hashMapOf(
    "kotlin.annotation.AnnotationRetention.SOURCE" to EnumValue(javaAnnotationRetentionPolicyId, Name.identifier("SOURCE")),
    "kotlin.annotation.AnnotationRetention.BINARY" to EnumValue(javaAnnotationRetentionPolicyId, Name.identifier("CLASS")),
    "kotlin.annotation.AnnotationRetention.RUNTIME" to EnumValue(javaAnnotationRetentionPolicyId, Name.identifier("RUNTIME"))
)

internal fun PsiAnnotation.tryConvertAsRetention(support: KtUltraLightSupport): KtLightAbstractAnnotation? {

    if ("kotlin.annotation.Retention" != qualifiedName) return null

    val convertedValue = extractAnnotationFqName(this, "value")
        ?.let { retentionMapping[it] }
        ?: retentionMapping["kotlin.annotation.AnnotationRetention.RUNTIME"]!!

    val targetAttributeValue = ArrayValue(listOf(convertedValue)) { module -> module.builtIns.array.defaultType }

    return KtUltraLightSimpleAnnotation(
        "java.lang.annotation.Retention",
        listOf("value" to targetAttributeValue),
        support,
        parent
    )
}

internal fun PsiAnnotation.tryConvertAsMustBeDocumented(support: KtUltraLightSupport): KtLightAbstractAnnotation? {

    if ("kotlin.annotation.MustBeDocumented" != qualifiedName) return null

    return KtUltraLightSimpleAnnotation(
        "java.lang.annotation.Documented",
        emptyList(),
        support,
        parent
    )
}