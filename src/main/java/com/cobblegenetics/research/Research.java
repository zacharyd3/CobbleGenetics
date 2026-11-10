package com.cobblegenetics.research;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.advancement.GeneticsTrigger;
import com.cobblegenetics.config.GeneticsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Research levels gate what the Genetic Analyzer shows. They rise through milestones (config) or Analyzer Upgrade
 * items, which quests hand out. The level is checked on the server when building an analysis, so a modified client
 * cannot see more than its level allows.
 */
public final class Research {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CobbleGenetics.MOD_ID);

    public static final Supplier<AttachmentType<ResearchData>> RESEARCH = ATTACHMENTS.register("research",
            () -> AttachmentType.serializable(ResearchData::new).copyOnDeath().build());

    private Research() {
    }

    public static void initialize(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static ResearchData of(ServerPlayer player) {
        return player.getData(RESEARCH);
    }

    public static int level(ServerPlayer player) {
        return of(player).level;
    }

    /** Raises the level by one if it is exactly {@code from}. Returns true on success. */
    public static boolean upgrade(ServerPlayer player, int from) {
        ResearchData data = of(player);
        if (data.level != from || from >= ResearchData.MAX_LEVEL) {
            return false;
        }
        setLevel(player, from + 1);
        return true;
    }

    public static void setLevel(ServerPlayer player, int level) {
        ResearchData data = of(player);
        int clamped = Math.max(1, Math.min(ResearchData.MAX_LEVEL, level));
        boolean raised = clamped > data.level;
        data.level = clamped;
        player.setData(RESEARCH, data);
        if (raised) {
            player.sendSystemMessage(Component.translatable("message.cobblegenetics.research_level", clamped,
                    Component.translatable("research.cobblegenetics.level" + clamped)).withStyle(ChatFormatting.AQUA));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                    0.6f, 1.4f);
        }
        GeneticsTrigger.trigger(player, GeneticsTrigger.Action.RESEARCH_LEVEL, clamped);
    }

    /** Re-checks the milestone levels after any counter changed. */
    public static void checkMilestones(ServerPlayer player) {
        GeneticsConfig config = GeneticsConfig.get();
        if (!config.researchMilestones) {
            return;
        }
        ResearchData data = of(player);
        int target = data.level;
        if (target == 1 && data.analyses >= config.level2Analyses) {
            target = 2;
        }
        if (target == 2 && data.analyses >= config.level3Analyses
                && data.mutationsDiscovered.size() >= config.level3Mutations) {
            target = 3;
        }
        if (target == 3 && data.offspringAnalyzed >= config.level4Offspring) {
            target = 4;
        }
        if (target == 4 && data.purified >= config.level5Purified) {
            target = 5;
        }
        if (target != data.level) {
            setLevel(player, target);
        }
    }

    public static void save(ServerPlayer player, ResearchData data) {
        player.setData(RESEARCH, data);
    }
}
