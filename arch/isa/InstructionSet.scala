package arch.isa

final case class InstructionSet(
  nop: Option[InstructionForm] = None,
  forms: Seq[InstructionForm] = Seq.empty
) {
  def all: Seq[InstructionForm] =
    nop.toSeq ++ forms

  def aliases: Seq[InstructionForm] =
    all.filter(_.isAlias)

  def primary: Seq[InstructionForm] =
    forms.filterNot(_.isAlias)

  def displayOrder: Seq[InstructionForm] =
    all.sortBy(f => (-f.priority, f.id))

  def decodeOrder: Seq[InstructionForm] =
    primary.sortBy(f => (-f.priority, f.id))

  def get(id: String): InstructionForm =
    all
      .find(_.id == id)
      .getOrElse(throw new NoSuchElementException(s"Instruction '$id' not found"))

  def getForm(id: String): InstructionForm =
    all
      .find(_.id == id)
      .getOrElse(throw new NoSuchElementException(s"Instruction form '$id' not found"))

  def fixedForms: Seq[InstructionForm] =
    all.collect { case f if f.pattern.isInstanceOf[FixedBitPattern] => f }

  def byteForms: Seq[InstructionForm] =
    all.collect { case f if f.pattern.isInstanceOf[BytePattern] => f }

  def decoderForms: Seq[InstructionForm] =
    all.collect { case f if f.pattern.isInstanceOf[DecoderPattern] => f }

  def hasOnlyFixedBitForms: Boolean =
    all.nonEmpty && all.forall(_.pattern.isInstanceOf[FixedBitPattern])

  def hasOnlyGenericMatchableForms: Boolean =
    all.forall(_.pattern.isGenericMatchable)

  def validate(checkFixedOverlaps: Boolean = true): Unit = {
    val duplicateIds = all.groupBy(_.id).collect { case (id, xs) if xs.size > 1 => id }.toSeq
    val primaryIds   = primary.map(_.id).toSet

    require(duplicateIds.isEmpty, s"duplicate instruction form ids: ${duplicateIds.mkString(", ")}")

    aliases.foreach { alias =>
      val base = alias.aliasOf.get
      require(
        primaryIds.contains(base),
        s"instruction alias '${alias.id}' references missing primary instruction '$base'"
      )
    }

    if (checkFixedOverlaps) {
      val fixed = primary.collect { case f if f.pattern.isInstanceOf[FixedBitPattern] => f }

      for {
        (lhs, index) <- fixed.zipWithIndex
        rhs          <- fixed.drop(index + 1)
      } {
        val lp = lhs.pattern.asInstanceOf[FixedBitPattern]
        val rp = rhs.pattern.asInstanceOf[FixedBitPattern]
        require(
          !lp.overlaps(rp),
          s"instruction fixed encodings overlap: '${lhs.id}' and '${rhs.id}'"
        )
      }
    }
  }
}
