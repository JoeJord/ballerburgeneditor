#include <gem.h>

#include "menu.h"

/**
 * Object index of the root of the menu tree.
 */
enum
{
    /** Root object containing the menu bar and dropdown area. */
    MENU_OBJECT_ROOT = 0,
    /** Menu bar background object. */
    MENU_OBJECT_BAR,
    /** Container for the menu title objects. */
    MENU_OBJECT_ACTIVE,
    /** Desk menu title object. */
    MENU_OBJECT_DESK,
    /** File menu title object. */
    MENU_OBJECT_FILE,
    /** Container for the dropdown menu objects. */
    MENU_OBJECT_DROPDOWNS,
    /** Desk dropdown background object. */
    MENU_OBJECT_DESK_BOX,
    /** Desk information item object. */
    MENU_OBJECT_INFO,
    /** File dropdown background object. */
    MENU_OBJECT_FILE_BOX,
    /** File quit item object. */
    MENU_OBJECT_QUIT,
    /** Number of objects in the menu tree. */
    MENU_OBJECT_COUNT
};

/** GEM object tree owned and managed by this module. */
static OBJECT menu_tree[MENU_OBJECT_COUNT];
/** Label shown for the Desk menu. */
static char desk_title[] = " Desk ";
/** Label shown for the File menu. */
static char file_title[] = " Datei ";
/** Label shown for the information menu item. */
static char info_text[] = "  Ballerburgen Editor Info";
/** Label shown for the quit menu item. */
static char quit_text[] = "  Beenden        ";

/**
 * Initializes one GEM object in the menu tree.
 *
 * @param id Index of the object to initialize.
 * @param next Next sibling or parent index.
 * @param head First child index, or -1 if the object has no children.
 * @param tail Last child index, or -1 if the object has no children.
 * @param type GEM object type.
 * @param flags GEM object flags.
 * @param state GEM object state.
 * @param spec Object-specific GEM data.
 * @param x Horizontal position in pixels.
 * @param y Vertical position in pixels.
 * @param width Object width in pixels.
 * @param height Object height in pixels.
 */
static void set_menu_object(
    int id,
    int next,
    int head,
    int tail,
    int type,
    int flags,
    int state,
    OBSPEC spec,
    int x,
    int y,
    int width,
    int height)
{
    menu_tree[id].ob_next = (short)next;
    menu_tree[id].ob_head = (short)head;
    menu_tree[id].ob_tail = (short)tail;
    menu_tree[id].ob_type = (unsigned short)type;
    menu_tree[id].ob_flags = (unsigned short)flags;
    menu_tree[id].ob_state = (unsigned short)state;
    menu_tree[id].ob_spec = spec;
    menu_tree[id].ob_x = (short)x;
    menu_tree[id].ob_y = (short)y;
    menu_tree[id].ob_width = (short)width;
    menu_tree[id].ob_height = (short)height;
}

/**
 * Creates the source-defined Desk and File menus.
 */
static void create_menu(void)
{
    /** Width of one system character cell. */
    short character_width;
    /** Height of one system character cell. */
    short character_height;
    /** Width of a system box character. */
    short box_width;
    /** Height of a system box character. */
    short box_height;
    /** Screen origin x-coordinate returned by wind_get(). */
    short screen_x;
    /** Screen origin y-coordinate returned by wind_get(). */
    short screen_y;
    /** Screen width returned by wind_get(). */
    short screen_width;
    /** Screen height returned by wind_get(). */
    short screen_height;
    /** Height of the GEM menu bar. */
    short menu_bar_height;
    /** Width of the dropdown menu boxes. */
    short menu_width;

    graf_handle(&character_width, &character_height, &box_width, &box_height);
    wind_get(0, WF_CURRXYWH, &screen_x, &screen_y, &screen_width, &screen_height);

    menu_bar_height = (short)(2 * character_height + 1);
    menu_width = (short)(28 * character_width);

    set_menu_object(MENU_OBJECT_ROOT,
        -1, MENU_OBJECT_BAR, MENU_OBJECT_DROPDOWNS,
        G_IBOX, 0, OS_NORMAL, (OBSPEC){ .index = 0L },
        0, 0, screen_width, screen_height);

    set_menu_object(MENU_OBJECT_BAR,
        MENU_OBJECT_DROPDOWNS, MENU_OBJECT_ACTIVE, MENU_OBJECT_ACTIVE,
        G_BOX, 0, OS_NORMAL, (OBSPEC){ .index = 0x00001100L },
        0, 0, screen_width, menu_bar_height);

    set_menu_object(MENU_OBJECT_ACTIVE,
        MENU_OBJECT_BAR, MENU_OBJECT_DESK, MENU_OBJECT_FILE,
        G_IBOX, 0, OS_NORMAL, (OBSPEC){ .index = 0L },
        2 * character_width, 0, 40 * character_width, character_height);

    set_menu_object(MENU_OBJECT_DESK,
        MENU_OBJECT_FILE, -1, -1,
        G_TITLE, 0, OS_NORMAL, (OBSPEC){ .free_string = desk_title },
        0, 0, 6 * character_width, character_height);

    set_menu_object(MENU_OBJECT_FILE,
        MENU_OBJECT_ACTIVE, -1, -1,
        G_TITLE, 0, OS_NORMAL, (OBSPEC){ .free_string = file_title },
        6 * character_width, 0, 7 * character_width, character_height);

    set_menu_object(MENU_OBJECT_DROPDOWNS,
        MENU_OBJECT_ROOT, MENU_OBJECT_DESK_BOX, MENU_OBJECT_FILE_BOX,
        G_IBOX, 0, OS_NORMAL, (OBSPEC){ .index = 0L },
        0, character_height + 1, screen_width,
        screen_height - character_height - 1);

    set_menu_object(MENU_OBJECT_DESK_BOX,
        MENU_OBJECT_FILE_BOX, MENU_OBJECT_INFO, MENU_OBJECT_INFO,
        G_BOX, 0, OS_NORMAL, (OBSPEC){ .index = 0x00001100L },
        2 * character_width, 0, menu_width, character_height + 2);

    set_menu_object(MENU_OBJECT_INFO,
        MENU_OBJECT_DESK_BOX, -1, -1,
        G_STRING, OF_SELECTABLE, OS_NORMAL,
        (OBSPEC){ .free_string = info_text },
        0, 0, menu_width, character_height);

    set_menu_object(MENU_OBJECT_FILE_BOX,
        MENU_OBJECT_DROPDOWNS, MENU_OBJECT_QUIT, MENU_OBJECT_QUIT,
        G_BOX, 0, OS_NORMAL, (OBSPEC){ .index = 0x00001100L },
        8 * character_width, 0, menu_width, character_height + 2);

    set_menu_object(MENU_OBJECT_QUIT,
        MENU_OBJECT_FILE_BOX, -1, -1,
        G_STRING, OF_SELECTABLE | OF_LASTOB, OS_NORMAL,
        (OBSPEC){ .free_string = quit_text },
        0, 0, menu_width, character_height);
}

/**
 * Builds and displays the application's GEM menu bar.
 */
void menu_show(void)
{
    create_menu();
    menu_bar(menu_tree, 1);
}

/**
 * Hides the application's GEM menu bar.
 */
void menu_hide(void)
{
    menu_bar(menu_tree, 0);
}

/**
 * Handles one AES message and normalizes selected menu titles.
 *
 * @param message Eight-word AES message buffer.
 * @return The menu action selected by the user, or MENU_ACTION_NONE.
 */
MenuAction menu_handle_message(const short message[8])
{
    /** Action resulting from the selected menu object. */
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
