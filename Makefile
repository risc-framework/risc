BASE_DIR = $(shell pwd)
BUILD_DIR = $(BASE_DIR)/build
SCRIPTS_DIR = $(BASE_DIR)/scripts
SIM_DIR = $(BASE_DIR)/sims
SYNTH_DIR = $(BASE_DIR)/synth

LIB ?= arch 
FZF ?= $(shell [ -x "$$(command -v fzf)" ] && echo true || echo false)
STA_TOOL ?= yosys

.PHONY: pre fmt build run clean update sta sta-yosys sta-vivado

pre:
	@mkdir -p $(BUILD_DIR)
	@mkdir -p $(SIM_DIR)
	@mkdir -p $(SYNTH_DIR)

fmt:
	@scalafmt

build: pre 
	@sbt compile

run: pre
	@sbt "$(LIB)/run"

clean:
	@rm -rf $(SYNTH_DIR)

update:
	@sbt bloopInstall
	@sbt update
	@sbt reload

sta-yosys: pre
	@if [ "$(FZF)" = "true" ] ; then \
		FZF=true bash $(SCRIPTS_DIR)/sta_yosys.sh ; \
	else \
		bash $(SCRIPTS_DIR)/sta_yosys.sh ; \
	fi

sta-vivado: pre
	@if [ "$(FZF)" = "true" ] ; then \
		FZF=true bash $(SCRIPTS_DIR)/sta_vivado.sh ; \
	else \
		bash $(SCRIPTS_DIR)/sta_vivado.sh ; \
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
