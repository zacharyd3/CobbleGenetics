package com.cobblegenetics.item;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CobbleGenetics.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CobbleGenetics.MOD_ID);

    public static final DeferredItem<GeneticAnalyzerItem> GENETIC_ANALYZER = ITEMS.registerItem("genetic_analyzer",
            GeneticAnalyzerItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<AnalyzerUpgradeItem> ANALYZER_UPGRADE_2 = ITEMS.registerItem(
            "analyzer_upgrade_2", p -> new AnalyzerUpgradeItem(2, p), new Item.Properties().stacksTo(16));
    public static final DeferredItem<AnalyzerUpgradeItem> ANALYZER_UPGRADE_3 = ITEMS.registerItem(
            "analyzer_upgrade_3", p -> new AnalyzerUpgradeItem(3, p), new Item.Properties().stacksTo(16));
    public static final DeferredItem<AnalyzerUpgradeItem> ANALYZER_UPGRADE_4 = ITEMS.registerItem(
            "analyzer_upgrade_4", p -> new AnalyzerUpgradeItem(4, p),
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<AnalyzerUpgradeItem> ANALYZER_UPGRADE_5 = ITEMS.registerItem(
            "analyzer_upgrade_5", p -> new AnalyzerUpgradeItem(5, p),
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<Item> GENETIC_RECORD = ITEMS.registerSimpleItem("genetic_record",
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<GeneticIncubatorItem> GENETIC_INCUBATOR = ITEMS.registerItem("genetic_incubator",
            GeneticIncubatorItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<MutagenItem> MUTAGEN = ITEMS.registerItem("mutagen", MutagenItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<GeneStabilizerItem> GENE_STABILIZER = ITEMS.registerItem("gene_stabilizer",
            GeneStabilizerItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<PurificationItem> PURIFYING_SCENT = ITEMS.registerItem("purifying_scent",
            p -> new PurificationItem(false, p), new Item.Properties().stacksTo(64));
    public static final DeferredItem<PurificationItem> PURIFICATION_INCENSE = ITEMS.registerItem(
            "purification_incense", p -> new PurificationItem(true, p),
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<ShadowBeaconItem> SHADOW_BEACON = ITEMS.registerItem("shadow_beacon",
            ShadowBeaconItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PURIFICATION_CHAMBER =
            ITEMS.registerSimpleBlockItem(ModBlocks.PURIFICATION_CHAMBER);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cobblegenetics"))
                    .icon(() -> new ItemStack(GENETIC_ANALYZER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(GENETIC_ANALYZER.get());
                        output.accept(ANALYZER_UPGRADE_2.get());
                        output.accept(ANALYZER_UPGRADE_3.get());
                        output.accept(ANALYZER_UPGRADE_4.get());
                        output.accept(ANALYZER_UPGRADE_5.get());
                        output.accept(GENETIC_INCUBATOR.get());
                        output.accept(MUTAGEN.get());
                        output.accept(GENE_STABILIZER.get());
                        output.accept(PURIFYING_SCENT.get());
                        output.accept(PURIFICATION_INCENSE.get());
                        output.accept(SHADOW_BEACON.get());
                        output.accept(PURIFICATION_CHAMBER.get());
                    })
                    .build());

    private ModItems() {
    }

    public static void initialize(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
