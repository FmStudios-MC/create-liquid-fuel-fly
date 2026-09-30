package com.forsteri.createliquidfuel.mixin;

import com.forsteri.createliquidfuel.core.IHasStomach;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidInventoryProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Create Fly's pipes, spouts etc. find a block's fluid tank through {@link FluidInventoryProvider}
 * on the block (upstream: a NeoForge block capability). {@code getBlockEntityClass} comes from the
 * burner's {@code IBE}. The type argument is {@link SmartBlockEntity} so no bridge method is needed.
 */
@Mixin(BlazeBurnerBlock.class)
public abstract class BlazeBurnerBlockMixin implements FluidInventoryProvider<SmartBlockEntity> {
    @Override
    public @Nullable FluidInventory getFluidInventory(
            LevelAccessor world,
            BlockPos pos,
            BlockState state,
            SmartBlockEntity blockEntity,
            @Nullable Direction context
    ) {
        return blockEntity instanceof IHasStomach burner ? burner.createliquidfuel$getStomach() : null;
    }
}
