#include <gem.h>

#include "about_dialog.h"
#include "menu.h"

/**
 * Initializes GEM and coordinates the application's event loop.
 *
 * @return 0 after a normal quit, or a non-zero value when GEM
 *         initialization fails.
 */
int main(void)
{
    /** AES application identifier returned by appl_init(). */
    short application_id;
    /** Eight-word buffer filled by evnt_mesag(). */
    short message[8];
    /** Action requested by a selected menu item. */
    MenuAction action;
    /** Whether the application should continue waiting for messages. */
    int running;

    application_id = appl_init();
    if (application_id < 0)
    {
        return 1;
    }

    menu_show();
    /* Restore the standard application cursor after AES startup. */
    graf_mouse(ARROW, 0);
    running = 1;
    while (running)
    {
        evnt_mesag(message);
        action = menu_handle_message(message);

        switch (action)
        {
            case MENU_ACTION_ABOUT:
                about_dialog_show();
                break;

            case MENU_ACTION_QUIT:
                running = 0;
                break;

            case MENU_ACTION_NONE:
                break;
        }
    }

    menu_hide();
    appl_exit();
    return 0;
}
