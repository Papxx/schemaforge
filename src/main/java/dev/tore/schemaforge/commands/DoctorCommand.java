package dev.tore.schemaforge.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.compat.ProbeReport;
import dev.tore.schemaforge.compat.ProbeReport.Line;
import dev.tore.schemaforge.compat.ProbeReport.Section;
import dev.tore.schemaforge.compat.ProbeReport.Status;
import dev.tore.schemaforge.compat.VersionProbe;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code .sf doctor}: runs {@link VersionProbe} and prints the report as one chat block per section,
 * each line coloured by status. The full report is also written to the log (P0-05).
 */
public final class DoctorCommand {
    private static final String PREFIX = "SchemaForge";

    private DoctorCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("doctor").executes(_ -> {
            print(VersionProbe.run());
            return Command.SINGLE_SUCCESS;
        });
    }

    private static void print(ProbeReport report) {
        long problems = report.problemCount();
        ChatUtils.sendMsg(PREFIX, Component.literal(problems == 0 ? "Doctor: all checks OK" : "Doctor: " + problems + " problem(s)")
            .withStyle(color(report.status())));

        for (Section section : report.sections()) {
            MutableComponent block = Component.literal(section.title()).withStyle(ChatFormatting.BOLD, color(section.status()));
            for (Line line : section.lines()) {
                block.append(Component.literal("\n " + symbol(line.status()) + " ").withStyle(color(line.status())));
                block.append(Component.literal(line.label() + ": ").withStyle(ChatFormatting.GRAY));
                block.append(Component.literal(line.value()).withStyle(color(line.status())));
                SchemaForgeAddon.LOG.info("doctor [{}] {} | {}: {}", line.status(), section.title(), line.label(), line.value());
            }
            ChatUtils.sendMsg(PREFIX, block);
        }
    }

    private static ChatFormatting color(Status status) {
        return switch (status) {
            case OK -> ChatFormatting.GREEN;
            case MISSING -> ChatFormatting.YELLOW;
            case FAIL -> ChatFormatting.RED;
        };
    }

    private static String symbol(Status status) {
        return switch (status) {
            case OK -> "✔";      // ✔
            case MISSING -> "?";
            case FAIL -> "✘";    // ✘
        };
    }
}
