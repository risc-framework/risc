package arch.core.flush

import vutils.graph.{ NodePort, NodeType }

object FlushMeta {
  val Type      = NodeType("flush")
  val ROB       = NodePort[FlushIO, FlushRobIO]("rob", _.rob)
  val EXCEPTION = NodePort[FlushIO, FlushExceptionIO]("exception", _.exception)
}
