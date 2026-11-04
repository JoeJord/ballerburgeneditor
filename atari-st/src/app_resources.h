#ifndef BALLERBURGENEDITOR_ATARI_ST_APP_RESOURCES_H
#define BALLERBURGENEDITOR_ATARI_ST_APP_RESOURCES_H

#include <gem.h>

/**
 * Loads the application's GEM resource file.
 *
 * @return Non-zero if the resource file was loaded successfully.
 */
short app_resources_load(void);

/**
 * Returns the main menu resource tree.
 *
 * @return The menu tree, or 0 if it is unavailable.
 */
OBJECT *app_resources_get_main_menu(void);

/**
 * Returns the About dialog resource tree.
 *
 * @return The About dialog tree, or 0 if it is unavailable.
 */
OBJECT *app_resources_get_about_dialog(void);

/**
 * Releases the application's GEM resource file if it is loaded.
 */
void app_resources_release(void);

#endif
