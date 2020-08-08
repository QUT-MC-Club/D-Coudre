package fr.catcore.deacoudre.game.map;

import fr.catcore.deacoudre.game.DeACoudreConfig;
import net.gegy1000.plasmid.game.map.template.MapTemplate;
import net.gegy1000.plasmid.util.BlockBounds;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.CompletableFuture;

public class DeACoudreMapGenerator {

    private final DeACoudreMapConfig config;

    public DeACoudreMapGenerator(DeACoudreMapConfig config) {
        this.config = config;
    }

    public CompletableFuture<DeACoudreMap> create() {
        return CompletableFuture.supplyAsync(this::build, Util.getServerWorkerExecutor());
    }

    private DeACoudreMap build() {
        MapTemplate template = MapTemplate.createEmpty();
        DeACoudreMap map = new DeACoudreMap(template, this.config);

        this.buildSpawn(template);
        this.buildPool(template);
        this.buildJumpingPlatform(template);

        map.setSpawn(new BlockPos(0,3,0));

        return map;
    }

    private void buildSpawn(MapTemplate builder) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                mutable.set(x, 2, z);
                builder.setBlockState(mutable, Blocks.SPRUCE_PLANKS.getDefaultState());
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

    private void buildPool(MapTemplate builder) {
        BlockPos.Mutable mutablePosWater = new BlockPos.Mutable();
        BlockPos.Mutable mutablePosBorder = new BlockPos.Mutable();

        for (int z = 5 + config.radius + (-config.radius); z <= 5 + (2* config.radius); z++) {
            for (int x = -config.radius; x <= config.radius; x++) {
                mutablePosBorder.set(x, 1, z);
                builder.setBlockState(mutablePosBorder, Blocks.SPRUCE_WOOD.getDefaultState());
            }
        }
        for (int z = 5 + config.radius + (-config.radius); z <= 5 + (2* config.radius); z++) {
            for (int x = -config.radius; x <= config.radius; x++) {
                mutablePosBorder.set(x, 2, z);
                mutablePosWater.set(x, 2, z);
                if (z == 5 + config.radius + (-config.radius) || z == 5 + (2* config.radius) || x == -config.radius || x == config.radius) builder.setBlockState(mutablePosBorder, Blocks.SPRUCE_WOOD.getDefaultState());
                else builder.setBlockState(mutablePosWater, Blocks.WATER.getDefaultState());
            }
        }
    }

    private void buildJumpingPlatform(MapTemplate builder) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos.Mutable barrierPos = new BlockPos.Mutable();
        int minZ = 5 + (2* config.radius) + 1;

        for (int z = minZ; z <= minZ + 3; z++) {
            for (int x = -1; x <= 1; x++) {
                mutable.set(x, config.height + 1, z);
                builder.setBlockState(mutable, Blocks.SPRUCE_PLANKS.getDefaultState());
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
                new BlockPos(2, config.height + 2, minZ + 1)
        };

        for (BlockPos pos : barrier) {
            barrierPos.set(pos.getX(), pos.getY(), pos.getZ());
            builder.setBlockState(barrierPos, Blocks.BARRIER.getDefaultState());
        }
        BlockBounds bounds = new BlockBounds(
                new BlockPos(-1, config.height, minZ),
                new BlockPos(1, config.height, minZ + 3)
        );
        builder.addRegion("jumpingArea", bounds);
    }
}
