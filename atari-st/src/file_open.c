#include <gem.h>
#include <osbind.h>
#include <string.h>

#include "BALLERED.h"
#include "app_resources.h"
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
        form_alert(1, "[1][Unable to determine the current directory.][OK]");
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
            form_alert(1, "[1][Current directory path is too long.][OK]");
            return 0;
        }
        memcpy(&path[position], directory_path, (size_t)directory_length);
        position += directory_length;
        path[position++] = '\\';
    }
    else if (position + 6 > OPEN_PATH_LENGTH)
    {
        form_alert(1, "[1][Current directory path is too long.][OK]");
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
        form_alert(1, "[1][Selected file path is too long.][OK]");
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
 * Shows the GEM file selector filtered to DAT files.
 *
 * @param selected_path Receives a pointer to the selected full path, valid
 *                      until the next call to this function.
 * @return 1 if a file was selected, 0 if cancelled, or -1 on error.
 */
short file_open_select(const char **selected_path)
{
    char path[OPEN_PATH_LENGTH];
    char file[FILE_NAME_LENGTH];
    char *dialog_title;
    short exit_button;

    if (!selected_path)
    {
        form_alert(1, "[1][Invalid selected path result.][OK]");
        return -1;
    }
    *selected_path = 0;
    selected_file_path[0] = '\0';

    if (!build_initial_path(path))
    {
        return -1;
    }

    dialog_title = app_resources_get_string(MSG_FILE_OPEN);
    if (!dialog_title)
    {
        form_alert(1, "[1][Open dialog title is missing from the resources.][OK]");
        return -1;
    }

    file[0] = '\0';
    exit_button = FSEL_CANCEL;
    if (!fsel_exinput(path, file, &exit_button, dialog_title))
    {
        form_alert(1, "[1][The GEM file selector could not be opened.][OK]");
        return -1;
    }
    if (exit_button != FSEL_OK)
    {
        return 0;
    }
    if (!file[0])
    {
        return 0;
    }
    if (!has_dat_extension(file))
    {
        form_alert(1, "[1][Please select a .DAT file.][OK]");
        return -1;
    }
    if (!combine_selected_path(path, file))
    {
        return -1;
    }

    *selected_path = selected_file_path;
    return 1;
}
