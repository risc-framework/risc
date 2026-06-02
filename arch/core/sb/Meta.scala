package arch.core.sb

import vutils.graph.{ NodePort, NodeType }

object StoreBufferMeta {
  val Type   = NodeType("store_buffer")
  val ALLOC  = NodePort[StoreBufferIO, StoreBufferAllocIO]("alloc", _.alloc)
  val COMMIT = NodePort[StoreBufferIO, StoreBufferCommitIO]("commit", _.commit)
  val WRITE  = NodePort[StoreBufferIO, StoreBufferWriteIO]("write", _.write)
  val FWD    = NodePort[StoreBufferIO, StoreBufferForwardIO]("fwd", _.fwd)
  val STATE  = NodePort[StoreBufferIO, StoreBufferStateIO]("state", _.state)
  val MEM    = NodePort[StoreBufferIO, StoreBufferMemIO]("mem", _.mem)
  val CTRL   = NodePort[StoreBufferIO, StoreBufferCtrlIO]("ctrl", _.ctrl)
}
