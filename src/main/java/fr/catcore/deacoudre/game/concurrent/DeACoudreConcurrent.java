package fr.catcore.deacoudre.game.concurrent;

import com.google.common.collect.Sets;
import fr.catcore.deacoudre.game.DeACoudreConfig;
import fr.catcore.deacoudre.game.DeACoudrePool;
import fr.catcore.deacoudre.game.DeACoudreSpawnLogic;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;
import xyz.nucleoid.plasmid.widget.SidebarWidget;

import java.util.Comparator;
import java.util.Set;

public class DeACoudreConcurrent {
    private final DeACoudreConfig config;

    public final GameSpace gameSpace;
    private final DeACoudreMap gameMap;

    private final DeACoudrePool pool;

    private final Set<ServerPlayerEntity> jumpers;
    private final DeACoudreSpawnLogic spawnLogic;

    private final Object2IntOpenHashMap<ServerPlayerEntity> points = new Object2IntOpenHashMap<>();

    private final SidebarWidget sidebar;

    private long closeTime = -1;

    private DeACoudreConcurrent(GameSpace gameSpace, DeACoudreMap map, DeACoudreConfig config, Set<ServerPlayerEntity> jumpers, GlobalWidgets widgets) {
        this.gameSpace = gameSpace;
        this.config = config;
        this.gameMap = map;
        this.jumpers = jumpers;

        this.pool = new DeACoudrePool(gameSpace, map);

        this.spawnLogic = new DeACoudreSpawnLogic(gameSpace, map);

        this.sidebar = widgets.addSidebar(new LiteralText("Dé à Coudre").formatted(Formatting.BLUE, Formatting.BOLD));
    }

    public static void open(GameSpace gameSpace, DeACoudreMap map, DeACoudreConfig config) {
        gameSpace.openGame(game -> {
            GlobalWidgets widgets = new GlobalWidgets(game);

            Set<ServerPlayerEntity> jumpers = Sets.newHashSet(gameSpace.getPlayers());
            DeACoudreConcurrent active = new DeACoudreConcurrent(gameSpace, map, config, jumpers, widgets);

            game.setRule(GameRule.CRAFTING, RuleResult.DENY);
            game.setRule(GameRule.PORTALS, RuleResult.DENY);
            game.setRule(GameRule.PVP, RuleResult.DENY);
            game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            game.setRule(GameRule.FALL_DAMAGE, RuleResult.ALLOW);
            game.setRule(GameRule.HUNGER, RuleResult.DENY);

            game.on(GameOpenListener.EVENT, active::onOpen);

            game.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
            game.on(PlayerAddListener.EVENT, active::addPlayer);
            game.on(PlayerRemoveListener.EVENT, active::removePlayer);

            game.on(GameTickListener.EVENT, active::tick);

            game.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
            game.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        for (ServerPlayerEntity player : this.jumpers) {
            this.spawnJumper(player);
        }

        this.updateSidebar();
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.jumpers.contains(player)) {
            this.spawnSpectator(player);
        }
    }

    private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (player == null) return ActionResult.FAIL;

        if (source == DamageSource.OUT_OF_WORLD || source == DamageSource.FALL) {
            this.onPlayerFailJump(player);
            return ActionResult.FAIL;
        }

        return ActionResult.FAIL;
    }

    private void onPlayerFailJump(ServerPlayerEntity player) {
        this.spawnJumper(player);
    }

    private void onPlayerLandInWater(ServerPlayerEntity player) {
        PlayerSet players = this.gameSpace.getPlayers();
        BlockPos pos = player.getBlockPos();

        this.spawnJumper(player);

        if (this.pool.canFormCoudreAt(pos)) {
            this.pool.putCoudreAt(pos);

            this.points.addTo(player, 10);
            this.updateSidebar();

            players.sendSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST);
            players.sendSound(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE);
        } else {
            this.points.addTo(player, 1);
            this.updateSidebar();

            this.pool.putBlockAt(player, pos);
            players.sendSound(SoundEvents.AMBIENT_UNDERWATER_ENTER);
        }
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnJumper(player);
        return ActionResult.FAIL;
    }

    private void removePlayer(ServerPlayerEntity player) {
        this.jumpers.remove(player);
    }

    private void tick() {
        ServerWorld world = this.gameSpace.getWorld();
        long time = world.getTime();

        if (this.closeTime > 0) {
            this.tickClosing(this.gameSpace, time);
            return;
        }

        for (ServerPlayerEntity jumper : this.jumpers) {
            BlockPos pos = jumper.getBlockPos();
            if (this.pool.contains(pos) && this.pool.isFreeAt(pos)) {
                this.onPlayerLandInWater(jumper);
            }
        }

        ServerPlayerEntity winningPlayer = this.checkWinResult();
        if (winningPlayer != null) {
            this.broadcastWin(winningPlayer);
            this.closeTime = time + 20 * 5;
        }
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.SPECTATOR);
    }

    private void spawnJumper(ServerPlayerEntity jumper) {
        Vec3d platformSpawn = this.gameMap.getJumpingPlatform().getCenter();

        jumper.teleport(this.gameSpace.getWorld(), platformSpawn.x, platformSpawn.y, platformSpawn.z, 180F, 0F);
        jumper.fallDistance = 0.0F;
    }

    private void updateSidebar() {
        this.sidebar.set(content -> {
            this.jumpers.stream()
                    .sorted(Comparator.comparingInt(this.points::getInt).reversed())
                    .forEach(player -> {
                        int points = this.points.getInt(player);
                        content.writeLine(Formatting.AQUA + player.getDisplayName().getString() + ": " + Formatting.GOLD + points);
                    });
        });
    }

    @Nullable
    private ServerPlayerEntity checkWinResult() {
        if (!this.pool.isFull()) {
            return null;
        }

        ServerPlayerEntity winner = null;
        int winnerPoints = 0;

        for (ServerPlayerEntity jumper : this.jumpers) {
            int points = this.points.getInt(jumper);
            if (points > winnerPoints) {
                winnerPoints = points;
                winner = jumper;
            }
        }

        return winner;
    }

    private void broadcastWin(ServerPlayerEntity winningPlayer) {
        Text message = new TranslatableText("text.dac.game.won", winningPlayer.getDisplayName()).formatted(Formatting.GOLD);

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.sendSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private void tickClosing(GameSpace game, long time) {
        if (time >= this.closeTime) {
            game.close(GameCloseReason.FINISHED);
        }
    }
}
