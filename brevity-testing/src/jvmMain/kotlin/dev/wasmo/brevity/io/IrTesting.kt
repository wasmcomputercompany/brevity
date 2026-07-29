package dev.wasmo.brevity.io

import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.io.validation.validate
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.withIssueCollector

fun IrMapper(toplevelWitPackages: List<IoToplevelWitPackage>): IrMapper = withIssueCollector {
  val validatedPackages = toplevelWitPackages.validate()
  contextOf<IssueCollector>().throwIfNotEmpty()
  IrMapper(validatedPackages!!)
}
