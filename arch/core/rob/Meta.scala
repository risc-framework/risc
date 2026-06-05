package arch.core.rob

import vutils.graph.{ NodePort, NodeType }

object RobMeta {
  val Type   = NodeType("rob")
  val ENQ    = NodePort[RobIO, RobEnqPortIO]("enq", _.enq)
  val WB     = NodePort[RobIO, RobWbPortIO]("wb", _.wb)
  val BRU    = NodePort[RobIO, RobBruPortIO]("bru", _.bru)
  val TRAP   = NodePort[RobIO, RobTrapPortIO]("trap", _.trap)
  val COMMIT = NodePort[RobIO, RobCommitPortIO]("commit", _.commit)
  val BYPASS = NodePort[RobIO, RobBypassIO]("bypass", _.bypass)
  val CTRL   = NodePort[RobIO, RobCtrlIO]("ctrl", _.ctrl)
}
