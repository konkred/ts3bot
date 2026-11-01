# TeamSpeak 3 Bot

Rekonstruierter Quellcode des ursprünglichen `bot.jar` (Floxiii, 2021), jetzt mit konfigurierbaren
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
| Mitglied | Gast → Mitglied, wenn der erste Besuch X Minuten her ist (beim Join) | `feature/JoinHandler` |
| Treue-Ränge | Rang nach Dauer seit dem ersten Besuch (beim Join) | `feature/JoinHandler` |

Neu gegenüber dem alten Bot: automatischer Reconnect, einstellbarer Query-Port und Flood-Rate.

## Struktur

```
src/main/java/ts3bot/
  Main.java               Verbindung, Login, Reconnect
  config/BotConfig.java   Liest config.properties (+ Standardwerte)
  config/LoyaltyRank.java
  listener/BotListener.java   Verteilt Events an die Features
  feature/CommandHandler.java Chat-Befehle
  feature/JoinHandler.java    Mitglied- und Treue-Ränge
```

Neue Features: eigene Klasse unter `feature/` anlegen und in `BotListener` aufrufen.
