package brevity.wasi.p2

import wit.wasi.cli.v0_2_0.Environment
import wit.wasi.cli.v0_2_0.Exit
import wit.wasi.cli.v0_2_0.Imports
import wit.wasi.cli.v0_2_0.Stderr
import wit.wasi.cli.v0_2_0.Stdin
import wit.wasi.cli.v0_2_0.Stdout
import wit.wasi.cli.v0_2_0.TerminalStderr
import wit.wasi.cli.v0_2_0.TerminalStdin
import wit.wasi.cli.v0_2_0.TerminalStdout
import wit.wasi.clocks.v0_2_0.MonotonicClock
import wit.wasi.clocks.v0_2_0.WallClock
import wit.wasi.filesystem.v0_2_0.Preopens
import wit.wasi.filesystem.v0_2_0.Types
import wit.wasi.io.v0_2_0.Poll
import wit.wasi.random.v0_2_0.Insecure
import wit.wasi.random.v0_2_0.InsecureSeed
import wit.wasi.random.v0_2_0.Random
import wit.wasi.sockets.v0_2_0.InstanceNetwork
import wit.wasi.sockets.v0_2_0.IpNameLookup
import wit.wasi.sockets.v0_2_0.TcpCreateSocket
import wit.wasi.sockets.v0_2_0.UdpCreateSocket

/**
 * Implement WASI Preview 2.
 */
class RealWasiP2Host : Imports.Host {
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
  override val types: Types
    get() = TODO("Not yet implemented")
  override val preopens: Preopens
    get() = TODO("Not yet implemented")
  override val instanceNetwork: InstanceNetwork
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
