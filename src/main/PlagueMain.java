package main;

import arc.*;
import arc.graphics.Color;
import mindustry.world.*;
import arc.math.geom.*;
import arc.math.Mathf;
import arc.struct.*;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.core.GameState;
import mindustry.core.GameState.State;
import mindustry.game.EventType;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import base.DBInterface;
import base.CustomPlayer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;

import base.Base;

import static mindustry.Vars.*;

public class PlagueMain extends Plugin {

    private boolean firstRun = true;
    private boolean resetting = false;
    private final Seq<Player> destroyers = new Seq<>();
    /**
     * player 1 runs /teamjoin player2
     * this adds a entry from player 2 to player 1
     * player 2 is told that they can run /teaminvite to accept the request
     * player 2 runs /teaminvite
     * we search the invitations to find ourselves in the map.
     * we add player 1 to player 2's team.
     * if player2 runs /teamjoin, we switch the order of the key and value.
     * its worth nothing that this means if multiple people /teamjoin player2,
     * only the latest one gets selected.
     */
    private final HashMap<Player, Player> invitations = new HashMap<>();

    private int teamsCount;

    private Rules rules;

    private final HashMap<UnitType, Float> originalUnitHealth = new HashMap<>();

    private HashMap<Team, PlagueTeam> teams;

    private final int pretime = 6;

    private final RTInterval corePlaceInterval = new RTInterval(20);
    private final RTInterval tenMinInterval = new RTInterval(60 * 10);

    private float multiplier;
    private static final DecimalFormat df = new DecimalFormat("0.00");

    private final int winTime = 60; // In minutes

    private float realTime = 0f;
    private static long startTime;
    private boolean newRecord;

    private String plagueLb;
    private String survLb;

    private final ArrayList<Integer> rotation = new ArrayList<>();
    private int mapIndex = 0;

    private int mapRecord;
    private int avgSurvived;
    private int mapPlays;

    private final DBInterface db = new DBInterface();
    private final Base base = new Base(db, () -> {
        return "[accent]Time survived: [orange]" + Base.formatTime(Base.seconds) + "[accent].\n" +
                "All-time record: [gold]" + Base.formatTime(mapRecord) + "[accent].\n\nDisable hud with [scarlet]/hud";
    });

    private boolean isSerpulo = true;

    private boolean pregame;
    private boolean gameover;
    private boolean hasWon;

    public int counts;
    final short MONO_LIMIT = 500;
    final String mono_info = "[accent]Monos([white][]) will explode, adding to your teams internal mono pool. Abstract monos will then produce [scarlet]20[] [white][] + [white][] per second. You can have a maximum of "
            + String.valueOf(MONO_LIMIT) + " \"monos\", meaning " + String.valueOf(MONO_LIMIT * 20)
            + " [white][] + [white][] per second!";
    final String info = "[olive]Plague[accent] is a survival game mode with two teams," +
            " the [scarlet]Plague [accent]and [green]Survivors[accent].\n\n" +
            "The [scarlet]Plague[accent] build up their economy to make the biggest army possible, and try to" +
            " break through the [green]Survivors[accent] defenses.\n\n" +
            "The [green]Survivors[accent] build up a huge defense and last 60 minutes to win.\n\n" +
            "To become a " +
            "[green]Survivor[accent], you must place a core in the first 2 minutes of the game, where you are " +
            "allowed to choose your team. Place any block to place a core at the start of the game.\n\n" +
            "Air units do no damage before 45 minutes\n\n" + mono_info;

    @Override
    public void init() {
        /*
         * CREATE TABLE `bans` (
         * `ip` varchar(40) NOT NULL,
         * `uuid` varchar(40) NOT NULL,
         * `bannedName` varchar(200) DEFAULT NULL,
         * `banPeriod` int DEFAULT NULL,
         * `banReason` varchar(200) DEFAULT NULL,
         * PRIMARY KEY (`ip`,`uuid`)
         * );
         * CREATE TABLE `mindustry_data` (
         * `uuid` varchar(40) NOT NULL,
         * `playTime` int DEFAULT '0',
         * `userID` int DEFAULT NULL,
         * `xp` int DEFAULT '0',
         * `wins` int DEFAULT '0',
         * `latestName` varchar(40) DEFAULT '',
         * `adminRank` int DEFAULT '0',
         * `hudOn` tinyint(1) DEFAULT '1',
         * PRIMARY KEY (`uuid`),
         * KEY `userID` (`userID`),
         * CONSTRAINT `mindustry_data_ibfk_1` FOREIGN KEY (`userID`) REFERENCES `data`
         * (`userID`) ON UPDATE CASCADE
         * );
         * CREATE TABLE `mindustry_map_data` (
         * `gamemode` varchar(50) NOT NULL,
         * `mapID` varchar(50) NOT NULL,
         * `survivorRecord` int DEFAULT '0',
         * `avgSurvived` int DEFAULT '0',
         * `plays` int DEFAULT '0',
         * PRIMARY KEY (`gamemode`,`mapID`)
         * );
         */

        if (System.getenv("DB_USER") == null) {
            Log.err("Set the env variables DB_USER and DB_PASSWORD");
            System.exit(1);
        }
        db.connect("users", System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        base.init();
        initRules();

        netServer.assigner = (player, players) -> {
            if (base.uuidMapping.containsKey(player.uuid())) {
                Team team = base.uuidMapping.get(player.uuid()).team;
                if (team == Team.blue && !pregame)
                    return Team.malis;
                return team;
            }
            if (pregame) {
                return Team.blue;
            } else {
                return Team.malis;
            }
        };

        netServer.admins.addActionFilter((action) -> {
            // dont care
            if (action.player == null || action.tile == null)
                return true;

            // mustnt touch power source
            if (action.tile.block() == Blocks.powerSource)
                return false;

            if (action.block == null)
                return true;

            // plague cant build banned blocks
            if (action.player.team() == Team.malis) {
                if (hasWon ? PlagueData.plagueBanned.contains(action.block)
                        : PlagueData.plagueBannedPreWin.contains(action.block))
                    return false;
                return true; // rest does not concern plague
            }

            // survivors cant build banned blocks
            if (action.block != null
                    && PlagueData.survivorBanned.contains(action.block)
                    && action.player.team() != Team.blue) {
                return false;
            }
            return true;
        });

        Events.run(EventType.Trigger.update, () -> {
            if (resetting || firstRun)
                return;
            // Spawn player in core if they aren't
            if (pregame) {
                for (Player player : Groups.player) {
                    if (player.dead())
                        CoreBlock.playerSpawn(Team.malis.cores().random().tile, player);
                }
            }

            Team.malis.cores().forEach(c -> {
                c.health(Float.MAX_VALUE);
            });

            // Notification about placing a core, then starting game
            if (counts < pretime && corePlaceInterval.get(base.seconds)) {
                counts++;
                if (counts == pretime) {
                    pregame = false;
                    for (Player ply : Groups.player) {
                        if (ply.team() == Team.blue) {
                            infect(Base.uuidMapping.get(ply.uuid()), true);
                            updatePlayer(ply);
                        }
                    }

                    teams.remove(Team.blue);

                    if (teams.size() == 1) {
                        endgame(new Seq<>());
                    } else {
                        Call.sendMessage(
                                "[accent]The game has started! [green]Survivors[accent] must survive for [gold]" +
                                        winTime + "[accent] minutes to win!");
                    }

                } else {
                    for (Player ply : Groups.player) {
                        if (ply.team() == Team.blue) {
                            ply.sendMessage("[accent]You have [scarlet]" + (pretime * 20 - counts * 20) +
                                    " [accent]seconds left to place a core. Place any block to place a core.");
                        }
                    }
                }

            }

            realTime = System.currentTimeMillis() - startTime;
            base.seconds = (int) (realTime / 1000);

            // Runs if survivors hit win condition
            boolean justWon = false;
            if (!gameover && !hasWon && base.seconds > winTime * 60) {
                hasWon = true;
                justWon = true;
                Groups.player.each((player) -> {
                    if (player.team() == Team.malis) {
                        Call.infoMessage(player.con,
                                "The survivors have evacuated all civilians and launched the inhibitors! " +
                                        "The plague is super powerful now, finish what's left of the survivors!");
                    } else {
                        Call.infoMessage(player.con,
                                "All civilians have been evacuated, and the inhibitors have been launched!" +
                                        "The plague are now extremely powerful and will only get worse. Defend the noble few for as long as possible!");
                        player.sendMessage("[gold]You win![accent]");
                        CustomPlayer cPly = Base.uuidMapping.get(player.uuid());
                        cPly.wins++;
                        player.sendMessage(
                                "[gold]+1 wins[accent] for a total of [gold]" + cPly.wins + "[accent] wins!");
                        int addXp = player.admin ? 6000 : 3000;
                        cPly.addXP(addXp, "[accent]+[scarlet]" + addXp + "xp[accent] for winning!");
                    }

                    updatePlayer(player);

                });
            }

            if (!gameover && !newRecord && base.seconds > mapRecord) {
                newRecord = true;
                Call.sendMessage("[gold]New record![accent] Old record of "
                        + Base.formatTime(mapRecord)
                        + " was beaten!");
            }

            if (tenMinInterval.get(base.seconds)) {
                float multiplyBy = hasWon ? 1.4f : 1.2f;
                multiplier *= multiplyBy;
                // state.rules.unitDamageMultiplier = multiplier;

                for (UnitType u : Vars.content.units()) {
                    if (u != UnitTypes.alpha && u != UnitTypes.beta && u != UnitTypes.gamma) {
                        u.health = originalUnitHealth.get(u) * multiplier;
                    }
                }
                String percent = "" + Math.round((multiplyBy - 1) * 100);
                Call.sendMessage("[accent]Units now have " +
                        "[scarlet]" + percent + "%[accent] more health " +
                        "for a total multiplier of [scarlet]" + df.format(multiplier) + "x");
                if (!justWon) {
                    Groups.player.each((player) -> {
                        if (player.team() != Team.malis) {
                            CustomPlayer cPly = base.uuidMapping.get(player.uuid());
                            int addXP = Math.min((int) (100.0 * multiplier), 2500);
                            cPly.addXP(addXP, "[accent]+[scarlet]" + addXP + "xp[] for surviving!");
                        }
                    });
                }
            }

            // runs 20 times a second, 1 mono = 20/s. caps at 255 monos = 5100/s
            if (Core.graphics.getFrameId() % 4 == 0) {
                for (PlagueTeam team : teams.values()) {
                    if (team.monos > 0 && !team.team.cores().isEmpty()) {
                        CoreBuild core = team.team.core();
                        if ((core.items.get(Items.lead) + team.monos) < core.storageCapacity)
                            core.items.add(Items.lead, team.monos);
                        if ((core.items.get(Items.copper) + team.monos) < core.storageCapacity)
                            core.items.add(Items.copper, team.monos);
                    }
                }
            }
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            loadPlayer(event.player);
        });

        Events.on(EventType.BuildSelectEvent.class, event -> {
            Player player = event.builder.getPlayer();
            // core placing is only relevant for unsettled pioneers placing blocks
            if (player == null || event.breaking || event.team != Team.blue)
                return;

            event.tile.removeNet();

            // check if it fits
            if (!canPlace(isSerpulo ? Blocks.spectre : Blocks.malign, event.tile))
                return;

            Team chosenTeam = null;
        // @formatter:off
            loop: { for (Teams.TeamData t : state.teams.getActive()) {
                // skip plague
                if (t.team == Team.malis)
                    continue;

                for (CoreBlock.CoreBuild core : t.cores) {
                    // check if we can join a team
                    if (new Vec2(event.tile.x, event.tile.y).dst(new Vec2(core.tile.x, core.tile.y)) > 150)
                        continue;

                    chosenTeam = t.team;
                    // TODO: allow joining teams when two tea ms are very close
                    if (teams.get(chosenTeam).locked) {
                        player.sendMessage("[accent]This team is locked, you cannot join it!");
                        return;
                    }
                    if (teams.get(chosenTeam).blacklistedPlayers.contains(player.uuid())) {
                        player.sendMessage("[accent]You have been blacklisted from this team!");
                        return;
                    }
                    break loop;
                }
            }}
            // @formatter:on
            // couldnt find a team, make a new one!
            if (chosenTeam == null) {
                teamsCount++;
                // i have no idea why its + 6!
                chosenTeam = Team.all[teamsCount + 6];
                teams.put(chosenTeam, new PlagueTeam(chosenTeam, base.uuidMapping.get(player.uuid())));
                base.historyHandler.addEntry(event.tile.x, event.tile.y,
                        "[green] + [accent]" + player.name + "[accent]: created a team and built a core");
            } else {
                base.historyHandler.addEntry(event.tile.x, event.tile.y,
                        "[green] + [accent]" + player.name + "[accent]: built a core, joining team #" + chosenTeam.id);
            }

            teams.get(chosenTeam).addPlayer(base.uuidMapping.get(player.uuid()));

            player.team(chosenTeam);
            base.uuidMapping.get(player.uuid()).team = chosenTeam;
            updatePlayer(player);

            event.tile.setNet(isSerpulo ? Blocks.coreFoundation : Blocks.coreCitadel, chosenTeam, 0);
            state.teams.registerCore((CoreBlock.CoreBuild) event.tile.build);
            // if just joining the team, only add a little copper+lead.
            // or beryllium+graphite if erekir.
            if (state.teams.cores(chosenTeam).size != 1) {
                event.tile.build.items
                        .add(isSerpulo ? PlagueData.survivorIncrementSerpulo : PlagueData.survivorIncrementErekir);
                return;
            }

            for (ItemStack stack : isSerpulo ? PlagueData.survivorLoadoutSerpulo : PlagueData.survivorLoadoutErekir) {
                Call.setItem(event.tile.build, stack.item, stack.amount);
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            if (event.tile.block() instanceof CoreBlock) {
                if (event.tile.team() == Team.malis) {
                    // Call.setTile(event.tile, event.tile.block(), Team.malis, 0)
                    // world.tiles.getc(event.tile.x, event.tile.y).build.health = Float.MAX_VALUE;
                    Call.sendMessage("[scarlet]stahp killing plague >:(");
                    return;
                }
                if (event.tile.team().cores().size == 1) {
                    Team deadTeam = event.tile.team();
                    Seq<CustomPlayer> winners = new Seq<>();
                    Log.info("Infecting " + deadTeam);
                    killTiles(deadTeam);
                    if (!pregame) {
                        Groups.player.each((player) -> {
                            if (player.team() == Team.malis) {
                                var cPly = Base.uuidMapping.get(player.uuid());
                                if (!hasWon) {
                                    cPly.addXP(1000,
                                            "[accent]+[scarlet]1000xp[] for infecting survivors [gold]before the win time!");
                                } else {
                                    cPly.addXP(500, "[accent]+[scarlet]500xp[] for infecting survivors");
                                }
                            }
                        });
                    }
                    if (!teams.containsKey(deadTeam)) {
                        Call.sendMessage(
                                "Welp that's not supposed to happen... Let [purple]me[white] know what just happened and what caused this"
                                        +
                                        " message to appear");
                        return;
                    }
                    for (CustomPlayer cPly : teams.get(deadTeam).players) {
                        if (teams.size() == 2 && cPly.connected) {
                            winners.add(cPly);
                            Call.infoMessage(cPly.player.con, "[green]You survived the longest\n" +
                                    (newRecord ? "    [gold]New record!\n" : "") +
                                    "[accent]Survive time: " + Base.formatTime(base.seconds) + ".");
                            int addXP = Math.min((int) (200.0 * multiplier), 20000);
                            cPly.addXP(addXP, "[accent]+[scarlet]" + addXP + "xp[] for surviving the longest!");
                            if (newRecord)
                                cPly.addXP(30000, "[accent]+[scarlet]30k xp[] for setting a new record!");
                        }
                        infect(cPly, false);
                    }
                    teams.remove(deadTeam);
                    if (teams.size() == 1) {
                        Log.info("Dead block endgame");
                        endgame(winners);
                    }
                } else if (!pregame) {
                    Groups.player.each((player) -> {
                        if (player.team() == Team.malis) {
                            var cPly = Base.uuidMapping.get(player.uuid());
                            cPly.addXP(hasWon ? 200 : 600, String.format(
                                    "[accent]+[scarlet]%dxp[] for killing a core!", hasWon ? 200 : 600), true);
                        }
                    });
                }
            }
        });

        Events.on(EventType.TapEvent.class, event -> {
            final Team t = event.tile.team();
            // comes from /destroy.
            int index = destroyers.indexOf(event.player);
            if (index != -1) {
                destroyers.remove(index);
                Player player = event.player;
                if (event.tile.build == null) {
                    player.sendMessage(String.format("[scarlet]No building at (%d, %d).", event.tile.x, event.tile.y));
                    return;
                }

                // i would structure this admin || (all checks) but i want the error handling
                if (player.admin) { // be admin (skip checks)
                    event.tile.build.kill();
                    for (Tile z : event.tile.getLinkedTiles(new Seq<>())) {
                        base.historyHandler.addEntry(z.x, z.y,
                                "[scarlet]! [accent]" + event.player.name + "[accent]:" +
                                        " used [scarlet]/destroy[accent] to break this block");
                    }
                    return;
                }
                if (t != player.team()) { // of same team
                    player.sendMessage("[scarlet]Can't break block of other team.");
                    return;
                }
                // this feels unnecessary but whatever
                CustomPlayer cPly = base.uuidMapping.get(player.uuid());
                PlagueTeam pTeam = teams.get(cPly.team);
                if (!pTeam.leader.player.uuid().equals(cPly.player.uuid())) { // be team leader
                    player.sendMessage("[scarlet]You must be the team leader to destroy a block!");
                    return;
                }
                event.tile.build.kill();
                for (Tile z : event.tile.getLinkedTiles(new Seq<>())) {
                    base.historyHandler.addEntry(z.x, z.y,
                            "[scarlet]! [accent]" + event.player.name + "[accent]:" +
                                    " used [scarlet]/destroy[accent] to break this block");
                }
                return;
            }

            // @formatter:off
            if (
                // plague team
                t == Team.malis
                // it isnt a vault
                || event.tile.block() != (isSerpulo ? Blocks.vault : Blocks.reinforcedVault)
                // player is tapping other teams vault
                || event.player.team() != t
            ) return;
            // (?)
            float dist = new Vec2(t.core().tile.x, t.core().tile.y).dst(new Vec2(event.tile.x, event.tile.y)) * (float)15.0;
            int cost = Mathf.clamp((int)snap(dist, (float)500f), 1000, 10000);
            
            // not enough items
            // if core items is 500, and vault items is 500, and cost is 1000, we can still make a core
            int wallet = t.core().items.get(Items.thorium) + event.tile.build.items.get(Items.thorium);
            if (wallet <= cost) {
                // i could not for the life of me get Call.label to work.
                event.player.sendMessage("[accent]Not enough [white][] to make a core. Needs [gold]" + -(wallet-cost) + "[] more [white][].");
                return;
            };
            // @formatter:on
            // remove 1000 - vault items thorium
            // if a vault is next to a core, its items are the teams thorium,
            // so building a chain of cores is free. (1000 - thorium in core < 0 = true)
            final int remove = cost - event.tile.build.items.get(Items.thorium);
            if (remove > 0)
                event.tile.team().core().items.remove(Items.thorium, remove);
            // bastion is 4x4 so you get wierd results, use a shard.
            final Block core = Blocks.coreShard; // isSerpulo ? Blocks.coreShard : Blocks.coreBastion;
            // event.tile.build.tile to not move the core when the click isnt centered
            event.tile.build.tile.setNet(core, t, 0);
            base.historyHandler.addEntry(event.tile.build.tile.x, event.tile.build.tile.y,
                    "[green] + [accent]" + event.player.name + "[accent]: built a core");
        });

        Events.on(EventType.UnitDestroyEvent.class, event -> {
            if (event.unit.type == UnitTypes.mono) {
                if (event.unit.team.core() == null) {
                    return;
                }
                PlagueTeam team = teams.get(event.unit.team);
                team.monos = (short) Math.min((int) (team.monos + 1), (int) MONO_LIMIT);
                // youve got monos
                for (Player player : Groups.player) {
                    if (player.team() == event.unit.team()) {
                        Call.label(player.con,
                                "monos = " + (team.monos != MONO_LIMIT ? String.valueOf(team.monos) : "MAX"),
                                5f, event.unit.tileX() * 8, event.unit.tileY() * 8);
                    }
                }
            }
        });
        // monos must die
        Events.on(EventType.UnitCreateEvent.class, event -> {
            // im pretty sure this actually never happens;
            // there seems to be a upgrade thing, that doesnt let horizons get out at all.
            // ill keep it just in case though, what harm could it cause?
            if ((event.unit.type == UnitTypes.horizon || event.unit.type == UnitTypes.zenith)
                    && event.unit.team != Team.malis) {
                // let players know they can't build this unit
                Call.label(
                        String.format("Survivors can't build %s!",
                                (event.unit.type == UnitTypes.horizon ? "horizon" : "zenith")),
                        5f, event.spawner.tileX() * 8, event.spawner.tileY() * 8);
            } else if (event.unit.type == UnitTypes.mono) {
                PlagueTeam team = teams.get(event.unit.team);
                if (team == null)
                    return;
                if (team.monos == MONO_LIMIT && !team.reached_cap) {
                    team.reached_cap = true;
                    team.players.forEach((p) -> {
                        p.player.sendMessage(
                                "[accent]You have reached the mono cap, feel free to delete the mono factory. See /monos for more information.");
                    });
                }
                // dont return: kill the monos even if their death is meaningless
            } else {
                return;
            }
            event.unit.health = 0;
            event.unit.dead = true;
        });
    }

    /** joins a to b */
    void join(Player a, Player b) {
        // join
        a.team(b.team());
        // update
        CustomPlayer aply = base.uuidMapping.get(a.uuid());
        PlagueTeam aTeam = teams.get(a.team());
        // tried to use if let lol
        if (aTeam != null)
            aTeam.removePlayer(aply);
        if (!PlagueData.transferUnits.contains(a.unit().type))
            a.clearUnit();
        if (aTeam.players.size() == 0) {
            aTeam.locked = true;
            killTiles(aTeam.team);
        }
        a.team(b.team());
        updatePlayer(a);
        teams.get(b.team()).addPlayer(aply);
        // explain
        b.sendMessage(String.format("[accent]%s[accent] is on your team now.", a.name));
        a.sendMessage(String.format("[accent]You have joined %s[accent]'s team.", b.name));
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        base.register_server(handler);
        handler.removeCommand("host");
        handler.removeCommand("gameover");
        handler.removeCommand("runwave");
        handler.removeCommand("shuffle");
        handler.removeCommand("nextmap");
        handler.register("host", "[map(index)]", "Host the plague game mode", args -> {
            if (!Vars.state.is(GameState.State.menu)) {
                Log.err("Stop the server first.");
                return;
            }
            mapReset(args);
        });

        handler.register("gameover", "[map(index)]", "End the plague game", args -> {
            Call.sendMessage("[scarlet]server[accent] has ended the plague game. Ending in 10 seconds...");
            Log.info("game ended.");
            endgame(new Seq<>(), args);
        });
    }

    public float snap(float f, float step) {
        return Mathf.floor(f / step + (float) 0.5) * step;
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        base.register_player(handler);
        handler.<Player>register("endplague", "[map]", "[scarlet]Ends the plague game (admin only)", (args, player) -> {
            if (!player.admin) {
                player.sendMessage("[accent]Admin only!");
                return;
            }
            Call.sendMessage("[scarlet]" + player.name + " [accent]has ended the plague game. Ending in 10 seconds...");
            endgame(new Seq<>(), args);
        });

        handler.<Player>register("monos", "explain monos", (args, player) -> {
            Call.infoMessage(player.con, mono_info);
        });

        handler.<Player>register("destroy", "Destroy a building", (args, player) -> {
            if (player.team() == Team.malis && !player.admin) {
                player.sendMessage("[scarlet]infected cannot use /destroy");
                return;
            }
            destroyers.add(player);
            player.sendMessage("[accent]Tap the block you want to destroy.");
        });

        handler.<Player>register("lb", "Show the leaderboard", (_args, player) -> {
            if (player.team() == Team.malis)
                player.sendMessage(plagueLb);
            else
                player.sendMessage(survLb);

        });

        handler.<Player>register("multiplier", "The current health multiplier", (_args, player) -> {
            player.sendMessage(String.format("[scarlet]%.1fx [accent]health", multiplier));
        });

        handler.<Player>register("monocount", "The current number of monos your team owns", (_args, player) -> {
            player.sendMessage(String.format("[scarlet]%d [white]", teams.get(player.team()).monos));
        });

        // @formatter:off
        // doesnt work, use `use `/js Vars.world.tiles.getc(Vars.player.x, Vars.player.y).setNet(Blocks.worldProcessor, Vars.player.team, 0)`
        // @formatter:on
        /*
         * handler.<Player>register("wp", "Creates a world processor(admin only)",
         * (args, player) -> {
         * if (!player.admin) {
         * player.sendMessage("[scarlet]Not admin!");
         * return;
         * }
         * Tile t = world.tiles.getc((int) player.x, (int) player.y);
         * if (t.build != null) {
         * player.sendMessage("[accent]Block already exists");
         * return;
         * }
         * t.setNet(Blocks.worldProcessor, player.team(), 0);
         * });
         */

        handler.<Player>register("status", "Server status", (_arg, player) -> {
            // Color col = Color.red.cpy().lerp(Color.green,
            // Core.graphics.getFramesPerSecond() / 60);
            Color col = Seq.with(Color.green, Color.yellow, Color.orange, Color.red)
                    .reverse()
                    .get(Math.min(Core.graphics.getFramesPerSecond(), 60) / 20);
            player.sendMessage(
                    String.format("[#%s]%d[accent] TPS, [gold]%d[] MB used.\n\n[gold]%d[] units.",
                            col.toString(),
                            Core.graphics.getFramesPerSecond(),
                            Core.app.getJavaHeap() / 1024 / 1024,
                            Groups.unit.size()));
        });

        // if no player specified, try and accept the request
        handler.<Player>register("teaminvite", "[player]", "Invite a player to join your team.", (arg, self) -> {
            if (self.team() == Team.blue) {
                self.sendMessage("[accent]Cannot invite to scout team");
                return;
            }
            if (arg.length == 0) {
                // Teamjoin puts us as the key and them as the value.
                Player them = invitations.remove(self);
                if (them == null) {
                    self.sendMessage(
                            "[accent]Nobody asked to join. Perhaps you meant to invite somebody? Do so with /teaminvite friend");
                    return;
                }
                if (them.team() == self.team()) {
                    // will occur if blue does /teamjoin, then places a block,
                    // then we run /teaminvite.
                    self.sendMessage("[accent]You are on the same team now.");
                    return;
                }
                join(them, self);
                return;
            }
            // this function searches by name and by id.
            Player target = Base.find(arg[0], self);
            if (target == null) {
                self.sendMessage("[scarlet]Couldn't find player.");
                return;
            }

            if (target.team() == self.team()) {
                self.sendMessage(String.format("[accent]%s[accent] is on your team already.", target.name));
                return;
            }

            // already invited
            if (invitations.get(target) != null) {
                // you only spam yourself
                self.sendMessage(String.format("[accent]Invited %s[accent].", target.name));
                return;
            }

            // they have already asked to join.
            if (invitations.remove(self) != null) {
                join(target, self);
                return;
            }

            self.sendMessage(String.format("[accent]Invited %s[accent].", target.name));
            // if we invite somebody as plague just tell them to run /infect
            target.sendMessage(
                    String.format("[accent]Run %s to join [accent]%s[accent]'s team.",
                            (self.team() == Team.malis ? "/infect" : "/teamjoin"), self.name));
            if (self.team() != Team.malis)
                invitations.put(target, self);
        });
        // the logic between these two functions is nearly identical,
        // just different strings. if only java had macros.
        // read above for comments.
        handler.<Player>register("teamjoin", "[team/id]", "Join a team", (arg, self) -> {
            if (arg.length == 0) {
                Player them = invitations.remove(self);
                if (them == null) {
                    self.sendMessage(
                            "[accent]Nobody invited you. Perhaps you meant to request to join somebody? Do so with /join friend.");
                    return;
                }

                if (them.team() == self.team()) {
                    self.sendMessage("[accent]You are on the same team now.");
                    return;
                }
                join(self, them);
                return;
            }
            Player target = Base.find(arg[0], self);
            if (target == null) {
                try {
                    // handle /teamjoin 7
                    target = teams.get(Team.all[Integer.parseInt(arg[0])]).leader.player;
                } catch (Exception _e) {
                    self.sendMessage("[scarlet]Couldn't find player.");
                    return;
                }
            }
            if (target.team() == Team.blue) {
                self.sendMessage("[accent]Cannot join scout team");
                return;
            }

            if (target.team() == self.team()) {
                self.sendMessage(String.format("You are already on [accent]%s[accent]'s team.", target.name));
                return;
            }

            if (invitations.get(target) != null) {
                self.sendMessage(String.format("[accent]Asked to join [accent]%s[accent].", target.name));
                return;
            }

            if (invitations.remove(self) != null) {
                join(self, target);
                return;
            }

            // teamjoin exclusive code.
            if (target.team() == Team.malis) {
                if (teams.get(self.team()).players.size() == 1) {
                    player.sendMessage("[accent]Run /infect to join. This will destroy your team.");
                    return;
                }
                infect(base.uuidMapping.get(self.uuid()), true);
                return;
            }
            self.sendMessage(String.format("[accent]Asked to join [accent]%s[accent].", target.name));
            target.sendMessage(
                    String.format("[accent]Run /teaminvite to let [accent]%s[accent] in to your team.",
                            self.name));
            invitations.put(target, self);
        });

        handler.<Player>register("turrets", "Count your turrets", (_arg, player) -> {
            if (player.team() == Team.malis) {
                player.sendMessage("[accent][purple]Plague[] team cannot have turrets.");
                return;
            }
            if (player.team() == Team.blue) {
                player.sendMessage("[accent]Become a survivor first.");
                return;
            }
            HashMap<Block, Integer> builds = new HashMap<>();
            for (Building b : Groups.build)
                if (b.team == player.team() && b instanceof TurretBuild)
                    builds.put(b.block, builds.getOrDefault(b.block, 0) + 1);
            if (builds.size() == 0) {
                player.sendMessage("[accent]You have no turrets. How are you alive?");
                return;
            }
            StringBuilder s = new StringBuilder("[accent]");
            builds.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(10)
                    .forEach(e -> {
                        s.append("[white]");
                        s.append(e.getKey().emoji());
                        s.append("[gold]");
                        s.append(e.getValue());
                        s.append("[accent]\n");
                    });
            player.sendMessage(s.toString());
        });

        handler.<Player>register("infect", "Infect yourself", (args, player) -> {
            if (player.team() == Team.malis) {
                player.sendMessage("[accent]Already infected!");
                return;
            }
            CustomPlayer cPly = base.uuidMapping.get(player.uuid());
            infect(cPly, true);
        });

        handler.<Player>register("stats", "Display stats about the current map", (args, player) -> {
            String s = "[accent]Map stats for: [white]" + state.map.name() + "\n" +
                    "[accent]Author: [white]" + state.map.author() + "\n" +
                    "[accent]Plays: [gold]" + mapPlays + "\n" +
                    "[accent]Average time survived: " + Base.formatTime(avgSurvived) + "\n" +
                    "[accent]Suvivor record: " + Base.formatTime(mapRecord);
            player.sendMessage(s);

        });

        handler.<Player>register("playtime", "How long you have played", (args, player) -> {
            player.sendMessage(
                    "[accent]Playtime: "
                            + Base.formatTime(Duration.ofMinutes(base.uuidMapping.get(player.uuid()).playTime)));
        });

        handler.<Player>register("time", "Display the time now", (args, player) -> {
            player.sendMessage("[accent]Time: " + Base.formatTime(Duration.ofSeconds(base.seconds)));
        });

        handler.<Player>register("rules", "Display the rules", (args, player) -> {
            player.sendMessage(
                    "[accent] Basic rules (these are on top of the obvious ones like no griefing, racial slurs etc):\n\n"
                            +
                            "[gold] - [scarlet]No[accent] survivor PVP. Do not attack other survivors as a survivor\n" +
                            "[gold] - [scarlet]No[accent] malicious cores. Do not place a core inside someone else's base on purpose\n"
                            +
                            "[gold] - [scarlet]Don't[accent] waste resources on useless or unneeded schematics\n" +
                            "[gold] - [scarlet]Don't[accent] blast/plast/pyra/oil bomb. [gold]Fuse bombing is ok\n");
        });

        handler.<Player>register("info", "Display info about the current game", (args, player) -> {
            Call.infoMessage(player.con, info);
        });

        handler.<Player>register("teamlock",
                "Toggles locking team, preventing other players from joining your team (leader only)",
                (args, player) -> {
                    if (player.team() == Team.blue || player.team() == Team.malis) {
                        player.sendMessage(("[accent]You can only lock a team as a survivor!"));
                        return;
                    }

                    CustomPlayer cPly = base.uuidMapping.get(player.uuid());
                    PlagueTeam pTeam = teams.get(cPly.team);

                    if (!pTeam.leader.player.uuid().equals(cPly.player.uuid())) {
                        player.sendMessage("[accent]You must be team leader to lock the team!");
                        return;
                    }

                    if (pTeam.locked) {
                        pTeam.locked = false;
                        player.sendMessage(
                                "[accent]Team is [scarlet]no longer locked[accent], other players can now join!");
                    } else {
                        pTeam.locked = true;
                        player.sendMessage("[accent]Team is [scarlet]now locked[accent], no one else can join!");
                    }

                });

        handler.<Player>register("teamkick", "[id/name]", "Kick a player from your team (leader only)",
                (args, player) -> {

                    if (player.team() == Team.blue || player.team() == Team.malis) {
                        player.sendMessage(("[accent]You can only kick players from a team as a survivor!"));
                        return;
                    }

                    // Same as the variable player, but converted into a CustomPlayer for additional
                    // plague features.
                    CustomPlayer plaguePlayer = base.uuidMapping.get(player.uuid());
                    PlagueTeam playerTeam = teams.get(plaguePlayer.team);

                    // Checks if caller is the leader, exits if not (insufficient permission)
                    if (!Objects.equals(playerTeam.leader.player.uuid(), plaguePlayer.player.uuid())) {
                        player.sendMessage("[accent]You must be team leader to kick players!");
                        return;
                    }

                    var teamMate = Base.find(args[0], player);
                    if (teamMate != null && teamMate.team() != player.team()) {
                        Team teamToSet = pregame ? Team.blue : Team.malis;
                        CustomPlayer target = base.uuidMapping.get(player.uuid());
                        target.team = teamToSet;
                        teamMate.team(teamToSet);
                        teamMate.sendMessage(
                                ("[accent]You have been kicked from the team by " + player.plainName() + "[accent]!"));
                        playerTeam.blacklistedPlayers.add(target.player.uuid());
                        playerTeam.removePlayer(target);
                        updatePlayer(teamMate);
                        return;
                    }

                    StringBuilder message = new StringBuilder(
                            "[accent]Invalid syntax!\n\nYou can kick the following players:\n");
                    for (CustomPlayer other : playerTeam.players) {
                        if (other.player == player)
                            continue;
                        message.append("[gold] - [accent]ID: [scarlet]").append(other.player.id)
                                .append("[accent]: [white]").append(other.player.plainName()).append("\n");
                    }
                    message.append("\n\nYou must specify a player [blue]name/id[accent]: [scarlet]/teamkick [blue]44");

                    player.sendMessage(message.toString());
                });

        handler.<Player>register("teamleave", "Leave your current team", (args, player) -> {
            if (player.team() == Team.blue || player.team() == Team.malis) {
                player.sendMessage(("[accent]Can only leave team if you are survivor!"));
                return;
            }

            CustomPlayer plaguePlayer = base.uuidMapping.get(player.uuid());

            if (!pregame) {
                infect(plaguePlayer, true);
                return;
            }

            PlagueTeam playerTeam = teams.get(plaguePlayer.team);

            plaguePlayer.team = Team.blue;
            plaguePlayer.player.team(Team.blue);
            plaguePlayer.player.sendMessage(("[accent]You have left the team and are blacklisted!"));
            playerTeam.blacklistedPlayers.add(player.uuid());
            playerTeam.removePlayer(plaguePlayer);
            updatePlayer(plaguePlayer.player);
            if (playerTeam.players.size() == 0) {
                playerTeam.locked = true;
                killTiles(playerTeam.team);
                return;
            }

            if (playerTeam.leader.player.uuid().equals(player.uuid())) {
                playerTeam.leader = playerTeam.players.get(0);
                playerTeam.leader.player
                        .sendMessage("[accent]The previous team leader left making you the new leader!");
            }

        });
    }

    /**
     * checks if a tile is build-upon-able
     * https://github.com/Anuken/Mindustry/blob/d09f4c0db564137ccf71774abbdef25335e64168/core/src/mindustry/world/Build.java#LL181C1-L207C1
     */
    boolean canPlace(Block type, Tile tile) {
        int offsetx = -(type.size - 1) / 2;
        int offsety = -(type.size - 1) / 2;

        for (int dx = 0; dx < type.size; dx++) {
            for (int dy = 0; dy < type.size; dy++) {
                int wx = dx + offsetx + tile.x, wy = dy + offsety + tile.y;

                Tile check = world.tile(wx, wy);
                // @formatter:off
                if (
                    // nothing there
                    check == null
                    // deep water
                    || check.floor().isDeep()
                    // same block, same rotation
                    || (type == check.block() && check.build != null && type.rotate)
                    // solid wall
                    || !check.floor().placeableOn
                )
                    return false;
                // @formatter:on
            }
        }
        return true;
    }

    void initRules() {
        rules = new Rules();
        rules.enemyCoreBuildRadius = 75 * 8;
        rules.canGameOver = false;
        // rules.playerDamageMultiplier = 0;
        rules.buildSpeedMultiplier = 4;
        rules.coreIncinerates = true;

        UnitTypes.alpha.weapons = new Seq<>();
        UnitTypes.beta.weapons = new Seq<>();
        UnitTypes.gamma.weapons = new Seq<>();
        UnitTypes.poly.weapons = new Seq<>();
        UnitTypes.mega.weapons = new Seq<>();
        UnitTypes.flare.weapons = new Seq<>();

        for (UnitType u : Vars.content.units()) {
            u.crashDamageMultiplier = 0f;
            u.payloadCapacity = 0f;
        }

        rules.unitCapVariable = false;
        rules.unitCap = 48;
        rules.fire = false;
        rules.modeName = "Plague";

        for (UnitType u : Vars.content.units()) {
            originalUnitHealth.put(u, u.health);
        }

        ((ItemTurret) Blocks.foreshadow).ammoTypes.get(Items.surgeAlloy).buildingDamageMultiplier = 0;
        ((PowerTurret) Blocks.malign).shootType.buildingDamageMultiplier = 0;

        for (int i = 0; i < maps.customMaps().size; i++) {
            rotation.add(i);
        }
        Collections.shuffle(rotation);
    }

    void resetRules() {
        UnitTypes.poly.weapons = new Seq<>();
        UnitTypes.mega.weapons = new Seq<>();
        UnitTypes.quad.weapons = new Seq<>();

        for (UnitType u : Vars.content.units()) {
            if (u != UnitTypes.alpha && u != UnitTypes.beta && u != UnitTypes.gamma) {
                u.health = originalUnitHealth.get(u);
            }
        }

        state.rules.unitDamageMultiplier = 1;
    }

    String leaderboard(boolean plague) {
        ResultSet rs;
        if (plague)
            rs = db.customQuery("select * from mindustry_data order by plagueXp desc limit 5");
        else
            rs = db.customQuery("select * from mindustry_data order by survXp desc limit 5");
        String s = "[accent]Leaderboard:\n";
        try {
            int i = 0;
            while (rs.next()) {
                i++;
                String rank;
                if (plague)
                    rank = CustomPlayer.plagueRank(rs.getInt("plagueXp")) + "[scarlet] ";
                else
                    rank = CustomPlayer.survRank(rs.getInt("survXp")) + "[olive] ";
                s += "\n[gold]" + i + "[white]: " + rank + rs.getString("latestName") + "[accent]: [gold]"
                        + rs.getInt("wins") + "[accent] wins";
            }
            rs.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return s;
    }

    void infect(CustomPlayer cPly, boolean remove) {
        if (cPly.player.team() != Team.blue && remove) {
            PlagueTeam cTeam = teams.get(cPly.player.team());
            if (cTeam.players.size() <= 1)
                killTiles(cPly.player.team());
            cTeam.removePlayer(cPly);
        }
        Call.sendMessage("[accent]" + cPly.player.name + "[white] was [red]infected[white]!");
        teams.get(Team.malis).addPlayer(cPly);

        if (cPly.connected) {
            cPly.player.team(Team.malis);
            cPly.player.clearUnit();
            updatePlayer(cPly.player);
        }

    }

    void killTiles(Team team) {
        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.height(); y++) {
                Tile tile = world.tile(x, y);
                if (tile.build != null && tile.team() == team) {
                    Time.run(Mathf.random(60f * 6), tile.build::kill);
                }
            }
        }
        for (Unit u : Groups.unit) {
            if (u.team == team) {
                u.kill();
            }
        }
    }

    /**
     * Loads the mindustry player into a {@link CustomPlayer} and
     * maps the uuid in the `base.uuidMapping`.
     */
    private void loadPlayer(Player player) {
        CustomPlayer cPly = base.uuidMapping.get(player.uuid());
        cPly.player = player;
        cPly.team = cPly.player.team();

        try {
            if (!teams.get(cPly.team).hasPlayer(cPly)) {
                teams.get(cPly.team).addPlayer(cPly);
            }
        } catch (NullPointerException e) {
            Log.err("Teams null pointer exception.\n" +
                    "Player team: " + player.team() + "\n" +
                    "Custom Player team: " + cPly.team + "\n" +
                    "Error: " + e);
        }

        updatePlayer(player);

        cPly.connected = true;
        if (player.team() == Team.blue)
            CoreBlock.playerSpawn(Team.malis.cores().random().tile, player);
        else if (player.team() == Team.malis)
            player.sendMessage(plagueLb);
        else
            player.sendMessage(survLb);

        if (cPly.playTime < 60)
            Call.infoMessage(player.con, info);

        // Spawn their starter units

        spawnPlayerUnits(cPly, player);
    }

    private void spawnPlayerUnits(CustomPlayer cPly, Player ply) {
        for (Unit u : cPly.followers) {
            u.kill();
            u.health = 0;
        }
        cPly.followers.clear();
        if (ply.team() == Team.blue) {
            return;
        }
        int count = ply.team() == Team.malis ? 1 : 4;
        for (int i = 0; i < count; i++) {
            Unit u = UnitTypes.poly.create(ply.team());
            u.set(ply.getX(), ply.getY());
            u.add();
            cPly.followers.add(u);
        }
        if (ply.team() != Team.malis) {
            Unit u = UnitTypes.mega.create(ply.team());
            u.set(ply.getX(), ply.getY());
            u.add();
            cPly.followers.add(u);
        }

    }

    void updateBanned(Player ply, ObjectSet<Block> banned) {
        Rules tempRules = rules.copy();
        tempRules.bannedBlocks = banned;
        for (int i = 0; i < 5; i++) { // Just making sure the packet gets there
            Call.setRules(ply.con, tempRules);
        }
    }

    private void updatePlayer(Player ply) {
        if (ply.team() == Team.malis) {
            updateBanned(ply, hasWon ? PlagueData.plagueBanned : PlagueData.plagueBannedPreWin);
        } else if (ply.team() != Team.blue) {
            updateBanned(ply, PlagueData.survivorBanned);
        }

        CustomPlayer cPly = base.uuidMapping.get(ply.uuid());
        // Update follower units violently
        spawnPlayerUnits(cPly, ply);
        cPly.updateName();
    }

    void endgame() {
        endgame(new Seq<>(), new String[] {});
    }

    void endgame(Seq<CustomPlayer> winners) {
        endgame(winners, new String[] {});
    }

    void endgame(Seq<CustomPlayer> winners, String[] map) {
        base.on_end();
        gameover = true;

        String[] keys = new String[] { "gamemode", "mapID" };
        Object[] vals = new Object[] { "plague", state.map.file.name() };
        HashMap<String, Object> entries = db.loadRow("mindustry_map_data", keys, vals);
        long timeNow = base.seconds;

        for (CustomPlayer cPly : teams.get(Team.malis).players) {
            if (!winners.contains(cPly)) {
                Call.infoMessage(cPly.player.con,
                        "[accent]Game over!\nAll survivors have been infected. Loading new map...");
            }
        }
        long plays = (int) entries.get("plays");
        long avgSurvived = (int) entries.get("avgSurvived");
        if (timeNow > 60 * 5) {
            plays++;
            avgSurvived = (avgSurvived * (plays - 1) + base.seconds) / plays;
        }

        long survivorRecord = (int) entries.get("survivorRecord");
        if (newRecord) {
            survivorRecord = base.seconds;
        }

        for (Player player : Groups.player) {
            base.savePlayerData(player.uuid());
        }

        long finalSurvivorRecord = survivorRecord;
        long finalAvgSurvived = avgSurvived;
        long finalPlays = plays;
        Time.runTask(60f * 5f, () -> {
            db.saveRow("mindustry_map_data", keys, vals,
                    new String[] { "survivorRecord", "avgSurvived", "plays" },
                    new Object[] { finalSurvivorRecord, finalAvgSurvived, finalPlays });
            mapReset(map);
        });
    }

    void mapReset() {
        mapReset(new String[] {});
    }

    void mapReset(String[] args) {
        resetting = true;

        teams = new HashMap<>();

        teamsCount = 0;

        multiplier = 1f;

        base.seconds = 0;
        startTime = System.currentTimeMillis();

        newRecord = false;

        counts = 0;
        pregame = true;
        gameover = false;
        hasWon = false;

        resetRules();

        survLb = leaderboard(false);
        plagueLb = leaderboard(true);

        corePlaceInterval.reset();
        tenMinInterval.reset();

        // Load new map:
        loadMap(args);
        resetting = false;
    }

    void loadMap(String args[]) {
        if (args.length > 0) {
            loadMap(Integer.parseInt(args[0]));
        } else {
            if (firstRun == true) {
                mapIndex = new Random().nextInt();
            }
            mapIndex = ((mapIndex > 0 ? mapIndex : -mapIndex) + 1) % maps.customMaps().size;
            loadMap(mapIndex);
        }
    }

    /**
     * overloading my beloved
     * 
     * @param map the map index out of the current maps, view with `listmaps`
     */
    void loadMap(int map) {
        loadMap(maps.customMaps().get(map));
    }

    void loadMap(mindustry.maps.Map map) {
        Seq<Player> players = new Seq<>();
        for (Player p : Groups.player) {
            if (p.isLocal())
                continue;

            players.add(p);
            p.clearUnit();
        }

        logic.reset();
        Log.info("Loading map " + map.name());

        world.loadMap(map);

        // Make cores and power source indestructible
        Team.malis.cores().each(coreBuild -> {
            coreBuild.health(Float.MAX_VALUE);
            coreBuild.items.clear();
        });
        isSerpulo = PlagueData.serpuloCores.contains(Team.malis.cores().get(0).block);
        world.tiles.forEach(t -> {
            if (t.build != null && t.build.block.equals(Blocks.powerSource) && t.build.team() == Team.malis)
                t.build.health = Float.MAX_VALUE;
        });
        world.beginMapLoad();
        PlagueGenerator.defaultOres(world.tiles, isSerpulo);

        world.endMapLoad();
        rules.hiddenBuildItems = (isSerpulo ? Items.erekirOnlyItems : PlagueData.serpuloOnlyItems).asSet();
        rules.bannedBlocks = map.rules().bannedBlocks;
        rules.bannedUnits = map.rules().bannedUnits;
        rules.unitWhitelist = map.rules().unitWhitelist;
        rules.weather = map.rules().weather;
        rules.lighting = map.rules().lighting;
        rules.ambientLight = map.rules().ambientLight;
        rules.hideBannedBlocks = true;
        state.rules = rules.copy();

        if (firstRun) {
            Log.info("Server not up, starting server...");
            netServer.openServer();
            firstRun = false;
        }

        String[] keys = new String[] { "gamemode", "mapID" };
        Object[] vals = new Object[] { "plague", state.map.file.name() };
        if (!db.hasRow("mindustry_map_data", keys, vals)) {
            db.addEmptyRow("mindustry_map_data", keys, vals);
        }
        HashMap<String, Object> entries = db.loadRow("mindustry_map_data", keys, vals);
        mapRecord = (int) entries.get("survivorRecord"); // Get map record
        avgSurvived = (int) entries.get("avgSurvived"); // Get average time survived
        mapPlays = (int) entries.get("plays"); // Get number of map plays

        teams.put(Team.malis, new PlagueTeam(Team.malis));
        teams.put(Team.blue, new PlagueTeam(Team.blue));
        state.set(State.playing);
        Events.fire(new EventType.PlayEvent());
        for (Player player : players) {
            Call.worldDataBegin(player.con);
            netServer.sendWorldData(player);
            base.uuidMapping.get(player.uuid()).reset();

            loadPlayer(player);
        }
    }
}
