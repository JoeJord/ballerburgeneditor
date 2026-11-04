#include <gem.h>

#include "BALLERED.h"
#include "menu.h"

/**
 * Resource object index of the Desk information item.
 */
enum
{
    /** Resource object index of the Desk information item. */
    MENU_OBJECT_INFO = 7,
    /** Resource object index of the File menu's quit item. */
    MENU_OBJECT_QUIT = 16
};

/** Loaded menu tree owned by the resource system. */
static OBJECT *menu_tree;
/** Whether the menu resource is currently loaded. */
static short resource_loaded;
/** Whether the resource menu is currently installed. */
static short menu_installed;

/**
 * Releases the menu resource if it has been loaded.
 */
static void release_menu_resource(void)
{
    if (resource_loaded)
    {
        rsrc_free();
        resource_loaded = 0;
        menu_tree = 0;
    }
}

/**
 * Loads the menu resource and installs its MAIN tree with AES.
 *
 * @return Non-zero if the menu was installed successfully.
 */
short menu_show(void)
{
    if (!rsrc_load("BALLERED.RSC"))
    {
        form_alert(1, "[1][Kann BALLERED.RSC nicht laden.][Beenden]");
        return 0;
    }
    resource_loaded = 1;

    if (!rsrc_gaddr(R_TREE, MAIN, &menu_tree))
    {
        form_alert(1, "[1][Menuebaum MAIN fehlt in BALLERED.RSC.][Beenden]");
        release_menu_resource();
        return 0;
    }

    menu_tree[MENU_OBJECT_INFO].ob_flags |= OF_SELECTABLE;
    menu_tree[MENU_OBJECT_QUIT].ob_flags |= OF_SELECTABLE;

    if (!menu_bar(menu_tree, 1))
    {
        form_alert(1, "[1][AES kann das GEM-Menue nicht installieren.][Beenden]");
        release_menu_resource();
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

    release_menu_resource();
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
        case MENU_OBJECT_INFO:
            action = MENU_ACTION_ABOUT;
            break;

        case MENU_OBJECT_QUIT:
            action = MENU_ACTION_QUIT;
            break;

        default:
            action = MENU_ACTION_NONE;
            break;
    }

    menu_tnormal(menu_tree, message[3], 1);
    return action;
}
