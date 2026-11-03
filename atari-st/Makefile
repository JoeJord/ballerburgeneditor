CC = m68k-atari-mintelf-gcc
CFLAGS = -O2 -g -Wall -Wextra -std=c99
LDFLAGS =
LDLIBS = -lgem
TARGET = ../out/artifacts/ballered.prg
SOURCES = src/main.c src/menu.c src/about_dialog.c

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SOURCES)
	mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(SOURCES) $(LDLIBS)

clean:
	rm -f $(TARGET)
