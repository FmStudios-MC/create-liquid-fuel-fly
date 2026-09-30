package com.forsteri.createliquidfuel.mixin;

import com.forsteri.createliquidfuel.core.BurnerStomachHandler;
import com.forsteri.createliquidfuel.core.IHasStomach;
import com.forsteri.createliquidfuel.core.StomachTank;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the blaze burner a fuel tank (upstream: {@code MixinBlazeBurnerTileEntity}). Upstream
 * created the tank by overriding {@code addBehaviours}; here it is created in the constructor so
 * Create's own {@code addBehaviours} is left alone.
 */
@Mixin(BlazeBurnerBlockEntity.class)
public abstract class BlazeBurnerBlockEntityMixin implements IHasStomach {
    @Unique
    private StomachTank createliquidfuel$stomach;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void createliquidfuel$createStomach(BlockPos pos, BlockState state, CallbackInfo ci) {
        createliquidfuel$stomach = new StomachTank((BlazeBurnerBlockEntity) (Object) this);
    }

    @Override
    public StomachTank createliquidfuel$getStomach() {
        return createliquidfuel$stomach;
    }

    // TAIL is the last return: reached on the server only, once the current burn time has run out.
    @Inject(method = "tick", at = @At("TAIL"))
    private void createliquidfuel$tick(CallbackInfo ci) {
        BurnerStomachHandler.tick((BlazeBurnerBlockEntity) (Object) this);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createliquidfuel$read(ValueInput view, boolean clientPacket, CallbackInfo ci) {
        createliquidfuel$stomach.read(view.childOrEmpty("Stomach"));
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void createliquidfuel$write(ValueOutput view, boolean clientPacket, CallbackInfo ci) {
        createliquidfuel$stomach.write(view.child("Stomach"));
    }

    @Inject(method = "tryUpdateFuel", at = @At("HEAD"), cancellable = true)
    private void createliquidfuel$tryUpdateFuel(ItemStack itemStack, boolean forceOverflow, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        BurnerStomachHandler.tryUpdateFuel((BlazeBurnerBlockEntity) (Object) this, itemStack, forceOverflow, simulate, cir);
    }
}
