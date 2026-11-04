CC = m68k-atari-mintelf-gcc
CFLAGS = -O2 -g -Wall -Wextra -std=c99
LDFLAGS =
LDLIBS = -lgem
TARGET = ../out/artifacts/ballered.prg
SOURCES = src/main.c src/menu.c src/about_dialog.c
HEADERS = src/menu.h src/about_dialog.h src/BALLERED.h
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
