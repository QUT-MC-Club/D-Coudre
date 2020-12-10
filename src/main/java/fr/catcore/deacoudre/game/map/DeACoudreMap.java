package fr.catcore.deacoudre.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.map.template.TemplateChunkGenerator;
import xyz.nucleoid.plasmid.util.BlockBounds;

public class DeACoudreMap {
    private final MapTemplate template;
    private final DeACoudreMapConfig config;

    private BlockPos spawn = BlockPos.ORIGIN;

    private BlockBounds pool = BlockBounds.EMPTY;
    private BlockBounds jumpingPlatform = BlockBounds.EMPTY;
    private BlockBounds jumpingArea = BlockBounds.EMPTY;

    public DeACoudreMap(MapTemplate template, DeACoudreMapConfig config) {
        this.template = template;
        this.config = config;
    }

    public void setSpawn(BlockPos spawn) {
        this.spawn = spawn;
    }

    public void setPool(BlockBounds pool) {
        this.pool = pool;
    }

    public void setJumpingPlatform(BlockBounds platform) {
        this.jumpingPlatform = platform;
    }

    public void setJumpingArea(BlockBounds area) {
        this.jumpingArea = area;
    }

    public BlockPos getSpawn() {
        return spawn;
    }

    public BlockBounds getPool() {
        return this.pool;
    }

    public BlockBounds getJumpingPlatform() {
        return this.jumpingPlatform;
    }

    public BlockBounds getJumpingArea() {
        return this.jumpingArea;
    }

    public MapTemplate getTemplate() {
        return template;
    }

    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }
}
