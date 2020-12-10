package fr.catcore.deacoudre.game.sequential;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
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

public class DeACoudreSequentialScoreboard implements AutoCloseable {

    private SidebarWidget sidebar;
    private DeACoudreSequential game;
    private ScoreboardObjective lifeObjective;

    private boolean dirty = true;

    private long ticks;

    public DeACoudreSequentialScoreboard(DeACoudreSequential game, SidebarWidget sidebar, ScoreboardObjective lifeObjective) {
        this.sidebar = sidebar;
        this.game = game;
        this.lifeObjective = lifeObjective;
    }

    public static DeACoudreSequentialScoreboard create(DeACoudreSequential game, GlobalWidgets widgets) {
        ServerScoreboard scoreboard = game.gameSpace.getWorld().getServer().getScoreboard();

        Text title = new LiteralText("Dé à Coudre").formatted(Formatting.BLUE, Formatting.BOLD);
        SidebarWidget sidebar = widgets.addSidebar(title);

        ScoreboardObjective scoreboardObjective2 = new ScoreboardObjective(
                scoreboard, "de_a_coudre_life",
                ScoreboardCriterion.DUMMY, title,
                ScoreboardCriterion.RenderType.INTEGER);
        scoreboard.addScoreboardObjective(scoreboardObjective2);

        scoreboard.setObjectiveSlot(0, scoreboardObjective2);

        return new DeACoudreSequentialScoreboard(game, sidebar, scoreboardObjective2);
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

            ServerPlayerEntity currentJumper = this.game.currentJumper;
            ServerPlayerEntity nextJumper = this.game.getNextJumper();

            if (currentJumper != null) {
                content.writeLine("Jumping: " + currentJumper.getName().getString());
            }
            if (nextJumper != null) {
                content.writeLine("Up Next: " + nextJumper.getName().getString());
            }
        });

        ServerScoreboard scoreboard = this.game.gameSpace.getWorld().getServer().getScoreboard();
        clear(scoreboard, lifeObjective);
        for (Object2IntMap.Entry<ServerPlayerEntity> entry : this.game.lives()) {
            if (entry.getKey() == null) continue;
            ServerPlayerEntity playerEntity = entry.getKey();
            scoreboard.getPlayerScore(playerEntity.getName().getString(), lifeObjective)
                .setScore(entry.getIntValue());
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
