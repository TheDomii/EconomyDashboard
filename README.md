# EconomyDashboard

Spigot/Paper-Plugin mit eingebettetem Webserver, das eine Live-Übersicht über die
Server-Wirtschaft liefert. End-to-end auf einem lokalen Testserver verifiziert
(Paper 1.21.11 + Vault + TheNewEconomy + Citizens + dtlTradersPlus + Towny +
QuickShop-Hikari + AdvancedRegionMarket) sowie gegen echte Produktionsdaten
(316k Händler-Transaktionen, 646 QuickShops, 146 Towny-Städte): **Geldmenge &
Verteilung**, **Händler-Übersicht über dtlTradersPlus** inkl. Live-Preisliste,
**Towny- & Nationen-Anbindung**, **QuickShop-Anbindung**,
**AdvancedRegionMarket-Anbindung**, **Suche, Filter & Sortierung pro Tabelle**,
**CSV-Export**, **spielerübergreifendes Profil**, **automatische
Anomalie-Erkennung** (mit Archiv), **Live-Aktivitäts-Feed**,
**Spieler-Onlinezeit-Tracking**, **Geldfluss-Visualisierung**, **optionaler
Discord-Alarm**, **mobilfreundlich**, **Login-Schutz mit Brute-Force-Sperre**,
**`/ecodash`-Admin-Befehl** und **Modul-Toggles**.

## Was es aktuell kann

- Liest über Vault alle Spielerkonten aus (funktioniert automatisch mit
  TheNewEconomy, da TNE sich bei Vault registriert).
- Berechnet: Gesamtgeldmenge im Umlauf, Spieleranzahl, Durchschnittsguthaben,
  Verteilung nach Kontostand-Bereichen, Top-10-Rangliste.
- Liest dtlTradersPlus' Log-Dateien (`plugins/dtlTradersPlus/shops/<Shop>/logs/*.log`)
  und zeigt Transaktionen, Einnahmen/Auszahlungen/Netto, Top-Shops, meistgehandelte
  Items, plus eine Live-Preisliste direkt aus den Shop-Configs (nicht aus dem Log).
- Towny: Anzahl Städte, Gesamtvermögen aller Stadtkassen, neue Städte (24h/7 Tage,
  aus `Town.getRegistered()`), neue Plots (24h/7 Tage, aus `TownBlock.getClaimedAt()`),
  Top-Städte nach Kassenstand. Zusätzlich eine **Nationen-Übersicht** (Name, Hauptstadt,
  Anzahl Städte, Einwohner, Gesamtkasse aller Mitgliedsstädte) - beim Durchlaufen der
  Städte-Liste mitaggregiert, kein zusätzlicher `TownyAPI`-Aufruf nötig.
- QuickShop-Hikari: liest QuickShops eigene Datenbank direkt über
  `DatabaseHelper#listShops()` (SQL-Join über QuickShops eigene Tabellen, Item-Daten
  über `QuickShop.getInstance().platform().decodeStack()`) - **nicht** über die
  Live-Shop-Registry (`ShopManager#getAllShops()`), die nur Shops in aktuell
  geladenen Chunks zeigt und auf einer nicht erkundeten Welt fast leer bleibt (und
  frühere Versuche, sie zu nutzen, konnten den Server durch erzwungenes
  Chunk-Laden/-Generieren zum Stocken bringen). Plus Transaktionshistorie über
  `ShopSuccessPurchaseEvent`.
- AdvancedRegionMarket: spiegelt die Region-Liste live (`RegionManager` ist
  `Iterable<Region>`, plus `Region#getSubregions()`). Verkäufe werden erkannt, indem
  der Sold-Status jeder Region bei jeder Aktualisierung mit dem vorherigen Stand
  verglichen wird (ARM hat kein zuverlässiges Post-Kauf-Event - `PreBuyEvent`
  feuert vor der eigentlichen Geld-/Besitzübertragung und kann abgebrochen werden,
  ist also kein Erfolgssignal). Zeigt Regionen insgesamt, verkauft/verfügbar,
  erkannte Verkäufe, Umsatz, Top-Käufer nach Ausgaben.
- **XSS-Härtung**: Spieler-, Item-, Shop-, Stadt- und Nationsnamen kommen letztlich
  von Minecraft-Spielern (dtlTradersPlus-Logs, QuickShop-Item-/Shop-Namen,
  Towny-Stadt-/Nationsnamen, amboss-umbenannte Items, ...) und sind damit nicht
  vertrauenswürdig. Alle ~70 Stellen im Frontend, die solche Werte per `innerHTML`
  in eine Tabellenzeile o.ä. einsetzen, laufen jetzt durch einen gemeinsamen
  `escapeHtml()`-Helfer in `app.js` (auch `playerLink()` und der wiederverwendbare
  `renderTrendChart()` escapen jetzt intern) - ein Spieler, der z.B. ein Item im
  Amboss `<img src=x onerror=...>` nennt, kann dadurch kein JavaScript im
  Admin-Dashboard mehr ausführen. Nur eigene, server-seitig erzeugte Strings
  (Severity-/Kategorie-Label, formatierte Zahlen, Datumswerte) bleiben unescaped,
  weil sie nicht von Spielern beeinflussbar sind.
- **Mobilfreundlich**: alle Seiten haben jetzt ein Viewport-Meta-Tag (fehlte
  vorher komplett - Handys rendern die Seite dadurch bisher in Desktop-Breite
  und zoomen verkleinert, kaum lesbar). Breite Tabellen scrollen innerhalb ihres
  Panels statt die ganze Seite seitlich verschiebbar zu machen (`overflow-x: auto`
  auf `.panel`, keine Änderung an den einzelnen Seiten nötig).
- Jedes Modul hat seine eigene Unterseite (`/economy.html`, `/traders.html`,
  `/towny.html`, `/quickshops.html`, `/regionmarket.html`) statt einer einzigen
  langen Seite; `/` ist eine Übersicht mit Links zu allen Modulen. Große Tabellen
  haben einen "Anzeigen"-Regler (50/100/250/Alle) statt alles auf einmal zu laden.
  Zahlen werden überall mit Tausendertrennzeichen dargestellt (`de-DE`-Format,
  z.B. `1.234.567`).
- **Suche & Filter direkt an jeder Tabelle** statt einer einzigen globalen
  Suchleiste: Ergebnisse werden sofort live gefiltert angezeigt (Registry-Daten
  wie Spieler/Shops/Preise/Städte/Regionen client-seitig aus dem bereits
  geladenen Datensatz, Transaktionshistorien server-seitig über einen eigenen
  JSON-Endpoint mit Live-Vorschau von max. 250 Zeilen), mit optionalem
  CSV-Export der exakt gleichen Filterkriterien.
- **Klickbare Tabellenspalten** (Excel-Stil): jede Tabellenüberschrift mit
  Sortier-Pfeil lässt sich anklicken, um die Tabelle nach dieser Spalte zu
  sortieren - erster Klick aufsteigend, zweiter Klick absteigend. Funktioniert
  zusammen mit Suche/Filter und der Seitengröße (die Sortierung bleibt beim
  Ändern des Filters erhalten) sowie bei server-seitig geladenen
  Transaktionshistorien (sortiert die aktuell geladene Vorschau, kein
  Nachladen nötig).
- **Spielerübergreifendes Profil** (`/player.html?name=...`, verlinkt von jedem
  Spielernamen in jeder Tabelle): fasst Guthaben, Towny-Zugehörigkeit
  (Stadt/Nation/Beitrittsdatum/eigene Plots, live über `TownyAPI#getResident()`),
  dtlTradersPlus- und QuickShop-Transaktionen (Zusammenfassung + letzte 20),
  eigene QuickShops und eigene AdvancedRegionMarket-Regionen für einen Spieler
  an einem Ort zusammen - vorher nur getrennt pro Modul einsehbar.
- **Optionaler Discord-Alarm** für HOCH-Anomalien (`config.yml` → `webhook.discord-url`,
  standardmäßig leer/deaktiviert): postet eine Nachricht in einen Discord-Channel,
  sobald eine neue HOCH-Auffälligkeit erkannt wird - inklusive Link zurück ins
  Dashboard (`web-server.public-url`). Merkt sich bereits gemeldete Auffälligkeiten
  nur im Arbeitsspeicher (kein Persistieren nötig, ein Neustart meldet nicht direkt
  alle bereits bekannten Auffälligkeiten erneut, sondern "seeded" sie beim ersten
  Durchlauf still). Fehlschläge (Discord down, falsche URL) werden nur geloggt,
  brechen die Anomalie-Erkennung selbst nicht ab.
- **Automatische Anomalie-Erkennung** ("Handlungsbedarf" auf der Übersichtsseite,
  siehe [Anomalie-Erkennung](#anomalie-erkennung) unten): findet statistische
  Ausreißer in den eigenen Serverdaten, keine festen Schwellenwerte. Standardmäßig
  eingeklappt (nur die dringendste Auffälligkeit sichtbar, "Alle N anzeigen" klappt
  auf). Jede Auffälligkeit lässt sich per "✓ Geprüft" als geprüft markieren - sie
  verschwindet dann aus dem Handlungsbedarf und landet auf der neuen **Archiv-Seite**
  (`/archiv.html`, sortierbare Tabelle mit Filter). "Wiederherstellen" entfernt einen
  Archiv-Eintrag wieder; besteht die zugrunde liegende Auffälligkeit noch, taucht sie
  beim nächsten Erkennungslauf erneut auf. Identifiziert wird eine Auffälligkeit über
  Kategorie+Titel (keine numerische ID, da Anomalien bei jedem Lauf neu berechnet
  werden statt persistente Objekte zu sein).
- **Reload-Button** oben in jeder Seite (`↻ Aktualisieren`): fragt sofort aktuelle
  Daten ab statt auf das nächste automatische Intervall (15-30s) zu warten.
- **Live-Aktivitäts-Feed** auf der Übersichtsseite: neueste Transaktionen über
  dtlTradersPlus, QuickShop, AdvancedRegionMarket und Towny hinweg, als eine
  gemeinsame, nach Zeit sortierte Liste ("was ist gerade neu dazugekommen") -
  jedes Modul liefert seine letzten N Zeilen als lesbaren Satz, die App mischt
  und sortiert. Towny hat (anders als die anderen drei) keine eigene
  Transaktionstabelle zum Anzapfen - stattdessen hört `TownyActivityService` auf
  Townys eigene Bukkit-Events (`NewTownEvent`, `DeleteTownEvent`,
  `TownAddResidentEvent`, `TownRemoveResidentEvent`, `NewResidentEvent`,
  `NewNationEvent`) und hält die letzten 200 in einem simplen In-Memory-Ringpuffer
  (kein SQLite nötig, "Live"-Feed muss keinen Neustart überstehen).
- **Spieler-Seite** (`/players.html`): trackt Server-Population fortlaufend über
  einen `PlayerJoinEvent`/`PlayerQuitEvent`-Listener (eindeutige Spieler pro Tag)
  und periodische Online-Anzahl-Samples (Rekord gleichzeitig online +
  Durchschnitt je Uhrzeit, "wann ist am meisten los"). Eigene SQLite-DB
  (`presence.db`), Aufzeichnung startet mit Plugin-Aktivierung - keine
  rückwirkenden Daten möglich, da vorher nirgends erfasst. Beide Trends
  (Tage, Uhrzeit) werden als echtes Trend-Diagramm dargestellt (Linie + Fläche
  + gestrichelter Durchschnitt + markierter Höchstwert), bewusst kein
  Balken-pro-Zeile-Layout - dazu eine Statistik-Zeile mit Ø-Wert, Trend
  (2. Hälfte des Zeitraums vs. 1. Hälfte, in %) sowie bestem/ruhigstem
  Tag bzw. Stoßzeit/ruhigster Uhrzeit.
- **Geldmenge über Zeit** (`/economy.html`): gleiche Trend-Diagramm-Idee wie
  bei der Spieler-Seite, diesmal für die Gesamtgeldmenge im Umlauf -
  Tagesdurchschnitt der letzten 30 Tage, mit Ø, Trend und Höchststand.
  Sampelt bei jedem Wirtschafts-Refresh in eine eigene SQLite-DB
  (`economy-history.db`), ebenfalls ohne rückwirkende Daten.
- **Geldfluss-Visualisierung** ("Wohin fließt das Geld?" auf der Übersichtsseite):
  vergleicht die Geld-Pools aller Module (Spielerguthaben, Stadtkassen,
  Händler-/QuickShop-/AdvancedRegionMarket-Umsatz) sowie die einzelnen Shops/
  Spieler mit der größten Geldbewegung, als horizontale Ranglisten. Komplett
  client-seitig aus den ohnehin schon geladenen Overview-Daten berechnet, kein
  neuer Backend-Endpunkt nötig.
- **CSV-Export mit Filtern** für jeden Datenbereich (Spieler, Preisliste, Städte,
  QuickShop-Registry, AdvancedRegionMarket-Registry) sowie separat für die
  vollständige, ungefilterte Transaktionshistorie von dtlTradersPlus, QuickShop
  und AdvancedRegionMarket (Zeitraum, Typ, Spieler, Shop/Besitzer/Region, Item,
  Preisspanne) - gedacht zum Weiterverarbeiten in Excel oder durch eine externe
  KI/Auswertung. Siehe [CSV-Export](#csv-export--filter) unten.
- Login-Maske mit konfigurierbarem Benutzer/Passwort und Hintergrundbild (URL oder
  lokale Datei), Session-Cookies, Logging von erfolgreichen/fehlgeschlagenen
  Login-Versuchen in der Server-Konsole. **Brute-Force-Schutz**: nach 5
  Fehlversuchen von derselben IP innerhalb von 5 Minuten wird diese IP für
  10 Minuten gesperrt (auch mit korrektem Passwort) - einfacher In-Memory-Zähler
  (`LoginRateLimiter`), kein Ersatz für ein verteiltes Rate-Limiting, aber macht
  simples Passwort-Raten teurer.
- Beim Start schreibt das Plugin einen Status-Block in die Konsole (welche Module
  aktiv sind, welche Fremd-Plugins gefunden wurden) und pro Modul eine einzelne
  Zeile, sobald die erste echte Datenerfassung durchgelaufen ist (z.B. "[traders]
  Erste Handelsdaten erfasst: 6 Transaktionen...") - siehe
  [Start-Logging](#start-logging) unten.
- Jede Datenquelle einzeln über `config.yml` → `modules` abschaltbar (z.B. nur Towny).
- **`/ecodash status`** zeigt den Start-Status-Block direkt im Spiel/über die Konsole,
  **`/ecodash reload`** lädt `config.yml` neu und startet den Webserver mit den neuen
  Bind-Adresse/Port/Login-Einstellungen neu, ohne den ganzen Server neu starten zu
  müssen. Modul-Umschalter und das Aktualisierungsintervall werden von den bereits
  laufenden Collector-Tasks nur einmal beim Start gelesen und brauchen weiterhin einen
  vollen Neustart. Berechtigung: `economydashboard.admin` (Standard: op).
- Speichert Händler- und QuickShop-Daten dauerhaft in SQLite
  (`plugins/EconomyDashboard/data.db`, `quickshop.db`) – übersteht Neustarts.
- Stellt das über einen eingebetteten Webserver bereit (kein Java-Webserver
  wie Tomcat nötig, läuft direkt im Plugin) unter `http://<server-ip>:8080/`.

## CSV-Export & Filter

Jeder Datenbereich im Dashboard hat unten ein kleines Filterfeld mit einem
"CSV herunterladen"-Button. Die gleichen Endpunkte lassen sich auch direkt (z.B.
per `curl` oder von einem externen Auswertungs-Tool) mit Query-Parametern aufrufen -
sie brauchen dieselbe Login-Session wie das Dashboard.

| Endpoint | Filter-Parameter | Inhalt |
|---|---|---|
| `/api/economy/export.csv` | `name`, `minBalance`, `maxBalance` | Alle Spieler mit Konto |
| `/api/traders/prices/export.csv` | `shop`, `item` | Aktuelle Preisliste (dtlTradersPlus) |
| `/api/traders/transactions/export.csv` | `from`, `to`, `type`, `player`, `shop`, `item`, `minPrice`, `maxPrice`, `limit` | Einzelne dtlTradersPlus-Transaktionen (nicht aggregiert) |
| `/api/towny/export.csv` | `name`, `nation`, `minBalance` | Alle Städte |
| `/api/towny/nations/export.csv` | `name` | Alle Nationen |
| `/api/quickshops/export.csv` | `owner`, `item` | Aktuelle QuickShop-Registry |
| `/api/quickshops/transactions/export.csv` | `from`, `to`, `type`, `player`, `owner`, `item`, `minPrice`, `maxPrice`, `limit` | Einzelne QuickShop-Transaktionen (nicht aggregiert) |
| `/api/regionmarket/export.csv` | `owner`, `world`, `sold` (`true`/`false`) | Aktuelle AdvancedRegionMarket-Registry |
| `/api/regionmarket/transactions/export.csv` | `from`, `to`, `type` (`SELL`/`UNSELL`), `player`, `region`, `minPrice`, `maxPrice`, `limit` | Einzelne erkannte Region-Verkäufe/Rücknahmen (nicht aggregiert) |
| `/api/players/activity/export.csv` | keine | Eindeutige Spieler pro Tag (letzte 30 Tage) |
| `/api/economy/history/export.csv` | keine | Geldmengen-Tagesdurchschnitt (letzte 30 Tage) |
| `/api/anomalies/archive/export.csv` | keine | Als geprüft markierte Auffälligkeiten |

Ein Klick auf einen Nationsnamen in der Nationen-Tabelle springt direkt zur
Städte-Tabelle und filtert automatisch danach (nutzt denselben `nation`-Filter).

`from`/`to` akzeptieren entweder `yyyy-MM-dd` oder Millisekunden seit Epoch.
`limit` begrenzt die Zeilenzahl (Standard 5000, Maximum 50000). Alle Textfilter
sind Teilstring-Suchen, nicht case-sensitiv. Die Dateien sind UTF-8 mit BOM (damit
Excel Umlaute korrekt anzeigt) und haben einen `Content-Disposition: attachment`-
Header, laden also direkt als Datei herunter statt im Browser angezeigt zu werden.

## Start-Logging

Am Ende von `onEnable()` schreibt das Plugin einen Status-Block:

```
============================================================
 EconomyDashboard v0.1.0 - Status
 economy-provider : aktiv (TheNewEconomy via Vault)
 economy           : aktiv (Geldmengen-Verlauf wird ab jetzt mitgeschrieben)
 traders (dtlTradersPlus) : aktiv
 towny             : aktiv (Towny gefunden)
 quickshop         : aktiv (QuickShop-Hikari gefunden)
 regionmarket      : aktiv (AdvancedRegionMarket gefunden)
 player-activity   : aktiv (Aufzeichnung startet jetzt, keine rueckwirkenden Daten)
 anomaly-detection : aktiv (alle 300s)
 web-dashboard     : http://0.0.0.0:8080/ (Login erforderlich)
============================================================
```

(`anomaly-detection` hängt zusätzlich `, Discord-Alarm aktiv` an, sobald
`webhook.discord-url` gesetzt ist. Dieser Block ist auch jederzeit per
`/ecodash status` abrufbar, siehe oben.)

Zusätzlich schreibt jedes aktive Modul genau einmal (beim ersten erfolgreichen
Durchlauf seines Erfassungs-Tasks) eine Zeile mit den tatsächlich gefundenen Daten,
z.B. `[traders] Erste Handelsdaten erfasst: 6 Transaktionen in 2 Shops (Top-Liste).`
- damit man ohne das Dashboard zu öffnen in der Konsole sieht, ob die Anbindung
wirklich Daten liefert.

## Anomalie-Erkennung

Auf der Übersichtsseite (`/`) läuft ein "Handlungsbedarf"-Panel, das automatisch
gefundene Auffälligkeiten anzeigt - jede mit Schweregrad (Hoch/Mittel), Erklärung
und, wo sinnvoll, einem direkten Link (meist zum [Spielerprofil](#was-es-aktuell-kann)).
Läuft alle `refresh-interval-seconds * 5` (mindestens alle 5 Minuten) als eigener
Hintergrund-Task, da die Erkennung die komplette Transaktionshistorie per SQL
`GROUP BY` durchsucht - deutlich teurer als die anderen Collector-Tasks.

Bewusst **keine festen Schwellenwerte** ("mehr als 1000 verkauft" o.ä.) - die
würden für einen kleinen Server viel zu viel und für einen großen Server viel zu
wenig anschlagen. Stattdessen wird alles gegen die eigenen Serverdaten verglichen:

- **Spieler verkauft überproportional viel von einem Item**: für jedes Item wird
  berechnet, wie viel Prozent der gesamten verkauften Menge auf einen einzelnen
  Spieler entfallen (bei dtlTradersPlus und QuickShop separat). Ab 60% Anteil
  (und einer Mindestmenge, damit nicht jedes selten gehandelte Item anschlägt)
  wird es als "Mittel" markiert, ab 80% als "Hoch".
- **Item wird insgesamt ungewöhnlich stark verkauft**: für jedes Item wird die
  Gesamtverkaufsmenge über alle Spieler berechnet, dann Mittelwert und
  Standardabweichung über alle Items gebildet. Ein Item, dessen Menge mehr als
  2,5 Standardabweichungen über dem Mittelwert liegt (Z-Score), wird markiert -
  ab einem Z-Score von 4 als "Hoch".
- **Stadt besitzt ungewöhnlich viele Plots**: gleiche Z-Score-Methode über die
  Plot-Anzahl aller Städte (braucht mindestens 4 Städte, um überhaupt sinnvoll
  zu sein - mit weniger Städten gibt es keine verlässliche Standardabweichung).
  Ab einem Z-Score von 2,0 "Mittel", ab 3,0 "Hoch" - niedrigere Schwelle als bei
  Items, weil es pro Server üblicherweise deutlich weniger Städte als Items gibt
  und die Verteilung dadurch weniger robust ist.

Die Rohdaten dafür (`sellVolumeByItemAndPlayer()` in `TraderDatabase` und
`QuickShopDatabase`) stehen auch für eigene Auswertungen offen, falls jemand
andere Schwellenwerte oder zusätzliche Regeln bauen möchte.

## Bauen

Voraussetzung: JDK 17+ und Maven.

```
mvn package
```

Ergebnis liegt danach unter `target/economydashboard-0.1.0.jar`.

## Installieren

1. Jar in den `plugins`-Ordner eures Servers kopieren.
2. Server neu starten (Vault + TheNewEconomy müssen laufen; dtlTradersPlus, Towny,
   QuickShop-Hikari und AdvancedRegionMarket (+ dessen eigene Abhängigkeiten
   WorldGuard/WorldEdit) sind optional - ohne sie bleiben die jeweiligen
   Dashboard-Bereiche einfach leer, gesteuert über `config.yml` → `modules`). Der
   SQLite-Treiber wird beim ersten Start automatisch von Maven Central nachgeladen
   (Spigot/Paper Library-Loader, `plugin.yml` → `libraries:`) – der Server braucht
   dafür einmalig Internetzugang.
3. `plugins/EconomyDashboard/config.yml` prüfen: Port, Login-Zugangsdaten
   (**Standardpasswort unbedingt ändern**, sonst Warnung in der Konsole bei jedem
   Start), ggf. Module abschalten.
4. Dashboard im Browser öffnen: `http://<server-ip>:8080/`. In der Konsole prüfen,
   ob der Status-Block beim Start alle erwarteten Module als "aktiv" zeigt.

## Konfiguration (config.yml)

```yaml
web-server:
  bind-address: "0.0.0.0"
  port: 8080
  public-url: ""   # z.B. "http://192.168.1.10:8080" - nur für Links in Discord-Alarmen
webhook:
  discord-url: ""  # leer = deaktiviert
refresh-interval-seconds: 60
modules:
  economy: true
  traders: true
  towny: true
  quickshop: true
  regionmarket: true
  player-activity: true
debug: false
login:
  enabled: true
  username: "admin"
  password: "changeme"
  background-image: ""
  session-minutes: 720
```

`bind-address: 0.0.0.0` macht das Dashboard aus dem Netzwerk erreichbar. Der
eingebettete Webserver (`com.sun.net.httpserver`) spricht nur **plain HTTP, kein
HTTPS** - der Login schützt vor Mitlesen im Klartext-Netzwerkverkehr nicht.
Für Zugriff von außerhalb des lokalen Netzwerks: VPN/SSH-Tunnel oder einen
Reverse-Proxy mit TLS davorsetzen, nicht direkt ins Internet exponieren.

## Lizenz

[MIT](LICENSE)
