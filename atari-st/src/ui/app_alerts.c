#include <gem.h>
#include <stddef.h>

#include "BALLERED.h"
#include "app_alerts.h"
#include "app_logger.h"
#include "app_resources.h"

typedef struct
{
    const char *fallback_text;
    short resource_index;
} AppAlertResource;

/** Maps built-in alert fallbacks to their RSC free strings. */
static const AppAlertResource alert_resources[] = {
    { APP_ALERT_LOG_OPEN_FAILED, ALR_LOG_OPEN },
    { APP_ALERT_ABOUT_RESOURCE_MISSING, ALR_ABOUT_MISS },
    { APP_ALERT_CLOSE_MENU_UPDATE_FAILED, ALR_CLOSE_UPD },
    { APP_ALERT_DOCUMENT_WINDOW_CLOSE_FAILED, ALR_DOC_CLOSE },
    { APP_ALERT_DOCUMENT_WINDOW_DELETE_FAILED, ALR_DOC_DELETE },
    { APP_ALERT_DESKTOP_SIZE_FAILED, ALR_DESKTOP },
    { APP_ALERT_GEM_MENU_INSTALL_FAILED, ALR_MENU_INSTALL },
    { APP_ALERT_FILE_SELECTOR_FAILED, ALR_FILE_SELECT },
    { APP_ALERT_MAIN_MENU_MISSING, ALR_MAIN_MENU },
    { APP_ALERT_CURRENT_DIRECTORY_FAILED, ALR_CUR_DIR },
    { APP_ALERT_FILE_PATH_TOO_LONG, ALR_PATH_LONG },
    { APP_ALERT_FILE_SELECTOR_TITLE_MISSING, ALR_OPEN_TITLE },
    { APP_ALERT_SELECTED_FILE_OPEN_FAILED, ALR_FILE_OPEN },
    { APP_ALERT_SELECTED_PATH_INVALID, ALR_PATH_INVALID },
    { APP_ALERT_DAT_FILE_REQUIRED, ALR_DAT_REQUIRED },
    { APP_ALERT_DAT_FORMAT_INVALID, ALR_DAT_INVALID },
    { APP_ALERT_DAT_MEMORY_EXHAUSTED, ALR_DAT_MEMORY },
    { APP_ALERT_VDI_OPEN_FAILED, ALR_VDI_OPEN },
    { APP_ALERT_DOCUMENT_WINDOW_CREATE_FAILED, ALR_WIN_CREATE },
    { APP_ALERT_DOCUMENT_WINDOW_OPEN_FAILED, ALR_WIN_OPEN },
    { APP_ALERT_DOCUMENT_WINDOW_TITLE_FAILED, ALR_WIN_TITLE },
    { APP_ALERT_DOCUMENT_WINDOW_TITLE_MISSING, ALR_TITLE_MISS },
    { APP_ALERT_DOCUMENT_MENU_UPDATE_FAILED, ALR_DOC_MENU },
    { APP_ALERT_TOP_DOCUMENT_WINDOW_MISSING, ALR_TOP_WIN },
    { APP_ALERT_DOCUMENT_WINDOW_MAXIMIZE_FAILED, ALR_WIN_MAX },
    { APP_ALERT_DOCUMENT_WINDOW_RESTORE_FAILED, ALR_WIN_RESTORE },
    { APP_ALERT_DISPLAY_MODE_UNSUPPORTED, ALR_DISPLAY },
    { APP_ALERT_CLOSE_MENU_DISABLE_FAILED, ALR_CLS_DISABLE },
    { APP_ALERT_TOOLBAR_VDI_OPEN_FAILED, ALR_TOOL_VDI },
    { APP_ALERT_TOOLBAR_ICON_MISSING, ALR_TOOL_ICON },
    { APP_ALERT_TOOLBAR_WINDOW_CREATE_FAILED, ALR_TOOL_CREATE },
    { APP_ALERT_TOOLBAR_WINDOW_OPEN_FAILED, ALR_TOOL_OPEN },
    { APP_ALERT_TOOLBAR_WINDOW_CLOSE_FAILED, ALR_TOOL_CLOSE },
    { APP_ALERT_TOOLBAR_WINDOW_DELETE_FAILED, ALR_TOOL_DELETE },
    { 0, 0 }
};

/**
 * Resolves a built-in fallback to its RSC alert when available.
 *
 * @param text Built-in alert fallback.
 * @return Resource string if available, otherwise the supplied fallback.
 */
static const char *resolve_alert_text(const char *text)
{
    size_t index;
    char *resource_text;

    for (index = 0; index < sizeof(alert_resources) / sizeof(alert_resources[0]); index++)
    {
        if (alert_resources[index].fallback_text
            && alert_resources[index].fallback_text == text)
        {
            if (!app_resources_load())
            {
                break;
            }
            resource_text = app_resources_get_string(
                alert_resources[index].resource_index
            );
            if (resource_text)
            {
                return resource_text;
            }
            break;
        }
    }

    return text;
}

/**
 * Displays an AES alert with the standard alert icon.
 *
 * @param text Built-in fallback alert text, including its button specification.
 */
void app_alerts_show(const char *text)
{
    const char *alert_text;

    alert_text = resolve_alert_text(text);
    app_logger_write(alert_text);
    form_alert(1, alert_text);
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
const char APP_ALERT_SELECTED_FILE_OPEN_FAILED[] =
    "[1][Unable to open the selected DAT file.][OK]";
const char APP_ALERT_SELECTED_PATH_INVALID[] =
    "[1][Invalid selected path result.][OK]";
const char APP_ALERT_DAT_FILE_REQUIRED[] =
    "[1][Please select a .DAT file.][OK]";
const char APP_ALERT_DAT_FORMAT_INVALID[] =
    "[1][The selected castle data file is invalid.][OK]";
const char APP_ALERT_DAT_MEMORY_EXHAUSTED[] =
    "[1][Not enough memory to load castle data.][OK]";
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
const char APP_ALERT_DOCUMENT_MENU_UPDATE_FAILED[] =
    "[1][AES could not update|File > New and File > Open.][OK]";
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
const char APP_ALERT_TOOLBAR_VDI_OPEN_FAILED[] =
    "[1][Unable to open the toolbar VDI workstation.][Quit]";
const char APP_ALERT_TOOLBAR_ICON_MISSING[] =
    "[1][A toolbar icon is missing from the resources.][Quit]";
const char APP_ALERT_TOOLBAR_WINDOW_CREATE_FAILED[] =
    "[1][Unable to create the toolbar.][Quit]";
const char APP_ALERT_TOOLBAR_WINDOW_OPEN_FAILED[] =
    "[1][Unable to open the toolbar.][Quit]";
const char APP_ALERT_TOOLBAR_WINDOW_CLOSE_FAILED[] =
    "[1][Unable to close the toolbar.][OK]";
const char APP_ALERT_TOOLBAR_WINDOW_DELETE_FAILED[] =
    "[1][Unable to delete the toolbar.][OK]";
