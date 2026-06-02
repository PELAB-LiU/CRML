## just a sample Makefile with graddle commands, will be improved

detected_OS ?= $(shell uname -s)

GRADLE_CMD=gradlew.bat

ifeq ($(detected_OS),Darwin)
	GRADLE_CMD := ./gradlew
else ifeq (MINGW,$(findstring MINGW,$(detected_OS)))
	GRADLE_CMD := ./gradlew
else
	GRADLE_CMD := ./gradlew
endif

all: build

test: tests

tests:
	$(GRADLE_CMD) test

##########################
# Langauge related tests #
##########################

test-language:
	$(GRADLE_CMD) :language:test

test-language-specification:
	$(GRADLE_CMD) :language:test --tests "crml.language.specification.*"

test-language-hints:
	$(GRADLE_CMD) :language:test --tests "crml.language.hints.*"

###############
# Experiments #
###############
exp:
	$(GRADLE_CMD) experiments:test --tests "crml.experiments.*"

exp-tl:
	$(GRADLE_CMD) experiments:test --tests "crml.experiments.TrafficLight"

exp-sri:
	$(GRADLE_CMD) experiments:test --tests "crml.experiments.SRIRef"

exp-sri2:
	$(GRADLE_CMD) experiments:test --tests "crml.experiments.SRI2Ref"

exp-ps:
	$(GRADLE_CMD) experiments:test --tests "crml.experiments.Pumps"

classify-llm:
	$(GRADLE_CMD) :experiments:classifyLLMFiles


#########
# Other #
#########
test-etl:
	$(GRADLE_CMD) test --tests "ctests.ETLTests*"

test-forml:
	$(GRADLE_CMD) test --tests "ctests.FORMLTests*"

build:
	$(GRADLE_CMD) build

cleanall: clean
clean:
	rm -rf build bin generated

# beware, this will remove *everything* that is was not added in the git repo
gitclean:
	git clean -fdx

distribution: all
	$(GRADLE_CMD) shadowJar
	