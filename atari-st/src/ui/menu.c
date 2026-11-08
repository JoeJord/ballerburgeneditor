#include <gem.h>

#include "BALLERED.h"
#include "app_resources.h"
#include "app_alerts.h"
#include "app_logger.h"
#include "menu.h"

enum
{
    MENU_KEYBOARD_CTRL_MASK = 0x0004, /**< Control modifier bit from evnt_multi(). */
    MENU_KEYBOARD_ALT_MASK = 0x0008, /**< Alt modifier bit from evnt_multi(). */
    MENU_SCAN_Q = 0x10, /**< Atari ST scan code for Q. */
    MENU_SCAN_W = 0x11, /**< Atari ST scan code for W. */
    MENU_SCAN_O = 0x18, /**< Atari ST scan code for O. */
    MENU_SCAN_S = 0x1f, /**< Atari ST scan code for S. */
    MENU_SCAN_N = 0x31 /**< Atari ST scan code for N. */
};

/** Loaded menu tree owned by the resource system. */
static OBJECT *menu_tree;
/** Whether the resource menu is currently installed. */
static short menu_installed;
/** Current enabled state of the File > Close menu item. */
static short close_enabled;
/** Current shared enabled state of File > New and File > Open. */
static short document_creation_enabled;
/** Current enabled state of the Previous Castle menu item. */
static short previous_castle_enabled;
/** Current enabled state of the Next Castle menu item. */
static short next_castle_enabled;
/** Current enabled state of the Delete Castle menu item. */
static short delete_castle_enabled;
/** Current enabled state of the Add Castle menu item. */
static short add_castle_enabled;

/**
 * Loads the menu resource and installs its MAIN tree with AES.
 *
 * @return Non-zero if the menu was installed successfully.
 */
short menu_show(void)
{
    if (!app_resources_load())
    {
        app_alerts_show(APP_ALERT_RESOURCES_LOAD_FAILED);
        return 0;
    }

    menu_tree = app_resources_get_main_menu();
    if (!menu_tree)
    {
        app_alerts_show(APP_ALERT_MAIN_MENU_MISSING);
        app_resources_release();
        return 0;
    }

    menu_tree[MENU_ABOUT].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_FILE_NEW].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_FILE_OPEN].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_FILE_CLOSE].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_QUIT].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_PREV_CASTLE].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_NEXT_CASTLE].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_CASTLE_DEL].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_CASTLE_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_THRONE_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_MONEY_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_POWDER_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_BALLS_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_CANNON_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_VANE_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_WALL_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_ROOF_ADD].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_EDIT_DEL].ob_flags |= OF_SELECTABLE;

    previous_castle_enabled = -1;
    next_castle_enabled = -1;
    delete_castle_enabled = -1;
    add_castle_enabled = -1;
    if (!menu_set_castle_navigation_enabled(0, 0))
    {
        app_logger_write("Unable to disable castle navigation menu items.");
        app_resources_release();
        menu_tree = 0;
        return 0;
    }

    if (!menu_ienable(menu_tree, MENU_FILE_CLOSE, 0))
    {
        app_alerts_show(APP_ALERT_CLOSE_MENU_DISABLE_FAILED);
        app_resources_release();
        menu_tree = 0;
        return 0;
    }
    close_enabled = 0;
    if (!menu_ienable(menu_tree, MENU_FILE_NEW, 1)
        || !menu_ienable(menu_tree, MENU_FILE_OPEN, 1))
    {
        app_alerts_show(APP_ALERT_DOCUMENT_MENU_UPDATE_FAILED);
        app_resources_release();
        menu_tree = 0;
        return 0;
    }
    document_creation_enabled = 1;

    if (!menu_bar(menu_tree, 1))
    {
        app_alerts_show(APP_ALERT_GEM_MENU_INSTALL_FAILED);
        app_resources_release();
        menu_tree = 0;
        return 0;
    }

    menu_installed = 1;
    return 1;
}

/**
 * Removes the menu bar and releases its loaded resource.
 */
void menu_hide(void)
{
    if (menu_installed)
    {
        menu_bar(menu_tree, 0);
        menu_installed = 0;
    }

    app_resources_release();
    menu_tree = 0;
    close_enabled = 0;
    document_creation_enabled = 0;
    previous_castle_enabled = 0;
    next_castle_enabled = 0;
    delete_castle_enabled = 0;
    add_castle_enabled = 0;
}

/**
 * Enables or disables the File > Close menu item.
 *
 * @param enabled Non-zero to enable the item.
 * @return Non-zero if AES updated the menu item successfully.
 */
short menu_set_close_enabled(short enabled)
{
    short new_state;

    new_state = enabled != 0;
    if (new_state == close_enabled)
    {
        return 1;
    }

    if (!menu_ienable(menu_tree, MENU_FILE_CLOSE, new_state))
    {
        return 0;
    }

    close_enabled = new_state;
    app_logger_write(
        new_state
            ? "File > Close menu item enabled."
            : "File > Close menu item disabled."
    );
    return 1;
}

/**
 * Enables or disables the File > New and File > Open menu items together.
 *
 * @param enabled Non-zero to enable both items.
 * @return Non-zero if AES updated both menu items successfully.
 */
short menu_set_document_creation_enabled(short enabled)
{
    short new_state;

    new_state = enabled != 0;
    if (new_state == document_creation_enabled)
    {
        return 1;
    }

    if (!menu_ienable(menu_tree, MENU_FILE_NEW, new_state)
        || !menu_ienable(menu_tree, MENU_FILE_OPEN, new_state))
    {
        return 0;
    }

    document_creation_enabled = new_state;
    app_logger_write(
        new_state
            ? "File > New and File > Open menu items enabled."
            : "File > New and File > Open menu items disabled."
    );
    return 1;
}

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
)
{
    short new_previous_state;
    short new_next_state;

    new_previous_state = previous_enabled != 0;
    new_next_state = next_enabled != 0;

    if (new_previous_state != previous_castle_enabled)
    {
        if (!menu_ienable(menu_tree, MENU_PREV_CASTLE, new_previous_state))
        {
            return 0;
        }
        previous_castle_enabled = new_previous_state;
        app_logger_write(
            new_previous_state
                ? "Previous Castle menu item enabled."
                : "Previous Castle menu item disabled."
        );
    }

    if (new_next_state != next_castle_enabled)
    {
        if (!menu_ienable(menu_tree, MENU_NEXT_CASTLE, new_next_state))
        {
            return 0;
        }
        next_castle_enabled = new_next_state;
        app_logger_write(
            new_next_state
                ? "Next Castle menu item enabled."
                : "Next Castle menu item disabled."
        );
    }

    return 1;
}

/**
 * Enables or disables the Delete Castle menu item.
 *
 * @param enabled Non-zero to enable the item.
 * @return Non-zero if AES updated the menu item successfully.
 */
short menu_set_delete_castle_enabled(short enabled)
{
    short new_state;

    new_state = enabled != 0;
    if (new_state == delete_castle_enabled)
    {
        return 1;
    }

    if (!menu_ienable(menu_tree, MENU_CASTLE_DEL, new_state))
    {
        return 0;
    }

    delete_castle_enabled = new_state;
    app_logger_write(
        new_state
            ? "Delete Castle menu item enabled."
            : "Delete Castle menu item disabled."
    );
    return 1;
}

/**
 * Enables or disables the Add Castle menu item.
 *
 * @param enabled Non-zero to enable the item.
 * @return Non-zero if AES updated the menu item successfully.
 */
short menu_set_add_castle_enabled(short enabled)
{
    short new_state;

    new_state = enabled != 0;
    if (new_state == add_castle_enabled)
    {
        return 1;
    }

    if (!menu_ienable(menu_tree, MENU_CASTLE_ADD, new_state))
    {
        return 0;
    }

    add_castle_enabled = new_state;
    app_logger_write(
        new_state
            ? "Add Castle menu item enabled."
            : "Add Castle menu item disabled."
    );
    return 1;
}

/**
 * Handles a message from AES and normalizes selected menu titles.
 *
 * @param message Eight-word AES message buffer.
 * @return The menu action selected by the user, or MENU_ACTION_NONE.
 */
MenuAction menu_handle_message(const short message[8])
{
    /** Application action resulting from the selected resource object. */
    MenuAction action;

    if (message[0] != MN_SELECTED)
    {
        return MENU_ACTION_NONE;
    }

    switch (message[4])
    {
        case MENU_FILE_NEW:
            action = MENU_ACTION_NEW;
            break;

        case MENU_FILE_OPEN:
            action = MENU_ACTION_OPEN;
            break;

        case MENU_FILE_CLOSE:
            action = MENU_ACTION_CLOSE;
            break;

        case MENU_FILE_SAVE:
            action = MENU_ACTION_SAVE;
            break;

        case MENU_ABOUT:
            action = MENU_ACTION_ABOUT;
            break;

        case MENU_QUIT:
            action = MENU_ACTION_QUIT;
            break;

        case MENU_PREV_CASTLE:
            action = MENU_ACTION_PREVIOUS_CASTLE;
            break;

        case MENU_NEXT_CASTLE:
            action = MENU_ACTION_NEXT_CASTLE;
            break;

        case MENU_CASTLE_DEL:
            action = MENU_ACTION_DELETE_CASTLE;
            break;

        case MENU_CASTLE_ADD:
            action = MENU_ACTION_ADD_CASTLE;
            break;

        case MENU_THRONE_ADD:
            action = MENU_ACTION_ADD_THRONE;
            break;

        case MENU_MONEY_ADD:
            action = MENU_ACTION_ADD_MONEY_STORAGE;
            break;

        case MENU_POWDER_ADD:
            action = MENU_ACTION_ADD_POWDER_STORAGE;
            break;

        case MENU_BALLS_ADD:
            action = MENU_ACTION_ADD_BALL_STORAGE;
            break;

        case MENU_CANNON_ADD:
            action = MENU_ACTION_ADD_CANNON;
            break;

        case MENU_VANE_ADD:
            action = MENU_ACTION_ADD_WIND_VANE;
            break;

        case MENU_WALL_ADD:
            action = MENU_ACTION_ADD_WALL;
            break;

        case MENU_ROOF_ADD:
            action = MENU_ACTION_ADD_ROOF;
            break;

        case MENU_EDIT_DEL:
            action = MENU_ACTION_DELETE_ELEMENT;
            break;

        default:
            action = MENU_ACTION_NONE;
            break;
    }

    menu_tnormal(menu_tree, message[3], 1);
    return action;
}

/**
 * Translates a Ctrl-key shortcut into its enabled File-menu action.
 *
 * @param key GEM keyboard code returned by evnt_multi().
 * @param key_state GEM modifier state returned by evnt_multi().
 * @return The matching action, or MENU_ACTION_NONE if no active shortcut matches.
 */
MenuAction menu_handle_key(short key, short key_state)
{
    short scan_code;

    if (!(key_state & MENU_KEYBOARD_CTRL_MASK)
        || (key_state & MENU_KEYBOARD_ALT_MASK))
    {
        return MENU_ACTION_NONE;
    }

    scan_code = (short)(((unsigned short)key >> 8) & 0x00ff);
    switch (scan_code)
    {
        case MENU_SCAN_N:
            return document_creation_enabled
                ? MENU_ACTION_NEW
                : MENU_ACTION_NONE;

        case MENU_SCAN_O:
            return document_creation_enabled
                ? MENU_ACTION_OPEN
                : MENU_ACTION_NONE;

        case MENU_SCAN_W:
            return close_enabled ? MENU_ACTION_CLOSE : MENU_ACTION_NONE;

        case MENU_SCAN_S:
            return MENU_ACTION_SAVE;

        case MENU_SCAN_Q:
            return MENU_ACTION_QUIT;

        default:
            return MENU_ACTION_NONE;
    }
}
