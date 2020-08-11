package fr.catcore.deacoudre;

import fr.catcore.deacoudre.game.DeACoudreConfig;
import fr.catcore.deacoudre.game.DeACoudreWaiting;
import net.fabricmc.api.ModInitializer;
import xyz.nucleoid.plasmid.game.GameType;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeACoudre implements ModInitializer {

    public static final String ID = "deacoudre";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public static final GameType<DeACoudreConfig> TYPE = GameType.register(
            new Identifier(ID, "deacoudre"),
            DeACoudreWaiting::open,
            DeACoudreConfig.CODEC
    );

    @Override
    public void onInitialize() {
//        MapProvider.REGISTRY.register(new Identifier(ID, "deacoudre"), DeACoudreMapGenerator.CODEC);
    }
}
