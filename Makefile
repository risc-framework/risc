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

LIB ?= arch
FZF ?= $(shell [ -x "$$(command -v fzf)" ] && echo true || echo false)
STA_TOOL ?= yosys

.PHONY: all pre defconfig olddefconfig menuconfig kconfig fmt build run rtl runtime-check sim difftest coremark clean sim-clean distclean update sta sta-yosys sta-vivado help
.PHONY: __sim __difftest __coremark

all: run

pre:
	@mkdir -p $(RISC_BUILD_DIR)
	@mkdir -p $(RISC_BUILD_DIR)/generated
	@mkdir -p $(GENERATED_INCLUDE_DIR)
	@mkdir -p $(SYNTH_DIR)

defconfig:
	@python3 $(RISC_DIR)/scripts/kconfig_emit.py \
		--kconfig $(RISC_DIR)/Kconfig \
		--config $(RISC_DIR)/.config \
		--mk $(RISC_DIR)/build/generated/kconfig.mk \
		--cmake $(RISC_DIR)/build/generated/kconfig.cmake \
		--defconfig

olddefconfig:
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

run: pre kconfig
	@sbt "$(LIB)/run"

rtl: run

runtime-check:
	@test -n "$(TARGET_FAMILY)" || (echo "TARGET_FAMILY is missing. Run make run first."; exit 1)
	@test -f "$(LINKER_SCRIPT)" || (echo "missing generated linker script: $(LINKER_SCRIPT)"; exit 1)
	@test -f "$(STARTUP_SOURCE)" || (echo "missing generated startup source: $(STARTUP_SOURCE)"; exit 1)

sim: kconfig run
	@$(MAKE) __sim

__sim:
	@$(MAKE) -C $(SIM_DIR) build

difftest: kconfig run
	@$(MAKE) __difftest

__difftest:
	@$(MAKE) -C $(SIM_DIR) difftest

coremark: kconfig run
	@$(MAKE) __coremark

__coremark:
	@$(MAKE) -C $(SIM_DIR) coremark

clean:
	@rm -rf $(SYNTH_DIR)

sim-clean:
	@$(MAKE) -C $(SIM_DIR) clean

distclean: clean sim-clean
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
	@echo "  defconfig     - Generate default .config and Kconfig outputs"
	@echo "  menuconfig    - Edit .config with Kconfig menu UI"
	@echo "  kconfig       - Generate build/generated/kconfig.mk and kconfig.cmake"
	@echo "  build         - Compile Scala/Chisel sources"
	@echo "  run           - Generate RTL, C++ headers, runtime, and Scala config files"
	@echo "  rtl           - Alias for run"
	@echo "  sim           - Generate everything and build simulator"
	@echo "  difftest      - Generate everything and run difftests"
	@echo "  coremark      - Generate everything and run CoreMark"
	@echo "  sta           - Run STA using STA_TOOL"
	@echo "  clean         - Remove synthesis artifacts"
	@echo "  sim-clean     - Remove simulator build directory"
	@echo "  distclean     - Remove generated build artifacts"
	@echo ""
	@echo "Loaded configuration:"
	@echo "  TARGET_FAMILY = $(TARGET_FAMILY)"
	@echo "  TARGET_ARCH   = $(TARGET_ARCH)"
	@echo "  TOP_MODULE    = $(TOP_MODULE)"
	@echo "  GEN_DIR       = $(GEN_DIR)"
	@echo "  RTL_SOURCE    = $(RTL_SOURCE)"
	@echo "  RUNTIME_DIR   = $(RUNTIME_DIR)"
	@echo "  LINKER_SCRIPT = $(LINKER_SCRIPT)"
	@echo "  STARTUP_SOURCE= $(STARTUP_SOURCE)"
	@echo "  BUILD_TYPE    = $(BUILD_TYPE)"
	@echo "  GENERATOR     = $(GENERATOR)"
	@echo "  STA_TOOL      = $(STA_TOOL)"
	@echo "  SIM_BUILD_DIR = $(SIM_BUILD_DIR)"
