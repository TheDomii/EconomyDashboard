# EconomyDashboard

Spigot/Paper-Plugin mit eingebettetem Webserver, das eine Live-Übersicht über die
Server-Wirtschaft liefert. End-to-end auf einem lokalen Testserver verifiziert
(Paper 1.21.11 + Vault + TheNewEconomy + Citizens + dtlTradersPlus + Towny +
QuickShop-Hikari + AdvancedRegionMarket): **Geldmenge & Verteilung**,
**Händler-Übersicht über dtlTradersPlus** inkl. Live-Preisliste,
**Towny-Anbindung**, **QuickShop-Anbindung**, **AdvancedRegionMarket-Anbindung**,
**Suche**, **CSV-Export mit Filtern**, **Login-Schutz** und **Modul-Toggles**.

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
  Top-Städte nach Kassenstand.
- QuickShop-Hikari: liest QuickShops eigene Shop-Registry live über
  `ShopManager#getAllShops()` (eine echte Datenbank aller Shops, kein
  Event-Zusammenbau nötig), plus Transaktionshistorie über `ShopSuccessPurchaseEvent`.
- AdvancedRegionMarket: spiegelt die Region-Liste live (`RegionManager` ist
  `Iterable<Region>`, plus `Region#getSubregions()`). Verkäufe werden erkannt, indem
  der Sold-Status jeder Region bei jeder Aktualisierung mit dem vorherigen Stand
  verglichen wird (ARM hat kein zuverlässiges Post-Kauf-Event - `PreBuyEvent`
  feuert vor der eigentlichen Geld-/Besitzübertragung und kann abgebrochen werden,
  ist also kein Erfolgssignal). Zeigt Regionen insgesamt, verkauft/verfügbar,
  erkannte Verkäufe, Umsatz, Top-Käufer nach Ausgaben.
- Live-Suche über Spieler, Shops (dtlTradersPlus), Items, QuickShops und
  AdvancedRegionMarket-Regionen (min. 2 Zeichen).
- Jedes Modul hat seine eigene Unterseite (`/economy.html`, `/traders.html`,
  `/towny.html`, `/quickshops.html`, `/regionmarket.html`) statt einer einzigen
  langen Seite; `/` ist eine Übersicht mit Links zu allen Modulen. Große Tabellen
  haben einen "Anzeigen"-Regler (50/100/250/Alle) statt alles auf einmal zu laden.
  Zahlen werden überall mit Tausendertrennzeichen dargestellt (`de-DE`-Format,
  z.B. `1.234.567`).
- **CSV-Export mit Filtern** für jeden Datenbereich (Spieler, Preisliste, Städte,
  QuickShop-Registry, AdvancedRegionMarket-Registry) sowie separat für die
  vollständige, ungefilterte Transaktionshistorie von dtlTradersPlus, QuickShop
  und AdvancedRegionMarket (Zeitraum, Typ, Spieler, Shop/Besitzer/Region, Item,
  Preisspanne) - gedacht zum Weiterverarbeiten in Excel oder durch eine externe
  KI/Auswertung. Siehe [CSV-Export](#csv-export--filter) unten.
- Login-Maske mit konfigurierbarem Benutzer/Passwort und Hintergrundbild (URL oder
  lokale Datei), Session-Cookies, Logging von erfolgreichen/fehlgeschlagenen
  Login-Versuchen in der Server-Konsole.
- Beim Start schreibt das Plugin einen Status-Block in die Konsole (welche Module
  aktiv sind, welche Fremd-Plugins gefunden wurden) und pro Modul eine einzelne
  Zeile, sobald die erste echte Datenerfassung durchgelaufen ist (z.B. "[traders]
  Erste Handelsdaten erfasst: 6 Transaktionen...") - siehe
  [Start-Logging](#start-logging) unten.
- Jede Datenquelle einzeln über `config.yml` → `modules` abschaltbar (z.B. nur Towny).
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
| `/api/towny/export.csv` | `name`, `minBalance` | Alle Städte |
| `/api/quickshops/export.csv` | `owner`, `item` | Aktuelle QuickShop-Registry |
| `/api/quickshops/transactions/export.csv` | `from`, `to`, `type`, `player`, `owner`, `item`, `minPrice`, `maxPrice`, `limit` | Einzelne QuickShop-Transaktionen (nicht aggregiert) |
| `/api/regionmarket/export.csv` | `owner`, `world`, `sold` (`true`/`false`) | Aktuelle AdvancedRegionMarket-Registry |
| `/api/regionmarket/transactions/export.csv` | `from`, `to`, `type` (`SELL`/`UNSELL`), `player`, `region`, `minPrice`, `maxPrice`, `limit` | Einzelne erkannte Region-Verkäufe/Rücknahmen (nicht aggregiert) |

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
 economy           : aktiv
 traders (dtlTradersPlus) : aktiv
 towny             : aktiv (Towny gefunden)
 quickshop         : aktiv (QuickShop-Hikari gefunden)
 regionmarket      : aktiv (AdvancedRegionMarket gefunden)
 web-dashboard     : http://0.0.0.0:8080/ (Login erforderlich)
============================================================
```

Zusätzlich schreibt jedes aktive Modul genau einmal (beim ersten erfolgreichen
Durchlauf seines Erfassungs-Tasks) eine Zeile mit den tatsächlich gefundenen Daten,
z.B. `[traders] Erste Handelsdaten erfasst: 6 Transaktionen in 2 Shops (Top-Liste).`
- damit man ohne das Dashboard zu öffnen in der Konsole sieht, ob die Anbindung
wirklich Daten liefert.

## Wie die Formate/APIs ermittelt wurden

- **dtlTradersPlus**-Log-Format: nirgends dokumentiert, aus dem kompilierten Jar
  (`dtlTradersPlus-6.4.38.jar`) mit dem CFR-Decompiler rekonstruiert
  (`com.degitise.minevid.dtlTraders.utils.Utils#logTradableGUIItem` /
  `#logCommandsGUIItem` / `#logTradeItem`) - das ist der Original-Code, keine
  Vermutung. Falls DTLTraders das Format in einer neuen Version ändert, muss
  `DtlLogParser` angepasst werden.
- **Towny**: offizielle `TownyAPI`/`Town`/`TownBlock`-Javadocs
  (townyadvanced.github.io/Towny/javadoc).
- **QuickShop-Hikari**: kompiliertes Jar mit CFR dekompiliert (die Source-API ist
  stark generisch, `Shop<U,L>`; die dekompilierten, typ-erasure-aufgelösten
  Signaturen zeigen, dass der rohe `Shop`-Typ ohne Generics-Ärger nutzbar ist).
  Zentrale Fundstellen: `ShopManager#getAllShops()`, `Shop#getPrice()`/`getItem()`/
  `getOwner()`/`shopType().isBuying()`, `ShopSuccessPurchaseEvent`.
- **AdvancedRegionMarket**: Source direkt vom GitHub-Repo gelesen
  (`alex9849/advanced-region-market`). `RegionManager` (implementiert
  `Iterable<Region>` über `YamlFileManager`) liefert alle Top-Level-Regionen,
  `Region#getSubregions()` die verschachtelten. Geprüft wurde auch, ob es ein
  brauchbares "Region verkauft"-Event gibt: `PreBuyEvent` ist der einzige
  Kauf-Hook und feuert synchron *vor* `setSold()`/dem eigentlichen Geldtransfer
  in `Region#buy()` - abbrechbar, also kein Erfolgssignal. Deshalb Registry-
  Spiegelung + Sold-Status-Diff wie bei Towny/QuickShop, nur ohne Event-Anteil.

## Test mit echten Produktionsdaten

Zusätzlich zum synthetischen Testserver wurde ein echter Datenexport eines
Produktionsservers importiert (89 dtlTradersPlus-Shops, 5.556 Log-Dateien,
316.198 Transaktionen, echte QuickShop- und TheNewEconomy-Datenbanken). Dabei
wurden mehrere echte Bugs gefunden und gefixt:

- **dtlTradersPlus-Zeitstempel wurden verworfen**: `DtlLogParser` hat den
  Zeitstempel jeder Log-Zeile (`[TT/MM/JJJJ hh:mm:ss]`) zwar per Regex erfasst,
  aber nie weitergereicht - `TraderDatabase#insertTransaction` hat stattdessen
  `System.currentTimeMillis()` (Importzeitpunkt) gespeichert. Dadurch lieferte
  der `from`/`to`-Datumsfilter auf echten historischen Daten (2023/2024) keine
  Treffer. Gefixt: `Transaction` trägt jetzt den geparsten Zeitstempel
  (`LocalDateTime` im Serverzeitzone-Kontext), durchgereicht bis in die DB.
  Nach dem Fix lieferte `from=2023-09-01&to=2023-09-30` korrekt historische
  Zeilen.
- **Spielernamen enthielten rohe Formatierungscodes**: dtlTradersPlus loggt den
  formatierten Anzeigenamen inkl. LuckPerms-Rang-Präfix, z.B.
  `§a[Spieler]Steve§r` statt `Steve` - macht Namensfilter/CSV-Export für externe
  Tools praktisch unbrauchbar. `DtlLogParser` entfernt jetzt Minecraft-
  Farbcodes (`§[0-9a-fk-or]`) und einen führenden `[Rang]`-Präfix.
- **QuickShop-Registry konnte den Server einfrieren**: `QuickShopRegistryCollector`
  rief `Shop#getShopBlock()` (→ `Location#getBlock()`) und `Shop#isValid()` auf,
  beide laden/generieren synchron den Chunk der Shop-Position, falls nicht
  geladen. Bei echten Shop-Daten über eine nicht vollständig erkundete Welt hat
  das den Hauptthread wiederholt für >10s blockiert (Paper-Watchdog-Abstürze).
  Gefixt: `Shop#bukkitLocation()` (reines `Location`-Feld, kein Chunk-Zugriff)
  statt `getShopBlock()`, und `Shop#isLoaded()` (gecachtes Flag) statt
  `isValid()` - Shops in aktuell ungeladenen Chunks werden für den jeweiligen
  Poll einfach übersprungen statt einen Chunk-Load zu erzwingen.
- **TheNewEconomy-Konfigurationsdatei war fehlerhaft**: die importierte
  `TheNewEconomy/config.yml` hatte einen fehlenden Leerzeichen-Fehler
  (`Symbol:" Taler"` statt `Symbol: " Taler"`), was TNE komplett am Start
  hinderte - das reißt über die harte Vault-Abhängigkeit das gesamte
  EconomyDashboard-Plugin mit (kein Bug in diesem Plugin, aber ein Beispiel
  dafür, dass eine einzige kaputte Fremd-Config die komplette Übersicht
  lahmlegen kann).
- **Wirtschafts-Modul sieht nur Bukkit-bekannte Spieler** - `EconomyCollector`
  iteriert `Bukkit.getOfflinePlayers()`, also nur Spieler, die sich JEMALS mit
  diesem konkreten Server verbunden haben. Das wurde extra nachrecherchiert, ob
  sich das mit TNEs eigener Account-API umgehen lässt (`TNECore.eco().account()`
  direkt statt über Vault) - zwei native Ansätze getestet
  (`AccountManager#getAccounts()`, das genau die Map ist, die auch TNEs eigener
  `/baltop`-Befehl nutzt; sowie `StorageManager#loadAll(Account.class, null)`,
  ein direkter DB-Bulk-Read). Ergebnis: **die importierte `Economy.mv.db` (69 MB)
  wird von der laufenden TNE-Instanz gar nicht genutzt** - das Testserver-Setup
  läuft im YAML-Flatfile-Speichermodus (`plugins/TheNewEconomy/accounts/*.yml`),
  die H2-Datenbank ist unverbundene Altlast aus dem Datenexport. Die YAML-Ordner
  enthalten tatsächlich nur 6 Konten (4 echte Spieler + 2 System-/Shop-Konten) -
  das Dashboard zeigte also von Anfang an die vollständigen, korrekten Zahlen;
  es gab keine "versteckten" Konten zu importieren. Für einen Server, der TNE
  tatsächlich im SQL/H2-Modus betreibt, könnte `StorageManager#loadAll` trotzdem
  relevant werden - der native Collector-Code wurde aber wieder entfernt, weil
  er sich gegen dieses Setup nicht verifizieren ließ und nicht ungetestet
  ausgeliefert werden sollte.

## Bekannte Einschränkungen

- dtlTradersPlus: Der Item-Name aus dem Transaktionslog (z.B. "Diamond", echter
  Minecraft-Materialname) und der Item-Name aus der Preisliste (z.B. "Diamant",
  Custom-Anzeigename aus der Shop-Config) sind zwei unterschiedliche Strings und
  werden in Suche/Item-Listen NICHT automatisch zusammengeführt, wenn ein Shop
  einen eigenen Anzeigenamen verwendet - dtlTradersPlus bildet diese beiden Werte
  an unterschiedlichen Stellen im Code, kein Bug in diesem Plugin.
- dtlTradersPlus TRADE-Zeilen: Shopname wird (anders als bei BUY/SELL) nicht in
  Anführungszeichen gesetzt - ein Shop mit Leerzeichen im Namen lässt sich aus
  einer TRADE-Zeile daher nicht zuverlässig parsen (Bug in dtlTradersPlus selbst).

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
refresh-interval-seconds: 60
modules:
  economy: true
  traders: true
  towny: true
  quickshop: true
  regionmarket: true
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
