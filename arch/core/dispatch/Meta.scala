package arch.core.dispatch

import arch.core.scheduler.SchedulerDispatchIO
import vutils.graph.{ NodePort, NodeType }

object DispatchMeta {
  val Type         = NodeType("dispatch")
  val DECODE       = NodePort[DispatchIO, DispatchDecodeIO]("decode", _.decode)
  val REGFILE      = NodePort[DispatchIO, DispatchRegfileIO]("regfile", _.regfile)
  val ROB          = NodePort[DispatchIO, DispatchRobIO]("rob", _.rob)
  val STORE_BUFFER = NodePort[DispatchIO, DispatchStoreBufferIO]("store_buffer", _.store_buffer)
  val SCHEDULER    = NodePort[DispatchIO, SchedulerDispatchIO]("scheduler", _.scheduler)
  val EXCEPTION    = NodePort[DispatchIO, DispatchExceptionIO]("exception", _.exception)
}
