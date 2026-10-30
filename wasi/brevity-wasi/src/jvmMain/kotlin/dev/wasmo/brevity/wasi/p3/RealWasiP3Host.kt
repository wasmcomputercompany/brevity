package dev.wasmo.brevity.wasi.p3

import wit.wasi.cli.v0_3_1.Environment
import wit.wasi.cli.v0_3_1.Exit
import wit.wasi.cli.v0_3_1.Imports
import wit.wasi.cli.v0_3_1.Stderr
import wit.wasi.cli.v0_3_1.Stdin
import wit.wasi.cli.v0_3_1.Stdout
import wit.wasi.cli.v0_3_1.TerminalInput
import wit.wasi.cli.v0_3_1.TerminalOutput
import wit.wasi.cli.v0_3_1.TerminalStderr
import wit.wasi.cli.v0_3_1.TerminalStdin
import wit.wasi.cli.v0_3_1.TerminalStdout
import wit.wasi.clocks.v0_3_1.MonotonicClock
import wit.wasi.clocks.v0_3_1.SystemClock
import wit.wasi.clocks.v0_3_1.Timezone
import wit.wasi.filesystem.v0_3_1.Preopens
import wit.wasi.filesystem.v0_3_1.Types as FilesystemTypes
import wit.wasi.random.v0_3_1.Insecure
import wit.wasi.random.v0_3_1.InsecureSeed
import wit.wasi.random.v0_3_1.Random
import wit.wasi.sockets.v0_3_1.IpNameLookup
import wit.wasi.sockets.v0_3_1.Types as SocketsTypes

/**
 * Implement WASI Preview 3.
 */
class RealWasiP3Host : Imports.Host {
  override val environment: Environment
    get() = TODO("Not yet implemented")
  override val exit: Exit
    get() = TODO("Not yet implemented")
  override val stdin: Stdin
    get() = TODO("Not yet implemented")
  override val stdout: Stdout
    get() = TODO("Not yet implemented")
  override val stderr: Stderr
    get() = TODO("Not yet implemented")
  override val terminalInput: TerminalInput
    get() = TODO("Not yet implemented")
  override val terminalOutput: TerminalOutput
    get() = TODO("Not yet implemented")
  override val terminalStdin: TerminalStdin
    get() = TODO("Not yet implemented")
  override val terminalStdout: TerminalStdout
    get() = TODO("Not yet implemented")
  override val terminalStderr: TerminalStderr
    get() = TODO("Not yet implemented")
  override val monotonicClock: MonotonicClock = RealMonotonicClock()
  override val systemClock: SystemClock
    get() = TODO("Not yet implemented")
  override val timezone: Timezone
    get() = TODO("Not yet implemented")
  override val filesystemTypes: FilesystemTypes
    get() = TODO("Not yet implemented")
  override val preopens: Preopens
    get() = TODO("Not yet implemented")
  override val socketsTypes: SocketsTypes
    get() = TODO("Not yet implemented")
  override val ipNameLookup: IpNameLookup
    get() = TODO("Not yet implemented")
  override val random: Random
    get() = TODO("Not yet implemented")
  override val insecure: Insecure
    get() = TODO("Not yet implemented")
  override val insecureSeed: InsecureSeed
    get() = TODO("Not yet implemented")
}

