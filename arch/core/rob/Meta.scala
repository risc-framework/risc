package arch.core.rob

import arch.core.fupool.FuPoolRobIO
import vutils.graph.{ NodePort, NodeType }

object RobMeta {
  val Type      = NodeType("rob")
  val DISPATCH  = NodePort[RobIO, RobDispatchIO]("dispatch", _.dispatch)
  val FU_POOL   = NodePort[RobIO, FuPoolRobIO]("fu_pool", _.fu_pool)
  val REGFILE   = NodePort[RobIO, RobRegfileIO]("regfile", _.regfile)
  val SB        = NodePort[RobIO, RobSbIO]("sb", _.sb)
  val BPU       = NodePort[RobIO, RobBpuIO]("bpu", _.bpu)
  val FLUSH     = NodePort[RobIO, RobFlushIO]("flush", _.flush)
  val EXCEPTION = NodePort[RobIO, RobExceptionIO]("exception", _.exception)
  val DEBUG     = NodePort[RobIO, RobDebugIO]("debug", _.debug)
}
