package fr.catcore.deacoudre.game;

import net.gegy1000.plasmid.game.Game;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.text.LiteralText;

public class DeACoudreScoreboard implements AutoCloseable {

    private ServerScoreboard scoreboard;
    private ScoreboardObjective scoreboardObjective;

    public DeACoudreScoreboard() {
    }

    public void create(Game game) {
        this.scoreboard = new ServerScoreboard(game.getWorld().getServer());
        this.scoreboard.setObjectiveSlot(0, null);
        this.scoreboardObjective = new ScoreboardObjective(this.scoreboard, "Lives", new ScoreboardCriterion("test"), new LiteralText("Test"), ScoreboardCriterion.RenderType.INTEGER);
    }

    @Override
    public void close() throws Exception {
        this.scoreboard = null;
    }
}
