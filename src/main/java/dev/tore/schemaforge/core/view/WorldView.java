package dev.tore.schemaforge.core.view;

import dev.tore.schemaforge.core.ContainerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Read-only world access for testable core logic. First consumer and test fake: P1-03 (WorkPlanner). */
public interface WorldView {
    BlockState getBlockState(BlockPos pos);

    boolean isChunkLoaded(BlockPos pos);

    Optional<ContainerType> containerAt(BlockPos pos);
}
