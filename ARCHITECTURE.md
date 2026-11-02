# Architektur des Ballerburgen-Editors

## Überblick

Der Ballerburgen-Editor ist eine Java-Swing-Desktopanwendung, die Burgsammlungen im textbasierten Originalformat `BALLER.DAT` bearbeitet. Die Anwendung verwendet HGBase für Hauptfenster, Menüs, Dateidialoge, Statusleiste, Konfiguration und Lokalisierung. Der Editor selbst ist in die Pakete `io`, `model` und `ui` aufgeteilt; die Oberfläche und die Bearbeitungslogik sind dabei enger gekoppelt als Modell und Dateiformat.

Der Anwendungseinstieg ist `at.josefjordan.ballerburg.editor.ui.MainFrame`. Die JAR-Manifestdatei `src/META-INF/MANIFEST.MF` verweist auf diese Klasse. Der Quellcode verwendet Records, sealed Interfaces, Record-Patterns und `Math.clamp()` und setzt damit Java 21 oder neuer voraus. Das IntelliJ-Modul `ballerburgeneditor.iml` bindet `ballerburgeneditor/src` als Quellwurzel ein und hängt vom separaten `HGBase`-Modul ab. Im Editor-Unterverzeichnis liegt kein eigener Maven-, Gradle- oder Ant-Build und kein separater Testquellbaum.

## Paket- und Dateiverantwortlichkeiten

| Bereich | Zentrale Klassen/Dateien | Verantwortung |
|---|---|---|
| Einstieg und Hauptfenster | `ui/MainFrame.java` | Initialisiert Fenster, Editor, Menü und Statusleiste; koordiniert Dateilebenszyklus, Undo/Redo, Navigation, Zoomanzeige und UI-Callbacks |
| Menüs und Status | `ui/MainMenu.java`, `ui/StatusBar.java`, `src/xml/menu.xml` | Verknüpft Menüs/Toolbar mit Editoraktionen, steuert Aktivierungszustände und zeigt Status bzw. Zoom |
| Dokumentmodell | `model/CastleDocument.java`, `model/CastleFileFormat.java` | Unveränderliche Dokument-, Burg-, Geometrie-, Ressourcen- und Zeichenbefehlsdaten sowie Konstanten des `BALLER.DAT`-Formats |
| Datei-I/O | `io/CastleReader.java`, `io/CastleWriter.java` | Liest und schreibt `BALLER.DAT` unabhängig von Swing |
| Dokument- und Auswahlsteuerung | `ui/editor/EditorPanel.java`, `EditorDocumentController.java`, `EditorSelectionController.java`, `CastleCollectionEditor.java` | Hält aktuelles Dokument und Auswahl, wendet Änderungen an und benachrichtigt abhängige Ansichten |
| Bearbeitungsaktionen | `ui/editor/EditorCastleActions.java` | Erzeugt, verschiebt und ändert Burgelemente und Zeichenpolygone |
| Zeichenfläche und Interaktion | `ui/editor/castle/` | Rendert Burgdaten und implementiert Auswahl, Hit-Tests, Polygonbearbeitung, Verschieben, Größenänderung, Zoom und Scrollen |
| Validierung | `ui/editor/castle/CastleValidator.java`, `ui/editor/problems/ProblemsPanel.java` | Prüft Burgdaten und zeigt lokalisierte Fehler/Warnungen |
| Eigenschaften | `ui/editor/properties/PropertyPalettePanel.java` | Bearbeitet Startwerte für Geld, Pulver, Kugeln und Bevölkerung |
| Verlauf | `ui/DocumentHistory.java` | Speichert unveränderliche Dokument-Snapshots für Undo/Redo und fasst zusammengehörige Gesten zusammen |
| Konfiguration und Texte | `src/xml/settings.xml`, `src/xml/messages.xml`, `src/xml/menu.xml` | HGBase-Einstellungen, deutsche/englische Beschriftungen, Menüs und Toolbars |
| Grafiken und Credits | `src/gfx/`, `src/html/de/`, `src/html/en/` | Anwendungs-, Status- und Menügrafiken sowie lokalisierte Credit-Seiten |

## Start- und Dokumentlebenszyklus

1. `MainFrame.main()` erzeugt das Hauptfenster mit `/xml/settings.xml`, setzt `MainMenu` und `StatusBar`, initialisiert `EditorPanel` und übergibt anschließend Kommandozeilenargumente an HGBase.
2. `MainFrame.initializeEditor()` verbindet Dokument-, Auswahl-, Verlaufs-, Zoom- und Statusmeldungen zwischen Fenster und Editor. Die Rastereinstellung wird aus der HGBase-Konfiguration übernommen.
3. Bei „Neu“ wird ein leeres `CastleDocument` erstellt und eine leere Burg hinzugefügt. Bei „Öffnen“ liest `CastleReader` die Datei, setzt das Dokument und setzt den Undo/Redo-Verlauf auf einen neuen Ausgangszustand zurück.
4. `EditorDocumentController` hält die aktuelle Dokumentreferenz und den Index der ausgewählten Burg. Änderungen an einer Burg ersetzen diese in einer neuen `CastleDocument`-Instanz; die jeweiligen Callbacks aktualisieren Zeichenfläche, Eigenschaften, Problemliste, Auswahlstatus und Verlauf.
5. „Speichern“ validiert das Dokument für die Bestätigungsabfrage, schreibt es über `CastleWriter` und lässt den Nutzer bei offenen Problemen ausdrücklich bestätigen, dass trotzdem gespeichert werden soll.
6. Schließen entfernt das Dokument und setzt den Verlauf zurück. `DocumentHistory` bewahrt vorherige und rückgängig gemachte Snapshots einschließlich ausgewähltem Burgindex auf.

Die Datei-I/O-Schicht wirft `IOException` bei ungültigen oder nicht lesbaren Daten; `MainFrame` zeigt Lese-/Schreibfehler mit HGBase-Fehlerdialogen an. Ungültige Eingabedaten beim Öffnen werden ebenfalls als Fehler angezeigt. Der Writer bricht mit `IOException` ab, wenn Polygonkoordinaten nach der Normalisierung negativ wären.

## Datenmodell und Bearbeitungsfluss

`CastleDocument` und seine verschachtelten Records (`Castle`, `Point`, `Size`, `Resources`, `FillStyle`, `Polygon`) bilden eine immutable Momentaufnahme. Listen werden bei der Konstruktion mit `List.copyOf()` kopiert. Ein `Castle` enthält die nominale Breite, bis zu zehn Kanonenpositionen, optionale Positionen für Thron, Windfahne und drei Vorratskammern, Kammergrößen, Startressourcen, vier reservierte Formatwerte und eine geordnete Liste von Zeichenbefehlen.

Eine nicht gesetzte Modellposition wird als `Point.unset()` mit `Integer.MIN_VALUE` in beiden Koordinaten dargestellt. Das Dateiformat hat dagegen eigene Sentinel-Werte, insbesondere `-1,-1` für ungenutzte Positionen. Die Modellgrenzen erzwingen höchstens zehn Kanonenpositionen, genau vier reservierte Werte und mindestens einen Punkt pro Polygon.

`EditorPanel` ist der UI-Kompositionspunkt. Es enthält die scrollbare `CastleViewPanel`, eine Eigenschaftspalette und die Problemliste. Es delegiert Modell-/Auswahländerungen an `EditorDocumentController` und `EditorSelectionController`; `EditorCastleActions` kapselt konkrete Bearbeitungsoperationen.

Die Zeichenfläche rendert den aktuellen Burg-Snapshot mit `CastleRenderer` und besitzt den flüchtigen Ansichtszustand: Auswahl, ausgewählten Polygonpunkt, Zoom, Scrollposition, Raster und laufende Polygonerstellung. `CastleViewTransformController` rechnet zwischen Burg- und Bildschirmkoordinaten um. `CastleMouseHandler` verteilt Maus- und Tastaturgesten an spezialisierte Helfer:

- `CastleHitTester` erkennt getroffene Objekte, Flächen und Polygonkanten.
- `CastlePolygonCreationHandler` und `CastlePolygonCreation` verwalten das Erstellen von Mauer- und Dachpolygonen.
- `CastlePolygonDragHandler` verschiebt Polygone und Punkte; `CastlePolygonEditor` transformiert deren immutable Zeichenbefehle.
- `CastleObjectDragHandler` bearbeitet Kanonen, Thron und Windfahne.
- `CastleResourceAreaDragHandler`, `ResourceAreaEditor` und `ResourceAreaResizer` wählen, verschieben und skalieren Vorratsbereiche.
- `CastleDragAutoScroller` scrollt während einer Ziehbewegung am Rand des Viewports.

Änderungen werden als neue `Castle`- und `CastleDocument`-Snapshots an den Dokumentcontroller zurückgegeben. `DocumentHistory` vergleicht Dokumentwerte, speichert den Zustand vor einer Änderung und leert den Redo-Verlauf bei neuen Bearbeitungen. Durch `beginGroup()`/`endGroup()` kann eine fortlaufende Geste als einzelner Verlaufsschritt gruppiert werden. Beim Wiederherstellen eines Snapshots werden Dokument und Burgindex gemeinsam restauriert.

## Darstellung und Validierung

`CastleRenderer` zeichnet Raster, Burgpolygone, Objekte und Auswahl-/Vorschau-Markierungen in fester Reihenfolge auf Swing-`Graphics2D`. Die Mauer- und Dachfüllungen werden aus den Format-Füllbefehlen interpretiert; `Sprites` stellt die grafischen Spielobjekte bereit. `CastleBoundsCalculator` bestimmt die Zeichenflächengrenzen. Ein Transformationscontroller verwaltet Zoom, Scrollen und Koordinatenabbildung.

`CastleValidator` prüft die für das Spiel erwarteten Bestandteile und Beziehungen: notwendige Burgmauer, Thron, Windfahne und Vorratsbereiche; Breite; Lage innerhalb der Mauer; Überlappungen von Kanonen, Objekten und Polygonen sowie leere Startressourcen. Fehlende Kanonen und Nullwerte werden als Warnungen dargestellt; andere Bedingungen können Fehler liefern. `ProblemsPanel` stellt die lokalisierten Befunde dar. Bei vorhandenen Problemen blockiert das Speichern nicht automatisch, sondern `MainFrame.onFileSave()` verlangt eine ausdrückliche Bestätigung.

## `BALLER.DAT`-Format und I/O

`CastleReader` dekodiert die Datei mit IBM437/CP437, entfernt Kommentare zwischen `*`-Zeichen und liest daraus vorzeichenbehaftete Dezimalzahlen. Kommas und Leerraum dienen als Trennzeichen. Der Parser erwartet die feste Feldreihenfolge des Originalformats und übersetzt Sentinel-Werte in Modellrepräsentationen. `-999` beendet die Datei, `-1` beendet die Zeichenbefehle einer Burg, `-2` führt einen Füllstil mit zwei Argumenten ein und `-9` beendet ein Polygon.

Der Burgdatensatz enthält die Breite, zehn feste Kanonenpaare, Positionen der übrigen Spielelemente, drei Vorratskammergrößen, Startressourcen, vier reservierte Werte und Zeichenbefehle. Die Zeichenbefehle sind geordnet: ein `FillStyle` ändert die Füllung für nachfolgende Polygone. `CastleFileFormat` hält die Marker und Spielkoordinatenmaße zentral fest.

`CastleWriter` schreibt eine kanonische Datei in CP437 mit CRLF-Zeilenenden und Kommentaren zu Burgen. Vor dem Schreiben verschiebt er alle gesetzten Koordinaten so, dass ihr minimaler Ursprung bei `(0,0)` liegt. Es werden höchstens zehn Kanonenplätze serialisiert; freie Plätze werden mit `-1,-1` aufgefüllt. Die nominale Burgbreite wird aus der größten Polygon-X-Koordinate bestimmt. Kommentare des Quelldokuments und dessen ursprüngliche Formatierung werden nicht als Dokumentdaten bewahrt.

Das Modell deckt den für den Editor relevanten `BALLER.DAT`-Inhalt ab, nicht die GEM-Ressourcen oder die separate Ergebnistabelle `BALLER.TAB` des Spiels.

## Bedien- und Konfigurationsoberfläche

`src/xml/menu.xml` deklariert Datei-, Bearbeiten-, Ansicht-, Einstellungen- und Hilfemenüs sowie Toolbar-Inhalte. `MainMenu` registriert die konkreten Swing-Actions und aktualisiert Menü-/Toolbarzustände abhängig davon, ob eine Datei, Burg oder bearbeitbare Auswahl existiert. Die einzeln in `ui/menu/` liegenden Actions delegieren an `MainFrame`.

`src/xml/messages.xml` enthält deutsche und englische Nachrichtenschlüssel, einschließlich Validierungsbefunden und Interaktionshinweisen. `settings.xml` bindet die Standard-HGBase-Einstellungen ein und konfiguriert Name/Version, Menü- und Nachrichtendatei, `.dat`-Dateityp, Fenstergröße sowie zuletzt verwendete Dateien. Damit werden Text und Menüstruktur überwiegend deklarativ gehalten; die Editoraktionen und dynamischen Aktivierungsregeln bleiben Java-Code.

## Wartungshinweise und Grenzen

- `CastleDocument` ist unveränderlich, die Auswahl, Werkzeugmodi, Zoom- und Scrollwerte sind dagegen zustandsbehaftete UI-Daten. Modelländerungen sollten als neue Snapshots über Controller/Editorhelfer fließen, nicht direkt im Renderer oder in Maus-Listenern.
- UI-Aktionen durchlaufen eine Callback-Kette von `MainMenu`/`MainFrame` zu `EditorPanel`, Aktionen/Controllern und zurück zu Dokumentverlauf und Anzeigen. Bei neuen Aktionen müssen auch Menü-/Toolbar-Aktivierung und Auswahlbenachrichtigungen berücksichtigt werden.
- Das Originalformat ist ein positionsabhängiger Zahlenstrom ohne Versionsfeld. Parser und Writer müssen dieselben Feldanzahlen, Marker, CP437-Kodierung und Koordinatenkonventionen einhalten.
- Der Writer erzeugt eine kanonische Ausgabe und normalisiert Koordinaten. Ein Öffnen/Speichern erhält deshalb nicht zwangsläufig ursprüngliche Formatierung oder Kommentaranordnung.
- `MainFrame` und `CastleValidator` verwenden HGBase-Texte/Dialoge; der Editor kann nicht ohne Weiteres als eigenständiges Swing-Modul kompiliert oder gestartet werden, solange HGBase nicht auf dem Klassenpfad liegt.
- Die Oberfläche ist Swing-basiert und muss auf dem Swing-UI-Thread aktualisiert werden. Die Klassenstruktur trennt Rendering und Gestenlogik, ist aber keine vollständige MVC- oder Plugin-Architektur.
- Als offener Bedienungspunkt nennt `TODO.md` das präzisere Scrollen beim Ziehen nahe den Zeichenflächenrändern.
