package arch.system

sealed abstract class DeviceType(
  val index: Int,
  val cppName: String
)

object DeviceType {
  case object DEVICE_TYPE_UNKNOWN extends DeviceType(0, "UNKNOWN")

  case object DEVICE_TYPE_SRAM extends DeviceType(1, "SRAM")

  case object DEVICE_TYPE_UART extends DeviceType(2, "UART")

  case object DEVICE_TYPE_IRH extends DeviceType(3, "IRH")

  val values: Seq[DeviceType] =
    Seq(
      DEVICE_TYPE_UNKNOWN,
      DEVICE_TYPE_SRAM,
      DEVICE_TYPE_UART,
      DEVICE_TYPE_IRH
    )
}

final case class DeviceDescriptor(
  name: String,
  `type`: DeviceType,
  base: Long,
  size: Long
)
