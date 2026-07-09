package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.ir.IrWorld

/**
 * Collects the exports or imports from an [IrWorld], and gives them an enclosing type name.
 */
data class ExternalApis(
  val instanceName: String,
  val type: ClassName,
  val items: List<IrWorld.Api>,
) {
  val bridgeType: ClassName = ClassName(
    packageName = type.packageName,
    simpleNames = type.simpleNames.map { "Bridge${it}" },
  )
}

val IrWorld.guestApis: ExternalApis?
  get() = ExternalApis(
    instanceName = "guest",
    type = serviceName.kotlinApi.nestedClass("Guest"),
    items = exports,
  ).takeIf { it.items.isNotEmpty() }

val IrWorld.hostApis: ExternalApis?
  get() = ExternalApis(
    instanceName = "host",
    type = serviceName.kotlinApi.nestedClass("Host"),
    items = imports,
  ).takeIf { it.items.isNotEmpty() }

