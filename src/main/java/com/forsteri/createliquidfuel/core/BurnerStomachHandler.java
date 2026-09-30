package com.forsteri.createliquidfuel.core;

import com.forsteri.createliquidfuel.mixin.BlazeBurnerAccessor;
import com.zurrtum.create.content.fluids.transfer.GenericItemEmptying;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class BurnerStomachHandler {
    /**
     * Runs at the end of the burner's server tick. That point is only reached once the burner's
     * burn time has run out, so {@code amountConsumedPerTick} is used up per refuel, not per tick.
     */
    public static void tick(BlazeBurnerBlockEntity burner) {
        StomachTank stomach = ((IHasStomach) burner).createliquidfuel$getStomach();
        FluidStack contents = stomach.getFluid();
        if (contents.isEmpty()) return;

        LiquidFuel fuel = LiquidFuels.get(contents.getFluid());
        if (fuel == null) return;

        int consumed = fuel.amountConsumedPerTick() * LiquidFuels.MB;

        if (contents.getAmount() < consumed) {
            stomach.setFluid(FluidStack.EMPTY);
            stomach.markDirty();
            return;
        }

        BlazeBurnerAccessor accessor = (BlazeBurnerAccessor) burner;
        accessor.createliquidfuel$invokeSetBlockHeat(fuel.superHeat()
                ? BlazeBurnerBlock.HeatLevel.SEETHING
                : BlazeBurnerBlock.HeatLevel.FADING);

        int newBurnTime = accessor.createliquidfuel$getRemainingBurnTime() + fuel.burnTime();
        if (newBurnTime > BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY) {
            return;
        }
        accessor.createliquidfuel$setRemainingBurnTime(newBurnTime);

        if (contents.getAmount() == consumed) {
            stomach.setFluid(FluidStack.EMPTY);
        } else {
            contents.decrement(consumed);
        }
        stomach.markDirty();
    }

    /**
     * Fluid containers holding a fuel (a lava bucket, say) go into the stomach instead of being
     * burnt as an item. The burner block turns the item into its crafting remainder afterwards.
     */
    public static void tryUpdateFuel(BlazeBurnerBlockEntity burner, ItemStack itemStack, boolean forceOverflow, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        Level level = burner.getLevel();
        if (level == null || itemStack.isEmpty()) return;
        if (!GenericItemEmptying.canItemBeEmptied(level, itemStack)) return;

        FluidStack fluidStack = GenericItemEmptying.emptyItem(level, itemStack.copy(), true).getFirst();
        if (fluidStack.isEmpty() || !LiquidFuels.isFuel(fluidStack.getFluid())) return;

        StomachTank stomach = ((IHasStomach) burner).createliquidfuel$getStomach();
        FluidStack contents = stomach.getFluid();
        if (!contents.isEmpty() && !FluidStack.areFluidsAndComponentsEqualIgnoreCapacity(contents, fluidStack)) return;

        int space = stomach.getCapacity() - contents.getAmount();
        if (space <= 0) return;
        if (fluidStack.getAmount() > space && !forceOverflow) return;

        if (!simulate) {
            int amount = Math.min(fluidStack.getAmount(), space);
            if (contents.isEmpty()) {
                stomach.setFluid(fluidStack.copyWithAmount(amount));
            } else {
                contents.increment(amount);
            }
            stomach.markDirty();
        }

        cir.setReturnValue(true);
    }
}
