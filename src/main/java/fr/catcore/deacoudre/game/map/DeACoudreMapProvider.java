package fr.catcore.deacoudre.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.catcore.deacoudre.game.DeACoudreConfig;
import net.gegy1000.plasmid.game.map.GameMap;
import net.gegy1000.plasmid.game.map.GameMapBuilder;
import net.gegy1000.plasmid.game.map.provider.MapProvider;
import net.gegy1000.plasmid.world.BlockBounds;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.CompletableFuture;

public class DeACoudreMapProvider implements MapProvider<DeACoudreConfig> {

    public static final Codec<DeACoudreMapProvider> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.INT.fieldOf("radius").forGetter(map -> map.radius),
                Codec.INT.fieldOf("height").forGetter(map -> map.height)
        ).apply(instance, DeACoudreMapProvider::new);
    });

    private final int height;
    private final int radius;

    public DeACoudreMapProvider(int radius, int height) {
        this.height = height;
        this.radius = radius;
    }

    @Override
    public CompletableFuture<GameMap> createAt(ServerWorld serverWorld, BlockPos blockPos, DeACoudreConfig deACoudreConfig) {
        int maxRadius = this.radius + 20;
        int maxHeight = this.height + 20;
        BlockBounds bounds = new BlockBounds(
                new BlockPos(-maxRadius, 0, -maxRadius + 10),
                new BlockPos(maxRadius, maxHeight, maxRadius + 10)
        );

        GameMapBuilder builder = GameMapBuilder.open(serverWorld, blockPos, bounds);

        return CompletableFuture.supplyAsync(() -> {
            this.buildMap(builder, deACoudreConfig);
            return builder.build();
        });
    }

    private void buildMap(GameMapBuilder builder, DeACoudreConfig config) {
        this.buildSpawn(builder, config);
        this.buildPool(builder, config);
        this.buildJumpingPlatform(builder, config);
    }

    private void buildSpawn(GameMapBuilder builder, DeACoudreConfig config) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                mutable.set(x, 1, z);
                builder.setBlockState(mutable, Blocks.SPRUCE_PLANKS.getDefaultState());
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 2; y <= 4; y++) {
                    if (x == -4 || x == 4 || z == -4 || z == 4) {
                        mutable.set(x, y, z);
                        builder.setBlockState(mutable, Blocks.BARRIER.getDefaultState());
                    }
                }
            }
        }
    }

    private void buildPool(GameMapBuilder builder, DeACoudreConfig config) {
        BlockPos.Mutable mutablePosWater = new BlockPos.Mutable();
        BlockPos.Mutable mutablePosBorder = new BlockPos.Mutable();

        for (int z = 5 + radius + (-radius); z <= 5 + (2* radius); z++) {
            for (int x = -radius; x <= radius; x++) {
                mutablePosBorder.set(x, 0, z);
                builder.setBlockState(mutablePosBorder, Blocks.SPRUCE_WOOD.getDefaultState());
            }
        }
        for (int z = 5 + radius + (-radius); z <= 5 + (2* radius); z++) {
            for (int x = -radius; x <= radius; x++) {
                mutablePosBorder.set(x, 1, z);
                mutablePosWater.set(x, 1, z);
                if (z == 5 + radius + (-radius) || z == 5 + (2* radius) || x == -radius || x == radius) builder.setBlockState(mutablePosBorder, Blocks.SPRUCE_WOOD.getDefaultState());
                else builder.setBlockState(mutablePosWater, Blocks.WATER.getDefaultState());
            }
        }
    }

    private void buildJumpingPlatform(GameMapBuilder builder, DeACoudreConfig config) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int minZ = 5 + (2* radius) + 1;

        for (int z = minZ; z <= minZ + 3; z++) {
            for (int x = -1; x <= 1; x++) {
                mutable.set(x, height, z);
                builder.setBlockState(mutable, Blocks.SPRUCE_PLANKS.getDefaultState());
            }
        }
        BlockBounds bounds = new BlockBounds(
                new BlockPos(-1, height, minZ),
                new BlockPos(1, height, minZ + 3)
        );
        builder.addRegion("jumpingArea", bounds);
    }

    @Override
    public Codec<? extends MapProvider<?>> getCodec() {
        return CODEC;
    }
}
