#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include "castle_data.h"

/**
 * Writes a small fixture file used to exercise parser success and failure.
 *
 * @param path Fixture path.
 * @param contents Text to write.
 */
static void write_fixture(const char *path, const char *contents)
{
    FILE *fixture;

    fixture = fopen(path, "wb");
    assert(fixture);
    assert(fputs(contents, fixture) >= 0);
    assert(fclose(fixture) == 0);
}

/**
 * Verifies the production castle parser against the original game's sample and
 * representative valid and invalid inputs.
 *
 * @param argc Number of command-line arguments.
 * @param argv Command-line arguments; argv[1] is the original sample path.
 * @return Zero when all checks pass.
 */
int main(int argc, char **argv)
{
    CastleDataDocument document = { 0, 0 };
    CastleDataReadResult result;
    size_t error_token;
    const char fixture_path[] = "castle_data_test_fixture.dat";
    const char fixture_contents[] =
        "*one castle with a mode toggle and fill style*\n"
        "+100\n"
        "-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1\n"
        "1,2,3,4,5,6,7,8,9,10\n"
        "10,20,9,18,6,12\n"
        "400,100,10,200\n"
        "0,0,0,0\n"
        "-4,-2,2,9,0,0,10,0,10,10,-9,-1,-999\n";

    assert(argc == 2);

    result = castle_data_read(argv[1], &document, &error_token);
    assert(result == CASTLE_DATA_READ_OK);
    assert(document.castle_count == 6);
    assert(document.castles[0].width == 130);
    assert(document.castles[0].cannon_count == 2);
    assert(document.castles[1].cannon_count == 5);
    assert(document.castles[2].cannon_count == 5);
    assert(document.castles[3].cannon_count == 6);
    assert(document.castles[4].cannon_count == 3);
    assert(document.castles[5].cannon_count == 4);
    assert(document.castles[0].resources.money == 400);

    write_fixture(fixture_path, fixture_contents);
    result = castle_data_read(fixture_path, &document, &error_token);
    assert(result == CASTLE_DATA_READ_OK);
    assert(document.castle_count == 1);
    assert(document.castles[0].width == 100);
    assert(document.castles[0].drawing_count == 3);
    assert(document.castles[0].drawing[0].type == CASTLE_DATA_DRAW_TOGGLE_MODE);
    assert(document.castles[0].drawing[1].type == CASTLE_DATA_DRAW_FILL_STYLE);
    assert(document.castles[0].drawing[1].value.fill_style.style == 2);
    assert(document.castles[0].drawing[1].value.fill_style.index == 9);
    assert(document.castles[0].drawing[2].type == CASTLE_DATA_DRAW_POLYGON);
    assert(document.castles[0].drawing[2].value.polygon.point_count == 3);

    write_fixture(fixture_path, "100\n-999\n");
    result = castle_data_read(fixture_path, &document, &error_token);
    assert(result == CASTLE_DATA_READ_FORMAT_ERROR);
    assert(error_token > 0);
    assert(document.castle_count == 1);
    assert(document.castles[0].width == 100);
    if (remove(fixture_path) != 0)
    {
        perror("Could not remove castle-data test fixture");
        return EXIT_FAILURE;
    }

    castle_data_document_destroy(&document);
    assert(document.castles == 0);
    assert(document.castle_count == 0);
    puts("castle_data_test: passed");
    return 0;
}
