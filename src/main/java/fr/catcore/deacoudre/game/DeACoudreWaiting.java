package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.map.DeACoudreMap;
import fr.catcore.deacoudre.game.map.DeACoudreMapGenerator;
import net.gegy1000.plasmid.game.Game;
import net.gegy1000.plasmid.game.GameWorld;
import net.gegy1000.plasmid.game.GameWorldState;
import net.gegy1000.plasmid.game.StartResult;
import net.gegy1000.plasmid.game.config.PlayerConfig;
import net.gegy1000.plasmid.game.event.OfferPlayerListener;
import net.gegy1000.plasmid.game.event.PlayerAddListener;
import net.gegy1000.plasmid.game.event.PlayerDeathListener;
import net.gegy1000.plasmid.game.event.RequestStartListener;
import net.gegy1000.plasmid.game.player.JoinResult;
import net.gegy1000.plasmid.game.rule.GameRule;
import net.gegy1000.plasmid.game.rule.RuleResult;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

import java.util.concurrent.CompletableFuture;

public class DeACoudreWaiting {
    private final GameWorld gameWorld;
    private final DeACoudreMap map;
    private final DeACoudreConfig config;
    private final DeACoudreSpawnLogic spawnLogic;

    private DeACoudreWaiting(GameWorld gameWorld, DeACoudreMap map, DeACoudreConfig config) {
        this.gameWorld = gameWorld;
        this.map = map;
        this.config = config;

        this.spawnLogic = new DeACoudreSpawnLogic(gameWorld, map);
    }

    public static CompletableFuture<Void> open(GameWorldState worldState, DeACoudreConfig config) {
        DeACoudreMapGenerator generator = new DeACoudreMapGenerator(config.mapConfig);

        return generator.create().thenAccept(map -> {
            GameWorld gameWorld = worldState.openWorld(map.asGenerator());

            DeACoudreWaiting waiting = new DeACoudreWaiting(gameWorld, map, config);

            gameWorld.newGame(builder -> {
                builder.setRule(GameRule.ALLOW_CRAFTING, RuleResult.DENY);
                builder.setRule(GameRule.ALLOW_PORTALS, RuleResult.DENY);
                builder.setRule(GameRule.ALLOW_PVP, RuleResult.DENY);
                builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
                builder.setRule(GameRule.ENABLE_HUNGER, RuleResult.DENY);
                builder.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);

                builder.on(RequestStartListener.EVENT, waiting::requestStart);
                builder.on(OfferPlayerListener.EVENT, waiting::offerPlayer);


                builder.on(PlayerAddListener.EVENT, waiting::addPlayer);
                builder.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
            });
        });
    }

    private JoinResult offerPlayer(ServerPlayerEntity player) {
        if (this.gameWorld.getPlayerCount() >= this.config.playerConfig.getMaxPlayers()) {
            return JoinResult.gameFull();
        }

        return JoinResult.ok();
    }

    private StartResult requestStart() {
        PlayerConfig playerConfig = this.config.playerConfig;
        if (this.gameWorld.getPlayerCount() < playerConfig.getMinPlayers()) {
            return StartResult.notEnoughPlayers();
        }

        DeACoudreActive.open(this.gameWorld, this.map, this.config);

        return StartResult.ok();
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnPlayer(player);
    }

    private boolean onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnPlayer(player);
        return true;
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }
}
