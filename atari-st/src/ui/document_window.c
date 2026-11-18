#include <gem.h>
#include <limits.h>
#include <osbind.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "BALLERED.h"
#include "app_alerts.h"
#include "app_logger.h"
#include "app_resources.h"
#include "castle_data.h"
#include "document_window.h"
#include "file_open.h"
#include "toolbar.h"

/** AES has seven application window handles; one is used by the toolbar. */
#define MAX_DOCUMENT_WINDOWS 6
/** Maximum formatted window-title length, including the terminating NUL. */
#define CASTLE_TITLE_BUFFER_SIZE 128
/** Bounds resource rendering for malformed or unusually large DAT values. */
#define MAX_RESOURCE_SPRITES_PER_STORE 8192
/** Width of the original game's wind-vane clearing rectangle. */
#define WIND_VANE_CLEAR_WIDTH 20
/** Height of the original game's wind-vane clearing rectangle. */
#define WIND_VANE_CLEAR_HEIGHT 15
/** Horizontal flag-tip offset produced by the fixed wind strength. */
#define WIND_VANE_FLAG_TIP_X_OFFSET 8
/** Vertical flag-tip offset produced by the fixed wind strength. */
#define WIND_VANE_FLAG_TIP_Y_OFFSET 8
/** Lower flag attachment offset from the vane baseline. */
#define WIND_VANE_FLAG_LOWER_Y_OFFSET 11

enum
{
    RESOURCE_PLACEHOLDER_NONE = 0, /**< Draw occupied resource sprites normally. */
    RESOURCE_PLACEHOLDER_MONEY = 1, /**< Draw an empty sack as an outer contour. */
    RESOURCE_PLACEHOLDER_POWDER = 2, /**< Draw an empty barrel as sparse pixels. */
    RESOURCE_PLACEHOLDER_BALL = 3 /**< Draw an empty cannonball as a circle. */
};

/**
 * Types of selectable castle objects.
 */
enum
{
    CASTLE_OBJECT_NONE = 0, /**< No object selected. */
    CASTLE_OBJECT_THRONE = 1, /**< King's throne. */
    CASTLE_OBJECT_CANNON = 2, /**< Cannon position. */
    CASTLE_OBJECT_WIND_VANE = 3, /**< Wind vane. */
    CASTLE_OBJECT_MONEY = 4, /**< Money storage area. */
    CASTLE_OBJECT_POWDER = 5, /**< Powder storage area. */
    CASTLE_OBJECT_CANNONBALLS = 6, /**< Cannonball storage area. */
    CASTLE_OBJECT_WALL = 7, /**< Wall polygon. */
    CASTLE_OBJECT_ROOF = 8 /**< Roof polygon. */
};

/** Original king sprite drawing stream from BALLER2.C. */
static const short king_sprite[] = {
    -2, 2, 2, 8, 3, 5, 12, 8, 12, 12, 16, 12, 17, 14, 15, 15, 15, 17,
    17, 17, 16, 21, 11, 24, 11, 21, 3, -9,
    -4, 3, 0, 5, 2, 24, 2, 26, 0, -9,
    19, 4, 18, 9, -9, 10, 4, 11, 9, -9, 14, 4, 15, 4, -9,
    8, 10, 11, 11, -9, 21, 10, 18, 11, -9, 14, 17, 15, 17, -9,
    13, 19, 13, 19, -9, 16, 19, 16, 19, -9,
    12, 22, 12, 21, 15, 21, 14, 22, 17, 21, 17, 22, -9,
    6, 13, 6, 17, 5, 16, 5, 17, 7, 17, 7, 16, -9, -1
};
/** Original cannon sprite drawing stream from BALLER2.C. */
static const short cannon_sprite[] = {
    -2, 1, 1, 1, 0, 3, 3, 5, 0, 3, 3, 3, 11, 2, 11, 2, 9, 4, 9, 4,
    11, 3, 11, 3, 8, 0, 5, 3, 8, 6, 5, 3, 8, 3, 3, -9,
    11, 0, 13, 2, 15, 0, 16, 0, 17, 1, 17, 2, 15, 4, 19, 8, 16, 11,
    8, 3, -9, -1
};
/** Original money sack sprite drawing stream from BALLER2.C. */
static const short money_sprite[] = {
    -4, 1, 1, 1, 4, 2, 5, 2, 0, 3, 0, 3, 8, 2, 8, 4, 8, 3, 8, 3,
    6, 4, 6, 4, 0, 5, 0, 5, 6, 6, 5, 6, 1, -9, -1
};
/** Original powder barrel sprite drawing stream from BALLER2.C. */
static const short powder_sprite[] = {
    -4, 2, 0, 6, 0, 8, 3, 8, 5, 6, 8, 2, 8, 0, 5, 0, 3, 2, 0, 3, 0,
    3, 3, 2, 4, 3, 5, 3, 8, 5, 8, 5, 5, 6, 4, 5, 3, 5, 1, -9, -1
};
/** Original cannonball sprite drawing stream from BALLER2.C. */
static const short cannonball_sprite[] = {
    -2, 1, 1, 1, 0, 0, 1, 0, 3, 1, 4, 3, 4, 4, 3, 4, 1, 3, 0, -9, -1
};

/** Open AES window handles; zero marks an unused slot. */
static short window_handles[MAX_DOCUMENT_WINDOWS];
/** Parsed castle data owned by each open document window. */
static CastleDataDocument window_documents[MAX_DOCUMENT_WINDOWS];
/** Whether a document was loaded from a castle data file. */
static short window_has_castle_document[MAX_DOCUMENT_WINDOWS];
/** Index of the castle currently shown in each document window. */
static size_t window_selected_castles[MAX_DOCUMENT_WINDOWS];
/** Stable, per-window title buffers passed to AES for WF_NAME. */
static char window_titles[MAX_DOCUMENT_WINDOWS][CASTLE_TITLE_BUFFER_SIZE];
/** Basename of the DAT file associated with each document window. */
static char window_file_names[MAX_DOCUMENT_WINDOWS][CASTLE_TITLE_BUFFER_SIZE];
/** Whether each document has an associated file path. */
static short window_has_file_path[MAX_DOCUMENT_WINDOWS];
/** Normal window bounds restored after unmaximizing each document window. */
static GRECT restore_bounds[MAX_DOCUMENT_WINDOWS];
/** Whether each document window is currently maximized. */
static short window_maximized[MAX_DOCUMENT_WINDOWS];
/** Whether each document has unsaved changes. */
static short window_modified[MAX_DOCUMENT_WINDOWS];
/** Type of selected object in each document window. */
static short window_selected_object_type[MAX_DOCUMENT_WINDOWS];
/** Index of selected object (for cannons, polygons, etc.) in each document window. */
static size_t window_selected_object_index[MAX_DOCUMENT_WINDOWS];
/** Whether an object is currently being dragged in each document window. */
static short window_dragging[MAX_DOCUMENT_WINDOWS];
/** Mouse X position where drag started in each document window. */
static short window_drag_start_x[MAX_DOCUMENT_WINDOWS];
/** Mouse Y position where drag started in each document window. */
static short window_drag_start_y[MAX_DOCUMENT_WINDOWS];
/** Original X coordinate of the dragged object in each document window. */
static int window_drag_original_x[MAX_DOCUMENT_WINDOWS];
/** Original Y coordinate of the dragged object in each document window. */
static int window_drag_original_y[MAX_DOCUMENT_WINDOWS];
/** Original polygon points for the currently dragged polygon (allocated dynamically). */
static CastleDataPoint *window_drag_original_polygon[MAX_DOCUMENT_WINDOWS];
/** Number of points in the currently dragged polygon. */
static size_t window_drag_polygon_point_count[MAX_DOCUMENT_WINDOWS];
/** VDI virtual workstation used to clear document work areas. */
static VdiHdl vdi_handle;
/** Number used to cascade newly opened windows. */
static short window_sequence;
/** Most recently activated document window, excluding the toolbar window. */
static short active_document_handle;

/**
 * Finds the slot associated with an AES window handle.
 *
 * @param handle AES window handle.
 * @return The matching slot index, or -1 if the window is not managed here.
 */
static short find_window_slot(short handle);
static short find_active_document(short *handle);
static void draw_sprite(
    long long origin_x,
    long long baseline_y,
    const short *drawing,
    short outline_only,
    short close_outline
);
static void draw_resource_placeholder(
    short placeholder_type,
    long long origin_x,
    long long baseline_y
);
static void draw_white_rectangle(
    long long left,
    long long top,
    long long width,
    long long height
);
static void draw_wind_vane(
    CastleDataPoint position,
    long long origin_x,
    long long baseline_y
);
static void draw_castle_objects(
    const CastleDataCastle *castle,
    long long origin_x,
    long long baseline_y
);
static void redraw_window(short handle, const GRECT *redraw_area);
static short point_in_rectangle(
    long long point_x,
    long long point_y,
    long long rect_x,
    long long rect_y,
    long long rect_width,
    long long rect_height
);
static short point_in_polygon(
    long long point_x,
    long long point_y,
    const CastleDataPolygon *polygon,
    long long origin_x,
    long long baseline_y
);
static void handle_object_click(
    short slot,
    short mouse_x,
    short mouse_y,
    short work_x,
    short work_y,
    short work_width,
    short work_height
);
static void draw_selection_highlight(
    short slot,
    const CastleDataCastle *castle,
    long long origin_x,
    long long baseline_y
);
static short update_object_position(
    short slot,
    short mouse_x,
    short mouse_y,
    short work_x,
    short work_y,
    short work_width,
    short work_height
);

/**
 * Appends text to a bounded output buffer.
 *
 * @param output Destination buffer.
 * @param capacity Destination capacity including the terminating NUL.
 * @param length Current output length; updated on success.
 * @param text Text to append.
 * @return Non-zero if the complete text was appended.
 */
static short append_title_text(
    char *output,
    size_t capacity,
    size_t *length,
    const char *text
)
{
    size_t text_length;

    text_length = strlen(text);
    if (text_length >= capacity - *length)
    {
        return 0;
    }

    memcpy(output + *length, text, text_length);
    *length += text_length;
    output[*length] = '\0';
    return 1;
}

/**
 * Formats the castle title resource's positional placeholders.
 *
 * @param slot Window slot index, or -1 to skip the modification indicator.
 * @param file_name Basename of the DAT file.
 * @param castle_number One-based current castle number, or zero for no castles.
 * @param castle_count Number of castles in the document.
 * @param output Receives the formatted title.
 * @param output_capacity Capacity of the output buffer.
 * @return Non-zero if the resource string was available and fit in the buffer.
 */
static short format_castle_title(
    short slot,
    const char *file_name,
    size_t castle_number,
    size_t castle_count,
    char *output,
    size_t output_capacity
)
{
    const char *template_text;
    size_t output_length;
    char number[12];

    template_text = app_resources_get_string(MSG_CASTLE_NO);
    if (!template_text || output_capacity == 0)
    {
        return 0;
    }

    output[0] = '\0';
    output_length = 0;
    if (slot >= 0 && window_modified[slot])
    {
        if (!append_title_text(output, output_capacity, &output_length, "* "))
        {
            return 0;
        }
    }
    if (!append_title_text(output, output_capacity, &output_length, file_name)
        || !append_title_text(output, output_capacity, &output_length, " - "))
    {
        return 0;
    }

    while (*template_text)
    {
        if (strncmp(template_text, "%1$d", 4) == 0)
        {
            sprintf(number, "%lu", (unsigned long)castle_number);
            if (!append_title_text(output, output_capacity, &output_length, number))
            {
                return 0;
            }
            template_text += 4;
        }
        else if (strncmp(template_text, "%2$d", 4) == 0)
        {
            sprintf(number, "%lu", (unsigned long)castle_count);
            if (!append_title_text(output, output_capacity, &output_length, number))
            {
                return 0;
            }
            template_text += 4;
        }
        else
        {
            char character[2];

            character[0] = *template_text++;
            character[1] = '\0';
            if (!append_title_text(output, output_capacity, &output_length, character))
            {
                return 0;
            }
        }
    }

    return 1;
}

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
    vsl_color(vdi_handle, 1);
    vsl_type(vdi_handle, 1);
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
 * Draws one original-game sprite stream at the supplied local origin.
 *
 * @param origin_x Screen X coordinate of the sprite's local origin.
 * @param baseline_y Screen Y coordinate of its local baseline.
 * @param drawing Ordered sprite commands terminated by -1.
 * @param outline_only Whether to draw only the sprite outline.
 * @param close_outline Whether to connect the outline's last point to its first.
 */
static void draw_sprite(
    long long origin_x,
    long long baseline_y,
    const short *drawing,
    short outline_only,
    short close_outline
)
{
    short points[128];
    short fill_mode;
    short point_count;
    short local_x;
    short local_y;
    short valid_polygon;
    short *point;
    long long screen_x;
    long long screen_y;

    fill_mode = outline_only ? 0 : 1;
    vswr_mode(vdi_handle, MD_REPLACE);
    vsf_color(vdi_handle, 1);
    vsl_color(vdi_handle, 1);
    vsf_interior(vdi_handle, FIS_PATTERN);
    vsf_style(vdi_handle, 9);
    vsl_type(vdi_handle, 1);

    while (*drawing != CASTLE_DATA_RECORD_TERMINATOR)
    {
        if (*drawing == CASTLE_DATA_FILL_STYLE_MARKER)
        {
            short interior;
            short style;

            drawing++;
            interior = *drawing++;
            style = *drawing++;
            if (!outline_only)
            {
                vsf_interior(vdi_handle, interior);
                vsf_style(vdi_handle, style);
            }
            continue;
        }
        if (*drawing == CASTLE_DATA_DRAW_MODE_MARKER)
        {
            if (!outline_only)
            {
                fill_mode = !fill_mode;
            }
            drawing++;
            continue;
        }

        point_count = 0;
        valid_polygon = 1;
        point = points;
        while (*drawing != CASTLE_DATA_POLYGON_TERMINATOR
            && *drawing != CASTLE_DATA_RECORD_TERMINATOR)
        {
            if (*drawing < 0 || point_count >= 63)
            {
                valid_polygon = 0;
                break;
            }
            local_x = *drawing++;
            if (*drawing < 0)
            {
                valid_polygon = 0;
                break;
            }
            local_y = *drawing++;
            screen_x = origin_x + local_x;
            screen_y = baseline_y - local_y;
            if (screen_x < SHRT_MIN || screen_x > SHRT_MAX
                || screen_y < SHRT_MIN || screen_y > SHRT_MAX)
            {
                valid_polygon = 0;
            }
            *point++ = screen_x >= SHRT_MIN && screen_x <= SHRT_MAX
                ? (short)screen_x
                : 0;
            *point++ = screen_y >= SHRT_MIN && screen_y <= SHRT_MAX
                ? (short)screen_y
                : 0;
            point_count++;
        }

        if (*drawing == CASTLE_DATA_POLYGON_TERMINATOR)
        {
            drawing++;
        }
        else
        {
            valid_polygon = 0;
        }

        if (!valid_polygon || point_count < (fill_mode ? 3 : 2))
        {
            app_logger_write("Invalid original-game sprite polygon ignored.");
            if (*drawing == CASTLE_DATA_RECORD_TERMINATOR)
            {
                break;
            }
            continue;
        }

        if (fill_mode)
        {
            points[point_count * 2] = points[0];
            points[point_count * 2 + 1] = points[1];
            v_fillarea(vdi_handle, point_count + 1, points);
        }
        else
        {
            if (close_outline)
            {
                points[point_count * 2] = points[0];
                points[point_count * 2 + 1] = points[1];
                v_pline(vdi_handle, point_count + 1, points);
            }
            else
            {
                v_pline(vdi_handle, point_count, points);
            }
        }
    }

    vswr_mode(vdi_handle, MD_REPLACE);
    vsf_interior(vdi_handle, FIS_SOLID);
    vsf_color(vdi_handle, 0);
    vsl_type(vdi_handle, 1);
}

/**
 * Draws a resource-specific empty-slot symbol.
 *
 * @param placeholder_type Resource symbol to draw.
 * @param origin_x Screen X coordinate of the sprite's local origin.
 * @param baseline_y Screen Y coordinate of the sprite's local baseline.
 */
static void draw_resource_placeholder(
    short placeholder_type,
    long long origin_x,
    long long baseline_y
)
{
    static const short money_sack_outline[] = {
        2, 8, 4, 8, 4, 7, 5, 6, 6, 4, 6, 2, 5, 1, 4, 0,
        2, 0, 1, 1, 0, 2, 0, 4, 1, 6, 2, 7, 2, 8
    };
    static const char powder_barrel_pixels[9][10] = {
        "..#####..",
        ".#.....#.",
        "#.......#",
        "#.......#",
        "#.......#",
        "#.......#",
        "#.......#",
        ".#.....#.",
        "..#####.."
    };
    short points[30];
    short rectangle[4];
    short index;
    short row;
    short column;
    long long screen_x;
    long long screen_y;

    vswr_mode(vdi_handle, MD_REPLACE);
    vsf_color(vdi_handle, 1);
    vsl_color(vdi_handle, 1);
    vsl_type(vdi_handle, 1);

    if (placeholder_type == RESOURCE_PLACEHOLDER_MONEY)
    {
        for (index = 0; index < 15; index++)
        {
            screen_x = origin_x + money_sack_outline[index * 2];
            screen_y = baseline_y - money_sack_outline[index * 2 + 1];
            if (screen_x < SHRT_MIN || screen_x > SHRT_MAX
                || screen_y < SHRT_MIN || screen_y > SHRT_MAX)
            {
                vswr_mode(vdi_handle, MD_REPLACE);
                vsf_interior(vdi_handle, FIS_SOLID);
                vsf_color(vdi_handle, 0);
                return;
            }
            points[index * 2] = (short)screen_x;
            points[index * 2 + 1] = (short)screen_y;
        }
        v_pline(vdi_handle, 15, points);
        vswr_mode(vdi_handle, MD_REPLACE);
        vsf_interior(vdi_handle, FIS_SOLID);
        vsf_color(vdi_handle, 0);
        return;
    }

    if (placeholder_type == RESOURCE_PLACEHOLDER_POWDER)
    {
        for (row = 0; row < 9; row++)
        {
            for (column = 0; column < 9; column++)
            {
                if (powder_barrel_pixels[row][column] != '#')
                {
                    continue;
                }
                screen_x = origin_x + column;
                screen_y = baseline_y - row;
                if (screen_x < SHRT_MIN || screen_x > SHRT_MAX
                    || screen_y < SHRT_MIN || screen_y > SHRT_MAX)
                {
                    continue;
                }
                rectangle[0] = (short)screen_x;
                rectangle[1] = (short)screen_y;
                rectangle[2] = (short)screen_x;
                rectangle[3] = (short)screen_y;
                vr_recfl(vdi_handle, rectangle);
            }
        }
    }

    vswr_mode(vdi_handle, MD_REPLACE);
    vsf_interior(vdi_handle, FIS_SOLID);
    vsf_color(vdi_handle, 0);
}

/**
 * Paints a bounded white rectangle behind a castle object.
 *
 * @param left Screen X coordinate of the rectangle's left edge.
 * @param top Screen Y coordinate of its top edge.
 * @param width Rectangle width in pixels.
 * @param height Rectangle height in pixels.
 */
static void draw_white_rectangle(
    long long left,
    long long top,
    long long width,
    long long height
)
{
    long long right;
    long long bottom;
    short rectangle[4];

    if (width <= 0 || height <= 0)
    {
        return;
    }

    right = left + width - 1;
    bottom = top + height - 1;
    if (left < SHRT_MIN || left > SHRT_MAX
        || top < SHRT_MIN || top > SHRT_MAX
        || right < SHRT_MIN || right > SHRT_MAX
        || bottom < SHRT_MIN || bottom > SHRT_MAX)
    {
        return;
    }

    rectangle[0] = (short)left;
    rectangle[1] = (short)top;
    rectangle[2] = (short)right;
    rectangle[3] = (short)bottom;
    vswr_mode(vdi_handle, MD_REPLACE);
    vsf_interior(vdi_handle, FIS_SOLID);
    vsf_color(vdi_handle, 0);
    vr_recfl(vdi_handle, rectangle);
    vswr_mode(vdi_handle, MD_REPLACE);
    vsf_interior(vdi_handle, FIS_SOLID);
    vsf_color(vdi_handle, 0);
}

/**
 * Converts a resource amount to the number of sprites used by the game.
 *
 * @param amount Initial amount from the castle record.
 * @param units_per_sprite Resource units represented by one sprite.
 * @return Number of sprites, rounded up as in the original game.
 */
static unsigned long resource_sprite_count(int amount, unsigned long units_per_sprite)
{
    if (amount <= 0)
    {
        return 0;
    }

    return ((unsigned long)amount - 1) / units_per_sprite + 1;
}

/**
 * Draws an inventory stack using the castle's original storage grid.
 *
 * @param position Storage grid's lower-left origin.
 * @param storage Storage grid dimensions in pixels.
 * @param item_count Number of inventory sprites to draw.
 * @param step_x Horizontal spacing between sprites.
 * @param step_y Vertical spacing between sprite rows.
 * @param sprite Original-game sprite command stream.
 * @param origin_x Screen X coordinate of the castle origin.
 * @param baseline_y Screen Y coordinate of the castle baseline.
 * @param placeholder_type Empty-slot symbol to draw, or NONE for no placeholders.
 */
static void draw_resource_stack(
    CastleDataPoint position,
    CastleDataSize storage,
    unsigned long item_count,
    short step_x,
    short step_y,
    const short *sprite,
    long long origin_x,
    long long baseline_y,
    short placeholder_type
)
{
    unsigned long columns;
    unsigned long rows;
    unsigned long column;
    unsigned long row;
    unsigned long drawn;

    if (storage.width <= 0 || storage.height <= 0)
    {
        return;
    }

    draw_white_rectangle(
        origin_x + position.x,
        baseline_y - position.y - storage.height + 1,
        (long long)storage.width + 1,
        (long long)storage.height + 1
    );

    columns = ((unsigned long)storage.width - 1) / (unsigned short)step_x + 1;
    rows = ((unsigned long)storage.height - 1) / (unsigned short)step_y + 1;
    if (item_count > MAX_RESOURCE_SPRITES_PER_STORE)
    {
        item_count = MAX_RESOURCE_SPRITES_PER_STORE;
    }

    drawn = 0;
    for (row = 0; row < rows && drawn < MAX_RESOURCE_SPRITES_PER_STORE; row++)
    {
        for (column = 0;
             column < columns && drawn < MAX_RESOURCE_SPRITES_PER_STORE;
             column++)
        {
            if (drawn < item_count)
            {
                draw_sprite(
                    origin_x + position.x + (long long)column * step_x,
                    baseline_y - position.y - (long long)row * step_y,
                    sprite,
                    0,
                    0
                );
            }
            else if (placeholder_type == RESOURCE_PLACEHOLDER_BALL)
            {
                draw_sprite(
                    origin_x + position.x + (long long)column * step_x,
                    baseline_y - position.y - (long long)row * step_y,
                    sprite,
                    1,
                    1
                );
            }
            else
            {
                draw_resource_placeholder(
                    placeholder_type,
                    origin_x + position.x + (long long)column * step_x,
                    baseline_y - position.y - (long long)row * step_y
                );
            }
            drawn++;
        }
    }
}

/**
 * Draws the original game's wind vane using a fixed positive wind strength.
 *
 * @param position Wind-vane position from the castle data.
 * @param origin_x Screen X coordinate of the castle origin.
 * @param baseline_y Screen Y coordinate of the castle baseline.
 */
static void draw_wind_vane(
    CastleDataPoint position,
    long long origin_x,
    long long baseline_y
)
{
    long long vane_x;
    long long vane_y;
    short mast[4];
    short flag_top[4];
    short flag_bottom[4];

    if (!castle_data_is_point_set(&position))
    {
        return;
    }

    vane_x = origin_x + position.x;
    vane_y = baseline_y - position.y;
    if (vane_x < SHRT_MIN || vane_x + WIND_VANE_FLAG_TIP_X_OFFSET > SHRT_MAX
        || vane_y - WIND_VANE_CLEAR_HEIGHT < SHRT_MIN || vane_y > SHRT_MAX)
    {
        return;
    }

    draw_white_rectangle(
        vane_x - WIND_VANE_CLEAR_WIDTH / 2,
        vane_y - WIND_VANE_CLEAR_HEIGHT,
        WIND_VANE_CLEAR_WIDTH,
        WIND_VANE_CLEAR_HEIGHT
    );

    mast[0] = (short)vane_x;
    mast[1] = (short)vane_y;
    mast[2] = (short)vane_x;
    mast[3] = (short)(vane_y - WIND_VANE_CLEAR_HEIGHT);
    flag_top[0] = (short)vane_x;
    flag_top[1] = mast[3];
    flag_top[2] = (short)(vane_x + WIND_VANE_FLAG_TIP_X_OFFSET);
    flag_top[3] = (short)(vane_y - WIND_VANE_FLAG_TIP_Y_OFFSET);
    flag_bottom[0] = (short)vane_x;
    flag_bottom[1] = (short)(vane_y - WIND_VANE_FLAG_LOWER_Y_OFFSET);
    flag_bottom[2] = flag_top[2];
    flag_bottom[3] = flag_top[3];

    vswr_mode(vdi_handle, MD_REPLACE);
    vsl_color(vdi_handle, 1);
    vsl_type(vdi_handle, 1);
    v_pline(vdi_handle, 2, mast);
    v_pline(vdi_handle, 2, flag_top);
    v_pline(vdi_handle, 2, flag_bottom);
    vswr_mode(vdi_handle, MD_REPLACE);
    vsl_type(vdi_handle, 1);
}

/**
 * Draws the king, all configured cannons, and starting resource sprites.
 *
 * @param castle Castle whose objects are drawn.
 * @param origin_x Screen X coordinate of the castle origin.
 * @param baseline_y Screen Y coordinate of the castle baseline.
 */
static void draw_castle_objects(
    const CastleDataCastle *castle,
    long long origin_x,
    long long baseline_y
)
{
    size_t index;

    if (castle_data_is_point_set(&castle->throne))
    {
        draw_white_rectangle(
            origin_x + castle->throne.x,
            baseline_y - castle->throne.y - 25,
            30,
            25
        );
        draw_sprite(
            origin_x + castle->throne.x,
            baseline_y - castle->throne.y,
            king_sprite,
            0,
            0
        );
    }
    for (index = 0; index < castle->cannon_count; index++)
    {
        if (!castle_data_is_point_set(&castle->cannons[index]))
        {
            continue;
        }
        draw_sprite(
            origin_x + castle->cannons[index].x,
            baseline_y - castle->cannons[index].y,
            cannon_sprite,
            0,
            0
        );
    }

    if (castle_data_is_point_set(&castle->money))
    {
        draw_resource_stack(
            castle->money,
            castle->money_storage,
            resource_sprite_count(castle->resources.money, 150),
            7,
            10,
            money_sprite,
            origin_x,
            baseline_y,
            RESOURCE_PLACEHOLDER_MONEY
        );
    }
    if (castle_data_is_point_set(&castle->powder))
    {
        draw_resource_stack(
            castle->powder,
            castle->powder_storage,
            resource_sprite_count(castle->resources.powder, 30),
            9,
            9,
            powder_sprite,
            origin_x,
            baseline_y,
            RESOURCE_PLACEHOLDER_POWDER
        );
    }
    if (castle_data_is_point_set(&castle->cannonballs))
    {
        draw_resource_stack(
            castle->cannonballs,
            castle->cannonball_storage,
            resource_sprite_count(castle->resources.cannonballs, 1),
            6,
            6,
            cannonball_sprite,
            origin_x,
            baseline_y,
            RESOURCE_PLACEHOLDER_BALL
        );
    }
    draw_wind_vane(castle->wind_vane, origin_x, baseline_y);
}

/**
 * Tests if a point lies within a rectangle.
 *
 * @param point_x Point X coordinate.
 * @param point_y Point Y coordinate.
 * @param rect_x Rectangle left X coordinate.
 * @param rect_y Rectangle top Y coordinate.
 * @param rect_width Rectangle width.
 * @param rect_height Rectangle height.
 * @return Non-zero if the point is inside the rectangle.
 */
static short point_in_rectangle(
    long long point_x,
    long long point_y,
    long long rect_x,
    long long rect_y,
    long long rect_width,
    long long rect_height
)
{
    if (rect_width <= 0 || rect_height <= 0)
    {
        return 0;
    }
    return point_x >= rect_x && point_x < rect_x + rect_width
        && point_y >= rect_y && point_y < rect_y + rect_height;
}

/**
 * Tests if a point lies within a polygon using ray casting.
 *
 * @param point_x Point X coordinate.
 * @param point_y Point Y coordinate.
 * @param polygon Polygon to test.
 * @param origin_x Castle origin X coordinate.
 * @param baseline_y Castle baseline Y coordinate.
 * @return Non-zero if the point is inside the polygon.
 */
static short point_in_polygon(
    long long point_x,
    long long point_y,
    const CastleDataPolygon *polygon,
    long long origin_x,
    long long baseline_y
)
{
    size_t i;
    size_t j;
    short inside;
    long long vi_x;
    long long vi_y;
    long long vj_x;
    long long vj_y;

    if (!polygon || polygon->point_count < 3)
    {
        return 0;
    }

    inside = 0;
    j = polygon->point_count - 1;
    for (i = 0; i < polygon->point_count; i++)
    {
        vi_x = origin_x + polygon->points[i].x;
        vi_y = baseline_y - polygon->points[i].y;
        vj_x = origin_x + polygon->points[j].x;
        vj_y = baseline_y - polygon->points[j].y;

        if (((vi_y > point_y) != (vj_y > point_y))
            && (point_x < (vj_x - vi_x) * (point_y - vi_y) / (vj_y - vi_y) + vi_x))
        {
            inside = !inside;
        }
        j = i;
    }

    return inside;
}

/**
 * Handles a mouse click in the document work area to select castle objects.
 *
 * @param slot Window slot index.
 * @param mouse_x Mouse X coordinate in screen space.
 * @param mouse_y Mouse Y coordinate in screen space.
 * @param work_x Work-area left coordinate.
 * @param work_y Work-area top coordinate.
 * @param work_width Work-area width.
 * @param work_height Work-area height.
 */
static void handle_object_click(
    short slot,
    short mouse_x,
    short mouse_y,
    short work_x,
    short work_y,
    short work_width,
    short work_height
)
{
    const CastleDataCastle *castle;
    long long origin_x;
    long long baseline_y;
    size_t index;
    size_t drawing_index;
    short found;

    if (!window_has_castle_document[slot]
        || window_documents[slot].castle_count == 0)
    {
        return;
    }

    castle = &window_documents[slot].castles[window_selected_castles[slot]];
    origin_x = (long long)work_x + ((long long)work_width - castle->width) / 2;
    baseline_y = (long long)work_y + work_height - 12;
    found = 0;

    /* Check wind vane (drawn last, so check first for hit detection) */
    if (!found && castle_data_is_point_set(&castle->wind_vane))
    {
        long long vane_x = origin_x + castle->wind_vane.x - WIND_VANE_CLEAR_WIDTH / 2;
        long long vane_y = baseline_y - castle->wind_vane.y - WIND_VANE_CLEAR_HEIGHT;
        if (point_in_rectangle(
            mouse_x, mouse_y,
            vane_x, vane_y,
            WIND_VANE_CLEAR_WIDTH, WIND_VANE_CLEAR_HEIGHT
        ))
        {
            window_selected_object_type[slot] = CASTLE_OBJECT_WIND_VANE;
            window_selected_object_index[slot] = 0;
            found = 1;
        }
    }

    /* Check cannonball storage area */
    if (!found && castle_data_is_point_set(&castle->cannonballs))
    {
        long long storage_x = origin_x + castle->cannonballs.x;
        long long storage_y = baseline_y - castle->cannonballs.y - castle->cannonball_storage.height + 1;
        if (point_in_rectangle(
            mouse_x, mouse_y,
            storage_x, storage_y,
            castle->cannonball_storage.width + 1,
            castle->cannonball_storage.height + 1
        ))
        {
            window_selected_object_type[slot] = CASTLE_OBJECT_CANNONBALLS;
            window_selected_object_index[slot] = 0;
            found = 1;
        }
    }

    /* Check powder storage area */
    if (!found && castle_data_is_point_set(&castle->powder))
    {
        long long storage_x = origin_x + castle->powder.x;
        long long storage_y = baseline_y - castle->powder.y - castle->powder_storage.height + 1;
        if (point_in_rectangle(
            mouse_x, mouse_y,
            storage_x, storage_y,
            castle->powder_storage.width + 1,
            castle->powder_storage.height + 1
        ))
        {
            window_selected_object_type[slot] = CASTLE_OBJECT_POWDER;
            window_selected_object_index[slot] = 0;
            found = 1;
        }
    }

    /* Check money storage area */
    if (!found && castle_data_is_point_set(&castle->money))
    {
        long long storage_x = origin_x + castle->money.x;
        long long storage_y = baseline_y - castle->money.y - castle->money_storage.height + 1;
        if (point_in_rectangle(
            mouse_x, mouse_y,
            storage_x, storage_y,
            castle->money_storage.width + 1,
            castle->money_storage.height + 1
        ))
        {
            window_selected_object_type[slot] = CASTLE_OBJECT_MONEY;
            window_selected_object_index[slot] = 0;
            found = 1;
        }
    }

    /* Check cannons */
    if (!found)
    {
        for (index = 0; index < castle->cannon_count; index++)
        {
            if (castle_data_is_point_set(&castle->cannons[index]))
            {
                /* Cannon coordinate is the lower-left corner of the sprite */
                long long cannon_x = origin_x + castle->cannons[index].x;
                long long cannon_y = baseline_y - castle->cannons[index].y - 19;
                if (point_in_rectangle(mouse_x, mouse_y, cannon_x, cannon_y, 20, 20))
                {
                    window_selected_object_type[slot] = CASTLE_OBJECT_CANNON;
                    window_selected_object_index[slot] = index;
                    found = 1;
                    break;
                }
            }
        }
    }

    /* Check throne (king) */
    if (!found && castle_data_is_point_set(&castle->throne))
    {
        long long throne_x = origin_x + castle->throne.x;
        long long throne_y = baseline_y - castle->throne.y - 25;
        if (point_in_rectangle(mouse_x, mouse_y, throne_x, throne_y, 30, 25))
        {
            window_selected_object_type[slot] = CASTLE_OBJECT_THRONE;
            window_selected_object_index[slot] = 0;
            found = 1;
        }
    }

    /* Check drawing polygons (walls and roofs) */
    if (!found)
    {
        for (drawing_index = 0; drawing_index < castle->drawing_count; drawing_index++)
        {
            const CastleDataDrawing *drawing = &castle->drawing[drawing_index];
            if (drawing->type == CASTLE_DATA_DRAW_POLYGON)
            {
                if (point_in_polygon(
                    mouse_x, mouse_y,
                    &drawing->value.polygon,
                    origin_x,
                    baseline_y
                ))
                {
                    /* Distinguish between wall and roof based on fill style */
                    /* This is a simplified approach - could be refined */
                    window_selected_object_type[slot] = CASTLE_OBJECT_WALL;
                    window_selected_object_index[slot] = drawing_index;
                    found = 1;
                    break;
                }
            }
        }
    }

    /* If no object was clicked, clear selection */
    if (!found)
    {
        window_selected_object_type[slot] = CASTLE_OBJECT_NONE;
        window_selected_object_index[slot] = 0;
        window_dragging[slot] = 0;
    }
    else
    {
        /* Initialize drag state when an object is selected */
        window_dragging[slot] = 1;
        window_drag_start_x[slot] = mouse_x;
        window_drag_start_y[slot] = mouse_y;

        /* Store original position based on object type */
        const CastleDataCastle *castle = &window_documents[slot].castles[window_selected_castles[slot]];
        switch (window_selected_object_type[slot])
        {
            case CASTLE_OBJECT_THRONE:
                window_drag_original_x[slot] = castle->throne.x;
                window_drag_original_y[slot] = castle->throne.y;
                break;
            case CASTLE_OBJECT_CANNON:
            {
                size_t cannon_index = window_selected_object_index[slot];
                if (cannon_index < castle->cannon_count)
                {
                    window_drag_original_x[slot] = castle->cannons[cannon_index].x;
                    window_drag_original_y[slot] = castle->cannons[cannon_index].y;
                }
                break;
            }
            case CASTLE_OBJECT_WIND_VANE:
                window_drag_original_x[slot] = castle->wind_vane.x;
                window_drag_original_y[slot] = castle->wind_vane.y;
                break;
            case CASTLE_OBJECT_MONEY:
                window_drag_original_x[slot] = castle->money.x;
                window_drag_original_y[slot] = castle->money.y;
                break;
            case CASTLE_OBJECT_POWDER:
                window_drag_original_x[slot] = castle->powder.x;
                window_drag_original_y[slot] = castle->powder.y;
                break;
            case CASTLE_OBJECT_CANNONBALLS:
                window_drag_original_x[slot] = castle->cannonballs.x;
                window_drag_original_y[slot] = castle->cannonballs.y;
                break;
            case CASTLE_OBJECT_WALL:
            case CASTLE_OBJECT_ROOF:
            {
                /* Store original polygon points for dragging */
                size_t polygon_index = window_selected_object_index[slot];
                if (polygon_index < castle->drawing_count
                    && castle->drawing[polygon_index].type == CASTLE_DATA_DRAW_POLYGON)
                {
                    const CastleDataPolygon *polygon = &castle->drawing[polygon_index].value.polygon;
                    window_drag_polygon_point_count[slot] = polygon->point_count;
                    window_drag_original_polygon[slot] = (CastleDataPoint *)malloc(
                        polygon->point_count * sizeof(CastleDataPoint)
                    );
                    if (window_drag_original_polygon[slot])
                    {
                        size_t i;
                        for (i = 0; i < polygon->point_count; i++)
                        {
                            window_drag_original_polygon[slot][i] = polygon->points[i];
                        }
                    }
                    else
                    {
                        window_dragging[slot] = 0;
                    }
                }
                else
                {
                    window_dragging[slot] = 0;
                }
                break;
            }
            default:
                window_dragging[slot] = 0;
                break;
        }

        /* Poll mouse position while button is held down for drag */
        if (window_dragging[slot])
        {
            short current_mouse_x;
            short current_mouse_y;
            short current_button;
            short current_key_state;

            while (window_dragging[slot])
            {
                /* Get current mouse state */
                graf_mkstate(&current_mouse_x, &current_mouse_y, &current_button, &current_key_state);

                /* Check if button is released */
                if ((current_button & 1) == 0)
                {
                    window_dragging[slot] = 0;
                    /* Free polygon memory if allocated */
                    if (window_drag_original_polygon[slot])
                    {
                        free(window_drag_original_polygon[slot]);
                        window_drag_original_polygon[slot] = NULL;
                        window_drag_polygon_point_count[slot] = 0;
                    }
                    /* Mark document as modified when drag ends */
                    window_modified[slot] = 1;
                    /* Update window title to show modification */
                    {
                        char title_buffer[CASTLE_TITLE_BUFFER_SIZE];
                        format_castle_title(
                            slot,
                            window_file_names[slot],
                            window_selected_castles[slot] + 1,
                            window_documents[slot].castle_count,
                            title_buffer,
                            CASTLE_TITLE_BUFFER_SIZE
                        );
                        wind_set_str(window_handles[slot], WF_NAME, title_buffer);
                    }
                    break;
                }

                /* Update object position if mouse moved */
                if (current_mouse_x != mouse_x || current_mouse_y != mouse_y)
                {
                    update_object_position(slot, current_mouse_x, current_mouse_y, work_x, work_y, work_width, work_height);
                    mouse_x = current_mouse_x;
                    mouse_y = current_mouse_y;
                }

                /* Small delay to prevent excessive CPU usage */
                evnt_timer(50);
            }
        }
    }

    /* Redraw the window to show selection highlight */
    {
        GRECT redraw_area;
        redraw_area.g_x = work_x;
        redraw_area.g_y = work_y;
        redraw_area.g_w = work_width;
        redraw_area.g_h = work_height;
        redraw_window(window_handles[slot], &redraw_area);
    }
}

/**
 * Draws a highlight rectangle around the selected castle object.
 *
 * @param slot Window slot index.
 * @param castle Castle being displayed.
 * @param origin_x Castle origin X coordinate.
 * @param baseline_y Castle baseline Y coordinate.
 */
static void draw_selection_highlight(
    short slot,
    const CastleDataCastle *castle,
    long long origin_x,
    long long baseline_y
)
{
    short highlight_rect[4];
    long long rect_x;
    long long rect_y;
    long long rect_width;
    long long rect_height;
    size_t index;
    short should_draw;

    if (window_selected_object_type[slot] == CASTLE_OBJECT_NONE)
    {
        return;
    }

    vswr_mode(vdi_handle, MD_XOR);
    vsl_color(vdi_handle, 1);
    vsl_type(vdi_handle, 7); /* Dotted line */
    vsl_width(vdi_handle, 1);

    should_draw = 0;

    switch (window_selected_object_type[slot])
    {
        case CASTLE_OBJECT_THRONE:
            if (castle_data_is_point_set(&castle->throne))
            {
                rect_x = origin_x + castle->throne.x - 2;
                rect_y = baseline_y - castle->throne.y - 27;
                rect_width = 34;
                rect_height = 29;
                should_draw = 1;
            }
            break;

        case CASTLE_OBJECT_CANNON:
            index = window_selected_object_index[slot];
            if (index < castle->cannon_count
                && castle_data_is_point_set(&castle->cannons[index]))
            {
                /* Cannon coordinate is the lower-left corner of the sprite */
                rect_x = origin_x + castle->cannons[index].x;
                rect_y = baseline_y - castle->cannons[index].y - 19;
                rect_width = 20;
                rect_height = 20;
                should_draw = 1;
            }
            break;

        case CASTLE_OBJECT_WIND_VANE:
            if (castle_data_is_point_set(&castle->wind_vane))
            {
                rect_x = origin_x + castle->wind_vane.x - WIND_VANE_CLEAR_WIDTH / 2 - 2;
                rect_y = baseline_y - castle->wind_vane.y - WIND_VANE_CLEAR_HEIGHT - 2;
                rect_width = WIND_VANE_CLEAR_WIDTH + 4;
                rect_height = WIND_VANE_CLEAR_HEIGHT + 4;
                should_draw = 1;
            }
            break;

        case CASTLE_OBJECT_MONEY:
            if (castle_data_is_point_set(&castle->money))
            {
                rect_x = origin_x + castle->money.x - 2;
                rect_y = baseline_y - castle->money.y - castle->money_storage.height - 1;
                rect_width = castle->money_storage.width + 5;
                rect_height = castle->money_storage.height + 5;
                should_draw = 1;
            }
            break;

        case CASTLE_OBJECT_POWDER:
            if (castle_data_is_point_set(&castle->powder))
            {
                rect_x = origin_x + castle->powder.x - 2;
                rect_y = baseline_y - castle->powder.y - castle->powder_storage.height - 1;
                rect_width = castle->powder_storage.width + 5;
                rect_height = castle->powder_storage.height + 5;
                should_draw = 1;
            }
            break;

        case CASTLE_OBJECT_CANNONBALLS:
            if (castle_data_is_point_set(&castle->cannonballs))
            {
                rect_x = origin_x + castle->cannonballs.x - 2;
                rect_y = baseline_y - castle->cannonballs.y - castle->cannonball_storage.height - 1;
                rect_width = castle->cannonball_storage.width + 5;
                rect_height = castle->cannonball_storage.height + 5;
                should_draw = 1;
            }
            break;

        case CASTLE_OBJECT_WALL:
        case CASTLE_OBJECT_ROOF:
            index = window_selected_object_index[slot];
            if (index < castle->drawing_count
                && castle->drawing[index].type == CASTLE_DATA_DRAW_POLYGON)
            {
                /* For polygons, draw small squares at each vertex */
                const CastleDataPolygon *polygon = &castle->drawing[index].value.polygon;
                size_t i;
                short marker_size = 4; /* Size of corner marker squares */

                for (i = 0; i < polygon->point_count; i++)
                {
                    long long screen_x = origin_x + polygon->points[i].x;
                    long long screen_y = baseline_y - polygon->points[i].y;
                    short marker_rect[4];

                    if (screen_x < SHRT_MIN || screen_x > SHRT_MAX
                        || screen_y < SHRT_MIN || screen_y > SHRT_MAX)
                    {
                        continue;
                    }

                    marker_rect[0] = (short)(screen_x - marker_size / 2);
                    marker_rect[1] = (short)(screen_y - marker_size / 2);
                    marker_rect[2] = (short)(screen_x + marker_size / 2);
                    marker_rect[3] = (short)(screen_y + marker_size / 2);

                    vr_recfl(vdi_handle, marker_rect);
                }
            }
            break;

        default:
            break;
    }

    /* Draw the highlight rectangle for non-polygon objects */
    if (should_draw)
    {
        /* Clamp to short range for VDI */
        if (rect_x < SHRT_MIN || rect_x > SHRT_MAX
            || rect_y < SHRT_MIN || rect_y > SHRT_MAX
            || rect_width <= 0 || rect_height <= 0
            || rect_x + rect_width > SHRT_MAX
            || rect_y + rect_height > SHRT_MAX)
        {
            should_draw = 0;
        }
        else
        {
            highlight_rect[0] = (short)rect_x;
            highlight_rect[1] = (short)rect_y;
            highlight_rect[2] = (short)(rect_x + rect_width - 1);
            highlight_rect[3] = (short)(rect_y + rect_height - 1);

            /* Draw the highlight rectangle */
            {
                short points[10];
                points[0] = highlight_rect[0];
                points[1] = highlight_rect[1];
                points[2] = highlight_rect[2];
                points[3] = highlight_rect[1];
                points[4] = highlight_rect[2];
                points[5] = highlight_rect[3];
                points[6] = highlight_rect[0];
                points[7] = highlight_rect[3];
                points[8] = highlight_rect[0];
                points[9] = highlight_rect[1];
                v_pline(vdi_handle, 5, points);
            }
        }
    }

    /* Always restore VDI state before returning */
    vswr_mode(vdi_handle, MD_REPLACE);
    vsl_type(vdi_handle, 1);
}

/**
 * Updates the position of the selected object based on mouse movement during drag.
 *
 * @param slot Window slot index.
 * @param mouse_x Current mouse X coordinate in screen space.
 * @param mouse_y Current mouse Y coordinate in screen space.
 * @param work_x Work-area left coordinate.
 * @param work_y Work-area top coordinate.
 * @param work_width Work-area width.
 * @param work_height Work-area height.
 * @return Non-zero if the object position was updated.
 */
static short update_object_position(
    short slot,
    short mouse_x,
    short mouse_y,
    short work_x,
    short work_y,
    short work_width,
    short work_height
)
{
    CastleDataCastle *castle;
    int delta_x;
    int delta_y;
    short updated;

    if (!window_dragging[slot]
        || !window_has_castle_document[slot]
        || window_documents[slot].castle_count == 0)
    {
        return 0;
    }

    castle = &window_documents[slot].castles[window_selected_castles[slot]];

    /* Calculate delta from drag start position */
    delta_x = mouse_x - window_drag_start_x[slot];
    delta_y = -(mouse_y - window_drag_start_y[slot]); /* Invert Y for castle coordinates */

    updated = 0;

    switch (window_selected_object_type[slot])
    {
        case CASTLE_OBJECT_THRONE:
            if (castle_data_is_point_set(&castle->throne))
            {
                castle->throne.x = window_drag_original_x[slot] + delta_x;
                castle->throne.y = window_drag_original_y[slot] + delta_y;
                updated = 1;
            }
            break;

        case CASTLE_OBJECT_CANNON:
        {
            size_t cannon_index = window_selected_object_index[slot];
            if (cannon_index < castle->cannon_count
                && castle_data_is_point_set(&castle->cannons[cannon_index]))
            {
                castle->cannons[cannon_index].x = window_drag_original_x[slot] + delta_x;
                castle->cannons[cannon_index].y = window_drag_original_y[slot] + delta_y;
                updated = 1;
            }
            break;
        }

        case CASTLE_OBJECT_WIND_VANE:
            if (castle_data_is_point_set(&castle->wind_vane))
            {
                castle->wind_vane.x = window_drag_original_x[slot] + delta_x;
                castle->wind_vane.y = window_drag_original_y[slot] + delta_y;
                updated = 1;
            }
            break;

        case CASTLE_OBJECT_MONEY:
            if (castle_data_is_point_set(&castle->money))
            {
                castle->money.x = window_drag_original_x[slot] + delta_x;
                castle->money.y = window_drag_original_y[slot] + delta_y;
                updated = 1;
            }
            break;

        case CASTLE_OBJECT_POWDER:
            if (castle_data_is_point_set(&castle->powder))
            {
                castle->powder.x = window_drag_original_x[slot] + delta_x;
                castle->powder.y = window_drag_original_y[slot] + delta_y;
                updated = 1;
            }
            break;

        case CASTLE_OBJECT_CANNONBALLS:
            if (castle_data_is_point_set(&castle->cannonballs))
            {
                castle->cannonballs.x = window_drag_original_x[slot] + delta_x;
                castle->cannonballs.y = window_drag_original_y[slot] + delta_y;
                updated = 1;
            }
            break;

        case CASTLE_OBJECT_WALL:
        case CASTLE_OBJECT_ROOF:
        {
            size_t polygon_index = window_selected_object_index[slot];
            if (polygon_index < castle->drawing_count
                && castle->drawing[polygon_index].type == CASTLE_DATA_DRAW_POLYGON
                && window_drag_original_polygon[slot])
            {
                CastleDataPolygon *polygon = &castle->drawing[polygon_index].value.polygon;
                size_t i;
                for (i = 0; i < polygon->point_count; i++)
                {
                    polygon->points[i].x = window_drag_original_polygon[slot][i].x + delta_x;
                    polygon->points[i].y = window_drag_original_polygon[slot][i].y + delta_y;
                }
                updated = 1;
            }
            break;
        }
        default:
            /* Other objects are not draggable */
            break;
    }

    if (updated)
    {
        /* Redraw the window to show the updated position */
        GRECT redraw_area;
        redraw_area.g_x = work_x;
        redraw_area.g_y = work_y;
        redraw_area.g_w = work_width;
        redraw_area.g_h = work_height;
        redraw_window(window_handles[slot], &redraw_area);
    }

    return updated;
}

/**
 * Draws a castle's ordered VDI drawing commands at one-to-one scale.
 *
 * @param castle Castle geometry to draw.
 * @param work_x Work-area left coordinate.
 * @param work_y Work-area top coordinate.
 * @param work_width Work-area width.
 * @param work_height Work-area height.
 */
static void draw_castle(
    const CastleDataCastle *castle,
    short work_x,
    short work_y,
    short work_width,
    short work_height
)
{
    size_t index;
    size_t point_index;
    size_t point_count;
    long long origin_x;
    long long baseline_y;
    long long screen_x;
    long long screen_y;
    short fill_mode;
    short *points;
    short vdi_point_count;

    if (!castle || castle->drawing_count == 0)
    {
        return;
    }

    origin_x = (long long)work_x
        + ((long long)work_width - castle->width) / 2;
    baseline_y = (long long)work_y + work_height - 12;
    fill_mode = 1;
    vswr_mode(vdi_handle, MD_ERASE);
    vsf_color(vdi_handle, 1);
    vsl_color(vdi_handle, 1);
    vsf_interior(vdi_handle, FIS_PATTERN);
    vsf_style(vdi_handle, 9);

    for (index = 0; index < castle->drawing_count; index++)
    {
        const CastleDataDrawing *drawing;

        drawing = &castle->drawing[index];
        if (drawing->type == CASTLE_DATA_DRAW_FILL_STYLE)
        {
            if (drawing->value.fill_style.style < SHRT_MIN
                || drawing->value.fill_style.style > SHRT_MAX
                || drawing->value.fill_style.index < SHRT_MIN
                || drawing->value.fill_style.index > SHRT_MAX)
            {
                app_logger_write("Castle fill style exceeds VDI limits.");
                continue;
            }
            vsf_interior(vdi_handle, (short)drawing->value.fill_style.style);
            vsf_style(vdi_handle, (short)drawing->value.fill_style.index);
            continue;
        }
        if (drawing->type == CASTLE_DATA_DRAW_TOGGLE_MODE)
        {
            fill_mode = !fill_mode;
            continue;
        }
        if (drawing->type != CASTLE_DATA_DRAW_POLYGON)
        {
            app_logger_write("Unsupported castle drawing command ignored.");
            continue;
        }

        point_count = drawing->value.polygon.point_count;
        if (point_count < (fill_mode ? 3U : 2U)
            || point_count >= (size_t)SHRT_MAX
            || point_count > ((size_t)-1 / sizeof(*points)) / 2 - 1)
        {
            app_logger_write("Castle polygon exceeds VDI drawing limits.");
            continue;
        }

        points = (short *)malloc((point_count + 1) * 2 * sizeof(*points));
        if (!points)
        {
            app_logger_write("Unable to allocate VDI polygon coordinates.");
            break;
        }
        for (point_index = 0; point_index < point_count; point_index++)
        {
            screen_x = origin_x
                + drawing->value.polygon.points[point_index].x;
            screen_y = baseline_y
                - drawing->value.polygon.points[point_index].y;
            if (screen_x < SHRT_MIN || screen_x > SHRT_MAX
                || screen_y < SHRT_MIN || screen_y > SHRT_MAX)
            {
                break;
            }
            points[point_index * 2] = (short)screen_x;
            points[point_index * 2 + 1] = (short)screen_y;
        }
        if (point_index != point_count)
        {
            free(points);
            app_logger_write("Castle polygon coordinates exceed VDI limits.");
            continue;
        }

        points[point_count * 2] = points[0];
        points[point_count * 2 + 1] = points[1];
        vdi_point_count = (short)(point_count + 1);
        if (fill_mode)
        {
            v_fillarea(vdi_handle, vdi_point_count, points);
        }
        else
        {
            v_pline(vdi_handle, vdi_point_count, points);
        }
        free(points);
    }

    vswr_mode(vdi_handle, MD_REPLACE);
    vsf_interior(vdi_handle, FIS_SOLID);
    vsf_color(vdi_handle, 0);
}

/**
 * Draws a document work area and its selected castle, if one is loaded.
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
    long long castle_origin_x;
    long long castle_baseline_y;
    const CastleDataCastle *castle;

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
                    if (window_documents[slot].castle_count > 0)
                    {
                        castle = &window_documents[slot].castles[
                            window_selected_castles[slot]
                        ];
                        castle_origin_x = (long long)work_x
                            + ((long long)work_width - castle->width) / 2;
                        castle_baseline_y = (long long)work_y
                            + work_height - 12;
                        draw_castle(
                            castle,
                            work_x,
                            work_y,
                            work_width,
                            work_height
                        );
                        draw_castle_objects(
                            castle,
                            castle_origin_x,
                            castle_baseline_y
                        );
                        draw_selection_highlight(
                            slot,
                            castle,
                            castle_origin_x,
                            castle_baseline_y
                        );
                    }
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
 * Handles a mouse click in a document window's work area.
 *
 * @param x Mouse X coordinate in screen space.
 * @param y Mouse Y coordinate in screen space.
 * @return Non-zero if the click was handled by a document window.
 */
short document_window_handle_click(short x, short y)
{
    short index;
    short handle;
    short work_x;
    short work_y;
    short work_width;
    short work_height;

    for (index = 0; index < MAX_DOCUMENT_WINDOWS; index++)
    {
        handle = window_handles[index];
        if (handle == 0)
        {
            continue;
        }

        if (wind_get(handle, WF_WORKXYWH, &work_x, &work_y, &work_width, &work_height))
        {
            if (x >= work_x && x < work_x + work_width
                && y >= work_y && y < work_y + work_height)
            {
                handle_object_click(index, x, y, work_x, work_y, work_width, work_height);
                return 1;
            }
        }
    }

    return 0;
}

/**
 * Finds the active managed document, preferring AES's top window.
 *
 * @param handle Receives the active AES window handle when found.
 * @return The matching document slot, or -1 if no document is active.
 */
static short find_active_document(short *handle)
{
    short top_handle;
    short unused_1;
    short unused_2;
    short unused_3;
    short slot;

    slot = -1;
    if (wind_get(0, WF_TOP, &top_handle, &unused_1, &unused_2, &unused_3))
    {
        slot = find_window_slot(top_handle);
    }
    if (slot < 0)
    {
        top_handle = active_document_handle;
        slot = find_window_slot(top_handle);
    }
    if (slot >= 0)
    {
        *handle = window_handles[slot];
    }

    return slot;
}

/**
 * Checks if a window slot can be closed, prompting to save if needed.
 *
 * @param slot Window handle slot.
 * @return Non-zero if the window should be closed, zero if cancelled.
 */
static short confirm_close(short slot)
{
    short alert_result;

    if (!window_modified[slot])
    {
        return 1;
    }

    alert_result = app_alerts_show(APP_ALERT_UNSAVED_CHANGES);
    if (alert_result == 3/*Save*/)
    {
        short active_handle;
        short original_slot;

        active_handle = window_handles[slot];
        original_slot = find_active_document(&active_handle);
        if (original_slot == slot)
        {
            if (!document_window_save(0))
            {
                return 0;
            }
        }
        else
        {
            active_document_handle = active_handle;
            if (!document_window_save(0))
            {
                return 0;
            }
        }
    }
    else if (alert_result == 1/*Cancel*/)
    {
        return 0;
    }

    return 1;
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
    window_titles[slot][0] = '\0';
    window_file_names[slot][0] = '\0';
    if (active_document_handle == handle)
    {
        active_document_handle = 0;
    }
    castle_data_document_destroy(&window_documents[slot]);
    window_has_castle_document[slot] = 0;
    window_selected_castles[slot] = 0;
    window_has_file_path[slot] = 0;
    window_modified[slot] = 0;
    window_maximized[slot] = 0;
    window_selected_object_type[slot] = CASTLE_OBJECT_NONE;
    window_selected_object_index[slot] = 0;
    app_logger_write("Document window closed.");
    return 1;
}

/**
 * Creates a document window with a stable, per-window title.
 *
 * @param title Title for a new, untitled document.
 * @param file_name DAT basename for a castle document.
 * @param document Castle data to transfer to the window, or null for a new
 *                 document.
 * @return Non-zero if the window was created and opened successfully.
 */
static short create_document_window(
    const char *title,
    const char *file_name,
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
    size_t title_length;
    const char *window_title;

    slot = find_free_window_slot();
    if (slot < 0)
    {
        app_logger_write("Document window limit reached; request ignored.");
        return 0;
    }
    if (!open_virtual_workstation())
    {
        return 0;
    }
    window_title = title;
    if (document)
    {
        if (!file_name)
        {
            app_logger_write("Missing filename for castle document title.");
            app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_TITLE_MISSING);
            return 0;
        }
        window_file_names[slot][0] = '\0';
        title_length = 0;
        if (!append_title_text(
            window_file_names[slot],
            sizeof(window_file_names[slot]),
            &title_length,
            file_name
        )
            || !format_castle_title(
            slot,
            window_file_names[slot],
            document->castle_count > 0 ? 1 : 0,
            document->castle_count,
            window_titles[slot],
            sizeof(window_titles[slot])
        ))
        {
            app_logger_write("Unable to format the castle window title.");
            app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_TITLE_MISSING);
            return 0;
        }
        window_title = window_titles[slot];
    }
    else
    {
        window_file_names[slot][0] = '\0';
        title_length = 0;
        if (!title || !append_title_text(
            window_file_names[slot],
            sizeof(window_file_names[slot]),
            &title_length,
            title
        ))
        {
            app_logger_write("Unable to store the document window title.");
            app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_TITLE_MISSING);
            return 0;
        }
        if (!format_castle_title(
            slot,
            title,
            0,
            0,
            window_titles[slot],
            sizeof(window_titles[slot])
        ))
        {
            app_logger_write("Unable to format the castle window title.");
            app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_TITLE_MISSING);
            return 0;
        }
        window_title = window_titles[slot];
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

    if (!wind_set_str(handle, WF_NAME, window_title))
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
    active_document_handle = handle;
    if (document)
    {
        window_documents[slot] = *document;
        document->castles = 0;
        document->castle_count = 0;
        window_has_castle_document[slot] = 1;
        window_selected_castles[slot] = 0;
        window_has_file_path[slot] = 0;
        window_modified[slot] = 0;
    }
    else
    {
        window_has_castle_document[slot] = 0;
        window_selected_castles[slot] = 0;
        window_has_file_path[slot] = 0;
        window_modified[slot] = 0;
    }
    restore_bounds[slot].g_x = x;
    restore_bounds[slot].g_y = y;
    restore_bounds[slot].g_w = width;
    restore_bounds[slot].g_h = height;
    window_maximized[slot] = 0;
    window_selected_object_type[slot] = CASTLE_OBJECT_NONE;
    window_selected_object_index[slot] = 0;
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

    return create_document_window(title, 0, 0);
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
    const char *file_name;
    char log_message[96];
    short slot;

    if (!document_window_has_capacity())
    {
        app_logger_write("Document window limit reached; open request ignored.");
        return 0;
    }

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

    sprintf(
        log_message,
        "Loaded %lu castles from the selected DAT file.",
        (unsigned long)document.castle_count
    );
    app_logger_write(log_message);
    file_name = strrchr(path, '\\');
    if (!file_name)
    {
        file_name = strrchr(path, '/');
    }
    file_name = file_name ? file_name + 1 : path;
    if (!create_document_window(0, file_name, &document))
    {
        castle_data_document_destroy(&document);
        return 0;
    }

    slot = find_window_slot(active_document_handle);
    if (slot >= 0)
    {
        window_has_file_path[slot] = 1;
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

    if (!confirm_close(slot))
    {
        return 0;
    }

    return close_window(slot);
}

/**
 * Selects an adjacent castle in the active document and redraws its work area.
 *
 * @param direction Previous or next castle.
 * @return Non-zero if a different castle was selected.
 */
short document_window_navigate_castles(DocumentCastleNavigation direction)
{
    short active_handle;
    short work_x;
    short work_y;
    short work_width;
    short work_height;
    short slot;
    size_t selected_castle;
    GRECT redraw_area;

    if (direction != DOCUMENT_CASTLE_PREVIOUS
        && direction != DOCUMENT_CASTLE_NEXT)
    {
        app_logger_write("Invalid castle navigation direction.");
        return 0;
    }

    slot = find_active_document(&active_handle);
    if (slot < 0)
    {
        app_logger_write("Castle navigation ignored without an active document window.");
        return 0;
    }

    if (!window_has_castle_document[slot])
    {
        app_logger_write("Castle navigation ignored without an active DAT document.");
        return 0;
    }

    if (!wind_get(
        active_handle,
        WF_WORKXYWH,
        &work_x,
        &work_y,
        &work_width,
        &work_height
    ))
    {
        app_alerts_show(APP_ALERT_DESKTOP_SIZE_FAILED);
        return 0;
    }

    if (direction == DOCUMENT_CASTLE_PREVIOUS)
    {
        if (window_selected_castles[slot] == 0)
        {
            return 0;
        }
        selected_castle = window_selected_castles[slot] - 1;
    }
    else
    {
        if (window_selected_castles[slot] + 1
            >= window_documents[slot].castle_count)
        {
            return 0;
        }
        selected_castle = window_selected_castles[slot] + 1;
    }

    if (!format_castle_title(
        slot,
        window_file_names[slot],
        selected_castle + 1,
        window_documents[slot].castle_count,
        window_titles[slot],
        sizeof(window_titles[slot])
    ))
    {
        app_logger_write("Unable to format the castle window title.");
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_TITLE_MISSING);
        return 0;
    }
    if (!wind_set_str(active_handle, WF_NAME, window_titles[slot]))
    {
        app_alerts_show(APP_ALERT_DOCUMENT_WINDOW_TITLE_FAILED);
        return 0;
    }

    window_selected_castles[slot] = selected_castle;
    window_selected_object_type[slot] = CASTLE_OBJECT_NONE;
    window_selected_object_index[slot] = 0;
    redraw_area.g_x = work_x;
    redraw_area.g_y = work_y;
    redraw_area.g_w = work_width;
    redraw_area.g_h = work_height;
    redraw_window(active_handle, &redraw_area);
    app_logger_write(
        direction == DOCUMENT_CASTLE_PREVIOUS
            ? "Selected previous castle."
            : "Selected next castle."
    );
    return 1;
}

/**
 * Checks whether the active document has a castle adjacent in the given direction.
 *
 * @param direction Previous or next castle.
 * @return Non-zero if navigation in that direction is possible.
 */
short document_window_can_navigate_castles(DocumentCastleNavigation direction)
{
    short active_handle;
    short slot;

    if (direction != DOCUMENT_CASTLE_PREVIOUS
        && direction != DOCUMENT_CASTLE_NEXT)
    {
        return 0;
    }

    slot = find_active_document(&active_handle);
    if (slot < 0 || !window_has_castle_document[slot])
    {
        return 0;
    }

    if (direction == DOCUMENT_CASTLE_PREVIOUS)
    {
        return window_selected_castles[slot] > 0;
    }

    return window_selected_castles[slot] + 1
        < window_documents[slot].castle_count;
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
 * Checks whether another document window can be opened.
 *
 * @return Non-zero if at least one document-window slot is available.
 */
short document_window_has_capacity(void)
{
    return find_free_window_slot() >= 0;
}

/**
 * Checks whether the active document has at least one castle.
 *
 * @return Non-zero if the active document window has a castle.
 */
short document_window_has_castle(void)
{
    short active_handle;
    short slot;

    slot = find_active_document(&active_handle);
    if (slot < 0 || !window_has_castle_document[slot])
    {
        return 0;
    }

    return window_documents[slot].castle_count > 0;
}

/**
 * Checks whether the active castle has a throne.
 *
 * @return Non-zero if the active castle has a throne set.
 */
short document_window_has_throne(void)
{
    short active_handle;
    short slot;
    const CastleDataCastle *castle;

    slot = find_active_document(&active_handle);
    if (slot < 0 || !window_has_castle_document[slot])
    {
        return 0;
    }

    if (window_documents[slot].castle_count == 0)
    {
        return 0;
    }

    castle = &window_documents[slot].castles[window_selected_castles[slot]];
    return castle_data_is_point_set(&castle->throne);
}

/**
 * Adds a throne to the active castle at position (0, 0).
 *
 * @return Non-zero if the throne was added successfully.
 */
short document_window_add_throne(void)
{
    short active_handle;
    short slot;
    CastleDataCastle *castle;

    slot = find_active_document(&active_handle);
    if (slot < 0 || !window_has_castle_document[slot])
    {
        return 0;
    }

    if (window_documents[slot].castle_count == 0)
    {
        return 0;
    }

    castle = &window_documents[slot].castles[window_selected_castles[slot]];

    /* Only add if throne is not already set */
    if (castle_data_is_point_set(&castle->throne))
    {
        return 0;
    }

    /* Set throne at position (0, 0) */
    castle->throne.x = 0;
    castle->throne.y = 0;

    /* Mark document as modified */
    window_modified[slot] = 1;

    /* Update window title to show modification */
    {
        char title_buffer[CASTLE_TITLE_BUFFER_SIZE];
        format_castle_title(
            slot,
            window_file_names[slot],
            window_selected_castles[slot] + 1,
            window_documents[slot].castle_count,
            title_buffer,
            CASTLE_TITLE_BUFFER_SIZE
        );
        wind_set_str(window_handles[slot], WF_NAME, title_buffer);
    }

    /* Redraw the window */
    {
        short work_x;
        short work_y;
        short work_width;
        short work_height;
        GRECT redraw_area;

        if (wind_get(window_handles[slot], WF_WORKXYWH, &work_x, &work_y, &work_width, &work_height))
        {
            redraw_area.g_x = work_x;
            redraw_area.g_y = work_y;
            redraw_area.g_w = work_width;
            redraw_area.g_h = work_height;
            redraw_window(window_handles[slot], &redraw_area);
        }
    }

    return 1;
}

/**
 * Checks whether the active document has unsaved changes.
 *
 * @return Non-zero if the active document has been modified.
 */
short document_window_is_modified(void)
{
    short active_handle;
    short slot;

    slot = find_active_document(&active_handle);
    if (slot < 0)
    {
        return 0;
    }

    return window_modified[slot];
}

/**
 * Checks whether any document has unsaved changes.
 *
 * @return Non-zero if any document has been modified.
 */
short document_window_has_unsaved_changes(void)
{
    short index;

    for (index = 0; index < MAX_DOCUMENT_WINDOWS; index++)
    {
        if (window_handles[index] != 0 && window_modified[index])
        {
            return 1;
        }
    }

    return 0;
}

/**
 * Checks whether the active document has a selected object.
 *
 * @return Non-zero if an object is selected in the active document.
 */
short document_window_has_selected_object(void)
{
    short active_handle;
    short slot;

    slot = find_active_document(&active_handle);
    if (slot < 0)
    {
        return 0;
    }

    return window_selected_object_type[slot] != CASTLE_OBJECT_NONE;
}

/**
 * Removes the currently selected object from the active document.
 *
 * @return Non-zero if an object was removed successfully.
 */
short document_window_remove_selected_object(void)
{
    short active_handle;
    short slot;
    CastleDataCastle *castle;
    GRECT redraw_area;
    size_t index;
    size_t drawing_index;
    short removed;

    slot = find_active_document(&active_handle);
    if (slot < 0 || !window_has_castle_document[slot])
    {
        app_logger_write("Object removal ignored without an active DAT document.");
        return 0;
    }

    if (window_documents[slot].castle_count == 0)
    {
        app_logger_write("Object removal ignored: document has no castles.");
        return 0;
    }

    castle = &window_documents[slot].castles[window_selected_castles[slot]];
    removed = 0;

    switch (window_selected_object_type[slot])
    {
        case CASTLE_OBJECT_THRONE:
            castle->throne.x = CASTLE_DATA_UNSET_COORDINATE;
            castle->throne.y = CASTLE_DATA_UNSET_COORDINATE;
            removed = 1;
            break;

        case CASTLE_OBJECT_CANNON:
            index = window_selected_object_index[slot];
            if (index < castle->cannon_count)
            {
                castle->cannons[index].x = CASTLE_DATA_UNSET_COORDINATE;
                castle->cannons[index].y = CASTLE_DATA_UNSET_COORDINATE;
                removed = 1;
            }
            break;

        case CASTLE_OBJECT_WIND_VANE:
            castle->wind_vane.x = CASTLE_DATA_UNSET_COORDINATE;
            castle->wind_vane.y = CASTLE_DATA_UNSET_COORDINATE;
            removed = 1;
            break;

        case CASTLE_OBJECT_MONEY:
            castle->money.x = CASTLE_DATA_UNSET_COORDINATE;
            castle->money.y = CASTLE_DATA_UNSET_COORDINATE;
            removed = 1;
            break;

        case CASTLE_OBJECT_POWDER:
            castle->powder.x = CASTLE_DATA_UNSET_COORDINATE;
            castle->powder.y = CASTLE_DATA_UNSET_COORDINATE;
            removed = 1;
            break;

        case CASTLE_OBJECT_CANNONBALLS:
            castle->cannonballs.x = CASTLE_DATA_UNSET_COORDINATE;
            castle->cannonballs.y = CASTLE_DATA_UNSET_COORDINATE;
            removed = 1;
            break;

        case CASTLE_OBJECT_WALL:
        case CASTLE_OBJECT_ROOF:
            drawing_index = window_selected_object_index[slot];
            if (drawing_index < castle->drawing_count)
            {
                /* Remove the drawing by shifting remaining drawings */
                for (index = drawing_index; index < castle->drawing_count - 1; index++)
                {
                    castle->drawing[index] = castle->drawing[index + 1];
                }
                castle->drawing_count--;
                removed = 1;
            }
            break;

        default:
            app_logger_write("No object selected for removal.");
            return 0;
    }

    if (removed)
    {
        window_selected_object_type[slot] = CASTLE_OBJECT_NONE;
        window_selected_object_index[slot] = 0;
        window_modified[slot] = 1;
        app_logger_write("Selected object removed from castle.");

        /* Redraw the work area */
        redraw_area.g_x = 0;
        redraw_area.g_y = 0;
        redraw_area.g_w = 0;
        redraw_area.g_h = 0;
        wind_get_grect(window_handles[slot], WF_WORKXYWH, &redraw_area);
        redraw_window(window_handles[slot], &redraw_area);

        /* Update window title to show modification */
        format_castle_title(
            slot,
            window_file_names[slot],
            window_selected_castles[slot] + 1,
            window_documents[slot].castle_count,
            window_titles[slot],
            CASTLE_TITLE_BUFFER_SIZE
        );
        wind_set(window_handles[slot], WF_NAME, (short)((long)window_titles[slot] >> 16), (short)((long)window_titles[slot] & 0xFFFF), 0, 0);

        return 1;
    }

    return 0;
}

/**
 * Closes all document windows, prompting to save each unsaved document.
 *
 * @return Non-zero if all documents were closed successfully.
 */
short document_window_close_all(void)
{
    short index;
    short handle;
    short slot;

    for (index = 0; index < MAX_DOCUMENT_WINDOWS; index++)
    {
        if (window_handles[index] == 0)
        {
            continue;
        }

        handle = window_handles[index];
        slot = find_window_slot(handle);
        if (slot < 0)
        {
            continue;
        }

        if (!confirm_close(slot))
        {
            return 0;
        }

        if (!close_window(slot))
        {
            return 0;
        }
    }

    return 1;
}

/**
 * Saves the active document.
 *
 * If the document has no associated file, the save-as dialog is shown.
 * On success, the modification flag is cleared and the window title is updated.
 *
 * @param force_dialog Non-zero to always show the save-as dialog.
 * @return Non-zero if the document was saved successfully.
 */
short document_window_save(short force_dialog)
{
    short active_handle;
    short slot;
    const char *save_path;
    const char *file_name;
    short selection_result;
    CastleDataWriteResult write_result;
    size_t title_length;
    short handle;
    char full_path[256];
    char directory[128];
    short drive;

    slot = find_active_document(&active_handle);
    if (slot < 0)
    {
        app_logger_write("Save failed: no active document.");
        return 0;
    }

    if (force_dialog || !window_has_file_path[slot])
    {
        app_logger_write("Showing save-as dialog.");
        selection_result = file_save_select(&save_path);
        if (selection_result <= 0)
        {
            app_logger_write((selection_result == 0) ? "Save-as cancelled." : "Save-as dialog failed.");
            return 0;
        }

        handle = window_handles[slot];
        window_file_names[slot][0] = '\0';
        title_length = 0;
        file_name = strrchr(save_path, '\\');
        if (file_name)
        {
            file_name++;
        }
        else
        {
            file_name = save_path;
        }
        if (!append_title_text(
            window_file_names[slot],
            sizeof(window_file_names[slot]),
            &title_length,
            file_name
        ))
        {
            app_logger_write("Unable to store the document file name.");
            return 0;
        }

        if (!format_castle_title(
            slot,
            window_file_names[slot],
            window_documents[slot].castle_count > 0
                ? window_selected_castles[slot] + 1
                : 0,
            window_documents[slot].castle_count,
            window_titles[slot],
            sizeof(window_titles[slot])
        ))
        {
            app_logger_write("Unable to format window title after save-as.");
        }
        else if (!wind_set_str(handle, WF_NAME, window_titles[slot]))
        {
            app_logger_write("Unable to update window title after save-as.");
        }

        window_has_file_path[slot] = 1;
    }
    else
    {
        drive = Dgetdrv();
        if (Dgetpath(directory, drive + 1) < 0)
        {
            app_logger_write("Unable to get current directory for save.");
            return 0;
        }
        sprintf(full_path, "%c:\\%s\\%s", 'A' + drive, directory, window_file_names[slot]);
        save_path = full_path;
    }

    write_result = castle_data_write(save_path, &window_documents[slot]);
    if (write_result != CASTLE_DATA_WRITE_OK)
    {
        app_logger_write("Save failed: unable to write document.");
        return 0;
    }

    window_modified[slot] = 0;
    app_logger_write("Document saved successfully.");

    if (!format_castle_title(
        slot,
        window_file_names[slot],
        window_documents[slot].castle_count > 0
            ? window_selected_castles[slot] + 1
            : 0,
        window_documents[slot].castle_count,
        window_titles[slot],
        sizeof(window_titles[slot])
    ))
    {
        app_logger_write("Unable to format window title after save.");
    }
    else if (!wind_set_str(active_handle, WF_NAME, window_titles[slot]))
    {
        app_logger_write("Unable to update window title after save.");
    }

    return 1;
}

/**
 * Removes the currently selected castle from the active document.
 *
 * @return Non-zero if a castle was removed successfully.
 */
short document_window_remove_castle(void)
{
    short active_handle;
    short slot;
    short work_x;
    short work_y;
    short work_width;
    short work_height;
    GRECT redraw_area;
    size_t selected_castle;

    slot = find_active_document(&active_handle);
    if (slot < 0 || !window_has_castle_document[slot])
    {
        app_logger_write("Castle removal ignored without an active DAT document.");
        return 0;
    }

    if (window_documents[slot].castle_count == 0)
    {
        app_logger_write("Castle removal ignored: document has no castles.");
        return 0;
    }

    selected_castle = window_selected_castles[slot];
    if (!castle_data_remove_castle(&window_documents[slot], selected_castle))
    {
        app_logger_write("Castle removal failed.");
        return 0;
    }
    window_modified[slot] = 1;

    if (window_documents[slot].castle_count == 0)
    {
        window_selected_castles[slot] = 0;
    }
    else if (selected_castle >= window_documents[slot].castle_count)
    {
        window_selected_castles[slot] = window_documents[slot].castle_count - 1;
    }

    if (!format_castle_title(
        slot,
        window_file_names[slot],
        window_documents[slot].castle_count > 0
            ? window_selected_castles[slot] + 1
            : 0,
        window_documents[slot].castle_count,
        window_titles[slot],
        sizeof(window_titles[slot])
    ))
    {
        app_logger_write("Unable to format window title after castle removal.");
    }
    else if (!wind_set_str(active_handle, WF_NAME, window_titles[slot]))
    {
        app_logger_write("Unable to update window title after castle removal.");
    }

    if (!wind_get(
        active_handle,
        WF_WORKXYWH,
        &work_x,
        &work_y,
        &work_width,
        &work_height
    ))
    {
        app_logger_write("Unable to get window work area for redraw.");
        return 1;
    }

    redraw_area.g_x = work_x;
    redraw_area.g_y = work_y;
    redraw_area.g_w = work_width;
    redraw_area.g_h = work_height;
    redraw_window(active_handle, &redraw_area);

    app_logger_write("Castle removed successfully.");
    return 1;
}

/**
 * Adds a new castle to the active document and selects it.
 *
 * @return Non-zero if a castle was added successfully.
 */
short document_window_add_castle(void)
{
    short active_handle;
    short slot;
    short work_x;
    short work_y;
    short work_width;
    short work_height;
    GRECT redraw_area;
    size_t insert_index;

    slot = find_active_document(&active_handle);
    if (slot < 0)
    {
        app_logger_write("Castle addition ignored without an active document window.");
        return 0;
    }

    if (window_documents[slot].castle_count == 0)
    {
        window_documents[slot].castles = 0;
        window_documents[slot].castle_count = 0;
        window_has_castle_document[slot] = 1;
        window_selected_castles[slot] = 0;
        insert_index = 0;
    }
    else
    {
        insert_index = window_selected_castles[slot] + 1;
    }

    if (!castle_data_insert_castle(&window_documents[slot], insert_index))
    {
        app_logger_write("Castle addition failed.");
        return 0;
    }

    window_selected_castles[slot] = insert_index;
    window_modified[slot] = 1;

    if (!format_castle_title(
        slot,
        window_file_names[slot],
        window_selected_castles[slot] + 1,
        window_documents[slot].castle_count,
        window_titles[slot],
        sizeof(window_titles[slot])
    ))
    {
        app_logger_write("Unable to format window title after castle addition.");
    }
    else if (!wind_set_str(active_handle, WF_NAME, window_titles[slot]))
    {
        app_logger_write("Unable to update window title after castle addition.");
    }

    if (!wind_get(
        active_handle,
        WF_WORKXYWH,
        &work_x,
        &work_y,
        &work_width,
        &work_height
    ))
    {
        app_logger_write("Unable to get window work area for redraw.");
        return 1;
    }

    redraw_area.g_x = work_x;
    redraw_area.g_y = work_y;
    redraw_area.g_w = work_width;
    redraw_area.g_h = work_height;
    redraw_window(active_handle, &redraw_area);

    app_logger_write("Castle added successfully.");
    return 1;
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
            if (confirm_close(slot))
            {
                close_window(slot);
            }
            break;

        case WM_TOPPED:
            wind_set(window_handles[slot], WF_TOP, 0, 0, 0, 0);
            active_document_handle = window_handles[slot];
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
        window_modified[index] = 0;
    }

    if (vdi_handle != 0)
    {
        v_clsvwk(vdi_handle);
        vdi_handle = 0;
    }
}
