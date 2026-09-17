package dev.tore.schemaforge.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.compat.LitematicaAdapter;
import dev.tore.schemaforge.compat.McWorldView;
import dev.tore.schemaforge.core.Cluster;
import dev.tore.schemaforge.core.PlanConfig;
import dev.tore.schemaforge.core.PreviewReport;
import dev.tore.schemaforge.core.SchematicSnapshot;
import dev.tore.schemaforge.core.WorkPlanner;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Optional;

/**
 * {@code .sf preview [placement]} (P1-05): snapshots the placement, plans it with the default {@link PlanConfig}
 * against the current world and prints a {@link PreviewReport}. Without an argument the only loaded placement is used;
 * with several, their names are listed. Read-only: sends no packets.
 */
public final class PreviewCommand {
    private static final String PREFIX = "SchemaForge";

    private PreviewCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("preview")
            .executes(_ -> run(Optional.empty()))
            .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("placement", StringArgumentType.greedyString())
                .suggests((_, builder) -> SharedSuggestionProvider.suggest(LitematicaAdapter.placementNames().stream().distinct(), builder))
                .executes(ctx -> run(Optional.of(StringArgumentType.getString(ctx, "placement")))));
    }

    private static int run(Optional<String> requested) {
        Minecraft mc = MeteorClient.mc;
        if (mc.level == null || mc.player == null) return error("Join a world first.");
        PlacementChoice.Result choice = PlacementChoice.resolve(requested, ".sf preview");
        if (choice instanceof PlacementChoice.Result.Error(String message)) return error(message);
        PlacementChoice.Result.Chosen chosen = (PlacementChoice.Result.Chosen) choice;
        String name = chosen.name();

        // Litematica allows equal names, but snapshot() can only address the first placement with a name.
        if (chosen.duplicates() > 1) {
            ChatUtils.sendMsg(PREFIX, Component.literal(chosen.duplicates() + " placements are named '" + name
                + "'; showing the first one. Rename them in Litematica to preview the others.").withStyle(ChatFormatting.YELLOW));
        }

        Optional<SchematicSnapshot> snapshot = LitematicaAdapter.snapshot(name);
        if (snapshot.isEmpty()) {
            return error("Could not read placement '" + name + "' (disabled, no enabled sub-region or Litematica incompatible; see log).");
        }

        SchematicSnapshot snap = snapshot.get();
        List<Cluster> clusters = new WorkPlanner(PlanConfig.defaults()).plan(snap, new McWorldView(mc.level), mc.player.blockPosition());
        PreviewReport report = PreviewReport.of(snap, clusters, new PreviewReport.Environment(
            mc.level.getMinY(), mc.level.getMaxY(), mc.options.getEffectiveRenderDistance(), item -> InvUtils.find(item).count()));
        print(report);
        return Command.SINGLE_SUCCESS;
    }

    private static void print(PreviewReport report) {
        MutableComponent block = Component.empty();
        List<PreviewReport.Line> lines = report.lines();
        for (int i = 0; i < lines.size(); i++) {
            PreviewReport.Line line = lines.get(i);
            block.append(Component.literal((i == 0 ? "" : "\n") + line.text()).withStyle(style(line.level())));
            SchemaForgeAddon.LOG.info("preview [{}] {}", line.level(), line.text());
        }
        ChatUtils.sendMsg(PREFIX, block);
    }

    private static ChatFormatting[] style(PreviewReport.Level level) {
        return switch (level) {
            case HEADER -> new ChatFormatting[]{ChatFormatting.BOLD, ChatFormatting.WHITE};
            case INFO -> new ChatFormatting[]{ChatFormatting.GRAY};
            case WARNING -> new ChatFormatting[]{ChatFormatting.YELLOW};
        };
    }

    private static int error(String message) {
        ChatUtils.sendMsg(PREFIX, Component.literal(message).withStyle(ChatFormatting.RED));
        return 0;
    }
}
