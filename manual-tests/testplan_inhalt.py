"""
Inhalt des Handtest-Plans. Aufbau: KAPITEL → unter (Unterthemen) → faelle (Test-Cases).

Auszeichnung in Texten: `code`, **fett**, [[datei:pfad unter dateien/]], [[url:adresse]] oder [[url:adresse|Text]].
Ein Schritt ist ein Text oder ein Paar (Text, Code) - der Code erscheint kopierbar darunter.
"""

TITEL = "LatherIt im IDE-Plugin - Handtests"
UNTERTITEL = "Testplan zum Abarbeiten in Etappen · Plugin ab 0.2.2 · Stand 24. Oktober 2026"

S = "http://localhost:8088"

VORWORT = [
    ("p", "Dieser Plan ergänzt die automatischen Tests (`./gradlew test`) um alles, was nur mit Oberfläche, "
          "echter IDE und echtem Betriebssystem prüfbar ist. Er ist in Themen aufgeteilt; jedes Thema lässt sich "
          "einzeln in einer Sitzung abarbeiten. Ergebnisse und Auffälligkeiten gehören mit der Test-ID "
          "(z. B. **W-12**) in den Ticket-Kommentar, nicht in dieses Dokument."),
    ("liste", [
        "**Kapitel 2-12** laufen **einmal** auf der Hauptplattform: macOS mit PhpStorm.",
        "**Kapitel 13** enthält die **plattformabhängigen** Fälle: Rauchtest plus die Besonderheiten von "
        "macOS, Windows, Linux und den anderen IDEs. Auf Windows, Linux und in IntelliJ IDEA wird nur "
        "Kapitel 13 durchgeklickt, nicht der ganze Plan.",
        "Alle Testdateien liegen in [[datei:.|dateien/]] neben diesem PDF und sind direkt verlinkt. "
        "Code-Blöcke lassen sich direkt aus dem PDF kopieren (markieren, ⌘C); die Einrückung geht dabei "
        "verloren, ⇧⌥F stellt sie in LatherIt wieder her. Dieselben Texte gibt es auch als Datei unter "
        "[[datei:schnipsel|dateien/schnipsel/]].",
        "**Ausgangslage:** Jedes Kapitel nennt am Anfang, mit welcher Projektdatei es arbeitet. Baut ein Fall "
        "auf einem anderen auf, steht das unter *Vorbereitung* (z. B. „Request aus R-10“). Ohne Angabe beginnt "
        "der Fall mit einem neuen Request.",
        "**Neuer REST-Request** heißt immer: ⇧⌘N im LatherIt-Tab, im Dialog Methode und Adresse eintragen, "
        "Anlegen. „GET `…`“ bzw. „POST an `…`“ meint genau das, danach mit ⌘↵ senden. Die Methode lässt sich "
        "später nur am Methoden-Knoten im Baum ändern - für eine andere Methode also einen neuen Request anlegen.",
        "Wo was steht: Endpunkt bzw. Adresse oben in der Zeile neben **Ausführen**; darunter die Tabs **Body** "
        "(bei REST mit Feld *Content-Type*), **Header**, **Anmeldung** und **Einstellungen** (Timeout). "
        "Projekt-Properties und Projekt-Anmeldung: im Baum den obersten Knoten (Projektname) wählen.",
        "Fälle mit der Marke **neu 24.10.** oder **geändert 24.10.** sind am 24. Oktober hinzugekommen bzw. "
        "angepasst worden (Scrollen, Editor-Bedienung, Suchen, Öffnen des Tabs) - wer den Plan schon "
        "durchgegangen ist, prüft nur diese.",
        "Fälle mit der Marke **neu in 0.3** prüfen die Schutzfunktion gegen das Überschreiben fremder "
        "Änderungen, die nach 0.2.2 hinzugekommen ist.",
    ]),
]

SITZUNGEN = [
    ["1", "Vorbereitung: Testserver, Plugin, Testordner (V-01 … V-04)", "15 min", "-"],
    ["2", "Projektdateien und Speichern (P-…)", "40 min", "Sitzung 1"],
    ["3", "SOAP und WSDL (W-…)", "30 min", "Testserver"],
    ["4", "REST (R-…)", "30 min", "Testserver"],
    ["5", "HTTP: Status, Zeiten, Fehler, Inhalte (H-01 … H-39)", "40 min", "Testserver"],
    ["6", "HTTPS und Proxy (H-40 … H-53)", "30 min", "Testserver, Internet"],
    ["7", "Anmeldung: Basic, Bearer, Digest, NTLM (A-…)", "30 min", "Testserver"],
    ["8", "GraphQL (G-…)", "20 min", "Testserver, REST"],
    ["9", "Platzhalter und Skripte (S-…)", "25 min", "REST"],
    ["10", "Tests, Assertions, Last-Test (T-…)", "25 min", "Demo-Projekt"],
    ["11", "Mock-Services im Plugin (M-…)", "35 min", "Demo-Projekt, Terminal"],
    ["12", "SoapUI-Import (I-…)", "15 min", "Testserver"],
    ["13", "Bedienung: Dialoge, Zwischenablage, Kürzel, Darstellung (B-…)", "45 min", "-"],
    ["14", "Plattformen und IDEs (Kapitel 13), je Plattform", "45-60 min", "Rauchtest zuerst"],
]

KAPITEL = [
    # ------------------------------------------------------------------ 1
    {
        "titel": "Vorbereitung",
        "kurz": "Testserver, Plugin, Testordner",
        "faelle": [
            {
                "id": "V-01", "titel": "Testserver starten",
                "ziel": "Ein lokaler Server für SOAP, REST, HTTP-Sonderfälle, Anmeldung, GraphQL, HTTPS und Proxy.",
                "schritte": [
                    "Python 3.9 oder neuer muss installiert sein (`python3 --version`).",
                    ("Terminal im Ordner `manual-tests/dateien` öffnen und starten "
                     "([[datei:testserver.py]]):", "python3 testserver.py"),
                    f"Im Browser [[url:{S}/]] öffnen: Übersicht aller Adressen.",
                    "Terminal offen lassen - jeder Request erscheint dort mit Zeit, Methode, Pfad und "
                    "(gekürzt) dem `Authorization`-Header.",
                ],
                "erwartet": [
                    "Terminal meldet HTTP auf 8088 und 8090, HTTPS auf 8443 und den Proxy auf 8089.",
                    "Die Übersichtsseite lädt.",
                ],
                "hinweis": "Für Test-VMs: `python3 testserver.py --host 0.0.0.0` auf dem Mac starten und in der VM "
                           "die Adresse des Macs nehmen (QEMU-User-Netz: `10.0.2.2`) statt `localhost`.",
            },
            {
                "id": "V-02", "titel": "Plugin bauen und installieren",
                "schritte": [
                    ("Im Plugin-Ordner bauen:", "./gradlew --no-build-cache clean test buildPlugin"),
                    "In PhpStorm: *Settings | Plugins | ⚙ | Install Plugin from Disk…* und "
                    "`build/distributions/latherit-ide-plugin-<version>.zip` wählen, IDE neu starten.",
                    "*Settings | Plugins | Installed*: LatherIt mit der erwarteten Version prüfen.",
                ],
                "erwartet": ["Alle automatischen Tests grün, Plugin aktiv, keine Fehlermeldung (rotes Ausrufezeichen unten rechts)."],
            },
            {
                "id": "V-03", "titel": "Umgebungsvariable für Platzhalter-Tests",
                "schritte": [
                    ("In `~/.zshrc` eintragen (Windows: *Systemeigenschaften › Umgebungsvariablen*, Linux: `~/.profile`):",
                     "export LATHERIT_TEST='Hallo aus der Umgebung'\nexport LATHERIT_TOKEN='test-token-123'"),
                    "IDE komplett beenden und neu starten (die IDE liest die Variablen beim Start).",
                ],
                "erwartet": ["Wird in S-02 und A-11 geprüft."],
            },
            {
                "id": "V-04", "titel": "Testordner als IDE-Projekt öffnen",
                "schritte": [
                    "Ordner [[datei:projekte|dateien/projekte/]] in einen neuen Arbeitsordner kopieren, "
                    "z. B. `~/latherit-handtest` (so bleiben die Originale unverändert).",
                    "In diesem Ordner `git init` und einen ersten Commit anlegen (für P-21).",
                    "Den Ordner in PhpStorm als Projekt öffnen.",
                ],
                "erwartet": ["`shop-api.latherit.yaml` öffnet mit dem Tab **LatherIt** vorn und **Text** dahinter."],
            },
        ],
    },
    # ------------------------------------------------------------------ 2
    {
        "titel": "Projektdateien und Speichern",
        "kurz": "Anlegen, automatisches Speichern, Änderungen von außen",
        "text": [("p", "**Ausgangslage:** Arbeitsordner aus V-04. P-01 bis P-05 legen neue Dateien an; ab P-10 "
                       "arbeiten alle Fälle mit `shop-api.latherit.yaml` aus diesem Ordner.")],
        "unter": [
            {
                "titel": "Anlegen und Öffnen",
                "faelle": [
                    {
                        "id": "P-01", "titel": "Neues Projekt über das Menü",
                        "schritte": [
                            "Im Projektbaum Rechtsklick auf einen Ordner › *New › LatherIt Project*.",
                            "Namen `Handtest` eingeben, OK.",
                        ],
                        "erwartet": [
                            "`Handtest.latherit.yaml` entsteht im gewählten Ordner und öffnet sich im LatherIt-Tab.",
                            "Text-Tab zeigt `latherit: 1`, eine `id` und `name: \"Handtest\"`.",
                        ],
                    },
                    {
                        "id": "P-02", "titel": "Ungültige und doppelte Namen",
                        "schritte": [
                            "*New › LatherIt Project*, nacheinander eingeben: leer, `a/b`, `a:b`, `Handtest`.",
                        ],
                        "erwartet": [
                            "OK bleibt gesperrt mit Meldung „Bitte einen Namen eingeben.“, "
                            "„Der Name darf kein / \\ : * ? \" < > | enthalten.“ bzw. „… gibt es dort schon.“",
                        ],
                    },
                    {
                        "id": "P-03", "titel": "Ordner mit Leerzeichen und Umlauten",
                        "schritte": [
                            "Ordner `Prüfung mit Leer zeichen` anlegen, darin ein neues Projekt `Größe`.",
                            ("Neuen REST-Request anlegen und senden (Testserver muss laufen):", f"{S}/api/orders/4711"),
                            "Tab schließen und wieder öffnen.",
                        ],
                        "erwartet": ["Speichern und Laden funktioniert, der Request ist nach dem Öffnen da."],
                    },
                    {
                        "id": "P-04", "titel": "Kaputte Projektdatei",
                        "schritte": [
                            "[[datei:projekte/kaputt.latherit.yaml]] in den Arbeitsordner kopieren und öffnen.",
                        ],
                        "erwartet": [
                            "Verständliche Fehlermeldung in der LatherIt-Oberfläche, kein leeres oder hängendes Fenster.",
                            "Die Datei wird **nicht** verändert (Text-Tab, `git status`).",
                        ],
                    },
                    {
                        "id": "P-05", "titel": "Neues Projekt aus der Oberfläche heraus",
                        "schritte": [
                            "Im LatherIt-Tab ⌘N (Neues Projekt) drücken, Namen eingeben.",
                            "Im LatherIt-Tab „Öffnen“ wählen und `shop-api.latherit.yaml` auswählen.",
                        ],
                        "erwartet": [
                            "Neues Projekt öffnet sich in einem **eigenen** Editor-Tab (nicht im bestehenden).",
                            "Ist die Datei schon offen, wechselt die IDE in deren Tab.",
                        ],
                    },
                ],
            },
            {
                "titel": "Automatisches Speichern",
                "faelle": [
                    {
                        "id": "P-10", "titel": "Änderung landet sofort in der Datei",
                        "schritte": [
                            "In `shop-api.latherit.yaml` den Request *Open orders* umbenennen (F2) in `Offene`.",
                            "Etwa eine Sekunde warten, dann in den Text-Tab wechseln.",
                            "*Commit*-Fenster bzw. `git diff` ansehen.",
                        ],
                        "erwartet": [
                            "Text-Tab zeigt `name: Offene`; der Diff enthält nur diese Zeile.",
                            "Der Tab-Titel zeigt keinen „ungespeichert“-Stern.",
                        ],
                    },
                    {
                        "id": "P-11", "titel": "Speichern ohne Änderung ändert nichts",
                        "schritte": ["Nach einem Commit im LatherIt-Tab nur Requests anklicken, auf- und zuklappen, ⌘S drücken."],
                        "erwartet": ["`git status` bleibt sauber."],
                    },
                    {
                        "id": "P-12", "titel": "Local History und Rückgängig",
                        "schritte": [
                            "Drei Änderungen im LatherIt-Tab machen, je ein paar Sekunden Abstand: einen Request "
                            "umbenennen, einen Header anlegen, den Body ändern.",
                            "Rechtsklick auf die Datei › *Local History › Show History*.",
                            "Einen älteren Stand per *Revert* wiederherstellen.",
                        ],
                        "erwartet": [
                            "Jede Änderung ist als Eintrag „LatherIt-Projekt speichern“ bzw. „Save LatherIt Project“ sichtbar.",
                            "Nach *Revert* lädt der LatherIt-Tab neu und zeigt den alten Stand.",
                        ],
                    },
                    {
                        "id": "P-13", "titel": "Leerzeichen am Zeilenende bleiben",
                        "schritte": [
                            ("In einen Request-Body (z. B. *Create order*) eine Zeile mit Leerzeichen am Ende schreiben (hier sichtbar als `·`):",
                             "<a>···\n</a>"),
                            "Zu einer anderen Datei wechseln (löst bei manchen Einstellungen Speichern aus).",
                        ],
                        "erwartet": [
                            "Im Text-Tab stehen die Leerzeichen noch; der LatherIt-Tab lädt dabei nicht neu "
                            "(Auswahl und aufgeklappter Baum bleiben).",
                        ],
                    },
                ],
            },
            {
                "titel": "Änderungen von außen",
                "text": [("p", "Hier steckt das größte Risiko für Datenverlust. Die Fälle P-22 und P-23 prüfen die "
                               "Schutzfunktion aus 0.3: Statt fremde Änderungen zu überschreiben, lädt die Ansicht neu "
                               "und meldet „… wurde außerhalb von LatherIt geändert“.")],
                "faelle": [
                    {
                        "id": "P-20", "titel": "Änderung im Text-Tab",
                        "schritte": [
                            "Im Text-Tab bei einem Request die Zeile `name:` ändern, z. B. *Open orders* in `Offen2`.",
                            "Zurück in den LatherIt-Tab.",
                        ],
                        "erwartet": [
                            "LatherIt zeigt den neuen Namen (lädt beim Zurückwechseln neu).",
                            "Aufgeklappte Knoten und Auswahl bleiben erhalten.",
                        ],
                    },
                    {
                        "id": "P-21", "titel": "Git-Checkout bei offenem Tab",
                        "vorher": "Arbeitsordner aus V-04 mit Commit.",
                        "schritte": [
                            "Im LatherIt-Tab etwas ändern, warten, committen.",
                            ("Im Terminal (LatherIt-Tab bleibt sichtbar):", "git checkout HEAD~1 -- shop-api.latherit.yaml"),
                            "Danach wieder auf den neuesten Stand:",
                            ("", "git checkout HEAD -- shop-api.latherit.yaml"),
                        ],
                        "erwartet": [
                            "Der Checkout überschreibt die Datei (so gewollt). Der sichtbare LatherIt-Tab übernimmt das "
                            "jeweils von selbst: nach dem ersten Checkout der alte Stand ohne die Änderung, nach dem "
                            "zweiten wieder die Änderung.",
                            "`git status` ist danach sauber - LatherIt schreibt nicht ungefragt zurück.",
                        ],
                    },
                    {
                        "id": "P-22", "titel": "Schnell tippen und sofort wechseln", "tag": "neu in 0.3",
                        "schritte": [
                            "Im LatherIt-Tab im Body eines Requests (z. B. *Shop API › … › Create order*) ein Zeichen tippen und **sofort** (unter ½ s) "
                            "mit ⌃⇥ bzw. Klick in den Text-Tab wechseln und dort eine Zeile ändern.",
                            "Ein paar Mal wiederholen, auch in umgekehrter Reihenfolge.",
                        ],
                        "erwartet": [
                            "Die Änderung im Text-Tab bleibt **immer** erhalten.",
                            "Ging die LatherIt-Änderung verloren, erscheint beim Zurückwechseln die Meldung "
                            "„… wurde außerhalb von LatherIt geändert - nicht gespeichert, die Ansicht lädt neu“.",
                        ],
                    },
                    {
                        "id": "P-23", "titel": "Zwei LatherIt-Tabs auf derselben Datei", "tag": "neu in 0.3",
                        "schritte": [
                            "Rechtsklick auf den Tab › *Split Right*. Beide Hälften zeigen den LatherIt-Tab.",
                            "Links einen Request umbenennen.",
                            "Rechts sofort danach einen anderen Request umbenennen.",
                        ],
                        "erwartet": [
                            "Rechts lädt nach der linken Änderung neu und zeigt sie.",
                            "Am Ende stehen **beide** Umbenennungen in der Datei, oder die zweite wird mit der "
                            "Meldung aus P-22 abgewiesen - nie geht die erste verloren.",
                        ],
                    },
                    {
                        "id": "P-24", "titel": "Umbenennen und Verschieben bei offenem Tab",
                        "schritte": [
                            "Datei im Projektbaum per *Refactor › Rename* umbenennen.",
                            "Danach im LatherIt-Tab etwas ändern.",
                            "Datei per Drag & Drop in einen anderen Ordner verschieben und wieder ändern.",
                        ],
                        "erwartet": [
                            "Die Änderung landet in der umbenannten bzw. verschobenen Datei.",
                            "Es entsteht **keine** neue Datei unter dem alten Namen.",
                        ],
                        "hinweis": "Die Oberfläche kennt nur den Pfad vom Öffnen des Tabs; das Plugin ordnet ihn der "
                                   "umbenannten bzw. verschobenen Datei zu. Taucht die alte Datei wieder auf, ist das ein "
                                   "Fehler fürs Ticket.",
                    },
                    {
                        "id": "P-25", "titel": "Datei löschen bei offenem Tab",
                        "schritte": [
                            "Im LatherIt-Tab etwas ändern und die Datei **gleich danach** im Projektbaum löschen "
                            "(Auswahl im Baum vorher setzen, dann ändern, ⌫ und Bestätigen).",
                            "Ein paar Sekunden warten, dann im Projektbaum und per `git status` nachsehen.",
                        ],
                        "erwartet": [
                            "Der Tab schließt sich sofort - das ist so gewollt.",
                            "Die Datei entsteht **nicht** durch das automatische Speichern neu.",
                        ],
                    },
                ],
            },
            {
                "titel": "Tab und IDE neu starten",
                "faelle": [
                    {
                        "id": "P-30", "titel": "Tab schließen und öffnen, IDE neu starten",
                        "schritte": [
                            "Eine Änderung machen und den Tab innerhalb einer halben Sekunde schließen.",
                            "Datei wieder öffnen.",
                            "IDE mit offenem LatherIt-Tab beenden und neu starten.",
                        ],
                        "erwartet": [
                            "Die Änderung ist gespeichert oder sichtbar verworfen - kein halber Stand in der Datei.",
                            "Nach dem Neustart öffnet der Tab wieder als LatherIt-Tab, ohne Fehler im Log "
                            "(*Help › Show Log in Finder*, nach `latherit` suchen).",
                        ],
                    },
                    {
                        "id": "P-31", "titel": "Öffnen ohne Zwischenstände", "tag": "neu 24.10.",
                        "schritte": [
                            "In LatherIt einen Request wählen. Unten von *LatherIt* auf den Tab *Text* wechseln, Datei schließen.",
                            "Datei wieder öffnen (Projektbaum), dabei genau hinsehen. Zwei-, dreimal wiederholen.",
                        ],
                        "erwartet": [
                            "Es öffnet der LatherIt-Tab (nicht *Text*), auch der Text-Tab blitzt nicht kurz auf.",
                            "Zu sehen ist nur kurz ein Ladekreis auf einfarbiger Fläche, dann direkt der zuletzt gewählte "
                            "Request - weder das leere Editor-„Dashboard“ der IDE noch die LatherIt-Willkommensseite "
                            "oder die Projektübersicht dazwischen.",
                        ],
                    },
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 3
    {
        "titel": "SOAP und WSDL",
        "kurz": "Import, Senden, Faults, SOAP 1.2, Assertions",
        "text": [("p", "**Ausgangslage:** Projekt `Handtest` aus P-01, Testserver läuft. Die Fälle bauen aufeinander auf: "
                       "W-01 importiert die WSDL, ab W-10 wird mit den so entstandenen Requests gearbeitet. W-02 "
                       "importiert dieselbe WSDL ein zweites Mal - danach gibt es `ShopSoap11`/`ShopSoap12` doppelt; "
                       "gemeint ist immer der **obere** Eintrag.")],
        "unter": [
            {
                "titel": "WSDL importieren",
                "faelle": [
                    {
                        "id": "W-01", "titel": "WSDL per Adresse mit eingebundenen XSDs",
                        "vorher": "Projekt `Handtest` aus P-01, Testserver läuft.",
                        "schritte": [
                            ("⌘I im LatherIt-Tab, Adresse eingeben:", f"{S}/shop?wsdl"),
                        ],
                        "erwartet": [
                            "Interfaces `ShopSoap11` und `ShopSoap12` mit den Operationen `GetOrder` und `Ping`.",
                            "Server-Terminal zeigt `GET /shop?wsdl`, `GET /xsd/shop-types.xsd`, `GET /xsd/common.xsd`.",
                            "Beispiel-Request zu `GetOrder` enthält `orderId`, die Auswahl `customerNo`/`email`, "
                            "`filter` und den Header `Session`.",
                        ],
                    },
                    {
                        "id": "W-02", "titel": "WSDL als Datei",
                        "schritte": [
                            "⌘I, „Datei wählen“: [[datei:wsdl/shop.wsdl]] (IDE-Dateidialog).",
                        ],
                        "erwartet": ["Wie W-01; die XSDs werden aus [[datei:wsdl/xsd|dateien/wsdl/xsd/]] geladen."],
                    },
                    {
                        "id": "W-03", "titel": "Beispiel-Request ohne Netz neu erzeugen",
                        "ziel": "LatherIt speichert die WSDL samt XSDs im Projekt. Ein Beispiel-Request lässt sich "
                                "daher auch erzeugen, wenn der Server mit der WSDL nicht erreichbar ist.",
                        "vorher": "Projekt mit importierter WSDL aus W-01.",
                        "schritte": [
                            "Im Baum *ShopSoap11 › GetOrder › Request 1* wählen und im Body ein paar Zeilen löschen.",
                            "Testserver im Server-Terminal mit Strg+C beenden.",
                            "LatherIt-Tab schließen und die Datei wieder öffnen - so kommt die WSDL sicher nicht "
                            "aus dem Zwischenspeicher.",
                            "Request wieder wählen. Im Body-Tab rechts oben auf das Kreispfeil-Symbol "
                            "„Beispiel-Request neu aus der WSDL erzeugen“ klicken.",
                            "Testserver wieder starten (für die nächsten Fälle).",
                        ],
                        "erwartet": [
                            "Der Body ist wieder der vollständige Beispiel-Request wie in W-01.",
                            "Keine Fehlermeldung, obwohl der Server aus ist.",
                        ],
                    },
                ],
            },
            {
                "titel": "Senden und Antworten",
                "faelle": [
                    {
                        "id": "W-10", "titel": "Ping",
                        "vorher": "WSDL aus W-01 importiert.",
                        "schritte": [
                            "Im Baum *ShopSoap11 › Ping › Request 1* wählen.",
                            "Als Endpunkt `http://localhost:8088/shop` eintragen, ⌘↵.",
                        ],
                        "erwartet": [
                            "Status 200, `PingResponse`, Zeit und Größe werden angezeigt.",
                            "Server-Terminal zeigt `POST /shop`.",
                        ],
                    },
                    {
                        "id": "W-11", "titel": "GetOrder mit Session aus einer Projekt-Property",
                        "vorher": "WSDL aus W-01 importiert.",
                        "schritte": [
                            "Im Baum den Projektknoten `Handtest` wählen und die Property `session` mit dem Wert `peter` anlegen.",
                            "*ShopSoap11 › GetOrder › Request 1* wählen, Endpunkt `http://localhost:8088/shop`.",
                            ("Body ersetzen ([[datei:schnipsel/soap-getorder.xml]]):",
                             open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/soap-getorder.xml"), encoding="utf-8").read()),
                            "⌘↵, danach den Tab **Gesendet** öffnen.",
                        ],
                        "erwartet": [
                            "Antwort mit `createdBy=\"peter\"`, `<shop:id>4711</shop:id>` und `currency=\"EUR\"`.",
                            "**Gesendet** zeigt `<shop:Session>peter</shop:Session>` und die SOAPAction `urn:GetOrder`.",
                        ],
                    },
                    {
                        "id": "W-12", "titel": "SOAP-Fault",
                        "vorher": "Request aus W-11 (*ShopSoap11 › GetOrder › Request 1*).",
                        "schritte": [
                            "Im Body `orderId` auf `0` setzen und senden.",
                            "Danach `orderId` wieder `4711`, Property `session` auf `abgelaufen`, senden.",
                        ],
                        "erwartet": [
                            "Status 500, der Fault ist rot hervorgehoben: „Bestellung „0“ nicht gefunden“ bzw. „Session abgelaufen“.",
                        ],
                    },
                    {
                        "id": "W-13", "titel": "SOAP 1.2",
                        "vorher": "W-11 und W-12 erledigt. Property `session` wieder auf `peter` setzen "
                                  "(W-12 hat sie auf `abgelaufen` gestellt, das ergäbe einen Fault).",
                        "schritte": [
                            "Im Baum *ShopSoap12 › GetOrder › Request 1* wählen.",
                            "Endpunkt `http://localhost:8088/shop12` eintragen (nicht `/shop` - das ist SOAP 1.1).",
                            ("Der Beispiel-Request enthält nur `?`. Body ersetzen "
                             "([[datei:schnipsel/soap12-getorder.xml]]):",
                             open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/soap12-getorder.xml"), encoding="utf-8").read()),
                            "⌘↵, danach den Tab **Gesendet** öffnen.",
                        ],
                        "erwartet": [
                            "Status 200, Antwort im SOAP-1.2-Envelope (`http://www.w3.org/2003/05/soap-envelope`) "
                            "mit `createdBy=\"peter\"` und `<shop:id>4711</shop:id>`.",
                            "**Gesendet** zeigt `Content-Type: application/soap+xml; …; action=\"urn:GetOrder\"`.",
                        ],
                    },
                    {
                        "id": "W-14", "titel": "XML falten und formatieren",
                        "vorher": "Request aus W-11, einmal gesendet (für eine Response).",
                        "schritte": [
                            ("Im Body vor `</soapenv:Body>` einen Kommentar und CDATA einfügen, gern schief eingerückt:",
                             "<!-- Kommentar\n     über zwei Zeilen -->\n<notiz><![CDATA[\n  <roh> & unverändert\n]]></notiz>"),
                            "Im Body ⇧⌥F (formatieren).",
                            "Tags über die Pfeile am Rand falten und aufklappen, auch in der Response.",
                        ],
                        "erwartet": ["Formatierung korrekt eingerückt; Falten funktioniert bei Tags, Kommentaren und CDATA."],
                    },
                ],
            },
            {
                "titel": "Assertions für SOAP",
                "faelle": [
                    {
                        "id": "W-20", "titel": "Schema Compliance und XPath Match", "tag": "geändert 24.10.",
                        "vorher": "Request aus W-11 mit `orderId` `4711` und Property `session` = `peter`.",
                        "schritte": [
                            "Am Request oben rechts „Als Step in einen Test-Case übernehmen“ › *Neue Test-Suite*. "
                            "Es entsteht *Test-Suite › Test-Case › GetOrder*; diesen Step wählen.",
                            "Assertions hinzufügen: *SOAP-Response*, *Kein SOAP-Fault*, *Schema Compliance* und "
                            "*XPath Match*.",
                            ("Bei *XPath Match* ins Feld **XPath** (ohne `= 4711`):",
                             "declare namespace s='http://example.com/shop/types'; //s:id"),
                            ("… und ins Feld **Erwartet**:", "4711"),
                            "Step mit ⌘↵ ausführen, dann im Body des Steps `orderId` auf `0` und erneut ausführen.",
                        ],
                        "erwartet": [
                            "Erster Lauf: alle grün.",
                            "Zweiter Lauf: *Kein SOAP-Fault* und *XPath Match* rot. *XPath Match* meldet „Kein Treffer für den XPath …“ "
                            "(nicht mehr „erhalten „““).",
                        ],
                    },
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 4
    {
        "titel": "REST",
        "kurz": "Anlegen, Parameter, Methoden und Bodies",
        "text": [("p", "**Ausgangslage:** Projekt `Handtest`, Testserver läuft. Jeder Fall legt einen neuen "
                       "REST-Request an (⇧⌘N), außer unter *Vorbereitung* steht ein anderer.")],
        "unter": [
            {
                "titel": "Anlegen",
                "faelle": [
                    {
                        "id": "R-01", "titel": "REST-Request aus einer Adresse",
                        "schritte": [("⇧⌘N im LatherIt-Tab, Methode GET, Adresse eingeben:", f"{S}/api/orders/{{id}}?status=open")],
                        "erwartet": [
                            "Service, Ressource `/api/orders/{id}` und Methode GET entstehen.",
                            "`id` ist Pfad-Parameter, `status` Query-Parameter.",
                        ],
                    },
                    {
                        "id": "R-02", "titel": "Adresse mit Satzzeichen am Ende", "tag": "neu 24.10.",
                        "schritte": [
                            ("⇧⌘N, Adresse wie aus einem Text kopiert eingeben (mit Punkt am Ende):", f"{S}/api/orders."),
                            ("Dialog erneut, diesmal in spitzen Klammern mit Komma:", f"<{S}/api/customers>,"),
                        ],
                        "erwartet": [
                            "Unter dem Feld erscheint „`.` am Ende gehört nicht zur Adresse und wird weggelassen.“ "
                            "(bzw. `>,`).",
                            "Die Ressourcen heißen `/api/orders` und `/api/customers`, ohne Satzzeichen und Klammern.",
                        ],
                    },
                ],
            },
            {
                "titel": "Parameter und Kodierung",
                "text": [("p", f"Alle Fälle gegen `{S}/api/echo/…`: Der Server antwortet mit dem, was er empfangen hat "
                               "(Methode, Pfad, Query, Matrix, Header, Body).")],
                "faelle": [
                    {
                        "id": "R-10", "titel": "Query-Parameter mit Sonderzeichen",
                        "schritte": [
                            ("Neuer REST-Request, GET:", f"{S}/api/echo/suche"),
                            ("Im Tab **Parameter** den Query-Parameter `q` anlegen, Wert:", "Grüße & Co. = 100% ?#/"),
                            "Senden.",
                        ],
                        "erwartet": [
                            "`rawQuery` ist URL-kodiert (`%C3%BC`, `%26`, `%3D`, `%25`, `%3F`, `%23`, `%2F`).",
                            "`query.q` zeigt wieder genau `Grüße & Co. = 100% ?#/`.",
                        ],
                    },
                    {
                        "id": "R-11", "titel": "Pfad- und Matrix-Parameter",
                        "schritte": [
                            ("Neuer REST-Request, GET:", f"{S}/api/echo/kunde/{{nr}}"),
                            "Im Tab **Parameter**: `nr` = `C 10/7`, zusätzlich Parameter `farbe` = `rot` mit Art *Matrix*.",
                            "Senden.",
                        ],
                        "erwartet": [
                            "`path` enthält `C%2010%2F7` (Schrägstrich kodiert, kein neues Segment) und `;farbe=rot`.",
                            "`matrix.farbe` ist `rot`.",
                        ],
                    },
                    {
                        "id": "R-12", "titel": "Header und abgeschaltete Parameter",
                        "vorher": f"Request aus R-10 (GET `{S}/api/echo/suche`) - der Echo-Server zeigt, was ankommt.",
                        "schritte": [
                            "Tab **Header**: `X-Test: eins` anlegen.",
                            "Tab **Parameter**: Query-Parameter `aus` = `1` anlegen und per Haken abschalten.",
                            "Tab **Header**: zusätzlich `Content-Length: 999` und `Host: anderswo` anlegen - "
                            "beide dürfen nicht gesendet werden, weil sie sonst den Request verfälschen.",
                            "Senden und die Antwort des Echo-Servers ansehen.",
                        ],
                        "erwartet": [
                            "Status 200 - der Request scheitert **nicht** an den beiden falschen Headern.",
                            "Unter `headers` steht `[\"X-Test\", \"eins\"]`, `Host` ist `localhost:8088` (nicht "
                            "`anderswo`), und es gibt kein `Content-Length` mit `999`.",
                            "Unter `query` steht `q`, aber **nicht** `aus`.",
                            "Unter `headers` steht `[\"User-Agent\", \"LatherIt\"]` (der Request setzt keinen eigenen User-Agent).",
                        ],
                    },
                ],
            },
            {
                "titel": "Methoden und Bodies",
                "faelle": [
                    {
                        "id": "R-20", "titel": "POST mit JSON",
                        "vorher": "Am Projektknoten `Handtest` die Property `name` = `Handtest` anlegen - der Body "
                                  "verwendet `${#Project#name}`.",
                        "schritte": [
                            ("Neuer REST-Request, POST:", f"{S}/api/orders"),
                            ("Tab **Body**: Content-Type `application/json`, Body ([[datei:schnipsel/rest-neue-bestellung.json]]):",
                             open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/rest-neue-bestellung.json"), encoding="utf-8").read()),
                        ],
                        "erwartet": [
                            "201, Header `Location: /api/orders/…`, `received.note` ist `Grüße aus Köln – Handtest` "
                            "(Umlaute, Gedankenstrich und aufgelöste Property).",
                        ],
                    },
                    {
                        "id": "R-21", "titel": "PUT, PATCH, DELETE, HEAD",
                        "schritte": [
                            f"⇧⌘N, im Dialog die Methoden PUT, PATCH, DELETE und HEAD anklicken (GET abwählen), "
                            f"Adresse `{S}/api/orders/4711`, „4 Requests anlegen“. Im Baum entstehen vier Methoden mit je einem Request.",
                            "PUT und PATCH: Body `{\"status\":\"x\"}`, Content-Type `application/json`, senden.",
                            "DELETE mit leerem Body senden; dann Content-Type `text/plain`, Body `grund=test` eintragen und erneut senden.",
                            "HEAD senden.",
                        ],
                        "erwartet": [
                            "PUT/PATCH: 200, `method` und `updatedWith` stimmen.",
                            "DELETE leer: 204 ohne Body; mit Body: 200 und `bodyReceived` = `grund=test`.",
                            "HEAD: 200, leerer Body, Header vorhanden.",
                        ],
                    },
                    {
                        "id": "R-22", "titel": "Ungültiges JSON und 404",
                        "schritte": [
                            "Im POST-Request aus R-20 (Content-Type `application/json` bleibt) den Body durch `{kaputt` ersetzen und senden.",
                            f"GET `{S}/api/orders/9999`.",
                        ],
                        "erwartet": ["400 bzw. 404 als normale Antwort (kein Fehlerdialog), JSON formatiert lesbar."],
                    },
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 5
    {
        "titel": "HTTP",
        "kurz": "Status, Zeiten, Verbindungsfehler, Inhalte, HTTPS, Proxy",
        "text": [("p", "**Ausgangslage:** Projekt `Handtest`, Testserver läuft. „GET `…`“ heißt: neuer REST-Request "
                       "(⇧⌘N) mit dieser Adresse, senden. Für mehrere Adressen in einem Fall genügt ein Request, "
                       "dessen Adresse jeweils geändert wird.")],
        "unter": [
            {
                "titel": "Status und Umleitungen",
                "faelle": [
                    {
                        "id": "H-01", "titel": "Status-Codes",
                        "schritte": [
                            f"GET `{S}/http/status/{{code}}`; im Tab **Parameter** `code` nacheinander auf 200, 204, 404, "
                            "418, 500, 503 setzen und jeweils senden.",
                        ],
                        "erwartet": [
                            "Status und Standardtext („No Content“, „Not Found“ …) stimmen; 418 ohne Text ist in Ordnung.",
                            "204 zeigt einen leeren Body ohne Fehlermeldung.",
                        ],
                    },
                    {
                        "id": "H-02", "titel": "Umleitungen",
                        "schritte": [
                            f"GET `{S}/http/redirect/3`.",
                            f"Neuer REST-Request POST an `{S}/http/redirect-post` - im Tab **Body** Content-Type "
                            "`text/plain` und einen kurzen Text wie `hallo` eintragen, senden",
                        ],
                        "erwartet": [
                            "Erster: Antwort von `/api/echo/ziel` (drei Umleitungen verfolgt).",
                            "Zweiter: Der Server antwortet mit 303; LatherIt folgt mit GET an `/api/echo/nach-303`. "
                            "In der Echo-Antwort steht `\"method\": \"GET\"` und `\"body\": \"\"` - der Text wird "
                            "nach einem 303 bewusst nicht noch einmal geschickt.",
                        ],
                    },
                ],
            },
            {
                "titel": "Zeiten",
                "faelle": [
                    {
                        "id": "H-10", "titel": "Langsame Antwort",
                        "schritte": [f"GET `{S}/http/delay/2500`; währenddessen in der IDE weiterarbeiten (anderer Tab, tippen)."],
                        "erwartet": ["Zeit ≈ 2500 ms; IDE und LatherIt-Tab bleiben bedienbar."],
                    },
                    {
                        "id": "H-11", "titel": "Zeitüberschreitung",
                        "schritte": [
                            f"GET `{S}/http/delay/3000` anlegen, im Tab **Einstellungen** Timeout `1000` ms, senden.",
                        ],
                        "erwartet": ["Nach etwa 1 s Meldung „Zeitüberschreitung: GET http://localhost:8088/http/delay/3000 …“."],
                    },
                ],
            },
            {
                "titel": "Verbindungsfehler",
                "faelle": [
                    {
                        "id": "H-20", "titel": "Nicht erreichbar, unbekannt, abgebrochen, ungültig",
                        "schritte": [
                            "GET `http://localhost:1/`.",
                            "GET `http://gibt-es-nicht.invalid/`.",
                            f"GET `{S}/http/abbruch` (Server schließt die Verbindung ohne Antwort).",
                            "GET `http://a b/` und `htp:/kaputt`.",
                        ],
                        "erwartet": [
                            "Jeweils eine verständliche Meldung mit Adresse und Ursache („Verbindung fehlgeschlagen …“, "
                            "„Fehler …“, „Ungültige Adresse …“) - kein leerer Text, kein Hängen.",
                        ],
                    },
                ],
            },
            {
                "titel": "Inhalte und Zeichensätze",
                "faelle": [
                    {
                        "id": "H-30", "titel": "Zeichensätze",
                        "schritte": [
                            f"GET `{S}/http/charset/latin1` (ISO-8859-1 laut Header).",
                            f"GET `{S}/http/charset/utf16bom` (UTF-16 mit BOM, ohne Charset).",
                            f"GET `{S}/http/charset/ohne-angabe` (UTF-8 ohne Charset).",
                        ],
                        "erwartet": ["Alle drei zeigen die Umlaute korrekt."],
                    },
                    {
                        "id": "H-31", "titel": "Große Antworten",
                        "schritte": [
                            f"GET `{S}/http/big?mb=5` (XML), dann `?mb=20`.",
                            f"GET `{S}/http/big?mb=5&format=json`.",
                            "In der Antwort scrollen, ans Ende und wieder an den Anfang springen (⌘↓ / ⌘↑), Blöcke über die Pfeile am Rand falten, "
                            "zwischen den Tabs **Formatiert** und **Roh** wechseln, suchen (⌘F). Die Antwort ist nur "
                            "lesbar - ⇧⌥F gibt es dort nicht, formatiert wird über den Tab.",
                        ],
                        "erwartet": [
                            "Antworten erscheinen nach wenigen Sekunden; die IDE friert nicht ein (kein „IDE is not responding“).",
                            "Scrollen bleibt flüssig. Bei 20 MB darf es langsamer werden - Zeiten ins Ticket.",
                        ],
                    },
                    {
                        "id": "H-32", "titel": "Besondere Inhalte", "tag": "geändert 24.10.",
                        "schritte": [
                            f"GET `{S}/http/binary`, `{S}/http/html`, `{S}/http/empty`.",
                            f"GET `{S}/http/gzip` (Server komprimiert ungefragt).",
                            f"GET `{S}/http/headers`.",
                        ],
                        "erwartet": [
                            "Binär: statt Zeichensalat steht unter *Formatiert* und *Roh* „Binärinhalt (application/octet-stream), "
                            "1.024 Bytes“ mit dem Knopf **Trotzdem als Text zeigen**; *Header* zeigt normal die Header. "
                            "HTML als Text. Leer: kein Fehler.",
                            "gzip: lesbarer Text „Dieser Text kam gzip-komprimiert an, obwohl niemand danach gefragt "
                            "hat.“ - LatherIt entpackt die Antwort wie die Desktop-App; der Header `Content-Encoding: gzip` bleibt sichtbar.",
                            "Headers: beide `Set-Cookie`-Zeilen einzeln, der 4000 Zeichen lange `X-Lang` bricht das Layout nicht.",
                        ],
                    },
                ],
            },
            {
                "titel": "HTTPS",
                "faelle": [
                    {
                        "id": "H-40", "titel": "Selbst signiertes Zertifikat",
                        "schritte": ["GET `https://localhost:8443/api/orders/4711`."],
                        "erwartet": [
                            "Die IDE fragt mit ihrem Zertifikatsdialog (Server ist nicht vertrauenswürdig).",
                            "Nach „Akzeptieren“ kommt die Antwort; ein zweiter Request fragt nicht noch einmal.",
                            "Bei „Ablehnen“ verständliche Fehlermeldung („Zertifikat nicht vertrauenswürdig oder abgelehnt …“). "
                            "Die IDE merkt sich die Ablehnung bis zum Neustart und fragt solange nicht noch einmal - das ist so gewollt.",
                        ],
                        "hinweis": "Akzeptierte Zertifikate stehen unter *Settings | Tools | Server Certificates* "
                                   "und lassen sich dort wieder entfernen. Bekannt: Danach beendet sich der Browser-Prozess "
                                   "der IDE (2026.2.3) - offene LatherIt-Tabs reagieren nicht mehr, neue zeigen „Bitte die IDE "
                                   "neu starten“. Also erst am Ende entfernen und die IDE dann neu starten.",
                    },
                    {
                        "id": "H-42", "titel": "Zertifikat entfernen: liegt es an der IDE?", "tag": "neu 24.10.",
                        "ziel": "Klären, ob das Beenden des Browser-Prozesses (Hinweis bei H-40) ein Fehler der IDE ist.",
                        "vorher": "Am Ende einer Sitzung - danach muss die IDE neu starten.",
                        "schritte": [
                            "LatherIt-Tab schließen. Eine `.md`-Datei öffnen und oben rechts auf **Preview** stellen.",
                            "Unter *Settings | Tools | Server Certificates* das in H-40 akzeptierte Zertifikat entfernen.",
                            "In der Vorschau scrollen und eine zweite `.md`-Datei mit Vorschau öffnen.",
                        ],
                        "erwartet": [
                            "Bekannter Fehler der IDE ([[url:https://youtrack.jetbrains.com/issue/IJPL-259611|IJPL-259611]]): "
                            "Beim Öffnen der zweiten Vorschau erscheint eine NullPointerException in "
                            "`RemoteMessageRouterImpl.create`. Ist er behoben, bleibt sie aus.",
                        ],
                    },
                    {
                        "id": "H-41", "titel": "Öffentliche Zertifikate und Fehlerfälle",
                        "schritte": [
                            "GET [[url:https://example.com/]].",
                            "GET [[url:https://expired.badssl.com/]], [[url:https://wrong.host.badssl.com/]], "
                            "[[url:https://untrusted-root.badssl.com/]].",
                        ],
                        "erwartet": [
                            "example.com ohne Rückfrage.",
                            "Abgelaufen und falscher Host: Fehler, auch nach „Akzeptieren“ keine stille Annahme des falschen Hosts.",
                        ],
                    },
                ],
            },
            {
                "titel": "Proxy",
                "text": [("p", "LatherIt nimmt den Proxy aus *Settings | Appearance & Behavior | System Settings | HTTP Proxy*. "
                               "Für `localhost` umgeht Java den Proxy - daher hier Ziele im Internet. Der Testproxy "
                               "protokolliert jeden Zugriff mit `[Proxy]` im Server-Terminal.")],
                "faelle": [
                    {
                        "id": "H-50", "titel": "HTTP und HTTPS über den Proxy",
                        "schritte": [
                            "In der IDE *Manual proxy configuration*, HTTP, Host `127.0.0.1`, Port `8089`.",
                            "GET [[url:http://neverssl.com/]] und GET [[url:https://example.com/]].",
                        ],
                        "erwartet": [
                            "Beide Antworten kommen; Terminal zeigt `[Proxy] GET http://neverssl.com/` und "
                            "`[Proxy] CONNECT example.com:443`.",
                        ],
                    },
                    {
                        "id": "H-51", "titel": "Proxy mit Anmeldung",
                        "schritte": [
                            ("Testserver neu starten mit:", "python3 testserver.py --proxy-auth"),
                            "In der IDE beim Proxy *Proxy authentication* mit `proxy` / `proxy`.",
                            "H-50 wiederholen. Danach falsches Passwort eintragen und wiederholen.",
                        ],
                        "erwartet": [
                            "Mit richtigem Passwort: beide Requests gehen durch.",
                            "Falsches Passwort: verständliche Meldung (407), kein endloses Wiederholen.",
                        ],
                        "hinweis": "Java sperrt Basic-Anmeldung beim HTTPS-Tunnel standardmäßig "
                                   "(`jdk.http.auth.tunneling.disabledSchemes`). Scheitert nur HTTPS, ist das ein Befund fürs Ticket.",
                    },
                    {
                        "id": "H-52", "titel": "Proxy wieder aus",
                        "schritte": ["*No proxy* einstellen, H-50 wiederholen."],
                        "erwartet": ["Requests gehen direkt, das Terminal zeigt kein `[Proxy]` mehr - ohne IDE-Neustart."],
                    },
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 6
    {
        "titel": "Anmeldung",
        "kurz": "Basic, Bearer, Digest, NTLM, Projektstandard",
        "text": [("p", "**Ausgangslage:** Projekt `Handtest`, Testserver läuft, jeder Fall mit neuem REST-Request "
                       "(außer unter *Vorbereitung* steht ein anderer). Anmeldedaten stellt man im Tab **Anmeldung** "
                       "des Requests ein. Benutzer `anna`, Passwort `geheim`, Bearer-Token `test-token-123`. Das "
                       "Server-Terminal zeigt den Anfang jedes `Authorization`-Headers.")],
        "unter": [
            {
                "titel": "Basic",
                "faelle": [
                    {
                        "id": "A-01", "titel": "Basic richtig und falsch",
                        "schritte": [
                            f"GET `{S}/auth/basic` ohne Anmeldung.",
                            "Tab **Anmeldung**: Basic, `anna` / `geheim`, senden.",
                            "Passwort `falsch`, senden.",
                        ],
                        "erwartet": [
                            "Ohne: 401, LatherIt nennt „Basic“ als angebotenes Verfahren.",
                            "Richtig: 200 mit `\"verfahren\": \"Basic\"`. **Gesendet** zeigt die Anmeldung ohne Passwort.",
                            "Falsch: 401.",
                        ],
                    },
                ],
            },
            {
                "titel": "Bearer",
                "faelle": [
                    {
                        "id": "A-10", "titel": "Bearer-Token",
                        "schritte": [f"GET `{S}/auth/bearer`, Anmeldung Bearer, Token `test-token-123`."],
                        "erwartet": ["200 mit `\"verfahren\": \"Bearer\"`; Terminal zeigt `Authorization: Bearer test-token-123`."],
                    },
                    {
                        "id": "A-11", "titel": "Token aus Umgebungsvariable",
                        "vorher": "V-03 erledigt, IDE danach neu gestartet. Request aus A-10.",
                        "schritte": [("Im Tab **Anmeldung** ins Token-Feld eintragen, senden:", "${#Env#LATHERIT_TOKEN}")],
                        "erwartet": ["200. Im Projekt steht nur der Platzhalter, nicht das Token (Text-Tab prüfen)."],
                    },
                    {
                        "id": "A-12", "titel": "Falsches Token",
                        "vorher": "Request aus A-10.",
                        "schritte": ["Token `abc` eintragen, senden."],
                        "erwartet": ["401 mit `WWW-Authenticate: Bearer … error=\"invalid_token\"` in den Antwort-Headern."],
                    },
                ],
            },
            {
                "titel": "Digest",
                "faelle": [
                    {
                        "id": "A-20", "titel": "Digest mit GET und POST",
                        "schritte": [
                            f"GET `{S}/auth/digest`, Anmeldung Digest, `anna` / `geheim`.",
                            f"Neuer REST-Request POST an `{S}/auth/digest`, Content-Type `application/json`, Body `{{\"a\":1}}`, Anmeldung wie oben, senden.",
                        ],
                        "erwartet": [
                            "Beide 200 mit `\"verfahren\": \"Digest\"`.",
                            "Terminal zeigt je Request zwei Zeilen: erst ohne, dann mit `Authorization: Digest …`.",
                            "Zeitangabe umfasst beide Runden.",
                        ],
                    },
                ],
            },
            {
                "titel": "NTLM",
                "text": [("p", "Der Testserver spielt nur den Ablauf nach (Challenge auf derselben Verbindung) und prüft "
                               "das Passwort nicht. Die Kryptografie prüfen die automatischen Tests; gegen einen echten "
                               "Windows-Server siehe WIN-05.")],
                "faelle": [
                    {
                        "id": "A-30", "titel": "NTLM-Ablauf",
                        "schritte": [
                            f"GET `{S}/auth/ntlm`, Anmeldung NTLM, Benutzer `bob`, Passwort `x`, Domäne `FIRMA`, Rechner `PC1`.",
                            "Im selben Request Domäne leeren, Benutzer `FIRMA\\bob`, erneut senden.",
                        ],
                        "erwartet": [
                            "200 mit `domaene: FIRMA`, `benutzer: bob`, `rechner: PC1`.",
                            "Bei `FIRMA\\bob` dasselbe Ergebnis (Domäne wird abgetrennt).",
                            "Terminal: drei Zeilen mit **derselben** Verbindungs-ID in eckigen Klammern.",
                        ],
                    },
                ],
            },
            {
                "titel": "Projektstandard und Auswahl",
                "faelle": [
                    {
                        "id": "A-40", "titel": "„Wie im Projekt“",
                        "schritte": [
                            "Im Baum den Projektknoten `Handtest` wählen, dort Anmeldung Basic `anna` / `geheim` als Standard.",
                            f"Neuer Request an `{S}/auth/basic` mit „Wie im Projekt“ senden.",
                            "Im selben Request Anmeldung „Keine“ wählen und senden.",
                            "Danach die Projekt-Anmeldung wieder entfernen (sonst wirkt sie in späteren Fällen mit).",
                        ],
                        "erwartet": ["Erst 200, dann 401."],
                    },
                    {
                        "id": "A-41", "titel": "Server bietet mehrere Verfahren an",
                        "schritte": [f"GET `{S}/auth/alle` ohne Anmeldung."],
                        "erwartet": ["401; LatherIt nennt Basic, Digest und NTLM."],
                    },
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 7
    {
        "titel": "GraphQL",
        "kurz": "Abfragen über REST-POST, Fehler, Assertions, Mock",
        "text": [("p", "LatherIt hat (noch) keine eigene GraphQL-Unterstützung. GraphQL läuft als REST-Request: POST mit "
                       "JSON-Body `{\"query\": …, \"variables\": …}`. Diese Fälle prüfen, ob das im Alltag trägt, und "
                       "liefern Material für die Entscheidung über eine eigene GraphQL-Ansicht (Pro-Idee)."),
                 ("p", "**Ausgangslage:** Projekt `Handtest`, Testserver läuft. G-01 legt den POST-Request an; "
                       "G-02, G-03, G-10 und G-11 verwenden ihn weiter und ersetzen nur den Body.")],
        "unter": [
            {
                "titel": "Abfragen",
                "faelle": [
                    {
                        "id": "G-01", "titel": "Query mit Variablen",
                        "schritte": [
                            ("Neuer REST-Request, POST, im Tab **Body** Content-Type `application/json`:", f"{S}/graphql"),
                            ("Body ([[datei:schnipsel/graphql-bestellung.json]]):",
                             open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/graphql-bestellung.json"), encoding="utf-8").read()),
                        ],
                        "erwartet": ["200 mit `data.order.id` = `4711` und `status` = `open`."],
                    },
                    {
                        "id": "G-02", "titel": "Liste mit JSONPath-Assertion",
                        "vorher": "Request aus G-01.",
                        "schritte": [
                            ("Body ersetzen ([[datei:schnipsel/graphql-liste.json]]):",
                             open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/graphql-liste.json"), encoding="utf-8").read()),
                            "Senden. Dann oben rechts „Als Step in einen Test-Case übernehmen“ › *Neue Test-Suite*, den Step wählen.",
                            "Im Tab **Assertions** des Steps: *JSONPath Anzahl* `$.data.orders[*]` = `4` und "
                            "*JSONPath vorhanden* `$.errors` = `false`. Step mit ⌘↵ ausführen.",
                        ],
                        "erwartet": ["Beide Assertions grün."],
                    },
                    {
                        "id": "G-03", "titel": "Mutation mit Platzhalter",
                        "vorher": "Request aus G-01 (nicht der Step aus G-02). Am Projektknoten `Handtest` Property `kunde` = `C-1007`.",
                        "schritte": [("Body ersetzen und senden ([[datei:schnipsel/graphql-mutation.json]]):",
                                      open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/graphql-mutation.json"), encoding="utf-8").read())],
                        "erwartet": ["`data.createOrder.customer` = `C-1007`; **Gesendet** zeigt den aufgelösten Wert."],
                        "hinweis": "Platzhalter in JSON-Strings: `${…}` steht in Anführungszeichen und wird ersetzt. "
                                   "GraphQL selbst nutzt `$name` ohne Klammern - das darf LatherIt nicht anfassen.",
                    },
                    {
                        "id": "G-04", "titel": "GET-Abfrage in der Adresse",
                        "schritte": [
                            f"Neuer REST-Request GET `{S}/graphql`, im Tab **Parameter** Query-Parameter `query` = "
                            "`{ orders { id } }`, senden.",
                        ],
                        "erwartet": ["Klammern und Leerzeichen werden kodiert, Antwort enthält `data.orders`."],
                    },
                ],
            },
            {
                "titel": "Fehler",
                "text": [("p", "GraphQL meldet Fehler meist mit Status 200 und einem `errors`-Feld. "
                               "Eine HTTP-Status-Assertion reicht daher nicht.")],
                "faelle": [
                    {
                        "id": "G-10", "titel": "Syntaxfehler und Teilergebnis",
                        "vorher": "Step aus G-02 (*Test-Suite › Test-Case › …*).",
                        "schritte": [
                            "Im Tab **Assertions** die *JSONPath Anzahl* löschen und *HTTP-Status* `200` ergänzen "
                            "(*JSONPath vorhanden* `$.errors` = `false` bleibt).",
                            ("Body mit Syntaxfehler ([[datei:schnipsel/graphql-fehler.json]]):",
                             open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/graphql-fehler.json"), encoding="utf-8").read()),
                            ("Body mit unbekannter ID ([[datei:schnipsel/graphql-nicht-gefunden.json]]):",
                             open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/graphql-nicht-gefunden.json"), encoding="utf-8").read()),
                            "Nach jedem Body-Wechsel den Step mit ⌘↵ ausführen.",
                        ],
                        "erwartet": [
                            "Status jeweils 200, *HTTP-Status* grün, *JSONPath vorhanden* rot - der Fehler wird also nur "
                            "über `$.errors` erkannt.",
                            "Zweiter Fall: `data.order` ist `null`, `errors[0].path` = `[\"order\"]`.",
                        ],
                    },
                    {
                        "id": "G-11", "titel": "Ungültiges JSON",
                        "vorher": "Request aus G-01.",
                        "schritte": ["Body durch `{\"query\": \"{ orders }\"` ersetzen (fehlende Klammer am Ende; Content-Type `application/json` bleibt), senden."],
                        "erwartet": ["400 mit `errors[0].message` „Kein gültiges JSON …“."],
                    },
                ],
            },
            {
                "titel": "GraphQL-Mock",
                "faelle": [
                    {
                        "id": "G-20", "titel": "Mock mit Auswahl nach Regel",
                        "schritte": [
                            "Im Baum mit der Maus über den REST-Service `localhost:8088` fahren (darunter liegt `/graphql` "
                            "aus G-01). Rechts in der Zeile erscheinen kleine Symbole - das Server-Symbol (Tooltip "
                            "„Mock-Service erzeugen“) anklicken.",
                            "Unten im Baum entsteht *localhost:8088 Mock* mit einer Aktion je Methode des Services, "
                            "darunter `POST /graphql`. Den Mock wählen und den Port auf `8098` stellen (8088 belegt der Testserver).",
                            "Aktion `POST /graphql` aufklappen: *Response 1* in `Liste` umbenennen, Body `{\"data\":{\"orders\":[]}}`. "
                            "Eine zweite Response `Einzeln` mit Body `{\"data\":{\"order\":{\"id\":\"1\"}}}` anlegen.",
                            "Die Aktion `POST /graphql` selbst wählen (nicht eine Response). Unter *Response-Auswahl* "
                            "**Auswahl** = *Nach Regeln*, dann *Regel hinzufügen*: Quelle `Body`, Ausdruck `$.variables.id`, "
                            "Wert **leer lassen** (leer heißt „vorhanden“), Response `Einzeln`.",
                            "Darunter **Standard** = `Liste` - gilt, wenn keine Regel zutrifft.",
                            "Mock starten: im Baum *localhost:8088 Mock* wählen und oben rechts **Starten** (oder ⌘↵). Im Request aus G-01 den Endpunkt auf `http://localhost:8098` "
                            "ändern (der Pfad `/graphql` bleibt) und nacheinander mit dem Body aus G-01 und aus G-02 senden.",
                            "Zum Schluss den Endpunkt wieder auf `http://localhost:8088` stellen.",
                        ],
                        "erwartet": [
                            "Body aus G-01 (mit `variables.id`) bekommt `Einzeln`, Body aus G-02 bekommt `Liste`.",
                            "Das Mock-Protokoll zeigt, welche Regel gegriffen hat.",
                        ],
                    },
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 8
    {
        "titel": "Platzhalter und Skripte",
        "kurz": "Inline-Skripte, System/Env, Skript-Steps, Zeitlimits",
        "text": [("p", "**Ausgangslage:** Projekt `Handtest`, Testserver läuft, V-03 erledigt. S-01 legt den Request an, "
                       "S-02 und das Inline-Skript in S-04 verwenden ihn weiter; S-03 legt einen Test-Case an, den S-04 nutzt.")],
        "faelle": [
            {
                "id": "S-01", "titel": "Inline-Skripte und Systemwerte",
                "schritte": [
                    (f"Neuer REST-Request POST an `{S}/api/echo/platzhalter`, Content-Type `text/plain`, Body ([[datei:schnipsel/inline-skripte.txt]]):",
                     open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/inline-skripte.txt"), encoding="utf-8").read()),
                    "Mit der Maus über die Platzhalter fahren, dann senden.",
                ],
                "erwartet": [
                    "Hover zeigt die Werte; **Gesendet** und `body` in der Antwort enthalten Datum, UUID, Zahl, "
                    "Benutzerordner, Betriebssystem und „Hallo aus der Umgebung“.",
                ],
            },
            {
                "id": "S-02", "titel": "Unbekannte Platzhalter",
                "vorher": "Request aus S-01.",
                "schritte": [("Body ersetzen (Content-Type `text/plain` bleibt), senden:", "${#Project#gibtEsNicht} ${#Env#GIBT_ES_NICHT}")],
                "erwartet": ["Im Editor rot markiert; nach dem Senden nennt LatherIt beide Platzhalter; gesendet wird leerer Text."],
            },
            {
                "id": "S-03", "titel": "Skript-Step",
                "schritte": [
                    f"Neuer REST-Request GET `{S}/api/orders/4711`, senden, über „Als Step in einen Test-Case übernehmen“ "
                    "› *Neue Test-Suite* zum Step machen und den Step in `Lesen` umbenennen (F2).",
                    "Am Test-Case einen Skript-Step anlegen.",
                    ("Danach Skript-Step ([[datei:schnipsel/skript-step.js]]):",
                     open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/skript-step.js"), encoding="utf-8").read()),
                    "Test-Case ausführen.",
                ],
                "erwartet": [
                    "Log zeigt „Status open Summe 23.8“; Test-Case-Property `kunde` = `C-1007`; Ergebnis `4711`.",
                ],
            },
            {
                "id": "S-04", "titel": "Endlosschleifen werden abgebrochen",
                "schritte": [
                    ("Im Test-Case aus S-03 einen weiteren Skript-Step mit diesem Inhalt, Test-Case ausführen:", "while (true) {}"),
                    ("Im Request aus S-01 den Body ersetzen (Content-Type `text/plain` bleibt) und senden:", "${=(() => { while (true) {} })()}"),
                ],
                "erwartet": [
                    "Skript-Step bricht nach etwa 5 s ab, das Inline-Skript nach etwa 1 s, jeweils mit Meldung.",
                    "IDE und andere Tabs bleiben die ganze Zeit bedienbar (Web-Worker).",
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 9
    {
        "titel": "Tests, Assertions und Last-Test",
        "kurz": "Demo-Projekt, Property Transfer, Abbruch, Last",
        "text": [("p", "**Ausgangslage:** `shop-api.latherit.yaml` aus dem Arbeitsordner (Demo-Projekt mit Suite "
                       "*Smoke tests* und *Shop Mock*). T-04 legt einen eigenen Test-Case im Projekt `Handtest` an.")],
        "faelle": [
            {
                "id": "T-01", "titel": "Demo-Test-Suite gegen den Mock",
                "vorher": "`shop-api.latherit.yaml` offen; Mock *Shop Mock* gestartet (M-01).",
                "schritte": ["Suite *Smoke tests* mit dem Play-Symbol ausführen."],
                "erwartet": ["Alle Steps grün; Status und Laufzeiten im Baum und im Ergebnis-Log."],
            },
            {
                "id": "T-02", "titel": "Property Transfer nach #Project#",
                "schritte": [
                    "In *Smoke tests* im Test-Case nach dem Step *Create order* einen Property Transfer anlegen: Quelle Step *Create order* › `Response`, "
                    "Pfad `$.id`; Ziel `#Project#` › `letzteBestellung`.",
                    "Ausführen, danach den Text-Tab ansehen.",
                ],
                "erwartet": ["Projekt-Property `letzteBestellung` = `4714`, auch in der Datei gespeichert."],
            },
            {
                "id": "T-03", "titel": "Abbruch bei Fehler",
                "schritte": ["*Shop Mock* stoppen, Suite *Smoke tests* erneut ausführen. Danach den Mock wieder starten."],
                "erwartet": ["Erster Step rot mit Verbindungsfehler; wegen „Bei fehlgeschlagenem Step abbrechen“ laufen die übrigen nicht."],
            },
            {
                "id": "T-04", "titel": "Kurzer Last-Test",
                "schritte": [
                    f"Im Projekt `Handtest`: neuer REST-Request GET `{S}/api/orders`, als Step in eine neue Test-Suite übernehmen.",
                    "Am Test-Case „Last-Test anlegen“: 10 Nutzer, 15 s, Anlauf 3 s.",
                    "Starten und währenddessen in der IDE tippen und scrollen.",
                ],
                "erwartet": [
                    "Zahlen und Diagramme laufen hoch, keine Fehler.",
                    "IDE bleibt bedienbar; nach Ende ist die Projektdatei unverändert (`git status`).",
                ],
                "hinweis": "Nur gegen den eigenen Testserver.",
            },
        ],
    },
    # ------------------------------------------------------------------ 10
    {
        "titel": "Mock-Services im Plugin",
        "kurz": "Laufen solange der Tab offen ist",
        "text": [("p", "**Ausgangslage:** `shop-api.latherit.yaml` mit *Shop Mock* (Port 8099); nur M-07 arbeitet im "
                       "Projekt `Handtest`. Im Plugin gehört ein Mock zum Editor-Tab, der ihn gestartet hat, und endet mit ihm. "
                       "Für die Requests von außen ein Terminal nutzen: [[datei:schnipsel/mock-curl.sh]] "
                       "(Windows: [[datei:schnipsel/mock-curl.ps1]]).")],
        "faelle": [
            {
                "id": "M-01", "titel": "Demo-Mock starten und von außen aufrufen",
                "schritte": [
                    "In `shop-api.latherit.yaml` *Shop Mock* wählen, oben rechts **Starten** oder ⌘↵.",
                    ("Im Terminal:", "curl -i http://localhost:8099/shop/orders\n"
                                     "curl -i http://localhost:8099/shop/orders/4711\n"
                                     "curl -i http://localhost:8099/shop/gibt-es-nicht"),
                ],
                "erwartet": [
                    "Liste mit drei Bestellungen; Einzelbestellung mit `\"id\": \"4711\"` (aus `${id}`); unbekannter Pfad 404.",
                    "Das Mock-Protokoll in LatherIt zeigt alle drei Requests mit gewählter Response.",
                ],
            },
            {
                "id": "M-02", "titel": "Stoppen und erneut starten",
                "schritte": ["⌘↵ zum Stoppen, curl wiederholen, wieder starten, curl wiederholen."],
                "erwartet": ["Gestoppt: `Connection refused`. Wieder gestartet: Antworten wie in M-01."],
            },
            {
                "id": "M-03", "titel": "Tab schließen beendet den Mock",
                "schritte": [
                    "In *Shop Mock* bei der Aktion für `/orders` die Response auf 10000 ms Verzögerung stellen, Mock starten.",
                    ("Im Terminal starten und **während** er wartet den LatherIt-Tab schließen:",
                     "curl -i http://localhost:8099/shop/orders"),
                    "Danach curl erneut. Zum Schluss die Verzögerung wieder auf 0 setzen.",
                ],
                "erwartet": [
                    "Der wartende curl endet sofort (Fehler oder 504), nicht erst nach 2 Minuten.",
                    "Danach `Connection refused`; Tab wieder öffnen und starten geht ohne „Port belegt“.",
                ],
            },
            {
                "id": "M-04", "titel": "Port belegt",
                "schritte": ["Mock stoppen, an *Shop Mock* den Port auf `8088` stellen (dort läuft der Testserver) und starten.",
                             "Danach Port wieder `8099`."],
                "erwartet": ["Meldung „Port 8088 lässt sich nicht öffnen: …“; Mock gilt als nicht gestartet."],
            },
            {
                "id": "M-05", "titel": "Neuladen der Oberfläche bei laufendem Mock",
                "schritte": [
                    "Mock starten, dann im Text-Tab eine Kleinigkeit ändern und zurück in den LatherIt-Tab (lädt neu).",
                    "curl wiederholen, Mock in LatherIt stoppen.",
                ],
                "erwartet": ["Nach dem Neuladen zeigt LatherIt den Mock weiter als laufend; Antworten kommen; Stoppen funktioniert."],
            },
            {
                "id": "M-06", "titel": "Auswahl per Skript und Verzögerung",
                "schritte": [
                    "In *Shop Mock* eine neue REST-Aktion mit Methode `GET` und Pfad `/x` und drei Responses `Standard` (200), `Angelegt` (201), `Fehler` (500, Verzögerung 2000 ms).",
                    ("Auswahl *Per Skript* ([[datei:schnipsel/skript-mock-dispatch.js]]):",
                     open(__file__.replace("testplan_inhalt.py", "dateien/schnipsel/skript-mock-dispatch.js"), encoding="utf-8").read()),
                    ("Aufrufen:", "curl -i 'http://localhost:8099/shop/x?kunde=Anna'\n"
                                  "curl -i 'http://localhost:8099/shop/x?neu=1'\n"
                                  "curl -i -H 'X-Fehler: 1' http://localhost:8099/shop/x"),
                ],
                "erwartet": ["200, 201, 500 (nach 2 s); `${kunde}` im Body wird zu `Anna` bzw. `unbekannt`."],
            },
            {
                "id": "M-07", "titel": "SOAP-Mock aus der WSDL",
                "schritte": [
                    "Im Projekt `Handtest` am Interface `ShopSoap11` das Server-Symbol „Mock-Service aus der WSDL erzeugen“, "
                    "Port `8097`, starten.",
                    "Im Request aus W-11 den Endpunkt auf `http://localhost:8097/…` (Pfad wie am Mock angezeigt) ändern und senden.",
                    "Zum Schluss Endpunkt wieder `http://localhost:8088/shop`.",
                ],
                "erwartet": ["Beispiel-Response aus dem Schema; unbekannte Operation ergibt einen SOAP-Fault."],
            },
            {
                "id": "M-08", "titel": "Mock auch aus dem Netzwerk",
                "schritte": [
                    "*Shop Mock* stoppen, „Auch aus dem Netzwerk erreichbar“ einschalten, starten.",
                    "Von einem anderen Gerät (Handy im WLAN) `http://<IP des Rechners>:8099/shop/orders` öffnen.",
                ],
                "erwartet": ["Antwort kommt an. Ohne die Option ist der Mock von außen nicht erreichbar."],
                "hinweis": "Firewall-Rückfragen sind plattformabhängig, siehe MAC-05 und WIN-07.",
            },
        ],
    },
    # ------------------------------------------------------------------ 11
    {
        "titel": "SoapUI-Import",
        "kurz": "Menü, Bericht, importierte Tests ausführen",
        "faelle": [
            {
                "id": "I-01", "titel": "Import über das Menü",
                "schritte": [
                    "Rechtsklick auf einen Ordner › *New › LatherIt Project from SoapUI…*",
                    "[[datei:soapui/shop-soapui-project.xml]] wählen, Namen bestätigen.",
                ],
                "erwartet": [
                    "Neue Datei `shop.latherit.yaml` (bzw. gewählter Name) öffnet sich, der Import-Bericht erscheint.",
                    "Die SoapUI-Datei bleibt unverändert.",
                    "Tab schließen und öffnen: Der Import läuft **nicht** ein zweites Mal.",
                ],
            },
            {
                "id": "I-02", "titel": "Importierte Tests gegen den Testserver",
                "schritte": ["Alle Suites des importierten Projekts ausführen (Testserver bedient 8088 und 8090)."],
                "erwartet": [
                    "SOAP-Steps gegen `localhost:8088/shop` und REST-Steps gegen `localhost:8090/api` laufen; "
                    "rote Steps mit Grund ins Ticket (Testserver ist kein Nachbau des Original-Dienstes).",
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 12
    {
        "titel": "Bedienung in der IDE",
        "kurz": "Dialoge, Zwischenablage, Tastenkürzel, Darstellung",
        "unter": [
            {
                "titel": "Dialoge",
                "faelle": [
                    {
                        "id": "B-01", "titel": "Öffnen- und Speichern-Dialoge",
                        "schritte": [
                            "⌘I › „Datei wählen“: Dialog zeigt nur passende Dateien? Abbrechen.",
                            "⇧⌘S (Speichern unter): Dialog öffnet im Projektordner mit Vorschlag `<Name>.latherit.yaml`; "
                            "unter neuem Namen speichern.",
                            "Beide Dialoge mit Esc abbrechen.",
                        ],
                        "erwartet": [
                            "Dialoge sind die der IDE, modal und vor dem Fenster; Abbrechen ändert nichts.",
                            "Speichern unter legt eine neue Datei an; der aktuelle Tab bleibt bei der alten Datei.",
                        ],
                    },
                    {
                        "id": "B-02", "titel": "Links und Rückfragen der Oberfläche",
                        "schritte": [
                            "Einen Link in der Oberfläche (z. B. Hilfe oder Handbuch) anklicken.",
                            "Im Projekt `Handtest` das untere (doppelte) `ShopSoap11` aus W-02 wählen und ⌘⌫ drücken; "
                            "die Rückfrage einmal mit Abbrechen und einmal mit Löschen beantworten.",
                        ],
                        "erwartet": [
                            "Links öffnen im System-Browser, nicht im Tab.",
                            "Rückfrage erscheint innerhalb der Oberfläche und ist mit Tastatur bedienbar.",
                        ],
                    },
                ],
            },
            {
                "titel": "Zwischenablage",
                "faelle": [
                    {
                        "id": "B-10", "titel": "Kopieren und Einfügen",
                        "schritte": [
                            "Request aus W-11 senden. In der Response Text markieren, ⌘C, in eine andere IDE-Datei ⌘V.",
                            "In einer IDE-Datei Text kopieren, im Request-Body ⌘V.",
                            "Knopf „Body kopieren“ am Editor, in eine IDE-Datei einfügen.",
                            "Knopf „Durch Zwischenablage ersetzen“, danach ⌘Z.",
                        ],
                        "erwartet": [
                            "Alle Richtungen funktionieren, Umlaute, Tabs, Zeilenumbrüche und Emoji (😀) bleiben erhalten.",
                            "⌘Z stellt den alten Body wieder her (in LatherIt, nicht das Undo der IDE).",
                        ],
                    },
                    {
                        "id": "B-11", "titel": "Große Inhalte",
                        "schritte": ["Request aus H-31 mit `?mb=5` senden, die Antwort komplett kopieren (⌘A, ⌘C) und in eine neue Scratch-Datei einfügen."],
                        "erwartet": ["Funktioniert in wenigen Sekunden, ohne Hänger."],
                    },
                ],
            },
            {
                "titel": "Tastenkürzel",
                "text": [
                    ("p", "Das Plugin fängt diese Kürzel ab, damit nicht die IDE sie ausführt. Für jedes gilt: "
                          "**im LatherIt-Tab** löst es die LatherIt-Aktion aus, **in einer anderen Datei** die "
                          "normale IDE-Aktion."),
                    ("tabelle", ["Kürzel", "LatherIt-Aktion", "IDE-Aktion außerhalb (Beispiel)"], [
                        ["⌘↵", "Senden / ausführen / Mock starten", "Show Context Actions u. a."],
                        ["⌘S", "Speichern", "Save All"],
                        ["⇧⌘S", "Speichern unter", "-"],
                        ["⌘I", "WSDL importieren", "Implement Methods"],
                        ["⇧⌘N", "REST-Request anlegen", "New Scratch File"],
                        ["⌘B", "Aufs Board / vom Board", "Go to Declaration"],
                        ["⇧⌘F", "Find in Files der IDE, mit dem markierten Text", "Find in Files"],
                        ["⌥⌘F", "Baum filtern (markierter Text als Filter)", "Introduce Field"],
                        ["⌘P", "Schnellsuche „Gehe zu …“ (markierter Text als Suchbegriff)", "Parameter Info"],
                        ["⌥⌘← / ⌥⌘→", "Zurück / Vor", "Back / Forward"],
                        ["⌃⇥ / ⌃⇧⇥", "Nächster / voriger LatherIt-Tab", "Switcher"],
                        ["⌘W", "LatherIt-Tab schließen (ohne Tab: den Editor)", "Close Tab"],
                        ["⌘1 / ⌘2", "Fokus Baum / Editor", "Project / Bookmarks Tool Window"],
                        ["⌘A", "Alles auswählen im Feld bzw. Editor", "Select All"],
                        ["⇧⌥F", "Body formatieren", "-"],
                        ["⌥⌘L (Kürzel von *Reformat Code*)", "Body formatieren (folgt der Keymap)", "Reformat Code"],
                        ["⇧⌘P", "Property einfügen (Vorschlagsliste)", "-"],
                        ["⇧⌘V", "Markierung als Property anlegen", "Paste from History"],
                        ["⌘↑ (nur Mac)", "An den Anfang des Editors", "Jump to Navigation Bar"],
                    ]),
                ],
                "faelle": [
                    {
                        "id": "B-20", "titel": "Abgefangene Kürzel", "tag": "geändert 24.10.",
                        "schritte": [
                            "Jedes Kürzel der Tabelle im LatherIt-Tab einmal mit Fokus im Baum und einmal mit Fokus im Editor drücken.",
                            "Dieselben Kürzel in einer normalen Textdatei drücken.",
                        ],
                        "erwartet": ["Im LatherIt-Tab nur die LatherIt-Aktion (keine IDE-Aktion zusätzlich), sonst die IDE-Aktion."],
                    },
                    {
                        "id": "B-21", "titel": "Nicht abgefangene Kürzel", "tag": "geändert 24.10.",
                        "schritte": [
                            "Im LatherIt-Tab: ⌘N, ⌘O, ⌘,, F2, ⌘⌫, ⌃1 … ⌃9, ⌘Z / ⇧⌘Z, ⌘F im Editor, Esc.",
                        ],
                        "erwartet": [
                            "Für jedes Kürzel notieren, wer reagiert (LatherIt oder IDE). Erwartet: F2, ⌘⌫, ⌃1-9, "
                            "⌘Z, ⌘F (Suchfeld im Editor) wirken in LatherIt; ⌘, öffnet die IDE-Einstellungen.",
                            "Abweichungen und Doppelauslösungen ins Ticket.",
                        ],
                    },
                    {
                        "id": "B-23", "titel": "Tastenbedienung im Editor", "tag": "neu 24.10.",
                        "vorher": "Request aus W-11 (XML-Body).",
                        "schritte": [
                            "Im Body: ⌥← / ⌥→, mit ⇧ dazu markieren, ⌥⌫ löscht ein Wort.",
                            "⌥↑ / ⌥↓ verschiebt die Zeile, ⇧⌥↓ kopiert sie; danach ⌘Z.",
                            "⌘/ auf einer Zeile (XML-Kommentar an und wieder aus).",
                            "Ein Wort markieren, ⌘D zweimal (weitere Vorkommen dazu), tippen, Esc.",
                            "⌘F: Suchfeld im Editor, Begriff eingeben, ⌘G / ⇧⌘G springen. Über den Pfeil links im Suchfeld "
                            "*Ersetzen* aufklappen und einmal ersetzen. Esc.",
                            "In der Antwort (nur lesbar) ebenfalls ⌘F.",
                        ],
                        "erwartet": [
                            "Springen wortweise, Zeilen verschieben, Kommentar, mehrere Cursor und Suchen funktionieren.",
                            "Die Symbole im Suchfeld (Pfeile, Schließen, Optionen) sind zu sehen, keine leeren Kästchen.",
                        ],
                    },
                    {
                        "id": "B-24", "titel": "Markierter Text als Suchbegriff", "tag": "neu 24.10.",
                        "schritte": [
                            "Im Body ein Wort markieren (z. B. `orderId`), ⇧⌘F.",
                            "Wieder ein Wort markieren, ⌥⌘F.",
                            "Wieder ein Wort markieren, ⌘P.",
                            "Dasselbe mit Text in einem Eingabefeld (z. B. Teil der Adresse markieren) und ohne Markierung.",
                            "Mehrere Zeilen markieren, ⌘P.",
                        ],
                        "erwartet": [
                            "⇧⌘F öffnet *Find in Files* der IDE mit dem Wort; ⌥⌘F setzt es als Baumfilter (markiert, "
                            "direkt überschreibbar); ⌘P öffnet die Schnellsuche mit dem Wort.",
                            "Ohne Markierung oder bei mehreren Zeilen öffnen die Suchen leer.",
                        ],
                    },
                    {
                        "id": "B-25", "titel": "Kontextmenü im Editor", "tag": "neu 24.10.",
                        "schritte": [
                            "Rechtsklick in Body und Antwort (XML und JSON), mit der Maus über die Einträge fahren.",
                            "Mehrmals schnell hintereinander rechtsklicken.",
                        ],
                        "erwartet": [
                            "Keine überflüssigen englischen Einträge wie „Change All Occurrences“, „Command Palette“, "
                            "„Go to Symbol…“. Vorhanden u. a.: Format Document, *XPath kopieren* bzw. *JSONPath kopieren*.",
                            "Der Eintrag unter der Maus ist hervorgehoben.",
                            "Das Menü bleibt offen, bis man etwas wählt oder daneben klickt - es schließt sich nicht sofort wieder.",
                        ],
                    },
                    {
                        "id": "B-22", "titel": "Andere Keymap", "tag": "geändert 24.10.",
                        "schritte": ["*Settings | Keymap* auf „VSCode“ oder „Eclipse“ stellen, B-20 im Schnelldurchlauf; Formatieren dort mit dem Reformat-Kürzel dieser Keymap."],
                        "erwartet": ["Die LatherIt-Kürzel funktionieren unabhängig von der Keymap."],
                    },
                ],
            },
            {
                "titel": "Darstellung",
                "faelle": [
                    {
                        "id": "B-30", "titel": "Hell und dunkel",
                        "schritte": ["IDE-Theme bei offenem LatherIt-Tab zwischen Light, Dark und High Contrast wechseln."],
                        "erwartet": ["LatherIt wechselt sofort mit, ohne Neuladen und ohne helle Blitzer."],
                    },
                    {
                        "id": "B-31", "titel": "Scrollen, Größe, Fenster", "tag": "geändert 24.10.",
                        "schritte": [
                            "Mit Trackpad und Mausrad in Baum, Collections, Request und Response scrollen - auch in "
                            "einer großen Antwort (H-31, über 5000 Zeilen). Dabei mehrmals schnell die Richtung wechseln.",
                            "Am rechten Rand die Scrollbalken-Anzeige ziehen.",
                            "Tab-Leiste mit vielen offenen Tabs waagerecht scrollen (Trackpad, ⇧+Mausrad).",
                            "Tab in ein eigenes Fenster ziehen, zurück; Split; IDE-Fenster stark verkleinern; Vollbild.",
                            "Zwischen zwei Monitoren mit unterschiedlicher Auflösung verschieben.",
                        ],
                        "erwartet": [
                            "Scrollen flüssig; ein Richtungswechsel greift sofort (kein Nachlaufen in die alte Richtung).",
                            "Beim Scrollen erscheint rechts eine schmale Anzeige, die sich ziehen lässt und danach verblasst.",
                            "Layout passt sich an; nichts bleibt schwarz oder unscharf.",
                        ],
                    },
                    {
                        "id": "B-32", "titel": "Fokus und Eingabe",
                        "schritte": [
                            "Zwischen LatherIt-Tab und anderen Tabs mit Maus und ⌃⇥ wechseln, sofort tippen.",
                            "Deutsche Sonderzeichen tippen: `{ } [ ] \\ | @ ~ €` sowie Umlaute und `ß`.",
                        ],
                        "erwartet": ["Die Eingabe landet im erwarteten Feld; alle Zeichen kommen an."],
                    },
                    {
                        "id": "B-33", "titel": "Baum ein- und ausklappen", "tag": "neu 24.10.",
                        "schritte": [
                            "Mehrere LatherIt-Tabs öffnen. Den Baum links über das Leisten-Symbol einklappen und wieder "
                            "ausfahren, fünf- bis zehnmal, auch schnell hintereinander.",
                        ],
                        "erwartet": [
                            "Kein alter Bildstand bleibt stehen (z. B. Tab-Leiste oder Breadcrumb oben im Baum). "
                            "Falls doch: sofort Screenshot, nichts anfassen.",
                        ],
                    },
                    {
                        "id": "B-34", "titel": "Collections beim Überfahren", "tag": "neu 24.10.",
                        "vorher": "Eine Collection mit mindestens einem Eintrag (Request › Rechtsklick › *In Collection aufnehmen …*).",
                        "schritte": ["Mit der Maus langsam über Collection-Kopf und Einträge fahren."],
                        "erwartet": ["Der Löschknopf erscheint, die Zeilen ändern dabei ihre Höhe nicht (nichts springt)."],
                    },
                    {
                        "id": "B-36", "titel": "Collections in der Projektdatei", "tag": "neu 24.10.",
                        "vorher": "Zwei LatherIt-Projektdateien geöffnet (A und B).",
                        "schritte": [
                            "In A eine Collection anlegen und zwei Requests aus A aufnehmen.",
                            "Bei einem Request aus B *In Collection aufnehmen …* wählen.",
                            "Den Text-Tab von A ansehen.",
                        ],
                        "erwartet": [
                            "Für den Request aus B wird nur eine Collection von B angeboten (oder eine neue), nicht die von A.",
                            "In der YAML von A steht ein Abschnitt collections mit Name und den beiden Knoten-IDs.",
                        ],
                    },
                    {
                        "id": "B-37", "titel": "Endung .latherit.yml", "tag": "neu 24.10.",
                        "vorher": "Eine Projektdatei in *.latherit.yml umbenennen.",
                        "schritte": ["Die Datei öffnen.", "Über *New › LatherIt Project* eine neue Datei anlegen."],
                        "erwartet": ["Der LatherIt-Tab erscheint wie bei .yaml.", "Die neue Datei endet auf .latherit.yaml."],
                    },
                    {
                        "id": "B-35", "titel": "Aktions-Symbole und Farben", "tag": "neu 24.10.",
                        "vorher": "Ein Request auf dem Board (⌘B), einer nicht; eine Collection.",
                        "schritte": [
                            "Ohne Maus: Board-Markierung am angehefteten Request ansehen.",
                            "Mit der Maus über beide Requests fahren und auf den Pin klicken (anheften und wieder lösen).",
                            "Über alle Aktions-Symbole fahren: Interface/REST-Service, Ressource, Test-Suite, Test-Case, "
                            "Mock (gestoppt und laufend), Request.",
                            "Eine Test-Suite zweimal hintereinander über das Play-Symbol starten.",
                            "Board- und Collections-Kopf über dem Baum ansehen, ebenso die beiden Symbole in der "
                            "eingeklappten Leiste. Rechtsklick auf einen Knoten (Menü „Neu …“).",
                        ],
                        "erwartet": [
                            "Pin angeheftet senkrecht und blau, nicht angeheftet schräg. Er steht ganz rechts und "
                            "springt beim Überfahren nicht, der Klick trifft nie den Mülleimer.",
                            "Beim Überfahren: Mülleimer und Stopp rot, Play grün, Pin blau, Mock erzeugen orange, "
                            "+, Neue Test-Suite und Duplizieren lila, Collection rosa.",
                            "Beide Läufe zeigen während des Laufs den Spinner (beim Überfahren Stopp).",
                            "Board-Symbol blau, Collections-Symbol rosa - im Kopf über dem Baum und in der Leiste. "
                            "Im Kontextmenü dieselben Farben (z. B. *In Collection aufnehmen* rosa, *Duplizieren* lila).",
                        ],
                    },
                ],
            },
        ],
    },
    # ------------------------------------------------------------------ 13
    {
        "titel": "Plattformen und IDEs",
        "kurz": "Rauchtest plus Besonderheiten je Plattform - nur dieses Kapitel auf jeder Plattform",
        "text": [
            ("p", "Auf jeder weiteren Plattform bzw. IDE: erst Vorbereitung V-01 bis V-04 (Testserver auf dem Mac mit "
                  "`--host 0.0.0.0`), dann der Rauchtest RT-01 bis RT-08, dann nur die Fälle der jeweiligen Plattform."),
            ("tabelle", ["Umgebung", "Rauchtest", "Zusätzlich"], [
                ["macOS + PhpStorm (Hauptplattform)", "über Kapitel 2-12 abgedeckt", "MAC-01 … MAC-06"],
                ["Windows 11 + IntelliJ IDEA oder PhpStorm", "RT-01 … RT-08", "WIN-01 … WIN-09"],
                ["Linux (Ubuntu, X11 und Wayland) + IDE", "RT-01 … RT-08", "LIN-01 … LIN-06"],
                ["IntelliJ IDEA (macOS)", "RT-01 … RT-08", "IDE-01 … IDE-04"],
                ["PyCharm, WebStorm, GoLand", "RT-01 … RT-08", "IDE-03"],
                ["Älteste unterstützte Version (2026.2.0)", "RT-01 … RT-08", "IDE-05"],
                ["Desktop-App (Windows, Linux, macOS)", "DT-01 … DT-08", "-"],
            ]),
            ("p", "Testbuilds der Desktop-App liegen nach `npm run release:cross` unter `src-tauri/target/cross/` "
                  "(Windows: `…-setup.exe`, Linux: `.AppImage`, `.deb`, `.rpm`). Windows 11 ARM und Ubuntu ARM (UTM mit "
                  "Rosetta) führen die x64-Pakete aus."),
        ],
        "unter": [
            {
                "titel": "Rauchtest (überall)",
                "faelle": [
                    {
                        "id": "RT-01", "titel": "Installation und Start",
                        "schritte": ["Plugin-ZIP installieren, IDE neu starten, `shop-api.latherit.yaml` öffnen."],
                        "erwartet": ["LatherIt-Tab vorn, Oberfläche lädt in unter 3 s, kein Fehler im IDE-Log."],
                    },
                    {
                        "id": "RT-02", "titel": "REST senden",
                        "schritte": [f"R-01 und R-10 (gegen den Testserver)."],
                        "erwartet": ["Wie dort beschrieben."],
                    },
                    {
                        "id": "RT-03", "titel": "WSDL und SOAP",
                        "schritte": ["W-01, W-11, W-12."],
                        "erwartet": ["Wie dort beschrieben."],
                    },
                    {
                        "id": "RT-04", "titel": "Speichern und Änderungen von außen",
                        "schritte": ["P-10, P-20, P-22."],
                        "erwartet": ["Wie dort beschrieben, insbesondere kein Verlust in P-22."],
                    },
                    {
                        "id": "RT-05", "titel": "Mock",
                        "schritte": ["M-01 und M-03."],
                        "erwartet": ["Wie dort beschrieben."],
                    },
                    {
                        "id": "RT-06", "titel": "Anmeldung",
                        "schritte": ["A-01, A-20, A-30."],
                        "erwartet": ["Wie dort beschrieben."],
                    },
                    {
                        "id": "RT-07", "titel": "Kürzel und Zwischenablage", "tag": "geändert 24.10.",
                        "schritte": ["B-20 (nur ⌘↵/Strg+Enter, ⌘S/Strg+S, ⌘B/Strg+B, ⇧⌘F/Strg+Shift+F) und B-10, B-23 (nur ⌥←/Strg+←, ⌘F/Strg+F)."],
                        "erwartet": ["Wie dort beschrieben."],
                    },
                    {
                        "id": "RT-08", "titel": "Theme, Scrollen und Skript", "tag": "geändert 24.10.",
                        "schritte": ["B-30, B-31 (nur Scrollen mit Richtungswechsel), P-31 und S-04."],
                        "erwartet": ["Wie dort beschrieben."],
                    },
                ],
            },
            {
                "titel": "Rauchtest Desktop-App", "tag": "neu 25.10.",
                "faelle": [
                    {
                        "id": "DT-01", "titel": "Installieren und Starten", "tag": "neu 25.10.",
                        "schritte": [
                            "Windows: `LatherIt_…_x64-setup.exe` ausführen (Warnung „Unbekannter Herausgeber“ über *Weitere Informationen › Trotzdem ausführen*). "
                            "Linux: `.deb` per `sudo apt install ./LatherIt_…_amd64.deb` bzw. `.rpm` installieren, außerdem das AppImage nach `chmod +x` direkt starten.",
                            "LatherIt über Startmenü bzw. App-Übersicht starten.",
                        ],
                        "erwartet": [
                            "Installation ohne Fehler, Eintrag mit LatherIt-Symbol im Startmenü bzw. in der App-Übersicht.",
                            "Fenster mit Willkommensseite in unter 5 s, Schrift und Symbole scharf (auch bei 150 % Skalierung).",
                        ],
                    },
                    {
                        "id": "DT-02", "titel": "Projekt öffnen und speichern", "tag": "geändert 26.10.",
                        "vorher": "Auf dem Mac: Dateiserver für die Handtest-Dateien läuft (im Ordner `manual-tests/dateien`: `python3 -m http.server 8001`).",
                        "schritte": [
                            ("Projekt in die VM holen - Ubuntu im Terminal (Strg+Alt+T), Windows im Browser (Adresse aufrufen, Datei speichern):",
                             "wget -P ~/Dokumente http://192.168.64.1:8001/projekte/shop-api.latherit.yaml"),
                            "In LatherIt Strg+O, `shop-api.latherit.yaml` wählen.",
                            "Im Baum einen Request anklicken, F2 (oder Doppelklick auf den Namen), neuen Namen eingeben, Enter. Dann Strg+S.",
                            "LatherIt schließen und wieder starten.",
                        ],
                        "erwartet": [
                            "Der Dateidialog ist der des Systems; das Projekt erscheint im Baum.",
                            "Nach dem Neustart ist das Projekt wieder offen und der neue Name gespeichert.",
                        ],
                    },
                    {
                        "id": "DT-03", "titel": "REST und SOAP senden", "tag": "geändert 26.10.",
                        "vorher": "Auf dem Mac läuft der Testserver für das Netz: `python3 testserver.py --host 0.0.0.0`. "
                                  "Der Mac ist aus der VM unter `192.168.64.1` erreichbar (nicht `localhost`).",
                        "schritte": [
                            ("REST: Strg+Umschalt+N, Methode GET, Adresse eingeben, *Anlegen*, dann Strg+Enter:",
                             "http://192.168.64.1:8088/api/orders/4711"),
                            ("WSDL: Strg+I, Adresse eingeben, *Importieren*:", "http://192.168.64.1:8088/shop?wsdl"),
                            ("Im Baum *ShopSoap11 › GetOrder › Request 1* wählen. Endpunkt oben auf diese Adresse setzen:",
                             "http://192.168.64.1:8088/shop"),
                            "Im Body in der Zeile `<shop:orderId>?</shop:orderId>` das `?` durch `4711` ersetzen "
                            "(die übrigen `?` dürfen bleiben), dann Strg+Enter.",
                        ],
                        "erwartet": [
                            "REST: Status 200, JSON mit `\"id\": \"4711\"`; die Statuszeile zeigt Zeit und Größe.",
                            "WSDL: Im Baum erscheinen `ShopSoap11` und `ShopSoap12` mit `GetOrder` und `Ping`.",
                            "SOAP: Status 200, Antwort mit `<shop:id>4711</shop:id>`.",
                        ],
                    },
                    {
                        "id": "DT-04", "titel": "HTTPS mit eigenem Zertifikat", "tag": "geändert 26.10.",
                        "ziel": "Die Desktop-App fragt nicht nach (anders als die IDE), sondern prüft gegen die "
                                "vertrauenswürdigen Zertifikate des Systems - wie ein Browser.",
                        "vorher": "Testserver wie in DT-03.",
                        "schritte": [
                            ("Strg+Umschalt+N, Methode GET, Adresse eingeben, *Anlegen*, Strg+Enter:",
                             "https://192.168.64.1:8443/api/orders/4711"),
                            ("Testzertifikat dem System bekannt machen - Ubuntu im Terminal:",
                             "wget -O /tmp/latherit-test.crt http://192.168.64.1:8001/zertifikat/localhost.pem\n"
                             "sudo cp /tmp/latherit-test.crt /usr/local/share/ca-certificates/ && sudo update-ca-certificates"),
                            ("Windows: Datei im Browser laden (`http://192.168.64.1:8001/zertifikat/localhost.pem`), dann "
                             "Startmenü › „Terminal“ › Rechtsklick › *Als Administrator ausführen*:",
                             "certutil -addstore -f Root $HOME\\Downloads\\localhost.pem"),
                            "LatherIt beenden und neu starten, den Request erneut senden.",
                        ],
                        "erwartet": [
                            "Erster Versuch: verständliche Fehlermeldung (Zertifikat nicht vertrauenswürdig), kein Absturz.",
                            "Nach dem Eintragen und Neustart: Status 200.",
                        ],
                    },
                    {
                        "id": "DT-05", "titel": "Mock starten", "tag": "geändert 26.10.",
                        "vorher": "Projekt aus DT-02 offen.",
                        "schritte": [
                            "Im Baum *Shop Mock* wählen, oben rechts **Starten** (oder Strg+Enter).",
                            ("Im Browser der VM aufrufen:", "http://localhost:8099/shop/orders/4711"),
                            "Im Mock-Protokoll in LatherIt nachsehen, dann **Stoppen**.",
                        ],
                        "erwartet": [
                            "Der Browser zeigt eine Bestellung mit `\"id\": \"4711\"`; das Protokoll zeigt den Aufruf.",
                            "Windows: Die Firewall fragt beim ersten Start - *Abbrechen* reicht (Mock bleibt in der VM erreichbar).",
                            "Nach Stoppen: Neu laden im Browser schlägt fehl (Verbindung abgelehnt).",
                        ],
                    },
                    {
                        "id": "DT-06", "titel": "Kürzel, Zwischenablage, Scrollen", "tag": "neu 25.10.",
                        "schritte": [
                            "Strg+Enter senden, Strg+P *Gehe zu*, Strg+W Tab schließen, Strg+F im Body suchen.",
                            "Body kopieren und in einen Editor der VM einfügen; Text aus der VM in den Body einfügen.",
                            "Lange Antwort mit Mausrad und Touchpad scrollen.",
                        ],
                        "erwartet": ["Alle Kürzel wirken, Umlaute bleiben beim Kopieren erhalten, Scrollen ohne Ruckeln."],
                    },
                    {
                        "id": "DT-07", "titel": "Hell und dunkel", "tag": "geändert 26.10.",
                        "schritte": [
                            "LatherIt offen lassen und im System das Design wechseln (nicht „Nachtmodus“, der färbt nur wärmer). "
                            "Ubuntu: *Einstellungen › Darstellung › Stil: Hell / Dunkel*. "
                            "Windows: *Einstellungen › Personalisierung › Farben › Modus: Hell / Dunkel*. "
                            "Ohne Aktivierung ist das gesperrt, dann in PowerShell (jeder Aufruf schaltet um): "
                            "`irm http://192.168.64.1:8000/vms/windows-design.ps1 | iex`.",
                        ],
                        "erwartet": ["LatherIt wechselt mit, auch Editor und Fensterrahmen; alles bleibt lesbar."],
                    },
                    {
                        "id": "DT-08", "titel": "Beenden und Deinstallieren", "tag": "geändert 26.10.",
                        "schritte": [
                            "Mit ungespeicherter Änderung Alt+F4 bzw. das Schließen-Kreuz drücken.",
                            "Windows: über *Einstellungen › Apps* deinstallieren; Linux: `sudo apt remove lather-it` (Paketname mit Bindestrich).",
                        ],
                        "erwartet": [
                            "Nachfrage zum Speichern erscheint, danach schließt die App vollständig (kein Prozess bleibt im Task-Manager bzw. `ps`).",
                            "Deinstallation ohne Fehler, Startmenü-Eintrag weg; die Projektdateien bleiben erhalten.",
                        ],
                    },
                ],
            },
            {
                "titel": "macOS",
                "faelle": [
                    {
                        "id": "MAC-01", "titel": "Zertifikat aus dem Schlüsselbund",
                        "schritte": [
                            "[[datei:zertifikat/localhost.pem]] doppelklicken, im Schlüsselbund *System* bzw. "
                            "*Anmeldung* auf „Immer vertrauen“ stellen.",
                            "Unter *Settings | Tools | Server Certificates* das in H-40 akzeptierte Zertifikat entfernen.",
                            "GET `https://localhost:8443/api/orders/4711`.",
                        ],
                        "erwartet": ["Antwort **ohne** Rückfrage der IDE (Systemvertrauen greift)."],
                        "hinweis": "Danach das Zertifikat wieder aus dem Schlüsselbund löschen.",
                    },
                    {
                        "id": "MAC-02", "titel": "Umgebungsvariablen beim Start aus dem Dock",
                        "schritte": ["IDE über Dock bzw. Toolbox starten (nicht aus dem Terminal), S-01 senden."],
                        "erwartet": ["`${#Env#LATHERIT_TEST}` ist gesetzt (die IDE lädt die Login-Shell)."],
                    },
                    {
                        "id": "MAC-03", "titel": "Trackpad und Retina",
                        "schritte": ["H-31 (5 MB) mit Trackpad scrollen, schnell und langsam; Pinch-Zoom; auf externem Nicht-Retina-Monitor."],
                        "erwartet": ["Flüssig, scharf, kein Nachlaufen oder Springen."],
                    },
                    {
                        "id": "MAC-04", "titel": "Kürzel mit ⌘ und ⌃",
                        "schritte": ["B-20 und ⌃1 … ⌃9 (Board)."],
                        "erwartet": ["Wie B-20; ⌃1 … ⌃9 öffnen Board-Einträge."],
                    },
                    {
                        "id": "MAC-05", "titel": "Firewall bei öffentlichem Mock",
                        "vorher": "*Systemeinstellungen › Netzwerk › Firewall* eingeschaltet.",
                        "schritte": ["M-08."],
                        "erwartet": ["macOS fragt einmal, ob die IDE eingehende Verbindungen annehmen darf; danach erreichbar."],
                    },
                    {
                        "id": "MAC-06", "titel": "Vollbild und Spaces",
                        "schritte": ["IDE im Vollbild, LatherIt-Tab in eigenes Fenster ziehen, zwischen Spaces wechseln."],
                        "erwartet": ["Inhalt bleibt sichtbar und bedienbar."],
                    },
                ],
            },
            {
                "titel": "Windows",
                "faelle": [
                    {
                        "id": "WIN-01", "titel": "Kürzel mit Strg und AltGr",
                        "schritte": [
                            "B-20 mit Strg statt ⌘ (Strg+Enter, Strg+S, Strg+B, Strg+I, Strg+Shift+N …).",
                            "Mit deutscher Tastatur im Request-Body tippen: AltGr+7 `{`, AltGr+0 `}`, AltGr+8/9 `[ ]`, "
                            "AltGr+ß `\\`, AltGr+Q `@`, AltGr+E `€`, AltGr+< `|`, AltGr++ `~`.",
                        ],
                        "erwartet": [
                            "Alle Zeichen erscheinen; AltGr (= Strg+Alt) löst **keine** Kürzel aus.",
                        ],
                    },
                    {
                        "id": "WIN-02", "titel": "Alt+1 … Alt+9 gegen Tool-Fenster",
                        "schritte": ["Im LatherIt-Tab Alt+1 … Alt+9 drücken."],
                        "erwartet": ["Notieren: öffnet LatherIt den Board-Eintrag oder die IDE ein Tool-Fenster (Alt+1 = Project)? Ins Ticket."],
                    },
                    {
                        "id": "WIN-03", "titel": "Pfade mit Laufwerk, Leerzeichen, Umlauten",
                        "schritte": [
                            "Projekt unter `C:\\Users\\<name>\\Prüfung mit Leer zeichen\\` anlegen (P-03).",
                            "WSDL als Datei importieren (W-02) aus einem anderen Laufwerk oder Netzlaufwerk (`\\\\server\\freigabe`), falls vorhanden.",
                        ],
                        "erwartet": ["Speichern, Laden und Import funktionieren; relative XSDs werden gefunden."],
                    },
                    {
                        "id": "WIN-04", "titel": "Zeilenenden CRLF",
                        "schritte": [
                            ("Mit Git-Einstellung:", "git config core.autocrlf true"),
                            "Projektdatei committen, `git checkout` erneut, Datei öffnen, eine Änderung machen.",
                            "[[datei:projekte/crlf.latherit.yaml]] (mit CRLF) öffnen und ändern.",
                        ],
                        "erwartet": [
                            "Kein Neuladen in Schleife, kein „ganze Datei geändert“ im Diff - nur die geänderte Zeile.",
                        ],
                    },
                    {
                        "id": "WIN-05", "titel": "NTLM gegen echten Windows-Server",
                        "vorher": "Nur falls verfügbar: IIS mit Windows-Authentifizierung oder ein Firmen-Dienst mit NTLM.",
                        "schritte": ["Request mit NTLM, Domäne und Benutzer wie bei der Windows-Anmeldung."],
                        "erwartet": ["200. Mit falschem Passwort 401 ohne Endlosschleife."],
                    },
                    {
                        "id": "WIN-06", "titel": "Zertifikat aus dem Windows-Zertifikatspeicher",
                        "schritte": [
                            "`localhost.pem` nach `.crt` kopieren, doppelklicken › *Zertifikat installieren* › "
                            "*Vertrauenswürdige Stammzertifizierungsstellen*.",
                            "In der IDE das in H-40 akzeptierte Zertifikat entfernen.",
                            "GET `https://<Mac-Adresse>:8443/…` - wegen des Hostnamens besser den Testserver in der VM "
                            "selbst starten und `https://localhost:8443/api/orders/4711` nehmen.",
                        ],
                        "erwartet": ["Antwort ohne Rückfrage der IDE."],
                    },
                    {
                        "id": "WIN-07", "titel": "Windows-Firewall bei öffentlichem Mock",
                        "schritte": ["M-08."],
                        "erwartet": ["Windows fragt nach Freigabe (privat/öffentlich); danach erreichbar, ohne Freigabe nur lokal."],
                    },
                    {
                        "id": "WIN-08", "titel": "Skalierung 125 % / 150 %",
                        "schritte": ["Anzeigeskalierung umstellen, IDE neu starten, B-31."],
                        "erwartet": ["Schrift scharf, Mausklicks treffen (keine Verschiebung zwischen Anzeige und Klickposition)."],
                    },
                    {
                        "id": "WIN-09", "titel": "System-Proxy und Umgebungsvariablen",
                        "schritte": [
                            "In der IDE *Auto-detect proxy settings* mit einem in Windows eingetragenen Proxy (127.0.0.1:8089), H-50.",
                            "Variable `LATHERIT_TEST` in den Windows-Einstellungen setzen, IDE neu starten, S-01.",
                        ],
                        "erwartet": ["Proxy wird übernommen; Variable ist sichtbar."],
                    },
                ],
            },
            {
                "titel": "Linux",
                "faelle": [
                    {
                        "id": "LIN-01", "titel": "JCEF vorhanden",
                        "schritte": ["Rauchtest RT-01; zusätzlich *Help › Find Action › Registry* `ide.browser.jcef.enabled` prüfen."],
                        "erwartet": [
                            "Oberfläche lädt. Ohne JCEF (z. B. alte JBR, Remote-Entwicklung) erscheint der Hinweis "
                            "„LatherIt braucht den eingebetteten Browser (JCEF) …“ statt eines leeren Tabs.",
                        ],
                    },
                    {
                        "id": "LIN-02", "titel": "X11 und Wayland",
                        "schritte": ["RT-07 und B-31 einmal in einer X11- und einmal in einer Wayland-Sitzung, mit 200 % Skalierung."],
                        "erwartet": ["Scharf, Klicks treffen, Scrollen flüssig, Tastatureingabe kommt an."],
                    },
                    {
                        "id": "LIN-03", "titel": "Keine System-Zertifikate",
                        "schritte": ["H-40 und H-41."],
                        "erwartet": [
                            "Selbst signiert: Rückfrage der IDE wie auf dem Mac. Öffentliche Seiten ohne Rückfrage "
                            "(Java-eigene Liste). Unter Linux gibt es bewusst keine Systemwurzeln.",
                        ],
                    },
                    {
                        "id": "LIN-04", "titel": "Umgebungsvariablen je nach Start",
                        "schritte": ["IDE einmal über das Startmenü/Toolbox und einmal aus dem Terminal starten, jeweils S-01."],
                        "erwartet": ["In beiden Fällen ist `LATHERIT_TEST` gesetzt (aus `~/.profile`); Unterschiede ins Ticket."],
                    },
                    {
                        "id": "LIN-05", "titel": "Zwischenablage und Auswahl",
                        "schritte": ["B-10; zusätzlich markieren und mit mittlerer Maustaste in eine IDE-Datei einfügen."],
                        "erwartet": ["Strg+C/V funktioniert; mittlere Maustaste ist nett, aber kein Muss - Ergebnis notieren."],
                    },
                    {
                        "id": "LIN-06", "titel": "Kürzel und Dateidialoge",
                        "schritte": ["B-20 mit Strg; WIN-02 sinngemäß (Alt+1 … 9); B-01."],
                        "erwartet": ["Wie dort; Dateidialoge sind die der IDE und zeigen Umlaute korrekt."],
                    },
                ],
            },
            {
                "titel": "Andere IDEs und Versionen",
                "faelle": [
                    {
                        "id": "IDE-01", "titel": "IntelliJ IDEA: Kürzel der Standard-Keymap",
                        "schritte": ["B-20 in IntelliJ IDEA (Ultimate und Community)."],
                        "erwartet": ["Wie B-20 - besonders ⌘B, ⌘1 und ⇧⌘F, die in IDEA häufig genutzt werden."],
                    },
                    {
                        "id": "IDE-02", "titel": "IntelliJ IDEA Ultimate: YAML-, Kubernetes- und HTTP-Client-Plugins",
                        "schritte": [
                            "Mit aktivem YAML-, Kubernetes- und OpenAPI-Plugin `shop-api.latherit.yaml` öffnen.",
                            "Text-Tab ansehen; P-10 und P-20.",
                        ],
                        "erwartet": [
                            "LatherIt-Tab bleibt vorn; keine Fremd-Inspektionen, die die Datei beim Speichern umformatieren.",
                        ],
                    },
                    {
                        "id": "IDE-03", "titel": "Menüpunkte in anderen IDEs",
                        "schritte": ["*File › New* bzw. Rechtsklick im Projektbaum › *New*."],
                        "erwartet": ["„LatherIt Project“ und „LatherIt Project from SoapUI…“ sind da und funktionieren (P-01, I-01)."],
                    },
                    {
                        "id": "IDE-04", "titel": "Sprache der IDE",
                        "schritte": ["Deutsches Sprachpaket aktivieren, IDE neu starten, Menüpunkte und Dialoge aus P-01/P-02 ansehen."],
                        "erwartet": ["Deutsche Texte („LatherIt-Projekt“, „Bitte einen Namen eingeben.“); sonst englisch."],
                    },
                    {
                        "id": "IDE-05", "titel": "Älteste und neueste Version",
                        "schritte": [
                            "Rauchtest in der ältesten unterstützten Version (2026.2.0) und in der aktuellen EAP.",
                            ("Vor dem Release zusätzlich:", "./gradlew verifyPlugin"),
                        ],
                        "erwartet": ["Läuft in beiden; `verifyPlugin` ohne Probleme und ohne Deprecations."],
                    },
                ],
            },
        ],
    },
]
