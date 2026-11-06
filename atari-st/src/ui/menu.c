#include <gem.h>

#include "BALLERED.h"
#include "app_resources.h"
#include "app_alerts.h"
#include "app_logger.h"
#include "menu.h"

/** Loaded menu tree owned by the resource system. */
static OBJECT *menu_tree;
/** Whether the resource menu is currently installed. */
static short menu_installed;
/** Current enabled state of the File > Close menu item. */
static short close_enabled;
/** Current shared enabled state of File > New and File > Open. */
static short document_creation_enabled;

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
