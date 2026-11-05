#include <gem.h>
#include <osbind.h>

#include "about_dialog.h"
#include "app_alerts.h"
#include "document_window.h"
#include "file_open.h"
#include "menu.h"
#include "toolbar.h"

/**
 * Verifies that the Atari ST is running in its high-resolution monochrome mode.
 *
 * @return Non-zero if the current resolution is supported; otherwise displays
 *         an alert and returns zero.
 */
static int check_display_mode(void)
{
    if (Getrez() >= 2)
    {
        return 1;
    }

    app_alerts_show(APP_ALERT_DISPLAY_MODE_UNSUPPORTED);
    return 0;
}

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
    /** Eight-word buffer filled by evnt_multi(). */
    short message[8];
    /** Full path returned by the GEM file selector. */
    const char *selected_path;
    /** Action requested by a selected menu item. */
    MenuAction action;
    /** Whether the application should continue waiting for messages. */
    int running;
    /** Events returned by evnt_multi(). */
    short event_flags;
    /** Mouse position and state returned by evnt_multi(). */
    short mouse_x;
    short mouse_y;
    short mouse_button;
    short key_state;
    short key;
    short clicks;

    application_id = appl_init();
    if (application_id < 0)
    {
        return 1;
    }

    if (!check_display_mode())
    {
        appl_exit();
        return 1;
    }

    if (!menu_show())
    {
        appl_exit();
        return 1;
    }
    if (!toolbar_show())
    {
        menu_hide();
        appl_exit();
        return 1;
    }
    /* Restore the standard application cursor after AES startup. */
    graf_mouse(ARROW, 0);
    running = 1;
    while (running)
    {
        event_flags = evnt_multi(
            MU_MESAG | MU_BUTTON,
            1,
            1,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            message,
            0L,
            &mouse_x,
            &mouse_y,
            &mouse_button,
            &key_state,
            &key,
            &clicks
        );
        action = MENU_ACTION_NONE;
        if (event_flags & MU_MESAG)
        {
            action = menu_handle_message(message);
            document_window_handle_message(message);
            toolbar_handle_message(message);
        }
        if ((event_flags & MU_BUTTON) && action == MENU_ACTION_NONE)
        {
            action = toolbar_handle_click(mouse_x, mouse_y);
        }

        switch (action)
        {
            case MENU_ACTION_NEW:
                document_window_create();
                break;

            case MENU_ACTION_OPEN:
            {
                short selection_result;

                selection_result = file_open_select(&selected_path);
                if (selection_result > 0)
                {
                    document_window_open_file(selected_path);
                }
                break;
            }

            case MENU_ACTION_CLOSE:
                document_window_close_top();
                break;

            case MENU_ACTION_ABOUT:
                about_dialog_show();
                break;

            case MENU_ACTION_QUIT:
                running = 0;
                break;

            case MENU_ACTION_NONE:
                break;
        }

        if (!menu_set_close_enabled(document_window_has_open_windows()))
        {
            app_alerts_show(APP_ALERT_CLOSE_MENU_UPDATE_FAILED);
        }
    }

    document_window_shutdown();
    toolbar_hide();
    menu_hide();
    appl_exit();
    return 0;
}
