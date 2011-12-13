INPUT_FILES := $(wildcard *.java)
OUTPUT_FILES := $(patsubst %.java, %.class, $(INPUT_FILES))
CLASSPATH := '.:lib/*'

.PHONY: all

all: $(OUTPUT_FILES)

%.class: %.java
	javac -cp $(CLASSPATH) $<
