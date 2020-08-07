package fr.catcore.deacoudre.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.gegy1000.plasmid.game.config.GameConfig;
import net.gegy1000.plasmid.game.config.GameMapConfig;
import net.gegy1000.plasmid.game.config.PlayerConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class DeACoudreConfig implements GameConfig {

    public static final Codec<DeACoudreConfig> CODEC = RecordCodecBuilder.create(instance -> {
        Codec<GameMapConfig<DeACoudreConfig>> mapCodec = GameMapConfig.codec();

        return instance.group(
                mapCodec.fieldOf("map").forGetter(DeACoudreConfig::getMapConfig),
                PlayerConfig.CODEC.fieldOf("players").forGetter(DeACoudreConfig::getPlayerConfig),
                Codec.INT.fieldOf("life").forGetter(DeACoudreConfig::getLife)
        ).apply(instance, DeACoudreConfig::new);
    });

    private final GameMapConfig<DeACoudreConfig> mapConfig;
    private final PlayerConfig playerConfig;
    private static final BlockState[] playerBlocks;
    private final int life;

    public DeACoudreConfig(
            GameMapConfig<DeACoudreConfig> mapConfig,
            PlayerConfig playerConfig,
            int life
    ) {
        this.mapConfig = mapConfig;
        this.playerConfig = playerConfig;
        this.life = life;
    }

    public int getLife() {
        return life;
    }

    public GameMapConfig<DeACoudreConfig> getMapConfig() {
        return mapConfig;
    }

    public BlockState[] getPlayerBlocks() {
        return playerBlocks;
    }

    public PlayerConfig getPlayerConfig() {
        return playerConfig;
    }

    static {
        playerBlocks = new BlockState[]{
                Blocks.BLACK_WOOL.getDefaultState(),
                Blocks.BROWN_WOOL.getDefaultState(),
                Blocks.GRAY_WOOL.getDefaultState(),
                Blocks.GREEN_WOOL.getDefaultState(),
                Blocks.LIGHT_GRAY_WOOL.getDefaultState(),
                Blocks.LIME_WOOL.getDefaultState(),
                Blocks.MAGENTA_WOOL.getDefaultState(),
                Blocks.ORANGE_WOOL.getDefaultState(),
                Blocks.PINK_WOOL.getDefaultState(),
                Blocks.PURPLE_WOOL.getDefaultState(),
                Blocks.RED_WOOL.getDefaultState(),
                Blocks.WHITE_WOOL.getDefaultState(),
                Blocks.YELLOW_WOOL.getDefaultState(),
                Blocks.TERRACOTTA.getDefaultState(),
                Blocks.BLACK_TERRACOTTA.getDefaultState(),
                Blocks.BROWN_TERRACOTTA.getDefaultState(),
                Blocks.GRAY_TERRACOTTA.getDefaultState(),
                Blocks.GREEN_TERRACOTTA.getDefaultState(),
                Blocks.LIGHT_GRAY_TERRACOTTA.getDefaultState(),
                Blocks.LIME_TERRACOTTA.getDefaultState(),
                Blocks.MAGENTA_TERRACOTTA.getDefaultState(),
                Blocks.ORANGE_TERRACOTTA.getDefaultState(),
                Blocks.PINK_TERRACOTTA.getDefaultState(),
                Blocks.PURPLE_TERRACOTTA.getDefaultState(),
                Blocks.RED_TERRACOTTA.getDefaultState(),
                Blocks.WHITE_TERRACOTTA.getDefaultState(),
                Blocks.YELLOW_TERRACOTTA.getDefaultState(),
                Blocks.GLASS.getDefaultState(),
                Blocks.BLACK_STAINED_GLASS.getDefaultState(),
                Blocks.BROWN_STAINED_GLASS.getDefaultState(),
                Blocks.GRAY_STAINED_GLASS.getDefaultState(),
                Blocks.GREEN_STAINED_GLASS.getDefaultState(),
                Blocks.LIGHT_GRAY_STAINED_GLASS.getDefaultState(),
                Blocks.LIME_STAINED_GLASS.getDefaultState(),
                Blocks.MAGENTA_STAINED_GLASS.getDefaultState(),
                Blocks.ORANGE_STAINED_GLASS.getDefaultState(),
                Blocks.PINK_STAINED_GLASS.getDefaultState(),
                Blocks.PURPLE_STAINED_GLASS.getDefaultState(),
                Blocks.RED_STAINED_GLASS.getDefaultState(),
                Blocks.WHITE_STAINED_GLASS.getDefaultState(),
                Blocks.YELLOW_STAINED_GLASS.getDefaultState(),
                Blocks.BLACK_CONCRETE.getDefaultState(),
                Blocks.BROWN_CONCRETE.getDefaultState(),
                Blocks.GRAY_CONCRETE.getDefaultState(),
                Blocks.GREEN_CONCRETE.getDefaultState(),
                Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(),
                Blocks.LIME_CONCRETE.getDefaultState(),
                Blocks.MAGENTA_CONCRETE.getDefaultState(),
                Blocks.ORANGE_CONCRETE.getDefaultState(),
                Blocks.PINK_CONCRETE.getDefaultState(),
                Blocks.PURPLE_CONCRETE.getDefaultState(),
                Blocks.RED_CONCRETE.getDefaultState(),
                Blocks.WHITE_CONCRETE.getDefaultState(),
                Blocks.YELLOW_CONCRETE.getDefaultState(),
                Blocks.BLACK_CONCRETE_POWDER.getDefaultState(),
                Blocks.BROWN_CONCRETE_POWDER.getDefaultState(),
                Blocks.GRAY_CONCRETE_POWDER.getDefaultState(),
                Blocks.GREEN_CONCRETE_POWDER.getDefaultState(),
                Blocks.LIGHT_GRAY_CONCRETE_POWDER.getDefaultState(),
                Blocks.LIME_CONCRETE_POWDER.getDefaultState(),
                Blocks.MAGENTA_CONCRETE_POWDER.getDefaultState(),
                Blocks.ORANGE_CONCRETE_POWDER.getDefaultState(),
                Blocks.PINK_CONCRETE_POWDER.getDefaultState(),
                Blocks.PURPLE_CONCRETE_POWDER.getDefaultState(),
                Blocks.RED_CONCRETE_POWDER.getDefaultState(),
                Blocks.WHITE_CONCRETE_POWDER.getDefaultState(),
                Blocks.YELLOW_CONCRETE_POWDER.getDefaultState()
        };
    }
}
