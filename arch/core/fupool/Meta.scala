package arch.core.fupool

import vutils.graph.{ NodePort, NodeType }

object FuPoolMeta {
  val Type      = NodeType("fu_pool")
  val EXCEPTION = NodePort[FuPoolIO, FuPoolExceptionIO]("exception", _.exception)
  val FU        = NodePort[FuPoolIO, FuPoolFuIO]("fu", _.fu)
  val LD_MEM    = NodePort[FuPoolIO, VecLdMemIO]("ld_mem", _.ld_mem)
  val LD_SB     = NodePort[FuPoolIO, VecLdSbFwdIO]("ld_sb", _.ld_sb)
  val ST_SB     = NodePort[FuPoolIO, VecStSbWriteIO]("st_sb", _.st_sb)
  val BRU       = NodePort[FuPoolIO, VecBruResolveIO]("bru", _.bru)
  val CSR       = NodePort[FuPoolIO, VecCsrCtrlIO]("csr", _.csr)
}
