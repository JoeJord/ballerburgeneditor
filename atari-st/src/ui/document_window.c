#include <gem.h>
#include <stdio.h>
#include <string.h>

#include "BALLERED.h"
#include "app_alerts.h"
#include "app_logger.h"
#include "app_resources.h"
#include "castle_data.h"
#include "document_window.h"
#include "toolbar.h"

/** Maximum number of document windows this module can manage simultaneously. */
#define MAX_DOCUMENT_WINDOWS 8

/** Open AES window handles; zero marks an unused slot. */
static short window_handles[MAX_DOCUMENT_WINDOWS];
/** Parsed castle data owned by each open document window. */
static CastleDataDocument window_documents[MAX_DOCUMENT_WINDOWS];
/** Whether a document was loaded from a castle data file. */
static short window_has_castle_document[MAX_DOCUMENT_WINDOWS];
/** Normal window bounds restored after unmaximizing each document window. */
static GRECT restore_bounds[MAX_DOCUMENT_WINDOWS];
/** Whether each document window is currently maximized. */
static short window_maximized[MAX_DOCUMENT_WINDOWS];
/** VDI virtual workstation used to clear document work areas. */
static VdiHdl vdi_handle;
/** Number used to cascade newly opened windows. */
static short window_sequence;

/**
 * Opens a VDI virtual workstation for document rendering.
 *
 * @return Non-zero if the workstation is ready.
 */
static short open_virtual_workstation(void)
{
    short physical_handle;
    short character_width;
    short character_height;
    short box_width;
    short box_height;
    short work_in[11];
    short work_out[57];
    short index;

    if (vdi_handle != 0)
    {
        return 1;
    }

    physical_handle = graf_handle(
        &character_width,
        &character_height,
        &box_width,
        &box_height
    );
    work_in[0] = physical_handle;
    for (index = 1; index < 10; index++)
    {
        work_in[index] = 1;
    }
    work_in[10] = 2;
    v_opnvwk(work_in, &vdi_handle, work_out);

    if (vdi_handle == 0)
    {
        app_alerts_show(APP_ALERT_VDI_OPEN_FAILED);
        return 0;
    }

    vsf_interior(vdi_handle, FIS_SOLID);
    vsf_color(vdi_handle, 0);
    return 1;
}

/**
 * Finds the first unused document window slot.
 *
 * @return An available slot index, or -1 if all slots are occupied.
 */
static short find_free_window_slot(void)
{
    short index;

    for (index = 0; index < MAX_DOCUMENT_WINDOWS; index++)
    {
        if (window_handles[index] == 0)
        {
            return index;
        }
    }

    return -1;
}

/**
 * Finds the slot associated with an AES window handle.
 *
 * @param handle AES window handle.
 * @return The matching slot index, or -1 if the window is not managed here.
 */
static short find_window_slot(short handle)
{
    short index;

    for (index = 0; index < MAX_DOCUMENT_WINDOWS; index++)
    {
        if (window_handles[index] != 0 && window_handles[index] == handle)
        {
            return index;
        }
    }

    return -1;
}

/**
 * Draws the blank white work area in the portions currently visible.
 *
 * @param handle AES window handle.
 * @param redraw_area Area requested by the AES redraw message.
 */
static void redraw_window(short handle, const GRECT *redraw_area)
{
    short slot;
    short work_x;
    short work_y;
    short work_width;
    short work_height;
    short visible_x;
    short visible_y;
    short visible_width;
    short visible_height;
    short left;
    short top;
    short right;
    short bottom;
    short clip[4];
    short fill[4];
    char status_text[40];

    slot = find_window_slot(handle);

    if (!wind_update(BEG_UPDATE))
    {
        return;
    }

    if (wind_get(handle, WF_WORKXYWH, &work_x, &work_y, &work_width, &work_height)
        && wind_get(
            handle,
            WF_FIRSTXYWH,
            &visible_x,
            &visible_y,
            &visible_width,
            &visible_height
        ))
    {
        while (visible_width > 0 && visible_height > 0)
        {
            left = visible_x > redraw_area->g_x ? visible_x : redraw_area->g_x;
            top = visible_y > redraw_area->g_y ? visible_y : redraw_area->g_y;
            right = visible_x + visible_width - 1;
            if (right > redraw_area->g_x + redraw_area->g_w - 1)
            {
                right = redraw_area->g_x + redraw_area->g_w - 1;
            }
            if (right > work_x + work_width - 1)
            {
                right = work_x + work_width - 1;
            }
            bottom = visible_y + visible_height - 1;
            if (bottom > redraw_area->g_y + redraw_area->g_h - 1)
            {
                bottom = redraw_area->g_y + redraw_area->g_h - 1;
            }
            if (bottom > work_y + work_height - 1)
            {
                bottom = work_y + work_height - 1;
            }

            if (right >= left && bottom >= top)
            {
                clip[0] = left;
                clip[1] = top;
                clip[2] = right;
                clip[3] = bottom;
                fill[0] = work_x;
                fill[1] = work_y;
                fill[2] = work_x + work_width - 1;
                fill[3] = work_y + work_height - 1;
                vs_clip(vdi_handle, 1, clip);
                vr_recfl(vdi_handle, fill);
                if (slot >= 0 && window_has_castle_document[slot])
                {
                    if (window_documents[slot].castle_count == 0)
                    {
                        strcpy(status_text, "No castles in this file.");
                    }
                    else
                    {
                        sprintf(
                            status_text,
                            "Castles loaded: %lu",
                            (unsigned long)window_documents[slot].castle_count
                        );
                    }
                    vst_color(vdi_handle, 1);
                    v_gtext(vdi_handle, work_x + 8, work_y + 16, status_text);
                }
            }

            if (!wind_get(
                handle,
                WF_NEXTXYWH,
                &visible_x,
                &visible_y,
                &visible_width,
                &visible_height
            ))
            {
                break;
            }
        }
        vs_clip_off(vdi_handle);
    }

    wind_update(END_UPDATE);
}

/**
 * Closes and deletes a managed window.
 *
 * @param slot Window handle slot.
 */
static short close_window(short slot)
{
    short handle;

    handle = window_handles[slot];
    if (!wind_close(handle))
    {
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_CLOSE_FAILED);
        return 0;
    }
    if (!wind_delete(handle))
    {
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_DELETE_FAILED);
        return 0;
    }

    window_handles[slot] = 0;
    castle_data_document_destroy(&window_documents[slot]);
    window_has_castle_document[slot] = 0;
    window_maximized[slot] = 0;
    app_logger_write("Document window closed.");
    return 1;
}

/**
 * Creates a document window with the supplied title.
 *
 * @param title Window title.
 * @return Non-zero if the window was created and opened successfully.
 */
static short create_document_window(
    const char *title,
    CastleDataDocument *document
)
{
    short slot;
    short desktop_x;
    short desktop_y;
    short desktop_width;
    short desktop_height;
    short x;
    short y;
    short width;
    short height;
    short handle;

    slot = find_free_window_slot();
    if (slot < 0)
    {
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_LIMIT_REACHED);
        return 0;
    }
    if (!open_virtual_workstation())
    {
        return 0;
    }
    if (!wind_get(0, WF_WORKXYWH, &desktop_x, &desktop_y, &desktop_width, &desktop_height))
    {
        app_alerts_show(APP_ALERT_DESKTOP_SIZE_FAILED);
        return 0;
    }

    desktop_y += toolbar_get_height();
    desktop_height -= toolbar_get_height();
    if (desktop_height <= 0)
    {
        app_alerts_show(APP_ALERT_DESKTOP_SIZE_FAILED);
        return 0;
    }

    width = desktop_width * 3 / 4;
    height = desktop_height * 3 / 4;
    x = desktop_x + (window_sequence % 5) * 20;
    y = desktop_y + (window_sequence % 5) * 20;
    window_sequence++;
    if (x + width > desktop_x + desktop_width)
    {
        x = desktop_x;
    }
    if (y + height > desktop_y + desktop_height)
    {
        y = desktop_y;
    }

    handle = wind_create(
        NAME | CLOSER | FULLER | MOVER | SIZER,
        desktop_x,
        desktop_y,
        desktop_width,
        desktop_height
    );
    if (handle <= 0)
    {
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_CREATE_FAILED);
        return 0;
    }

    if (!wind_set_str(handle, WF_NAME, title))
    {
        wind_delete(handle);
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_TITLE_FAILED);
        return 0;
    }
    if (!wind_open(handle, x, y, width, height))
    {
        wind_delete(handle);
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_OPEN_FAILED);
        return 0;
    }

    window_handles[slot] = handle;
    if (document)
    {
        window_documents[slot] = *document;
        document->castles = 0;
        document->castle_count = 0;
        window_has_castle_document[slot] = 1;
    }
    else
    {
        window_has_castle_document[slot] = 0;
    }
    restore_bounds[slot].g_x = x;
    restore_bounds[slot].g_y = y;
    restore_bounds[slot].g_w = width;
    restore_bounds[slot].g_h = height;
    window_maximized[slot] = 0;
    app_logger_write("Document window created.");
    return 1;
}

/**
 * Creates and opens a new blank document window.
 *
 * @return Non-zero if the window was created and opened successfully.
 */
short document_window_create(void)
{
    const char *title;

    app_logger_write("Creating an untitled document window.");
    title = app_resources_get_string(MSG_DOC_UNTITLED);
    if (!title)
    {
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_TITLE_MISSING);
        return 0;
    }

    return create_document_window(title, 0);
}

/**
 * Parses a DAT file and opens its castle collection in a document window.
 *
 * @param path Full path to the selected file.
 * @return Non-zero if the document window was created successfully.
 */
short document_window_open_file(const char *path)
{
    CastleDataDocument document;
    CastleDataReadResult read_result;
    size_t error_token;
    const char *title;
    char log_message[96];

    app_logger_write("Opening selected DAT file.");
    if (!path)
    {
        app_logger_write("Selected DAT file path is null.");
        app_alerts_show(APP_ALERT_SELECTED_PATH_INVALID);
        return 0;
    }

    document.castles = 0;
    document.castle_count = 0;
    error_token = 0;
    read_result = castle_data_read(path, &document, &error_token);
    if (read_result != CASTLE_DATA_READ_OK)
    {
        if (read_result == CASTLE_DATA_READ_FORMAT_ERROR)
        {
            sprintf(
                log_message,
                "Invalid castle data at numeric value %lu.",
                (unsigned long)error_token
            );
            app_logger_write(log_message);
            app_alerts_show(APP_ALERT_DAT_FORMAT_INVALID);
        }
        else if (read_result == CASTLE_DATA_READ_OUT_OF_MEMORY)
        {
            app_logger_write("Not enough memory to load castle data.");
            app_alerts_show(APP_ALERT_DAT_MEMORY_EXHAUSTED);
        }
        else
        {
            app_logger_write("Unable to read the selected castle data file.");
            app_alerts_show(APP_ALERT_SELECTED_FILE_OPEN_FAILED);
        }
        return 0;
    }

    title = strrchr(path, '\\');
    if (!title)
    {
        title = strrchr(path, '/');
    }
    title = title ? title + 1 : path;
    sprintf(
        log_message,
        "Loaded %lu castles from the selected DAT file.",
        (unsigned long)document.castle_count
    );
    app_logger_write(log_message);
    if (!create_document_window(title, &document))
    {
        castle_data_document_destroy(&document);
        return 0;
    }
    return 1;
}

/**
 * Closes the topmost document window.
 *
 * @return Non-zero if a document window was closed.
 */
short document_window_close_top(void)
{
    short top_handle;
    short unused_1;
    short unused_2;
    short unused_3;
    short slot;

    if (!wind_get(
        0,
        WF_TOP,
        &top_handle,
        &unused_1,
        &unused_2,
        &unused_3
    ))
    {
        app_alerts_show(APP_ALERT_TOP_DOCUMENT_WINDOW_MISSING);
        return 0;
    }

    slot = find_window_slot(top_handle);
    if (slot < 0)
    {
        return 0;
    }

    return close_window(slot);
}

/**
 * Checks whether at least one document window is open.
 *
 * @return Non-zero if a document window is open.
 */
short document_window_has_open_windows(void)
{
    short index;

    for (index = 0; index < MAX_DOCUMENT_WINDOWS; index++)
    {
        if (window_handles[index] != 0)
        {
            return 1;
        }
    }

    return 0;
}

/**
 * Handles window-related messages from the AES event loop.
 *
 * @param message Eight-word AES message buffer.
 */
void document_window_handle_message(const short message[8])
{
    short slot;
    GRECT redraw_area;

    slot = find_window_slot(message[3]);
    if (slot < 0)
    {
        return;
    }

    switch (message[0])
    {
        case WM_REDRAW:
            redraw_area.g_x = message[4];
            redraw_area.g_y = message[5];
            redraw_area.g_w = message[6];
            redraw_area.g_h = message[7];
            redraw_window(window_handles[slot], &redraw_area);
            break;

        case WM_CLOSED:
            close_window(slot);
            break;

        case WM_TOPPED:
            wind_set(window_handles[slot], WF_TOP, 0, 0, 0, 0);
            app_logger_write("Document window brought to front.");
            break;

        case WM_FULLED:
            if (window_maximized[slot])
            {
                if (!wind_set_grect(
                    window_handles[slot],
                    WF_CURRXYWH,
                    &restore_bounds[slot]
                ))
                {
                    app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_RESTORE_FAILED);
                }
                else
                {
                    window_maximized[slot] = 0;
                    app_logger_write("Document window restored.");
                }
            }
            else
            {
                GRECT current_bounds;
                GRECT full_bounds;

                if (!wind_get_grect(
                    window_handles[slot],
                    WF_CURRXYWH,
                    &current_bounds
                )
                    || !wind_get_grect(
                        window_handles[slot],
                        WF_FULLXYWH,
                        &full_bounds
                    ))
                {
                    app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_MAXIMIZE_FAILED);
                }
                else if (!wind_set_grect(
                    window_handles[slot],
                    WF_CURRXYWH,
                    &full_bounds
                ))
                {
                    app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_MAXIMIZE_FAILED);
                }
                else
                {
                    restore_bounds[slot] = current_bounds;
                    window_maximized[slot] = 1;
                    app_logger_write("Document window maximized.");
                }
            }
            break;

        case WM_SIZED:
        case WM_MOVED:
            if (wind_set(
                window_handles[slot],
                WF_CURRXYWH,
                message[4],
                message[5],
                message[6],
                message[7]
            ) && !window_maximized[slot])
            {
                restore_bounds[slot].g_x = message[4];
                restore_bounds[slot].g_y = message[5];
                restore_bounds[slot].g_w = message[6];
                restore_bounds[slot].g_h = message[7];
                app_logger_write(
                    message[0] == WM_SIZED
                        ? "Document window resized."
                        : "Document window moved."
                );
            }
            break;
    }
}

/**
 * Closes all document windows and releases the virtual workstation.
 */
void document_window_shutdown(void)
{
    short index;

    for (index = 0; index < MAX_DOCUMENT_WINDOWS; index++)
    {
        if (window_handles[index] != 0)
        {
            close_window(index);
        }
        castle_data_document_destroy(&window_documents[index]);
        window_has_castle_document[index] = 0;
    }

    if (vdi_handle != 0)
    {
        v_clsvwk(vdi_handle);
        vdi_handle = 0;
    }
}
