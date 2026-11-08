#ifndef BALLERBURGENEDITOR_ATARI_ST_CASTLE_DATA_H
#define BALLERBURGENEDITOR_ATARI_ST_CASTLE_DATA_H

#include <stddef.h>

/**
 * Fixed values and command markers defined by the BALLER.DAT format.
 */
enum
{
    /** Number of cannon-position pairs stored in each castle record. */
    CASTLE_DATA_CANNON_POSITION_COUNT = 10,
    /** Number of reserved values stored after each castle's resource values. */
    CASTLE_DATA_RESERVED_VALUE_COUNT = 4,
    /** Marker that terminates the drawing data for one castle. */
    CASTLE_DATA_RECORD_TERMINATOR = -1,
    /** Marker that terminates the complete castle file. */
    CASTLE_DATA_FILE_TERMINATOR = -999,
    /** Marker that terminates a polygon's coordinate list. */
    CASTLE_DATA_POLYGON_TERMINATOR = -9,
    /** Marker that introduces a fill-style and fill-index pair. */
    CASTLE_DATA_FILL_STYLE_MARKER = -2,
    /** Marker that switches between filled polygons and outlines. */
    CASTLE_DATA_DRAW_MODE_MARKER = -4
};

/**
 * One coordinate pair in a castle's local coordinate system.
 */
typedef struct
{
    int x;
    int y;
} CastleDataPoint;

/**
 * Width and height of a castle storage area.
 */
typedef struct
{
    int width;
    int height;
} CastleDataSize;

/**
 * The four initial resource values stored in a castle record.
 */
typedef struct
{
    int money;
    int powder;
    int cannonballs;
    int people;
} CastleDataResources;

/**
 * A polygon in the castle drawing data.
 */
typedef struct
{
    CastleDataPoint *points;
    size_t point_count;
} CastleDataPolygon;

/**
 * Kind of drawing operation in the castle drawing stream.
 */
typedef enum
{
    /** A polygon terminated by the file-format marker -9. */
    CASTLE_DATA_DRAW_POLYGON,
    /** A fill-style change introduced by the file-format marker -2. */
    CASTLE_DATA_DRAW_FILL_STYLE,
    /** A fill/outline mode toggle introduced by the file-format marker -4. */
    CASTLE_DATA_DRAW_TOGGLE_MODE
} CastleDataDrawingType;

/**
 * One ordered drawing operation from a castle record.
 */
typedef struct
{
    CastleDataDrawingType type;
    union
    {
        CastleDataPolygon polygon;
        struct
        {
            int style;
            int index;
        } fill_style;
    } value;
} CastleDataDrawing;

/**
 * Parsed geometry, objects, storage areas, and initial resources for a castle.
 */
typedef struct
{
    int width;
    CastleDataPoint cannons[CASTLE_DATA_CANNON_POSITION_COUNT];
    size_t cannon_count;
    CastleDataPoint throne;
    CastleDataPoint wind_vane;
    CastleDataPoint money;
    CastleDataPoint powder;
    CastleDataPoint cannonballs;
    CastleDataSize money_storage;
    CastleDataSize powder_storage;
    CastleDataSize cannonball_storage;
    CastleDataResources resources;
    int reserved[CASTLE_DATA_RESERVED_VALUE_COUNT];
    CastleDataDrawing *drawing;
    size_t drawing_count;
} CastleDataCastle;

/**
 * An ordered collection of castles loaded from one BALLER.DAT file.
 */
typedef struct
{
    CastleDataCastle *castles;
    size_t castle_count;
} CastleDataDocument;

/**
 * Result of reading a BALLER.DAT file.
 */
typedef enum
{
    /** The complete file was parsed successfully. */
    CASTLE_DATA_READ_OK,
    /** The path is invalid or the file could not be read completely. */
    CASTLE_DATA_READ_IO_ERROR,
    /** The file contains malformed syntax or an incomplete record. */
    CASTLE_DATA_READ_FORMAT_ERROR,
    /** Memory allocation for the parsed document failed. */
    CASTLE_DATA_READ_OUT_OF_MEMORY,
    /** A required function argument was null. */
    CASTLE_DATA_READ_INVALID_ARGUMENT
} CastleDataReadResult;

/**
 * Reads a BALLER.DAT file, replacing the initialized output document on success.
 *
 * @param path File path to read.
 * @param document Initialized destination document.
 * @param error_token Receives the one-based numeric token position on format
 *                    errors; may be null.
 * @return The read result.
 */
CastleDataReadResult castle_data_read(
    const char *path,
    CastleDataDocument *document,
    size_t *error_token
);

/**
 * Frees all allocations owned by a castle document and resets it to empty.
 *
 * @param document Document to release; may be null.
 */
void castle_data_document_destroy(CastleDataDocument *document);

/**
 * Removes a castle from the document at the specified index.
 *
 * @param document Document to modify.
 * @param index Zero-based index of the castle to remove.
 * @return Non-zero if the castle was removed successfully.
 */
short castle_data_remove_castle(CastleDataDocument *document, size_t index);

/**
 * Appends a new castle with default values to the document.
 *
 * @param document Document receiving the new castle.
 * @return Non-zero if the castle was added successfully.
 */
short castle_data_add_castle(CastleDataDocument *document);

/**
 * Inserts a new castle with default values at the specified position.
 *
 * @param document Document receiving the new castle.
 * @param index Zero-based index where the castle should be inserted.
 * @return Non-zero if the castle was inserted successfully.
 */
short castle_data_insert_castle(CastleDataDocument *document, size_t index);

#endif
