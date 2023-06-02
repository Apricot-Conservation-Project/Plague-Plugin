package main;

import arc.graphics.Color;
import arc.struct.Seq;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import arc.util.Strings;

import java.util.HashMap;
import java.util.Map;

public class CustomPlayer {

    protected Player player;
    public long playTime;
    public String rawName;
    public Team team;
    public Seq<Unit> followers = new Seq<>();
    public long controlledT5;
    public int bannedT5 = -1000;

    private Map<Team, String> colorMapping = new HashMap<Team, String>() {
        {
            put(Team.malis, "[scarlet]");
            put(Team.blue, "[royal]");
        }
    };

    public boolean connected = true;

    public CustomPlayer(Player player) {
        this.player = player;
        this.rawName = Strings.stripColors(player.name);
        player.color = Color.white;
    }

    public void reset() {
        updateName();
        player.team(Team.blue);
    }

    public void updateName() {
        String team = colorMapping.getOrDefault(player.team(), "[olive]");
        player.name = team + Strings.stripColors(this.rawName);
    }
}
