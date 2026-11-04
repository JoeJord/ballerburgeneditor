#include <gem.h>

#include "BALLERED.h"
#include "about_dialog.h"

/**
 * Displays the application information dialog.
 */
void about_dialog_show(void)
{
    /** About dialog object tree from the loaded resource file. */
    OBJECT *about_tree;
    /** Centered dialog bounds. */
    short x;
    short y;
    short width;
    short height;
    /** Object returned by form_do() when the dialog is dismissed. */
    short exit_object;

    if (!rsrc_gaddr(R_TREE, DLG_ABOUT, &about_tree))
    {
        form_alert(1, "[1][About-Dialog fehlt in BALLERED.RSC.][OK]");
        return;
    }

    form_center(about_tree, &x, &y, &width, &height);
    form_dial(FMD_START, 0, 0, 0, 0, x, y, width, height);
    objc_draw(about_tree, ROOT, MAX_DEPTH, x, y, width, height);
    exit_object = form_do(about_tree, 0);
    if (exit_object >= 0)
    {
        exit_object &= 0x7fff;
        if (exit_object == BTN_ABOUT_OK)
        {
            about_tree[BTN_ABOUT_OK].ob_state &= ~OS_SELECTED;
        }
    }
    form_dial(FMD_FINISH, 0, 0, 0, 0, x, y, width, height);
}
