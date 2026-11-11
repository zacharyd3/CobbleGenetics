package com.cobblegenetics.compat.relics;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.compat.RelicHooks;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.genetics.GeneticsRules;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.item.ModItems;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * CobbleGenetics relics for the Relics mod (optional): they only exist when Relics is installed. These classes extend
 * Relics classes, and CobbleGenetics only reaches here after checking ModList, so without Relics they never load.
 * Effects go through {@link RelicHooks}: the core calls the hooks, this class answers them.
 *
 * <ul>
 *   <li>Breeder's Apron (body): faster Day Care Aides; rank 3, carried Eggs hatch sooner</li>
 *   <li>Geneticist's Loupe (head): read a Pokémon's breeding value by looking at it; rank 3, potential and stability</li>
 *   <li>Training Weights (feet): more IVs raised by training; rank 3, higher ceiling</li>
 *   <li>Resonance Fork (ring): stronger affinity resonance; rank 3, off-type affinities resonate a little</li>
 *   <li>Heirloom Locket (back): more potential for bred offspring; rank 3, half the opposed-affinity penalty</li>
 *   <li>Glowcap Lantern (belt): more environmental mutations; rank 3, night vision near glowing Pokémon</li>
 * </ul>
 */
public final class RelicsCompat {

    public static final String MOD_ID = "relics";
    private static final double CARRIED_EGG_BONUS = 1.25;
    private static final int CEILING_BONUS = 3;
    private static final double OFF_TYPE_SHARE = 1 / 3.0;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CobbleGenetics.MOD_ID);

    public static final DeferredItem<BreedersApronItem> BREEDERS_APRON =
            ITEMS.register("breeders_apron", BreedersApronItem::new);
    public static final DeferredItem<GeneticistsLoupeItem> GENETICISTS_LOUPE =
            ITEMS.register("geneticists_loupe", GeneticistsLoupeItem::new);
    public static final DeferredItem<TrainingWeightsItem> TRAINING_WEIGHTS =
            ITEMS.register("training_weights", TrainingWeightsItem::new);
    public static final DeferredItem<ResonanceForkItem> RESONANCE_FORK =
            ITEMS.register("resonance_fork", ResonanceForkItem::new);
    public static final DeferredItem<HeirloomLocketItem> HEIRLOOM_LOCKET =
            ITEMS.register("heirloom_locket", HeirloomLocketItem::new);
    public static final DeferredItem<GlowcapLanternItem> GLOWCAP_LANTERN =
            ITEMS.register("glowcap_lantern", GlowcapLanternItem::new);

    private static final List<Supplier<? extends Item>> ALL = List.of(BREEDERS_APRON, GENETICISTS_LOUPE,
            TRAINING_WEIGHTS, RESONANCE_FORK, HEIRLOOM_LOCKET, GLOWCAP_LANTERN);

    /** Pokémon each player has recently read with the Loupe (experience once per Pokémon), capped. */
    private static final Map<UUID, LinkedHashSet<UUID>> READ = new ConcurrentHashMap<>();

    private RelicsCompat() {
    }

    /** From the CobbleGenetics constructor, only with Relics installed. */
    public static void initialize(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == ModItems.TAB.getKey()) {
                for (Supplier<? extends Item> item : ALL) {
                    event.accept(item.get());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(RelicsCompat::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                READ.remove(event.getEntity().getUUID()));
        RelicHooks.set(new Hooks());
    }

    private static <T> T safely(String where, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RuntimeException | LinkageError e) {
            // Another Relics version (its API changes a lot) must not break genetics: that relic stops working
            CobbleGenetics.failed(where, e);
            return fallback;
        }
    }

    private static void safely(String where, Runnable action) {
        safely(where, () -> {
            action.run();
            return null;
        }, null);
    }

    // ------------------------------------------------------------------ hooks

    private static final class Hooks implements RelicHooks.Hooks {

        @Override
        public double aideSpeed(ServerPlayer owner) {
            return safely("the Breeder's Apron", () -> {
                GeneRelicItem.Worn worn = BREEDERS_APRON.get().best(owner, "speed");
                return worn == null ? 1 : 1 + worn.value();
            }, 1.0);
        }

        @Override
        public void aideHatched(ServerPlayer owner) {
            safely("the Breeder's Apron", () -> {
                GeneRelicItem.Worn worn = BREEDERS_APRON.get().best(owner, "speed");
                if (worn != null) {
                    BREEDERS_APRON.get().reward(owner, worn, "aide_hatch", 10, "eggs_hatched");
                }
            });
        }

        @Override
        public double carriedEggSteps(ServerPlayer player) {
            return safely("the Breeder's Apron", () -> {
                GeneRelicItem.Worn worn = BREEDERS_APRON.get().best(player, "speed");
                return worn != null && worn.rank("pocket_warmth") ? CARRIED_EGG_BONUS : 1;
            }, 1.0);
        }

        @Override
        public void walkedHatch(ServerPlayer player) {
            safely("the Breeder's Apron", () -> {
                GeneRelicItem.Worn worn = BREEDERS_APRON.get().best(player, "speed");
                if (worn != null) {
                    BREEDERS_APRON.get().reward(player, worn, "egg_hatched", 3, "eggs_hatched");
                }
            });
        }

        @Override
        public double trainingChance(ServerPlayer owner) {
            return safely("the Training Weights", () -> {
                GeneRelicItem.Worn worn = TRAINING_WEIGHTS.get().best(owner, "chance");
                return worn == null ? 0 : worn.value();
            }, 0.0);
        }

        @Override
        public int trainingCeiling(ServerPlayer owner) {
            return safely("the Training Weights", () -> {
                GeneRelicItem.Worn worn = TRAINING_WEIGHTS.get().best(owner, "chance");
                return worn != null && worn.rank("beyond_limits") ? CEILING_BONUS : 0;
            }, 0);
        }

        @Override
        public void trained(ServerPlayer owner) {
            safely("the Training Weights", () -> {
                GeneRelicItem.Worn worn = TRAINING_WEIGHTS.get().best(owner, "chance");
                if (worn != null) {
                    TRAINING_WEIGHTS.get().reward(owner, worn, "iv_raised", 2, "ivs_raised");
                }
            });
        }

        @Override
        public double resonanceBoost(ServerPlayer owner) {
            return safely("the Resonance Fork", () -> {
                GeneRelicItem.Worn worn = RESONANCE_FORK.get().best(owner, "boost");
                return worn == null ? 1 : 1 + worn.value();
            }, 1.0);
        }

        @Override
        public double offTypeResonance(ServerPlayer owner) {
            return safely("the Resonance Fork", () -> {
                GeneRelicItem.Worn worn = RESONANCE_FORK.get().best(owner, "boost");
                return worn != null && worn.rank("sympathy") ? OFF_TYPE_SHARE : 0;
            }, 0.0);
        }

        @Override
        public void resonated(ServerPlayer owner) {
            safely("the Resonance Fork", () -> {
                GeneRelicItem.Worn worn = RESONANCE_FORK.get().best(owner, "boost");
                if (worn != null) {
                    RESONANCE_FORK.get().reward(owner, worn, "resonant_gain", 1, "resonant_gains");
                }
            });
        }

        @Override
        public double breedingPotential(ServerPlayer owner) {
            return safely("the Heirloom Locket", () -> {
                GeneRelicItem.Worn worn = HEIRLOOM_LOCKET.get().best(owner, "potential");
                return worn == null ? 0 : worn.value();
            }, 0.0);
        }

        @Override
        public boolean breedingHarmony(ServerPlayer owner) {
            return safely("the Heirloom Locket", () -> {
                GeneRelicItem.Worn worn = HEIRLOOM_LOCKET.get().best(owner, "potential");
                return worn != null && worn.rank("harmony");
            }, false);
        }

        @Override
        public void bred(ServerPlayer owner) {
            safely("the Heirloom Locket", () -> {
                GeneRelicItem.Worn worn = HEIRLOOM_LOCKET.get().best(owner, "potential");
                if (worn != null) {
                    HEIRLOOM_LOCKET.get().reward(owner, worn, "breed", 10, "offspring");
                }
            });
        }

        @Override
        public double environmentChance(ServerPlayer player) {
            return safely("the Glowcap Lantern", () -> {
                GeneRelicItem.Worn worn = GLOWCAP_LANTERN.get().best(player, "chance");
                return worn == null ? 1 : 1 + worn.value();
            }, 1.0);
        }

        @Override
        public void environmentCheck(ServerPlayer player) {
            safely("the Glowcap Lantern", () -> {
                GeneRelicItem.Worn worn = GLOWCAP_LANTERN.get().best(player, "chance");
                if (worn != null) {
                    GLOWCAP_LANTERN.get().reward(player, worn, "exposure", 1, null);
                }
            });
        }

        @Override
        public void environmentMutation(ServerPlayer player) {
            safely("the Glowcap Lantern", () -> {
                GeneRelicItem.Worn worn = GLOWCAP_LANTERN.get().best(player, "chance");
                if (worn != null) {
                    GLOWCAP_LANTERN.get().reward(player, worn, "mutation", 10, "mutations_found");
                }
            });
        }
    }

    // ------------------------------------------------------------------ Loupe and Lantern (every half second)

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount % 10 != 0) {
            return;
        }
        safely("the Geneticist's Loupe", () -> loupe(player));
        if (player.tickCount % 40 == 0) {
            safely("the Glowcap Lantern", () -> lantern(player));
        }
    }

    /** Looking at a Pokémon within range: its breeding value on the action bar. */
    private static void loupe(ServerPlayer player) {
        GeneticistsLoupeItem loupe = GENETICISTS_LOUPE.get();
        GeneRelicItem.Worn worn = loupe.best(player, "range");
        if (worn == null) {
            return;
        }
        PokemonEntity target = lookedAt(player, worn.value());
        if (target == null || target.getPokemon() == null) {
            return;
        }
        Pokemon pokemon = target.getPokemon();
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        if (genome == null) {
            return;
        }
        double effective = GeneticsService.effectiveStability(pokemon, genome, PokemonGenetics.shadow(pokemon));
        double value = GeneticsRules.breedingValue(genome, effective);
        Component grade = Component.literal(GeneticsRules.grade(value) + " (" + Math.round(value) + ")")
                .withStyle(Style.EMPTY.withColor(value >= 70 ? 0x55D66B : value >= 40 ? 0xE8C547 : 0xE5534B));
        Component text = worn.rank("deep_reading")
                ? Component.translatable("message.cobblegenetics.loupe_deep", pokemon.getDisplayName(false), grade,
                GeneticsRules.grade(genome.potential) + " (" + Math.round(genome.potential) + ")",
                Math.round(effective))
                : Component.translatable("message.cobblegenetics.loupe", pokemon.getDisplayName(false), grade);
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.AQUA), true);

        LinkedHashSet<UUID> read = READ.computeIfAbsent(player.getUUID(), id -> new LinkedHashSet<>());
        if (read.add(pokemon.getUuid())) {
            loupe.reward(player, worn, "reading", 1, "pokemon_read");
            if (read.size() > 256) {
                read.remove(read.iterator().next());
            }
        }
    }

    /** The Pokémon the player is looking at within range, not behind a wall; null if none. */
    private static PokemonEntity lookedAt(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1);
        HitResult block = player.level().clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1),
                entity -> entity instanceof PokemonEntity && entity.isAlive(), reach * reach);
        return hit != null && hit.getEntity() instanceof PokemonEntity pokemon ? pokemon : null;
    }

    /** Rank 3: night vision while one of the wearer's glowing Pokémon is sent out. */
    private static void lantern(ServerPlayer player) {
        GeneRelicItem.Worn worn = GLOWCAP_LANTERN.get().best(player, "chance");
        if (worn == null || !worn.rank("living_light")) {
            return;
        }
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            PokemonEntity entity = pokemon == null ? null : pokemon.getEntity();
            if (entity != null && entity.isAlive()
                    && entity.getEntityData().get(PokemonEntity.Companion.getASPECTS()).contains("cg-glow")) {
                // A little over the check interval, so it doesn't flicker; ambient, no particles
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
                return;
            }
        }
    }
}
