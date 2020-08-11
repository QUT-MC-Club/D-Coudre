package fr.catcore.deacoudre.game.map;

import xyz.nucleoid.plasmid.game.map.template.MapTemplate;
import xyz.nucleoid.plasmid.game.map.template.TemplateChunkGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public class DeACoudreMap {
    private final MapTemplate template;
    private final DeACoudreMapConfig config;

    private BlockPos spawn = BlockPos.ORIGIN;

    public DeACoudreMap(MapTemplate template, DeACoudreMapConfig config) {
        this.template = template;
        this.config = config;
    }

    public void setSpawn(BlockPos spawn) {
        this.spawn = spawn;
    }

    public BlockPos getSpawn() {
        return spawn;
    }

    public MapTemplate getTemplate() {
        return template;
    }

    public ChunkGenerator asGenerator() {
        return new TemplateChunkGenerator(this.template, BlockPos.ORIGIN);
    }
}
