package dev.tore.schemaforge.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * Root of {@code .sf <sub>}; every sub-command lives in its own class added by its ticket.
 * Registered in P0-05 together with the first sub-command ({@link DoctorCommand}).
 */
public final class SfCommand extends Command {
    public SfCommand() {
        super("sf", "SchemaForge commands.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(DoctorCommand.build());
    }
}
