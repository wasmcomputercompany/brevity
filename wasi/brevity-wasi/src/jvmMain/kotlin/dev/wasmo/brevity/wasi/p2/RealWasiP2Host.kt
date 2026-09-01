package dev.wasmo.brevity.wasi.p2

import wit.wasi.cli.v0_2_12.Environment
import wit.wasi.cli.v0_2_12.Exit
import wit.wasi.cli.v0_2_12.Imports
import wit.wasi.cli.v0_2_12.Stderr
import wit.wasi.cli.v0_2_12.Stdin
import wit.wasi.cli.v0_2_12.Stdout
import wit.wasi.cli.v0_2_12.TerminalStderr
import wit.wasi.cli.v0_2_12.TerminalStdin
import wit.wasi.cli.v0_2_12.TerminalStdout
import wit.wasi.clocks.v0_2_12.MonotonicClock
import wit.wasi.clocks.v0_2_12.Timezone
import wit.wasi.clocks.v0_2_12.WallClock
import wit.wasi.filesystem.v0_2_12.Preopens
import wit.wasi.filesystem.v0_2_12.Types
import wit.wasi.io.v0_2_12.Poll
import wit.wasi.random.v0_2_12.Insecure
import wit.wasi.random.v0_2_12.InsecureSeed
import wit.wasi.random.v0_2_12.Random
import wit.wasi.sockets.v0_2_12.InstanceNetwork
import wit.wasi.sockets.v0_2_12.IpNameLookup
import wit.wasi.sockets.v0_2_12.Network
import wit.wasi.sockets.v0_2_12.TcpCreateSocket
import wit.wasi.sockets.v0_2_12.UdpCreateSocket

/**
 * Implement WASI Preview 2.
 *
 * Note: when Rust panics, it checks [environment] for `RUST_BACKTRACE=1`.
 */
class RealWasiP2Host : Imports.Host {
  override val environment: Environment = RealEnvironment()
  override val exit: Exit
    get() = TODO("Not yet implemented")
  override val stdin: Stdin
    get() = TODO("Not yet implemented")
  override val stdout: Stdout = object : Stdout {
    override fun getStdout() = RealOutputStream(System.out)
  }
  override val stderr: Stderr = object : Stderr {
    override fun getStderr() = RealOutputStream(System.err)
  }
  override val terminalStdin: TerminalStdin
    get() = TODO("Not yet implemented")
  override val terminalStdout: TerminalStdout
    get() = TODO("Not yet implemented")
  override val terminalStderr: TerminalStderr
    get() = TODO("Not yet implemented")
  override val monotonicClock: MonotonicClock
    get() = TODO("Not yet implemented")
  override val wallClock: WallClock
    get() = TODO("Not yet implemented")
  override val timezone: Timezone
    get() = TODO("Not yet implemented")
  override val types: Types
    get() = TODO("Not yet implemented")
  override val preopens: Preopens
    get() = TODO("Not yet implemented")
  override val instanceNetwork: InstanceNetwork
    get() = TODO("Not yet implemented")
  override val network: Network
    get() = TODO("Not yet implemented")
  override val udpCreateSocket: UdpCreateSocket
    get() = TODO("Not yet implemented")
  override val tcpCreateSocket: TcpCreateSocket
    get() = TODO("Not yet implemented")
  override val ipNameLookup: IpNameLookup
    get() = TODO("Not yet implemented")
  override val random: Random
    get() = TODO("Not yet implemented")
  override val insecure: Insecure
    get() = TODO("Not yet implemented")
  override val insecureSeed: InsecureSeed
    get() = TODO("Not yet implemented")
  override val poll: Poll
    get() = TODO("Not yet implemented")
}
