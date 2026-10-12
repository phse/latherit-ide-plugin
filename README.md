# LatherIt als IDE-Plugin

LatherIt in JetBrains-IDEs (PhpStorm, IntelliJ IDEA, WebStorm, …): Projektdateien `*.latherit.yaml`
öffnen sich mit einem Tab **LatherIt** vor dem normalen Text-Editor. Plugin-ID `app.heckit.ide.latherit`.
· [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/34776-latherit)

![LatherIt in PhpStorm](marketplace/screenshots/01-request.png)

**Stand: Prototyp.** Geprüft: Projekt öffnen, automatisch speichern, Neuladen nach Änderungen von außen,
HTTP-Requests, Ressourcen laden, `${#System#…}`/`${#Env#…}`, Web-Worker, Hell/Dunkel wie die IDE.
Mock-Services, Anmeldung per Basic, Bearer, Digest und NTLM (mit Unit-Tests). Noch nicht geprüft: Dialoge,
Zwischenablage, Tastenkürzel.

## Aufbau

Die Oberfläche ist dieselbe wie in der LatherIt-Desktop-App. Ihr Quellcode liegt nicht in diesem Repository,
sondern im (nicht öffentlichen) LatherIt-Repository: Der Gradle-Build ruft dort `npm run build` auf und packt
das Ergebnis nach `/latherit-web/` ins Plugin, angezeigt wird es in einem eingebetteten Chromium (JCEF).
Statt des Rust-Backends der Desktop-App beantwortet das Plugin die Befehle der Oberfläche - gleiche Namen,
Parameter und Ergebnisse. Pfade wie `src/lib/backend.ts` in Kommentaren beziehen sich auf das LatherIt-Repository.

| Datei | Inhalt |
|---|---|
| `LatherItEditorProvider.kt` | Editor-Tab für `*.latherit.yaml` |
| `LatherItView.kt` | JCEF-Browser, Brücke (`window.__latheritIde`), Hell/Dunkel, Tastenkürzel, Neuladen bei Änderungen von außen |
| `WebResources.kt` | liefert die Oberfläche unter `https://latherit.ide/` aus (echter Origin für Web-Worker) |
| `HostCommands.kt` | Befehle: Dateien (über die Dokumente der IDE), System, Dialoge, Zwischenablage |
| `HttpBackend.kt` | HTTP mit Proxy aus den IDE-Einstellungen (Gegenstück zu `http.rs`) |
| `ChallengeAuth.kt` | Anmeldung: Basic, Bearer, Digest, NTLMv2 (Gegenstück zu `auth.rs`) |
| `MockServers.kt` | Mock-Server mit dem HTTP-Server des JDK (Gegenstück zu `mock.rs`) |
| `SystemTrust.kt` | Zertifikate wie im Browser: Stammzertifikate von macOS/Windows, danach die der IDE |

Unterschiede zur Desktop-App:

- **Speichern** geschieht automatisch kurz nach jeder Änderung (wie bei anderen Dateien in der IDE),
  über das Dokument der IDE - die Änderung ist damit auch im Text-Tab, in Git und mit Local History sichtbar.
- Wird die Datei **außerhalb** geändert (Text-Tab, Git, anderes Programm), lädt die Oberfläche neu.
- **Mock-Services** gehören zum Editor-Tab, der sie gestartet hat, und enden, wenn er geschlossen wird.
- **Tastenkürzel**, die die IDE selbst belegt, leitet das Plugin weiter: ⌘↵, ⌘S, ⇧⌘S, ⌘I, ⇧⌘N, ⌘B, ⇧⌘F, ⌘1, ⌘2.

## Bauen und starten

Voraussetzungen: ein Checkout von LatherIt (Standard: `../../latherit`, siehe `latheritDir`), Node/npm für
dessen Build und JDK 17 oder neuer für Gradle - die Toolchain (JDK 25) lädt Gradle bei Bedarf selbst.

```
./gradlew runIde        # Sandbox-IDE mit dem Plugin
./gradlew buildPlugin   # build/distributions/latherit-ide-plugin-<version>.zip
./gradlew verifyPlugin
./gradlew test          # Unit-Tests (Anmeldung, Mock-Server)
```

Optionen (`-P…`):

- `openProject=/pfad/zum/projekt` öffnet direkt ein Projekt in der Sandbox
- `cefDebugPort=9222` macht die Chromium-DevTools der Sandbox unter `http://localhost:9222` erreichbar
- `npmCommand=/pfad/zu/npm`, falls Gradle `npm` nicht findet

Standardmäßig lädt Gradle PhpStorm `platformVersion` (aus `gradle.properties`). Schneller mit einer
installierten IDE: `local.properties` anlegen (wird nicht eingecheckt), z. B.

```
platformLocalPath=/Applications/PhpStorm.app
latheritDir=/pfad/zu/latherit
```

## Screenshots neu erzeugen

```
./gradlew runIde -Pscreenshots=marketplace/screenshots
```

Kopiert `marketplace/demo-project` nach `/Users/Shared/latherit-demo` (damit kein Benutzerpfad im Bild
erscheint), öffnet es in der Sandbox, startet den Mock und schreibt vier PNGs nach `marketplace/screenshots/`
(Request nebeneinander, übereinander, Mock mit Protokoll, Testlauf). Die IDE zeichnet ihr Fenster selbst, die
Oberfläche kommt aus den Chromium-DevTools - Bildschirmaufnahme-Rechte sind nicht nötig. Der Helfer liegt in
`src/screenshot/` und wird nur mit `-Pscreenshots` gebaut, nie in ein Release. Danach die Sandbox schließen.

Fragt die Sandbox beim ersten Lauf, ob sie dem Ordner vertrauen soll: einmal „Trust Project“ bestätigen und
den Befehl erneut ausführen.

## Lizenz

Der Quellcode dieses Repositorys steht unter der [Apache License 2.0](LICENSE). Die beim Build eingebundene
LatherIt-Oberfläche sowie Name und Logo sind davon ausgenommen, siehe [NOTICE](NOTICE).
