package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.DeACoudre;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.nucleoid.plasmid.util.PlayerRef;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;
import xyz.nucleoid.plasmid.widget.SidebarWidget;

import java.util.Collection;
import java.util.Map;

public class DeACoudreScoreboard implements AutoCloseable {

    private SidebarWidget sidebar;
    private DeACoudreActive game;
    private ScoreboardObjective lifeObjective;

    private boolean dirty = true;

    private long ticks;

    public DeACoudreScoreboard(DeACoudreActive game, SidebarWidget sidebar, ScoreboardObjective lifeObjective) {
        this.sidebar = sidebar;
        this.game = game;
        this.lifeObjective = lifeObjective;
    }

    public static DeACoudreScoreboard create(DeACoudreActive game, GlobalWidgets widgets) {
        ServerScoreboard scoreboard = game.gameSpace.getWorld().getServer().getScoreboard();

        Text title = new LiteralText("Dé à Coudre").formatted(Formatting.BLUE, Formatting.BOLD);
        SidebarWidget sidebar = widgets.addSidebar(title);

        ScoreboardObjective scoreboardObjective2 = new ScoreboardObjective(
                scoreboard, "de_a_coudre_life",
                ScoreboardCriterion.DUMMY, title,
                ScoreboardCriterion.RenderType.INTEGER);
        scoreboard.addScoreboardObjective(scoreboardObjective2);

        scoreboard.setObjectiveSlot(0, scoreboardObjective2);

        return new DeACoudreScoreboard(game, sidebar, scoreboardObjective2);
    }

    public void tick() {
        this.ticks++;

        if (this.dirty || this.ticks % 20 == 0) {
            this.rerender();
            this.dirty = false;
        }
    }

    private void rerender() {
        this.sidebar.set(content -> {
            long seconds = (this.ticks / 20) % 60;
            long minutes = this.ticks / (20 * 60);

            content.writeLine(String.format("%sTime: %s%02d:%02d", Formatting.RED.toString() + Formatting.BOLD, Formatting.RESET, minutes, seconds));

            long playersAlive = this.game.participants().size();
            content.writeLine(Formatting.BLUE.toString() + playersAlive + " players alive");
            content.writeLine("");

            PlayerRef currentJumper = this.game.nextJumper;
            PlayerRef nextJumper = this.game.nextPlayer(false);

            if (currentJumper == null) {
                DeACoudre.LOGGER.warn("currentJumper is null!");
            } else if (nextJumper == null) {
                DeACoudre.LOGGER.warn("nextJumper is null!");
            } else {
                ServerPlayerEntity currentPlayer = currentJumper.getEntity(this.game.gameSpace.getWorld());
                ServerPlayerEntity nextPlayer = nextJumper.getEntity(this.game.gameSpace.getWorld());
                if (currentPlayer != null) {
                    content.writeLine("Current Jumper: " + currentPlayer.getName().getString());
                    content.writeLine("");
                }
                if (nextPlayer != null) {
                    content.writeLine("Next Jumper: " + nextPlayer.getName().getString());
                    content.writeLine("");
                }
            }
        });

        ServerScoreboard scoreboard = this.game.gameSpace.getWorld().getServer().getScoreboard();
        clear(scoreboard, lifeObjective);
        for (Map.Entry<PlayerRef, Integer> entry : this.game.lifes().entrySet()) {
            if (entry.getKey() == null) continue;
            ServerPlayerEntity playerEntity = entry.getKey().getEntity(this.game.gameSpace.getWorld());
            if (playerEntity != null) {
                scoreboard.getPlayerScore(playerEntity.getName().getString(), lifeObjective)
                    .setScore(entry.getValue());
            }
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
        ServerScoreboard scoreboard = this.game.gameSpace.getWorld().getServer().getScoreboard();
        scoreboard.removeObjective(this.lifeObjective);
    }
}
