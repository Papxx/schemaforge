package dev.tore.schemaforge.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tore.schemaforge.core.ContainerIndex;
import dev.tore.schemaforge.core.ContainerReport;
import dev.tore.schemaforge.modules.ContainerRestock;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** {@code .sf containers} (P4-02): lists what the container index has learned, nearest first. */
public final class ContainersCommand {
    private static final String PREFIX = "SchemaForge";

    private ContainersCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("containers").executes(_ -> {
            Minecraft mc = MeteorClient.mc;
            if (mc.player == null) {
                ChatUtils.sendMsg(PREFIX, Component.literal("Join a world first.").withStyle(ChatFormatting.RED));
                return 0;
            }
            ContainerRestock module = Modules.get().get(ContainerRestock.class);
            ContainerIndex index = module.index();
            List<String> lines = ContainerReport.lines(index.entries(), mc.player.position(), module.isActive());
            print(lines);
            return Command.SINGLE_SUCCESS;
        });
    }

    private static void print(List<String> lines) {
        MutableComponent block = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            block.append(Component.literal((i == 0 ? "" : "\n") + line).withStyle(style(line)));
        }
        ChatUtils.sendMsg(PREFIX, block);
    }

    private static ChatFormatting style(String line) {
        if (line.contains("(stale)")) return ChatFormatting.YELLOW;
        if (line.startsWith("Container index")) return ChatFormatting.WHITE;
        return ChatFormatting.GRAY;
    }
}
