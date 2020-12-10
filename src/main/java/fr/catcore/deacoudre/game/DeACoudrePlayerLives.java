package fr.catcore.deacoudre.game;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Iterator;
import java.util.Set;

public final class DeACoudrePlayerLives implements Iterable<Object2IntMap.Entry<ServerPlayerEntity>> {
    private final Object2IntOpenHashMap<ServerPlayerEntity> map = new Object2IntOpenHashMap<>();

    public void addPlayers(Set<ServerPlayerEntity> players, int lives) {
        for (ServerPlayerEntity player : players) {
            this.map.put(player, lives);
            player.setExperienceLevel(lives);
        }
    }

    public int grantLife(ServerPlayerEntity player) {
        int remaining = this.map.addTo(player, 1) + 1;
        player.setExperienceLevel(remaining);
        return remaining;
    }

    public int takeLife(ServerPlayerEntity player) {
        int remaining = this.map.addTo(player, -1) - 1;
        remaining = Math.max(remaining, 0);
        player.setExperienceLevel(remaining);
        return remaining;
    }

    public void removePlayer(ServerPlayerEntity player) {
        this.map.removeInt(player);
    }

    @Override
    public Iterator<Object2IntMap.Entry<ServerPlayerEntity>> iterator() {
        return this.map.object2IntEntrySet().fastIterator();
    }
}
