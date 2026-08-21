# EconomyDashboard

Spigot/Paper-Plugin mit eingebettetem Webserver, das eine Live-Übersicht über die
Server-Wirtschaft liefert. End-to-end auf einem lokalen Testserver verifiziert
(Paper 1.21.11 + Vault + TheNewEconomy + Citizens + dtlTradersPlus + Towny +
ChestShop + QuickShop-Hikari): **Geldmenge & Verteilung**, **Händler-Übersicht über
dtlTradersPlus** inkl. Live-Preisliste, **Towny-Anbindung**, **Suche**,
**ChestShop-Anbindung**, **QuickShop-Anbindung**, **Login-Schutz** und **Modul-Toggles**.

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
- ChestShop: hört auf `ShopCreatedEvent`/`ShopEditedEvent`/`ShopDestroyedEvent`/
  `TransactionEvent` (dtlTradersPlus liest Logs, ChestShop selbst hat keine Logs -
  hier wird direkt an den echten Events gehorcht, zuverlässiger als Log-Scraping).
  Zeigt jeden bekannten Shop mit Besitzer, Item, Mengen, Kauf-/Verkaufspreis, plus
  Top-Spieler nach Netto-Umsatz.
- QuickShop-Hikari: liest QuickShops eigene Shop-Registry live über
  `ShopManager#getAllShops()` (QuickShop führt, anders als ChestShop, tatsächlich
  eine echte Datenbank aller Shops - kein Event-Zusammenbau nötig), plus
  Transaktionshistorie über `ShopSuccessPurchaseEvent`.
- Live-Suche über Spieler, Shops (dtlTradersPlus), Items, ChestShops und QuickShops
  (min. 2 Zeichen).
- Login-Maske mit konfigurierbarem Benutzer/Passwort und Hintergrundbild (URL oder
  lokale Datei), Session-Cookies, Logging von erfolgreichen/fehlgeschlagenen
  Login-Versuchen in der Server-Konsole.
- Jede Datenquelle einzeln über `config.yml` → `modules` abschaltbar (z.B. nur Towny).
- Speichert Händler-, ChestShop- und QuickShop-Daten dauerhaft in SQLite
  (`plugins/EconomyDashboard/data.db`, `chestshop.db`, `quickshop.db`) –
  übersteht Neustarts.
- Stellt das über einen eingebetteten Webserver bereit (kein Java-Webserver
  wie Tomcat nötig, läuft direkt im Plugin) unter `http://<server-ip>:8080/`.

## Wie die Formate/APIs ermittelt wurden

- **dtlTradersPlus**-Log-Format: nirgends dokumentiert, aus dem kompilierten Jar
  (`dtlTradersPlus-6.4.38.jar`) mit dem CFR-Decompiler rekonstruiert
  (`com.degitise.minevid.dtlTraders.utils.Utils#logTradableGUIItem` /
  `#logCommandsGUIItem` / `#logTradeItem`) - das ist der Original-Code, keine
  Vermutung. Falls DTLTraders das Format in einer neuen Version ändert, muss
  `DtlLogParser` angepasst werden.
- **Towny**: offizielle `TownyAPI`/`Town`/`TownBlock`-Javadocs
  (townyadvanced.github.io/Towny/javadoc).
- **ChestShop**: offizielle Event-Klassen aus dem `ChestShop-authors/ChestShop-3`
  GitHub-Repo (`com.Acrobot.ChestShop.Events.*`).
- **QuickShop-Hikari**: kompiliertes Jar mit CFR dekompiliert (die Source-API ist
  stark generisch, `Shop<U,L>`; die dekompilierten, typ-erasure-aufgelösten
  Signaturen zeigen, dass der rohe `Shop`-Typ ohne Generics-Ärger nutzbar ist).
  Zentrale Fundstellen: `ShopManager#getAllShops()`, `Shop#getPrice()`/`getItem()`/
  `getOwner()`/`shopType().isBuying()`, `ShopSuccessPurchaseEvent`.

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
- ChestShop hat keine "Liste alle Shops"-API - die Registrierung baut sich live aus
  Events auf. Shops, die vor der Installation dieses Plugins gebaut wurden, tauchen
  erst auf, sobald sie das nächste Mal bearbeitet oder gehandelt werden.
- ChestShop-Preise: Der Sign-Text wird best-effort geparst (`buy:sell`-Konvention).
  Bei stark angepasstem Preisformat (eigenes Währungssymbol o.ä.) können Preise
  als `null` ankommen - betrifft nur die Registrierungs-Anzeige, nicht die
  Transaktionshistorie (die nutzt `TransactionEvent#getExactPrice()`, den echten
  von ChestShop selbst berechneten Preis).
- **QuickShop ist vollständig end-to-end mit einer echten Transaktion getestet**:
  Shop per `/quickshop create 5` angelegt (Item, Preis, Besitzer korrekt in der
  Registry gelandet), Kiste befüllt, ein zweiter Spieler hat per Linksklick
  5x Netherite Scrap für $25.00 gekauft - danach zeigte
  `/api/quickshops/overview` exakt 1 Transaktion, 25.00 Einnahmen, korrekt
  TestSpieler1 zugeordnet. (Nebenbefund: das anfängliche "kein unterstützter
  Blocktyp" lag nicht an Towny/Berechtigungen, sondern schlicht an einer
  leeren Verkaufskiste bzw. ungeladenem Terrain beim Testen - beides gelöst.)
- ChestShop-Integration wurde **nicht** mit einer echten Transaktion end-to-end
  getestet - Sign-Edit-Netzwerkpakete sind in MC 1.21.11 zu komplex, um sie per
  Bot sauber nachzubilden (Kiste+Schild aufstellen ging, das "offizielle"
  Beschreiben des Schilds per Client-Paket nicht). Kompiliert und lädt sauber
  gegen die echte API (`com.Acrobot.ChestShop.Events.*`), nur die Live-Transaktion
  selbst ist unbestätigt. Empfehlung: einmal manuell im Spiel eine Kiste mit
  Schild bauen und einen Kauf/Verkauf testen.
- Nation-Kassen/Steuer-Einnahmen (nur Stadt-Ebene ist bisher angebunden).
- Zeitverlauf/Trends (Dashboard zeigt aktuell nur den Gesamtstand, keine Graphen
  über Zeit - die Rohdaten in SQLite stehen dafür aber schon bereit).

## Bauen

Voraussetzung: JDK 17+ und Maven. `libs/ChestShop.jar` muss im Projekt liegen
(nicht auf Maven Central verfügbar, wird als lokale `system`-Abhängigkeit
referenziert - liegt bereits im Repo).

```
mvn package
```

Ergebnis liegt danach unter `target/economydashboard-0.1.0.jar`.

## Installieren

1. Jar in den `plugins`-Ordner eures Servers kopieren.
2. Server neu starten (Vault + TheNewEconomy müssen laufen; dtlTradersPlus, Towny,
   ChestShop und QuickShop-Hikari sind optional - ohne sie bleiben die jeweiligen
   Dashboard-Bereiche einfach leer, gesteuert über `config.yml` → `modules`). Der SQLite-Treiber wird
   beim ersten Start automatisch von Maven Central nachgeladen (Spigot/Paper
   Library-Loader, `plugin.yml` → `libraries:`) – der Server braucht dafür
   einmalig Internetzugang.
3. `plugins/EconomyDashboard/config.yml` prüfen: Port, Login-Zugangsdaten
   (**Standardpasswort unbedingt ändern**, sonst Warnung in der Konsole bei jedem
   Start), ggf. Module abschalten.
4. Dashboard im Browser öffnen: `http://<server-ip>:8080/`.

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
  chestshop: true
  quickshop: true
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
