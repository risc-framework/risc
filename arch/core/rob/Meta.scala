package arch.core.rob

import arch.core.fupool.FuPoolRobIO
import vutils.graph.{ NodePort, NodeType }

object RobMeta {
  val Type      = NodeType("rob")
  val DISPATCH  = NodePort[RobIO, RobDispatchIO]("dispatch", _.dispatch)
  val FU_POOL   = NodePort[RobIO, FuPoolRobIO]("fu_pool", _.fu_pool)
  val BPU       = NodePort[RobIO, RobBpuIO]("bpu", _.bpu)
  val FLUSH     = NodePort[RobIO, RobFlushIO]("flush", _.flush)
  val EXCEPTION = NodePort[RobIO, RobExceptionIO]("exception", _.exception)
  val COMMIT    = NodePort[RobIO, RobCommitPortIO]("commit", _.commit)
  val CTRL      = NodePort[RobIO, RobCtrlIO]("ctrl", _.ctrl)
}
