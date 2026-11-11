#include <ctype.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "castle_data.h"

/** Streaming lexical state and the first error found while parsing. */
typedef struct
{
    FILE *file;
    size_t token_count;
    size_t error_token;
    short in_comment;
    CastleDataReadResult result;
} CastleDataParser;

/**
 * Records the first parser error so cleanup does not hide its cause.
 *
 * @param parser Parser state to update.
 * @param result Error result to preserve.
 */
static void set_parser_error(
    CastleDataParser *parser,
    CastleDataReadResult result
)
{
    if (parser->result == CASTLE_DATA_READ_OK)
    {
        parser->result = result;
    }
}

/**
 * Reads the next signed decimal token, skipping whitespace, commas, and remarks.
 *
 * Remarks are delimited by asterisks, matching the original game and Java
 * editor. The return value distinguishes end-of-input from parser errors.
 *
 * @param parser Parser state.
 * @param value Receives the parsed integer.
 * @return 1 for a value, 0 at clean end-of-input, or -1 on error.
 */
static short next_integer(CastleDataParser *parser, int *value)
{
    int character;
    int sign;
    unsigned long magnitude;
    unsigned long limit;
    unsigned int digit;

    while (1)
    {
        character = fgetc(parser->file);
        if (character == EOF)
        {
            parser->error_token = parser->token_count + 1;
            if (ferror(parser->file))
            {
                set_parser_error(parser, CASTLE_DATA_READ_IO_ERROR);
                return -1;
            }
            if (parser->in_comment)
            {
                set_parser_error(parser, CASTLE_DATA_READ_FORMAT_ERROR);
                return -1;
            }
            return 0;
        }

        if (parser->in_comment)
        {
            if (character == '*')
            {
                parser->in_comment = 0;
            }
            continue;
        }
        if (character == '*')
        {
            parser->in_comment = 1;
            continue;
        }
        if (character == ',' || isspace((unsigned char)character))
        {
            continue;
        }
        break;
    }

    parser->error_token = parser->token_count + 1;
    sign = 1;
    if (character == '-' || character == '+')
    {
        sign = character == '-' ? -1 : 1;
        character = fgetc(parser->file);
        if (character == EOF)
        {
            parser->error_token = parser->token_count + 1;
            set_parser_error(
                parser,
                ferror(parser->file)
                    ? CASTLE_DATA_READ_IO_ERROR
                    : CASTLE_DATA_READ_FORMAT_ERROR
            );
            return -1;
        }
    }
    if (character < '0' || character > '9')
    {
        set_parser_error(parser, CASTLE_DATA_READ_FORMAT_ERROR);
        return -1;
    }

    limit = sign < 0 ? (unsigned long)INT_MAX + 1UL : (unsigned long)INT_MAX;
    magnitude = 0;
    do
    {
        digit = (unsigned int)(character - '0');
        if (magnitude > (limit - digit) / 10UL)
        {
            set_parser_error(parser, CASTLE_DATA_READ_FORMAT_ERROR);
            return -1;
        }
        magnitude = magnitude * 10UL + digit;
        character = fgetc(parser->file);
    }
    while (character >= '0' && character <= '9');

    if (character == EOF)
    {
        if (ferror(parser->file))
        {
            set_parser_error(parser, CASTLE_DATA_READ_IO_ERROR);
            return -1;
        }
    }
    else if (character != ',' && character != '*' &&
        !isspace((unsigned char)character))
    {
        set_parser_error(parser, CASTLE_DATA_READ_FORMAT_ERROR);
        return -1;
    }
    else if (ungetc(character, parser->file) == EOF)
    {
        set_parser_error(parser, CASTLE_DATA_READ_IO_ERROR);
        return -1;
    }

    if (sign < 0)
    {
        *value = magnitude == (unsigned long)INT_MAX + 1UL
            ? INT_MIN
            : -(int)magnitude;
    }
    else
    {
        *value = (int)magnitude;
    }
    parser->token_count++;
    return 1;
}

/**
 * Reads one required integer and reports a missing value as malformed input.
 *
 * @param parser Parser state.
 * @param value Receives the integer.
 * @return Non-zero if a value was read.
 */
static short read_required_integer(CastleDataParser *parser, int *value)
{
    short result;

    result = next_integer(parser, value);
    if (result == 0)
    {
        set_parser_error(parser, CASTLE_DATA_READ_FORMAT_ERROR);
    }
    return result == 1;
}

/**
 * Reads one coordinate pair from the token stream.
 *
 * @param parser Parser state.
 * @param point Receives the coordinates.
 * @return Non-zero if both coordinates were read.
 */
static short read_point(CastleDataParser *parser, CastleDataPoint *point)
{
    return read_required_integer(parser, &point->x)
        && read_required_integer(parser, &point->y);
}

/**
 * Grows an array geometrically while checking allocation-size overflow.
 *
 * @param array Current allocation, or null for a new array.
 * @param capacity Current element capacity.
 * @param required Required element count.
 * @param element_size Size of one element.
 * @param new_capacity Receives the capacity of the returned allocation.
 * @return The resized allocation, or null on overflow or allocation failure.
 */
static void *reserve_array(
    void *array,
    size_t capacity,
    size_t required,
    size_t element_size,
    size_t *new_capacity
)
{
    size_t maximum_count;
    size_t capacity_candidate;
    void *resized_array;

    maximum_count = (size_t)-1 / element_size;
    if (required > maximum_count)
    {
        return 0;
    }
    if (required <= capacity)
    {
        *new_capacity = capacity;
        return array;
    }

    capacity_candidate = capacity ? capacity : 8;
    while (capacity_candidate < required)
    {
        if (capacity_candidate > maximum_count / 2)
        {
            capacity_candidate = required;
            break;
        }
        capacity_candidate *= 2;
    }
    if (capacity_candidate > maximum_count)
    {
        return 0;
    }

    resized_array = realloc(
        array,
        capacity_candidate * element_size
    );
    if (!resized_array)
    {
        return 0;
    }

    *new_capacity = capacity_candidate;
    return resized_array;
}

/**
 * Appends a point to a dynamically sized point array.
 *
 * @param points Address of the point array.
 * @param count Address of the current point count.
 * @param capacity Address of the current allocation capacity.
 * @param point Point to append.
 * @return Non-zero if the point was appended.
 */
static short append_point(
    CastleDataPoint **points,
    size_t *count,
    size_t *capacity,
    CastleDataPoint point
)
{
    CastleDataPoint *resized_points;
    size_t new_capacity;

    resized_points = (CastleDataPoint *)reserve_array(
        *points,
        *capacity,
        *count + 1,
        sizeof(**points),
        &new_capacity
    );
    if (!resized_points)
    {
        return 0;
    }

    resized_points[*count] = point;
    *points = resized_points;
    *capacity = new_capacity;
    (*count)++;
    return 1;
}

/**
 * Releases the dynamic drawing data owned by one castle.
 *
 * @param castle Castle to release.
 */
static void destroy_castle(CastleDataCastle *castle)
{
    size_t index;

    for (index = 0; index < castle->drawing_count; index++)
    {
        if (castle->drawing[index].type == CASTLE_DATA_DRAW_POLYGON)
        {
            free(castle->drawing[index].value.polygon.points);
        }
    }
    free(castle->drawing);
    castle->drawing = 0;
    castle->drawing_count = 0;
}

/**
 * Appends a drawing operation to a castle.
 *
 * Ownership of polygon point storage transfers only when this succeeds.
 *
 * @param castle Castle receiving the operation.
 * @param drawing_capacity Address of the drawing array capacity.
 * @param drawing Operation to append.
 * @return Non-zero if the operation was appended.
 */
static short append_drawing(
    CastleDataCastle *castle,
    size_t *drawing_capacity,
    CastleDataDrawing drawing
)
{
    CastleDataDrawing *resized_drawing;
    size_t new_capacity;

    resized_drawing = (CastleDataDrawing *)reserve_array(
        castle->drawing,
        *drawing_capacity,
        castle->drawing_count + 1,
        sizeof(*castle->drawing),
        &new_capacity
    );
    if (!resized_drawing)
    {
        return 0;
    }

    resized_drawing[castle->drawing_count] = drawing;
    castle->drawing = resized_drawing;
    *drawing_capacity = new_capacity;
    castle->drawing_count++;
    return 1;
}

/**
 * Reads one polygon, including its initial X coordinate and -9 terminator.
 *
 * @param parser Parser state.
 * @param castle Castle receiving the polygon.
 * @param drawing_capacity Address of the drawing array capacity.
 * @param first_x First vertex's X coordinate, already read.
 * @return Non-zero if the polygon was read and stored.
 */
static short read_polygon(
    CastleDataParser *parser,
    CastleDataCastle *castle,
    size_t *drawing_capacity,
    int first_x
)
{
    CastleDataDrawing drawing;
    CastleDataPoint point;
    int x;
    size_t point_capacity;

    memset(&drawing, 0, sizeof(drawing));
    drawing.type = CASTLE_DATA_DRAW_POLYGON;
    point_capacity = 0;
    point.x = first_x;
    if (!read_required_integer(parser, &point.y)
        || !append_point(
            &drawing.value.polygon.points,
            &drawing.value.polygon.point_count,
            &point_capacity,
            point
        ))
    {
        if (parser->result == CASTLE_DATA_READ_OK)
        {
            set_parser_error(parser, CASTLE_DATA_READ_OUT_OF_MEMORY);
        }
        free(drawing.value.polygon.points);
        return 0;
    }

    while (read_required_integer(parser, &x))
    {
        if (x == CASTLE_DATA_POLYGON_TERMINATOR)
        {
            if (!append_drawing(castle, drawing_capacity, drawing))
            {
                free(drawing.value.polygon.points);
                set_parser_error(parser, CASTLE_DATA_READ_OUT_OF_MEMORY);
                return 0;
            }
            return 1;
        }
        if (x < 0)
        {
            set_parser_error(parser, CASTLE_DATA_READ_FORMAT_ERROR);
            free(drawing.value.polygon.points);
            return 0;
        }

        point.x = x;
        if (!read_required_integer(parser, &point.y)
            || !append_point(
                &drawing.value.polygon.points,
                &drawing.value.polygon.point_count,
                &point_capacity,
                point
            ))
        {
            if (parser->result == CASTLE_DATA_READ_OK)
            {
                set_parser_error(parser, CASTLE_DATA_READ_OUT_OF_MEMORY);
            }
            free(drawing.value.polygon.points);
            return 0;
        }
    }

    free(drawing.value.polygon.points);
    return 0;
}

/**
 * Reads the drawing command stream through the castle terminator.
 *
 * @param parser Parser state.
 * @param castle Castle receiving the drawing data.
 * @param drawing_capacity Address of the drawing array capacity.
 * @return Non-zero if the drawing stream is complete.
 */
static short read_drawing(
    CastleDataParser *parser,
    CastleDataCastle *castle,
    size_t *drawing_capacity
)
{
    CastleDataDrawing drawing;
    int command;

    while (read_required_integer(parser, &command))
    {
        memset(&drawing, 0, sizeof(drawing));
        if (command == CASTLE_DATA_RECORD_TERMINATOR)
        {
            return 1;
        }
        if (command >= 0)
        {
            if (!read_polygon(parser, castle, drawing_capacity, command))
            {
                return 0;
            }
            continue;
        }
        if (command == CASTLE_DATA_FILL_STYLE_MARKER)
        {
            drawing.type = CASTLE_DATA_DRAW_FILL_STYLE;
            if (!read_required_integer(parser, &drawing.value.fill_style.style)
                || !read_required_integer(
                    parser,
                    &drawing.value.fill_style.index
                ))
            {
                return 0;
            }
        }
        else if (command == CASTLE_DATA_DRAW_MODE_MARKER)
        {
            drawing.type = CASTLE_DATA_DRAW_TOGGLE_MODE;
        }
        else
        {
            set_parser_error(parser, CASTLE_DATA_READ_FORMAT_ERROR);
            return 0;
        }

        if (!append_drawing(castle, drawing_capacity, drawing))
        {
            set_parser_error(parser, CASTLE_DATA_READ_OUT_OF_MEMORY);
            return 0;
        }
    }
    return 0;
}

/**
 * Reads one complete castle record after its width has been consumed.
 *
 * @param parser Parser state.
 * @param castle Receives the parsed castle.
 * @param width Castle width.
 * @return Non-zero if the entire record is valid.
 */
static short read_castle(
    CastleDataParser *parser,
    CastleDataCastle *castle,
    int width
)
{
    CastleDataPoint point;
    size_t index;
    size_t drawing_capacity;

    memset(castle, 0, sizeof(*castle));
    drawing_capacity = 0;
    castle->width = width;
    for (index = 0; index < CASTLE_DATA_CANNON_POSITION_COUNT; index++)
    {
        if (!read_point(parser, &point))
        {
            return 0;
        }
        if (point.x != -1 || point.y != -1)
        {
            castle->cannons[castle->cannon_count++] = point;
        }
    }

    if (!read_point(parser, &castle->throne)
        || !read_point(parser, &castle->wind_vane)
        || !read_point(parser, &castle->money)
        || !read_point(parser, &castle->powder)
        || !read_point(parser, &castle->cannonballs)
        || !read_required_integer(parser, &castle->money_storage.width)
        || !read_required_integer(parser, &castle->money_storage.height)
        || !read_required_integer(parser, &castle->powder_storage.width)
        || !read_required_integer(parser, &castle->powder_storage.height)
        || !read_required_integer(parser, &castle->cannonball_storage.width)
        || !read_required_integer(parser, &castle->cannonball_storage.height)
        || !read_required_integer(parser, &castle->resources.money)
        || !read_required_integer(parser, &castle->resources.powder)
        || !read_required_integer(parser, &castle->resources.cannonballs)
        || !read_required_integer(parser, &castle->resources.people))
    {
        return 0;
    }
    for (index = 0; index < CASTLE_DATA_RESERVED_VALUE_COUNT; index++)
    {
        if (!read_required_integer(parser, &castle->reserved[index]))
        {
            return 0;
        }
    }

    return read_drawing(parser, castle, &drawing_capacity);
}

/**
 * Appends a fully parsed castle to a document.
 *
 * @param document Document receiving the castle.
 * @param castle_capacity Address of the castle array capacity.
 * @param castle Castle to append.
 * @return Non-zero if the castle was appended.
 */
static short append_castle(
    CastleDataDocument *document,
    size_t *castle_capacity,
    const CastleDataCastle *castle
)
{
    CastleDataCastle *resized_castles;
    size_t new_capacity;

    resized_castles = (CastleDataCastle *)reserve_array(
        document->castles,
        *castle_capacity,
        document->castle_count + 1,
        sizeof(*document->castles),
        &new_capacity
    );
    if (!resized_castles)
    {
        return 0;
    }

    resized_castles[document->castle_count] = *castle;
    document->castles = resized_castles;
    *castle_capacity = new_capacity;
    document->castle_count++;
    return 1;
}

/**
 * Frees all allocations owned by a castle document and resets it to empty.
 *
 * @param document Document to release; may be null.
 */
void castle_data_document_destroy(CastleDataDocument *document)
{
    size_t index;

    if (!document)
    {
        return;
    }

    for (index = 0; index < document->castle_count; index++)
    {
        destroy_castle(&document->castles[index]);
    }
    free(document->castles);
    document->castles = 0;
    document->castle_count = 0;
}

/**
 * Removes a castle from the document at the specified index.
 *
 * @param document Document to modify.
 * @param index Zero-based index of the castle to remove.
 * @return Non-zero if the castle was removed successfully.
 */
short castle_data_remove_castle(CastleDataDocument *document, size_t index)
{
    size_t move_count;

    if (!document || index >= document->castle_count)
    {
        return 0;
    }

    destroy_castle(&document->castles[index]);
    move_count = document->castle_count - index - 1;
    if (move_count > 0)
    {
        memmove(
            &document->castles[index],
            &document->castles[index + 1],
            move_count * sizeof(*document->castles)
        );
    }
    document->castle_count--;
    return 1;
}

/**
 * Appends a new castle with default values to the document.
 *
 * @param document Document receiving the new castle.
 * @return Non-zero if the castle was added successfully.
 */
short castle_data_add_castle(CastleDataDocument *document)
{
    CastleDataCastle castle;
    size_t castle_capacity;
    size_t index;

    if (!document)
    {
        return 0;
    }

    memset(&castle, 0, sizeof(castle));
    castle.width = 100;
    castle.cannon_count = 0;
    castle.throne.x = -1;
    castle.throne.y = -1;
    castle.wind_vane.x = -1;
    castle.wind_vane.y = -1;
    castle.money.x = -1;
    castle.money.y = -1;
    castle.powder.x = -1;
    castle.powder.y = -1;
    castle.cannonballs.x = -1;
    castle.cannonballs.y = -1;
    castle.money_storage.width = 0;
    castle.money_storage.height = 0;
    castle.powder_storage.width = 0;
    castle.powder_storage.height = 0;
    castle.cannonball_storage.width = 0;
    castle.cannonball_storage.height = 0;
    castle.resources.money = 0;
    castle.resources.powder = 0;
    castle.resources.cannonballs = 0;
    castle.resources.people = 0;
    for (index = 0; index < CASTLE_DATA_RESERVED_VALUE_COUNT; index++)
    {
        castle.reserved[index] = 0;
    }
    castle.drawing = 0;
    castle.drawing_count = 0;

    castle_capacity = document->castle_count;
    if (!append_castle(document, &castle_capacity, &castle))
    {
        return 0;
    }

    return 1;
}

/**
 * Inserts a new castle with default values at the specified position.
 *
 * @param document Document receiving the new castle.
 * @param index Zero-based index where the castle should be inserted.
 * @return Non-zero if the castle was inserted successfully.
 */
short castle_data_insert_castle(CastleDataDocument *document, size_t index)
{
    CastleDataCastle castle;
    CastleDataCastle *resized_castles;
    size_t castle_capacity;
    size_t new_capacity;
    size_t move_count;
    size_t i;

    if (!document)
    {
        return 0;
    }

    if (index > document->castle_count)
    {
        index = document->castle_count;
    }

    memset(&castle, 0, sizeof(castle));
    castle.width = 100;
    castle.cannon_count = 0;
    castle.throne.x = -1;
    castle.throne.y = -1;
    castle.wind_vane.x = -1;
    castle.wind_vane.y = -1;
    castle.money.x = -1;
    castle.money.y = -1;
    castle.powder.x = -1;
    castle.powder.y = -1;
    castle.cannonballs.x = -1;
    castle.cannonballs.y = -1;
    castle.money_storage.width = 0;
    castle.money_storage.height = 0;
    castle.powder_storage.width = 0;
    castle.powder_storage.height = 0;
    castle.cannonball_storage.width = 0;
    castle.cannonball_storage.height = 0;
    castle.resources.money = 0;
    castle.resources.powder = 0;
    castle.resources.cannonballs = 0;
    castle.resources.people = 0;
    for (i = 0; i < CASTLE_DATA_RESERVED_VALUE_COUNT; i++)
    {
        castle.reserved[i] = 0;
    }
    castle.drawing = 0;
    castle.drawing_count = 0;

    castle_capacity = document->castle_count;
    resized_castles = (CastleDataCastle *)reserve_array(
        document->castles,
        castle_capacity,
        document->castle_count + 1,
        sizeof(*document->castles),
        &new_capacity
    );
    if (!resized_castles)
    {
        return 0;
    }

    document->castles = resized_castles;

    move_count = document->castle_count - index;
    if (move_count > 0)
    {
        memmove(
            &document->castles[index + 1],
            &document->castles[index],
            move_count * sizeof(*document->castles)
        );
    }

    document->castles[index] = castle;
    document->castle_count++;
    return 1;
}

/**
 * Reads a BALLER.DAT file into an initialized document.
 *
 * The destination remains unchanged unless the complete file, including its
 * -999 terminator and all following comments, has been validated.
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
)
{
    CastleDataParser parser;
    CastleDataDocument parsed_document;
    CastleDataCastle castle;
    CastleDataReadResult result;
    int value;
    size_t castle_capacity;
    short token_result;

    if (!path || !document)
    {
        return CASTLE_DATA_READ_INVALID_ARGUMENT;
    }
    if (error_token)
    {
        *error_token = 0;
    }
    memset(&parsed_document, 0, sizeof(parsed_document));
    memset(&parser, 0, sizeof(parser));
    castle_capacity = 0;
    parser.result = CASTLE_DATA_READ_OK;
    parser.file = fopen(path, "rb");
    if (!parser.file)
    {
        return CASTLE_DATA_READ_IO_ERROR;
    }

    while (1)
    {
        token_result = next_integer(&parser, &value);
        if (token_result == 0)
        {
            set_parser_error(&parser, CASTLE_DATA_READ_FORMAT_ERROR);
            break;
        }
        if (token_result < 0)
        {
            break;
        }
        if (value == CASTLE_DATA_FILE_TERMINATOR)
        {
            token_result = next_integer(&parser, &value);
            if (token_result == 1)
            {
                set_parser_error(&parser, CASTLE_DATA_READ_FORMAT_ERROR);
            }
            else if (token_result == 0)
            {
                parser.result = CASTLE_DATA_READ_OK;
            }
            break;
        }
        if (value < 0)
        {
            set_parser_error(&parser, CASTLE_DATA_READ_FORMAT_ERROR);
            break;
        }
        if (!read_castle(&parser, &castle, value))
        {
            destroy_castle(&castle);
            break;
        }
        if (!append_castle(&parsed_document, &castle_capacity, &castle))
        {
            destroy_castle(&castle);
            set_parser_error(&parser, CASTLE_DATA_READ_OUT_OF_MEMORY);
            break;
        }
    }

    result = parser.result;
    if (fclose(parser.file) != 0 && result == CASTLE_DATA_READ_OK)
    {
        result = CASTLE_DATA_READ_IO_ERROR;
    }
    if (error_token && result == CASTLE_DATA_READ_FORMAT_ERROR)
    {
        *error_token = parser.error_token;
    }
    if (result != CASTLE_DATA_READ_OK)
    {
        castle_data_document_destroy(&parsed_document);
        return result;
    }

    castle_data_document_destroy(document);
    *document = parsed_document;
    return CASTLE_DATA_READ_OK;
}

/**
 * Writes a single integer token to the file.
 *
 * @param file File to write to.
 * @param value Integer value to write.
 * @return Non-zero if the value was written successfully.
 */
static short write_integer(FILE *file, int value)
{
    return fprintf(file, "%d ", value) >= 0;
}

/**
 * Writes a castle's drawing commands to the file.
 *
 * @param file File to write to.
 * @param castle Castle whose drawing to write.
 * @return Non-zero if the drawing was written successfully.
 */
static short write_drawing(FILE *file, const CastleDataCastle *castle)
{
    size_t index;
    size_t point_index;

    for (index = 0; index < castle->drawing_count; index++)
    {
        const CastleDataDrawing *drawing = &castle->drawing[index];

        switch (drawing->type)
        {
            case CASTLE_DATA_DRAW_POLYGON:
                for (point_index = 0;
                     point_index < drawing->value.polygon.point_count;
                     point_index++)
                {
                    const CastleDataPoint *point =
                        &drawing->value.polygon.points[point_index];
                    if (!write_integer(file, point->x)
                        || !write_integer(file, point->y))
                    {
                        return 0;
                    }
                }
                if (!write_integer(file, CASTLE_DATA_POLYGON_TERMINATOR))
                {
                    return 0;
                }
                break;

            case CASTLE_DATA_DRAW_FILL_STYLE:
                if (!write_integer(file, CASTLE_DATA_FILL_STYLE_MARKER)
                    || !write_integer(file, drawing->value.fill_style.style)
                    || !write_integer(file, drawing->value.fill_style.index))
                {
                    return 0;
                }
                break;

            case CASTLE_DATA_DRAW_TOGGLE_MODE:
                if (!write_integer(file, CASTLE_DATA_DRAW_MODE_MARKER))
                {
                    return 0;
                }
                break;
        }
    }
    return 1;
}

/**
 * Writes a single castle record to the file.
 *
 * @param file File to write to.
 * @param castle Castle to write.
 * @return Non-zero if the castle was written successfully.
 */
static short write_castle(FILE *file, const CastleDataCastle *castle)
{
    size_t index;

    if (!write_integer(file, castle->width))
    {
        return 0;
    }

    for (index = 0; index < CASTLE_DATA_CANNON_POSITION_COUNT; index++)
    {
        const CastleDataPoint *point = &castle->cannons[index];
        if (index < castle->cannon_count)
        {
            if (!write_integer(file, point->x) || !write_integer(file, point->y))
            {
                return 0;
            }
        }
        else
        {
            if (!write_integer(file, -1) || !write_integer(file, -1))
            {
                return 0;
            }
        }
    }

    if (!write_integer(file, castle->throne.x)
        || !write_integer(file, castle->throne.y)
        || !write_integer(file, castle->wind_vane.x)
        || !write_integer(file, castle->wind_vane.y)
        || !write_integer(file, castle->money.x)
        || !write_integer(file, castle->money.y)
        || !write_integer(file, castle->powder.x)
        || !write_integer(file, castle->powder.y)
        || !write_integer(file, castle->cannonballs.x)
        || !write_integer(file, castle->cannonballs.y)
        || !write_integer(file, castle->money_storage.width)
        || !write_integer(file, castle->money_storage.height)
        || !write_integer(file, castle->powder_storage.width)
        || !write_integer(file, castle->powder_storage.height)
        || !write_integer(file, castle->cannonball_storage.width)
        || !write_integer(file, castle->cannonball_storage.height)
        || !write_integer(file, castle->resources.money)
        || !write_integer(file, castle->resources.powder)
        || !write_integer(file, castle->resources.cannonballs)
        || !write_integer(file, castle->resources.people))
    {
        return 0;
    }

    for (index = 0; index < CASTLE_DATA_RESERVED_VALUE_COUNT; index++)
    {
        if (!write_integer(file, castle->reserved[index]))
        {
            return 0;
        }
    }

    if (!write_drawing(file, castle))
    {
        return 0;
    }

    if (!write_integer(file, CASTLE_DATA_RECORD_TERMINATOR))
    {
        return 0;
    }

    return 1;
}

/**
 * Writes a castles file from a document.
 *
 * @param path File path to write.
 * @param document Document to write.
 * @return The write result.
 */
CastleDataWriteResult castle_data_write(
    const char *path,
    const CastleDataDocument *document
)
{
    FILE *file;
    size_t index;

    if (!path || !document)
    {
        return CASTLE_DATA_WRITE_INVALID_ARGUMENT;
    }

    file = fopen(path, "wb");
    if (!file)
    {
        return CASTLE_DATA_WRITE_IO_ERROR;
    }

    for (index = 0; index < document->castle_count; index++)
    {
        if (!write_castle(file, &document->castles[index]))
        {
            fclose(file);
            return CASTLE_DATA_WRITE_IO_ERROR;
        }
    }

    if (!write_integer(file, CASTLE_DATA_FILE_TERMINATOR))
    {
        fclose(file);
        return CASTLE_DATA_WRITE_IO_ERROR;
    }

    if (fclose(file) != 0)
    {
        return CASTLE_DATA_WRITE_IO_ERROR;
    }

    return CASTLE_DATA_WRITE_OK;
}
