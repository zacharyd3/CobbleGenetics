package com.cobblegenetics.village;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.block.GeneticIncubatorBlock;
import com.cobblegenetics.block.ModBlocks;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.item.ModItems;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * The Day Care Attendant: a villager profession whose job site is the Genetic Incubator (its lower half). Unemployed
 * villagers claim it like any workstation (the POI type is in the vanilla tag minecraft:acquirable_job_site). An Egg
 * left in an Incubator with an attendant nearby hatches over time instead of by walking (GeneticIncubatorBlockEntity).
 */
public final class ModVillagers {

    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CobbleGenetics.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, CobbleGenetics.MOD_ID);

    public static final ResourceKey<PoiType> INCUBATOR_POI =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CobbleGenetics.id("genetic_incubator"));

    public static final DeferredHolder<PoiType, PoiType> INCUBATOR = POI_TYPES.register("genetic_incubator",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.GENETIC_INCUBATOR.get().getStateDefinition()
                    .getPossibleStates().stream()
                    .filter(state -> state.getValue(GeneticIncubatorBlock.HALF) == DoubleBlockHalf.LOWER)
                    .toList()), 1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> DAYCARE_ATTENDANT =
            PROFESSIONS.register("daycare_attendant", () -> new VillagerProfession("daycare_attendant",
                    holder -> holder.is(INCUBATOR_POI), holder -> holder.is(INCUBATOR_POI), ImmutableSet.of(),
                    ImmutableSet.of(), SoundEvents.VILLAGER_WORK_FARMER));

    private ModVillagers() {
    }

    public static void initialize(IEventBus modBus) {
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(ModVillagers::trades);
    }

    /** The nearest Day Care Aide within range of an Incubator, or null (checked about once a second). */
    public static Villager attendantNear(ServerLevel level, BlockPos pos) {
        if (!GeneticsConfig.get().daycareAttendants) {
            return null;
        }
        int range = GeneticsConfig.get().attendantRange;
        Villager nearest = null;
        double best = Double.MAX_VALUE;
        for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(range),
                v -> v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == DAYCARE_ATTENDANT.get())) {
            double distance = villager.distanceToSqr(pos.getCenter());
            if (distance < best) {
                best = distance;
                nearest = villager;
            }
        }
        return nearest;
    }

    // ------------------------------------------------------------------ trades

    /**
     * Berries (the breeding food) both ways, then purification supplies. Cobblemon items are looked up by id so a
     * missing one (another Cobblemon version) just drops that trade.
     */
    private static void trades(VillagerTradesEvent event) {
        if (event.getType() != DAYCARE_ATTENDANT.get()) {
            return;
        }
        var trades = event.getTrades();
        trades.get(1).add(buy(() -> cobblemon("oran_berry"), 12, 16, 2));
        trades.get(1).add(sell(() -> cobblemon("oran_berry"), 4, 1, 12, 1));
        trades.get(2).add(sell(() -> cobblemon("pecha_berry"), 3, 1, 12, 5));
        trades.get(2).add(sell(() -> ModItems.PURIFYING_SCENT.get(), 1, 4, 8, 10));
        trades.get(3).add(buy(() -> Items.EGG, 16, 12, 15));
        trades.get(3).add(sell(() -> ModItems.GENE_STABILIZER.get(), 1, 10, 6, 15));
        trades.get(4).add(sell(() -> ModItems.MUTAGEN.get(), 1, 14, 4, 20));
        trades.get(5).add(sell(() -> ModItems.PURIFICATION_INCENSE.get(), 1, 18, 4, 30));
    }

    private static Item cobblemon(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
    }

    /** The villager buys count items for one emerald. */
    private static VillagerTrades.ItemListing buy(Supplier<Item> item, int count, int maxUses, int xp) {
        return (trader, random) -> {
            Item wanted = item.get();
            if (wanted == Items.AIR) {
                return null;
            }
            return new MerchantOffer(new ItemCost(wanted, count), new ItemStack(Items.EMERALD), maxUses, xp, 0.05F);
        };
    }

    /** The villager sells count items for emeralds. */
    private static VillagerTrades.ItemListing sell(Supplier<Item> item, int count, int emeralds, int maxUses, int xp) {
        return (trader, random) -> {
            Item sold = item.get();
            if (sold == Items.AIR) {
                return null;
            }
            return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(sold, count), maxUses, xp,
                    0.05F);
        };
    }
}
