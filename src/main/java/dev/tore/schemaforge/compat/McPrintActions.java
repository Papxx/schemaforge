package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.core.PlacementPlan;
import dev.tore.schemaforge.core.view.PrintActions;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@link PrintActions} for the local player (P2-03); client thread only.
 * The rotation goes through Meteor's {@link Rotations} queue, so the click runs in its callback after the movement
 * packet with the new look direction is sent. Sneaking is not done via {@code BlockUtils.interact} (which releases
 * sneak, see docs/NOTES-meteor-api.md): if the server does not already see the player sneaking, one input packet with
 * shift goes out before the click and one with the previous input after it, and the local key state is switched for
 * the same span so the client-side prediction matches the server.
 */
public final class McPrintActions implements PrintActions {
    /** Above Meteor's default 0, so a module rotating with default priority in the same tick does not win. */
    private static final int ROTATION_PRIORITY = 50;

    private final Minecraft mc;
    private final boolean rotationSpoof;

    /** @param rotationSpoof true: only the server sees the rotation; false: the camera turns as well */
    public McPrintActions(Minecraft mc, boolean rotationSpoof) {
        this.mc = mc;
        this.rotationSpoof = rotationSpoof;
    }

    @Override
    public boolean place(PlacementPlan plan, int hotbarSlot) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return false;
        BlockHitResult hit = new BlockHitResult(plan.hitVec(), plan.clickFace(), plan.clickPos(), false);
        Rotations.rotate(plan.yaw(), plan.pitch(), ROTATION_PRIORITY, !rotationSpoof, () -> click(hit, hotbarSlot, plan.sneak()));
        return true;
    }

    private void click(BlockHitResult hit, int hotbarSlot, boolean sneak) {
        LocalPlayer player = mc.player;
        ClientPacketListener connection = mc.getConnection();
        if (player == null || mc.gameMode == null || connection == null) return;
        if (!InvUtils.swap(hotbarSlot, false)) return;

        Input sent = player.getLastSentInput();
        Input keys = player.input.keyPresses;
        boolean pressShift = sneak && !sent.shift();
        if (pressShift) {
            connection.send(new ServerboundPlayerInputPacket(withShift(sent)));
            player.input.keyPresses = withShift(keys);
        }
        try {
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) player.swing(InteractionHand.MAIN_HAND);
        } finally {
            if (pressShift) {
                player.input.keyPresses = keys;
                connection.send(new ServerboundPlayerInputPacket(sent));
            }
        }
    }

    private static Input withShift(Input input) {
        return new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), true, input.sprint());
    }
}
