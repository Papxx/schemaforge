package dev.tore.schemaforge.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.tore.schemaforge.compat.LitematicaAdapter;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Optional;

/**
 * {@code .sf start [placement]} (P2-07): picks the placement, writes it to the module setting and (re)starts the
 * SchemaPrinter module. Everything else happens in the module's activation.
 */
public final class StartCommand {
    private StartCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("start")
            .executes(_ -> run(Optional.empty()))
            .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("placement", StringArgumentType.greedyString())
                .suggests((_, builder) -> SharedSuggestionProvider.suggest(LitematicaAdapter.placementNames().stream().distinct(), builder))
                .executes(ctx -> run(Optional.of(StringArgumentType.getString(ctx, "placement")))));
    }

    private static int run(Optional<String> requested) {
        SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
        return switch (PlacementChoice.resolve(requested, ".sf start")) {
            case PlacementChoice.Result.Error(String message) -> {
                printer.error(message);
                yield 0;
            }
            case PlacementChoice.Result.Chosen chosen -> {
                if (chosen.duplicates() > 1) {
                    printer.warning("%d placements are named '%s'; printing the first one.", chosen.duplicates(), chosen.name());
                }
                printer.placementSetting().set(chosen.name());
                // A running build is replaced by the new one.
                printer.disable();
                printer.enable();
                yield Command.SINGLE_SUCCESS;
            }
        };
    }
}
