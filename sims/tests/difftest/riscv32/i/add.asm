// RUN: %bare_asm
// RUN: %difftest -c 50

.section .text.entry, "ax"
.globl _start

_start:
    addi x1, x0, 10
    addi x2, x0, 20
    add x3, x1, x2

    j .
