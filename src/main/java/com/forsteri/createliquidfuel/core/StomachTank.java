package com.forsteri.createliquidfuel.core;

import com.zurrtum.create.foundation.fluid.FluidTank;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The blaze burner's fuel tank: holds one bucket, and only fluids listed as fuel. */
public class StomachTank extends FluidTank {
    public static final int CAPACITY = 1000 * LiquidFuels.MB;

    private final BlockEntity owner;

    public StomachTank(BlockEntity owner) {
        super(CAPACITY);
        this.owner = owner;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean isValid(int slot, FluidStack stack) {
        return LiquidFuels.isFuel(stack.getFluid());
    }

    @Override
    public void markDirty() {
        owner.setChanged();
    }
}
