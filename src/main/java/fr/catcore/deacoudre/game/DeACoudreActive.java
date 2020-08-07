package fr.catcore.deacoudre.game;

import net.gegy1000.plasmid.game.Game;
import net.gegy1000.plasmid.game.JoinResult;
import net.gegy1000.plasmid.game.event.*;
import net.gegy1000.plasmid.game.map.GameMap;
import net.gegy1000.plasmid.game.rule.GameRule;
import net.gegy1000.plasmid.game.rule.RuleResult;
import net.gegy1000.plasmid.util.PlayerRef;
import net.gegy1000.plasmid.world.BlockBounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.*;

public class DeACoudreActive {

    private final DeACoudreConfig config;

    private final GameMap gameMap;

    private final Set<PlayerRef> participants;

    private final Map<PlayerRef, BlockState> blockStateMap;

    private final Map<PlayerRef, Integer> lifeMap;

    private final DeACoudreSpawnLogic spawnLogic;

    private PlayerRef nextJumper;

    private boolean turnStarting = true;

    private final DeACoudreScoreboard scoreboard;

    private final boolean ignoreWinState;
    private long closeTime = -1;

    private DeACoudreActive(GameMap map, DeACoudreConfig config, Set<PlayerRef> participants) {
        this.config = config;
        this.gameMap = map;
        this.participants = new HashSet<>(participants);
        this.spawnLogic = new DeACoudreSpawnLogic(map);
        this.nextJumper = (PlayerRef) this.participants.toArray()[0];
        this.blockStateMap = new HashMap<>();
        List<BlockState> blockList = Arrays.asList(this.config.getPlayerBlocks());
        Collections.shuffle(blockList);
        for (PlayerRef playerRef : this.participants) {
            this.blockStateMap.put(playerRef, blockList.get(new Random().nextInt(blockList.size())));
        }
        this.lifeMap = new HashMap<>();
        for (PlayerRef playerRef : this.participants) {
            this.lifeMap.put(playerRef, config.getLife());
        }
        this.scoreboard = new DeACoudreScoreboard();
        this.ignoreWinState = this.participants.size() <= 1;
    }

    public static Game open(GameMap map, DeACoudreConfig config, Set<PlayerRef> participants) {
        DeACoudreActive active = new DeACoudreActive(map, config, participants);

        Game.Builder builder = Game.builder();
        builder.setMap(map);

        builder.setRule(GameRule.ALLOW_CRAFTING, RuleResult.DENY);
        builder.setRule(GameRule.ALLOW_PORTALS, RuleResult.DENY);
        builder.setRule(GameRule.ALLOW_PVP, RuleResult.DENY);
        builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
        builder.setRule(GameRule.FALL_DAMAGE, RuleResult.ALLOW);
        builder.setRule(GameRule.ENABLE_HUNGER, RuleResult.DENY);

        builder.on(GameOpenListener.EVENT, active::open);
        builder.on(GameCloseListener.EVENT, active::close);

        builder.on(OfferPlayerListener.EVENT, (game, player) -> JoinResult.ok());
        builder.on(PlayerAddListener.EVENT, active::addPlayer);

        builder.on(GameTickListener.EVENT, active::tick);

        builder.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
        builder.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
        builder.on(PlayerRejoinListener.EVENT, active::rejoinPlayer);

        return builder.build();
    }

    private void open(Game game) {
        ServerWorld world = game.getWorld();
        for (PlayerRef ref : this.participants) {
            ref.ifOnline(world, this::spawnParticipant);
        }
        this.scoreboard.create(game);
        this.broadcastMessage(game, new LiteralText(String.format("All player start with %s life(s).", this.config.getLife())));
    }

    private void close(Game game) {

    }

    private void addPlayer(Game game, ServerPlayerEntity player) {
        if (!this.participants.contains(PlayerRef.of(player))) {
            this.spawnSpectator(player);
        }
    }

    private void rejoinPlayer(Game game, ServerPlayerEntity player) {
        this.spawnSpectator(player);
    }

    private boolean onPlayerDamage(Game game, ServerPlayerEntity player, DamageSource source, float amount) {
        if (source == DamageSource.FALL || source == DamageSource.OUT_OF_WORLD) {

            if (this.lifeMap.get(PlayerRef.of(player)) < 2) this.eliminatePlayer(game, player);
            else {
                this.lifeMap.replace(PlayerRef.of(player), this.lifeMap.get(PlayerRef.of(player)) - 1);
                this.nextPlayer();
                this.spawnParticipant(player);
                Text message = player.getDisplayName().shallowCopy();

                this.broadcastMessage(game, new LiteralText(String.format("%s lost a life! %s life/lives left!", message.getString(), this.lifeMap.get(PlayerRef.of(player)))).formatted(Formatting.YELLOW));
            }
        }
        return true;
    }

    private boolean onPlayerDeath(Game game, ServerPlayerEntity player, DamageSource source) {
        this.eliminatePlayer(game, player);
        return true;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }

    private void eliminatePlayer(Game game, ServerPlayerEntity player) {
        Text message = player.getDisplayName().shallowCopy().append(" has been eliminated!")
                .formatted(Formatting.RED);

        this.broadcastMessage(game, message);
        this.broadcastSound(game, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);

        this.spawnSpectator(player);
        PlayerRef eliminated = PlayerRef.of(player);
        nextPlayer();
        this.participants.remove(eliminated);
        this.lifeMap.remove(eliminated);
        this.blockStateMap.remove(eliminated);
    }

    private void broadcastMessage(Game game, Text message) {
        game.onlinePlayers().forEach(player -> {
            player.sendMessage(message, false);
        });
    }

    private void broadcastSound(Game game, SoundEvent sound) {
        game.onlinePlayers().forEach(player -> {
            player.playSound(sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
        });
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
    }

    private void tick(Game game) {
        ServerPlayerEntity playerEntity = this.nextJumper.getEntity(game.getWorld());
        long time = game.getWorld().getTime();
        if (this.closeTime > 0) {
            this.tickClosing(game, time);
            return;
        }
        if (this.turnStarting && this.participants.contains(this.nextJumper)) {
            BlockBounds jumpBoundaries = this.gameMap.getFirstRegion("jumpingArea");
            Vec3d vec3d = jumpBoundaries.getCenter().add(0, 1, 0);
            playerEntity.teleport(game.getWorld(), vec3d.x, vec3d.y, vec3d.z, 180F, 0F);
            Text message = playerEntity.getDisplayName().shallowCopy();

            this.broadcastMessage(game, new LiteralText(String.format("It's %s turn!", message.getString())));
            this.turnStarting = false;
        }
        if (playerEntity.isTouchingWater() && this.participants.contains(this.nextJumper)) {
            BlockPos pos = playerEntity.getBlockPos();
            if (game.getWorld().getBlockState(pos.west()) != Blocks.WATER.getDefaultState()
                && game.getWorld().getBlockState(pos.east()) != Blocks.WATER.getDefaultState()
                && game.getWorld().getBlockState(pos.north()) != Blocks.WATER.getDefaultState()
                && game.getWorld().getBlockState(pos.south()) != Blocks.WATER.getDefaultState()
            ) {
                game.getWorld().setBlockState(pos, Blocks.EMERALD_BLOCK.getDefaultState());
                this.lifeMap.replace(this.nextJumper, this.lifeMap.get(this.nextJumper) + 1);
                Text message = playerEntity.getDisplayName().shallowCopy();

                this.broadcastMessage(game, new LiteralText(String.format("%s made a dé a coudre! They are winning an additionnal life! %s lives left!", message.getString(), this.lifeMap.get(this.nextJumper))).formatted(Formatting.GREEN));
            } else {
                game.getWorld().setBlockState(pos, this.blockStateMap.get(this.nextJumper));
            }
            nextPlayer();
            spawnParticipant(playerEntity);
            WinResult result = this.checkWinResult(game);
            if (result.isWin()) {
                this.broadcastWin(game, result);
                this.closeTime = time + 20 * 5;
            }
        }
    }

    private void broadcastWin(Game game, WinResult result) {
        ServerPlayerEntity winningPlayer = result.getWinningPlayer();

        Text message;
        if (winningPlayer != null) {
            message = winningPlayer.getDisplayName().shallowCopy().append(" has won the game!").formatted(Formatting.GOLD);
        } else {
            message = new LiteralText("The game ended, but nobody won!").formatted(Formatting.GOLD);
        }

        this.broadcastMessage(game, message);
        this.broadcastSound(game, SoundEvents.ENTITY_VILLAGER_YES);
    }

    private void tickClosing(Game game, long time) {
        if (time >= this.closeTime) {
            game.close();
        }
    }

    private void nextPlayer() {
        boolean bool = false;
        for (PlayerRef playerRef : participants) {
            if (playerRef == this.nextJumper) {
                bool = true;
                continue;
            }
            if (bool) {
                bool = false;
                this.nextJumper = playerRef;
                this.turnStarting = true;
                break;
            }
        }
        if (bool) {
            for (PlayerRef playerRef : participants) {
                this.nextJumper = playerRef;
                this.turnStarting = true;
                break;
            }
        }
    }

    private WinResult checkWinResult(Game game) {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.ignoreWinState) {
            return WinResult.no();
        }

        ServerWorld world = game.getWorld();

        ServerPlayerEntity winningPlayer = null;

        for (PlayerRef ref : this.participants) {
            ServerPlayerEntity player = ref.getEntity(world);
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
