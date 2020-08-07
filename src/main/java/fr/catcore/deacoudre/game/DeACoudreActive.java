package fr.catcore.deacoudre.game;

import net.gegy1000.plasmid.game.Game;
import net.gegy1000.plasmid.game.JoinResult;
import net.gegy1000.plasmid.game.event.*;
import net.gegy1000.plasmid.game.map.GameMap;
import net.gegy1000.plasmid.game.rule.GameRule;
import net.gegy1000.plasmid.game.rule.RuleResult;
import net.gegy1000.plasmid.util.PlayerRef;
import net.minecraft.block.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DeACoudreActive {

    private final DeACoudreConfig config;

    private final Set<PlayerRef> participants;

    private final Map<PlayerRef, BlockState> blockStateMap;

    private final DeACoudreSpawnLogic spawnLogic;

    private PlayerRef nextJumper;

    private DeACoudreActive(GameMap map, DeACoudreConfig config, Set<PlayerRef> participants) {
        this.config = config;
        this.participants = new HashSet<>(participants);
        this.spawnLogic = new DeACoudreSpawnLogic(map);
        this.nextJumper = (PlayerRef) this.participants.toArray()[0];
        this.blockStateMap = new HashMap<>();
        int a = 0;
        for (PlayerRef playerRef : this.participants) {
            this.blockStateMap.putIfAbsent(playerRef, this.config.getPlayerBlocks().get(a));
            a++;
        }
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
        if (source == DamageSource.FALL) {
            this.eliminatePlayer(game, player);
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
        if (playerEntity.isTouchingWater() && this.participants.contains(this.nextJumper)) {
            BlockPos pos = playerEntity.getBlockPos();
            game.getWorld().setBlockState(pos, this.blockStateMap.get(this.nextJumper));
            nextPlayer();
            spawnParticipant(playerEntity);
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
                this.nextJumper = playerRef;
                break;
            }
        }
        if (bool) {
            for (PlayerRef playerRef : participants) {
                this.nextJumper = playerRef;
                break;
            }
        }
    }
}
