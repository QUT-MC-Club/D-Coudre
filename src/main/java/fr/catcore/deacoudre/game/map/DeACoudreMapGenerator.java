package fr.catcore.deacoudre.game.map;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.util.BlockBounds;

public class DeACoudreMapGenerator {

    private final DeACoudreMapConfig config;
    private final DeACoudreMapConfig.MapShape shape;

    public DeACoudreMapGenerator(DeACoudreMapConfig config) {
        this.config = config;
        this.shape = DeACoudreMapConfig.MapShape.valueOf(config.shape);
    }

    public DeACoudreMap build() {
        MapTemplate template = MapTemplate.createEmpty();
        DeACoudreMap map = new DeACoudreMap(template, this.config);

        this.buildSpawn(template);
        this.buildPool(map, template);
        this.buildJumpingPlatform(map, template);

        map.setSpawn(new BlockPos(0,3,0));

        return map;
    }

    private void buildSpawn(MapTemplate builder) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                mutable.set(x, 2, z);
                builder.setBlockState(mutable, this.config.spawnBlock);
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 3; y <= 6; y++) {
                    if (x == -4 || x == 4 || z == -4 || z == 4) {
                        mutable.set(x, y, z);
                        builder.setBlockState(mutable, Blocks.BARRIER.getDefaultState());
                    }
                    if (y == 6) {
                        mutable.set(x, y, z);
                        builder.setBlockState(mutable, Blocks.BARRIER.getDefaultState());
                    }
                }
            }
        }
    }

    private void buildPool(DeACoudreMap map, MapTemplate builder) {
        BlockPos.Mutable mutablePosWater = new BlockPos.Mutable();
        BlockPos.Mutable mutablePosBorder = new BlockPos.Mutable();

        BlockBounds bounds = shape.generatePool(this.config, builder, mutablePosWater, mutablePosBorder);
        map.setPool(bounds);
    }

    private void buildJumpingPlatform(DeACoudreMap map, MapTemplate builder) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos.Mutable barrierPos = new BlockPos.Mutable();
        int minZ = 5 + (2* config.radius) + 1;

        for (int z = minZ; z <= minZ + 3; z++) {
            for (int x = -1; x <= 1; x++) {
                mutable.set(x, config.height + 1, z);
                builder.setBlockState(mutable, this.config.jumpPlatformBlock);
            }
        }
        BlockPos[] barrier = new BlockPos[]{
                new BlockPos(-2, config.height + 1, minZ + 1),
                new BlockPos(-2, config.height + 1, minZ + 2),
                new BlockPos(-2, config.height + 1, minZ + 3),
                new BlockPos(-2, config.height + 1, minZ + 4),
                new BlockPos(-1, config.height + 1, minZ + 4),
                new BlockPos(0, config.height + 1, minZ + 4),
                new BlockPos(1, config.height + 1, minZ + 4),
                new BlockPos(2, config.height + 1, minZ + 4),
                new BlockPos(2, config.height + 1, minZ + 3),
                new BlockPos(2, config.height + 1, minZ + 2),
                new BlockPos(2, config.height + 1, minZ + 1),

                new BlockPos(-2, config.height + 2, minZ + 1),
                new BlockPos(-2, config.height + 2, minZ + 2),
                new BlockPos(-2, config.height + 2, minZ + 3),
                new BlockPos(-2, config.height + 2, minZ + 4),
                new BlockPos(-1, config.height + 2, minZ + 4),
                new BlockPos(0, config.height + 2, minZ + 4),
                new BlockPos(1, config.height + 2, minZ + 4),
                new BlockPos(2, config.height + 2, minZ + 4),
                new BlockPos(2, config.height + 2, minZ + 3),
                new BlockPos(2, config.height + 2, minZ + 2),
                new BlockPos(2, config.height + 2, minZ + 1),

                new BlockPos(-2, config.height + 3, minZ + 1),
                new BlockPos(-2, config.height + 3, minZ + 2),
                new BlockPos(-2, config.height + 3, minZ + 3),
                new BlockPos(-2, config.height + 3, minZ + 4),
                new BlockPos(-1, config.height + 3, minZ + 4),
                new BlockPos(0, config.height + 3, minZ + 4),
                new BlockPos(1, config.height + 3, minZ + 4),
                new BlockPos(2, config.height + 3, minZ + 4),
                new BlockPos(2, config.height + 3, minZ + 3),
                new BlockPos(2, config.height + 3, minZ + 2),
                new BlockPos(2, config.height + 3, minZ + 1)
        };

        for (BlockPos pos : barrier) {
            barrierPos.set(pos.getX(), pos.getY(), pos.getZ());
            builder.setBlockState(barrierPos, Blocks.BARRIER.getDefaultState());
        }

        map.setJumpingPlatform(new BlockBounds(
                new BlockPos(-1, config.height, minZ-1),
                new BlockPos(1, config.height, minZ + 3)
        ));
        map.setJumpingArea(new BlockBounds(
                new BlockPos(-2, config.height, minZ-1),
                new BlockPos(2, config.height + 2, minZ + 4)
        ));
    }
}
