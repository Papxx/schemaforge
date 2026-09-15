# Notiz: Dev-Client-Absturz mit Litematica/MaLiLib (untersucht 2026-09-15)

## Symptom

`./gradlew runClient` mit Litematica 0.28.8 + MaLiLib 0.29.6 in `run/mods/` stürzt nach ~4 s ab, noch vor dem ersten gerenderten Frame (Client ticks: 0, erster Resource-Reload läuft noch):

```
java.lang.IllegalStateException: Missing uniform Globals (should be UNIFORM_BUFFER)
	at com.mojang.blaze3d.opengl.GlCommandEncoder.trySetup(GlCommandEncoder.java:536)
	...
	at net.minecraft.client.renderer.texture.SpriteContents$AnimationState.drawToAtlas(SpriteContents.java:382)
	at net.minecraft.client.renderer.texture.TextureAtlas.uploadAnimationFrames(TextureAtlas.java:242)
	at net.minecraft.client.renderer.texture.TextureManager.tick(TextureManager.java:111)
	at net.minecraft.client.Minecraft.runTick(Minecraft.java:1243)
```

Crash-Reports: `run/crash-reports/crash-2026-09-13_16.4*.txt`, `crash-2026-09-15_19.37.41-client.txt`, `crash-2026-09-15_19.39.30-client.txt`.

## Ergebnis

**Ursache ist MaLiLib, nicht Litematica, nicht SchemaForge und nicht der AMD-Treiber.** MaLiLib schaltet im Dev-Umfeld Mojangs interne GPU-Validierung ein, und Minecraft 26.2 scheitert dann an seiner eigenen Prüfung.

Kette:

1. **MaLiLib** `fi.dy.masa.malilib.mixin.test.MixinSharedConstants` (in `mixins.malilib.json`, Abschnitt `mixins`) setzt am Ende von `SharedConstants.<clinit>` das Vanilla-Flag `SharedConstants.IS_RUNNING_IN_IDE = MaLiLibReference.RUNNING_IN_IDE`.
2. `MaLiLibReference.isRunningInIde()` ist `true`, sobald `-Dfabric.development=true` gesetzt ist (Loom setzt das in `.gradle/loom-cache/launch.cfg`) oder `sun.java.command` den `net.fabricmc.devlaunchinjector` enthält – beides beim `runClient` immer der Fall. Einen Schalter zum Abschalten gibt es nicht (`MaLiLibMixinConfigPlugin` filtert nichts).
3. **Vanilla** `GlRenderPass.VALIDATION = SharedConstants.IS_RUNNING_IN_IDE`. Nur wenn das `true` ist, prüft `GlCommandEncoder.trySetup` vor jedem Draw, dass jede Uniform aus den Bind-Group-Layouts der Pipeline gesetzt ist.
4. **Vanilla** `RenderPipelines.ANIMATE_SPRITE_SNIPPET` baut auf `GLOBALS_SNIPPET` auf, verlangt also `Globals`. `TextureAtlas.uploadAnimationFrames` ruft `RenderSystem.bindDefaultUniforms(pass)`, das `Globals` nur setzt, wenn `RenderSystem.getGlobalSettingsUniform() != null`. Den Puffer legt erst `GlobalSettingsUniform.update()` beim Rendern des ersten Frames in `GameRenderer` an.
5. `Minecraft.runTick` tickt `textureManager` **vor** dem Rendern. Ist der erste Resource-Reload schon fertig, bevor ein Frame gerendert wurde (hier: Meteor/MaLiLib/Litematica-Init blockiert den Render-Thread ~3 s, währenddessen läuft der Reload im Hintergrund fertig), werden animierte Atlanten ohne `Globals` gezeichnet → mit Validierung `IllegalStateException`, ohne Validierung wird der Draw einfach ohne die Uniform ausgeführt.

Kurz: latente Reihenfolge-Schwäche in Vanilla 26.2, die nur mit Validierung auffällt; MaLiLib erzwingt die Validierung im Dev-Client.

## Belege (Läufe vom 2026-09-15, Logs im Scratchpad der Session)

| Lauf | `run/mods` | Ergebnis |
|---|---|---|
| 1 | Baritone, Litematica, MaLiLib | Absturz wie oben (reproduziert) |
| 2 | Baritone, MaLiLib (Litematica deaktiviert) | gleicher Absturz → Litematica unbeteiligt |
| 3 | Baritone, Litematica, MaLiLib **ohne** `test.MixinSharedConstants` | kein Absturz; alle 13 Atlanten erstellt, Titelbildschirm, Client läuft stabil weiter |
| 13.09. | Baritone allein | kein Absturz (TESTLOG P0-05) |

Quellen gelesen per `javap -c` bzw. Vineflower 1.11.1 (dekompiliert, nur zum Lesen): `GlCommandEncoder.trySetup`, `GlRenderPass.VALIDATION`, `RenderPipelines`, `RenderPipeline$Builder`, `TextureAtlas.uploadAnimationFrames`, `RenderSystem.bindDefaultUniforms`, `GlobalSettingsUniform`, `Minecraft.runTick`, MaLiLib `MixinSharedConstants`, `MaLiLibReference`, `MaLiLibMixinConfigPlugin`, `MixinRenderPipelines`, `MixinBindGroupLayouts`.

Ausgeschlossen: Meteors `GlCommandEncoderMixin`/`GlDeviceMixin`/`RenderPipelineMixin` (nur Scissor/Line-Smooth/Feld), MaLiLibs `MixinRenderPipelines`/`MixinBindGroupLayouts` (registrieren nur eigene Pipelines; Vanilla-Builder kopiert Snippet-Listen), Grafiktreiber (Validierungsfehler entsteht vor jedem GL-Aufruf).

Nach dem Workaround lief der Client stabil durch Weltgenerierung, zweimaliges Betreten der Welt und `.sf doctor` (alle Zeilen OK).

## Nicht verwechseln: Watchdog-Report beim Beenden

`crash-2026-09-15_19.52.35-client.txt` („Watchdog (Client shutdown from post-main)“) ist ein anderes Thema: Das Spiel hatte die Welt schon gespeichert und sich beendet, aber die JVM lief > 15 s weiter, weil Baritones `CachedWorld`-Threadpool (`pool-4-thread-1..3`, nicht-daemon) offen blieb. Alle übrigen Threads waren Daemons. Kein Datenverlust, kein SchemaForge-Code beteiligt.

## Folgen

- **Normale Launcher-Instanzen sind nicht betroffen:** Dort ist weder `fabric.development` gesetzt noch der Dev-Launch-Injector aktiv, MaLiLib lässt das Flag auf `false`. (Nicht selbst getestet – es gibt keine Nicht-Dev-Instanz in diesem Setup.)
- **SchemaForge muss nichts ändern.** Kein Code-Fix, kein Mixin (harte Regel 4 bleibt unberührt).

## Workaround für den Dev-Client

```
python tools/patch_malilib_dev.py            # run/mods: malilib-*-devpatch.jar ohne test.MixinSharedConstants, Original → *.jar.disabled
python tools/patch_malilib_dev.py --restore  # zurück zum Original
```

Betrifft nur den lokalen, gitignorten `run/`-Ordner. Die gepatchte Jar nie weitergeben. Nebenwirkung: MaLiLibs übrige `test.*`-Mixins bleiben aktiv; Vanilla-Debug-Features, die an `IS_RUNNING_IN_IDE` hängen, sind im Dev-Client aus (wie in Vanilla ohne MaLiLib).

Bei einem MaLiLib-Update: Skript erneut ausführen; bricht es mit „not found“ ab, hat MaLiLib den Mixin entfernt oder umbenannt → Absturz neu prüfen. Kandidat für einen Upstream-Hinweis an MaLiLib (github.com/maruohon/malilib/issues) – nicht gemeldet.
