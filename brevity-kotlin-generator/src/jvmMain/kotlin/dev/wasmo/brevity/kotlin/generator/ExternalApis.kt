package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.ir.IrWorld

/**
 * Collects the exports or imports from an [IrWorld], and gives them an enclosing type name.
 */
data class ExternalApis(
  val instanceName: String,
  val type: ClassName,
  val items: List<IrWorld.Api>,
)

val IrWorld.apiType: TypeName
  get() = Symbols.Brevity.World.parameterizedBy(
    hostApis.type,
    guestApis.type,
  )

val IrWorld.guestApis: ExternalApis
  get() = ExternalApis(
    instanceName = "guest",
    type = serviceName.kotlinApi.nestedClass("Guest"),
    items = exports,
  )

val IrWorld.hostApis: ExternalApis
  get() = ExternalApis(
    instanceName = "host",
    type = serviceName.kotlinApi.nestedClass("Host"),
    items = imports,
  )
