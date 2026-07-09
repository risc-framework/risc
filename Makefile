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

.PHONY: all
.PHONY: pre defconfig olddefconfig menuconfig kconfig
.PHONY: fmt build rtl
.PHONY: sim-config sim difftest coremark
.PHONY: generated-check synth-clean sim-clean clean update
.PHONY: sta sta-yosys sta-vivado help
.PHONY: __sim_config __sim __difftest __coremark

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

synth-clean:
	@rm -rf $(SYNTH_DIR)

sim-clean:
	@rm -rf $(SIM_BUILD_DIR)

clean: synth-clean sim-clean
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
	@echo "  rtl           - Generate RTL, C++ headers, runtime, and Scala config files"
	@echo "  sim-config    - Configure simulator CMake build"
	@echo "  sim           - Build simulator from existing generated RTL"
	@echo "  difftest      - Run difftests from existing generated RTL"
	@echo "  coremark      - Run CoreMark from existing generated runtime"
	@echo "  sta           - Run STA using STA_TOOL"
	@echo "  synth-clean   - Remove synthesis artifacts"
	@echo "  sim-clean     - Remove simulator build directory"
	@echo "  clean         - Remove generated build artifacts"
	@echo ""
	@echo "Typical flow:"
	@echo "  make rtl"
	@echo "  make sim"
	@echo "  make difftest"
	@echo "  make coremark"
	@echo ""
	@echo "Loaded configuration:"
	@echo "  TARGET_FAMILY            = $(TARGET_FAMILY)"
	@echo "  TARGET_ARCH              = $(TARGET_ARCH)"
	@echo "  FAMILY                   = $(FAMILY)"
	@echo "  ARCH                     = $(ARCH)"
	@echo "  TOP_MODULE               = $(TOP_MODULE)"
	@echo "  GEN_DIR                  = $(GEN_DIR)"
	@echo "  RTL_SOURCE               = $(RTL_SOURCE)"
	@echo "  RUNTIME_DIR              = $(RUNTIME_DIR)"
	@echo "  LINKER_SCRIPT            = $(LINKER_SCRIPT)"
	@echo "  STARTUP_SOURCE           = $(STARTUP_SOURCE)"
	@echo "  BUILD_TYPE               = $(BUILD_TYPE)"
	@echo "  GENERATOR                = $(GENERATOR)"
	@echo "  STA_TOOL                 = $(STA_TOOL)"
	@echo "  SIM_BUILD_DIR            = $(SIM_BUILD_DIR)"
	@echo "  COREMARK_ITERATIONS      = $(COREMARK_ITERATIONS)"
	@echo "  COREMARK_EXECS           = $(COREMARK_EXECS)"
	@echo "  COREMARK_TOTAL_DATA_SIZE = $(COREMARK_TOTAL_DATA_SIZE)"
