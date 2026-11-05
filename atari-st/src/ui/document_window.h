#ifndef BALLERBURGENEDITOR_ATARI_ST_DOCUMENT_WINDOW_H
#define BALLERBURGENEDITOR_ATARI_ST_DOCUMENT_WINDOW_H

#include <gem.h>

/**
 * Creates and opens a new blank document window.
 *
 * @return Non-zero if the window was created and opened successfully.
 */
short document_window_create(void);

/**
 * Opens a readable DAT file in a document window.
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
 * Checks whether at least one document window is open.
 *
 * @return Non-zero if a document window is open.
 */
short document_window_has_open_windows(void);

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
