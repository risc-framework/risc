package arch.core.sb

import arch.core.dispatch.DispatchStoreBufferIO
import arch.core.rob.RobSbIO
import vutils.graph.{ NodePort, NodeType }

object StoreBufferMeta {
  val Type           = NodeType("store_buffer")
  val EXCEPTION      = NodePort[StoreBufferIO, StoreBufferExceptionIO]("exception", _.exception)
  val DISPATCH       = NodePort[StoreBufferIO, DispatchStoreBufferIO]("dispatch", _.dispatch)
  val ROB            = NodePort[StoreBufferIO, RobSbIO]("rob", _.rob)
  val FU_POOL        = NodePort[StoreBufferIO, StoreBufferFuPoolIO]("fu_pool", _.fu_pool)
  val MEMORY_ARBITER =
    NodePort[StoreBufferIO, StoreBufferMemoryArbiterIO]("memory_arbiter", _.memory_arbiter)
}
