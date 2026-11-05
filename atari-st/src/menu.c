#include <gem.h>

#include "BALLERED.h"
#include "app_resources.h"
#include "menu.h"

/** Loaded menu tree owned by the resource system. */
static OBJECT *menu_tree;
/** Whether the resource menu is currently installed. */
static short menu_installed;
/** Current enabled state of the File > Close menu item. */
static short close_enabled;

/**
 * Loads the menu resource and installs its MAIN tree with AES.
 *
 * @return Non-zero if the menu was installed successfully.
 */
short menu_show(void)
{
    if (!app_resources_load())
    {
        form_alert(1, "[1][Unable to load application resources.][Quit]");
        return 0;
    }

    menu_tree = app_resources_get_main_menu();
    if (!menu_tree)
    {
        form_alert(1, "[1][Main menu tree is missing from the resources.][Quit]");
        app_resources_release();
        return 0;
    }

    menu_tree[MENU_ABOUT].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_FILE_NEW].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_FILE_CLOSE].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_QUIT].ob_flags |= OF_SELECTABLE;

    if (!menu_ienable(menu_tree, MENU_FILE_CLOSE, 0))
    {
        form_alert(1, "[1][AES could not disable the Close menu item.][Quit]");
        app_resources_release();
        menu_tree = 0;
        return 0;
    }
    close_enabled = 0;

    if (!menu_bar(menu_tree, 1))
    {
        form_alert(1, "[1][AES could not install the GEM menu.][Quit]");
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

        case MENU_FILE_CLOSE:
            action = MENU_ACTION_CLOSE;
            break;

        case MENU_ABOUT:
            action = MENU_ACTION_ABOUT;
            break;

        case MENU_QUIT:
            action = MENU_ACTION_QUIT;
            break;

        default:
            action = MENU_ACTION_NONE;
            break;
    }

    menu_tnormal(menu_tree, message[3], 1);
    return action;
}
