package dev.tore.schemaforge.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.tore.schemaforge.core.PlacementLog;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.registries.Registries;

import java.util.List;

/**
 * {@code .sf undo <n>} (P5-01): breaks the last n blocks SchemaForge placed, newest first.
 * Only blocks that still match the log are touched; the undo itself runs in the {@link SchemaPrinter} tick.
 */
public final class UndoCommand {
    public static final int MAX_UNDO = 4096;

    private UndoCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("undo")
            .then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("stop").executes(_ -> stop()))
            .then(RequiredArgumentBuilder.<ClientSuggestionProvider, Integer>argument("count", IntegerArgumentType.integer(1, MAX_UNDO))
                .executes(ctx -> run(IntegerArgumentType.getInteger(ctx, "count"))));
    }

    private static int run(int count) {
        Minecraft mc = MeteorClient.mc;
        SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
        if (mc.level == null || mc.player == null) {
            printer.error("Join a world first.");
            return 0;
        }
        if (printer.isActive()) {
            printer.error("Stop the build first (.sf stop); undoing while printing would fight the printer.");
            return 0;
        }

        List<PlacementLog.Entry> entries = PlacementLog.readFrom(
            printer.placementLogFile(), mc.level.holderLookup(Registries.BLOCK));
        if (entries.isEmpty()) {
            printer.error("Nothing in the placement log for '%s'.", printer.placementSetting().get());
            return 0;
        }

        int planned = Math.min(count, entries.size());
        if (!printer.startUndo(entries, planned)) {
            printer.error("An undo is already running (.sf undo stop).");
            return 0;
        }
        printer.info("Undoing the last %d block%s. Stop with .sf undo stop.", planned, planned == 1 ? "" : "s");
        return Command.SINGLE_SUCCESS;
    }

    private static int stop() {
        SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
        if (printer.undo().isEmpty()) {
            printer.error("No undo is running.");
            return 0;
        }
        printer.info("Undo stopped after %d block%s.", printer.undo().get().removedCount(),
            printer.undo().get().removedCount() == 1 ? "" : "s");
        printer.cancelUndo();
        return Command.SINGLE_SUCCESS;
    }
}
