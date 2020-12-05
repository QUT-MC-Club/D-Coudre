package fr.catcore.deacoudre.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.util.BlockBounds;

public class DeACoudreMapConfig {

    public static final Codec<DeACoudreMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.INT.fieldOf("radius").forGetter(map -> map.radius),
                Codec.INT.fieldOf("height").forGetter(map -> map.height),
                Codec.STRING.fieldOf("shape").forGetter(map -> map.shape),
                Codec.INT.optionalFieldOf("in_circle_radius", 3).forGetter(map -> map.inCircleRadius),
                BlockState.CODEC.fieldOf("spawn_block").forGetter(map -> map.spawnBlock),
                BlockState.CODEC.fieldOf("pool_outline_block").forGetter(map -> map.poolOutlineBlock),
                BlockState.CODEC.fieldOf("jump_platform_block").forGetter(map -> map.jumpPlatformBlock)
        ).apply(instance, DeACoudreMapConfig::new);
    });

    public final int height;
    public final int radius;
    public final String shape;
    public final int inCircleRadius;
    public final BlockState spawnBlock;
    public final BlockState poolOutlineBlock;
    public final BlockState jumpPlatformBlock;

    public DeACoudreMapConfig(int radius, int height, String shape, int inCircleRadius, BlockState spawnBlock, BlockState poolOutlineBlock, BlockState jumpPlatformBlock) {
        this.height = height + 1;
        this.radius = radius;
        this.shape = shape;
        this.inCircleRadius = inCircleRadius;
        this.spawnBlock = spawnBlock;
        this.poolOutlineBlock = poolOutlineBlock;
        this.jumpPlatformBlock = jumpPlatformBlock;
    }

    public enum MapShape {
        square((config, builder, mutablePosWater, mutablePosBorder) -> {
            BlockBounds blockBounds = BlockBounds.EMPTY;
            for (int z = 5; z <= 5 + (2 * config.radius); z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    mutablePosBorder.set(x, 1, z);
                    builder.setBlockState(mutablePosBorder, config.poolOutlineBlock);
                }
            }
            for (int z = 5; z <= 5 + (2 * config.radius); z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    mutablePosBorder.set(x, 2, z);
                    mutablePosWater.set(x, 2, z);
                    if (z == 5 || z == 5 + (2 * config.radius) || x == -config.radius || x == config.radius)
                        builder.setBlockState(mutablePosBorder, config.poolOutlineBlock);
                    else {
                        builder.setBlockState(mutablePosWater, Blocks.WATER.getDefaultState());
                        blockBounds = blockBounds.union(BlockBounds.of(mutablePosWater));
                    }
                }
            }

            return blockBounds;
        }),
        circle((config, builder, mutablePosWater, mutablePosBorder) -> {
            BlockBounds blockBounds = BlockBounds.EMPTY;
            int radius2 = config.radius * config.radius;
            int outlineRadius2 = (config.radius - 1) * (config.radius - 1);
            for (int z = -config.radius; z <= config.radius; z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    int distance2 = x * x + z * z;

                    mutablePosBorder.set(x, 1, getRightZ(config, z));
                    builder.setBlockState(mutablePosBorder, config.poolOutlineBlock);

                    if (distance2 <= outlineRadius2) {
                        mutablePosWater.set(x, 2, getRightZ(config, z));
                        builder.setBlockState(mutablePosWater, Blocks.WATER.getDefaultState());
                        blockBounds = blockBounds.union(BlockBounds.of(mutablePosWater));
                    } else {
                        mutablePosBorder.set(x, 2, getRightZ(config, z));
                        builder.setBlockState(mutablePosBorder, config.poolOutlineBlock);
                    }
                }
            }

            return blockBounds;
        }),
        donut((config, builder, mutablePosWater, mutablePosBorder) -> {
            BlockBounds blockBounds = BlockBounds.EMPTY;
            int radius2 = config.radius * config.radius;
            int outlineRadius2 = (config.radius - 1) * (config.radius - 1);
            int inlineRadius = (config.inCircleRadius - 1) * (config.inCircleRadius - 1);
            for (int z = -config.radius; z <= config.radius; z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    int distance2 = x * x + z * z;

                    mutablePosBorder.set(x, 1, getRightZ(config, z));
                    builder.setBlockState(mutablePosBorder, config.poolOutlineBlock);

                    if (distance2 <= outlineRadius2 && distance2 > inlineRadius) {
                        mutablePosWater.set(x, 2, getRightZ(config, z));
                        builder.setBlockState(mutablePosWater, Blocks.WATER.getDefaultState());
                        blockBounds = blockBounds.union(BlockBounds.of(mutablePosWater));
                    } else {
                        mutablePosBorder.set(x, 2, getRightZ(config, z));
                        builder.setBlockState(mutablePosBorder, config.poolOutlineBlock);
                    }
                }
            }

            return blockBounds;
        });

        private GeneratePool generatePool;

        MapShape(GeneratePool generatePool) {
            this.generatePool = generatePool;
        }

        public BlockBounds generatePool(DeACoudreMapConfig config, MapTemplate builder, BlockPos.Mutable mutablePosWater, BlockPos.Mutable mutablePosBorder) {
            return this.generatePool.generatePool(config, builder, mutablePosWater, mutablePosBorder);
        }

        private static int getRightZ(DeACoudreMapConfig config, int z) {
            return 5 + (z - -config.radius);
        }

        private interface GeneratePool {

            BlockBounds generatePool(DeACoudreMapConfig config, MapTemplate builder, BlockPos.Mutable mutablePosWater, BlockPos.Mutable mutablePosBorder);
        }
    }
}
