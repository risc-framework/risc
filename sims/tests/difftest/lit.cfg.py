import os

import lit.formats

config.name = "Demu-Difftest"
config.test_format = lit.formats.ShTest(
    not bool(os.environ.get("LIT_USE_INTERNAL_SHELL"))
)
config.suffixes = [".c", ".S", ".asm"]

config.available_features.add(config.arch)

TOOLCHAINS = {
    "rv32i": {
        "family": "riscv32",
        "excludes": ["m"],
        "cc": "riscv64-unknown-elf-gcc",
        "cflags": "-march=rv32i_zicsr -mabi=ilp32 -mcmodel=medany -static -nostartfiles -nostdlib",
    },
    "rv32im": {
        "family": "riscv32",
        "excludes": [],
        "cc": "riscv64-unknown-elf-gcc",
        "cflags": "-march=rv32im_zicsr -mabi=ilp32 -mcmodel=medany -static -nostartfiles -nostdlib",
    },
}

if config.arch not in TOOLCHAINS:
    lit_config.fatal(f"Unsupported architecture for difftest: {config.arch}")

tc = TOOLCHAINS[config.arch]

config.available_features.add(tc["family"])

bare_c = f"{tc['cc']} {tc['cflags']} -T {config.baremetal_linker} {config.baremetal_start} %s -o %t.elf"
bare_asm = f"{tc['cc']} {tc['cflags']} -T {config.baremetal_linker} -x assembler-with-cpp %s -o %t.elf"

config.substitutions.append(("%bare_c", bare_c))
config.substitutions.append(("%bare_asm", bare_asm))

difftest_cmd = f"{config.difftest} -R gdb %t.elf -L5"
config.substitutions.append(("%difftest", difftest_cmd))

config.test_source_root = os.path.join(config.src_root, "tests/difftest", tc["family"])

config.excludes = []
for exclude_dir in tc["excludes"]:
    config.excludes.append(exclude_dir)
