package com.cobblegenetics.compat.relics;

import it.hurts.sskirillss.relics.api.relics.data.AbilityData;
import it.hurts.sskirillss.relics.items.relics.base.WearableRelicItem;
import it.hurts.sskirillss.relics.utils.EntityUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Base of the CobbleGenetics relics for the Relics mod (same design as Fusionmon's): worn in Curios slots, levelled by
 * using them, abilities unlocked through Relics' star research. Each relic has ONE ability (ABILITY of the subclass);
 * what it does is in RelicsCompat, here only the shared parts: finding the worn one and reading its stats.
 *
 * <p>Relics looks up texts by item path ("relics.description.breeders_apron...") and ability images in
 * assets/relics/textures/abilities/&lt;item&gt;/: the item names are unique and those images ship in our jar under the
 * "relics" namespace.</p>
 */
public abstract class GeneRelicItem extends WearableRelicItem {

    protected abstract String ability();

    @Override
    public String getConfigRoute() {
        // config/cobblegenetics/relics/<item>.json: each relic's values, editable like Relics' own
        return "cobblegenetics";
    }

    /** The ability of a worn relic, if usable (unlocked and researched). */
    public AbilityData usable(LivingEntity entity, ItemStack stack) {
        AbilityData data = getRelicData(entity, stack).getAbilitiesData().getAbilityData(ability());
        return data != null && data.canPlayerUse(entity) ? data : null;
    }

    /** The worn relic of this kind with the highest usable value of that stat; null if none. */
    public Worn best(LivingEntity entity, String stat) {
        Worn best = null;
        for (ItemStack stack : EntityUtils.findEquippedCurios(entity, this)) {
            AbilityData data = usable(entity, stack);
            if (data == null) {
                continue;
            }
            double value = data.getStatData(stat).getValue();
            if (best == null || value > best.value()) {
                best = new Worn(stack, data, value);
            }
        }
        return best;
    }

    /** Adds relic experience (what levels it up) from one of its sources, and counts its statistic if any. */
    public void reward(LivingEntity entity, Worn worn, String source, double experience, String metric) {
        getRelicData(entity, worn.stack()).getLevelingData().addExperience(ability(), source, experience);
        if (metric != null) {
            worn.data().getStatisticData().getMetricData(metric).addValue(1.0);
        }
    }

    /** A worn relic: its stack, its ability and the value of the stat asked for. */
    public record Worn(ItemStack stack, AbilityData data, double value) {

        public boolean rank(String modifier) {
            return data.getRankModifierData(modifier).isEnabled();
        }
    }
}
