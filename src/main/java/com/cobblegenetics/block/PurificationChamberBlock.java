package com.cobblegenetics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Purification Chamber: a dedicated purification location. Shadow Pokémon in the party of a player standing near one
 * purify several times faster (config chamberMultiplier / chamberRadius). Its block entity only registers its
 * position, so finding nearby chambers never scans blocks.
 */
public class PurificationChamberBlock extends BaseEntityBlock {

    public static final MapCodec<PurificationChamberBlock> CODEC = simpleCodec(PurificationChamberBlock::new);

    public PurificationChamberBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PurificationChamberBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
        }
    }
}
