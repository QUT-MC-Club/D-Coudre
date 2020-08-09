package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.DeACoudre;
import net.gegy1000.plasmid.util.PlayerRef;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DeACoudreScoreboard implements AutoCloseable {

    private ScoreboardObjective nextPlayerObjective;
    private DeACoudreActive game;
    private ScoreboardObjective lifeObjective;

    private boolean dirty = true;

    private long ticks;

    public DeACoudreScoreboard(DeACoudreActive game, ScoreboardObjective nextPlayerObjective, ScoreboardObjective lifeObjective) {
        this.nextPlayerObjective = nextPlayerObjective;
        this.game = game;
        this.lifeObjective = lifeObjective;
    }

    public static DeACoudreScoreboard create(DeACoudreActive game) {
        ServerScoreboard scoreboard = game.gameWorld.getWorld().getServer().getScoreboard();
        ScoreboardObjective scoreboardObjective = new ScoreboardObjective(
                scoreboard, "de_a_coudre_next",
                ScoreboardCriterion.DUMMY, new LiteralText("Dé à Coudre").formatted(Formatting.BLUE, Formatting.BOLD),
                ScoreboardCriterion.RenderType.INTEGER);
        scoreboard.addScoreboardObjective(scoreboardObjective);

        scoreboard.setObjectiveSlot(1, scoreboardObjective);
        ScoreboardObjective scoreboardObjective2 = new ScoreboardObjective(
                scoreboard, "de_a_coudre_life",
                ScoreboardCriterion.DUMMY, new LiteralText("Dé à Coudre").formatted(Formatting.BLUE, Formatting.BOLD),
                ScoreboardCriterion.RenderType.INTEGER);
        scoreboard.addScoreboardObjective(scoreboardObjective2);

        scoreboard.setObjectiveSlot(0, scoreboardObjective2);

        return new DeACoudreScoreboard(game, scoreboardObjective, scoreboardObjective2);
    }

    public void tick() {
        this.ticks++;

        if (this.dirty || this.ticks % 20 == 0) {
            this.rerender();
            this.dirty = false;
        }
    }

    private void rerender() {
        List<String> lines = new ArrayList<>(10);

        long seconds = (this.ticks / 20) % 60;
        long minutes = this.ticks / (20 * 60);

        lines.add(String.format("%sTime: %s%02d:%02d", Formatting.RED.toString() + Formatting.BOLD, Formatting.RESET, minutes, seconds));

        long playersAlive = this.game.participants().size();
        lines.add(Formatting.BLUE.toString() + playersAlive + " players alive");
        lines.add("");

        PlayerRef currentJumper = this.game.nextJumper;
        PlayerRef nextJumper = this.game.nextPlayer(false);

        if (currentJumper == null) {
            DeACoudre.LOGGER.warn("currentJumper is null!");
        } else if (nextJumper == null) {
            DeACoudre.LOGGER.warn("nextJumper is null!");
        } else {
            ServerPlayerEntity currentPlayer = currentJumper.getEntity(this.game.gameWorld.getWorld());
            ServerPlayerEntity nextPlayer = currentJumper.getEntity(this.game.gameWorld.getWorld());
            lines.add("Current Jumper: " + currentPlayer.getName().getString());
            lines.add("");
            lines.add("Next Jumper: " + nextPlayer.getName().getString());
            lines.add("");
        }
        this.render(lines.toArray(new String[0]));

        ServerScoreboard scoreboard = this.game.gameWorld.getWorld().getServer().getScoreboard();
        clear(scoreboard, lifeObjective);
        for (Map.Entry<PlayerRef, Integer> entry : this.game.lifes().entrySet()) {
            scoreboard.getPlayerScore(entry.getKey().getEntity(this.game.gameWorld.getWorld()).getName().getString(), lifeObjective)
                    .setScore(entry.getValue());
        }
    }

    private void render(String[] lines) {
        ServerScoreboard scoreboard = this.game.gameWorld.getWorld().getServer().getScoreboard();

        render(scoreboard, this.nextPlayerObjective, lines);
    }

    private static void render(ServerScoreboard scoreboard, ScoreboardObjective objective, String[] lines) {
        clear(scoreboard, objective);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            scoreboard.getPlayerScore(line, objective).setScore(lines.length - i);
        }
    }

    private static void clear(ServerScoreboard scoreboard, ScoreboardObjective objective) {
        Collection<ScoreboardPlayerScore> existing = scoreboard.getAllPlayerScores(objective);
        for (ScoreboardPlayerScore score : existing) {
            scoreboard.resetPlayerScore(score.getPlayerName(), objective);
        }
    }

    @Override
    public void close() {

        ServerScoreboard scoreboard = this.game.gameWorld.getWorld().getServer().getScoreboard();

        scoreboard.removeObjective(this.nextPlayerObjective);
        scoreboard.removeObjective(this.lifeObjective);
    }
}
