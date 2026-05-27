package arch.cpp

private[cpp] object CppTypeDsl {
  final case class CppType(value: String) {
    def ptr: CppType              = CppType(s"$value *")
    override def toString: String = value
  }

  val boolean: CppType = CppType("bool")
  val u8: CppType      = CppType("uint8_t")
  val u32: CppType     = CppType("uint32_t")
  val u64: CppType     = CppType("uint64_t")
  val addr: CppType    = CppType("::demu::isa_def::addr_t")
  val instr: CppType   = CppType("::demu::isa_def::instr_t")
  val word: CppType    = CppType("::demu::isa_def::word_t")
  val sizeT: CppType   = CppType("size_t")
}
