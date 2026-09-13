package dev.tore.schemaforge.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * Root of {@code .sf <sub>}; every sub-command lives in its own class added by its ticket.
 * Shell from P0-04; the first sub-command ({@code doctor}) and registration are implemented in P0-05.
 */
public final class SfCommand extends Command {
    public SfCommand() {
        super("sf", "SchemaForge commands.");
    }

    /** Implemented in P0-05. */
    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        throw new UnsupportedOperationException("P0-05");
    }
}
