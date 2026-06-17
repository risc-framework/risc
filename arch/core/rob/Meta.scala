package arch.core.rob

import arch.configs._
import vutils.graph.NodeDims
import chisel3._

abstract class RobStorage {
  def view(idx: UInt): RobEntry
  def readCommit(idx: UInt, en: Bool): RobEntry
  def write(idx: UInt, entry: RobEntry, en: Bool): Unit
  def clearValid(): Unit
}

object RobDims extends NodeDims("rob") {
  val STORAGE = dim("storage")
}

trait RobStorageImpl extends RobDims.STORAGE.Impl {
  def make(implicit p: Parameters): RobStorage
}

object RobStorageFactory extends RobDims.STORAGE.Registry[RobStorageImpl]

object RobInit {
  val reg = impls.storage.reg.RegRobStorageImpl.registered
}
