# TeamSpeak 3 Bot

Rekonstruierter Quellcode des ursprünglichen `bot.jar` (Floxiii, 2021, liegt unter `legacy/bot.jar`), jetzt mit konfigurierbaren
Gruppen-IDs und Texten. Verwendet die [TeamSpeak-3-Java-API](https://github.com/TheHolyWaffle/TeamSpeak-3-Java-API).

## Bauen & Starten

Benötigt Java 17+ und Maven.

```bash
mvn package
java -jar target/bot.jar
```

Beim ersten Start wird `config.properties` aus der Vorlage
(`src/main/resources/config.default.properties`) angelegt und der Bot beendet sich.
Zugangsdaten eintragen, dann erneut starten. Eine bestehende `config.properties` des alten Bots
funktioniert weiter. Fehlende Schlüssel bekommen dabei die alten Standardwerte.

## Funktionen

| Funktion | Beschreibung | Code |
|---|---|---|
| `!ruhe` | Schaltet die „Ruhe“-Gruppe an/aus (mit Cooldown) | `feature/CommandHandler` |
| `!botinfo` | Antwortet mit einem Info-Text | `feature/CommandHandler` |
| `!treue` | Zeigt, wie lange man dabei ist und wann der nächste Rang kommt | `feature/CommandHandler` |
| `!help` | Listet alle Befehle auf | `feature/CommandHandler` |
| Mitglied | Gast → Mitglied, wenn der erste Besuch X Minuten her ist | `feature/RankManager` |
| Treue-Ränge | Rang nach Dauer seit dem ersten Besuch | `feature/RankManager` |
| Rang-Prüfung | Prüft Mitglied und Treue-Ränge beim Join und alle 5 Minuten für alle Online-Nutzer | `feature/RankManager` |
| Gruppen-Check | Warnt beim Start im Log vor Gruppen-IDs, die auf dem Server fehlen | `feature/GroupCheck` |
| Willkommen | Private Nachricht beim allerersten Besuch, öffnet den Chat-Tab mit dem Bot | `feature/WelcomeMessage` |
| Bot-Channel | Wer den Channel betritt, bekommt die Befehlsliste privat (optional) | `feature/BotChannel` |

Der Bot ist für normale Nutzer unsichtbar. Sie erreichen ihn über den Chat-Tab, der sich öffnet, wenn
er sie anschreibt (Willkommen, Rang-Nachricht, Bot-Channel), oder im Chat des Channels, in dem er sitzt.
Für den Bot-Channel einen Channel anlegen und dessen ID als `bot-channel.id` eintragen. Nicht den
Standard-Channel verwenden, sonst bekommt jeder bei jedem Join die Befehlsliste.

Weitere Funktionen, alle standardmäßig aus und in `config.default.properties` beschrieben:

| Funktion | Beschreibung | Schlüssel |
|---|---|---|
| Ankündigungen | Nachricht alle X Minuten in Server- oder Channel-Chat | `announcement.*` |
| Automatisches Verschieben | Gruppen beim Betreten in einen Channel verschieben | `auto-move.*` |
| Abwesenheit | In einen Channel verschieben, bei Rückkehr zurück | `away.*` |
| Stummschaltung | Nach X Sekunden verschieben, danach zurück | `mute.*` |
| Untätigkeit | Warnen, dann verschieben oder kicken | `idle.*` |
| Aufnahmen | Verschieben oder kicken | `recording.*` |
| Unerlaubte Nicknames | Warnen, kicken oder bannen | `nickname.*` |
| Unerlaubte Channelnamen | Löschen, und warnen, kicken oder bannen | `channel-name.*` |
| Channel-Überwachung | Gruppen informieren, wenn jemand einen Channel betritt | `channel-watch.*` |
| Gruppen-Benachrichtigung | Gruppen informieren, wenn ein Mitglied online kommt | `group-notify.*` |
| Inaktive Channels | Lange leere Channels löschen (`dry-run` zuerst) | `inactive-channel.*` |
| Zuletzt gesehen | `!seen <Name>` | `seen.*` |
| Gruppen-Schutz | Nur erlaubte Nutzer in einer Gruppe, `!schutz-add` / `!schutz-del` | `group-protect.*` |

Gruppen (Standard): 11 = Gast (Standardgruppe), 10 = Member, 410 = Anti-Bot. Bei Nutzern in der
Anti-Bot-Gruppe (`exempt-group`) verändert der Bot keine Ränge automatisch, `!ruhe` funktioniert aber weiterhin.

Neu gegenüber dem alten Bot: automatischer Reconnect, einstellbarer Query-Port und Flood-Rate.

## Struktur

```
src/main/java/ts3bot/
  Main.java               Verbindung, Login, Reconnect
  config/BotConfig.java   Liest config.properties (+ Standardwerte)
  config/LoyaltyRank.java
  listener/BotListener.java   Verteilt Events an die Features
  feature/CommandHandler.java Chat-Befehle
  feature/RankManager.java    Mitglied- und Treue-Ränge
  feature/Periodic.java       Timer für regelmäßige Aufgaben
  feature/AutoMover.java      Abwesenheit, Stummschaltung, Untätigkeit, Aufnahmen
  feature/AutoMoveOnJoin.java Verschieben beim Betreten des Servers
  feature/JoinNotifications.java  Channel-Überwachung, Gruppen-Benachrichtigung
  feature/NameFilter.java     Unerlaubte Nicknames und Channelnamen
  feature/InactiveChannels.java   Löscht lange leere Channels
  feature/LastSeen.java       Befehl !seen
  feature/GroupProtection.java    Servergruppen-Schutz
  feature/GroupCheck.java     Prüft beim Start die Gruppen-IDs
  feature/WelcomeMessage.java Nachricht beim ersten Besuch
  feature/BotChannel.java     Befehlsliste beim Betreten des Bot-Channels
```

Neue Features: eigene Klasse unter `feature/` anlegen und in `BotListener` aufrufen.
