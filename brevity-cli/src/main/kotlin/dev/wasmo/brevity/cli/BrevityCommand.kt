package dev.wasmo.brevity.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

class BrevityCommand : CliktCommand(
  name = "brevity",
) {
  override fun run() = Unit
}

fun main(args: Array<String>) = BrevityCommand()
  .subcommands(GenerateKotlinCommand())
  .main(args)
