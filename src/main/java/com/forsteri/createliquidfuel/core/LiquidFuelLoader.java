package com.forsteri.createliquidfuel.core;

import com.forsteri.createliquidfuel.CreateLiquidFuel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import java.io.Reader;
import java.util.*;

/**
 * Loads the fuel table. Upstream used a NeoForge data map, which Fabric does not have, so this
 * reads the same file in the same format: {@code data/createliquidfuel/data_maps/fluid/liquid_fuel.json}
 * from every data pack, lowest priority first, with {@code replace}, {@code values} (fluid ids or
 * {@code #tags}), {@code remove}, and {@code neoforge:conditions}/{@code neoforge:value} entries.
 * <p>
 * Also reads upstream's deprecated {@code data/<namespace>/compat/*.json} files, which apply only
 * to fluids the data map does not list.
 */
public class LiquidFuelLoader extends SimpleReloadListener<Map<Fluid, LiquidFuel>> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(CreateLiquidFuel.MOD_ID, "liquid_fuel");
    private static final Identifier DATA_MAP_FILE = Identifier.fromNamespaceAndPath(CreateLiquidFuel.MOD_ID, "data_maps/fluid/liquid_fuel.json");

    @Override
    protected Map<Fluid, LiquidFuel> prepare(PreparableReloadListener.SharedState state) {
        ResourceManager resourceManager = state.resourceManager();
        HolderLookup.Provider registries = state.get(ResourceLoader.REGISTRY_LOOKUP_KEY);

        Map<Fluid, LiquidFuel> fuels = new LinkedHashMap<>();
        loadDataMap(resourceManager, registries, fuels);
        loadLegacyCompat(resourceManager, fuels);
        return fuels;
    }

    @Override
    protected void apply(Map<Fluid, LiquidFuel> fuels, PreparableReloadListener.SharedState state) {
        LiquidFuels.set(fuels);
        CreateLiquidFuel.LOGGER.info("Loaded {} liquid blaze burner fuels", fuels.size());
    }

    private static void loadDataMap(ResourceManager resourceManager, HolderLookup.Provider registries, Map<Fluid, LiquidFuel> fuels) {
        for (Resource resource : resourceManager.getResourceStack(DATA_MAP_FILE)) {
            JsonObject root;
            try (Reader reader = resource.openAsReader()) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                CreateLiquidFuel.LOGGER.error("Could not read {} from pack {}", DATA_MAP_FILE, resource.sourcePackId(), e);
                continue;
            }

            if (root.has("neoforge:conditions") && !conditionsMet(root.getAsJsonArray("neoforge:conditions"))) {
                continue;
            }
            if (root.has("replace") && root.get("replace").getAsBoolean()) {
                fuels.clear();
            }

            if (root.has("values")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("values").entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value.isJsonObject() && value.getAsJsonObject().has("neoforge:value")) {
                        JsonObject wrapper = value.getAsJsonObject();
                        if (wrapper.has("neoforge:conditions") && !conditionsMet(wrapper.getAsJsonArray("neoforge:conditions"))) {
                            continue;
                        }
                        value = wrapper.get("neoforge:value");
                    }

                    Optional<LiquidFuel> fuel = LiquidFuel.CODEC.parse(JsonOps.INSTANCE, value)
                            .resultOrPartial(error -> CreateLiquidFuel.LOGGER.warn(
                                    "Skipping liquid burner fuel {} in pack {}: {}", entry.getKey(), resource.sourcePackId(), error));
                    if (fuel.isEmpty()) continue;

                    for (Fluid fluid : resolve(entry.getKey(), registries)) {
                        fuels.put(fluid, fuel.get());
                    }
                }
            }

            if (root.has("remove")) {
                for (JsonElement removed : root.getAsJsonArray("remove")) {
                    String key = removed.isJsonObject()
                            ? removed.getAsJsonObject().get("key").getAsString()
                            : removed.getAsString();
                    for (Fluid fluid : resolve(key, registries)) {
                        fuels.remove(fluid);
                    }
                }
            }
        }
    }

    private static void loadLegacyCompat(ResourceManager resourceManager, Map<Fluid, LiquidFuel> fuels) {
        Set<Fluid> fromDataMap = new HashSet<>(fuels.keySet());

        for (Map.Entry<Identifier, Resource> file : resourceManager.listResources("compat", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier id = file.getKey();
            JsonObject object;
            try (Reader reader = file.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) continue;
                object = element.getAsJsonObject();
            } catch (Exception e) {
                CreateLiquidFuel.LOGGER.warn("Skipping {}: not valid JSON", id);
                continue;
            }

            JsonElement fluidElement = object.get("fluid");
            if (fluidElement == null) {
                CreateLiquidFuel.LOGGER.warn("Skipping {}: not a liquid burner fuel definition (no \"fluid\" field)", id);
                continue;
            }

            Identifier fluidId = Identifier.tryParse(fluidElement.getAsString());
            Optional<Fluid> fluid = fluidId == null ? Optional.empty() : BuiltInRegistries.FLUID.getOptional(fluidId);
            if (fluid.isEmpty()) {
                CreateLiquidFuel.LOGGER.warn("Skipping liquid burner fuel {}: unknown fluid {}", id, fluidElement.getAsString());
                continue;
            }
            if (fromDataMap.contains(fluid.get())) {
                continue;
            }

            CreateLiquidFuel.LOGGER.warn(
                    "Liquid burner fuel {} is using the deprecated data/<namespace>/compat/ format. Move it to data/createliquidfuel/data_maps/fluid/liquid_fuel.json",
                    id
            );

            boolean superHeat = object.has("superHeat") && object.get("superHeat").getAsBoolean();
            int burnTime = object.has("burnTime")
                    ? object.get("burnTime").getAsInt()
                    : superHeat ? 32 : 20;
            int amountConsumedPerTick = object.has("amountConsumedPerTick")
                    ? object.get("amountConsumedPerTick").getAsInt()
                    : superHeat ? 10 : 1;

            fuels.put(fluid.get(), new LiquidFuel(burnTime, superHeat, amountConsumedPerTick));
        }
    }

    /** A fluid id, or {@code #namespace:tag}. Unknown ids resolve to nothing (their mod is not installed). */
    private static List<Fluid> resolve(String key, HolderLookup.Provider registries) {
        if (key.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(key.substring(1));
            if (tagId == null) {
                CreateLiquidFuel.LOGGER.warn("Invalid fluid tag {} in liquid burner fuels", key);
                return List.of();
            }
            List<Fluid> fluids = new ArrayList<>();
            registries.lookupOrThrow(Registries.FLUID)
                    .get(TagKey.create(Registries.FLUID, tagId))
                    .ifPresent(set -> set.forEach(holder -> fluids.add(holder.value())));
            return fluids;
        }

        Identifier id = Identifier.tryParse(key);
        if (id == null) {
            CreateLiquidFuel.LOGGER.warn("Invalid fluid id {} in liquid burner fuels", key);
            return List.of();
        }
        Optional<Fluid> fluid = BuiltInRegistries.FLUID.getOptional(id);
        if (fluid.isEmpty()) {
            CreateLiquidFuel.LOGGER.debug("Liquid burner fuel {} is not a registered fluid, skipped", key);
            return List.of();
        }
        return List.of(fluid.get());
    }

    /** The NeoForge load conditions a data map may carry, plus their Fabric counterparts. */
    private static boolean conditionsMet(JsonArray conditions) {
        for (JsonElement condition : conditions) {
            if (!conditionMet(condition.getAsJsonObject())) return false;
        }
        return true;
    }

    private static boolean conditionMet(JsonObject condition) {
        String type = condition.get("type").getAsString();
        FabricLoader loader = FabricLoader.getInstance();
        return switch (type) {
            case "neoforge:true" -> true;
            case "neoforge:false" -> false;
            case "neoforge:mod_loaded" -> loader.isModLoaded(condition.get("modid").getAsString());
            case "neoforge:not" -> !conditionMet(condition.getAsJsonObject("value"));
            case "neoforge:and" -> conditionsMet(condition.getAsJsonArray("values"));
            case "neoforge:or" -> {
                for (JsonElement inner : condition.getAsJsonArray("values")) {
                    if (conditionMet(inner.getAsJsonObject())) yield true;
                }
                yield false;
            }
            case "fabric:all_mods_loaded" -> {
                for (JsonElement mod : condition.getAsJsonArray("values")) {
                    if (!loader.isModLoaded(mod.getAsString())) yield false;
                }
                yield true;
            }
            case "fabric:any_mods_loaded" -> {
                for (JsonElement mod : condition.getAsJsonArray("values")) {
                    if (loader.isModLoaded(mod.getAsString())) yield true;
                }
                yield false;
            }
            default -> {
                CreateLiquidFuel.LOGGER.warn("Unsupported load condition {} in liquid burner fuels, entry skipped", type);
                yield false;
            }
        };
    }
}
