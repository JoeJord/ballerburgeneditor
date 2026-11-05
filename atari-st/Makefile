CC = m68k-atari-mintelf-gcc
CFLAGS = -O2 -g -Wall -Wextra -std=c99
LDFLAGS =
LDLIBS = -lgem
TARGET = ../out/artifacts/ballered.prg
CFLAGS += -Isrc -Isrc/io -Isrc/ui -Isrc/util
SOURCES = src/main.c src/io/file_open.c src/ui/menu.c src/ui/about_dialog.c src/ui/app_resources.c src/ui/document_window.c src/ui/app_alerts.c src/ui/toolbar.c src/util/app_logger.c
HEADERS = src/BALLERED.h src/io/file_open.h src/ui/menu.h src/ui/about_dialog.h src/ui/app_resources.h src/ui/document_window.h src/ui/app_alerts.h src/ui/toolbar.h src/util/app_logger.h
RESOURCE = src/BALLERED.RSC
RESOURCE_TARGET = $(dir $(TARGET))BALLERED.RSC

.PHONY: all clean

all: $(TARGET) $(RESOURCE_TARGET)

$(TARGET): $(SOURCES) $(HEADERS)
	mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(SOURCES) $(LDLIBS)

$(RESOURCE_TARGET): $(RESOURCE)
	mkdir -p $(dir $@)
	cp $(RESOURCE) $(RESOURCE_TARGET)

clean:
	rm -f $(TARGET) $(RESOURCE_TARGET)
