package arch.core.dispatch

import vutils.graph.{ NodePort, NodeType }

object DispatchMeta {
  val Type      = NodeType("dispatch")
  val DECODE    = NodePort[DispatchIO, DispatchDecodeIO]("decode", _.decode)
  val REGFILE   = NodePort[DispatchIO, DispatchRegfileIO]("regfile", _.regfile)
  val ROB       = NodePort[DispatchIO, DispatchRobIO]("rob", _.rob)
  val SB        = NodePort[DispatchIO, DispatchStoreBufferIO]("sb", _.sb)
  val SCHEDULER = NodePort[DispatchIO, DispatchSchedulerIO]("scheduler", _.scheduler)
  val EXCEPTION = NodePort[DispatchIO, DispatchExceptionIO]("exception", _.exception)
}
