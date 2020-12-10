package fr.catcore.deacoudre.game;

import com.google.common.collect.Sets;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.BlockBounds;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;

import java.util.*;

public class DeACoudreActive {

    private final DeACoudreConfig config;

    public final GameSpace gameSpace;
    private final DeACoudreMap gameMap;

    private final Set<ServerPlayerEntity> participants;
    private final List<ServerPlayerEntity> jumpOrder;

    private final Map<ServerPlayerEntity, BlockState> blockStateMap;

    private final DeACoudrePlayerLives lives;

    private final DeACoudreSpawnLogic spawnLogic;

    public ServerPlayerEntity currentJumper;
    private int jumperIndex;

    private boolean turnStarting = true;

    private final DeACoudreScoreboard scoreboard;

    private final boolean singleplayer;
    private long closeTime = -1;
    private long ticks;
    private long jumpingSeconds;

    private DeACoudreActive(GameSpace gameSpace, DeACoudreMap map, DeACoudreConfig config, Set<ServerPlayerEntity> participants, GlobalWidgets widgets) {
        this.gameSpace = gameSpace;
        this.config = config;
        this.gameMap = map;
        this.participants = participants;

        this.jumpOrder = new ArrayList<>(participants);
        Collections.shuffle(this.jumpOrder);

        this.spawnLogic = new DeACoudreSpawnLogic(gameSpace, map);
        this.currentJumper = this.jumpOrder.get(0);
        this.blockStateMap = new Object2ObjectOpenHashMap<>();

        List<BlockState> blockList = Arrays.asList(this.config.getPlayerBlocks());
        Collections.shuffle(blockList);
        int blockIndex = 0;
        for (ServerPlayerEntity player : this.participants) {
            this.blockStateMap.put(player, blockList.get(blockIndex++ % blockList.size()));
        }

        this.lives = new DeACoudrePlayerLives();
        this.lives.addPlayers(this.participants, config.life);

        this.scoreboard = DeACoudreScoreboard.create(this, widgets);
        this.singleplayer = this.participants.size() <= 1;
    }

    public Set<ServerPlayerEntity> participants() {
        return this.participants;
    }

    public DeACoudrePlayerLives lives() {
        return this.lives;
    }

    public static void open(GameSpace gameSpace, DeACoudreMap map, DeACoudreConfig config) {
        gameSpace.openGame(game -> {
            GlobalWidgets widgets = new GlobalWidgets(game);

            Set<ServerPlayerEntity> participants = Sets.newHashSet(gameSpace.getPlayers());
            DeACoudreActive active = new DeACoudreActive(gameSpace, map, config, participants, widgets);

            game.setRule(GameRule.CRAFTING, RuleResult.DENY);
            game.setRule(GameRule.PORTALS, RuleResult.DENY);
            game.setRule(GameRule.PVP, RuleResult.DENY);
            game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            game.setRule(GameRule.FALL_DAMAGE, RuleResult.ALLOW);
            game.setRule(GameRule.HUNGER, RuleResult.DENY);

            game.on(GameOpenListener.EVENT, active::onOpen);
            game.on(GameCloseListener.EVENT, active::onClose);

            game.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
            game.on(PlayerAddListener.EVENT, active::addPlayer);
            game.on(PlayerRemoveListener.EVENT, active::eliminatePlayer);

            game.on(GameTickListener.EVENT, active::tick);

            game.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
            game.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        for (ServerPlayerEntity player : this.participants) {
            this.spawnParticipant(player);
        }

        MutableText text;
        if (this.config.life > 1) {
            text = new TranslatableText("text.dac.game.start_plural", this.config.life);
        } else {
            text = new TranslatableText("text.dac.game.start_singular");
        }

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(text.formatted(Formatting.GREEN));
    }

    private void onClose() {
        this.scoreboard.close();
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.contains(player)) {
            this.spawnSpectator(player);
        }
    }

    private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (player == null) return ActionResult.FAIL;

        if (source == DamageSource.OUT_OF_WORLD) {
            this.onPlayerFailJump(player);
            return ActionResult.FAIL;
        }

        if (player == this.currentJumper && source == DamageSource.FALL) {
            BlockBounds pool = this.gameMap.getPool();
            BlockPos groundPos = player.getBlockPos().down();
            if (pool.contains(groundPos)) {
                this.onPlayerFailJump(player);
            }
        }

        return ActionResult.FAIL;
    }

    private void onPlayerFailJump(ServerPlayerEntity player) {
        int livesRemaining = this.lives.takeLife(player);
        if (livesRemaining == 0) {
            this.eliminatePlayer(player);
            return;
        }

        MutableText message = new TranslatableText("text.dac.game.lose_life", player.getDisplayName());
        if (livesRemaining > 1) {
            message = message.append(new TranslatableText("text.dac.game.lives_left", livesRemaining));
        } else {
            message = message.append(new TranslatableText("text.dac.game.life_left"));
        }

        this.gameSpace.getPlayers().sendMessage(message.formatted(Formatting.YELLOW));

        this.spawnParticipant(player);
        this.currentJumper = this.nextJumper(true);
    }

    private void onPlayerLandInWater(ServerPlayerEntity player) {
        ServerWorld world = this.gameSpace.getWorld();
        PlayerSet players = this.gameSpace.getPlayers();

        BlockPos pos = player.getBlockPos();

        this.currentJumper = this.nextJumper(true);
        this.spawnParticipant(player);

        if (this.canFormCoudreAt(world, pos)) {
            world.setBlockState(pos, Blocks.EMERALD_BLOCK.getDefaultState());

            int remainingLife = this.lives.grantLife(this.currentJumper);
            players.sendMessage(new TranslatableText("text.dac.game.dac", player.getDisplayName(), remainingLife).formatted(Formatting.AQUA));
            players.sendSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST);
            players.sendSound(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE);
        } else {
            world.setBlockState(pos, this.blockStateMap.get(this.currentJumper));

            players.sendSound(SoundEvents.AMBIENT_UNDERWATER_ENTER);
        }
    }

    private boolean canFormCoudreAt(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos.west()) != Blocks.WATER.getDefaultState()
                && world.getBlockState(pos.east()) != Blocks.WATER.getDefaultState()
                && world.getBlockState(pos.north()) != Blocks.WATER.getDefaultState()
                && world.getBlockState(pos.south()) != Blocks.WATER.getDefaultState();
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.eliminatePlayer(player);
        return ActionResult.FAIL;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
    }

    private void eliminatePlayer(ServerPlayerEntity player) {
        if (this.participants.remove(player)) {
            this.jumpOrder.remove(player);
            this.blockStateMap.remove(player);
            this.lives.removePlayer(player);

            Text message = new TranslatableText("text.dac.game.eliminated", player.getDisplayName())
                    .formatted(Formatting.RED);

            PlayerSet players = this.gameSpace.getPlayers();
            players.sendMessage(message);
            players.sendSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);

            this.spawnSpectator(player);

            if (this.singleplayer || player == this.currentJumper) {
                this.currentJumper = this.nextJumper(true);
            }
        }
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.SPECTATOR);
    }

    private void tick() {
        ServerPlayerEntity jumper = this.currentJumper;
        PlayerSet players = this.gameSpace.getPlayers();
        ServerWorld world = this.gameSpace.getWorld();
        long time = world.getTime();

        // check for invalid jumper
        if (jumper == null || !players.contains(jumper) || !this.participants.contains(jumper)) {
            this.currentJumper = this.nextJumper(true);
            if (jumper != null) {
                this.eliminatePlayer(jumper);
            }
            return;
        }

        this.ticks++;
        this.scoreboard.tick();

        if (this.closeTime > 0) {
            this.tickClosing(this.gameSpace, time);
            return;
        }

        if (this.jumpingSeconds == 19) jumper.sendMessage(new TranslatableText("text.dac.time.1"), true);
        else jumper.sendMessage(new TranslatableText("text.dac.time.+", 20 - this.jumpingSeconds), true);

        if (this.turnStarting) {
            this.ticks = 0;
            this.jumpingSeconds = 0;
            BlockBounds platform = this.gameMap.getJumpingPlatform();
            Vec3d platformSpawn = platform.getCenter().add(0, 2, 0);
            jumper.teleport(world, platformSpawn.x, platformSpawn.y, platformSpawn.z, 180F, 0F);
            jumper.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 1.0F, 1.0F);
            players.sendMessage(new TranslatableText("text.dac.game.turn", jumper.getDisplayName()).formatted(Formatting.BLUE));
            this.turnStarting = false;
        }

        if (world.getBlockState(jumper.getBlockPos()).equals(Blocks.WATER.getDefaultState())) {
            this.onPlayerLandInWater(jumper);
        }

        if (!this.turnStarting && this.ticks % 20 == 0) {
            this.jumpingSeconds++;

            if (this.jumpingSeconds >= 20) {
                int remainingLife = this.lives.takeLife(jumper);

                players.sendMessage(new TranslatableText("text.dac.game.slow", jumper.getName().getString(), remainingLife).formatted(Formatting.YELLOW));
                this.currentJumper = this.nextJumper(true);
                this.spawnParticipant(jumper);

                if (remainingLife == 0) {
                    this.eliminatePlayer(jumper);
                }
            }
        }

        WinResult result = this.checkWinResult();
        if (result.isWin()) {
            this.broadcastWin(result);
            this.closeTime = time + 20 * 5;
        }
    }

    private void broadcastWin(WinResult result) {
        ServerPlayerEntity winningPlayer = result.getWinningPlayer();

        Text message;
        if (winningPlayer != null) {
            message = new TranslatableText("text.dac.game.won", winningPlayer.getDisplayName()).formatted(Formatting.GOLD);
        } else {
            message = new TranslatableText("text.dac.game.won.nobody").formatted(Formatting.GOLD);
        }

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.sendSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private void tickClosing(GameSpace game, long time) {
        if (time >= this.closeTime) {
            game.close(GameCloseReason.FINISHED);
        }
    }

    public ServerPlayerEntity nextJumper(boolean newTurn) {
        int jumperIndex = (this.jumperIndex + 1) % this.jumpOrder.size();
        if (newTurn) {
            this.jumperIndex = jumperIndex;
            this.turnStarting = true;
        }
        return this.jumpOrder.get(jumperIndex);
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.singleplayer) {
            return WinResult.no();
        }

        if (this.isPoolFull()) {
            return WinResult.win(null);
        }

        ServerPlayerEntity winningPlayer = null;

        for (ServerPlayerEntity player : this.participants) {
            if (player != null) {
                // we still have more than one player remaining
                if (winningPlayer != null) {
                    return WinResult.no();
                }

                winningPlayer = player;
            }
        }

        return WinResult.win(winningPlayer);
    }

    private boolean isPoolFull() {
        BlockBounds pool = this.gameMap.getPool();
        ServerWorld world = this.gameSpace.getWorld();
        for (BlockPos pos : pool) {
            if (world.getBlockState(pos) == Blocks.WATER.getDefaultState()) {
                return false;
            }
        }
        return true;
    }

    static class WinResult {
        final ServerPlayerEntity winningPlayer;
        final boolean win;

        private WinResult(ServerPlayerEntity winningPlayer, boolean win) {
            this.winningPlayer = winningPlayer;
            this.win = win;
        }

        static WinResult no() {
            return new WinResult(null, false);
        }

        static WinResult win(ServerPlayerEntity player) {
            return new WinResult(player, true);
        }

        public boolean isWin() {
            return this.win;
        }

        public ServerPlayerEntity getWinningPlayer() {
            return this.winningPlayer;
        }
    }
}
