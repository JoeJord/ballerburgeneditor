#include <gem.h>
#include <osbind.h>

#include "about_dialog.h"
#include "app_alerts.h"
#include "app_logger.h"
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

    if (!app_logger_open())
    {
        application_id = appl_init();
        if (application_id >= 0)
        {
            app_alerts_show(APP_ALERT_LOG_OPEN_FAILED);
            appl_exit();
        }
        return 1;
    }
    app_logger_write("Initializing AES.");

    application_id = appl_init();
    if (application_id < 0)
    {
        app_logger_write("AES initialization failed.");
        app_logger_close();
        return 1;
    }
    app_logger_write("AES initialized.");

    if (!check_display_mode())
    {
        app_logger_write("Unsupported display mode; exiting.");
        appl_exit();
        app_logger_close();
        return 1;
    }
    app_logger_write("Display mode accepted.");

    if (!menu_show())
    {
        app_logger_write("Menu initialization failed; exiting.");
        appl_exit();
        app_logger_close();
        return 1;
    }
    app_logger_write("Starting toolbar initialization.");
    if (!toolbar_show())
    {
        app_logger_write("Toolbar initialization failed; exiting.");
        menu_hide();
        appl_exit();
        app_logger_close();
        return 1;
    }
    app_logger_write("Toolbar initialization complete.");
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
                app_logger_write("Action: New document.");
                document_window_create();
                break;

            case MENU_ACTION_OPEN:
            {
                short selection_result;

                if (!document_window_has_capacity())
                {
                    app_logger_write(
                        "Document window limit reached; open request ignored."
                    );
                    break;
                }

                app_logger_write("Action: Open document.");
                selection_result = file_open_select(&selected_path);
                if (selection_result > 0)
                {
                    document_window_open_file(selected_path);
                }
                else if (selection_result == 0)
                {
                    app_logger_write("File selection cancelled.");
                }
                break;
            }

            case MENU_ACTION_CLOSE:
                app_logger_write("Action: Close document.");
                document_window_close_top();
                break;

            case MENU_ACTION_ABOUT:
                app_logger_write("Action: About.");
                about_dialog_show();
                break;

            case MENU_ACTION_PREVIOUS_CASTLE:
                document_window_navigate_castles(DOCUMENT_CASTLE_PREVIOUS);
                break;

            case MENU_ACTION_NEXT_CASTLE:
                document_window_navigate_castles(DOCUMENT_CASTLE_NEXT);
                break;

            case MENU_ACTION_DELETE_CASTLE:
                app_logger_write("Edit action requested: delete castle.");
                break;

            case MENU_ACTION_ADD_CASTLE:
                app_logger_write("Edit action requested: add castle.");
                break;

            case MENU_ACTION_ADD_THRONE:
                app_logger_write("Edit action requested: add throne.");
                break;

            case MENU_ACTION_ADD_MONEY_STORAGE:
                app_logger_write("Edit action requested: add money storage.");
                break;

            case MENU_ACTION_ADD_POWDER_STORAGE:
                app_logger_write("Edit action requested: add powder storage.");
                break;

            case MENU_ACTION_ADD_BALL_STORAGE:
                app_logger_write("Edit action requested: add cannonball storage.");
                break;

            case MENU_ACTION_ADD_CANNON:
                app_logger_write("Edit action requested: add cannon.");
                break;

            case MENU_ACTION_ADD_WIND_VANE:
                app_logger_write("Edit action requested: add wind vane.");
                break;

            case MENU_ACTION_ADD_WALL:
                app_logger_write("Edit action requested: add wall.");
                break;

            case MENU_ACTION_ADD_ROOF:
                app_logger_write("Edit action requested: add roof.");
                break;

            case MENU_ACTION_DELETE_ELEMENT:
                app_logger_write("Edit action requested: delete selected element.");
                break;

            case MENU_ACTION_QUIT:
                app_logger_write("Action: Quit.");
                running = 0;
                break;

            case MENU_ACTION_NONE:
                break;
        }

        if (!menu_set_close_enabled(document_window_has_open_windows()))
        {
            app_alerts_show(APP_ALERT_CLOSE_MENU_UPDATE_FAILED);
        }
        if (!menu_set_document_creation_enabled(
            document_window_has_capacity()
        ))
        {
            app_alerts_show(APP_ALERT_DOCUMENT_MENU_UPDATE_FAILED);
        }
    }

    document_window_shutdown();
    app_logger_write("Document windows shut down.");
    toolbar_hide();
    menu_hide();
    app_logger_write("Menu and resources released.");
    appl_exit();
    app_logger_close();
    return 0;
}
