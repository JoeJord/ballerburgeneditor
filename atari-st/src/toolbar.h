#ifndef BALLERBURGENEDITOR_ATARI_ST_TOOLBAR_H
#define BALLERBURGENEDITOR_ATARI_ST_TOOLBAR_H

#include "menu.h"

/**
 * Creates and displays the fixed toolbar below the GEM menu bar.
 *
 * @return Non-zero if the toolbar was created and opened successfully.
 */
short toolbar_show(void);

/**
 * Closes the toolbar window and releases its VDI workstation.
 */
void toolbar_hide(void);

/**
 * Returns the vertical space reserved for the toolbar.
 *
 * @return Toolbar height in pixels, or zero when it is not shown.
 */
short toolbar_get_height(void);

/**
 * Updates whether the Close toolbar button is enabled.
 *
 * @param enabled Non-zero if a document window is open.
 */
void toolbar_set_close_enabled(short enabled);

/**
 * Handles toolbar redraw messages.
 *
 * @param message Eight-word AES message buffer.
 */
void toolbar_handle_message(const short message[8]);

/**
 * Returns the toolbar action at the specified screen coordinates.
 *
 * @param x Mouse X coordinate.
 * @param y Mouse Y coordinate.
 * @return The selected action, or MENU_ACTION_NONE if no active button was hit.
 */
MenuAction toolbar_handle_click(short x, short y);

#endif
