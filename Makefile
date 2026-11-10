SRC_DIR := src
OUT_DIR := out
CLASSES_DIR := $(OUT_DIR)/classes
JAR := stegobmp.jar
MAIN_CLASS := stegobmp.Main
LAUNCHER := stegobmp
SOURCES := $(shell find $(SRC_DIR) -name '*.java')
TEST_DIR := tests
TEST_CLASSES_DIR := $(OUT_DIR)/test-classes
TEST_SOURCES = $(shell find $(TEST_DIR) -name '*.java')
TEST_MAIN_CLASSES := stegobmp.steg.LsbiCapacityTest stegobmp.steg.LsbiExtractTest \
                     stegobmp.steg.LsbiEmbedTest stegobmp.steg.LsbiInvariantTest

.PHONY: all clean test

all: $(JAR)
	chmod +x $(LAUNCHER)

$(JAR): $(SOURCES)
	rm -rf $(CLASSES_DIR)
	mkdir -p $(CLASSES_DIR)
	javac --release 21 -encoding UTF-8 -Xlint:all,-serial -d $(CLASSES_DIR) $(SOURCES)
	jar --create --file $(JAR) --main-class $(MAIN_CLASS) -C $(CLASSES_DIR) .

test: $(JAR)
	mkdir -p $(TEST_CLASSES_DIR)
	javac --release 21 -encoding UTF-8 -Xlint:all,-serial -cp $(CLASSES_DIR) -d $(TEST_CLASSES_DIR) $(TEST_SOURCES)
	for test_class in $(TEST_MAIN_CLASSES); do \
		java -cp $(CLASSES_DIR):$(TEST_CLASSES_DIR) $$test_class || exit $$?; \
	done

clean:
	rm -rf $(OUT_DIR) $(JAR)
