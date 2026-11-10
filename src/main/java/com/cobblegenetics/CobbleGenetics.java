package com.cobblegenetics;

import com.cobblegenetics.advancement.GeneticsTrigger;
import com.cobblegenetics.block.ModBlocks;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.item.ModItems;
import com.cobblegenetics.network.ModNetworking;
import com.cobblegenetics.research.Research;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CobbleGenetics: individual genetics, mutations and Shadow Pokémon for Cobblemon. Everything is stored in Cobblemon's
 * own per-Pokémon persistentData and applied through Cobblemon's events, aspects and properties: no Cobblemon class is
 * replaced and no mixin touches the server side.
 */
@Mod(CobbleGenetics.MOD_ID)
public class CobbleGenetics {

    public static final String MOD_ID = "cobblegenetics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    public CobbleGenetics(IEventBus modBus) {
        GeneticsConfig.load(FMLPaths.CONFIGDIR.get().resolve(MOD_ID + ".json"));
        ModBlocks.initialize(modBus);
        ModItems.initialize(modBus);
        Research.initialize(modBus);
        GeneticsTrigger.initialize(modBus);
        ModNetworking.initialize(modBus);
        WorldHooks.register();
        // NeoForge constructs mods in parallel: Cobblemon's lists (aspect providers, properties, events) are touched
        // on the main thread, in common setup, long before any Pokémon exists
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(CobblemonHooks::register));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Logs a failure once per place, so a conflict with another mod costs one feature, not the game or the log. */
    public static void failed(String where, Throwable error) {
        if (FAILED.add(where)) {
            LOGGER.error("CobbleGenetics: {} failed; it will be skipped from now on where possible (logged once)",
                    where, error);
        }
    }
}
