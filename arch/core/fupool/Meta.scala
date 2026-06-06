package arch.core.fupool

import vutils.graph.{ NodePort, NodeType }

object FuPoolMeta {
  val Type           = NodeType("fu_pool")
  val EXCEPTION      = NodePort[FuPoolIO, FuPoolExceptionIO]("exception", _.exception)
  val SCHEDULER      = NodePort[FuPoolIO, FuPoolSchedulerIO]("scheduler", _.scheduler)
  val ROB            = NodePort[FuPoolIO, FuPoolRobIO]("rob", _.rob)
  val MEMORY_ARBITER = NodePort[FuPoolIO, FuPoolMemoryArbiterIO]("memory_arbiter", _.memory_arbiter)
  val SB             = NodePort[FuPoolIO, FuPoolSbIO]("sb", _.sb)
  val CSR            = NodePort[FuPoolIO, VecCsrCtrlIO]("csr", _.csr)
}
