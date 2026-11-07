SRC_DIR := src
OUT_DIR := out
CLASSES_DIR := $(OUT_DIR)/classes
JAR := stegobmp.jar
MAIN_CLASS := stegobmp.Main
LAUNCHER := stegobmp
SOURCES := $(shell find $(SRC_DIR) -name '*.java')

.PHONY: all clean

all: $(JAR)
	chmod +x $(LAUNCHER)

$(JAR): $(SOURCES)
	rm -rf $(CLASSES_DIR)
	mkdir -p $(CLASSES_DIR)
	javac --release 21 -encoding UTF-8 -Xlint:all,-serial -d $(CLASSES_DIR) $(SOURCES)
	jar --create --file $(JAR) --main-class $(MAIN_CLASS) -C $(CLASSES_DIR) .

clean:
	rm -rf $(OUT_DIR) $(JAR)
