package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.Annotation

val KtResource.handleName: ClassName
  get() = ClassName(type.packageName, "${type.simpleName}Handle")

val KtTypeName.Declared.handleName: ClassName
  get() = ClassName(apiType.packageName, "${apiType.simpleName}Handle")

/** Returns true if we've done the work to implement this. */
val KtFunction.isSupported: Boolean
  get() = name.annotation == null ||
    name.annotation == Annotation.Method ||
    name.annotation == Annotation.ResourceDrop
