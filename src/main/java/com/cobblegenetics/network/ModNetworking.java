package com.cobblegenetics.network;

import com.cobblegenetics.research.AnalysisService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers CobbleGenetics' two messages. Handlers run on the main thread. */
public final class ModNetworking {

    private static final String PROTOCOL = "1";

    private ModNetworking() {
    }

    public static void initialize(IEventBus modBus) {
        modBus.addListener(ModNetworking::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        // optional(): a vanilla-ish client without CobbleGenetics can still join; the server checks hasChannel
        PayloadRegistrar registrar = event.registrar(PROTOCOL).optional();
        // Client handler through a lambda only: a dedicated server never loads the client class
        registrar.playToClient(AnalysisPayload.TYPE, AnalysisPayload.CODEC,
                (payload, context) -> com.cobblegenetics.client.ClientPayloads.openAnalysis(payload));
        registrar.playToServer(RecordRequestPayload.TYPE, RecordRequestPayload.CODEC,
                (payload, context) -> AnalysisService.record((ServerPlayer) context.player(), payload.pokemon()));
    }
}
