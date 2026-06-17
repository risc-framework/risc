package arch.core.rob.impls.storage.reg

import arch.configs._
import arch.core.rob._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object RegRobStorageImpl extends RegisteredNodeUtils[RobStorageImpl] {
  override def utils: RobStorageImpl = new RobStorageImpl {
    override def value: String = "reg"

    override def make(implicit p: Parameters): RobStorage = new RobStorage {
      private val entries = RegInit(VecInit(Seq.fill(p(RobSize))(0.U.asTypeOf(new RobEntry))))

      override def view(idx: UInt): RobEntry =
        entries(idx)

      override def readCommit(idx: UInt, en: Bool): RobEntry = {
        val out = RegInit(0.U.asTypeOf(new RobEntry))
        when(en) {
          out := entries(idx)
        }
        out
      }

      override def write(idx: UInt, entry: RobEntry, en: Bool): Unit =
        when(en) {
          entries(idx) := entry
        }

      override def clearValid(): Unit =
        for (i <- 0 until p(RobSize))
          entries(i).valid := false.B
    }
  }

  override def registry: NodeDimensionRegistry[RobStorageImpl] =
    RobStorageFactory
}
