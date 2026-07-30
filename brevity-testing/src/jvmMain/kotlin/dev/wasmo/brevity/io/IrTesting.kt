package dev.wasmo.brevity.io

import dev.wasmo.brevity.io.validation.buildSymbolTable
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.withIssueCollector

fun IrMapper(toplevelWitPackages: List<IoToplevelWitPackage>): IrMapper = withIssueCollector {
  val symbolTable = toplevelWitPackages.buildSymbolTable()
  throwIfNotEmpty()
  IrMapper(toplevelWitPackages, symbolTable!!)
}
