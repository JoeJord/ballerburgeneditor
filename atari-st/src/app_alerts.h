#ifndef BALLERBURGENEDITOR_ATARI_ST_APP_ALERTS_H
#define BALLERBURGENEDITOR_ATARI_ST_APP_ALERTS_H

/** Alert shown when the About dialog tree cannot be found. */
extern const char APP_ALERT_ABOUT_RESOURCE_MISSING[];
/** Alert shown when AES cannot update the Close menu item. */
extern const char APP_ALERT_CLOSE_MENU_UPDATE_FAILED[];
/** Alert shown when AES cannot close a document window. */
extern const char APP_ALERT_DOCUMENT_WINDOW_CLOSE_FAILED[];
/** Alert shown when AES cannot delete a document window. */
extern const char APP_ALERT_DOCUMENT_WINDOW_DELETE_FAILED[];
/** Alert shown when AES cannot determine the desktop size. */
extern const char APP_ALERT_DESKTOP_SIZE_FAILED[];
/** Alert shown when AES cannot install the GEM menu. */
extern const char APP_ALERT_GEM_MENU_INSTALL_FAILED[];
/** Alert shown when the GEM file selector cannot be opened. */
extern const char APP_ALERT_FILE_SELECTOR_FAILED[];
/** Alert shown when AES cannot load the application resources. */
extern const char APP_ALERT_RESOURCES_LOAD_FAILED[];
/** Alert shown when the main menu tree is missing from the resources. */
extern const char APP_ALERT_MAIN_MENU_MISSING[];
/** Alert shown when the current directory cannot be determined. */
extern const char APP_ALERT_CURRENT_DIRECTORY_FAILED[];
/** Alert shown when a selected path exceeds the supported path length. */
extern const char APP_ALERT_FILE_PATH_TOO_LONG[];
/** Alert shown when the file selector title is missing from the resources. */
extern const char APP_ALERT_FILE_SELECTOR_TITLE_MISSING[];
/** Alert shown when the selected DAT file cannot be closed after checking. */
extern const char APP_ALERT_SELECTED_FILE_CLOSE_FAILED[];
/** Alert shown when the selected DAT file cannot be opened. */
extern const char APP_ALERT_SELECTED_FILE_OPEN_FAILED[];
/** Alert shown when the file selector result pointer is invalid. */
extern const char APP_ALERT_SELECTED_PATH_INVALID[];
/** Alert shown when the selected filename does not have a DAT extension. */
extern const char APP_ALERT_DAT_FILE_REQUIRED[];
/** Alert shown when the VDI virtual workstation cannot be opened. */
extern const char APP_ALERT_VDI_OPEN_FAILED[];
/** Alert shown when a document window cannot be created. */
extern const char APP_ALERT_DOCUMENT_WINDOW_CREATE_FAILED[];
/** Alert shown when a document window cannot be opened. */
extern const char APP_ALERT_DOCUMENT_WINDOW_OPEN_FAILED[];
/** Alert shown when a document window title cannot be set. */
extern const char APP_ALERT_DOCUMENT_WINDOW_TITLE_FAILED[];
/** Alert shown when the document window limit has been reached. */
extern const char APP_ALERT_DOCUMENT_WINDOW_LIMIT_REACHED[];
/** Alert shown when there is no topmost document window to close. */
extern const char APP_ALERT_TOP_DOCUMENT_WINDOW_MISSING[];
/** Alert shown when a document window cannot be maximized. */
extern const char APP_ALERT_DOCUMENT_WINDOW_MAXIMIZE_FAILED[];
/** Alert shown when a document window cannot be restored. */
extern const char APP_ALERT_DOCUMENT_WINDOW_RESTORE_FAILED[];
/** Alert shown when the display mode is not supported. */
extern const char APP_ALERT_DISPLAY_MODE_UNSUPPORTED[];
/** Alert shown when the resource menu cannot disable its Close item. */
extern const char APP_ALERT_CLOSE_MENU_DISABLE_FAILED[];

/**
 * Displays an AES alert with the standard alert icon.
 *
 * @param text GEM alert text, including its button specification.
 */
void app_alerts_show(const char *text);

#endif
