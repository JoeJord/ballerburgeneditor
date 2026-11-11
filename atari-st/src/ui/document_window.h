#ifndef BALLERBURGENEDITOR_ATARI_ST_DOCUMENT_WINDOW_H
#define BALLERBURGENEDITOR_ATARI_ST_DOCUMENT_WINDOW_H

#include <gem.h>

/**
 * Direction used to navigate the active document's castle collection.
 */
typedef enum
{
    /** Select the castle before the currently displayed castle. */
    DOCUMENT_CASTLE_PREVIOUS = -1,
    /** Select the castle after the currently displayed castle. */
    DOCUMENT_CASTLE_NEXT = 1
} DocumentCastleNavigation;

/**
 * Creates and opens a new blank document window.
 *
 * @return Non-zero if the window was created and opened successfully.
 */
short document_window_create(void);

/**
 * Parses a DAT file and opens its castle collection in a document window.
 *
 * @param path Full path to the selected file.
 * @return Non-zero if the document window was created successfully.
 */
short document_window_open_file(const char *path);

/**
 * Closes the topmost document window.
 *
 * @return Non-zero if a document window was closed.
 */
short document_window_close_top(void);

/**
 * Selects an adjacent castle in the active document and redraws its work area.
 *
 * @param direction Previous or next castle.
 * @return Non-zero if a different castle was selected.
 */
short document_window_navigate_castles(DocumentCastleNavigation direction);

/**
 * Checks whether the active document can navigate in the requested direction.
 *
 * @param direction Previous or next castle.
 * @return Non-zero if an adjacent castle exists in the active document.
 */
short document_window_can_navigate_castles(DocumentCastleNavigation direction);

/**
 * Checks whether at least one document window is open.
 *
 * @return Non-zero if a document window is open.
 */
short document_window_has_open_windows(void);

/**
 * Checks whether another document window can be opened.
 *
 * @return Non-zero if at least one document-window slot is available.
 */
short document_window_has_capacity(void);

/**
 * Checks whether the active document has at least one castle.
 *
 * @return Non-zero if the active document window has a castle.
 */
short document_window_has_castle(void);

/**
 * Checks whether the active document has unsaved changes.
 *
 * @return Non-zero if the active document has been modified.
 */
short document_window_is_modified(void);

/**
 * Saves the active document.
 *
 * If the document has no associated file, the save-as dialog is shown.
 * On success, the modification flag is cleared and the window title is updated.
 *
 * @param force_dialog Non-zero to always show the save-as dialog.
 * @return Non-zero if the document was saved successfully.
 */
short document_window_save(short force_dialog);

/**
 * Removes the currently selected castle from the active document.
 *
 * @return Non-zero if a castle was removed successfully.
 */
short document_window_remove_castle(void);

/**
 * Adds a new castle to the active document and selects it.
 *
 * @return Non-zero if a castle was added successfully.
 */
short document_window_add_castle(void);

/**
 * Handles window-related messages from the AES event loop.
 *
 * @param message Eight-word AES message buffer.
 */
void document_window_handle_message(const short message[8]);

/**
 * Closes all document windows and releases the virtual workstation.
 */
void document_window_shutdown(void);

#endif
