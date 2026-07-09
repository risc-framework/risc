package arch.configs.runtime

import arch.configs.Parameters
import arch.isa.Isa
import arch.system.device.DeviceDescriptor

object Riscv32BareMetalRuntimeBackend extends RuntimeBackend {
  override def family: String = "riscv32"

  override def profile: String = "bare-metal"

  override def renderLinkerScript(
    p: Parameters,
    isa: Isa,
    imem: DeviceDescriptor,
    dmem: DeviceDescriptor
  ): String =
    s"""OUTPUT_ARCH("riscv")
       |ENTRY(_start)
       |
       |MEMORY
       |{
       |    IMEM (rx) : ORIGIN = ${hex(imem.base)}, LENGTH = ${hex(imem.size)}
       |    DMEM (rw) : ORIGIN = ${hex(dmem.base)}, LENGTH = ${hex(dmem.size)}
       |}
       |
       |SECTIONS
       |{
       |    .text : {
       |        KEEP(*(.text.entry))
       |        *(.init)
       |        *(.init*)
       |        *(.text)
       |        *(.text*)
       |        . = ALIGN(4);
       |    } > IMEM
       |
       |    .rodata : {
       |        *(.srodata)
       |        *(.srodata*)
       |        *(.rodata)
       |        *(.rodata*)
       |        . = ALIGN(4);
       |    } > IMEM
       |
       |    .data : {
       |        . = ALIGN(4);
       |        __data_start = .;
       |        __sdata_start = .;
       |        PROVIDE(__global_pointer$$ = __sdata_start + 0x800);
       |
       |        *(.sdata)
       |        *(.sdata*)
       |        *(.data)
       |        *(.data*)
       |
       |        . = ALIGN(4);
       |        __data_end = .;
       |    } > DMEM
       |
       |    .bss : {
       |        . = ALIGN(4);
       |        _sbss = .;
       |        __bss_start = .;
       |
       |        *(.sbss)
       |        *(.sbss*)
       |        *(.scommon)
       |        *(.bss)
       |        *(.bss*)
       |        *(COMMON)
       |
       |        . = ALIGN(4);
       |        _ebss = .;
       |        __bss_end = .;
       |    } > DMEM
       |
       |    . = ALIGN(16);
       |    __end = .;
       |    _end = .;
       |
       |    __imem_base = ORIGIN(IMEM);
       |    __imem_size = LENGTH(IMEM);
       |    __imem_end = ORIGIN(IMEM) + LENGTH(IMEM);
       |
       |    __dmem_base = ORIGIN(DMEM);
       |    __dmem_size = LENGTH(DMEM);
       |    __dmem_end = ORIGIN(DMEM) + LENGTH(DMEM);
       |
       |    __stack = ORIGIN(DMEM) + LENGTH(DMEM);
       |    __stack_top = __stack;
       |
       |    ASSERT(__data_end <= ORIGIN(DMEM) + LENGTH(DMEM), "DMEM overflow in .data")
       |    ASSERT(__bss_end <= ORIGIN(DMEM) + LENGTH(DMEM), "DMEM overflow in .bss")
       |
       |    /DISCARD/ : {
       |        *(.comment)
       |        *(.eh_frame)
       |        *(.riscv.attributes)
       |        *(.note*)
       |    }
       |}
       |""".stripMargin

  override def renderStartupSource(
    p: Parameters,
    isa: Isa,
    imem: DeviceDescriptor,
    dmem: DeviceDescriptor
  ): String =
    """# start.S - Minimal RISC-V startup code
      |.section .text.entry, "ax"
      |.globl _start
      |
      |_start:
      |    .option push
      |    .option norelax
      |    la gp, __global_pointer$
      |    .option pop
      |
      |    la sp, __stack
      |    andi sp, sp, -16
      |
      |    mv fp, zero
      |
      |    la t0, _sbss
      |    la t1, _ebss
      |    bgeu t0, t1, bss_clear_done
      |
      |bss_clear_loop:
      |    sw zero, 0(t0)
      |    addi t0, t0, 4
      |    bltu t0, t1, bss_clear_loop
      |
      |bss_clear_done:
      |    jal ra, main
      |
      |halt:
      |    j halt
      |""".stripMargin

  private def hex(value: Long): String =
    "0x" + java.lang.Long.toUnsignedString(value, 16).toUpperCase
}

object Riscv32BareMetalRuntimeInit {
  val backend: RuntimeBackend =
    RuntimeBackendFactory.register(Riscv32BareMetalRuntimeBackend)
}
