#ifndef BALLERBURGENEDITOR_ATARI_ST_MENU_H
#define BALLERBURGENEDITOR_ATARI_ST_MENU_H

#include <gem.h>

/**
 * Actions reported by the GEM menu and toolbar event handlers.
 */
typedef enum
{
    /** No actionable menu selection was present in the AES message. */
    MENU_ACTION_NONE,
    /** The user selected the New document item. */
    MENU_ACTION_NEW,
    /** The user selected the Open document item. */
    MENU_ACTION_OPEN,
    /** The user selected the Close document item. */
    MENU_ACTION_CLOSE,
    /** The user requested saving the active document. */
    MENU_ACTION_SAVE,
    /** The user selected the application information item. */
    MENU_ACTION_ABOUT,
    /** The user selected the quit item. */
    MENU_ACTION_QUIT,
    /** The user requested the previous castle in the active document. */
    MENU_ACTION_PREVIOUS_CASTLE,
    /** The user requested the next castle in the active document. */
    MENU_ACTION_NEXT_CASTLE,
    /** The user requested removing the current castle. */
    MENU_ACTION_DELETE_CASTLE,
    /** The user requested adding a castle. */
    MENU_ACTION_ADD_CASTLE,
    /** The user requested adding the throne. */
    MENU_ACTION_ADD_THRONE,
    /** The user requested adding the money storage area. */
    MENU_ACTION_ADD_MONEY_STORAGE,
    /** The user requested adding the powder storage area. */
    MENU_ACTION_ADD_POWDER_STORAGE,
    /** The user requested adding the cannonball storage area. */
    MENU_ACTION_ADD_BALL_STORAGE,
    /** The user requested adding a cannon. */
    MENU_ACTION_ADD_CANNON,
    /** The user requested adding the wind vane. */
    MENU_ACTION_ADD_WIND_VANE,
    /** The user requested creating a wall polygon. */
    MENU_ACTION_ADD_WALL,
    /** The user requested creating a roof polygon. */
    MENU_ACTION_ADD_ROOF,
    /** The user requested deleting the selected editor element. */
    MENU_ACTION_DELETE_ELEMENT
} MenuAction;

/**
 * Loads and displays the application's resource-defined GEM menu bar.
 *
 * @return Non-zero if the resource and menu bar were loaded successfully.
 */
short menu_show(void);

/**
 * Hides the application's GEM menu bar.
 */
void menu_hide(void);

/**
 * Enables or disables the File > Close menu item.
 *
 * @param enabled Non-zero to enable the item.
 * @return Non-zero if AES updated the menu item successfully.
 */
short menu_set_close_enabled(short enabled);

/**
 * Enables or disables the File > New and File > Open menu items together.
 *
 * @param enabled Non-zero to enable both items.
 * @return Non-zero if AES updated both menu items successfully.
 */
short menu_set_document_creation_enabled(short enabled);

/**
 * Enables or disables the Previous Castle and Next Castle menu items.
 *
 * @param previous_enabled Non-zero to enable Previous Castle.
 * @param next_enabled Non-zero to enable Next Castle.
 * @return Non-zero if AES updated the menu items successfully.
 */
short menu_set_castle_navigation_enabled(
    short previous_enabled,
    short next_enabled
);

/**
 * Enables or disables the Delete Castle menu item.
 *
 * @param enabled Non-zero to enable the item.
 * @return Non-zero if AES updated the menu item successfully.
 */
short menu_set_delete_castle_enabled(short enabled);

/**
 * Handles a message from the application's AES event loop.
 *
 * @param message Eight-word AES message buffer.
 * @return The selected menu action, or MENU_ACTION_NONE for other messages.
 */
MenuAction menu_handle_message(const short message[8]);

/**
 * Translates a Ctrl-key shortcut into its enabled File-menu action.
 *
 * @param key GEM keyboard code returned by evnt_multi().
 * @param key_state GEM modifier state returned by evnt_multi().
 * @return The matching action, or MENU_ACTION_NONE if no active shortcut matches.
 */
MenuAction menu_handle_key(short key, short key_state);

#endif
