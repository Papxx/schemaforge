/*
 * This file is part of SchemaForge (https://github.com/Papxx/schemaforge).
 * Copyright (C) 2026 Papxx
 *
 * SchemaForge is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, version 3 of the License.
 *
 * SchemaForge is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SchemaForge.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
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
import net.minecraft.world.inventory.InventoryMenu;
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
        // The packet carries the encoded hit with accurate placement (P5-06); the rotation still looks at the real one.
        BlockHitResult hit = new BlockHitResult(plan.packetHitVec(), plan.clickFace(), plan.clickPos(), false);
        Rotations.rotate(plan.yaw(), plan.pitch(), ROTATION_PRIORITY, !rotationSpoof, () -> click(hit, hotbarSlot, plan.sneak()));
        return true;
    }

    /**
     * One {@code ContainerInput.SWAP} click via Meteor's {@code InvUtils.quickSwap()} (hotbar index as button, inventory
     * slot as target). Only while the player inventory menu is active, so slot ids cannot point into another container.
     */
    @Override
    public boolean swapToHotbar(int inventorySlot, int hotbarSlot) {
        if (mc.player == null || mc.gameMode == null || !(mc.player.containerMenu instanceof InventoryMenu)) return false;
        if (inventorySlot == hotbarSlot) return false;
        InvUtils.quickSwap().fromId(hotbarSlot).to(inventorySlot);
        return true;
    }

    /**
     * P5-03: {@code MultiPlayerGameMode.useItem} sends the use packet with the player's own rotation, and
     * {@code BucketItem.use} raycasts from it on both sides. With rotation spoofing (or as a later rotation of the same
     * tick) the player's rotation in the callback is not the planned one, so it is set for the duration of the use and
     * put back afterwards; Meteor's rotation queue handles camera and movement packets as for placing.
     */
    @Override
    public boolean useBucket(PlacementPlan plan, int hotbarSlot) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return false;
        Rotations.rotate(plan.yaw(), plan.pitch(), ROTATION_PRIORITY, !rotationSpoof, () -> use(plan, hotbarSlot));
        return true;
    }

    private void use(PlacementPlan plan, int hotbarSlot) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        if (!InvUtils.swap(hotbarSlot, false)) return;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        player.setYRot(plan.yaw());
        player.setXRot(plan.pitch());
        try {
            InteractionResult result = mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            if (result.consumesAction()) player.swing(InteractionHand.MAIN_HAND);
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
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
