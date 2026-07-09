PROJECT_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
RISC_DIR ?= $(abspath $(PROJECT_DIR)/..)

SCALA_CONFIG_MK := $(RISC_DIR)/build/generated/config.mk
KCONFIG_MK := $(RISC_DIR)/build/generated/kconfig.mk

-include $(SCALA_CONFIG_MK)
-include $(KCONFIG_MK)

CMAKE_DIR := $(PROJECT_DIR)/cmake
BUILD_DIR ?= $(SIM_BUILD_DIR)
BUILD_DIR ?= $(RISC_DIR)/build/sim

COREMARK_DIR := $(PROJECT_DIR)/tests/coremark

BUILD_TYPE ?= Release
GENERATOR ?= Ninja

FAMILY ?= $(TARGET_FAMILY)
ARCH ?= $(TARGET_ARCH)

COREMARK_ENABLE_DEBUG ?= 0
COREMARK_TOTAL_DATA_SIZE ?=
COREMARK_ITERATIONS ?=
COREMARK_EXECS ?=

ifndef GENERATOR
  ifneq ($(wildcard $(BUILD_DIR)/build.ninja),)
    GENERATOR := Ninja
  else ifneq ($(wildcard $(BUILD_DIR)/Makefile),)
    GENERATOR := Unix Makefiles
  else
    GENERATOR := Ninja
  endif
endif

ifeq ($(GENERATOR),Ninja)
  BUILD_CMD := ninja -C $(BUILD_DIR)
  TEST_CMD := ninja -C $(BUILD_DIR) check-difftest
else ifeq ($(GENERATOR),Unix Makefiles)
  BUILD_CMD := cmake --build $(BUILD_DIR)
  TEST_CMD := cmake --build $(BUILD_DIR) --target check-difftest
else
  $(error Unsupported GENERATOR: $(GENERATOR). Supported generators: Ninja, Unix Makefiles)
endif

.PHONY: all config build reconfigure clean difftest coremark check-generated check-targets help

all: build

check-generated:
	@test -f "$(SCALA_CONFIG_MK)" || (echo "missing $(SCALA_CONFIG_MK). Run make run from repo root."; exit 1)
	@test -f "$(KCONFIG_MK)" || (echo "missing $(KCONFIG_MK). Run make kconfig from repo root."; exit 1)
	@test -n "$(TARGET_FAMILY)" || (echo "TARGET_FAMILY is empty. Run make run from repo root."; exit 1)
	@test -n "$(TARGET_ARCH)" || (echo "TARGET_ARCH is empty. Run make run from repo root."; exit 1)
	@test -n "$(TOP_MODULE)" || (echo "TOP_MODULE is empty. Run make run from repo root."; exit 1)
	@test -f "$(RTL_SOURCE)" || (echo "missing RTL source: $(RTL_SOURCE). Run make run from repo root."; exit 1)
	@test -f "$(LINKER_SCRIPT)" || (echo "missing linker script: $(LINKER_SCRIPT). Run make run from repo root."; exit 1)
	@test -f "$(STARTUP_SOURCE)" || (echo "missing startup source: $(STARTUP_SOURCE). Run make run from repo root."; exit 1)

config: check-generated
	@echo "==> Configuring simulator project..."
	@mkdir -p $(BUILD_DIR)

build: config
	@if [ ! -f $(BUILD_DIR)/CMakeCache.txt ]; then \
		echo "Running CMake with generator: $(GENERATOR)"; \
		cd $(BUILD_DIR) && cmake $(PROJECT_DIR) \
			-DCMAKE_BUILD_TYPE="$(BUILD_TYPE)" \
			-G "$(GENERATOR)"; \
	fi
	@echo "==> Building simulator with $(GENERATOR)..."
	@$(BUILD_CMD)

reconfigure: check-generated
	@echo "==> Forcing simulator reconfiguration..."
	@rm -f $(BUILD_DIR)/CMakeCache.txt
	@$(MAKE) build

clean:
	@echo "==> Cleaning simulator build directory..."
	@rm -rf $(BUILD_DIR)

check-targets: build
	@if [ "$(GENERATOR)" = "Ninja" ]; then \
		ninja -C $(BUILD_DIR) -t targets | grep -q '^check-difftest:' || \
			(echo "CMake did not generate target check-difftest."; \
			 echo "Check ENABLE_TESTING and ENABLE_DIFF in build/generated/kconfig.cmake."; \
			 exit 1); \
	fi

difftest: check-targets
	@echo "==> Running difftests..."
	@$(TEST_CMD)

coremark: check-generated
	@echo "==> Running CoreMark..."
	@mkdir -p $(BUILD_DIR)
	@mkdir -p $(COREMARK_DIR)
	@cd $(COREMARK_DIR) && make \
		PORT_DIR=$(FAMILY) \
		OPATH=$(BUILD_DIR)/tests/coremark/ \
		RUNTIME_DIR=$(RUNTIME_DIR) \
		ITERATIONS=$(COREMARK_ITERATIONS) \
		EXECS=$(COREMARK_EXECS) \
		TOTAL_DATA_SIZE=$(COREMARK_TOTAL_DATA_SIZE) \
		ENABLE_DEBUG=$(COREMARK_ENABLE_DEBUG)

help:
	@echo "Available targets:"
	@echo "  all          - Configure and build simulator"
	@echo "  config       - Validate generated config and prepare CMake build dir"
	@echo "  build        - Configure and build simulator"
	@echo "  reconfigure  - Force CMake reconfiguration"
	@echo "  clean        - Remove simulator build directory"
	@echo "  difftest     - Run difftests"
	@echo "  coremark     - Run CoreMark"
	@echo "  help         - Show this help message"
	@echo ""
	@echo "Loaded configuration:"
	@echo "  TARGET_FAMILY = $(TARGET_FAMILY)"
	@echo "  TARGET_ARCH   = $(TARGET_ARCH)"
	@echo "  TOP_MODULE    = $(TOP_MODULE)"
	@echo "  RTL_SOURCE    = $(RTL_SOURCE)"
	@echo "  LINKER_SCRIPT = $(LINKER_SCRIPT)"
	@echo "  STARTUP_SOURCE= $(STARTUP_SOURCE)"
	@echo "  BUILD_DIR     = $(BUILD_DIR)"
	@echo "  BUILD_TYPE    = $(BUILD_TYPE)"
	@echo "  GENERATOR     = $(GENERATOR)"
