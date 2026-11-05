#ifndef BALLERBURGENEDITOR_ATARI_ST_FILE_OPEN_H
#define BALLERBURGENEDITOR_ATARI_ST_FILE_OPEN_H

/**
 * Shows the GEM file selector filtered to DAT files.
 *
 * @param selected_path Receives a pointer to the selected full path, valid
 *                      until the next call to this function.
 * @return 1 if a file was selected, 0 if cancelled, or -1 on error.
 */
short file_open_select(const char **selected_path);

#endif
