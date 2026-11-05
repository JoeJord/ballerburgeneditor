#ifndef BALLERBURGENEDITOR_ATARI_ST_APP_LOGGER_H
#define BALLERBURGENEDITOR_ATARI_ST_APP_LOGGER_H

/**
 * Opens the application log in append mode.
 *
 * @return Non-zero if the log file was opened successfully.
 */
short app_logger_open(void);

/**
 * Writes one event line to the log and flushes it to disk.
 *
 * @param message Event description to record.
 * @return Non-zero if the message was written successfully.
 */
short app_logger_write(const char *message);

/**
 * Writes the final session event and closes the log file.
 */
void app_logger_close(void);

#endif
