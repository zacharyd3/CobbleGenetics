package com.cobblegenetics.client;

import com.cobblegenetics.CobbleGenetics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client entry point. The visuals are driven by synced aspects (see GeneticsAspects); nothing to set up here yet. */
@Mod(value = CobbleGenetics.MOD_ID, dist = Dist.CLIENT)
public class CobbleGeneticsClient {

    public CobbleGeneticsClient(IEventBus modBus) {
        CobbleGenetics.LOGGER.debug("CobbleGenetics client loaded");
    }
}
