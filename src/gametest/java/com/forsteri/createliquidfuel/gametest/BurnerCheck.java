package com.forsteri.createliquidfuel.gametest;

import com.forsteri.createliquidfuel.core.IHasStomach;
import com.forsteri.createliquidfuel.core.LiquidFuel;
import com.forsteri.createliquidfuel.core.LiquidFuels;
import com.forsteri.createliquidfuel.core.StomachTank;
import com.forsteri.createliquidfuel.network.LiquidFuelSyncPayload;
import com.zurrtum.create.AllFluids;
import com.zurrtum.create.content.fluids.tank.FluidTankBlockEntity;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock;
import com.zurrtum.create.foundation.fluid.FluidHelper;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Checks the port in a real world: the fuel table loads (including a second data pack's entries and
 * conditions), fuel goes into a burner through Create Fly's fluid API, Fabric's transfer API, a
 * right-clicked bucket and a pump, burners heat up and use their fuel, non-fuels are refused, and
 * the tank survives save and reload. Each check logs one "CLF-TEST PASS/FAIL" line.
 */
public class BurnerCheck implements FabricClientGameTest {
    static final int Y = 150;
    static final int BUCKET = 81000;

    static final BlockPos LAVA_BURNER = new BlockPos(0, Y, 4);
    static final BlockPos WATER_BURNER = new BlockPos(2, Y, 4);
    static final BlockPos BUCKET_BURNER = new BlockPos(4, Y, 4);
    static final BlockPos HONEY_BURNER = new BlockPos(6, Y, 4);
    static final BlockPos EMPTY_BURNER = new BlockPos(8, Y, 4);

    // tank -> pipe -> pump (driven through a cogwheel above it) -> burner
    static final BlockPos TANK = new BlockPos(0, Y, 0);
    static final BlockPos PIPE = new BlockPos(1, Y, 0);
    static final BlockPos PUMP = new BlockPos(2, Y, 0);
    static final BlockPos PUMPED_BURNER = new BlockPos(3, Y, 0);

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(true).create()) {
            singleplayer.getConnection().waitForChunksRender();
            TestServerContext server = singleplayer.getServer();
            save = singleplayer.getWorldSave();

            server.runCommand("gamemode spectator @p");
            server.runCommand("tp @p 4 " + (Y + 3) + " -4 facing 4 " + Y + " 2");
            server.runCommand("fill -4 " + (Y - 1) + " -4 12 " + (Y - 1) + " 8 minecraft:smooth_stone");
            server.runCommand("fill -4 " + Y + " -4 12 " + (Y + 6) + " 8 minecraft:air");

            check(server, "fuel table: lava from the mod's data map", level -> describe(LiquidFuels.get(Fluids.LAVA), new LiquidFuel(20, false, 1)));
            check(server, "fuel table: water from a second pack, defaults for superHeat", level -> describe(LiquidFuels.get(Fluids.WATER), new LiquidFuel(32, true, 10)));
            check(server, "fuel table: entry with an unmet mod_loaded condition is skipped", level ->
                    LiquidFuels.isFuel(AllFluids.CHOCOLATE) ? "chocolate is fuel FAIL" : "chocolate is not fuel");
            check(server, "fuel table survives the sync packet's codec", level -> {
                LiquidFuelSyncPayload sent = LiquidFuelSyncPayload.fromServer();
                ByteBuf buf = Unpooled.buffer();
                LiquidFuelSyncPayload.STREAM_CODEC.encode(buf, sent);
                LiquidFuelSyncPayload received = LiquidFuelSyncPayload.STREAM_CODEC.decode(buf);
                return received.equals(sent) ? sent.fuels().size() + " entries" : "got " + received + " FAIL";
            });

            for (BlockPos pos : List.of(LAVA_BURNER, WATER_BURNER, BUCKET_BURNER, HONEY_BURNER, EMPTY_BURNER, PUMPED_BURNER)) {
                set(server, pos, "create:blaze_burner[blaze=smouldering]");
            }
            set(server, TANK, "create:fluid_tank");
            set(server, PIPE, "create:fluid_pipe");
            set(server, PUMP, "create:mechanical_pump[facing=east]");
            set(server, PUMP.above(), "create:cogwheel[axis=x]");
            set(server, PUMP.above().west(), "create:creative_motor[facing=east]");
            context.waitTicks(5);

            check(server, "Create Fly's fluid lookup finds the stomach", level -> {
                FluidInventory inventory = FluidHelper.getFluidInventory(level, LAVA_BURNER, Direction.WEST);
                return inventory instanceof StomachTank ? "found" : "got " + inventory + " FAIL";
            });
            check(server, "lava goes in through Create Fly's fluid API", level -> {
                FluidInventory inventory = FluidHelper.getFluidInventory(level, LAVA_BURNER, Direction.WEST);
                int inserted = inventory == null ? 0 : inventory.insert(new FluidStack(Fluids.LAVA, BUCKET));
                return "inserted " + inserted + (inserted == BUCKET ? "" : " FAIL");
            });
            check(server, "water goes in through Fabric's transfer API", level -> {
                Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, WATER_BURNER, Direction.UP);
                if (storage == null) return "no storage FAIL";
                long inserted;
                try (Transaction transaction = Transaction.openOuter()) {
                    inserted = storage.insert(FluidVariant.of(Fluids.WATER), BUCKET, transaction);
                    transaction.commit();
                }
                return "inserted " + inserted + (inserted == BUCKET ? "" : " FAIL");
            });
            check(server, "a right-clicked lava bucket fills the stomach and leaves a bucket", level -> {
                BlockState state = level.getBlockState(BUCKET_BURNER);
                ItemStack bucket = new ItemStack(Items.LAVA_BUCKET);
                InteractionResult result = BlazeBurnerBlock.tryInsert(state, level, BUCKET_BURNER, bucket, false, true, false);
                ItemStack leftover = result instanceof InteractionResult.Success success ? success.heldItemTransformedTo() : null;
                int amount = stomach(level, BUCKET_BURNER).getAmount();
                String diag = "result " + result + ", leftover " + leftover + ", stomach " + amount;
                return amount == BUCKET && leftover != null && leftover.is(Items.BUCKET) ? diag : diag + " FAIL";
            });
            check(server, "a non-fuel fluid is refused", level -> {
                FluidInventory inventory = FluidHelper.getFluidInventory(level, HONEY_BURNER, Direction.WEST);
                int inserted = inventory == null ? -1 : inventory.insert(new FluidStack(AllFluids.HONEY, BUCKET));
                return "inserted " + inserted + (inserted == 0 ? "" : " FAIL");
            });
            check(server, "the stomach refuses a second fuel while holding another", level -> {
                FluidInventory inventory = FluidHelper.getFluidInventory(level, LAVA_BURNER, Direction.WEST);
                int inserted = inventory == null ? -1 : inventory.insert(new FluidStack(Fluids.WATER, 1000));
                return "inserted " + inserted + (inserted == 0 ? "" : " FAIL");
            });
            server.runOnServer(mc -> {
                if (mc.overworld().getBlockEntity(TANK) instanceof FluidTankBlockEntity tank) {
                    tank.getTankInventory().setFluid(new FluidStack(Fluids.LAVA, 8 * BUCKET));
                    tank.notifyUpdate();
                }
            });

            context.waitTicks(200);

            check(server, "lava burner is fading (heated) and has used some lava", level -> heatAndUse(level, LAVA_BURNER, BlazeBurnerBlock.HeatLevel.FADING, BUCKET));
            check(server, "water burner is seething (superheated) and has used some water", level -> heatAndUse(level, WATER_BURNER, BlazeBurnerBlock.HeatLevel.SEETHING, BUCKET));
            check(server, "bucket-fed burner is fading", level -> heatAndUse(level, BUCKET_BURNER, BlazeBurnerBlock.HeatLevel.FADING, BUCKET));
            check(server, "burner without fuel stays smouldering", level -> {
                BlazeBurnerBlock.HeatLevel heat = level.getBlockState(EMPTY_BURNER).getValue(BlazeBurnerBlock.HEAT_LEVEL);
                return heat + (heat == BlazeBurnerBlock.HeatLevel.SMOULDERING ? "" : " FAIL");
            });
            check(server, "pump moves lava from a tank into a burner, which heats up", level -> {
                FluidStack contents = stomach(level, PUMPED_BURNER);
                BlazeBurnerBlock.HeatLevel heat = level.getBlockState(PUMPED_BURNER).getValue(BlazeBurnerBlock.HEAT_LEVEL);
                String diag = "stomach " + describe(contents) + ", heat " + heat + ", pump " + level.getBlockState(PUMP);
                return !contents.isEmpty() && heat == BlazeBurnerBlock.HeatLevel.FADING ? diag : diag + " FAIL";
            });

            context.takeScreenshot("burners");
        }

        try (TestSingleplayerContext singleplayer = save.open()) {
            singleplayer.getConnection().waitForChunksRender();
            TestServerContext server = singleplayer.getServer();
            context.waitTicks(20);
            check(server, "stomach contents survive save and reload", level -> {
                FluidStack contents = stomach(level, WATER_BURNER);
                return describe(contents) + (contents.isOf(Fluids.WATER) && contents.getAmount() > 0 ? "" : " FAIL");
            });
            check(server, "burner keeps burning after reload", level -> heatAndUse(level, LAVA_BURNER, BlazeBurnerBlock.HeatLevel.FADING, BUCKET));
        }

        if (!failures.isEmpty())
            throw new AssertionError("Create Liquid Fuel checks failed: " + failures);
    }

    private static String heatAndUse(ServerLevel level, BlockPos pos, BlazeBurnerBlock.HeatLevel expected, int initial) {
        BlazeBurnerBlock.HeatLevel heat = level.getBlockState(pos).getValue(BlazeBurnerBlock.HEAT_LEVEL);
        FluidStack contents = stomach(level, pos);
        String diag = "heat " + heat + ", stomach " + describe(contents);
        boolean used = !contents.isEmpty() && contents.getAmount() < initial;
        return heat == expected && used ? diag : diag + " FAIL";
    }

    private static FluidStack stomach(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof IHasStomach burner ? burner.createliquidfuel$getStomach().getFluid() : FluidStack.EMPTY;
    }

    private static String describe(FluidStack stack) {
        return stack.isEmpty() ? "empty" : BuiltInRegistries.FLUID.getKey(stack.getFluid()) + " x" + stack.getAmount();
    }

    private static String describe(LiquidFuel actual, LiquidFuel expected) {
        return actual + (expected.equals(actual) ? "" : ", expected " + expected + " FAIL");
    }

    private void check(TestServerContext server, String name, Function<ServerLevel, String> probe) {
        String result = server.computeOnServer(mc -> probe.apply(mc.overworld()));
        boolean failed = result.endsWith("FAIL");
        if (failed)
            failures.add(name);
        System.out.println("CLF-TEST " + (failed ? "FAIL" : "PASS") + " | " + name + " | " + result);
    }

    static void set(TestServerContext server, BlockPos pos, String block) {
        server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
    }
}
