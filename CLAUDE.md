# TeamSpeak 3 Bot

Java-Bot für einen TeamSpeak-3-Server, verbunden über ServerQuery mit der
[TeamSpeak-3-Java-API](https://github.com/TheHolyWaffle/TeamSpeak-3-Java-API) 1.3.1.
Der Quellcode ist aus der alten, nur kompiliert vorliegenden `legacy/bot.jar` (Floxiii, 2021)
rekonstruiert. Die Funktionen der alten jar sollen sich mit den Standardwerten weiter genauso
verhalten. Neu dazugekommen sind die regelmäßige Rang-Prüfung, `!treue`, `!help`, der Gruppen-Check,
die Willkommensnachricht, der Bot-Channel und die Funktionen im unteren Teil der Config-Vorlage.

Der Query-Bot ist für normale Nutzer unsichtbar, sie können ihn also nicht von sich aus anschreiben.
Er muss sie zuerst anschreiben, dann öffnet sich bei ihnen ein Chat-Tab, in dem sie antworten können.
Der Bot reagiert auf private Nachrichten und auf den Chat des Channels, in dem er sitzt. Den Server-Chat
ignoriert er bewusst, dort sollen normale Nutzer nicht schreiben.

## Befehle

```bash
mvn package                                  # baut target/bot.jar (fat jar) und führt die Tests aus
mvn test                                     # nur Tests
/usr/lib/jvm/java-21-openjdk/bin/java -jar target/bot.jar   # starten ("java" ist nicht im PATH)
```

Java 17 ist das Kompilier-Ziel (`maven.compiler.release`), lokal ist JDK 21 installiert.

## Struktur

- `ts3bot/Main.java`: baut die `TS3Config` und verbindet sich. Login, Server-Auswahl, Nickname und
  `registerAllEvents()` stehen im `ConnectionHandler.onConnect`, weil der beim ersten Verbinden
  **und nach jedem Reconnect** läuft. Was nur einmal passieren soll (Listener registrieren,
  Startnachricht), steht nach `query.connect()`.
- `config/BotConfig.java`: typisierte Getter für `config.properties`.
- `listener/BotListener.java`: einziger `TS3EventAdapter`. Verteilt Events an die Features und
  ignoriert die eigenen Nachrichten des Bots.
- `feature/CommandHandler.java`: Chat-Befehle `!ruhe` (Gruppe an/aus mit Cooldown), `!botinfo`, `!treue [Name]`, `!help`.
  Jeder Befehl antwortet immer, auch bei Fehlern (`command.error-message`) und unbekannten Befehlen (`command.unknown-message`).
  Stilles Ignorieren vermeiden. Nachrichten ohne `!` bleiben unbeantwortet.
  `!treue <Name>` dürfen alle, außer `command.treue.other-groups` ist gesetzt.
- `feature/ClientLookup.java`: findet Nutzer zu einer Chat-Eingabe, auch wenn der Client den Namen als Link schickt.
  Namen sind nicht eindeutig: Bei mehreren Treffern zeigt der Bot eine Liste (`lookup.*`), nie stillschweigend den ersten.
  Die API fragt jeden Datenbank-Treffer einzeln ab, deshalb ist `%` in der Eingabe gesperrt.
  Der Gruppen-Schutz listet nichts, sondern verlangt bei mehrdeutigen Namen die Unique-ID.
- `feature/RankManager.java`: Gast → Member nach Wartezeit und Treue-Ränge, beim Join und für alle Online-Nutzer.
  `update` ist `synchronized`, weil Join-Events und die regelmäßige Prüfung in verschiedenen Threads laufen.
- `feature/Periodic.java`: Timer auf Daemon-Threads, fängt Exceptions ab. `Main` startet damit die Rang-Prüfung und den `client-monitor`.
- `feature/GroupCheck.java`: warnt beim Start im Log vor Gruppen-IDs, die auf dem Server fehlen.
- `feature/WelcomeMessage.java`: private Nachricht beim allerersten Besuch (`client_totalconnections`).
- `feature/BotChannel.java`: optionaler Channel (`bot-channel.id`). Wer ihn betritt, bekommt die Befehlsliste
  privat. Der Bot betritt ihn im `onConnect` **vor** `registerAllEvents()`, damit der Channel-Chat dort ankommt.
- `src/main/resources/config.default.properties`: Vorlage **und** Quelle aller Standardwerte.
- `feature/AutoMover.java`: verschiebt oder kickt bei Aufnahme, Abwesenheit, Stummschaltung und Untätigkeit, und verschiebt zurück.
  Wer sich selbst aus dem Ziel-Channel bewegt hat, wird nicht zurückgeholt und nicht erneut verschoben.
- `feature/AutoMoveOnJoin.java`: verschiebt gewählte Gruppen beim Join, wenn sie im Standard-Channel landen.
- `feature/JoinNotifications.java`: Channel-Überwachung und Servergruppen-Benachrichtigung.
- `feature/NameFilter.java`: unerlaubte Nicknames (per `client-monitor`) und Channelnamen (per Event).
- `feature/InactiveChannels.java`: löscht lange leere Channels, standardmäßig nur `dry-run`.
- `feature/LastSeen.java`: Befehl `!seen`.
- `feature/GroupProtection.java`: entfernt nicht erlaubte Mitglieder aus geschützten Gruppen. Die Liste steht in
  `group-protection.properties` (nicht im Repo). Beim ersten Lauf oder ersten Chat-Befehl für eine Gruppe werden die aktuellen Mitglieder übernommen.
  Die Liste per Chat ändern dürfen `group-protect.admin-groups` (Gruppen-IDs) und `group-protect.admin-users` (Unique-IDs).
- `feature/ClientActions.java`: gemeinsame Helfer für Nachrichten, Benachrichtigungen und Strafen.

Ein neues Feature bekommt eine eigene Klasse unter `feature/` und wird in `BotListener` aufgerufen.

## Konfiguration

- Jeder neue Config-Schlüssel muss mit Standardwert und Kommentar in
  `config.default.properties` stehen. `BotConfig` nimmt fehlende Schlüssel beim Laden aus dieser Datei und schreibt
  sie nicht in `config.properties` zurück. Deshalb funktionieren alte Configs, die nur `host`/`port`/`user`/`password`/`nickname`
  enthalten, weiter. Im Code gibt es keine eigenen Fallback-Werte.
- Features lesen ihre Schlüssel im Konstruktor über `config.string/integer/bool/intSet/patterns/choice`. `Main` legt sie
  vor `query.connect()` an, damit eine falsche Config den Bot mit einer klaren Meldung beendet. `FeaturesTest` prüft das.
- Funktionen, die Nutzer verschieben, kicken oder Channels und Gruppen ändern, sind standardmäßig aus.
- `port` ist der Port des virtuellen Servers (Name aus der alten Config). Der Query-Port heißt `query.port`.
- Eigene `loyalty.rank.*`-Einträge ersetzen die Standard-Ränge komplett, sie werden nicht gemischt.
- Die Datei wird als UTF-8 gelesen.
- `config.properties` enthält Zugangsdaten und steht in `.gitignore`. Nie committen.

## Servergruppen (Standardwerte)

| ID | Bedeutung |
|---|---|
| 11 | Gast (Standardgruppe des Servers) |
| 10 | Member, wird automatisch vergeben, wenn der erste Besuch 10 Minuten her ist |
| 410 | Anti-Bot (`exempt-group`): Der Bot ändert bei diesen Nutzern **keine** Ränge automatisch. `!ruhe` funktioniert weiter. |
| 416 | Ruhe (`!ruhe`) |
| 315–318, 409, 417, 422, 423 | Treue-Ränge: 6 Monate bis 7 Jahre |
| 399 | Stammuser, wird von Admins von Hand vergeben. Der Bot fasst diese Gruppe nicht an. |

Die Standard-Treue-Ränge rechnen wie der alte Bot mit 30-Tage-Monaten (1 Jahr = 360 Tage), `!treue` zeigt
Dauern dagegen mit 365 Tagen pro Jahr. Gemessen wird ab dem ersten Besuch auf dem Server (`client_created`),
nicht nach Online-Zeit.

## Hinweis zur Geschwindigkeit

Mit `query.flood-rate=default` schickt die API etwa 3 Befehle pro Sekunde. Bei viel Betrieb verzögern sich
Reaktionen um einige Sekunden. Abhilfe: Bot-IP in `query_ip_allowlist.txt` eintragen und `unlimited` setzen.

## Konventionen

- Texte für Nutzer, Log-Meldungen, Kommentare und Commit-Messages sind auf Deutsch.
- Nachrichten an Nutzer dürfen TeamSpeak-BBCode enthalten (`[B]`, `[COLOR=…]`, `[URL=…]`).
- Gruppen-IDs, Texte und Zeiten kommen in die Config, nicht in den Code.
- Logik ohne TS3-Verbindung (z. B. `RankManager.highestReached`) als package-private
  statische Methode herausziehen und mit JUnit 5 testen.

## Release

1. Version in `pom.xml` erhöhen.
2. `mvn clean package`, dann `cp target/bot.jar target/ts3bot-<version>.jar`.
3. Committen, pushen, Tag `v<version>` setzen und pushen.
4. `gh release create v<version> target/ts3bot-<version>.jar --repo konkred/ts3bot`

Das Repo `konkred/ts3bot` ist öffentlich. Gepusht wird per SSH, `gh` ist für Releases eingerichtet.
