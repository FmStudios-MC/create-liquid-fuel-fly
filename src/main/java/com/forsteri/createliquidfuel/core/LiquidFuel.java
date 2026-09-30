package com.forsteri.createliquidfuel.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * How a fluid fuels a blaze burner. Amounts are in millibuckets, as in upstream's data files;
 * {@link LiquidFuels#MB} converts them to Create Fly's droplets.
 */
public record LiquidFuel(int burnTime, boolean superHeat, int amountConsumedPerTick) {
    public static final Codec<LiquidFuel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("superHeat", false).forGetter(LiquidFuel::superHeat),
            Codec.INT.optionalFieldOf("burnTime").forGetter(fuel -> Optional.of(fuel.burnTime())),
            Codec.INT.optionalFieldOf("amountConsumedPerTick").forGetter(fuel -> Optional.of(fuel.amountConsumedPerTick()))
    ).apply(instance, (superHeat, burnTime, amountConsumedPerTick) -> new LiquidFuel(
            burnTime.orElse(superHeat ? 32 : 20),
            superHeat,
            amountConsumedPerTick.orElse(superHeat ? 10 : 1)
    )));

    public static final StreamCodec<ByteBuf, LiquidFuel> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LiquidFuel::burnTime,
            ByteBufCodecs.BOOL, LiquidFuel::superHeat,
            ByteBufCodecs.VAR_INT, LiquidFuel::amountConsumedPerTick,
            LiquidFuel::new
    );
}
