#include <gem.h>
#include <osbind.h>

#include "about_dialog.h"
#include "app_alerts.h"
#include "app_logger.h"
#include "document_window.h"
#include "file_open.h"
#include "menu.h"

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
    /** Eight-word buffer filled by evnt_mesag(). */
    short message[8];
    /** Full path returned by the GEM file selector. */
    const char *selected_path;
    /** Action requested by a selected menu item. */
    MenuAction action;
    /** Whether the application should continue waiting for messages. */
    int running;

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
    app_logger_write("Menu installed.");
    /* Restore the standard application cursor after AES startup. */
    graf_mouse(ARROW, 0);
    running = 1;
    while (running)
    {
        evnt_mesag(message);
        action = menu_handle_message(message);
        document_window_handle_message(message);

        switch (action)
        {
            case MENU_ACTION_NEW:
                app_logger_write("Action: New document.");
                document_window_create();
                break;

            case MENU_ACTION_OPEN:
            {
                short selection_result;

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
    }

    document_window_shutdown();
    app_logger_write("Document windows shut down.");
    menu_hide();
    app_logger_write("Menu and resources released.");
    appl_exit();
    app_logger_close();
    return 0;
}
