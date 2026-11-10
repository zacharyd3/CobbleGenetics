package com.cobblegenetics.client;

import com.cobblegenetics.client.screen.AnalyzerScreen;
import com.cobblegenetics.network.AnalysisPayload;
import net.minecraft.client.Minecraft;

/** Client handlers of server messages. Only reached through lambdas, so dedicated servers never load it. */
public final class ClientPayloads {

    private ClientPayloads() {
    }

    public static void openAnalysis(AnalysisPayload payload) {
        Minecraft.getInstance().setScreen(new AnalyzerScreen(payload));
    }
}
