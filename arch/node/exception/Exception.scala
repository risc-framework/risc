package arch.node.exception

import arch.configs._
import arch.node.csr.CsrTrapUpdate
import arch.node.interrupt.TrapCandidate
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class ExceptionIO(implicit p: Parameters) extends Bundle {
  val interrupt      = Input(new TrapCandidate)
  val commitRedirect = Input(new RedirectBundle)
  val csrBusy        = Input(Bool())
  val archPc         = Input(UInt(p(XLen).W))

  val redirect      = Output(new RedirectBundle)
  val csrTrapUpdate = Output(new CsrTrapUpdate)
}

class Exception(implicit p: Parameters) extends Node(new ExceptionIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      ExceptionDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = ExceptionMeta.Type
  override def desiredName: String = s"exception_${cfg.selector.canonicalName}"

  private val isaImpl  = ExceptionIsaFactory.select(cfg)
  private val selected = isaImpl.select(io.interrupt, io.commitRedirect, io.csrBusy, io.archPc)

  io.redirect      := selected._1
  io.csrTrapUpdate := selected._2
}
