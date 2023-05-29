package main;

import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.units.Reconstructor;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;

import static mindustry.Vars.*;

public class PlagueMain extends Plugin {

    private boolean firstRun = true;
    private boolean resetting = false;

    private int teamsCount;

    private final Rules rules = new Rules();

    private Seq<Weapon> polyWeapons;
    private Seq<Weapon> megaWeapon;
    private Seq<Weapon> quadWeapon;
    private Seq<Weapon> octWeapon;

    private final HashMap<UnitType, Float> originalUnitHealth = new HashMap<>();

    private Seq<UnitType[]> additiveFlare;
    private Seq<UnitType[]> additiveNoFlare;

    private final HashMap<String, CustomPlayer> uuidMapping = new HashMap<>();;
    private HashMap<Team, PlagueTeam> teams;

    private final int pretime = 6;

    private final RTInterval corePlaceInterval = new RTInterval(20);
    private final RTInterval tenMinInterval = new RTInterval(60 * 10);
    private final RTInterval oneMinInterval = new RTInterval(60);

    private float multiplier;
    private static final DecimalFormat df = new DecimalFormat("0.00");

    private final int winTime = 45; // In minutes

    private float realTime = 0f;
    private long seconds;
    private static long startTime;
    private boolean newRecord;

    private final int[] plagueCore = new int[2];

    private final ArrayList<Integer> rotation = new ArrayList<Integer>();
    private int mapIndex = 0;
    private String mapID = "0";

    private int mapRecord;
    private int avgSurvived;
    private int mapPlays;

    private String leaderboardString;

    private final DBInterface db = new DBInterface();

    private boolean isSerpulo = true;

    private boolean pregame;
    private boolean gameover;
    private boolean hasWon;

    public int counts;

    String info = "[#4d004d]{[purple]AA[#4d004d]} [olive]Plague[accent] is a survival game mode with two teams," +
            " the [scarlet]Plague [accent]and [green]Survivors[accent].\n\n" +
            "The [scarlet]Plague[accent] build up their economy to make the biggest army possible, and try to" +
            " break through the [green]Survivors[accent] defenses.\n\n" +
            "The [green]Survivors[accent] build up a huge defense and last 45 minutes to win.\n\n" +
            "To become a " +
            "[green]Survivor[accent], you must place a core in the first 2 minutes of the game, where you are " +
            "allowed to choose your team. Place any block to place a core at the start of the game.\n\n" +
            "Air units do no damage before 45 minutes";

    @Override
    public void init() {
        /*
         * CREATE TABLE `bans` (
         * `ip` varchar(40) NOT NULL,
         * `uuid` varchar(40) NOT NULL,
         * `bannedName` varchar(200) DEFAULT NULL,
         * `banPeriod` int DEFAULT NULL,
         * `banReason` varchar(200) DEFAULT NULL,
         * `banJS` varchar(300) DEFAULT NULL,
         * PRIMARY KEY (`ip`,`uuid`)
         * );
         * CREATE TABLE `data` (
         * `userID` int NOT NULL AUTO_INCREMENT,
         * `username` varchar(20) DEFAULT NULL,
         * `password` varchar(20) DEFAULT NULL,
         * PRIMARY KEY (`userID`)
         * );
         * CREATE TABLE `mindustry_data` (
         * `uuid` varchar(40) NOT NULL,
         * `playTime` int DEFAULT '0',
         * `userID` int DEFAULT NULL,
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

        initRules();

        netServer.assigner = (player, players) -> {
            if (uuidMapping.containsKey(player.uuid())) {
                Team team = uuidMapping.get(player.uuid()).team;
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
            if (action.player != null && action.tile != null) {
                if (cartesianDistance(action.tile.x, action.tile.y,
                        plagueCore[0], plagueCore[1]) < world.height() / 2.8) {
                    if ((action.player.team() != Team.malis
                            && (action.block == Blocks.vault || action.block == Blocks.reinforcedVault))
                            || action.player.team() == Team.blue) {
                        action.player.sendMessage("[scarlet]Cannot place core/vault that close to plague!");
                        return false;
                    }
                }
                if (action.tile.block() == Blocks.powerSource) {
                    return false;
                }

                if (action.block != null && PlagueData.survivorBanned.contains(action.block)
                        && action.player.team() != Team.malis && action.player.team() != Team.blue) {
                    return false;
                }
                return action.block == null ||
                        ((!PlagueData.plagueBanned.contains(action.block) || !hasWon)
                                &&
                                (!PlagueData.plagueBannedPreWin.contains(action.block) || hasWon))
                        || action.player.team() != Team.malis;

            } // TODO readability

            return true;
        });

        Events.run(EventType.Trigger.update, () -> {
            if (resetting || firstRun)
                return;
            // Spawn player in core if they aren't
            if (pregame) {
                for (Player player : Groups.player) {
                    if (player.dead()) {
                        CoreBlock.playerSpawn(world.tile(plagueCore[0], plagueCore[1]), player);
                    }
                }
            }
            // Notification about placing a core, then starting game
            if (counts < pretime && corePlaceInterval.get(seconds)) {

                counts++;
                if (counts == pretime) {
                    pregame = false;
                    for (Player ply : Groups.player) {
                        if (ply.team() == Team.blue) {
                            infect(uuidMapping.get(ply.uuid()), true);
                            updatePlayer(ply);
                        }
                    }

                    teams.remove(Team.blue);

                    if (teams.size() == 1) {
                        Log.info("No survs endgame, Count: " + counts);
                        endgame(new Seq<>());
                    } else {
                        Call.sendMessage(
                                "[accent]The game has started! [green]Survivors[accent] must survive for [gold]" +
                                        winTime + "[accent] minutes to win!");
                    }

                } else {
                    Call.sendMessage("[accent]You have [scarlet]" + (pretime * 20 - counts * 20) +
                            " [accent]seconds left to place a core. Place any block to place a core.");
                }

            }

            realTime = System.currentTimeMillis() - startTime;
            seconds = (int) (realTime / 1000);

            // Runs if survivors hit win condition
            if (!gameover && !hasWon && seconds > winTime * 60) {
                hasWon = true;
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
                    }

                    updatePlayer(player);

                });

                Call.sendMessage("[scarlet]The plague can now build and attack with air units!");
                // BUFF DA PLAGUE (enable air)

                // So survivor megas can't do damage
                UnitTypes.poly.weapons = polyWeapons;
                UnitTypes.mega.weapons = megaWeapon;
                UnitTypes.quad.weapons = quadWeapon;
                UnitTypes.oct.weapons = octWeapon;

                ((Reconstructor) Blocks.additiveReconstructor).upgrades = additiveFlare;

            }

            if (!gameover && !newRecord && seconds > mapRecord) {
                newRecord = true;
                Call.sendMessage("[gold]New record![accent] Old record of[gold] "
                        + mapRecord / 60 + "[accent] minutes and [gold]" + mapRecord % 60 + "[accent] seconds"
                        + "[accent] was beaten!");
            }

            if (tenMinInterval.get(seconds)) {
                float multiplyBy = hasWon ? 1.4f : 1.2f;
                multiplier *= multiplyBy;
                state.rules.unitDamageMultiplier = multiplier;

                for (UnitType u : Vars.content.units()) {
                    if (u != UnitTypes.alpha && u != UnitTypes.beta && u != UnitTypes.gamma) {
                        u.health = originalUnitHealth.get(u) * multiplier;
                    }
                }
                String percent = "" + Math.round((multiplyBy - 1) * 100);
                Call.sendMessage("[accent]Units now deal [scarlet]" + percent + "%[accent] more damage and have " +
                        "[scarlet]" + percent + "%[accent] more health " +
                        "for a total multiplier of [scarlet]" + df.format(multiplier) + "x");
            }

            if (oneMinInterval.get(seconds)) {
                Groups.player.each((player) -> {
                    uuidMapping.get(player.uuid()).playTime += 1;
                });
            }
        });

        Events.on(EventType.UnitControlEvent.class, event -> {
            if (Arrays.asList(UnitTypes.toxopid,
                    UnitTypes.eclipse,
                    UnitTypes.corvus,
                    UnitTypes.oct,
                    UnitTypes.reign,
                    UnitTypes.omura).contains(event.unit.type)) {
                CustomPlayer cPly = uuidMapping.get(event.player.uuid());

                if (cPly.playTime < 600) {
                    event.player.sendMessage("[accent]You need at least [scarlet]600[accent] minutes of playtime " +
                            "before you can control a T5!");
                    return;
                }

                if (seconds < cPly.bannedT5) {
                    event.player.clearUnit();
                    event.player.sendMessage("[accent]You killed a T5 too fast recently! " +
                            "You are banned from controlling T5 units for [scarlet]" + (cPly.bannedT5 - seconds) +
                            "[accent] more seconds!");
                    return;
                }
                cPly.controlledT5 = seconds;
            }
        });

        Events.on(EventType.UnitDestroyEvent.class, event -> {
            if (Arrays.asList(UnitTypes.toxopid,
                    UnitTypes.eclipse,
                    UnitTypes.corvus,
                    UnitTypes.oct,
                    UnitTypes.reign,
                    UnitTypes.omura).contains(event.unit.type) &&
                    event.unit.isPlayer()) {
                Player ply = event.unit.getPlayer();
                CustomPlayer cPly = uuidMapping.get(ply.uuid());

                long diff = seconds - cPly.controlledT5;
                if (diff > 10 && diff < 240) {
                    ply.sendMessage(
                            "[scarlet]You killed the T5 too quickly! You are banned from controlling T5's for 5 minutes!");
                    cPly.bannedT5 = (int) seconds + 3 * 5;
                }
            }
        });

        Events.on(EventType.UnitCreateEvent.class, event -> {

            if (event.unit.type == UnitTypes.horizon && event.unit.team != Team.malis) {
                // Let players know they can't build this unit
                Call.label("Survivors can't build horizons!",
                        5f, event.spawner.tileX() * 8, event.spawner.tileY() * 8);
                event.unit.health = 0;
                event.unit.dead = true;
            }
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            loadPlayer(event.player);
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            savePlayerData(event.player.uuid());
            CustomPlayer cPly = uuidMapping.get(event.player.uuid());
            cPly.connected = false;
        });

        Events.on(EventType.BuildSelectEvent.class, event -> {
            Player player = event.builder.getPlayer();
            if (player == null)
                return;

            if (event.team == Team.blue) {
                event.tile.removeNet();
                if (Build.validPlace(isSerpulo ? Blocks.spectre : Blocks.malign, event.team, event.tile.x, event.tile.y,
                        0) && !event.breaking) {
                    Team chosenTeam = null;
                    for (Teams.TeamData t : state.teams.getActive()) {
                        if (t.team != Team.malis) {
                            for (CoreBlock.CoreBuild core : t.cores) {
                                if (cartesianDistance(event.tile.x, event.tile.y, core.tile.x, core.tile.y) < 150) {
                                    chosenTeam = t.team;
                                    if (teams.get(chosenTeam).locked) {
                                        player.sendMessage("[accent]This team is locked, you cannot join it!");
                                        return;
                                    } else if (teams.get(chosenTeam).blacklistedPlayers.contains(player.uuid())) {
                                        player.sendMessage("[accent]You have been blacklisted from this team!");
                                        return;
                                    }
                                    break;
                                }
                            }
                            if (chosenTeam != null) {
                                break;
                            }
                        }
                    }

                    if (chosenTeam == null) {
                        teamsCount++;
                        chosenTeam = Team.all[teamsCount + 6];
                        teams.put(chosenTeam, new PlagueTeam(chosenTeam, uuidMapping.get(player.uuid())));
                    }

                    teams.get(chosenTeam).addPlayer(uuidMapping.get(player.uuid()));

                    player.team(chosenTeam);
                    uuidMapping.get(player.uuid()).team = chosenTeam;
                    updatePlayer(player);

                    event.tile.setNet(isSerpulo ? Blocks.coreFoundation : Blocks.coreCitadel, chosenTeam, 0);
                    state.teams.registerCore((CoreBlock.CoreBuild) event.tile.build);
                    if (state.teams.cores(chosenTeam).size == 1) {
                        for (ItemStack stack : isSerpulo ? PlagueData.survivorLoadoutSerpulo
                                : PlagueData.survivorLoadoutErekir) {
                            // Call.transferItemTo(stack.item, stack.amount, event.tile.drawx(),
                            // event.tile.drawy(), event.tile);
                            Call.setItem(event.tile.build, stack.item, stack.amount);
                        }
                    }

                    /*
                     * player.setDead(true);
                     * player.onRespawn(state.teams.cores(chosenTeam).get(0).tile);
                     */

                }
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            if (event.tile.block() instanceof CoreBlock && event.tile.team().cores().size == 1) {
                Team deadTeam = event.tile.team();
                Seq<CustomPlayer> winners = new Seq<CustomPlayer>();
                Log.info("Dead team to infect: " + deadTeam);
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
                    }
                    infect(cPly, false);
                }

                killTiles(deadTeam);

                teams.remove(deadTeam);
                if (teams.size() == 1) {
                    Log.info("Dead block endgame");
                    endgame(winners);
                }
            }
        });

        Events.on(EventType.TapEvent.class, event -> {
            if (event.tile.team() != Team.malis && event.player.team() == event.tile.team()) {
                if (event.tile.block() == Blocks.vault && event.tile.build.items.has(Items.thorium, 997)) {
                    event.tile.build.tile.setNet(Blocks.coreShard, event.tile.team(), 0);
                }
                if (event.tile.block() == Blocks.reinforcedVault && event.tile.build.items.has(Items.thorium, 897)) {
                    event.tile.build.tile.setNet(Blocks.coreBastion, event.tile.team(), 0);
                }
            }
        });

        // Events.on(EventType.NewName.class, event -> {
        // Player ply = uuidMapping.get(event.uuid).player;
        // CustomPlayer cPly = uuidMapping.get(event.uuid);
        // cPly.rawName = ply.name;
        // ply.name = StringHandler.determinePrestige(cPly.prestige) +
        // StringHandler.determineRank(cPly.xp) + "\u00A0"
        // + ply.name;
        // });

        // Events.on(EventType.HudToggle.class, event -> {
        // CustomPlayer cPly = uuidMapping.get(event.uuid);
        // cPly.hudEnabled = event.enabled;
        // });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("plague", "[map(index)]", "Host the plague game mode", args -> {
            if (!Vars.state.is(GameState.State.menu)) {
                Log.err("Stop the server first.");
                return;
            }

            mapReset(args);

        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("endplague", "[scarlet]Ends the plague game (admin only)", (args, player) -> {
            if (!player.admin) {
                player.sendMessage("[accent]Admin only!");
                return;
            }
            Call.sendMessage("[scarlet]" + player.name + " [accent]has ended the plague game. Ending in 10 seconds...");
            endgame(new Seq<>());
        });

        // destroy a building, must:
        // either: be admin
        // or: be team leader and destroy building of your team
        handler.<Player>register("destroy", "<x> <y>", "Destroy a building", (args, player) -> {
            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            Building build = world.build(x, y);
            if (build == null) {
                player.sendMessage(String.format("[accent]No building at (%d, %d)", x, y));
                return;
            }
            // i would structure this admin || all checks but i want the error handling
            if (player.admin) { // be admin (skip checks)
                build.damageContinuous(Float.MAX_VALUE);
                return;
            }
            if (build.team != player.team()) { // of same team
                player.sendMessage("[accent]Can't break block of other team");
                return;
            }
            CustomPlayer cPly = uuidMapping.get(player.uuid());
            PlagueTeam pTeam = teams.get(cPly.team);
            if (!pTeam.leader.player.uuid().equals(cPly.player.uuid())) { // be team leader
                player.sendMessage("[accent]You must be team leader to destroy a block!");
                return;
            }
            build.damageContinuous(Float.MAX_VALUE);
        });

        handler.<Player>register("infect", "Infect yourself", (args, player) -> {
            if (player.team() == Team.malis) {
                player.sendMessage("[accent]Already infected!");
                return;
            }
            CustomPlayer cPly = uuidMapping.get(player.uuid());
            infect(cPly, true);
        });

        handler.<Player>register("stats", "Display stats about the current map", (args, player) -> {
            String s = "[accent]Map stats for: [white]" + state.map.name() + "\n" +
                    "[accent]Author: [white]" + state.map.author() + "\n" +
                    "[accent]Plays: [scarlet]" + mapPlays + "\n" +
                    "[accent]Average time survived: [scarlet]" + avgSurvived / 60 + "[accent] minutes and [scarlet]"
                    + avgSurvived % 60 + "[accent] seconds.\n" +
                    "[accent]Suvivor record: [scarlet]" + mapRecord / 60 + "[accent] minutes and [scarlet]"
                    + mapRecord % 60 + "[accent] seconds.";
            player.sendMessage(s);

        });

        handler.<Player>register("playtime", "How long you have played", (args, player) -> {
            Duration dur = Duration.ofMinutes(uuidMapping.get(player.uuid()).playTime);
            long hours = dur.toHours();
            long mins = dur.toMinutesPart();
            if (hours == 0) {
                player.sendMessage(
                        String.format("[accent]Playtime: [scarlet]%2d [accent]minute", mins) + (mins != 1 ? "s" : ""));
                return;
            }
            player.sendMessage(
                    String.format("[accent]Playtime: [scarlet]%d [accent]hour%s [scarlet]%2d [accent]minute%s",
                            hours, hours != 1 ? "s" : "", mins, mins != 1 ? "s" : ""));
        });

        handler.<Player>register("time", "Display the time now", (args, player) -> {
            Duration dur = Duration.ofSeconds(seconds);
            long hours = dur.toHours();
            int mins = dur.toMinutesPart();
            int seconds = dur.toSecondsPart();

            String t = "[accent]Time: ";
            if (hours != 0) {
                t += String.format("[scarlet]%d[accent]:", hours);
            }
            if (mins != 0) {
                t += String.format("[scarlet]%d[accent]:", mins);
            }
            player.sendMessage(t + String.format("[scarlet]%d", seconds));
        });

        handler.<Player>register("leaderboard", "Display the leaderboard", (args, player) -> {
            player.sendMessage(leaderboardString);
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

                    CustomPlayer cPly = uuidMapping.get(player.uuid());
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

                    // Same as the variable player, but converted into a CustomPlayer for additional plague features.
                    CustomPlayer plaguePlayer = uuidMapping.get(player.uuid());
                    PlagueTeam playerTeam = teams.get(plaguePlayer.team);

                    // Checks if caller is the leader, exits if not (insufficient permission)
                    if (!Objects.equals(playerTeam.leader.player.uuid(), plaguePlayer.player.uuid())) {
                        player.sendMessage("[accent]You must be team leader to kick players!");
                        return;
                    }

                    if (args.length != 0) {
                        for (CustomPlayer teammate : playerTeam.players) {
                            if (String.valueOf( teammate.player.id() ).equals(args[0]) ||
                                    teammate.player.name.equalsIgnoreCase(args[0]) ||
                                    teammate.rawName.equalsIgnoreCase(args[0])) {
                                if (teammate.player == player)
                                    continue;

                                Team teamToSet = pregame ? Team.blue : Team.malis;

                                teammate.team = teamToSet;
                                teammate.player.team(teamToSet);
                                teammate.player.sendMessage(("[accent]You have been kicked from the team!"));
                                playerTeam.blacklistedPlayers.add(teammate.player.uuid());
                                playerTeam.removePlayer(teammate);
                                updatePlayer(teammate.player);
                                return;
                            }
                        }
                    }

                    StringBuilder message = new StringBuilder("[accent]Invalid syntax!\n\nYou can kick the following players:\n");
                    for (CustomPlayer other : playerTeam.players) {
                        if (other.player == player)
                            continue;
                        message.append("[gold] - [accent]ID: [scarlet]" + other.player.id + "[accent]: [white]" + other.rawName + "\n");
                    }
                    message.append("\n\nYou must specify a player [blue]name/id[accent]: [scarlet]/teamkick [blue]44");

                    player.sendMessage(message.toString());
                    return;

                });

        handler.<Player>register("teamleave", "Leave your current team", (args, player) -> {
            if (player.team() == Team.blue || player.team() == Team.malis) {
                player.sendMessage(("[accent]Can only leave team if you are survivor!"));
                return;
            }

            CustomPlayer plaguePlayer = uuidMapping.get(player.uuid());

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
                playerTeam.leader.player.sendMessage("[accent]The previous team leader left making you the new leader!");
            }

        });
    }

    void initRules() {
        rules.canGameOver = false;
        // rules.playerDamageMultiplier = 0;
        rules.buildSpeedMultiplier = 4;
        rules.coreIncinerates = true;

        UnitTypes.alpha.weapons = new Seq<>();
        UnitTypes.beta.weapons = new Seq<>();
        UnitTypes.gamma.weapons = new Seq<>();

        /*
         * UnitTypes.alpha.health = 1f;
         * UnitTypes.beta.health = 1f;
         * UnitTypes.gamma.health = 1f;
         */

        polyWeapons = UnitTypes.poly.weapons.copy();
        megaWeapon = UnitTypes.mega.weapons.copy();
        quadWeapon = UnitTypes.quad.weapons.copy();
        octWeapon = UnitTypes.oct.weapons.copy();

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

        additiveFlare = ((Reconstructor) Blocks.additiveReconstructor).upgrades.copy();
        ((Reconstructor) Blocks.additiveReconstructor).upgrades.remove(3);

        additiveNoFlare = ((Reconstructor) Blocks.additiveReconstructor).upgrades.copy();

        ((ItemTurret) Blocks.foreshadow).ammoTypes.get(Items.surgeAlloy).buildingDamageMultiplier = 0;

        for (int i = 0; i < maps.customMaps().size; i++) {
            rotation.add(i);
        }
        Collections.shuffle(rotation);
    }

    void resetRules() {

        UnitTypes.poly.weapons = new Seq<>();
        UnitTypes.mega.weapons = new Seq<>();
        UnitTypes.quad.weapons = new Seq<>();
        UnitTypes.oct.weapons = new Seq<>();

        ((Reconstructor) Blocks.additiveReconstructor).upgrades = additiveNoFlare;

        for (UnitType u : Vars.content.units()) {
            if (u != UnitTypes.alpha && u != UnitTypes.beta && u != UnitTypes.gamma) {
                u.health = originalUnitHealth.get(u);
            }
        }

        state.rules.unitDamageMultiplier = 1;

    }

    String _leaderboardInit(int limit) {
        return "[gold]no leaderboard cause i dont like the win based lb[white]";
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
     * maps the uuid in the `uuidMapping`.
     */
    private void loadPlayer(Player player) {
        if (!db.hasRow("mindustry_data", "uuid", player.uuid())) {
            Log.info("New uuid: " + player.uuid() + ", adding to local tables...");
            db.addEmptyRow("mindustry_data", "uuid", player.uuid());
        }

        if (!uuidMapping.containsKey(player.uuid())) {
            uuidMapping.put(player.uuid(), new CustomPlayer(player));
        }
        CustomPlayer cPly = uuidMapping.get(player.uuid());
        cPly.player = player;
        cPly.team = cPly.player.team();
        cPly.rawName = player.name;

        String[] keys = new String[] { "uuid" };
        Object[] vals = new Object[] { player.uuid() };
        HashMap<String, Object> row = db.loadRow("mindustry_data", keys, vals);
        cPly.playTime = (int) row.get("playTime");

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

        if (player.team() == Team.blue) {
            CoreBlock.playerSpawn(world.tile(plagueCore[0], plagueCore[1]), player);
        }

        player.sendMessage(leaderboardString);

        if (cPly.playTime < 60) {
            Call.infoMessage(player.con, info);
        }

        // Spawn their starter units

        spawnPlayerUnits(cPly, player);
    }

    private void spawnPlayerUnits(CustomPlayer cPly, Player ply) {
        for (Unit u : cPly.followers) {
            u.kill();
            u.health = 0;
        }
        cPly.followers.clear();
        Log.info(ply.team());
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

    private void updatePlayer(Player ply) {
        if (ply.team() == Team.malis) {
            Rules tempRules = rules.copy();
            tempRules.bannedBlocks = hasWon ? PlagueData.plagueBanned : PlagueData.plagueBannedPreWin;
            for (int i = 0; i < 5; i++) { // Just making sure the packet gets there
                Call.setRules(ply.con, tempRules);
            }
        } else if (ply.team() != Team.blue) {
            Rules tempRules = rules.copy();
            tempRules.bannedBlocks = PlagueData.survivorBanned;
            for (int i = 0; i < 5; i++) { // Just making sure the packet gets there
                Call.setRules(ply.con, tempRules);
            }
        }

        CustomPlayer cPly = uuidMapping.get(ply.uuid());
        // Update follower units violently
        spawnPlayerUnits(cPly, ply);
        cPly.updateName();
    }

    private float cartesianDistance(float x, float y, float cx, float cy) {
        return (float) Math.sqrt(Math.pow(x - cx, 2) + Math.pow(y - cy, 2));
    }

    // void showHud(Player ply) {
    // CustomPlayer cPly = uuidMapping.get(ply.uuid());
    // String s = "[accent]Time survived: [orange]" + seconds / 60 + "[accent]
    // mins.\n" +
    // "All-time record: [gold]" + mapRecord / 60 + "[accent] mins.\n" +
    // "Monthly wins: [gold]" + cPly.monthWins + "\n";
    // s += "\n\n[accent]Disable hud with [scarlet]/hud";
    // Call.infoPopup(ply.con, s,
    // 60, 10, 120, 0, 140, 0);
    // }

    void savePlayerData(String uuid) {
        if (!uuidMapping.containsKey(uuid)) {
            Log.warn("uuid mapping does not contain uuid " + uuid + "! Not saving data!");
            return;
        }
        Log.info("PLAGUE: Saving " + uuid + " data...");
        CustomPlayer cPly = uuidMapping.get(uuid);
        cPly.team = cPly.player.team();

        String[] keys = { "playTime" };
        Object[] vals = { cPly.playTime };
        db.saveRow("mindustry_data", "uuid", uuid, keys, vals);
    }

    void endgame(Seq<CustomPlayer> winners) {

        Log.info("---- ENDING GAME ----");

        gameover = true;

        String[] keys = new String[] { "gamemode", "mapID" };
        Object[] vals = new Object[] { "plague", mapID };
        HashMap<String, Object> entries = db.loadRow("mindustry_map_data", keys, vals);
        long timeNow = seconds;

        for (CustomPlayer cPly : winners) {
            if (!cPly.connected)
                continue;
            Call.infoMessage(cPly.player.con, "[green]You survived the longest\n" +
                    (newRecord ? "    [gold]New record!\n" : "") +
                    "[accent]Survive time: [scarlet]" + timeNow / 60 + "[accent] minutes and [scarlet]" +
                    timeNow % 60 + "[accent] seconds.");
        }

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
            avgSurvived = (avgSurvived * (plays - 1) + seconds) / plays;
        }

        long survivorRecord = (int) entries.get("survivorRecord");
        if (newRecord) {
            survivorRecord = seconds;
        }

        for (Player player : Groups.player) {
            savePlayerData(player.uuid());
        }

        long finalSurvivorRecord = survivorRecord;
        long finalAvgSurvived = avgSurvived;
        long finalPlays = plays;
        Time.runTask(60f * 10f, () -> {

            db.saveRow("mindustry_map_data", keys, vals,
                    new String[] { "survivorRecord", "avgSurvived", "plays" },
                    new Object[] { finalSurvivorRecord, finalAvgSurvived, finalPlays });

            Events.fire(new EventType.GameOverEvent(Team.malis));

            Log.info("Game ended successfully.");
            mapReset();
        });

    }

    void mapReset() {
        mapReset(new String[] {});
    }

    void mapReset(String[] args) {
        resetting = true;

        uuidMapping.keySet().removeIf(uuid -> !uuidMapping.get(uuid).connected);
        teams = new HashMap<>();

        teamsCount = 0;

        multiplier = 1f;

        seconds = 0;
        startTime = System.currentTimeMillis();

        newRecord = false;

        counts = 0;
        pregame = true;
        gameover = false;
        hasWon = false;

        resetRules();
        leaderboardString = _leaderboardInit(5);

        corePlaceInterval.reset();
        tenMinInterval.reset();
        oneMinInterval.reset();

        // Load new map:
        loadMap(args);
        resetting = false;
    }

    void loadMap(String[] args) {

        int currMap;
        mapIndex = (mapIndex + 1) % maps.customMaps().size;
        if (args.length > 0) {
            int i = 0;
            for (mindustry.maps.Map map : maps.customMaps()) {
                Log.info(i + ": " + map.name());
                i += 1;
            }

            currMap = Integer.parseInt(args[0]);
        } else {
            currMap = rotation.get(mapIndex);
        }

        Seq<Player> players = new Seq<>();
        for (Player p : Groups.player) {
            if (p.isLocal())
                continue;

            players.add(p);
            p.clearUnit();
        }

        logic.reset();
        mindustry.maps.Map map = maps.customMaps().get(currMap);
        Log.info("Loading map " + map.name());

        world.loadMap(map);
        mindustry.maps.Map loadedMap = state.map;

        Tile tile = state.teams.cores(Team.malis)
                .find(build -> build.block == Blocks.coreNucleus || build.block == Blocks.coreFoundation
                        || build.block == Blocks.coreAcropolis).tile;

        // Make cores and power source indestructible
        state.teams.cores(Team.malis).each(coreBuild -> coreBuild.health = Integer.MAX_VALUE);
        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.height(); y++) {
                Tile t = world.tile(x, y);
                if (t.build != null && t.build.block.equals(Blocks.powerSource)) {
                    t.build.health = Integer.MAX_VALUE;
                }
            }
        }

        isSerpulo = tile.build.block != Blocks.coreAcropolis;

        plagueCore[0] = tile.x;
        plagueCore[1] = tile.y;
        world.beginMapLoad();
        PlagueGenerator.defaultOres(world.tiles, isSerpulo);

        world.endMapLoad();

        rules.enemyCoreBuildRadius = 75 * 7;
        rules.hiddenBuildItems = map.rules().hiddenBuildItems;
        rules.hideBannedBlocks = true;
        state.rules = rules.copy();

        if (firstRun) {
            Log.info("Server not up, starting server...");
            netServer.openServer();
            firstRun = false;
        }

        mapID = loadedMap.file.name();
        String[] keys = new String[] { "gamemode", "mapID" };
        Object[] vals = new Object[] { "plague", mapID };
        if (!db.hasRow("mindustry_map_data", keys, vals)) {
            db.addEmptyRow("mindustry_map_data", keys, vals);
        }
        HashMap<String, Object> entries = db.loadRow("mindustry_map_data", keys, vals);
        mapRecord = (int) entries.get("survivorRecord"); // Get map record
        avgSurvived = (int) entries.get("avgSurvived"); // Get average time survived
        mapPlays = (int) entries.get("plays"); // Get number of map plays

        tile.build.items.clear();

        teams.put(Team.malis, new PlagueTeam(Team.malis));
        teams.put(Team.blue, new PlagueTeam(Team.blue));
        logic.play();

        for (Player player : players) {
            Call.worldDataBegin(player.con);
            netServer.sendWorldData(player);
            uuidMapping.get(player.uuid()).reset();

            loadPlayer(player);
        }

        Log.info("Done");

    }
}
