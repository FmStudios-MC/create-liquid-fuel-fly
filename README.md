# Create Liquid Fuel (Fly Port)

Unofficial Fabric / Minecraft 26.2 port of [Create Liquid Fuel](https://github.com/Forsteri123/CreateLiquidFuel)
by Forsteri, built on ZurrTum's [Create Fly](https://modrinth.com/mod/create-fly).
Not made, maintained or supported by Forsteri: report problems with this port here, not upstream.

Blaze burners get a one-bucket fuel tank. Pump lava (or any fluid a data pack lists) into them
with pipes, or right-click them with a bucket of it.

| Fuel | Heat | Burn time per refuel | Used per refuel |
|---|---|---|---|
| Lava | heated | 20 ticks | 1 mB |
| Seed oil (Crafts & Additions) | heated | 10 ticks | 2 mB |
| Bioethanol (Crafts & Additions) | superheated | 24 ticks | 1 mB |
| Peanut oil (Garnished) | superheated | 10 ticks | 2 mB |

## Requirements

Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API, Create Fly 6.0.9-1+.

## Data packs

Same file and format as upstream: `data/createliquidfuel/data_maps/fluid/liquid_fuel.json`.

```json
{
  "values": {
    "minecraft:lava": { "burnTime": 20, "superHeat": false, "amountConsumedPerTick": 1 },
    "#c:some_fluid_tag": { "superHeat": true }
  }
}
```

`burnTime` defaults to 32 (superheated) or 20; `amountConsumedPerTick` (mB, used up once per
refuel, not every tick) to 10 or 1. Files from all packs stack; `"replace": true`, `"remove": [...]`
and NeoForge's `neoforge:conditions` / `neoforge:value` wrappers (`mod_loaded`, `not`, `and`, `or`,
`true`, `false`) work as on NeoForge. Upstream's deprecated `data/<namespace>/compat/*.json` files
are still read.

## Building

```
./gradlew build
```

JDK 25 is required (Gradle picks it up through `gradle/gradle-daemon-jvm.properties`).
`./gradlew runClientGameTest` runs the in-game checks (see `PORTING.md`).

## Licence

MIT, as upstream. Copyright (c) 2023 Forsteri; see `LICENSE`.
