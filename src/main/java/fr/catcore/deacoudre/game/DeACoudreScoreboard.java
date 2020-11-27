package fr.catcore.deacoudre.game;

import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

            ServerPlayerEntity currentJumper = this.game.nextJumper;
            ServerPlayerEntity nextJumper = this.game.nextPlayer(false);

            if (currentJumper != null) {
                content.writeLine("Current Jumper: " + currentJumper.getName().getString());
                content.writeLine("");
            }
            if (nextJumper != null) {
                content.writeLine("Next Jumper: " + nextJumper.getName().getString());
                content.writeLine("");
            }
        });

        ServerScoreboard scoreboard = this.game.gameSpace.getWorld().getServer().getScoreboard();
        clear(scoreboard, lifeObjective);
        for (Map.Entry<ServerPlayerEntity, Integer> entry : this.game.lifes().entrySet()) {
            if (entry.getKey() == null) continue;
            ServerPlayerEntity playerEntity = entry.getKey();
            scoreboard.getPlayerScore(playerEntity.getName().getString(), lifeObjective)
                .setScore(entry.getValue());
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
