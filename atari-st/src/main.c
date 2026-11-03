
#include <gem.h>

#define OBJ_ROOT       0
#define OBJ_BAR        1
#define OBJ_ACTIVE     2
#define OBJ_DESK       3
#define OBJ_FILE       4
#define OBJ_DROPDOWNS  5
#define OBJ_DESK_BOX   6
#define OBJ_INFO       7
#define OBJ_FILE_BOX   8
#define OBJ_QUIT       9

#define MENU_COUNT    10

static OBJECT menu[MENU_COUNT];

/* Menu texts */
static char desk_title[] = " Desk ";
static char file_title[] = " Datei ";

static char info_text[]  = "  Ballerburgen Editor Info";
static char quit_text[]  = "  Beenden        ";

/* Initialize menu object */
/**
 * Initializes one object in the application menu tree.
 *
 * @param id Object index.
 * @param next Next sibling or parent index.
 * @param head First child index, or -1 when there are no children.
 * @param tail Last child index, or -1 when there are no children.
 * @param type GEM object type.
 * @param flags GEM object flags.
 * @param state GEM object state.
 * @param spec Object-specific GEM data.
 * @param x Horizontal position in pixels.
 * @param y Vertical position in pixels.
 * @param width Object width in pixels.
 * @param height Object height in pixels.
 */
static void set_object(
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
    menu[id].ob_next   = next;
    menu[id].ob_head   = head;
    menu[id].ob_tail   = tail;
    menu[id].ob_type   = type;
    menu[id].ob_flags  = flags;
    menu[id].ob_state  = state;
    menu[id].ob_spec   = spec;
    menu[id].ob_x      = x;
    menu[id].ob_y      = y;
    menu[id].ob_width  = width;
    menu[id].ob_height = height;
}

/**
 * Creates the Desk and Datei menu tree.
 */
static void init_menu(void)
{
    short wchar, hchar;
    short wbox, hbox;
    short sx, sy, sw, sh;

    short bar_height;
    short menu_width;

    graf_handle(&wchar, &hchar, &wbox, &hbox);
    wind_get(0, WF_CURRXYWH, &sx, &sy, &sw, &sh);

    bar_height = 2 * hchar + 1;
    menu_width = 28 * wchar;

    /* ROOT */
    set_object(OBJ_ROOT,
        -1, OBJ_BAR, OBJ_DROPDOWNS,
        G_IBOX, 0, OS_NORMAL, (OBSPEC){ .index = 0L },
        0, 0, sw, sh);

    /* BAR */
    set_object(OBJ_BAR,
        OBJ_DROPDOWNS, OBJ_ACTIVE, OBJ_ACTIVE,
        G_BOX, 0, OS_NORMAL, (OBSPEC){ .index = 0x00001100L },
        0, 0, sw, bar_height);

    /* ACTIVE */
    set_object(OBJ_ACTIVE,
        OBJ_BAR, OBJ_DESK, OBJ_FILE,
        G_IBOX, 0, OS_NORMAL, (OBSPEC){ .index = 0L },
        2 * wchar, 0, 40 * wchar, hchar);

    /* DESK TITLE */
    set_object(OBJ_DESK,
        OBJ_FILE, -1, -1,
        G_TITLE, 0, OS_NORMAL, (OBSPEC){ .free_string = desk_title },
        0, 0, 6 * wchar, hchar);

    /* FILE TITLE */
    set_object(OBJ_FILE,
        OBJ_ACTIVE, -1, -1,
        G_TITLE, 0, OS_NORMAL, (OBSPEC){ .free_string = file_title },
        6 * wchar, 0, 7 * wchar, hchar);

    /* DROPDOWNS */
    set_object(OBJ_DROPDOWNS,
        OBJ_ROOT, OBJ_DESK_BOX, OBJ_FILE_BOX,
        G_IBOX, 0, OS_NORMAL, (OBSPEC){ .index = 0L },
        0, hchar + 1, sw, sh - hchar - 1);

    /* DESK MENU */
    set_object(OBJ_DESK_BOX,
        OBJ_FILE_BOX, OBJ_INFO, OBJ_INFO,
        G_BOX, 0, OS_NORMAL, (OBSPEC){ .index = 0x00001100L },
        2 * wchar, 0, menu_width, hchar + 2);

    /* INFO */
    set_object(OBJ_INFO,
        OBJ_DESK_BOX, -1, -1,
        G_STRING, OF_SELECTABLE, OS_NORMAL,
        (OBSPEC){ .free_string = info_text },
        0, 0, menu_width, hchar);

    /* FILE MENU */
    set_object(OBJ_FILE_BOX,
        OBJ_DROPDOWNS, OBJ_QUIT, OBJ_QUIT,
        G_BOX, 0, OS_NORMAL, (OBSPEC){ .index = 0x00001100L },
        8 * wchar, 0, menu_width, hchar + 2);

    /* QUIT */
    set_object(OBJ_QUIT,
        OBJ_FILE_BOX, -1, -1,
        G_STRING, OF_SELECTABLE | OF_LASTOB, OS_NORMAL,
        (OBSPEC){ .free_string = quit_text },
        0, 0, menu_width, hchar);
}

/* Main program */
int main(void)
{
    /* AES application identifier returned by appl_init. */
    short app_id;
    /* AES event message buffer. */
    short message[8];
    /* Whether the application should continue processing events. */
    short quit = 0;

    /* Initialize GEM */
    app_id = appl_init();

    if (app_id < 0)
        return 1;

    /* Initialize and display menu */
    init_menu();
    menu_bar(menu, 1);
    graf_mouse(ARROW, 0);

    /* Main event loop */
    while (!quit)
    {
        evnt_mesag(message);

        if (message[0] == MN_SELECTED)
        {
            switch (message[4])
            {
                case OBJ_INFO:
                    form_alert(
                        1,
                        "[1][Ballerburgen-Editor|"
                        "Atari Demo Version][OK]"
                    );
                    break;

                case OBJ_QUIT:
                    quit = 1;
                    break;
            }

            /* Reset menu title */
            menu_tnormal(menu, message[3], 1);
        }
    }

    /* Clean up */
    menu_bar(menu, 0);
    appl_exit();

    return 0;
}
