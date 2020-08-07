package fr.catcore.deacoudre;

import fr.catcore.deacoudre.game.DeACoudreConfig;
import fr.catcore.deacoudre.game.DeACoudreWaiting;
import fr.catcore.deacoudre.game.map.DeACoudreMapProvider;
import net.fabricmc.api.ModInitializer;
import net.gegy1000.plasmid.game.GameType;
import net.gegy1000.plasmid.game.config.GameMapConfig;
import net.gegy1000.plasmid.game.map.provider.MapProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeACoudre implements ModInitializer {

    public static final String ID = "deacoudre";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public static final GameType<DeACoudreConfig> TYPE = GameType.register(
            new Identifier(ID, "deacoudre"),
            (server, config) -> {
                GameMapConfig<DeACoudreConfig> mapConfig = config.getMapConfig();
                RegistryKey<World> dimension = mapConfig.getDimension();
                BlockPos origin = mapConfig.getOrigin();
                ServerWorld world = server.getWorld(dimension);

                return mapConfig.getProvider().createAt(world, origin, config).thenApply(map -> {
                    return DeACoudreWaiting.open(map, config);
                });
            },
            DeACoudreConfig.CODEC
    );

    @Override
    public void onInitialize() {
        MapProvider.REGISTRY.register(new Identifier(ID, "deacoudre"), DeACoudreMapProvider.CODEC);
    }
}
