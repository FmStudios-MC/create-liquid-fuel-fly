package com.forsteri.createliquidfuel.network;

import com.forsteri.createliquidfuel.CreateLiquidFuel;
import com.forsteri.createliquidfuel.core.LiquidFuel;
import com.forsteri.createliquidfuel.core.LiquidFuels;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends the fuel table to clients, as upstream's synced data map did. Clients need it to predict
 * right-clicking a burner with a fuel bucket.
 */
public record LiquidFuelSyncPayload(Map<Identifier, LiquidFuel> fuels) implements CustomPacketPayload {
    public static final Type<LiquidFuelSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CreateLiquidFuel.MOD_ID, "sync_fuels"));

    public static final StreamCodec<ByteBuf, LiquidFuelSyncPayload> STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, Identifier, LiquidFuel, Map<Identifier, LiquidFuel>>map(
                    HashMap::new, Identifier.STREAM_CODEC, LiquidFuel.STREAM_CODEC
            ).map(LiquidFuelSyncPayload::new, LiquidFuelSyncPayload::fuels);

    public static LiquidFuelSyncPayload fromServer() {
        Map<Identifier, LiquidFuel> fuels = new HashMap<>();
        LiquidFuels.all().forEach((fluid, fuel) -> fuels.put(BuiltInRegistries.FLUID.getKey(fluid), fuel));
        return new LiquidFuelSyncPayload(fuels);
    }

    public void apply() {
        Map<Fluid, LiquidFuel> resolved = new HashMap<>();
        fuels.forEach((id, fuel) -> BuiltInRegistries.FLUID.getOptional(id).ifPresent(fluid -> resolved.put(fluid, fuel)));
        LiquidFuels.set(resolved);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
