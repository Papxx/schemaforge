package dev.tore.schemaforge.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tore.schemaforge.core.StatusReport;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** {@code .sf status} (P2-07, AK2): state, cluster, placed blocks, rate and mismatched blocks of the current run. */
public final class StatusCommand {
    private StatusCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("status").executes(_ -> {
            SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
            List<String> lines = StatusReport.lines(printer.lastStatus(), printer.session().isPresent());
            MutableComponent text = Component.empty();
            for (int i = 0; i < lines.size(); i++) {
                text.append(Component.literal((i == 0 ? "" : "\n") + lines.get(i)));
            }
            printer.info(text);
            return Command.SINGLE_SUCCESS;
        });
    }
}
