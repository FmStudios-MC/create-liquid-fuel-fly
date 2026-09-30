package com.forsteri.createliquidfuel.core;

import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The fuel table. Replaces upstream's NeoForge data map {@code createliquidfuel:liquid_fuel}; it is
 * filled by {@link LiquidFuelLoader} on the server and synced to clients.
 */
public final class LiquidFuels {
    /** Create Fly measures fluids in droplets: 81 per millibucket, 81000 per bucket. */
    public static final int MB = 81;

    private static volatile Map<Fluid, LiquidFuel> fuels = Map.of();

    private LiquidFuels() {}

    public static @Nullable LiquidFuel get(Fluid fluid) {
        return fuels.get(fluid);
    }

    public static boolean isFuel(Fluid fluid) {
        return get(fluid) != null;
    }

    public static Map<Fluid, LiquidFuel> all() {
        return fuels;
    }

    public static void set(Map<Fluid, LiquidFuel> newFuels) {
        fuels = Map.copyOf(newFuels);
    }
}
