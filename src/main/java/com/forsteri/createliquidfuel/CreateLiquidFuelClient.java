package com.forsteri.createliquidfuel;

import com.forsteri.createliquidfuel.core.LiquidFuels;
import com.forsteri.createliquidfuel.network.LiquidFuelSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Map;

public class CreateLiquidFuelClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LiquidFuelSyncPayload.TYPE, (payload, context) -> payload.apply());
        // A server without this mod sends no table; do not keep the previous server's.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (!client.hasSingleplayerServer()) {
                LiquidFuels.set(Map.of());
            }
        });
    }
}
