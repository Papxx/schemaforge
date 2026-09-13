# Prompts für Claude Code

Kopierfertig. Immer im Repo-Root starten, damit `CLAUDE.md` automatisch geladen wird.

## Session-Start (jede Session)

```
Lies CLAUDE.md, docs/ARCHITECTURE.md und docs/TASKS.md. Fasse in 5 Zeilen zusammen:
welches Ticket ist in_progress oder das nächste todo, welche AK hat es, welche refs/-Dateien
sind dafür relevant. Dann warte auf mein Go.
```

## Ticket abarbeiten

```
Bearbeite Ticket <ID>. Vorgehen:
1. Ticket in docs/TASKS.md auf in_progress setzen.
2. Relevante refs/-Dateien lesen und die konkreten API-Signaturen, die du brauchst, kurz auflisten
   (Klasse#methode) – bevor du Code schreibst.
3. Implementieren. Unit-Tests zuerst, wo das Ticket welche verlangt.
4. ./gradlew build && ./gradlew test – muss grün sein.
5. Jede AK einzeln durchgehen: erledigt / manuell zu testen (mit exakten Schritten für mich) / nicht erledigt + warum.
6. Commit mit "<ID>: <Kurzbeschreibung>". Ticket erst auf done, wenn ich die manuellen AK bestätigt habe.
Kein Scope über das Ticket hinaus; Ideen ins Backlog in docs/TASKS.md.
```

## Nach manuellem Test

```
Ergebnis <ID>: <was passiert ist, ggf. Log-Auszug>. Trage es in docs/TESTLOG.md ein
(Datum, Ticket, Ergebnis). Wenn AK erfüllt: Ticket done. Sonst: Ursache eingrenzen, Fix vorschlagen,
erst nach meinem Go ändern.
```

## API-Unsicherheit

```
Du hast eine Minecraft-/Meteor-/Baritone-/Litematica-API verwendet, die ich in refs/ nicht finde.
Zeige mir die Fundstelle (Datei + Zeile) in refs/ oder im Loom-Sourcejar. Wenn es keine gibt,
ist die Signatur erfunden – korrigiere sie.
```

## Review vor Phasenende

```
Phase <n> ist laut TASKS.md fertig. Prüfe gegen CLAUDE.md „Harte Regeln“ 1–7:
liste jede Datei, die eine Regel verletzt (Import aus baritone.* ohne api, fi.dy.masa.* außerhalb compat,
Paket-Aktion ohne ActionBudget, Break-Pfad ohne additiveOnly-Check). Behebe nur Regelverstöße,
keine Refactorings.
```

## Wenn der Stack sich ändert (neue Meteor/Baritone/Litematica-Version)

```
Neue Version: <Komponente> <Version>. Aktualisiere gradle/libs.versions.toml, ziehe refs/ neu
(git pull in refs/*), führe .sf doctor-Logik gedanklich durch: welche MethodHandles in
LitematicaAdapter könnten brechen? Build, Tests, dann Liste der nötigen Anpassungen – noch nichts umbauen.
```
