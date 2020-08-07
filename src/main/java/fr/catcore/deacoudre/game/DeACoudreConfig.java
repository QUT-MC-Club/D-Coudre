package fr.catcore.deacoudre.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.gegy1000.plasmid.game.config.GameConfig;
import net.gegy1000.plasmid.game.config.GameMapConfig;
import net.gegy1000.plasmid.game.config.PlayerConfig;
import net.minecraft.block.BlockState;

import java.util.List;

public class DeACoudreConfig implements GameConfig {

    public static final Codec<DeACoudreConfig> CODEC = RecordCodecBuilder.create(instance -> {
        Codec<GameMapConfig<DeACoudreConfig>> mapCodec = GameMapConfig.codec();

        return instance.group(
                mapCodec.fieldOf("map").forGetter(DeACoudreConfig::getMapConfig),
                PlayerConfig.CODEC.fieldOf("players").forGetter(DeACoudreConfig::getPlayerConfig),
                BlockState.CODEC.listOf().fieldOf("blocks").forGetter(DeACoudreConfig::getPlayerBlocks)
        ).apply(instance, DeACoudreConfig::new);
    });

    private final GameMapConfig<DeACoudreConfig> mapConfig;
    private final PlayerConfig playerConfig;
    private final List<BlockState> playerBlocks;

    public DeACoudreConfig(
            GameMapConfig<DeACoudreConfig> mapConfig,
            PlayerConfig playerConfig,
            List<BlockState> playerBlocks
    ) {
        this.mapConfig = mapConfig;
        this.playerConfig = playerConfig;
        this.playerBlocks = playerBlocks;
    }

    public GameMapConfig<DeACoudreConfig> getMapConfig() {
        return mapConfig;
    }

    public List<BlockState> getPlayerBlocks() {
        return playerBlocks;
    }

    public PlayerConfig getPlayerConfig() {
        return playerConfig;
    }
}
