package arch.node.pma.impls.mode.default

import arch.node.pma._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }

object PmaDefaultMode extends RegisteredNodeUtils[PmaModeImpl] {
  override def utils: PmaModeImpl = new PmaModeImpl {
    override def value: String = "default"
  }

  override def registry: NodeRegistry[PmaModeImpl] = PmaModeFactory
}
