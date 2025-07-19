SRC_DIR := src
RES_DIR := res
BUILD_DIR := bin
CLASSES_DIR := classes

MF := $(BUILD_DIR)/MANIFEST.MF
LIBS := $(JAVA_HOME)/jre/lib/rt.jar
SRCS := $(shell find $(SRC_DIR) -type f -name \*.java)
OBJS := $(SRCS:$(SRC_DIR)/%.java=$(CLASSES_DIR)/%.class)

JC := javac
JAR := jar
PG := proguard

JFLAGS := -g -cp $(LIBS) -sourcepath $(SRC_DIR) -d $(CLASSES_DIR)
PGFLAGS := -dontnote -dontwarn -libraryjars $(LIBS) -dontshrink -dontoptimize -keep 'public class com.bhmsware.jzip.JZip { public *; }'

.PHONY: all distribute clean

all: distribute

distribute: $(BUILD_DIR)/JZip.jar
	@echo $< is now ready!

clean:
	rm -rf $(CLASSES_DIR)/*

$(CLASSES_DIR)/%.class: $(SRC_DIR)/%.java
	mkdir -p $(CLASSES_DIR)
	$(JC) $(JFLAGS) $^

$(BUILD_DIR)/JZip_tmp.jar: $(OBJS)
	$(JAR) -cvfm $@ $(MF)
	$(JAR) -uvf $@ -C $(CLASSES_DIR) .
	$(JAR) -uvf $@ -C $(RES_DIR) .

$(BUILD_DIR)/JZip.jar: $(BUILD_DIR)/JZip_tmp.jar
	$(PG) $(PGFLAGS) -injars $< -outjar $@
