package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.map.DeACoudreMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.util.Map;
import java.util.Random;

public final class DeACoudrePool {
    private final GameSpace gameSpace;
    private final BlockBounds bounds;

    private final Map<ServerPlayerEntity, BlockState> playerPalette = new Object2ObjectOpenHashMap<>();

    public DeACoudrePool(GameSpace gameSpace, DeACoudreMap map) {
        this.gameSpace = gameSpace;
        this.bounds = map.getPool();
    }

    private BlockState getBlockForPlayer(ServerPlayerEntity player) {
        BlockState block = this.playerPalette.get(player);
        if (block == null) {
            Random random = this.gameSpace.getWorld().random;
            block = DeACoudreConfig.PLAYER_PALETTE[random.nextInt(DeACoudreConfig.PLAYER_PALETTE.length)];
            this.playerPalette.put(player, block);
        }
        return block;
    }

    public void putBlockAt(ServerPlayerEntity player, BlockPos pos) {
        BlockState block = this.getBlockForPlayer(player);
        this.gameSpace.getWorld().setBlockState(pos, block);
    }

    public void putCoudreAt(BlockPos pos) {
        this.gameSpace.getWorld().setBlockState(pos, Blocks.EMERALD_BLOCK.getDefaultState());
    }

    public boolean canFormCoudreAt(BlockPos pos) {
        return !this.isFreeAt(pos.west()) && !this.isFreeAt(pos.east())
                && !this.isFreeAt(pos.north()) && !this.isFreeAt(pos.south());
    }

    public boolean isFreeAt(BlockPos pos) {
        ServerWorld world = this.gameSpace.getWorld();
        return world.getBlockState(pos) == Blocks.WATER.getDefaultState();
    }

    public boolean isFull() {
        for (BlockPos pos : this.bounds) {
            if (this.isFreeAt(pos)) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(BlockPos pos) {
        return this.bounds.contains(pos);
    }
}
