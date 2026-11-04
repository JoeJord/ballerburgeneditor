#include <gem.h>

#include "BALLERED.h"
#include "app_resources.h"

/** Resource filename used by the Atari application. */
static char resource_file[] = "BALLERED.RSC";
/** Whether the resource file is currently loaded. */
static short resource_loaded;

/**
 * Loads the application's GEM resource file.
 *
 * @return Non-zero if the resource file is loaded or was loaded successfully.
 */
short app_resources_load(void)
{
    if (resource_loaded)
    {
        return 1;
    }

    resource_loaded = rsrc_load(resource_file);
    return resource_loaded;
}

/**
 * Returns a tree from the loaded resource file.
 *
 * @param tree_index Resource tree index.
 * @return The requested tree, or 0 if unavailable.
 */
static OBJECT *get_resource_tree(short tree_index)
{
    OBJECT *tree;

    if (!resource_loaded || !rsrc_gaddr(R_TREE, tree_index, &tree))
    {
        return 0;
    }

    return tree;
}

/**
 * Returns the main menu resource tree.
 *
 * @return The menu tree, or 0 if it is unavailable.
 */
OBJECT *app_resources_get_main_menu(void)
{
    return get_resource_tree(MAIN);
}

/**
 * Returns the About dialog resource tree.
 *
 * @return The About dialog tree, or 0 if it is unavailable.
 */
OBJECT *app_resources_get_about_dialog(void)
{
    return get_resource_tree(DLG_ABOUT);
}

/**
 * Releases the application's GEM resource file if it is loaded.
 */
void app_resources_release(void)
{
    if (resource_loaded)
    {
        rsrc_free();
        resource_loaded = 0;
    }
}
