#include <gem.h>

#include "about_dialog.h"

/** GEM alert containing the application's name and platform. */
static char about_alert[] =
    "[1][Ballerburgen-Editor|Atari Demo Version][OK]";

/**
 * Displays the application information dialog.
 */
void about_dialog_show(void)
{
    form_alert(1, about_alert);
}
