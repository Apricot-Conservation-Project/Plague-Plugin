package main;

import mindustry.game.Team;

import java.util.ArrayList;

public class PlagueTeam {
    protected Team team;
    protected ArrayList<CustomPlayer> players = new ArrayList<>();

    public ArrayList<String> blacklistedPlayers = new ArrayList<>();
    public CustomPlayer leader;
    public boolean locked = false;
    public boolean reached_cap = false;
    public short monos = 0;

    public PlagueTeam(Team team) {
        this(team, null);
    }

    public PlagueTeam(Team team, CustomPlayer leader) {
        this.team = team;
        this.leader = leader;
    }

    public void addPlayer(CustomPlayer ply) {
        players.add(ply);
    }

    public void removePlayer(CustomPlayer ply) {
        players.remove(ply);
    }

    public boolean hasPlayer(CustomPlayer ply) {
        return players.contains(ply);
    }

}
