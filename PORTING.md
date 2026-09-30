# Porting notes: Create Liquid Fuel → Create Fly (Fabric, MC 26.2)

## State

**Done, 2026-09-30.** Everything upstream had is ported. `./gradlew runClientGameTest` passes all
17 checks (see *Testing*). A dedicated server (`runServer`) loads the fuel table and reaches `Done`.
Still only checkable by playing: other mods' pipes through the Fabric transfer API, and
Crafts & Additions / Garnished fuels with those mods installed.

| | |
|---|---|
| Upstream | https://github.com/Forsteri123/CreateLiquidFuel, branch `neoforge/1.21.1` (3.0.0, commit `d176f46`) |
| Target | Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.160.0+26.2 |
| Create Fly | `maven.modrinth:create-fly:26.2-rc-2-6.0.9-1` |
| Licence | MIT: keep `LICENSE` with Forsteri's copyright notice |

Mod id (`createliquidfuel`) and package (`com.forsteri.createliquidfuel`) are unchanged, so data
packs written for upstream work as they are.

## What changed

- **Units.** Create Fly measures fluids in droplets (81000 per bucket). Data files stay in mB;
  `LiquidFuels.MB` (81) converts. The tank holds 1000 mB = 81000 droplets.
- **Tank.** Upstream's `SmartFluidTank` became `StomachTank`, a Create Fly `FluidTank` that only
  accepts listed fuels. It is created in the block entity's constructor. Upstream overrode
  `addBehaviours` from the mixin, which silently replaced Create's own; that is gone.
- **Capability → two lookups.** Create Fly's pipes, pumps and spouts ask the *block* for a
  `FluidInventoryProvider`: `BlazeBurnerBlockMixin` adds it. Other mods use Fabric's
  `FluidStorage.SIDED`, registered in `CreateLiquidFuel` the way Create Fly's `AllTransfer` does.
- **Data map → reload listener.** Fabric has no data maps. `LiquidFuelLoader` reads the same file
  from every pack (`getResourceStack`) and implements the parts of the data map format that matter
  (`values` with ids and `#tags`, `replace`, `remove`, NeoForge conditions). The deprecated
  `compat/*.json` loader is merged into it.
- **Sync.** Upstream's data map was synced to clients. `LiquidFuelSyncPayload` is sent on join and
  after `/reload`, only to clients that have the mod. The client needs it to predict a
  right-click with a fuel bucket.
- **Buckets.** Upstream read an item's fluid handler; the port uses Create Fly's
  `GenericItemEmptying` (buckets, bottles, anything with an emptying recipe or Fabric fluid storage).
  Upstream could overfill the tank past one bucket on a player's right-click; the port fills up to
  the capacity and refuses when full.
- NBT: the tank is stored under `Stomach` via `ValueOutput`. Worlds from the NeoForge version do not
  carry over anyway.
- Removed: `Triplet`, `MathUtil` (unused), the NeoForge event handler classes.

## Behaviour kept on purpose

The stomach tick is injected at the *tail* of `BlazeBurnerBlockEntity.tick`. That tail is only
reached once the current burn time has run out, so `amountConsumedPerTick` is really "per refuel":
lava uses 1 mB every 20 ticks, i.e. one bucket lasts 20000 ticks, the same as a lava bucket as item
fuel. Non-superheating fuels hold the burner at `FADING`, superheating ones at `SEETHING`. If less
than one refuel's worth is left, the remainder is discarded. All as upstream.

## Testing

`./gradlew runClientGameTest` (about a minute) runs `src/gametest/.../BurnerCheck`, which logs
`CLF-TEST PASS/FAIL` lines. A test data pack (`src/gametest/resources/data`) adds water as a
superheating fuel and a conditioned entry that must be skipped. Checked: fuel table and pack
stacking, the sync codec, Create Fly's fluid lookup and Fabric's transfer API finding the tank,
bucket right-click, refusal of non-fuels and of a second fuel, heat levels and consumption rates,
a real tank → pipe → pump → burner chain, save and reload. The run ends with a Flywheel
client-shutdown watchdog crash report after the tests: Create Fly's worker threads, harmless.
