package com.cobblegenetics.block;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.breeding.BreedingService;
import com.cobblegenetics.breeding.EggHatching;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.item.PartySelect;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Incubator's state, in the lower half. EMPTY -> (two parents put in, free) WAITING -> (each parent fed a berry,
 * love hearts) BREEDING -> (time passes) EGG_READY -> (egg taken) EMPTY. While WAITING the owner can sneak-click to
 * take the pair back. The parents are stored as Cobblemon's own full Pokémon NBT, so nothing about them is lost while
 * they are inside, and they go back to the owner's party (or PC) the moment breeding finishes, online or not.
 *
 * <p>Clients only receive what they need to draw the case: each parent's species and aspects, the end time, and the
 * egg item.</p>
 */
public class GeneticIncubatorBlockEntity extends BlockEntity {

    public enum Phase {
        EMPTY, WAITING, BREEDING, EGG_READY
    }

    /**
     * What the client draws for one parent: the Poké Ball it was caught in (its species and aspects are kept for
     * addons and future use, but the case shows the balls: real models never fit every species inside the glass).
     */
    public record Display(String species, Set<String> aspects, String ball) {
    }

    private Phase phase = Phase.EMPTY;
    private UUID owner;
    private final List<CompoundTag> parents = new ArrayList<>();
    private final List<Display> displays = new ArrayList<>();
    /** How many of the parents were fed (0-2) while WAITING. */
    private int fed;
    private long endTime;
    private long startTime;
    private ItemStack egg = ItemStack.EMPTY;
    /** Set while a player is choosing parents, so two players cannot fill the same incubator at once. */
    private UUID choosing;
    /** Whether this Incubator has checked it is a villager job site since it was loaded (not saved). */
    private boolean poiChecked;
    /** A Day Care Aide is keeping the Egg warm right now (synced to clients for the label). */
    private boolean tended;
    /** Fraction of a step carried between seconds while an attendant tends the Egg (not saved). */
    private double attendantSteps;

    public GeneticIncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.INCUBATOR_ENTITY.get(), pos, state);
    }

    public Phase phase() {
        return phase;
    }

    public List<Display> displays() {
        return displays;
    }

    public int fed() {
        return fed;
    }

    public ItemStack egg() {
        return egg;
    }

    /** 0..1 progress of the current breeding (client side uses the synced times). */
    public float progress(long gameTime) {
        if (phase != Phase.BREEDING || endTime <= startTime) {
            return phase == Phase.EGG_READY ? 1 : 0;
        }
        return Math.max(0, Math.min(1, (gameTime - startTime) / (float) (endTime - startTime)));
    }

    // ------------------------------------------------------------------ interaction

    public void use(ServerPlayer player) {
        switch (phase) {
            case EMPTY -> startChoosing(player);
            case WAITING -> {
                if (player.isShiftKeyDown() && isOwner(player)) {
                    returnParents(player.serverLevel());
                    phase = Phase.EMPTY;
                    fed = 0;
                    owner = null;
                    changed();
                    player.displayClientMessage(Component.translatable("message.cobblegenetics.incubator_returned"),
                            true);
                } else {
                    player.displayClientMessage(Component.translatable("message.cobblegenetics.incubator_feed", fed)
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
            case BREEDING -> {
                long seconds = Math.max(0, (endTime - player.level().getGameTime()) / 20);
                player.displayClientMessage(Component.translatable("message.cobblegenetics.incubator_breeding",
                        seconds / 60, String.format("%02d", seconds % 60)).withStyle(ChatFormatting.AQUA), true);
            }
            case EGG_READY -> {
                // With an aide at work a click shows how it is going; sneak-click still takes the Egg
                if (tended && !player.isShiftKeyDown()) {
                    showDaycare(player);
                } else {
                    takeEgg(player);
                }
            }
        }
    }

    private void startChoosing(ServerPlayer player) {
        if (!GeneticsConfig.get().builtInBreeding) {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.incubator_disabled")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            return;
        }
        choosing = player.getUUID();
        boolean opened = PartySelect.open(player, Component.translatable("gui.cobblegenetics.select_parent1"),
                pokemon -> true, first -> selectSecond(player, first));
        if (!opened) {
            choosing = null;
            player.displayClientMessage(Component.translatable("message.cobblegenetics.empty_party"), true);
        }
    }

    private void selectSecond(ServerPlayer player, Pokemon first) {
        long time = player.serverLevel().getGameTime();
        boolean opened = PartySelect.open(player, Component.translatable("gui.cobblegenetics.select_parent2",
                        first.getDisplayName(false)),
                pokemon -> BreedingService.incompatibility(first, pokemon, time) == null,
                second -> start(player, first, second));
        if (!opened) {
            choosing = null;
            Component reason = Component.translatable("breeding.cobblegenetics.no_partner");
            for (Pokemon other : PartySelect.party(player)) {
                Component problem = BreedingService.incompatibility(first, other, time);
                if (problem != null && !other.getUuid().equals(first.getUuid())) {
                    reason = problem;
                    break;
                }
            }
            player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    /** Both parents chosen: re-check everything (time passed while the menus were open), then move them in. */
    private void start(ServerPlayer player, Pokemon first, Pokemon second) {
        if (isRemoved() || phase != Phase.EMPTY || !player.getUUID().equals(choosing)
                || player.distanceToSqr(worldPosition.getCenter()) > 64) {
            return;
        }
        choosing = null;
        Component problem = BreedingService.incompatibility(first, second, player.serverLevel().getGameTime());
        if (problem != null) {
            player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            return;
        }
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        parents.clear();
        displays.clear();
        for (Pokemon parent : List.of(first, second)) {
            if (parent.getEntity() != null) {
                parent.getEntity().discard();
            }
            parents.add(parent.saveToNBT(player.registryAccess(), new CompoundTag()));
            displays.add(new Display(parent.getSpecies().getResourceIdentifier().toString(),
                    new LinkedHashSet<>(parent.getAspects()), ballOf(parent)));
            party.remove(parent);
        }
        owner = player.getUUID();
        phase = Phase.WAITING;
        fed = 0;
        changed();
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.4f);
        player.sendSystemMessage(Component.translatable("message.cobblegenetics.incubator_inside",
                first.getDisplayName(false), second.getDisplayName(false)).withStyle(ChatFormatting.AQUA));
    }

    /**
     * Right-click with breeding food (the item tag cobblegenetics:breeding_food, Cobblemon's berries by default):
     * feeds the next parent, with love hearts. When both are fed, breeding starts. Returns true if the food was used.
     */
    public boolean feed(ServerPlayer player, ItemStack food) {
        if (phase != Phase.WAITING || !food.is(BREEDING_FOOD)) {
            return false;
        }
        int slot = fed;
        fed++;
        food.consume(1, player);
        ServerLevel serverLevel = player.serverLevel();
        double side = slot == 0 ? -0.19 : 0.19;
        net.minecraft.core.Direction facing = getBlockState().getValue(GeneticIncubatorBlock.FACING);
        // The same left/right as the renderer: across the facing direction
        double x = worldPosition.getX() + 0.5 + facing.getClockWise().getStepX() * side;
        double z = worldPosition.getZ() + 0.5 + facing.getClockWise().getStepZ() * side;
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, x, worldPosition.getY() + 1.0, z,
                6, 0.15, 0.25, 0.15, 0.05);
        serverLevel.playSound(null, worldPosition, SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 0.7f, 1.2f);
        if (fed >= 2) {
            phase = Phase.BREEDING;
            startTime = serverLevel.getGameTime();
            endTime = startTime + Math.round(GeneticsConfig.get().incubatorBreedingMinutes * 1200);
            serverLevel.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 1.3f);
            player.sendSystemMessage(Component.translatable("message.cobblegenetics.incubator_started",
                    Math.round(GeneticsConfig.get().incubatorBreedingMinutes * 10) / 10.0)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.incubator_feed", fed)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        changed();
        return true;
    }

    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> BREEDING_FOOD =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    CobbleGenetics.id("breeding_food"));

    private boolean isOwner(ServerPlayer player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** Item id of the ball a Pokémon was caught in (a plain Poké Ball when unknown). */
    private static String ballOf(Pokemon pokemon) {
        try {
            var item = pokemon.getCaughtBall().item();
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        } catch (RuntimeException e) {
            return "cobblemon:poke_ball";
        }
    }

    private void showDaycare(ServerPlayer player) {
        var data = egg.get(com.cobblegenetics.item.ModItems.EGG_DATA.get());
        double rate = GeneticsConfig.get().attendantStepsPerSecond;
        if (data == null || rate <= 0) {
            takeEgg(player);
            return;
        }
        long minutes = Math.max(1, Math.round(data.remaining() / rate / 60));
        player.displayClientMessage(Component.translatable("message.cobblegenetics.daycare_status",
                Math.round(data.progress() * 100), minutes).withStyle(ChatFormatting.GREEN), true);
    }

    public boolean tended() {
        return tended;
    }

    private void takeEgg(ServerPlayer player) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.incubator_not_yours")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack taken = egg;
        egg = ItemStack.EMPTY;
        tended = false;
        phase = Phase.EMPTY;
        owner = null;
        changed();
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8f, 0.8f);
        player.displayClientMessage(Component.translatable("message.cobblegenetics.egg_taken")
                .withStyle(ChatFormatting.GREEN), true);
    }

    // ------------------------------------------------------------------ breeding

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneticIncubatorBlockEntity incubator) {
        if (!incubator.poiChecked && level instanceof ServerLevel serverLevel) {
            incubator.poiChecked = true;
            ensureJobSite(serverLevel, pos, state);
        }
        if (incubator.phase == Phase.EGG_READY && level.getGameTime() % 20 == 0
                && level instanceof ServerLevel serverLevel) {
            try {
                incubator.tendEgg(serverLevel);
            } catch (RuntimeException e) {
                CobbleGenetics.failed("a Day Care Attendant tending an Egg", e);
            }
        }
        if (incubator.phase == Phase.BREEDING && level.getGameTime() >= incubator.endTime
                && level instanceof ServerLevel serverLevel) {
            try {
                incubator.finish(serverLevel);
            } catch (RuntimeException e) {
                CobbleGenetics.failed("finishing a breeding in the Incubator", e);
                // Whatever happened, the parents must not stay locked inside
                incubator.returnParents(serverLevel);
                incubator.phase = Phase.EMPTY;
                incubator.changed();
            }
        }
    }

    /**
     * Makes sure villagers see this Incubator as a job site (Day Care Attendant). Minecraft records points of interest
     * when a block is placed, and rescans only chunks it has never saved: Incubators placed before CobbleGenetics had
     * the profession would never be found by villagers without this.
     */
    private static void ensureJobSite(ServerLevel level, BlockPos pos, BlockState state) {
        try {
            var poi = level.getPoiManager();
            if (poi.getType(pos).isEmpty()) {
                net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state)
                        .ifPresent(type -> poi.add(pos, type));
            }
        } catch (RuntimeException e) {
            CobbleGenetics.failed("registering an Incubator as a villager job site", e);
        }
    }

    /**
     * Once a second while an Egg waits: a Day Care Attendant nearby keeps it warm, so it counts down without anyone
     * walking (config.attendantStepsPerSecond), and hatches straight to the owner's party or PC.
     */
    private void tendEgg(ServerLevel level) {
        double rate = GeneticsConfig.get().attendantStepsPerSecond;
        net.minecraft.world.entity.npc.Villager aide = owner == null || egg.isEmpty() || rate <= 0 ? null
                : com.cobblegenetics.village.ModVillagers.attendantNear(level, worldPosition);
        if ((aide != null) != tended) {
            // Clients draw the "Day Care" label and the wobble from this
            tended = aide != null;
            changed();
        }
        if (aide == null) {
            return;
        }
        if (level.getGameTime() % 60 == 0) {
            // Every few seconds: sparkles around the aide's head, so you can see who is looking after it
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, aide.getX(),
                    aide.getEyeY() + 0.4, aide.getZ(), 3, 0.25, 0.15, 0.25, 0);
        }
        var data = egg.get(com.cobblegenetics.item.ModItems.EGG_DATA.get());
        if (data == null) {
            return;
        }
        // Optional relics (Breeder's Apron): the owner's aides work faster
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            rate *= Math.max(0, com.cobblegenetics.compat.RelicHooks.get().aideSpeed(ownerPlayer));
        }
        attendantSteps += rate;
        int steps = (int) attendantSteps;
        attendantSteps -= steps;
        if (steps <= 0) {
            return;
        }
        int remaining = data.remaining() - steps;
        if (remaining > 0) {
            egg.set(com.cobblegenetics.item.ModItems.EGG_DATA.get(), data.withRemaining(remaining));
            // The label's percentage on clients: an update every 5 seconds is plenty
            if (level.getGameTime() % 100 == 0) {
                changed();
            } else {
                setChanged();
            }
            if (level.random.nextInt(2) == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5,
                        2, 0.2, 0.2, 0.2, 0);
            }
            return;
        }
        if (EggHatching.hatchFor(level, owner, data, worldPosition)) {
            egg = ItemStack.EMPTY;
            phase = Phase.EMPTY;
            owner = null;
            attendantSteps = 0;
            tended = false;
            changed();
        }
    }

    private void finish(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
        List<Pokemon> loaded = new ArrayList<>();
        for (CompoundTag tag : parents) {
            loaded.add(Pokemon.Companion.loadFromNBT(level.registryAccess(), tag));
        }
        if (loaded.size() != 2) {
            returnParents(level);
            phase = Phase.EMPTY;
            changed();
            return;
        }
        BreedingService.Offspring offspring = BreedingService.breed(player, level, worldPosition, loaded.get(0),
                loaded.get(1));
        // The parents (updated: offspring count, cooldown) go home first
        parents.clear();
        displays.clear();
        for (Pokemon parent : loaded) {
            giveToOwner(level, parent);
        }
        if (GeneticsConfig.get().incubatorEggs) {
            egg = EggHatching.createEgg(level.registryAccess(), offspring.child(), offspring.mother(),
                    offspring.father());
            phase = Phase.EGG_READY;
        } else {
            giveToOwner(level, offspring.child());
            phase = Phase.EMPTY;
        }
        changed();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1, 1);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(GeneticsConfig.get().incubatorEggs
                            ? (offspring.fusion() ? "message.cobblegenetics.incubator_done_fusion"
                            : "message.cobblegenetics.incubator_done") : "message.cobblegenetics.bred",
                    offspring.mother().getDisplayName(false), offspring.father().getDisplayName(false),
                    offspring.child().getDisplayName(false)).withStyle(ChatFormatting.GREEN));
            if (offspring.mutation() != null) {
                player.sendSystemMessage(Component.translatable("message.cobblegenetics.bred_mutation")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    /** Party first, PC when full: works for offline owners too (Cobblemon loads their store). */
    private void giveToOwner(ServerLevel level, Pokemon pokemon) {
        if (owner == null) {
            return;
        }
        Cobblemon.INSTANCE.getStorage().getParty(owner, level.registryAccess()).add(pokemon);
    }

    private void returnParents(ServerLevel level) {
        for (CompoundTag tag : parents) {
            try {
                giveToOwner(level, Pokemon.Companion.loadFromNBT(level.registryAccess(), tag));
            } catch (RuntimeException e) {
                CobbleGenetics.LOGGER.error("CobbleGenetics: could not return a parent from the Incubator at {}: {}",
                        worldPosition, tag, e);
            }
        }
        parents.clear();
        displays.clear();
    }

    /** The block is being removed: parents go back to their owner, a waiting egg drops. */
    void emptyOnBreak(ServerLevel level, BlockPos pos) {
        returnParents(level);
        if (!egg.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, egg);
            egg = ItemStack.EMPTY;
        }
        phase = Phase.EMPTY;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("phase", phase.name());
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        ListTag list = new ListTag();
        list.addAll(parents);
        tag.put("parents", list);
        writeDisplay(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.EMPTY;
        }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        if (tag.contains("parents")) {
            parents.clear();
            ListTag list = tag.getList("parents", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                parents.add(list.getCompound(i));
            }
        }
        startTime = tag.getLong("start");
        fed = tag.getInt("fed");
        endTime = tag.getLong("end");
        tended = tag.getBoolean("tended");
        egg = tag.contains("egg") ? ItemStack.parseOptional(registries, tag.getCompound("egg")) : ItemStack.EMPTY;
        displays.clear();
        ListTag shown = tag.getList("displays", Tag.TAG_COMPOUND);
        for (int i = 0; i < shown.size(); i++) {
            CompoundTag entry = shown.getCompound(i);
            Set<String> aspects = new LinkedHashSet<>();
            ListTag aspectList = entry.getList("aspects", Tag.TAG_STRING);
            for (int j = 0; j < aspectList.size(); j++) {
                aspects.add(aspectList.getString(j));
            }
            String ball = entry.contains("ball") ? entry.getString("ball") : "cobblemon:poke_ball";
            displays.add(new Display(entry.getString("species"), aspects, ball));
        }
    }

    /** The fields both saved and sent to clients. */
    private void writeDisplay(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("phase", phase.name());
        tag.putLong("start", startTime);
        tag.putInt("fed", fed);
        tag.putLong("end", endTime);
        tag.putBoolean("tended", tended);
        if (!egg.isEmpty()) {
            tag.put("egg", egg.save(registries));
        }
        ListTag shown = new ListTag();
        for (Display display : displays) {
            CompoundTag entry = new CompoundTag();
            entry.putString("species", display.species());
            entry.putString("ball", display.ball());
            ListTag aspects = new ListTag();
            display.aspects().forEach(aspect -> aspects.add(StringTag.valueOf(aspect)));
            entry.put("aspects", aspects);
            shown.add(entry);
        }
        tag.put("displays", shown);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // No parents' NBT or owner for clients: only what the case shows
        CompoundTag tag = new CompoundTag();
        writeDisplay(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
