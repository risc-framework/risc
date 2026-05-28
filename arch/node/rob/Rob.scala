package arch.node.rob

import arch.configs._
import vutils.graph.NodeInterface
import chisel3._

class RobWbIO(implicit p: Parameters) extends NodeInterface {
  val valid   = Input(Bool())
  val rob_tag = Input(UInt(p(RobTagWidth).W))
  val data    = Input(UInt(p(XLen).W))
}

class RobIO(implicit p: Parameters) extends Bundle {
  val fu_pool = Vec(p(NumFUs), new RobWbIO)
}
