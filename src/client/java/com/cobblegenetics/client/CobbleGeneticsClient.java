package com.cobblegenetics.client;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.block.ModBlocks;
import com.cobblegenetics.breeding.EggData;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.cobblegenetics.item.ModItems;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client entry point. Pokémon visuals are driven by synced aspects (see GeneticsAspects); here only item colours. */
@Mod(value = CobbleGenetics.MOD_ID, dist = Dist.CLIENT)
public class CobbleGeneticsClient {

    public CobbleGeneticsClient(IEventBus modBus) {
        modBus.addListener(CobbleGeneticsClient::itemColors);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(ModBlocks.INCUBATOR_ENTITY.get(), IncubatorRenderer::new));
    }

    /** Pokémon Egg: layer 1 (the spots) takes the colour of the offspring's primary type. */
    private static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) {
                return -1;
            }
            EggData data = stack.get(ModItems.EGG_DATA.get());
            return data == null ? 0xFF8FCF6B : 0xFF000000 | data.color();
        }, ModItems.POKEMON_EGG.get());
    }
}
