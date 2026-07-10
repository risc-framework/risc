RISC_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))

SCALA_CONFIG_MK := $(RISC_DIR)/build/generated/config.mk
KCONFIG_MK := $(RISC_DIR)/build/generated/kconfig.mk

-include $(SCALA_CONFIG_MK)
-include $(KCONFIG_MK)

RISC_BUILD_DIR ?= $(RISC_DIR)/build
BUILD_DIR ?= $(RISC_BUILD_DIR)
GEN_DIR ?= $(RISC_BUILD_DIR)
GENERATED_INCLUDE_DIR ?= $(RISC_BUILD_DIR)/include
SYNTH_DIR ?= $(RISC_DIR)/synth
SIM_DIR ?= $(RISC_DIR)/sims
SIM_BUILD_DIR ?= $(RISC_BUILD_DIR)/sim
RUNTIME_ROOT ?= $(RISC_BUILD_DIR)/runtime

TARGET_FAMILY ?=
TARGET_ARCH ?=
TOP_MODULE ?=
FAMILY ?= $(TARGET_FAMILY)
ARCH ?= $(TARGET_ARCH)

RUNTIME_PROFILE ?= bare-metal
LINKER_SCRIPT_NAME ?= linker.ld
STARTUP_SOURCE_NAME ?= start.S
RUNTIME_DIR ?= $(RUNTIME_ROOT)/$(RUNTIME_PROFILE)/$(TARGET_FAMILY)
LINKER_SCRIPT ?= $(RUNTIME_DIR)/$(LINKER_SCRIPT_NAME)
STARTUP_SOURCE ?= $(RUNTIME_DIR)/$(STARTUP_SOURCE_NAME)
RTL_SOURCE ?= $(GEN_DIR)/$(TOP_MODULE).sv

LIB ?= arch
FZF ?= $(shell [ -x "$$(command -v fzf)" ] && echo true || echo false)
STA_TOOL ?= yosys
GENERATOR ?= Ninja
BUILD_TYPE ?= Release

ENABLE_BAREMETAL_RUNTIME ?= 1
BAREMETAL_CC ?=
BAREMETAL_OBJCOPY ?=
BAREMETAL_OBJDUMP ?=
BAREMETAL_CFLAGS ?= -O3 -ffreestanding -fno-builtin -fno-common -ffunction-sections -fdata-sections -Wall
BAREMETAL_ASFLAGS ?= -x assembler-with-cpp
BAREMETAL_LDFLAGS ?= -nostartfiles -nostdlib -Wl,--gc-sections
BAREMETAL_PACKAGE_DIR ?= $(RUNTIME_DIR)
BAREMETAL_STARTUP_OBJ ?= $(BAREMETAL_PACKAGE_DIR)/obj/start.o
BAREMETAL_LINKER_SCRIPT ?= $(LINKER_SCRIPT)
BAREMETAL_EXPORT_MK ?= $(BAREMETAL_PACKAGE_DIR)/baremetal.mk

ENABLE_RTTHREAD ?= 0
RTTHREAD_PLATFORM ?= demu
RTTHREAD_LIBCPU_TARGET ?= manual
RTTHREAD_ROOT ?= $(RISC_DIR)/sims/runtime/rtthread-nano/rt-thread
RTTHREAD_PACKAGE_ROOT ?= $(RUNTIME_ROOT)/rtthread-nano
RTTHREAD_TARGET := $(RTTHREAD_PLATFORM)-$(TARGET_FAMILY)-$(TARGET_ARCH)

RTTHREAD_BSP_DIR_REL ?=
ifeq ($(strip $(RTTHREAD_BSP_DIR_REL)),)
  RTTHREAD_BSP_DIR_REL := bsp/$(RTTHREAD_TARGET)
endif

RTTHREAD_EXTRA_INC_DIRS_REL ?=
RTTHREAD_PORT_C_SRCS_REL ?=
RTTHREAD_PORT_ASM_SRCS_REL ?=

RTTHREAD_CC ?=
RTTHREAD_AR ?=
RTTHREAD_OBJCOPY ?=
RTTHREAD_OBJDUMP ?=
RTTHREAD_CFLAGS ?= -Os -ffunction-sections -fdata-sections -fno-common -fno-builtin -ffreestanding -Wall
RTTHREAD_ASFLAGS ?= -x assembler-with-cpp
RTTHREAD_LDFLAGS ?=
RTTHREAD_PACKAGE_DIR ?= $(RTTHREAD_PACKAGE_ROOT)/$(RTTHREAD_TARGET)

RTTHREAD_LIB_DIR := $(RTTHREAD_PACKAGE_DIR)/lib
RTTHREAD_OBJ_DIR := $(RTTHREAD_PACKAGE_DIR)/obj
RTTHREAD_LIB ?= $(RTTHREAD_LIB_DIR)/librtthread-nano.a
RTTHREAD_EXPORT_MK ?= $(RTTHREAD_PACKAGE_DIR)/rtthread-nano.mk

RTTHREAD_STARTUP_OBJ := $(BAREMETAL_STARTUP_OBJ)
RTTHREAD_LINKER_SCRIPT := $(BAREMETAL_LINKER_SCRIPT)

RTTHREAD_BSP_DIR := $(RTTHREAD_ROOT)/$(RTTHREAD_BSP_DIR_REL)

RTTHREAD_INC_DIRS := \
	-I$(RTTHREAD_BSP_DIR) \
	-I$(RTTHREAD_ROOT)/include \
	$(addprefix -I$(RTTHREAD_ROOT)/,$(RTTHREAD_EXTRA_INC_DIRS_REL))

RTTHREAD_LIB_C_SRCS_REL := \
	$(RTTHREAD_BSP_DIR_REL)/board.c \
	$(RTTHREAD_PORT_C_SRCS_REL) \
	src/clock.c \
	src/components.c \
	src/idle.c \
	src/ipc.c \
	src/irq.c \
	src/kservice.c \
	src/mem.c \
	src/object.c \
	src/scheduler.c \
	src/thread.c \
	src/timer.c

RTTHREAD_LIB_ASM_SRCS_REL := $(RTTHREAD_PORT_ASM_SRCS_REL)

RTTHREAD_LIB_C_OBJS := $(addprefix $(RTTHREAD_OBJ_DIR)/,$(patsubst %.c,%.o,$(RTTHREAD_LIB_C_SRCS_REL)))
RTTHREAD_LIB_ASM_OBJS := $(addprefix $(RTTHREAD_OBJ_DIR)/,$(patsubst %.S,%.o,$(RTTHREAD_LIB_ASM_SRCS_REL)))
RTTHREAD_LIB_OBJS := $(RTTHREAD_LIB_ASM_OBJS) $(RTTHREAD_LIB_C_OBJS)

COREMARK_ENABLE_DEBUG ?= 0
COREMARK_TOTAL_DATA_SIZE ?= 2000
COREMARK_ITERATIONS ?= 1
COREMARK_EXECS ?= 1

.PHONY: all
.PHONY: pre defconfig olddefconfig menuconfig kconfig
.PHONY: fmt build rtl
.PHONY: generated-check
.PHONY: sim-config sim difftest coremark
.PHONY: baremetal baremetal-check baremetal-package baremetal-info baremetal-clean
.PHONY: rtthread rtthread-check rtthread-package rtthread-info rtthread-clean
.PHONY: synth-clean sim-clean clean update
.PHONY: sta sta-yosys sta-vivado help
.PHONY: __sim_config __sim __difftest __coremark
.PHONY: __baremetal __baremetal_info
.PHONY: __rtthread __rtthread_info

all: rtl

pre:
	@mkdir -p $(RISC_BUILD_DIR)
	@mkdir -p $(RISC_BUILD_DIR)/generated
	@mkdir -p $(GENERATED_INCLUDE_DIR)
	@mkdir -p $(SYNTH_DIR)

defconfig: pre
	@python3 $(RISC_DIR)/scripts/kconfig_emit.py \
		--kconfig $(RISC_DIR)/Kconfig \
		--config $(RISC_DIR)/.config \
		--mk $(RISC_DIR)/build/generated/kconfig.mk \
		--cmake $(RISC_DIR)/build/generated/kconfig.cmake \
		--defconfig

olddefconfig: pre
	@python3 $(RISC_DIR)/scripts/kconfig_emit.py \
		--kconfig $(RISC_DIR)/Kconfig \
		--config $(RISC_DIR)/.config \
		--mk $(RISC_DIR)/build/generated/kconfig.mk \
		--cmake $(RISC_DIR)/build/generated/kconfig.cmake

menuconfig:
	@cd $(RISC_DIR) && KCONFIG_CONFIG=$(RISC_DIR)/.config python3 -m menuconfig $(RISC_DIR)/Kconfig
	@$(MAKE) olddefconfig

kconfig:
	@if [ ! -f "$(RISC_DIR)/.config" ]; then \
		$(MAKE) defconfig; \
	else \
		$(MAKE) olddefconfig; \
	fi

fmt:
	@scalafmt

build: pre
	@sbt compile

rtl: pre kconfig
	@sbt "$(LIB)/run"

generated-check:
	@test -f "$(SCALA_CONFIG_MK)" || \
		(echo "missing $(SCALA_CONFIG_MK). Run: make rtl"; exit 1)
	@test -f "$(KCONFIG_MK)" || \
		(echo "missing $(KCONFIG_MK). Run: make kconfig"; exit 1)
	@test -n "$(TARGET_FAMILY)" || \
		(echo "TARGET_FAMILY is empty. Run: make rtl"; exit 1)
	@test -n "$(TARGET_ARCH)" || \
		(echo "TARGET_ARCH is empty. Run: make rtl"; exit 1)
	@test -n "$(TOP_MODULE)" || \
		(echo "TOP_MODULE is empty. Run: make rtl"; exit 1)
	@test -n "$(FAMILY)" || \
		(echo "FAMILY is empty. Run: make rtl"; exit 1)
	@test -n "$(ARCH)" || \
		(echo "ARCH is empty. Run: make rtl"; exit 1)
	@test -n "$(RTL_SOURCE)" || \
		(echo "RTL_SOURCE is empty. Run: make kconfig"; exit 1)
	@test -n "$(LINKER_SCRIPT)" || \
		(echo "LINKER_SCRIPT is empty. Run: make kconfig"; exit 1)
	@test -n "$(STARTUP_SOURCE)" || \
		(echo "STARTUP_SOURCE is empty. Run: make kconfig"; exit 1)
	@test -f "$(RTL_SOURCE)" || \
		(echo "missing RTL source: $(RTL_SOURCE). Run: make rtl"; exit 1)
	@test -f "$(LINKER_SCRIPT)" || \
		(echo "missing generated linker script: $(LINKER_SCRIPT). Run: make rtl"; exit 1)
	@test -f "$(STARTUP_SOURCE)" || \
		(echo "missing generated startup source: $(STARTUP_SOURCE). Run: make rtl"; exit 1)

sim-config: kconfig
	@$(MAKE) __sim_config

__sim_config: generated-check
	@echo "==> Configuring simulator project..."
	@mkdir -p $(SIM_BUILD_DIR)
	@cmake -S $(SIM_DIR) -B $(SIM_BUILD_DIR) \
		-G "$(GENERATOR)" \
		-DCMAKE_BUILD_TYPE="$(BUILD_TYPE)"

sim: kconfig
	@$(MAKE) __sim

__sim: __sim_config
	@echo "==> Building simulator..."
	@cmake --build $(SIM_BUILD_DIR)

difftest: kconfig
	@$(MAKE) __difftest

__difftest: __sim_config
	@echo "==> Running difftests..."
	@cmake --build $(SIM_BUILD_DIR) --target check-difftest

coremark: kconfig
	@$(MAKE) __coremark

__coremark: generated-check
	@echo "==> Running CoreMark..."
	@mkdir -p $(SIM_BUILD_DIR)
	@mkdir -p $(SIM_BUILD_DIR)/tests/coremark
	@cd $(SIM_DIR)/tests/coremark && $(MAKE) \
		PORT_DIR=$(FAMILY) \
		ARCH=$(ARCH) \
		OPATH=$(SIM_BUILD_DIR)/tests/coremark/ \
		RUNTIME_DIR=$(RUNTIME_DIR) \
		ITERATIONS=$(COREMARK_ITERATIONS) \
		EXECS=$(COREMARK_EXECS) \
		TOTAL_DATA_SIZE=$(COREMARK_TOTAL_DATA_SIZE) \
		ENABLE_DEBUG=$(COREMARK_ENABLE_DEBUG)

baremetal: kconfig
	@$(MAKE) __baremetal

__baremetal: baremetal-check baremetal-package
	@echo ""
	@echo "  Bare-metal runtime package generated:"
	@echo "    FAMILY  : $(TARGET_FAMILY)"
	@echo "    ARCH    : $(TARGET_ARCH)"
	@echo "    LINKER  : $(BAREMETAL_LINKER_SCRIPT)"
	@echo "    START.S : $(STARTUP_SOURCE)"
	@echo "    START.O : $(BAREMETAL_STARTUP_OBJ)"
	@echo "    EXPORT  : $(BAREMETAL_EXPORT_MK)"
	@echo ""

baremetal-check: generated-check
	@test "$(ENABLE_BAREMETAL_RUNTIME)" = "1" || \
		(echo "Bare-metal runtime package build is disabled. Enable ENABLE_BAREMETAL_RUNTIME in make menuconfig."; exit 1)
	@test -n "$(BAREMETAL_CC)" || \
		(echo "BAREMETAL_CC is empty. Set it in make menuconfig for your target architecture."; exit 1)
	@test -n "$(BAREMETAL_OBJCOPY)" || \
		(echo "BAREMETAL_OBJCOPY is empty. Set it in make menuconfig for your target architecture."; exit 1)
	@test -n "$(BAREMETAL_OBJDUMP)" || \
		(echo "BAREMETAL_OBJDUMP is empty. Set it in make menuconfig for your target architecture."; exit 1)
	@test -f "$(STARTUP_SOURCE)" || \
		(echo "missing bare-metal startup source: $(STARTUP_SOURCE). Run: make rtl"; exit 1)
	@test -f "$(BAREMETAL_LINKER_SCRIPT)" || \
		(echo "missing bare-metal linker script: $(BAREMETAL_LINKER_SCRIPT). Run: make rtl"; exit 1)

baremetal-package: $(BAREMETAL_STARTUP_OBJ) $(BAREMETAL_EXPORT_MK)

$(BAREMETAL_STARTUP_OBJ): $(STARTUP_SOURCE)
	@mkdir -p $(dir $@)
	$(BAREMETAL_CC) $(BAREMETAL_CFLAGS) $(BAREMETAL_ASFLAGS) -c $< -o $@

$(BAREMETAL_EXPORT_MK): $(BAREMETAL_STARTUP_OBJ) $(BAREMETAL_LINKER_SCRIPT)
	@mkdir -p $(dir $@)
	@printf '%s\n' \
		'# Generated bare-metal runtime package fragment. Do not edit.' \
		'BAREMETAL_PACKAGE_DIR := $(BAREMETAL_PACKAGE_DIR)' \
		'BAREMETAL_CC := $(BAREMETAL_CC)' \
		'BAREMETAL_OBJCOPY := $(BAREMETAL_OBJCOPY)' \
		'BAREMETAL_OBJDUMP := $(BAREMETAL_OBJDUMP)' \
		'BAREMETAL_CFLAGS := $(BAREMETAL_CFLAGS)' \
		'BAREMETAL_ASFLAGS := $(BAREMETAL_ASFLAGS)' \
		'BAREMETAL_STARTUP_OBJ := $(BAREMETAL_STARTUP_OBJ)' \
		'BAREMETAL_LINKER_SCRIPT := $(BAREMETAL_LINKER_SCRIPT)' \
		'BAREMETAL_LDFLAGS := -T $(BAREMETAL_LINKER_SCRIPT) $(BAREMETAL_LDFLAGS)' \
		> $@

baremetal-info: kconfig
	@$(MAKE) __baremetal_info

__baremetal_info:
	@echo "ENABLE_BAREMETAL_RUNTIME = $(ENABLE_BAREMETAL_RUNTIME)"
	@echo "BAREMETAL_PACKAGE_DIR    = $(BAREMETAL_PACKAGE_DIR)"
	@echo "BAREMETAL_CC             = $(BAREMETAL_CC)"
	@echo "BAREMETAL_OBJCOPY        = $(BAREMETAL_OBJCOPY)"
	@echo "BAREMETAL_OBJDUMP        = $(BAREMETAL_OBJDUMP)"
	@echo "BAREMETAL_CFLAGS         = $(BAREMETAL_CFLAGS)"
	@echo "BAREMETAL_ASFLAGS        = $(BAREMETAL_ASFLAGS)"
	@echo "BAREMETAL_LDFLAGS        = $(BAREMETAL_LDFLAGS)"
	@echo "BAREMETAL_STARTUP_OBJ    = $(BAREMETAL_STARTUP_OBJ)"
	@echo "BAREMETAL_LINKER_SCRIPT  = $(BAREMETAL_LINKER_SCRIPT)"
	@echo "BAREMETAL_EXPORT_MK      = $(BAREMETAL_EXPORT_MK)"

baremetal-clean:
	@rm -rf $(BAREMETAL_PACKAGE_DIR)/obj
	@rm -f $(BAREMETAL_EXPORT_MK)

rtthread: kconfig
	@$(MAKE) __rtthread

__rtthread: rtthread-check rtthread-package
	@echo ""
	@echo "  RT-Thread Nano runtime package generated:"
	@echo "    PLATFORM : $(RTTHREAD_PLATFORM)"
	@echo "    LIBCPU   : $(RTTHREAD_LIBCPU_TARGET)"
	@echo "    TARGET   : $(RTTHREAD_TARGET)"
	@echo "    BSP      : $(RTTHREAD_BSP_DIR)"
	@echo "    LIB      : $(RTTHREAD_LIB)"
	@echo "    BOOT     : $(BAREMETAL_EXPORT_MK)"
	@echo "    STARTUP  : $(RTTHREAD_STARTUP_OBJ)"
	@echo "    LINKER   : $(RTTHREAD_LINKER_SCRIPT)"
	@echo "    EXPORT   : $(RTTHREAD_EXPORT_MK)"
	@echo ""

rtthread-check: baremetal-check
	@test "$(ENABLE_RTTHREAD)" = "1" || \
		(echo "RT-Thread package build is disabled. Enable ENABLE_RTTHREAD in make menuconfig."; exit 1)
	@test -n "$(RTTHREAD_PLATFORM)" || \
		(echo "RTTHREAD_PLATFORM is empty. Set it in make menuconfig."; exit 1)
	@test -n "$(RTTHREAD_LIBCPU_TARGET)" || \
		(echo "RTTHREAD_LIBCPU_TARGET is empty. Set it in make menuconfig."; exit 1)
	@test -n "$(TARGET_FAMILY)" || \
		(echo "TARGET_FAMILY is empty. Run: make rtl"; exit 1)
	@test -n "$(TARGET_ARCH)" || \
		(echo "TARGET_ARCH is empty. Run: make rtl"; exit 1)
	@test -n "$(RTTHREAD_TARGET)" || \
		(echo "RTTHREAD_TARGET is empty. Run: make rtl"; exit 1)
	@test -n "$(RTTHREAD_CC)" || \
		(echo "RTTHREAD_CC is empty. Set it in make menuconfig for your selected RT-Thread platform."; exit 1)
	@test -n "$(RTTHREAD_AR)" || \
		(echo "RTTHREAD_AR is empty. Set it in make menuconfig for your selected RT-Thread platform."; exit 1)
	@test -n "$(RTTHREAD_OBJCOPY)" || \
		(echo "RTTHREAD_OBJCOPY is empty. Set it in make menuconfig for your selected RT-Thread platform."; exit 1)
	@test -n "$(RTTHREAD_OBJDUMP)" || \
		(echo "RTTHREAD_OBJDUMP is empty. Set it in make menuconfig for your selected RT-Thread platform."; exit 1)
	@test -d "$(RTTHREAD_ROOT)" || \
		(echo "missing RT-Thread root: $(RTTHREAD_ROOT)"; exit 1)
	@test -d "$(RTTHREAD_BSP_DIR)" || \
		(echo "missing RT-Thread BSP dir: $(RTTHREAD_BSP_DIR)"; exit 1)
	@test -f "$(RTTHREAD_BSP_DIR)/board.c" || \
		(echo "missing RT-Thread board source: $(RTTHREAD_BSP_DIR)/board.c"; exit 1)
	@test -n "$(RTTHREAD_PORT_C_SRCS_REL)$(RTTHREAD_PORT_ASM_SRCS_REL)" || \
		(echo "RT-Thread port sources are empty. Select RTTHREAD_LIBCPU_TARGET or set RTTHREAD_PORT_* in make menuconfig."; exit 1)
	@for inc in $(RTTHREAD_EXTRA_INC_DIRS_REL); do \
		test -d "$(RTTHREAD_ROOT)/$$inc" || \
			(echo "missing RT-Thread extra include dir: $(RTTHREAD_ROOT)/$$inc"; exit 1); \
	done
	@for src in $(RTTHREAD_PORT_C_SRCS_REL) $(RTTHREAD_PORT_ASM_SRCS_REL); do \
		test -f "$(RTTHREAD_ROOT)/$$src" || \
			(echo "missing RT-Thread port source: $(RTTHREAD_ROOT)/$$src"; exit 1); \
	done

rtthread-package: $(BAREMETAL_EXPORT_MK) $(RTTHREAD_LIB) $(RTTHREAD_EXPORT_MK)

$(RTTHREAD_OBJ_DIR)/%.o: $(RTTHREAD_ROOT)/%.c
	@mkdir -p $(dir $@)
	$(RTTHREAD_CC) $(RTTHREAD_CFLAGS) $(RTTHREAD_INC_DIRS) -c $< -o $@

$(RTTHREAD_OBJ_DIR)/%.o: $(RTTHREAD_ROOT)/%.S
	@mkdir -p $(dir $@)
	$(RTTHREAD_CC) $(RTTHREAD_CFLAGS) $(RTTHREAD_ASFLAGS) $(RTTHREAD_INC_DIRS) -c $< -o $@

$(RTTHREAD_LIB): $(RTTHREAD_LIB_OBJS)
	@mkdir -p $(dir $@)
	$(RTTHREAD_AR) rcs $@ $(RTTHREAD_LIB_OBJS)

$(RTTHREAD_EXPORT_MK): $(RTTHREAD_LIB) $(BAREMETAL_EXPORT_MK)
	@mkdir -p $(dir $@)
	@printf '%s\n' \
		'# Generated RT-Thread Nano package fragment. Do not edit.' \
		'include $(BAREMETAL_EXPORT_MK)' \
		'RTTHREAD_PLATFORM := $(RTTHREAD_PLATFORM)' \
		'RTTHREAD_LIBCPU_TARGET := $(RTTHREAD_LIBCPU_TARGET)' \
		'RTTHREAD_TARGET := $(RTTHREAD_TARGET)' \
		'RTTHREAD_PACKAGE_DIR := $(RTTHREAD_PACKAGE_DIR)' \
		'RTTHREAD_CC := $(RTTHREAD_CC)' \
		'RTTHREAD_AR := $(RTTHREAD_AR)' \
		'RTTHREAD_OBJCOPY := $(RTTHREAD_OBJCOPY)' \
		'RTTHREAD_OBJDUMP := $(RTTHREAD_OBJDUMP)' \
		'RTTHREAD_CFLAGS := $(RTTHREAD_CFLAGS)' \
		'RTTHREAD_ASFLAGS := $(RTTHREAD_ASFLAGS)' \
		'RTTHREAD_INC_DIRS := $(RTTHREAD_INC_DIRS)' \
		'RTTHREAD_LIB := $(RTTHREAD_LIB)' \
		'RTTHREAD_STARTUP_OBJ := $$(BAREMETAL_STARTUP_OBJ)' \
		'RTTHREAD_LINKER_SCRIPT := $$(BAREMETAL_LINKER_SCRIPT)' \
		'RTTHREAD_LDFLAGS := $$(BAREMETAL_LDFLAGS) $(RTTHREAD_LDFLAGS)' \
		> $@

rtthread-info: kconfig
	@$(MAKE) __rtthread_info

__rtthread_info:
	@echo "ENABLE_RTTHREAD              = $(ENABLE_RTTHREAD)"
	@echo "RTTHREAD_PLATFORM            = $(RTTHREAD_PLATFORM)"
	@echo "RTTHREAD_LIBCPU_TARGET       = $(RTTHREAD_LIBCPU_TARGET)"
	@echo "RTTHREAD_TARGET              = $(RTTHREAD_TARGET)"
	@echo "RTTHREAD_ROOT                = $(RTTHREAD_ROOT)"
	@echo "RTTHREAD_PACKAGE_ROOT        = $(RTTHREAD_PACKAGE_ROOT)"
	@echo "RTTHREAD_PACKAGE_DIR         = $(RTTHREAD_PACKAGE_DIR)"
	@echo "RTTHREAD_BSP_DIR_REL         = $(RTTHREAD_BSP_DIR_REL)"
	@echo "RTTHREAD_BSP_DIR             = $(RTTHREAD_BSP_DIR)"
	@echo "RTTHREAD_EXTRA_INC_DIRS_REL  = $(RTTHREAD_EXTRA_INC_DIRS_REL)"
	@echo "RTTHREAD_PORT_C_SRCS_REL     = $(RTTHREAD_PORT_C_SRCS_REL)"
	@echo "RTTHREAD_PORT_ASM_SRCS_REL   = $(RTTHREAD_PORT_ASM_SRCS_REL)"
	@echo "RTTHREAD_CC                  = $(RTTHREAD_CC)"
	@echo "RTTHREAD_AR                  = $(RTTHREAD_AR)"
	@echo "RTTHREAD_OBJCOPY             = $(RTTHREAD_OBJCOPY)"
	@echo "RTTHREAD_OBJDUMP             = $(RTTHREAD_OBJDUMP)"
	@echo "RTTHREAD_CFLAGS              = $(RTTHREAD_CFLAGS)"
	@echo "RTTHREAD_ASFLAGS             = $(RTTHREAD_ASFLAGS)"
	@echo "RTTHREAD_LDFLAGS             = $(RTTHREAD_LDFLAGS)"
	@echo "RTTHREAD_INC_DIRS            = $(RTTHREAD_INC_DIRS)"
	@echo "RTTHREAD_LIB                 = $(RTTHREAD_LIB)"
	@echo "RTTHREAD_STARTUP_OBJ         = $(RTTHREAD_STARTUP_OBJ)"
	@echo "RTTHREAD_LINKER_SCRIPT       = $(RTTHREAD_LINKER_SCRIPT)"
	@echo "RTTHREAD_EXPORT_MK           = $(RTTHREAD_EXPORT_MK)"
	@echo "BAREMETAL_EXPORT_MK          = $(BAREMETAL_EXPORT_MK)"

rtthread-clean:
	@rm -rf $(RTTHREAD_PACKAGE_ROOT)

synth-clean:
	@rm -rf $(SYNTH_DIR)

sim-clean:
	@rm -rf $(SIM_BUILD_DIR)

clean: synth-clean sim-clean baremetal-clean rtthread-clean
	@rm -rf $(RISC_BUILD_DIR)

update:
	@sbt bloopInstall
	@sbt update
	@sbt reload

sta-yosys: pre
	@if [ "$(FZF)" = "true" ] ; then \
		FZF=true bash $(RISC_DIR)/scripts/sta_yosys.sh ; \
	else \
		bash $(RISC_DIR)/scripts/sta_yosys.sh ; \
	fi

sta-vivado: pre
	@if [ "$(FZF)" = "true" ] ; then \
		FZF=true bash $(RISC_DIR)/scripts/sta_vivado.sh ; \
	else \
		bash $(RISC_DIR)/scripts/sta_vivado.sh ; \
	fi

sta: pre
	@if [ "$(STA_TOOL)" = "yosys" ] ; then \
		$(MAKE) sta-yosys FZF=$(FZF) ; \
	elif [ "$(STA_TOOL)" = "vivado" ] ; then \
		$(MAKE) sta-vivado FZF=$(FZF) ; \
	else \
		echo "Unsupported STA_TOOL: $(STA_TOOL)" ; \
		exit 1 ; \
	fi

help:
	@echo "Available targets:"
	@echo "  defconfig        - Generate default .config and Kconfig outputs"
	@echo "  menuconfig       - Edit .config with Kconfig menu UI"
	@echo "  kconfig          - Generate build/generated/kconfig.mk and kconfig.cmake"
	@echo "  build            - Compile Scala/Chisel sources"
	@echo "  rtl              - Generate RTL, C++ headers, runtime, and Scala config files"
	@echo "  baremetal        - Build bare-metal runtime package"
	@echo "  baremetal-info   - Print bare-metal package configuration"
	@echo "  rtthread         - Build RT-Thread Nano runtime package"
	@echo "  rtthread-info    - Print RT-Thread package configuration"
	@echo "  sim-config       - Configure simulator CMake build"
	@echo "  sim              - Build simulator from existing generated RTL"
	@echo "  difftest         - Run difftests from existing generated RTL"
	@echo "  coremark         - Run CoreMark from existing generated runtime"
	@echo "  sta              - Run STA using STA_TOOL"
	@echo "  synth-clean      - Remove synthesis artifacts"
	@echo "  sim-clean        - Remove simulator build directory"
	@echo "  baremetal-clean  - Remove bare-metal runtime package objects"
	@echo "  rtthread-clean   - Remove RT-Thread runtime package"
	@echo "  clean            - Remove generated build artifacts"
	@echo ""
	@echo "Typical flow:"
	@echo "  make rtl"
	@echo "  make baremetal"
	@echo "  make rtthread"
	@echo "  make sim"
	@echo "  make difftest"
	@echo ""
	@echo "Loaded configuration:"
	@echo "  TARGET_FAMILY              = $(TARGET_FAMILY)"
	@echo "  TARGET_ARCH                = $(TARGET_ARCH)"
	@echo "  FAMILY                     = $(FAMILY)"
	@echo "  ARCH                       = $(ARCH)"
	@echo "  TOP_MODULE                 = $(TOP_MODULE)"
	@echo "  GEN_DIR                    = $(GEN_DIR)"
	@echo "  RTL_SOURCE                 = $(RTL_SOURCE)"
	@echo "  RUNTIME_DIR                = $(RUNTIME_DIR)"
	@echo "  LINKER_SCRIPT              = $(LINKER_SCRIPT)"
	@echo "  STARTUP_SOURCE             = $(STARTUP_SOURCE)"
	@echo "  BAREMETAL_EXPORT_MK        = $(BAREMETAL_EXPORT_MK)"
	@echo "  RTTHREAD_PLATFORM          = $(RTTHREAD_PLATFORM)"
	@echo "  RTTHREAD_LIBCPU_TARGET     = $(RTTHREAD_LIBCPU_TARGET)"
	@echo "  RTTHREAD_TARGET            = $(RTTHREAD_TARGET)"
	@echo "  RTTHREAD_EXPORT_MK         = $(RTTHREAD_EXPORT_MK)"
	@echo "  BUILD_TYPE                 = $(BUILD_TYPE)"
	@echo "  GENERATOR                  = $(GENERATOR)"
	@echo "  STA_TOOL                   = $(STA_TOOL)"
	@echo "  SIM_BUILD_DIR              = $(SIM_BUILD_DIR)"
	@echo "  COREMARK_ITERATIONS        = $(COREMARK_ITERATIONS)"
	@echo "  COREMARK_EXECS             = $(COREMARK_EXECS)"
	@echo "  COREMARK_TOTAL_DATA_SIZE   = $(COREMARK_TOTAL_DATA_SIZE)"
