package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.DeACoudre;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameCloseListener;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.PlayerRemoveListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.BlockBounds;
import xyz.nucleoid.plasmid.util.PlayerRef;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class DeACoudreActive {

    private final DeACoudreConfig config;

    public final GameSpace gameSpace;
    private final DeACoudreMap gameMap;

    private final Set<PlayerRef> participants;

    private final Map<PlayerRef, BlockState> blockStateMap;

    private final Map<PlayerRef, Integer> lifeMap;

    private final DeACoudreSpawnLogic spawnLogic;

    public PlayerRef nextJumper;

    private boolean turnStarting = true;

    private final DeACoudreScoreboard scoreboard;

    private final boolean singleplayer;
    private long closeTime = -1;
    private long ticks;
    private long seconds;

    private DeACoudreActive(GameSpace gameSpace, DeACoudreMap map, DeACoudreConfig config, Set<PlayerRef> participants, GlobalWidgets widgets) {
        this.gameSpace = gameSpace;
        this.config = config;
        this.gameMap = map;
        this.participants = new HashSet<>(participants);
        this.spawnLogic = new DeACoudreSpawnLogic(gameSpace, map);
        this.nextJumper = (PlayerRef) this.participants.toArray()[0];
        this.blockStateMap = new HashMap<>();
        List<BlockState> blockList = Arrays.asList(this.config.getPlayerBlocks());
        Collections.shuffle(blockList);
        for (PlayerRef playerRef : this.participants) {
            this.blockStateMap.put(playerRef, blockList.get(new Random().nextInt(blockList.size())));
        }
        this.lifeMap = new HashMap<>();
        for (PlayerRef playerRef : this.participants) {
            this.lifeMap.put(playerRef, config.life);
        }
        this.scoreboard = DeACoudreScoreboard.create(this, widgets);
        this.singleplayer = this.participants.size() <= 1;
    }

    public Set<PlayerRef> participants() {
        return this.participants;
    }

    public Map<PlayerRef, Integer> lifes() {
        return this.lifeMap;
    }

    public static void open(GameSpace gameSpace, DeACoudreMap map, DeACoudreConfig config) {
        gameSpace.openGame(game -> {
            GlobalWidgets widgets = new GlobalWidgets(game);

            Set<PlayerRef> participants = gameSpace.getPlayers().stream()
                    .map(PlayerRef::of)
                    .collect(Collectors.toSet());
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
            game.on(PlayerRemoveListener.EVENT, active::removePlayer);

            game.on(GameTickListener.EVENT, active::tick);

            game.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
            game.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        ServerWorld world = this.gameSpace.getWorld();
        for (PlayerRef ref : this.participants) {
            ref.ifOnline(world, this::spawnParticipant);
        }
        MutableText text;
        if (this.config.life > 1) {
            text = new TranslatableText("text.dac.game.start_plural", this.config.life);
        } else {
            text = new TranslatableText("text.dac.game.start_singular");
        }
        this.broadcastMessage(text.formatted(Formatting.GREEN));
    }

    private void onClose() {
        this.scoreboard.close();
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.contains(PlayerRef.of(player))) {
            this.spawnSpectator(player);
        }
    }

    private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (player == null) return ActionResult.FAIL;
        if (!this.singleplayer && !PlayerRef.of(player).equals(this.nextJumper)) return ActionResult.FAIL;

        Vec3d playerPos = player.getPos();
        BlockBounds poolBounds = this.gameMap.getTemplate().getMetadata().getFirstRegionBounds("pool");
        boolean isInPool = poolBounds.contains((int)playerPos.x, (int)playerPos.y, (int)playerPos.z);
        if (source == DamageSource.FALL && ((playerPos.y < 10 && playerPos.y > 6) || isInPool)) {

            if (this.lifeMap.get(PlayerRef.of(player)) < 2) this.eliminatePlayer(player);
            else {
                this.spawnParticipant(player);
                this.lifeMap.replace(PlayerRef.of(player), this.lifeMap.get(PlayerRef.of(player)) - 1);
                this.nextJumper = this.nextPlayer(true);
                MutableText message = new TranslatableText("text.dac.game.lose_life", player.getDisplayName());
                if (this.lifeMap.get(PlayerRef.of(player)) > 1) {
                    message = message.append(new TranslatableText("text.dac.game.lives_left", this.lifeMap.get(PlayerRef.of(player))));
                } else {
                    message = message.append(new TranslatableText("text.dac.game.life_left"));
                }

                this.broadcastMessage(message.formatted(Formatting.YELLOW));
            }
        } else if (source == DamageSource.OUT_OF_WORLD) {
            BlockPos blockPos = this.gameMap.getSpawn();
            player.teleport(this.gameSpace.getWorld(), blockPos.getX(), 10, blockPos.getZ(), 180F, 0F);
        }
        return ActionResult.FAIL;
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.eliminatePlayer(player);
        return ActionResult.FAIL;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
    }

    private void removePlayer(ServerPlayerEntity player) {
        PlayerRef leaving = PlayerRef.of(player);
        boolean contains = false;
        for (PlayerRef playerRef : this.participants) {
            if (playerRef.equals(leaving)) {
                contains = true;
                leaving = playerRef;
                break;
            }
        }
        if (leaving == this.nextJumper) this.nextPlayer(true);
        if (contains) this.eliminatePlayer(player);

        Text message = new TranslatableText("text.dac.game.left", player.getDisplayName());
        this.broadcastMessage(message);
    }

    private void eliminatePlayer(ServerPlayerEntity player) {

        Text message = new TranslatableText("text.dac.game.eliminated", player.getDisplayName())
                .formatted(Formatting.RED);

        this.broadcastMessage(message);
        this.broadcastSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);

        this.spawnSpectator(player);
        PlayerRef eliminated = PlayerRef.of(player);
        for (PlayerRef playerRef : this.participants) {
            if (playerRef.equals(eliminated)) {
                eliminated = playerRef;
                break;
            }
        }
        this.participants.remove(eliminated);
        this.lifeMap.remove(eliminated);
        this.blockStateMap.remove(eliminated);

        if (this.singleplayer || PlayerRef.of(player) == this.nextJumper) {
           this.nextJumper = nextPlayer(true);
        }
    }

    private void broadcastMessage(Text message) {
        this.gameSpace.getPlayers().sendMessage(message);
    }

    private void broadcastSound(SoundEvent sound) {
        this.gameSpace.getPlayers().sendSound(sound);
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.SPECTATOR);
    }

    private void tick() {
        if (this.nextJumper == null) {
            this.nextJumper = nextPlayer(true);
            return;
        }
        this.ticks++;
        this.updateExperienceBar();
        this.scoreboard.tick();
        ServerPlayerEntity playerEntity = this.nextJumper.getEntity(this.gameSpace.getWorld());
        long time = this.gameSpace.getWorld().getTime();
        if (this.closeTime > 0) {
            this.tickClosing(this.gameSpace, time);
            return;
        }
        if (this.turnStarting && this.participants.contains(this.nextJumper)) {
            this.ticks = 0;
            this.seconds = 0;
            BlockBounds jumpBoundaries = this.gameMap.getTemplate().getMetadata().getFirstRegionBounds("jumpingPlatform");
            Vec3d vec3d = jumpBoundaries.getCenter().add(0, 2, 0);
            if (playerEntity == null || this.nextJumper == null) {
                this.broadcastMessage(new TranslatableText("text.dac.error.next_jumper.null").formatted(Formatting.RED));
                this.nextJumper = nextPlayer(false);
                playerEntity = this.nextJumper.getEntity(this.gameSpace.getWorld());
                this.broadcastMessage(new TranslatableText("text.dac.error.next_jumper.try", playerEntity.getName()));
                this.scoreboard.tick();
            }
            playerEntity.teleport(this.gameSpace.getWorld(), vec3d.x, vec3d.y, vec3d.z, 180F, 0F);
            playerEntity.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 1.0F, 1.0F);
            Text message = playerEntity.getDisplayName().shallowCopy();

            this.broadcastMessage(new TranslatableText("text.dac.game.turn", message.getString()).formatted(Formatting.BLUE));
            this.turnStarting = false;
        }
        if (playerEntity == null || this.nextJumper == null) {
            if (playerEntity == null) {
                DeACoudre.LOGGER.warn("playerEntity is null!");
            }
            if (this.nextJumper == null) {
                DeACoudre.LOGGER.warn(new TranslatableText("text.dac.error.next_jumper.null"));
                this.nextJumper = nextPlayer(true);
            }
        } else if (this.gameSpace.getWorld().getBlockState(playerEntity.getBlockPos()).equals(Blocks.WATER.getDefaultState()) && this.participants.contains(this.nextJumper)) {
            BlockPos pos = playerEntity.getBlockPos();
            boolean coudre = false;
            if (this.gameSpace.getWorld().getBlockState(pos.west()) != Blocks.WATER.getDefaultState()
                && this.gameSpace.getWorld().getBlockState(pos.east()) != Blocks.WATER.getDefaultState()
                && this.gameSpace.getWorld().getBlockState(pos.north()) != Blocks.WATER.getDefaultState()
                && this.gameSpace.getWorld().getBlockState(pos.south()) != Blocks.WATER.getDefaultState()
            ) {
                this.gameSpace.getWorld().setBlockState(pos, Blocks.EMERALD_BLOCK.getDefaultState());
                this.lifeMap.replace(this.nextJumper, this.lifeMap.get(this.nextJumper) + 1);
                Text message = playerEntity.getDisplayName().shallowCopy();

                this.broadcastMessage(new TranslatableText("text.dac.game.dac", message.getString(), this.lifeMap.get(this.nextJumper)).formatted(Formatting.AQUA));
                coudre = true;
            } else {
                this.gameSpace.getWorld().setBlockState(pos, this.blockStateMap.get(this.nextJumper));
            }
            this.nextJumper = nextPlayer(true);
            spawnParticipant(playerEntity);
            if (coudre) {
                this.broadcastSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST);
                this.broadcastSound(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE);
            } else {
                this.broadcastSound(SoundEvents.AMBIENT_UNDERWATER_ENTER);
            }
        }
        if (this.ticks % 20 == 0 && !this.turnStarting) {
            this.seconds++;
        }
        BlockBounds jumpingArea = this.gameMap.getTemplate().getMetadata().getFirstRegionBounds("jumpingArea");
        if (this.seconds % 20 == 0 && !this.turnStarting && this.nextJumper != null && playerEntity != null && jumpingArea.contains(playerEntity.getBlockPos())) {
            this.broadcastMessage(new TranslatableText("text.dac.game.slow", playerEntity.getName().getString(), this.lifeMap.get(this.nextJumper) - 1).formatted(Formatting.YELLOW));
            this.lifeMap.replace(this.nextJumper, this.lifeMap.get(this.nextJumper) - 1);
            this.nextJumper = nextPlayer(true);
            spawnParticipant(playerEntity);
            if (this.lifeMap.get(PlayerRef.of(playerEntity)) < 1) {
                eliminatePlayer(playerEntity);
            }
        }
        WinResult result = this.checkWinResult();
        if (result.isWin()) {
            this.broadcastWin(result);
            this.closeTime = time + 20 * 5;
        }
    }

    private void updateExperienceBar() {
        for (PlayerRef playerRef : this.participants) {
            if (playerRef == null) continue;
            ServerPlayerEntity playerEntity = playerRef.getEntity(this.gameSpace.getWorld());
            if (playerEntity == null) continue;
            if (this.lifeMap.containsKey(playerRef)) playerEntity.setExperienceLevel(this.lifeMap.get(playerRef));
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

        this.broadcastMessage(message);
        this.broadcastSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private void tickClosing(GameSpace game, long time) {
        if (time >= this.closeTime) {
            game.close();
        }
    }

    public PlayerRef nextPlayer(boolean newTurn) {
        boolean bool = false;
        boolean bool2 = true;
        PlayerRef next = this.nextJumper;
        for (PlayerRef playerRef : participants) {
            if (playerRef == next) {
                bool = true;
                bool2 = false;
                continue;
            }
            if (bool) {
                bool = false;
                next = playerRef;
                if (newTurn) {
                    this.turnStarting = true;
                }
                break;
            }
        }
        if ((bool || bool2) && next == this.nextJumper) {
            for (PlayerRef playerRef : participants) {
                next = playerRef;
                if (newTurn) {
                    this.turnStarting = true;
                }
                break;
            }
        }
        if (next == this.nextJumper && !this.singleplayer) {
            DeACoudre.LOGGER.warn("next is equals to nextJumper, something might be wrong!");
            return null;
        }
        return next;
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.singleplayer) {
            return WinResult.no();
        }

        ServerWorld world = this.gameSpace.getWorld();

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

        BlockBounds pool = this.gameMap.getTemplate().getMetadata().getFirstRegionBounds("pool");
        boolean isFull = true;
        for (BlockPos pos : pool) {
            if (this.gameSpace.getWorld().getBlockState(pos) == Blocks.WATER.getDefaultState()) isFull = false;
        }

        if (isFull) {
            return WinResult.win(null);
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
