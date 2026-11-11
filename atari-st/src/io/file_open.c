#include <gem.h>
#include <osbind.h>
#include <string.h>

#include "BALLERED.h"
#include "app_resources.h"
#include "app_alerts.h"
#include "app_logger.h"
#include "file_open.h"

/** Maximum path length supported by the GEM file selector and document API. */
#define OPEN_PATH_LENGTH 128
/** Maximum 8.3 filename length, including its terminating null byte. */
#define FILE_NAME_LENGTH 14

/** Internal storage for the path returned by the most recent file selection. */
static char selected_file_path[OPEN_PATH_LENGTH];

/**
 * Builds a file-selector pattern from the current drive and directory.
 *
 * @param path Output path pattern buffer.
 * @return Non-zero if the initial pattern was created successfully.
 */
static short build_initial_path(char path[OPEN_PATH_LENGTH])
{
    char directory[OPEN_PATH_LENGTH];
    char *directory_path;
    short drive;
    short directory_length;
    short position;

    drive = Dgetdrv();
    if (drive < 0 || drive >= 26
        || Dgetpath(directory, (short)(drive + 1)) < 0)
    {
        app_alerts_show(APP_ALERT_CURRENT_DIRECTORY_FAILED);
        return 0;
    }

    directory_path = directory;
    if (directory_path[0] == '\\')
    {
        directory_path++;
    }
    directory_length = (short)strlen(directory_path);

    position = 0;
    path[position++] = (char)('A' + drive);
    path[position++] = ':';
    path[position++] = '\\';

    if (directory_length > 0)
    {
        if (position + directory_length + 1 + 6 > OPEN_PATH_LENGTH)
        {
            app_alerts_show(APP_ALERT_FILE_PATH_TOO_LONG);
            return 0;
        }
        memcpy(&path[position], directory_path, (size_t)directory_length);
        position += directory_length;
        path[position++] = '\\';
    }
    else if (position + 6 > OPEN_PATH_LENGTH)
    {
        app_alerts_show(APP_ALERT_FILE_PATH_TOO_LONG);
        return 0;
    }

    memcpy(&path[position], "*.DAT", 6);
    return 1;
}

/**
 * Checks whether a filename has the DAT extension, ignoring case.
 *
 * @param file Filename returned by the GEM file selector.
 * @return Non-zero if the filename ends in .DAT.
 */
static short has_dat_extension(const char *file)
{
    const char *extension;
    char first;
    char second;
    char third;

    extension = strrchr(file, '.');
    if (!extension || strlen(extension) != 4)
    {
        return 0;
    }

    first = extension[1];
    second = extension[2];
    third = extension[3];
    if (first >= 'a' && first <= 'z')
    {
        first = (char)(first - 'a' + 'A');
    }
    if (second >= 'a' && second <= 'z')
    {
        second = (char)(second - 'a' + 'A');
    }
    if (third >= 'a' && third <= 'z')
    {
        third = (char)(third - 'a' + 'A');
    }

    return first == 'D' && second == 'A' && third == 'T';
}

/**
 * Combines the selected directory and filename into a full path.
 *
 * @param directory Path buffer returned by the file selector.
 * @param file Selected filename.
 * @return Non-zero if the full path fits in the output buffer.
 */
static short combine_selected_path(
    char *directory,
    const char *file
)
{
    char *wildcard;
    char *separator;
    short directory_length;
    short file_length;
    short add_separator;

    wildcard = strrchr(directory, '*');
    if (wildcard)
    {
        separator = strrchr(directory, '\\');
        if (separator && wildcard > separator)
        {
            separator[1] = '\0';
        }
    }

    directory_length = (short)strlen(directory);
    file_length = (short)strlen(file);
    add_separator = directory_length > 0
        && directory[directory_length - 1] != '\\'
        && directory[directory_length - 1] != '/';

    if (directory_length + add_separator + file_length + 1 > OPEN_PATH_LENGTH)
    {
        app_alerts_show(APP_ALERT_FILE_PATH_TOO_LONG);
        return 0;
    }

    memcpy(selected_file_path, directory, (size_t)directory_length);
    if (add_separator)
    {
        selected_file_path[directory_length++] = '\\';
    }
    memcpy(
        &selected_file_path[directory_length],
        file,
        (size_t)file_length + 1
    );
    return 1;
}

/**
 * Internal implementation for file selection dialogs.
 *
 * @param title_resource Resource ID for the dialog title.
 * @param log_prefix Prefix for log messages.
 * @param selected_path Receives a pointer to the selected full path, valid
 *                      until the next call to this function.
 * @return 1 if a file was selected, 0 if cancelled, or -1 on error.
 */
static short file_select_internal(
    short title_resource,
    const char *log_prefix,
    const char **selected_path
)
{
    char path[OPEN_PATH_LENGTH];
    char file[FILE_NAME_LENGTH];
    char *dialog_title;
    short exit_button;

    if (!selected_path)
    {
        app_logger_write("File selector called with an invalid output pointer.");
        app_alerts_show(APP_ALERT_SELECTED_PATH_INVALID);
        return -1;
    }
    *selected_path = 0;
    selected_file_path[0] = '\0';

    if (!build_initial_path(path))
    {
        app_logger_write("Unable to prepare the initial file-selector path.");
        return -1;
    }

    dialog_title = app_resources_get_string(title_resource);
    if (!dialog_title)
    {
        app_logger_write("File selector title is missing from the resources.");
        app_alerts_show(APP_ALERT_FILE_SELECTOR_TITLE_MISSING);
        return -1;
    }

    file[0] = '\0';
    exit_button = FSEL_CANCEL;
    if (!fsel_exinput(path, file, &exit_button, dialog_title))
    {
        app_logger_write("GEM file selector failed.");
        app_alerts_show(APP_ALERT_FILE_SELECTOR_FAILED);
        return -1;
    }
    if (exit_button != FSEL_OK)
    {
        app_logger_write("File selection cancelled.");
        return 0;
    }
    if (!file[0])
    {
        app_logger_write("File selector returned an empty filename.");
        return 0;
    }
    if (!has_dat_extension(file))
    {
        app_logger_write("Selected file does not have a DAT extension.");
        app_alerts_show(APP_ALERT_DAT_FILE_REQUIRED);
        return -1;
    }
    if (!combine_selected_path(path, file))
    {
        app_logger_write("Selected file path exceeds the supported length.");
        return -1;
    }

    *selected_path = selected_file_path;
    app_logger_write(log_prefix);
    return 1;
}

/**
 * Shows the GEM file selector filtered to DAT files.
 *
 * @param selected_path Receives a pointer to the selected full path, valid
 *                      until the next call to this function.
 * @return 1 if a file was selected, 0 if cancelled, or -1 on error.
 */
short file_open_select(const char **selected_path)
{
    return file_select_internal(MSG_FILE_OPEN, "DAT file selected.", selected_path);
}

/**
 * Shows the GEM file selector for saving DAT files.
 *
 * @param selected_path Receives a pointer to the selected full path, valid
 *                      until the next call to this function.
 * @return 1 if a file was selected, 0 if cancelled, or -1 on error.
 */
short file_save_select(const char **selected_path)
{
    return file_select_internal(MSG_FILE_SAVE, "DAT file selected for saving.", selected_path);
}
