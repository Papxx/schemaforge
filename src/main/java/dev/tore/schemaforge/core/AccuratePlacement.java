package dev.tore.schemaforge.core;

import dev.tore.schemaforge.compat.EasyPlaceProtocol;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Accurate block placement ("EasyPlace protocol", P5-06): the wanted block state travels to the server in the x
 * coordinate of the hit vector, so rotation-dependent blocks need no real rotation. Layout read from Litematica 0.28.8
 * (see ARCHITECTURE.md §4) and implemented independently. Only servers with Carpet, Servux or a matching plugin decode
 * it; the caller decides whether the protocol is available.
 */
public final class AccuratePlacement {
    /** Properties V3 transmits, as in Litematica's {@code PlacementHandler.WHITELISTED_PROPERTIES}. */
    private static final Set<Property<?>> V3_PROPERTIES = Set.of(
        BlockStateProperties.INVERTED, BlockStateProperties.OPEN, BlockStateProperties.BELL_ATTACHMENT,
        BlockStateProperties.AXIS, BlockStateProperties.HALF, BlockStateProperties.ATTACH_FACE,
        BlockStateProperties.CHEST_TYPE, BlockStateProperties.MODE_COMPARATOR, BlockStateProperties.DOOR_HINGE,
        BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING_HOPPER,
        BlockStateProperties.ORIENTATION, BlockStateProperties.RAIL_SHAPE, BlockStateProperties.RAIL_SHAPE_STRAIGHT,
        BlockStateProperties.SLAB_TYPE, BlockStateProperties.STAIRS_SHAPE, BlockStateProperties.COPPER_GOLEM_POSE,
        BlockStateProperties.BITES, BlockStateProperties.DELAY, BlockStateProperties.NOTE, BlockStateProperties.ROTATION_16);

    private AccuratePlacement() {
    }

    /** The hit vector to send; unchanged for {@link EasyPlaceProtocol#NONE} or when there is nothing to encode. */
    public static Vec3 encode(EasyPlaceProtocol proto, BlockState state, Vec3 hit) {
        return switch (proto) {
            case NONE -> hit;
            case V2_CARPET -> v2(state, hit);
            case V3_SERVUX -> v3(state, hit);
        };
    }

    /**
     * True if the protocol carries the property a real rotation would otherwise have to produce (facing, sign
     * rotation, rail shape). Doors stay out of V2: vanilla derives the hinge from {@code hit.x - pos.x}, which the
     * encoding moves beyond 2, and V2 does not transmit the hinge itself.
     */
    public static boolean carriesRotation(EasyPlaceProtocol proto, BlockState state) {
        return switch (proto) {
            case NONE -> false;
            case V2_CARPET -> firstDirectionProperty(state).isPresent() && !(state.getBlock() instanceof DoorBlock);
            case V3_SERVUX -> firstDirectionProperty(state).filter(p -> p != BlockStateProperties.VERTICAL_DIRECTION).isPresent()
                || state.hasProperty(BlockStateProperties.ROTATION_16)
                || state.hasProperty(BlockStateProperties.RAIL_SHAPE)
                || state.hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT);
        };
    }

    /** Carpet's protocol: facing (or axis) plus 16 for a top half, a subtracting comparator or 16 per repeater delay. */
    private static Vec3 v2(BlockState state, Vec3 hit) {
        Optional<EnumProperty<Direction>> facing = firstDirectionProperty(state);
        int code = 0;
        boolean oriented = false;
        if (facing.isPresent()) {
            code = state.getValue(facing.get()).get3DDataValue();
            oriented = true;
        } else if (state.hasProperty(BlockStateProperties.AXIS)) {
            code = state.getValue(BlockStateProperties.AXIS).ordinal();
            oriented = true;
        }
        if (state.getBlock() instanceof RepeaterBlock) {
            code += state.getValue(RepeaterBlock.DELAY) * 16;
        } else if (state.getBlock() instanceof ComparatorBlock) {
            if (state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT) code += 16;
        } else if (state.hasProperty(BlockStateProperties.HALF)) {
            if (state.getValue(BlockStateProperties.HALF) == Half.TOP) code += 16;
        } else if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            if (state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP) code += 16;
        }
        if (code == 0 && !oriented) return hit;
        return new Vec3(hit.x + code * 2 + 2, hit.y, hit.z);
    }

    /** Litematica's own protocol: every whitelisted property as a bit field, the facing first. */
    private static Vec3 v3(BlockState state, Vec3 hit) {
        int value = 0;
        int shift = 1;
        int encoded = 0;
        Optional<EnumProperty<Direction>> facing = firstDirectionProperty(state)
            .filter(p -> p != BlockStateProperties.VERTICAL_DIRECTION);
        if (facing.isPresent()) {
            value |= state.getValue(facing.get()).get3DDataValue() << shift;
            shift += 3;
            encoded++;
        }
        List<Property<?>> properties = new ArrayList<>(state.getProperties());
        properties.sort(Comparator.comparing(Property::getName));
        for (Property<?> property : properties) {
            if (facing.isPresent() && facing.get().equals(property)) continue;
            if (!V3_PROPERTIES.contains(property)) continue;
            int index = sortedIndex(property, state);
            if (index == -1) continue;
            value |= index << shift;
            shift += Mth.log2(Mth.smallestEncompassingPowerOfTwo(property.getPossibleValues().size()));
            encoded++;
        }
        if (encoded == 0) return hit;
        return new Vec3(hit.x + 2 + value, hit.y, hit.z);
    }

    private static <T extends Comparable<T>> int sortedIndex(Property<T> property, BlockState state) {
        List<T> values = new ArrayList<>(property.getPossibleValues());
        values.sort(Comparator.naturalOrder());
        return values.indexOf(state.getValue(property));
    }

    /** First enum property holding a {@link Direction}, in the state's own property order (as MaLiLib picks it). */
    @SuppressWarnings("unchecked")
    static Optional<EnumProperty<Direction>> firstDirectionProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof EnumProperty<?> e && e.getValueClass().equals(Direction.class)) {
                return Optional.of((EnumProperty<Direction>) e);
            }
        }
        return Optional.empty();
    }
}
