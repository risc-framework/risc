package arch.core.rob

import vutils.graph.{ NodePort, NodeType }

object RobMeta {
  val Type      = NodeType("rob")
  val DISPATCH  = NodePort[RobIO, arch.core.dispatch.DispatchRobIO]("dispatch", _.dispatch)
  val FLUSH     = NodePort[RobIO, RobFlushIO]("flush", _.flush)
  val EXCEPTION = NodePort[RobIO, RobExceptionIO]("exception", _.exception)
  val WB        = NodePort[RobIO, RobWbPortIO]("wb", _.wb)
  val BRU       = NodePort[RobIO, RobBruPortIO]("bru", _.bru)
  val TRAP      = NodePort[RobIO, RobTrapPortIO]("trap", _.trap)
  val COMMIT    = NodePort[RobIO, RobCommitPortIO]("commit", _.commit)
  val CTRL      = NodePort[RobIO, RobCtrlIO]("ctrl", _.ctrl)
}
