package com.forsteri.createliquidfuel;

import com.forsteri.createliquidfuel.core.IHasStomach;
import com.forsteri.createliquidfuel.core.LiquidFuelLoader;
import com.forsteri.createliquidfuel.network.LiquidFuelSyncPayload;
import com.zurrtum.create.AllBlockEntityTypes;
import com.zurrtum.create.AllTransfer;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.CachedFluidInventoryBehaviour;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateLiquidFuel implements ModInitializer {
    public static final String MOD_ID = "createliquidfuel";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(LiquidFuelLoader.ID, new LiquidFuelLoader());

        PayloadTypeRegistry.clientboundPlay().register(LiquidFuelSyncPayload.TYPE, LiquidFuelSyncPayload.STREAM_CODEC);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            if (ServerPlayNetworking.canSend(player, LiquidFuelSyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, LiquidFuelSyncPayload.fromServer());
            }
        });

        // Create Fly's own pipes find the stomach through BlazeBurnerBlockMixin (FluidInventoryProvider).
        // Other mods' pipes go through Fabric's transfer API, registered the way Create Fly's AllTransfer does it.
        if (!AllTransfer.DISABLE) {
            BlockEntityBehaviour.add(AllBlockEntityTypes.HEATER, be -> new CachedFluidInventoryBehaviour<>(
                    be, burner -> ((IHasStomach) burner).createliquidfuel$getStomach()));
            FluidStorage.SIDED.registerForBlockEntity(CachedFluidInventoryBehaviour::get, AllBlockEntityTypes.HEATER);
        }
    }
}
