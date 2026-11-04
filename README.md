# TeamSpeak 3 Bot

Bot für einen TeamSpeak-3-Server, der über ServerQuery läuft. Er vergibt Ränge automatisch, beantwortet
Chat-Befehle und kann Nutzer verschieben, Channels aufräumen und Gruppen schützen.

Der Quellcode ist aus der alten, nur kompiliert vorliegenden `bot.jar` von Floxiii (2021) rekonstruiert, die
unter [legacy/bot.jar](legacy/bot.jar) liegt. Grundlage ist die
[TeamSpeak-3-Java-API](https://github.com/TheHolyWaffle/TeamSpeak-3-Java-API).

Der Bot ist mit Unit-Tests und gegen einen simulierten Query-Server getestet und läuft auf einem echten
TeamSpeak-Server. Dort sind noch nicht alle optionalen Funktionen erprobt.

- [Einrichtung](#einrichtung)
- [Funktionen](#funktionen)
- [Konfiguration](#konfiguration)
- [Fehlersuche](#fehlersuche)
- [Entwicklung](#entwicklung)

## Einrichtung

### Voraussetzungen

- Java 17 oder neuer auf dem Rechner, auf dem der Bot läuft
- Ein ServerQuery-Login für den TeamSpeak-Server, z. B. `serveradmin`
- Erreichbarer Query-Port des Servers (Standard 10011)

### 1. Jar herunterladen

Die aktuelle `ts3bot-<version>.jar` liegt unter
[Releases](https://github.com/konkred/ts3bot/releases). Das Repo ist öffentlich, der Download braucht also
kein GitHub-Konto. Lege die Datei in einen eigenen Ordner, z. B. `/opt/ts3bot`.

Mit der GitHub-CLI geht das so:

```bash
gh release download --repo konkred/ts3bot --pattern 'ts3bot-*.jar' --dir /opt/ts3bot
```

### 2. Config anlegen

Beim ersten Start legt der Bot `config.properties` im **aktuellen Ordner** an und beendet sich:

```bash
cd /opt/ts3bot
java -jar ts3bot-<version>.jar
```

Die Datei enthält das Query-Passwort und sollte nur für den Bot lesbar sein:

```bash
chmod 600 /opt/ts3bot/config.properties
```

### 3. Zugangsdaten eintragen

Trage in `config.properties` mindestens diese Werte ein:

```properties
# Adresse des TeamSpeak-Servers
host=127.0.0.1
# ServerQuery-Port
query.port=10011
# Voice-Port des virtuellen Servers
port=9987
user=serveradmin
password=DEIN_QUERY_PASSWORT
# Anzeigename des Bots
nickname=Bot
```

Läuft der Bot nicht auf demselben Rechner wie der Server, trage die Bot-IP in die `query_ip_allowlist.txt` des
TeamSpeak-Servers ein (bei älteren Versionen `query_ip_whitelist.txt`). Dann kann der Bot auch
`query.flood-rate=unlimited` nutzen. Sonst sendet er nur etwa 3 Befehle pro Sekunde, damit der Server ihn nicht
wegen Spam sperrt.

### 4. Gruppen-IDs anpassen

Die Standardwerte passen nur zu dem Server, für den der Bot geschrieben wurde. Auf einem anderen Server müssen
sie angepasst werden, sonst vergibt der Bot die falschen Gruppen:

| Schlüssel | Bedeutung | Standard |
|---|---|---|
| `member.guest-group` | Gast, die Standardgruppe des Servers | 11 |
| `member.member-group` | Member | 10 |
| `exempt-group` | Anti-Bot: keine automatischen Rang-Änderungen | 410 |
| `command.ruhe.group` | Gruppe für `!ruhe` | 416 |
| `loyalty.rank.1` bis `.8` | Treue-Ränge im Format `<tage>;<gruppen-id>;<bezeichnung>` | 315–318, 409, 417, 422, 423 |

Member-Gruppe und Treue-Ränge lassen sich mit `member.enabled=false` und `loyalty.enabled=false` abschalten.

Die IDs von Gruppen und Channels zeigt die ServerQuery. Das Passwort geht dabei unverschlüsselt über die
Leitung, führe die Befehle also am besten direkt auf dem Server aus:

```bash
telnet 127.0.0.1 10011
```

Danach nacheinander eingeben, `quit` beendet die Sitzung:

```text
login serveradmin DEIN_QUERY_PASSWORT
use port=9987
servergrouplist
channellist
quit
```

In der Ausgabe stehen die IDs bei `sgid=` (Gruppen) und `cid=` (Channels).

### 5. Bot starten

```bash
cd /opt/ts3bot
java -jar ts3bot-<version>.jar
```

Wenn alles passt, steht im Log `Der Bot wurde erfolgreich gestartet!`. Prüfe dort auch die Warnungen: Der Bot
meldet beim Start alle eingetragenen Gruppen-IDs, die es auf dem Server nicht gibt. Schreibe zum Test in einem
TeamSpeak-Client im Standard-Channel `!help`, der Bot antwortet dann privat.

Beim Start schickt der Bot eine Nachricht an den ganzen Server. Der Standardtext wirbt für Floxiii. Mit einem
leeren `startup.message=` entfällt sie.

Etwa 5 Minuten nach dem Start prüft der Bot alle Online-Nutzer. Wer einen Rang verpasst hat, bekommt ihn dann
samt Glückwunsch-Nachricht, beim ersten Start können das mehrere auf einmal sein.

### 6. Dauerhaft laufen lassen (optional)

Beispiel für systemd in `/etc/systemd/system/ts3bot.service`. Benutzer und Jar-Namen musst du anpassen:

```ini
[Unit]
Description=TeamSpeak 3 Bot
After=network-online.target
Wants=network-online.target

[Service]
User=ts3bot
WorkingDirectory=/opt/ts3bot
ExecStart=/usr/bin/java -jar /opt/ts3bot/ts3bot-<version>.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now ts3bot
```

### Bot-Channel einrichten (empfohlen)

Normale Nutzer sehen den Bot nicht und können ihn deshalb nicht von sich aus anschreiben. Er öffnet den Chat
selbst: mit der Willkommensnachricht beim ersten Besuch und mit der Rang-Nachricht. Damit Nutzer ihn jederzeit
erreichen, lege einen Channel an, z. B. „Bot-Befehle“, und trage dessen ID ein:

```properties
bot-channel.id=77
```

Wer den Channel betritt, bekommt die Befehlsliste privat und kann im geöffneten Tab antworten. Der Bot setzt
sich selbst hinein, dort funktionieren die Befehle also auch im Channel-Chat. Der Channel darf kein Passwort
haben und nicht der Standard-Channel sein, sonst bekommt jeder bei jedem Join die Befehlsliste.

### Update

Neue Jar in den Ordner legen und den Bot damit neu starten. Der Bot verändert `config.properties` dabei nicht,
auch die Config des alten Bots funktioniert weiter. Für Schlüssel, die in der Datei fehlen, nutzt er beim Laufen
die Standardwerte, sie erscheinen aber nicht in der Datei.

Um eine neue Einstellung zu ändern, kopiere ihren Schlüssel aus
[config.default.properties](src/main/resources/config.default.properties) in deine `config.properties`, passe
den Wert an und starte den Bot neu.

## Funktionen

### Immer aktiv

| Funktion | Beschreibung |
|---|---|
| Automatischer Reconnect | Verbindet sich nach einem Abbruch neu (`reconnect`) |
| Gruppen-Check | Warnt beim Start vor Gruppen-IDs, die auf dem Server fehlen |

### Standardmäßig an

| Funktion | Beschreibung | Schlüssel |
|---|---|---|
| Member-Gruppe | Gast wird Member, wenn der erste Besuch 10 Minuten her ist | `member.*` |
| Treue-Ränge | Rang nach Dauer seit dem ersten Besuch, von 6 Monaten bis 7 Jahre | `loyalty.*` |
| Rang-Prüfung | Prüft beides beim Join und alle 5 Minuten für alle Online-Nutzer | `rank-check.*` |
| Willkommensnachricht | Private Nachricht beim ersten Besuch | `welcome.*` |

Bei Nutzern in der Anti-Bot-Gruppe (`exempt-group`) ändert der Bot keine Ränge automatisch.

### Chat-Befehle

Der Bot reagiert auf private Nachrichten und im Chat des Channels, in dem er sitzt. Die Antwort kommt immer
privat. Der Server-Chat wird bewusst ignoriert. Auf unbekannte Befehle mit `!` antwortet er mit einem Hinweis auf
`!help` (`command.unknown-message`, leer schaltet das ab).

| Befehl | Beschreibung | Verfügbar |
|---|---|---|
| `!help` | Listet die Befehle auf | immer |
| `!treue [Name]` | Zeigt, wie lange man selbst oder ein anderer Nutzer dabei ist und wann der nächste Rang kommt | immer, `!treue <Name>` lässt sich mit `command.treue.other-groups` auf Gruppen beschränken |
| `!ruhe` | Schaltet die Ruhe-Gruppe an oder aus | immer |
| `!botinfo` | Infotext zum Bot | immer |
| `!seen <Name>` | Zeigt, wann jemand zuletzt online war | mit `seen.enabled=true` |
| `!schutz-add <Gruppe> <Name oder Unique-ID>` | Erlaubt einem Nutzer eine geschützte Gruppe | mit Gruppen-Schutz, siehe unten |
| `!schutz-del <Gruppe> <Name oder Unique-ID>` | Entzieht die Erlaubnis | mit Gruppen-Schutz, siehe unten |

Haben mehrere Nutzer denselben Namen, zeigen `!seen` und `!treue <Name>` eine Liste mit Unique-IDs, höchstens `lookup.max-results` Einträge. Mit `!seen <Unique-ID>` oder `!treue <Unique-ID>` fragt man dann genau einen Nutzer ab. `!schutz-add` und `!schutz-del` verlangen die Unique-ID, wenn mehrere Nutzer dieses Namens online sind.

### Optional, standardmäßig aus

| Funktion | Beschreibung | Schlüssel |
|---|---|---|
| Bot-Channel | Befehlsliste beim Betreten eines Channels | `bot-channel.id` |
| Ankündigungen | Nachricht alle X Minuten in den Server-Chat oder den Chat des Channels, in dem der Bot sitzt | `announcement.*` |
| Automatisches Verschieben | Gruppen beim Betreten des Servers in einen Channel verschieben | `auto-move.*` |
| Abwesenheit | In einen Channel verschieben, bei Rückkehr zurück | `away.*` |
| Stummschaltung | Nach X Sekunden verschieben, danach zurück | `mute.*` |
| Untätigkeit | Warnen, dann verschieben oder kicken | `idle.*` |
| Aufnahmen | Verschieben oder kicken | `recording.*` |
| Unerlaubte Nicknames | Warnen, kicken oder bannen | `nickname.*` |
| Unerlaubte Channelnamen | Channel löschen, Ersteller warnen, kicken oder bannen | `channel-name.*` |
| Channel-Überwachung | Gruppen informieren, wenn jemand einen Channel betritt, z. B. für Support | `channel-watch.*` |
| Gruppen-Benachrichtigung | Gruppen informieren, wenn ein Mitglied online kommt | `group-notify.*` |
| Inaktive Channels | Lange leere Channels löschen, zuerst im Probemodus | `inactive-channel.*` |
| Zuletzt gesehen | Befehl `!seen` | `seen.*` |
| Gruppen-Schutz | Nur erlaubte Nutzer in einer Gruppe | `group-protect.*` |

## Konfiguration

Alle Einstellungen stehen mit Erklärung in
[config.default.properties](src/main/resources/config.default.properties). Beim ersten Start wird diese Vorlage
zu `config.properties`. Die Datei wird als UTF-8 gelesen und nur beim Start, nach einer Änderung muss der Bot
also neu starten.

Allgemeine Regeln:

- Mehrere Gruppen oder Channels werden durch Komma getrennt.
- Verbotene Namen sind durch Semikolon getrennte reguläre Ausdrücke, Groß- und Kleinschreibung zählt nicht.
- Eigene `loyalty.rank.*`-Einträge ersetzen die Standard-Ränge komplett.
- Die meisten falschen Einstellungen beenden den Bot beim Start mit einer Meldung, die den Schlüssel nennt.

### Optionale Funktionen einschalten

Übernimm den Block der Funktion aus der Vorlage, trage die IDs ein und setze `enabled=true`. Am sichersten
schaltest du die Funktionen einzeln ein, von harmlos zu heikel:

1. **Nur Nachrichten:** `!seen`, Ankündigungen, Channel-Überwachung, Gruppen-Benachrichtigung
2. **Verschieben:** Abwesenheit, Stummschaltung, Untätigkeit (zuerst mit `idle.action=move`)
3. **Folgen für Nutzer:** Nickname- und Channelnamen-Filter, Aufnahmen, Gruppen-Schutz
4. **Inaktive Channels:** erst im Probemodus laufen lassen und die im Log genannten Channels prüfen, danach
   `inactive-channel.dry-run=false` setzen

Das solltest du dabei wissen:

- Die Anti-Bot-Gruppe schützt nur vor Rang-Änderungen. Wer auch von Kicks und Verschieben ausgenommen sein soll,
  gehört in `ignore-groups` der jeweiligen Funktion. Das gilt auch für Admins.
- Der Query-Login braucht die passenden Rechte zum Kicken, Bannen, Verschieben und Löschen.
- Beim Gruppen-Schutz keine Gruppen eintragen, die der Bot selbst vergibt. Die erlaubten Nutzer stehen in
  `group-protection.properties` neben der Jar. Beim ersten Lauf oder ersten Chat-Befehl für eine Gruppe übernimmt der Bot die aktuellen Mitglieder.
- Die Liste per Chat ändern dürfen nur die Mitglieder von `group-protect.admin-groups` und die Nutzer in
  `group-protect.admin-users` (Unique-IDs). Sind beide leer, darf es niemand:

  ```properties
  group-protect.admin-groups=9
  group-protect.admin-users=XEP638xkA3770DrBmhXZ0y8L6Ng=
  ```
- Inaktive Channels: Der Standard-Channel, Channels mit Unterchanneln und Channels, die der Bot selbst braucht,
  bleiben immer erhalten. Permanente Channels bleiben, solange `inactive-channel.keep-permanent=true` gilt.

## Fehlersuche

| Beobachtung | Ursache und Lösung |
|---|---|
| Bot beendet sich mit `config.properties wurde neu angelegt` | Normal beim ersten Start. Zugangsdaten eintragen und neu starten. |
| Fehler beim Verbinden (`Connection refused`) | `host` und `query.port` prüfen, und ob der Query-Port erreichbar ist. |
| Fehler beim Login | `user` und `password` prüfen. Das ist der Query-Login, nicht der normale TeamSpeak-Zugang. |
| `Servergruppe aus der Config existiert nicht auf dem Server` | ID in der Config anpassen, siehe [Gruppen-IDs](#4-gruppen-ids-anpassen). |
| `Bot-Channel … konnte nicht betreten werden` | Channel-ID, Rechte des Query-Logins und ein mögliches Channel-Passwort prüfen. |
| `Du darfst den Gruppen-Schutz nicht bearbeiten.` | Gruppe in `group-protect.admin-groups` oder Unique-ID in `group-protect.admin-users` eintragen und neu starten. Das Log nennt die abgelehnte Unique-ID. |
| `Das hat leider nicht geklappt` als Antwort auf einen Befehl | Die Ursache steht im Log unter `Befehl … fehlgeschlagen`, oft fehlen Rechte oder Schreibzugriff auf `group-protection.properties`. |
| Bot reagiert verzögert | Bot-IP in die Allowlist eintragen und `query.flood-rate=unlimited` setzen. |
| Bot wird vom Server gesperrt | Spam-Sperre: `query.flood-rate=default` nutzen oder die Bot-IP in die Allowlist eintragen. |
| Nutzer können den Bot nicht anschreiben | Er ist für sie unsichtbar, siehe [Bot-Channel](#bot-channel-einrichten-empfohlen). |

## Entwicklung

Zum Bauen brauchst du Java 17+ und Maven. Das erzeugt `target/bot.jar` mit allen Abhängigkeiten und führt die
Tests aus:

```bash
mvn package
```

```text
src/main/java/ts3bot/
  Main.java                       Verbindung, Login, Reconnect, Start der Timer
  config/BotConfig.java           Liest config.properties und ergänzt Standardwerte
  config/LoyaltyRank.java         Ein Treue-Rang
  listener/BotListener.java       Verteilt die Events an die Features
  feature/AutoMover.java          Abwesenheit, Stummschaltung, Untätigkeit, Aufnahmen
  feature/AutoMoveOnJoin.java     Verschieben beim Betreten des Servers
  feature/Announcements.java      Regelmäßige Ankündigungen
  feature/BotChannel.java         Befehlsliste beim Betreten des Bot-Channels
  feature/ClientActions.java      Gemeinsame Helfer für Nachrichten und Strafen
  feature/ClientLookup.java       Findet Nutzer zu einer Chat-Eingabe
  feature/CommandHandler.java     Chat-Befehle
  feature/GroupCheck.java         Prüft beim Start die Gruppen-IDs
  feature/GroupProtection.java    Gruppen-Schutz
  feature/InactiveChannels.java   Löscht lange leere Channels
  feature/JoinNotifications.java  Channel-Überwachung, Gruppen-Benachrichtigung
  feature/LastSeen.java           Befehl !seen
  feature/NameFilter.java         Unerlaubte Nicknames und Channelnamen
  feature/Periodic.java           Timer für regelmäßige Aufgaben
  feature/RankManager.java        Member-Gruppe und Treue-Ränge
  feature/WelcomeMessage.java     Willkommensnachricht
```

Ein neues Feature bekommt eine eigene Klasse unter `feature/` und wird in `BotListener` oder `Main` aufgerufen.
Weitere Regeln und der Ablauf für Releases stehen in [CLAUDE.md](CLAUDE.md).
