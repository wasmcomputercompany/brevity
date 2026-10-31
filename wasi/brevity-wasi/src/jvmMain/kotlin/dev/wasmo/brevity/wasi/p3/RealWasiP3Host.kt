package dev.wasmo.brevity.wasi.p3

import wit.wasi.cli.v0_3_0.Environment
import wit.wasi.cli.v0_3_0.Exit
import wit.wasi.cli.v0_3_0.Imports
import wit.wasi.cli.v0_3_0.Stderr
import wit.wasi.cli.v0_3_0.Stdin
import wit.wasi.cli.v0_3_0.Stdout
import wit.wasi.cli.v0_3_0.TerminalInput
import wit.wasi.cli.v0_3_0.TerminalOutput
import wit.wasi.cli.v0_3_0.TerminalStderr
import wit.wasi.cli.v0_3_0.TerminalStdin
import wit.wasi.cli.v0_3_0.TerminalStdout
import wit.wasi.clocks.v0_3_0.MonotonicClock
import wit.wasi.clocks.v0_3_0.SystemClock
import wit.wasi.clocks.v0_3_0.Timezone
import wit.wasi.filesystem.v0_3_0.Preopens
import wit.wasi.filesystem.v0_3_0.Types as FilesystemTypes
import wit.wasi.random.v0_3_0.Insecure
import wit.wasi.random.v0_3_0.InsecureSeed
import wit.wasi.random.v0_3_0.Random
import wit.wasi.sockets.v0_3_0.IpNameLookup
import wit.wasi.sockets.v0_3_0.Types as SocketsTypes

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

