package dev.wasmo.brevity

import kotlin.RequiresOptIn.Level.ERROR
import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY

@RequiresOptIn(level = ERROR, message = "Internal APIs for Brevity's generated code")
@Retention(BINARY)
@Target(CLASS, FUNCTION, PROPERTY)
annotation class BrevityInternalApi
