package com.cobblegenetics.item;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.callback.PartySelectCallbacks;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Opens Cobblemon's own party selection screen (the same one its items use) and calls back with the choice. */
public final class PartySelect {

    private PartySelect() {
    }

    public static List<Pokemon> party(ServerPlayer player) {
        List<Pokemon> party = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon != null) {
                party.add(pokemon);
            }
        }
        return party;
    }

    /** Returns false (and opens nothing) when no party member passes the filter. */
    public static boolean open(ServerPlayer player, Component title, Predicate<Pokemon> selectable,
                               Consumer<Pokemon> chosen) {
        List<Pokemon> party = party(player);
        if (party.stream().noneMatch(selectable)) {
            return false;
        }
        PartySelectCallbacks.INSTANCE.createFromPokemon(player, title, party,
                pokemon -> selectable.test(pokemon),
                cancelledBy -> Unit.INSTANCE,
                pokemon -> {
                    // The callback runs later: re-check that the Pokémon is still in the party
                    if (party(player).contains(pokemon) && selectable.test(pokemon)) {
                        chosen.accept(pokemon);
                    }
                    return Unit.INSTANCE;
                });
        return true;
    }
}
