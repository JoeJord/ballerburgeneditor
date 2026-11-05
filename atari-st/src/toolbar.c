#include <gem.h>

#include "BALLERED.h"
#include "app_alerts.h"
#include "app_logger.h"
#include "app_resources.h"
#include "toolbar.h"

/** Width of each toolbar button's layout and hit area. */
#define TOOLBAR_BUTTON_WIDTH 20
/** Height of each toolbar button's layout and hit area. */
#define TOOLBAR_BUTTON_HEIGHT 20
/** Toolbar height leaves a one-pixel margin above and below button areas. */
#define TOOLBAR_HEIGHT 22
/** Horizontal spacing between adjacent toolbar buttons. */
#define TOOLBAR_BUTTON_GAP 4

typedef struct
{
    MenuAction action;
    short resource_index;
    short x;
    BITBLK *image_block;
} ToolbarButton;

/** Actions represented by their resource icons, in left-to-right order. */
static ToolbarButton toolbar_buttons[] = {
    { MENU_ACTION_QUIT, IC_QUIT, 0, 0 },
    { MENU_ACTION_NEW, IC_FILE_NEW, 0, 0 },
    { MENU_ACTION_OPEN, IC_FILE_OPEN, 0, 0 },
    { MENU_ACTION_ABOUT, IC_ABOUT, 0, 0 }
};
/** AES handle for the fixed, borderless toolbar window. */
static short toolbar_handle;
/** VDI workstation used to draw toolbar controls. */
static VdiHdl toolbar_vdi_handle;
/** Toolbar window screen bounds. */
static GRECT toolbar_bounds;

/**
 * Returns the number of buttons defined for the toolbar.
 *
 * @return Number of toolbar buttons.
 */
static short toolbar_button_count(void)
{
    return (short)(sizeof(toolbar_buttons) / sizeof(toolbar_buttons[0]));
}

/**
 * Loads all toolbar images from the GEM resource file.
 *
 * @return Non-zero if every toolbar image is available.
 */
static short load_toolbar_icons(void)
{
    short index;

    for (index = 0; index < toolbar_button_count(); index++)
    {
        switch (toolbar_buttons[index].action)
        {
            case MENU_ACTION_QUIT:
                app_logger_write("Loading toolbar icon IC_QUIT.");
                break;

            case MENU_ACTION_NEW:
                app_logger_write("Loading toolbar icon IC_FILE_NEW.");
                break;

            case MENU_ACTION_OPEN:
                app_logger_write("Loading toolbar icon IC_FILE_OPEN.");
                break;

            case MENU_ACTION_CLOSE:
                break;

            case MENU_ACTION_ABOUT:
                app_logger_write("Loading toolbar icon IC_ABOUT.");
                break;

            case MENU_ACTION_NONE:
                break;
        }
        toolbar_buttons[index].image_block =
            app_resources_get_free_image(toolbar_buttons[index].resource_index);
        if (!toolbar_buttons[index].image_block)
        {
            app_logger_write("Toolbar free-image resource lookup failed.");
            return 0;
        }
    }

    app_logger_write("Toolbar free-image resources loaded.");
    return 1;
}

/**
 * Draws one toolbar button within the current VDI clipping rectangle.
 *
 * @param button Button to draw.
 */
static void draw_button(
    const ToolbarButton *button,
    short clip_x,
    short clip_y,
    short clip_width,
    short clip_height
)
{
    OBJECT image_object;
    short button_y;
    short image_x;
    short image_y;
    short image_width;

    button_y = toolbar_bounds.g_y
        + (toolbar_bounds.g_h - TOOLBAR_BUTTON_HEIGHT) / 2;

    image_width = button->image_block->bi_wb * 8;
    image_x = button->x + (TOOLBAR_BUTTON_WIDTH - image_width) / 2;
    image_y = button_y
        + (TOOLBAR_BUTTON_HEIGHT - button->image_block->bi_hl) / 2;

    image_object.ob_next = -1;
    image_object.ob_head = -1;
    image_object.ob_tail = -1;
    image_object.ob_type = G_IMAGE;
    image_object.ob_flags = 0;
    image_object.ob_state = 0;
    image_object.ob_spec.bitblk = button->image_block;
    image_object.ob_x = image_x;
    image_object.ob_y = image_y;
    image_object.ob_width = image_width;
    image_object.ob_height = button->image_block->bi_hl;
    objc_draw(
        &image_object,
        0,
        MAX_DEPTH,
        clip_x,
        clip_y,
        clip_width,
        clip_height
    );
}

/**
 * Redraws the toolbar in all visible portions of its window.
 *
 * @param redraw_area Area requested by the AES redraw message.
 */
static void redraw_toolbar(const GRECT *redraw_area)
{
    short visible_x;
    short visible_y;
    short visible_width;
    short visible_height;
    short clip[4];
    short background[4];
    short index;
    short left;
    short top;
    short right;
    short bottom;

    app_logger_write("Toolbar redraw started.");
    if (!wind_update(BEG_UPDATE))
    {
        app_logger_write("Toolbar redraw could not lock window updates.");
        return;
    }

    if (wind_get(
        toolbar_handle,
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
            bottom = visible_y + visible_height - 1;
            if (bottom > redraw_area->g_y + redraw_area->g_h - 1)
            {
                bottom = redraw_area->g_y + redraw_area->g_h - 1;
            }

            if (right >= left && bottom >= top)
            {
                clip[0] = left;
                clip[1] = top;
                clip[2] = right;
                clip[3] = bottom;
                vs_clip(toolbar_vdi_handle, 1, clip);

                background[0] = toolbar_bounds.g_x;
                background[1] = toolbar_bounds.g_y;
                background[2] = toolbar_bounds.g_x + toolbar_bounds.g_w - 1;
                background[3] = toolbar_bounds.g_y + toolbar_bounds.g_h - 1;
                vsf_color(toolbar_vdi_handle, 0);
                vsf_interior(toolbar_vdi_handle, FIS_SOLID);
                vr_recfl(toolbar_vdi_handle, background);

                for (index = 0; index < toolbar_button_count(); index++)
                {
                    app_logger_write("Drawing a toolbar icon.");
                    draw_button(
                        &toolbar_buttons[index],
                        left,
                        top,
                        right - left + 1,
                        bottom - top + 1
                    );
                    app_logger_write("Toolbar icon drawing completed.");
                }

                vsl_color(toolbar_vdi_handle, 1);
                vsl_type(toolbar_vdi_handle, 1);
                clip[0] = toolbar_bounds.g_x;
                clip[1] = toolbar_bounds.g_y + toolbar_bounds.g_h - 1;
                clip[2] = toolbar_bounds.g_x + toolbar_bounds.g_w - 1;
                clip[3] = clip[1];
                v_pline(toolbar_vdi_handle, 2, clip);
            }

            if (!wind_get(
                toolbar_handle,
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
    }

    vs_clip(toolbar_vdi_handle, 0, clip);
    wind_update(END_UPDATE);
    app_logger_write("Toolbar redraw completed.");
}

/**
 * Creates and displays the fixed toolbar below the GEM menu bar.
 *
 * @return Non-zero if the toolbar was created and opened successfully.
 */
short toolbar_show(void)
{
    short physical_handle;
    short character_width;
    short character_height;
    short box_width;
    short box_height;
    short work_in[11];
    short work_out[57];
    short index;
    short desktop_x;
    short desktop_y;
    short desktop_width;
    short desktop_height;

    if (toolbar_handle != 0)
    {
        return 1;
    }

    app_logger_write("Opening toolbar VDI workstation.");
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
    v_opnvwk(work_in, &toolbar_vdi_handle, work_out);
    if (toolbar_vdi_handle == 0)
    {
        app_alerts_show(APP_ALERT_TOOLBAR_VDI_OPEN_FAILED);
        return 0;
    }
    app_logger_write("Toolbar VDI workstation opened.");

    if (!load_toolbar_icons())
    {
        app_alerts_show(APP_ALERT_TOOLBAR_ICON_MISSING);
        v_clsvwk(toolbar_vdi_handle);
        toolbar_vdi_handle = 0;
        return 0;
    }

    app_logger_write("Getting desktop work area for toolbar.");
    if (!wind_get(
        0,
        WF_WORKXYWH,
        &desktop_x,
        &desktop_y,
        &desktop_width,
        &desktop_height
    ))
    {
        app_alerts_show(APP_ALERT_DESKTOP_SIZE_FAILED);
        v_clsvwk(toolbar_vdi_handle);
        toolbar_vdi_handle = 0;
        return 0;
    }

    toolbar_bounds.g_x = desktop_x;
    toolbar_bounds.g_y = desktop_y;
    toolbar_bounds.g_w = desktop_width;
    toolbar_bounds.g_h = TOOLBAR_HEIGHT;
    app_logger_write("Creating borderless toolbar window.");

    desktop_x += TOOLBAR_BUTTON_GAP;
    for (index = 0; index < toolbar_button_count(); index++)
    {
        toolbar_buttons[index].x = desktop_x;
        desktop_x += TOOLBAR_BUTTON_WIDTH + TOOLBAR_BUTTON_GAP;
    }

    toolbar_handle = wind_create(
        0,
        toolbar_bounds.g_x,
        toolbar_bounds.g_y,
        toolbar_bounds.g_w,
        toolbar_bounds.g_h
    );
    if (toolbar_handle <= 0)
    {
        toolbar_handle = 0;
        app_alerts_show(APP_ALERT_TOOLBAR_WINDOW_CREATE_FAILED);
        v_clsvwk(toolbar_vdi_handle);
        toolbar_vdi_handle = 0;
        return 0;
    }
    app_logger_write("Opening toolbar window.");
    if (!wind_open(
        toolbar_handle,
        toolbar_bounds.g_x,
        toolbar_bounds.g_y,
        toolbar_bounds.g_w,
        toolbar_bounds.g_h
    ))
    {
        wind_delete(toolbar_handle);
        toolbar_handle = 0;
        app_alerts_show(APP_ALERT_TOOLBAR_WINDOW_OPEN_FAILED);
        v_clsvwk(toolbar_vdi_handle);
        toolbar_vdi_handle = 0;
        return 0;
    }

    app_logger_write("Toolbar displayed.");
    return 1;
}

/**
 * Closes the toolbar window and releases its VDI workstation.
 */
void toolbar_hide(void)
{
    short toolbar_was_open;

    toolbar_was_open = toolbar_handle != 0;
    if (toolbar_handle != 0)
    {
        if (!wind_close(toolbar_handle))
        {
            app_alerts_show(APP_ALERT_TOOLBAR_WINDOW_CLOSE_FAILED);
            return;
        }
        if (!wind_delete(toolbar_handle))
        {
            app_alerts_show(APP_ALERT_TOOLBAR_WINDOW_DELETE_FAILED);
            return;
        }
        toolbar_handle = 0;
    }

    if (toolbar_vdi_handle != 0)
    {
        v_clsvwk(toolbar_vdi_handle);
        toolbar_vdi_handle = 0;
    }
    toolbar_bounds.g_h = 0;
    if (toolbar_was_open)
    {
        app_logger_write("Toolbar closed.");
    }
}

/**
 * Returns the vertical space reserved for the toolbar.
 *
 * @return Toolbar height in pixels, or zero when it is not shown.
 */
short toolbar_get_height(void)
{
    return toolbar_bounds.g_h;
}

/**
 * Handles toolbar redraw messages.
 *
 * @param message Eight-word AES message buffer.
 */
void toolbar_handle_message(const short message[8])
{
    GRECT redraw_area;

    if (toolbar_handle == 0 || message[0] != WM_REDRAW
        || message[3] != toolbar_handle)
    {
        return;
    }

    redraw_area.g_x = message[4];
    redraw_area.g_y = message[5];
    redraw_area.g_w = message[6];
    redraw_area.g_h = message[7];
    redraw_toolbar(&redraw_area);
}

/**
 * Returns the toolbar action at the specified screen coordinates.
 *
 * @param x Mouse X coordinate.
 * @param y Mouse Y coordinate.
 * @return The selected action, or MENU_ACTION_NONE if no active button was hit.
 */
MenuAction toolbar_handle_click(short x, short y)
{
    short index;
    short button_top;
    short button_bottom;

    button_top = toolbar_bounds.g_y
        + (toolbar_bounds.g_h - TOOLBAR_BUTTON_HEIGHT) / 2;
    button_bottom = button_top + TOOLBAR_BUTTON_HEIGHT;

    for (index = 0; index < toolbar_button_count(); index++)
    {
        if (x >= toolbar_buttons[index].x
            && x < toolbar_buttons[index].x + TOOLBAR_BUTTON_WIDTH
            && y >= button_top
            && y < button_bottom)
        {
            switch (toolbar_buttons[index].action)
            {
                case MENU_ACTION_QUIT:
                    app_logger_write("Toolbar: Quit button clicked.");
                    break;

                case MENU_ACTION_NEW:
                    app_logger_write("Toolbar: New button clicked.");
                    break;

                case MENU_ACTION_OPEN:
                    app_logger_write("Toolbar: Open button clicked.");
                    break;

                case MENU_ACTION_CLOSE:
                    break;

                case MENU_ACTION_ABOUT:
                    app_logger_write("Toolbar: Info button clicked.");
                    break;

                case MENU_ACTION_NONE:
                    break;
            }
            return toolbar_buttons[index].action;
        }
    }

    return MENU_ACTION_NONE;
}
