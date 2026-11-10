package com.cobblegenetics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the set of loaded chambers (server side) up to date as chunks load and unload. */
public class PurificationChamberBlockEntity extends BlockEntity {

    private static final Set<GlobalPos> LOADED = ConcurrentHashMap.newKeySet();

    public PurificationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CHAMBER_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            LOADED.add(GlobalPos.of(level.dimension(), worldPosition));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            LOADED.remove(GlobalPos.of(level.dimension(), worldPosition));
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            LOADED.remove(GlobalPos.of(level.dimension(), worldPosition));
        }
    }

    /** True if a loaded chamber is within radius blocks of pos in that dimension. */
    public static boolean near(GlobalPos pos, int radius) {
        long max = (long) radius * radius;
        for (GlobalPos chamber : LOADED) {
            if (chamber.dimension().equals(pos.dimension()) && chamber.pos().distSqr(pos.pos()) <= max) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        LOADED.clear();
    }
}
