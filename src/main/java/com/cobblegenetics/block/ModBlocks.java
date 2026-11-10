package com.cobblegenetics.block;

import com.cobblegenetics.CobbleGenetics;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CobbleGenetics.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CobbleGenetics.MOD_ID);

    public static final DeferredBlock<PurificationChamberBlock> PURIFICATION_CHAMBER = BLOCKS.registerBlock(
            "purification_chamber", PurificationChamberBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ).strength(3.0f, 6.0f).sound(SoundType.AMETHYST)
                    .lightLevel(state -> 10).requiresCorrectToolForDrops());

    public static final DeferredBlock<GeneticIncubatorBlock> GENETIC_INCUBATOR = BLOCKS.registerBlock(
            "genetic_incubator", GeneticIncubatorBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(2.5f, 6.0f).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> 7).isViewBlocking((state, level, pos) -> false));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneticIncubatorBlockEntity>>
            INCUBATOR_ENTITY = BLOCK_ENTITIES.register("genetic_incubator", () -> BlockEntityType.Builder
            .of(GeneticIncubatorBlockEntity::new, GENETIC_INCUBATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PurificationChamberBlockEntity>>
            CHAMBER_ENTITY = BLOCK_ENTITIES.register("purification_chamber", () -> BlockEntityType.Builder
            .of(PurificationChamberBlockEntity::new, PURIFICATION_CHAMBER.get()).build(null));

    private ModBlocks() {
    }

    public static void initialize(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
