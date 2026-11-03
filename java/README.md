# Ballerburgen Editor — Java Edition

The Java Edition of the Ballerburgen Editor is a desktop application for creating and editing castle collections for [Ballerburg](https://www.eckhardkruse.net/atari_st/baller.html?en), the classic artillery game originally developed for the Atari ST.

## Features

- Create and manage multiple castles in one document.
- Edit castle walls and roofs, cannons, the throne, the wind vane, and storage areas.
- Set starting resources such as money, powder, cannonballs, and population.
- Use grid display, zoom, and castle navigation while editing.
- Review validation warnings and errors.
- Undo and redo changes.
- Open and save castle collections in the game's `BALLER.DAT` format.
- Use the application in English or German.

## Requirements

- Java 21 or later.
- Subversion (SVN), to download the [HGBase](https://svn.code.sf.net/p/hgbase/code_svn/trunk/HGBase/) source. HGBase provides the application's shared GUI and configuration components and is not included in this repository.

## Build and Run

There is currently no Maven or Gradle build file. Compile the editor together with HGBase from source using the JDK tools:

1. Install a JDK 21 or later, Git, and an SVN client.
2. Clone this repository and change into `ballerburgeneditor/java`.
3. Run the commands for your platform. They check out HGBase, compile both source trees, and launch the editor:

   **Windows (PowerShell):**

   ```powershell
   New-Item -ItemType Directory -Force target | Out-Null
   if (Test-Path target\HGBase\.svn) {
     svn update target\HGBase
   } else {
     svn checkout https://svn.code.sf.net/p/hgbase/code_svn/trunk/HGBase/ target\HGBase
   }
   New-Item -ItemType Directory -Force target\classes | Out-Null
   $javaSources = Get-ChildItem -Path src,target\HGBase\src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
   javac --release 21 -encoding UTF-8 -d target\classes $javaSources
   java -cp "target\classes;src;target\HGBase\src" at.josefjordan.ballerburg.editor.ui.MainFrame
   ```

   **macOS or Linux:**

   ```sh
   mkdir -p target
   if [ -d target/HGBase/.svn ]; then
     svn update target/HGBase
   else
     svn checkout https://svn.code.sf.net/p/hgbase/code_svn/trunk/HGBase/ target/HGBase
   fi
   mkdir -p target/classes
   find src target/HGBase/src -name '*.java' -print0 |
     xargs -0 javac --release 21 -encoding UTF-8 -d target/classes
   java -cp "target/classes:src:target/HGBase/src" at.josefjordan.ballerburg.editor.ui.MainFrame
   ```

The compiled classes are written to `target/classes`. The commands above compile and launch the application; they do not package a standalone JAR. Both source directories are included at runtime so Java can load the XML and graphics resources.

## Usage

Use **File > Open** to open an existing `BALLER.DAT` file, or **File > New** to start a castle collection. Edit castles on the canvas and use the validation panel to review potential issues. Choose **File > Save** to write the collection back to disk.

The editor may ask for confirmation before saving a document with validation issues. Review those issues before using the file in the game.

## Project Structure

- `src/at/josefjordan/ballerburg/editor/` — Java application source.
- `src/xml/` — menus, settings, and localized messages.
- `src/gfx/` — application and menu graphics.
- `src/html/` — localized credits pages.

For implementation details, see [ARCHITECTURE.md](ARCHITECTURE.md).

## License

This project is licensed under the [MIT License](../LICENSE).

## Credits

**Ballerburg** was originally created by [Eckhard Kruse](https://www.eckhardkruse.net/). The Ballerburgen Editor is an independent project and is not affiliated with the original author.