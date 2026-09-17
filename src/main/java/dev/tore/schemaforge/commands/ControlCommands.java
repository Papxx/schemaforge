package dev.tore.schemaforge.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tore.schemaforge.core.BuildSession;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.Optional;

/** {@code .sf pause}, {@code .sf resume} and {@code .sf stop} (P2-07): they only drive the running build. */
public final class ControlCommands {
    private ControlCommands() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> pause() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("pause").executes(_ -> {
            SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
            Optional<BuildSession> session = printer.session();
            if (session.isEmpty()) {
                printer.error("Nothing is being printed.");
                return 0;
            }
            if (!session.get().pause()) {
                printer.error("Cannot pause in state %s.", session.get().state());
                return 0;
            }
            printer.info("Paused. Continue with .sf resume.");
            return Command.SINGLE_SUCCESS;
        });
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> resume() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("resume").executes(_ -> {
            SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
            Optional<BuildSession> session = printer.session();
            if (session.isEmpty()) {
                printer.error("Nothing is being printed.");
                return 0;
            }
            if (!session.get().resume()) {
                printer.error("The build is not paused (state %s).", session.get().state());
                return 0;
            }
            printer.info("Resumed.");
            return Command.SINGLE_SUCCESS;
        });
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> stop() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("stop").executes(_ -> {
            SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
            if (!printer.isActive()) {
                printer.error("Nothing is being printed.");
                return 0;
            }
            printer.disable();
            printer.info("Stopped. The last status stays available with .sf status.");
            return Command.SINGLE_SUCCESS;
        });
    }
}
