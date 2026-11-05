#include <stdio.h>

#include "app_logger.h"

/** Compile-time switch that enables or disables file logging. */
#define APP_LOGGING_ENABLED 0
/** Filename of the application event log, stored in the current directory. */
static const char APP_LOG_FILE_NAME[] = "BALLERED.LOG";
/** Open application log stream, or null when logging is unavailable. */
static FILE *log_file;

/**
 * Opens the application log in append mode.
 *
 * @return Non-zero if the log file was opened successfully.
 */
short app_logger_open(void)
{
    if (!APP_LOGGING_ENABLED)
    {
        return 1;
    }

    if (log_file)
    {
        return 1;
    }

    log_file = fopen(APP_LOG_FILE_NAME, "a");
    if (!log_file)
    {
        return 0;
    }

    if (!app_logger_write("=== Ballerburgen Editor session started ==="))
    {
        fclose(log_file);
        log_file = 0;
        return 0;
    }

    return 1;
}

/**
 * Writes one event line to the log and flushes it to disk.
 *
 * @param message Event description to record.
 * @return Non-zero if the message was written successfully.
 */
short app_logger_write(const char *message)
{
    if (!APP_LOGGING_ENABLED)
    {
        return 1;
    }

    if (!log_file || !message)
    {
        return 0;
    }

    if (fprintf(log_file, "%s\n", message) < 0 || fflush(log_file) != 0)
    {
        return 0;
    }

    return 1;
}

/**
 * Writes the final session event and closes the log file.
 */
void app_logger_close(void)
{
    if (!APP_LOGGING_ENABLED)
    {
        return;
    }

    if (log_file)
    {
        app_logger_write("=== Ballerburgen Editor session ended ===");
        fclose(log_file);
        log_file = 0;
    }
}
