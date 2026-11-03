#ifndef BALLERBURGENEDITOR_ATARI_ST_MENU_H
#define BALLERBURGENEDITOR_ATARI_ST_MENU_H

#include <gem.h>

/**
 * Actions reported by the GEM menu event handler.
 */
typedef enum
{
    /** No actionable menu selection was present in the AES message. */
    MENU_ACTION_NONE,
    /** The user selected the application information item. */
    MENU_ACTION_ABOUT,
    /** The user selected the quit item. */
    MENU_ACTION_QUIT
} MenuAction;

/**
 * Builds and displays the application's GEM menu bar.
 */
void menu_show(void);

/**
 * Hides the application's GEM menu bar.
 */
void menu_hide(void);

/**
 * Handles a message from the application's AES event loop.
 *
 * @param message Eight-word AES message buffer.
 * @return The selected menu action, or MENU_ACTION_NONE for other messages.
 */
MenuAction menu_handle_message(const short message[8]);

#endif
