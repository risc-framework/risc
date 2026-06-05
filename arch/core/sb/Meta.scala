package arch.core.sb

import arch.core.dispatch.DispatchStoreBufferIO
import vutils.graph.{ NodePort, NodeType }

object StoreBufferMeta {
  val Type      = NodeType("store_buffer")
  val EXCEPTION = NodePort[StoreBufferIO, StoreBufferExceptionIO]("exception", _.exception)
  val DISPATCH  = NodePort[StoreBufferIO, DispatchStoreBufferIO]("dispatch", _.dispatch)
  val ROB       = NodePort[StoreBufferIO, StoreBufferRobIO]("rob", _.rob)
  val WRITE     = NodePort[StoreBufferIO, StoreBufferWriteIO]("write", _.write)
  val FWD       = NodePort[StoreBufferIO, StoreBufferForwardIO]("fwd", _.fwd)
  val STATE     = NodePort[StoreBufferIO, StoreBufferStateIO]("state", _.state)
  val MEM       = NodePort[StoreBufferIO, StoreBufferMemIO]("mem", _.mem)
}
