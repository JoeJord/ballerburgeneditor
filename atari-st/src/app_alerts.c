#include <gem.h>

#include "app_alerts.h"
#include "app_logger.h"

/**
 * Displays an AES alert with the standard alert icon.
 *
 * @param text GEM alert text, including its button specification.
 */
void app_alerts_show(const char *text)
{
    app_logger_write(text);
    form_alert(1, text);
}

const char APP_ALERT_LOG_OPEN_FAILED[] =
    "[1][Unable to open BALLERED.LOG.][Continue]";
const char APP_ALERT_ABOUT_RESOURCE_MISSING[] =
    "[1][About dialog is missing from the resources.][OK]";
const char APP_ALERT_CLOSE_MENU_UPDATE_FAILED[] =
    "[1][AES could not update the Close menu item.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_CLOSE_FAILED[] =
    "[1][Unable to close the document window.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_DELETE_FAILED[] =
    "[1][Unable to delete the document window.][OK]";
const char APP_ALERT_DESKTOP_SIZE_FAILED[] =
    "[1][Unable to determine the desktop size.][OK]";
const char APP_ALERT_GEM_MENU_INSTALL_FAILED[] =
    "[1][AES could not install the GEM menu.][Quit]";
const char APP_ALERT_FILE_SELECTOR_FAILED[] =
    "[1][The GEM file selector could not be opened.][OK]";
const char APP_ALERT_RESOURCES_LOAD_FAILED[] =
    "[1][Unable to load application resources.][Quit]";
const char APP_ALERT_MAIN_MENU_MISSING[] =
    "[1][Main menu tree is missing from the resources.][Quit]";
const char APP_ALERT_CURRENT_DIRECTORY_FAILED[] =
    "[1][Unable to determine the current directory.][OK]";
const char APP_ALERT_FILE_PATH_TOO_LONG[] =
    "[1][Selected file path is too long.][OK]";
const char APP_ALERT_FILE_SELECTOR_TITLE_MISSING[] =
    "[1][Open dialog title is missing from the resources.][OK]";
const char APP_ALERT_SELECTED_FILE_CLOSE_FAILED[] =
    "[1][Unable to close the selected DAT file.][OK]";
const char APP_ALERT_SELECTED_FILE_OPEN_FAILED[] =
    "[1][Unable to open the selected DAT file.][OK]";
const char APP_ALERT_SELECTED_PATH_INVALID[] =
    "[1][Invalid selected path result.][OK]";
const char APP_ALERT_DAT_FILE_REQUIRED[] =
    "[1][Please select a .DAT file.][OK]";
const char APP_ALERT_VDI_OPEN_FAILED[] =
    "[1][Unable to open the VDI workstation.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_CREATE_FAILED[] =
    "[1][Window creation failed.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_OPEN_FAILED[] =
    "[1][Unable to open the document window.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_TITLE_FAILED[] =
    "[1][Unable to set the document window title.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_TITLE_MISSING[] =
    "[1][Untitled document name is missing from the resources.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_LIMIT_REACHED[] =
    "[1][Window limit reached.][OK]";
const char APP_ALERT_TOP_DOCUMENT_WINDOW_MISSING[] =
    "[1][Unable to find the top document window.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_MAXIMIZE_FAILED[] =
    "[1][Unable to maximize the window.][OK]";
const char APP_ALERT_DOCUMENT_WINDOW_RESTORE_FAILED[] =
    "[1][Unable to restore the window size.][OK]";
const char APP_ALERT_DISPLAY_MODE_UNSUPPORTED[] =
    "[1][Ballerburgen Editor requires|ST monochrome (640x400).][Quit]";
const char APP_ALERT_CLOSE_MENU_DISABLE_FAILED[] =
    "[1][AES could not disable the Close menu item.][Quit]";
