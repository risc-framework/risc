package arch.core.regfile

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class RegfileReadReq(implicit p: Parameters) extends Bundle {
  val addr = UInt(log2Ceil(p(NumArchRegs)).W)
}

class RegfileReadResp(implicit p: Parameters) extends Bundle {
  val data = UInt(p(XLen).W)
}

class RegfileWrite(implicit p: Parameters) extends Bundle {
  val addr = UInt(log2Ceil(p(NumArchRegs)).W)
  val data = UInt(p(XLen).W)
}
