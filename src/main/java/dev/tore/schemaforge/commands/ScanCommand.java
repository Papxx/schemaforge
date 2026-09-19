package dev.tore.schemaforge.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.tore.schemaforge.compat.BaritoneBridge;
import dev.tore.schemaforge.core.ScanSession;
import dev.tore.schemaforge.modules.ContainerRestock;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.Optional;

/**
 * {@code .sf scan [radius]} (P4-03): walks every container in the radius, opens it and lets the passive
 * learning record it. {@code .sf scan stop} cancels a running scan.
 */
public final class ScanCommand {
    public static final int DEFAULT_RADIUS = 32;

    private ScanCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("scan")
            .executes(_ -> run(DEFAULT_RADIUS))
            .then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("stop").executes(_ -> stop()))
            .then(RequiredArgumentBuilder.<ClientSuggestionProvider, Integer>argument("radius", IntegerArgumentType.integer(1, 128))
                .executes(ctx -> run(IntegerArgumentType.getInteger(ctx, "radius"))));
    }

    private static int run(int radius) {
        ContainerRestock module = Modules.get().get(ContainerRestock.class);
        if (MeteorClient.mc.player == null) {
            module.error("Join a world first.");
            return 0;
        }
        if (!module.isActive()) {
            module.error("Turn on container-restock first; the scan learns through it.");
            return 0;
        }
        if (!BaritoneBridge.isPresent()) {
            module.warning("Baritone is missing; only containers already within reach are opened.");
        }

        Optional<Integer> started = module.startScan(radius, note -> module.warning("%s", note));
        if (started.isEmpty()) {
            module.error("A scan is already running (.sf scan stop).");
            return 0;
        }
        if (started.get() == 0) {
            module.info("No containers found within %d blocks of loaded chunks.", radius);
            return Command.SINGLE_SUCCESS;
        }
        module.info("Scanning %d container%s within %d blocks. Stop with .sf scan stop.",
            started.get(), started.get() == 1 ? "" : "s", radius);
        return Command.SINGLE_SUCCESS;
    }

    private static int stop() {
        ContainerRestock module = Modules.get().get(ContainerRestock.class);
        Optional<ScanSession> scan = module.scan();
        if (scan.isEmpty() || scan.get().state() == ScanSession.State.DONE) {
            module.error("No scan is running.");
            return 0;
        }
        module.info("Scan stopped after %d of %d containers.", scan.get().learnedCount(), scan.get().total());
        module.cancelScan();
        return Command.SINGLE_SUCCESS;
    }
}
