package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.concurrent.DeACoudreConcurrent;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import fr.catcore.deacoudre.game.map.DeACoudreMapGenerator;
import fr.catcore.deacoudre.game.sequential.DeACoudreSequential;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.BubbleWorldConfig;
import xyz.nucleoid.plasmid.game.*;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;

public class DeACoudreWaiting {
    private final GameSpace gameSpace;
    private final DeACoudreMap map;
    private final DeACoudreConfig config;
    private final DeACoudreSpawnLogic spawnLogic;

    private DeACoudreWaiting(GameSpace gameSpace, DeACoudreMap map, DeACoudreConfig config) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;

        this.spawnLogic = new DeACoudreSpawnLogic(gameSpace, map);
    }

    public static GameOpenProcedure open(GameOpenContext<DeACoudreConfig> context) {
        DeACoudreMapGenerator generator = new DeACoudreMapGenerator(context.getConfig().mapConfig);
        DeACoudreMap map = generator.build();

        BubbleWorldConfig worldConfig = new BubbleWorldConfig()
                .setGenerator(map.asGenerator(context.getServer()))
                .setDefaultGameMode(GameMode.SPECTATOR)
                .setSpawnAt(new Vec3d(0,3,0));

        return context.createOpenProcedure(worldConfig, game -> {
            DeACoudreWaiting waiting = new DeACoudreWaiting(game.getSpace(), map, context.getConfig());

            GameWaitingLobby.applyTo(game, context.getConfig().playerConfig);

            game.setRule(GameRule.CRAFTING, RuleResult.DENY);
            game.setRule(GameRule.PORTALS, RuleResult.DENY);
            game.setRule(GameRule.PVP, RuleResult.DENY);
            game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            game.setRule(GameRule.HUNGER, RuleResult.DENY);
            game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);

            game.on(RequestStartListener.EVENT, waiting::requestStart);

            game.on(PlayerAddListener.EVENT, waiting::addPlayer);
            game.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
        });
    }

    private StartResult requestStart() {
        if (this.config.concurrent) {
            DeACoudreConcurrent.open(this.gameSpace, this.map, this.config);
        } else {
            DeACoudreSequential.open(this.gameSpace, this.map, this.config);
        }
        return StartResult.OK;
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
        return ActionResult.FAIL;
    }
}
