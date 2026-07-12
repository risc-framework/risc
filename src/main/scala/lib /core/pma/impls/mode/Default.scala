package arch.core.pma.impls.mode.default

import arch.core.pma._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object PmaDefaultMode extends RegisteredNodeUtils[PmaModeImpl] {
  override def utils: PmaModeImpl = new PmaModeImpl {
    override def value: String = "default"
  }

  override def registry: NodeDimensionRegistry[PmaModeImpl] =
    PmaModeFactory
}
