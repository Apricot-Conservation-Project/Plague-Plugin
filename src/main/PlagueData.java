package main;

import arc.math.Mathf;
import arc.struct.*;
import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.Block;

public class PlagueData {
    public static final Seq<UnitType> transferUnits = Seq.with(UnitTypes.mega, UnitTypes.poly, UnitTypes.flare,
            UnitTypes.oct);

    public static final Seq<ItemStack> survivorLoadoutSerpulo = ItemStack.list(Items.copper, 3600, Items.lead, 3600,
            Items.graphite, 500, Items.titanium, 900, Items.silicon, 500, Items.metaglass, 200);

    public static final Seq<ItemStack> survivorIncrementSerpulo = ItemStack.list(Items.copper, 500, Items.lead, 500);

    public static final Seq<ItemStack> survivorIncrementErekir = ItemStack.list(Items.beryllium, 500, Items.graphite,
            500);

    public static final Seq<ItemStack> survivorLoadoutErekir = ItemStack.list(Items.beryllium, 1000, Items.graphite,
            1000,
            Items.tungsten, 500, Items.thorium, 500, Items.silicon, 900);

    public static final Seq<Item> serpuloOnlyItems = Seq.with(Items.copper, Items.lead, Items.metaglass,
            Items.blastCompound, Items.sporePod,
            Items.titanium, Items.plastanium);

    public static final ObjectSet<Block> serpuloCores = ObjectSet.with(Blocks.coreNucleus, Blocks.coreFoundation,
            Blocks.coreShard);

    public static final ObjectSet<Block> erekirBlockSet = ObjectSet.with(Blocks.duct,
            Blocks.ductBridge, Blocks.ductRouter, Blocks.ductUnloader, Blocks.armoredDuct);

    public static final ObjectSet<Block> survivorBanned = ObjectSet.with(Blocks.groundFactory, Blocks.navalFactory,
            Blocks.exponentialReconstructor, Blocks.tetrativeReconstructor,
            Blocks.shipAssembler, Blocks.mechAssembler, Blocks.tankAssembler, Blocks.tankFabricator,
            Blocks.shipFabricator,
            Blocks.mechFabricator, Blocks.tankRefabricator, Blocks.mechRefabricator, Blocks.shipRefabricator,
            Blocks.primeRefabricator, Blocks.scathe);

    public static final ObjectSet<Block> turrets = ObjectSet.with(Blocks.duo, Blocks.scatter, Blocks.scorch,
            Blocks.lancer, Blocks.arc,
            Blocks.swarmer, Blocks.salvo, Blocks.fuse, Blocks.cyclone, Blocks.spectre, Blocks.meltdown,
            Blocks.hail, Blocks.ripple, Blocks.shockMine, Blocks.parallax, Blocks.segment, Blocks.tsunami,
            Blocks.foreshadow, Blocks.breach, Blocks.diffuse, Blocks.sublimate, Blocks.titan, Blocks.disperse,
            Blocks.afflict, Blocks.lustre,
            Blocks.scathe, Blocks.smite, Blocks.malign);

    public static final ObjectSet<Block> plagueBanned;
    static {
        plagueBanned = ObjectSet.with(turrets.toSeq());
        plagueBanned.addAll(
                Blocks.berylliumWall, Blocks.berylliumWallLarge, Blocks.tungstenWall, Blocks.tungstenWallLarge,
                Blocks.blastDoor, Blocks.reinforcedSurgeWall, Blocks.reinforcedSurgeWallLarge, Blocks.carbideWall,
                Blocks.carbideWallLarge, Blocks.shieldedWall);
        plagueBanned.addAll(
                Blocks.combustionGenerator, Blocks.thermalGenerator,
                Blocks.steamGenerator, Blocks.differentialGenerator, Blocks.rtgGenerator, Blocks.solarPanel,
                Blocks.largeSolarPanel, Blocks.thoriumReactor, Blocks.impactReactor,
                Blocks.surgeWall, Blocks.surgeWallLarge, Blocks.thoriumWall, Blocks.thoriumWallLarge, Blocks.phaseWall,
                Blocks.phaseWallLarge, Blocks.titaniumWall, Blocks.titaniumWallLarge, Blocks.copperWallLarge,
                Blocks.copperWall, Blocks.door, Blocks.doorLarge, Blocks.plastaniumWall, Blocks.plastaniumWallLarge,
                Blocks.logicDisplay, Blocks.largeLogicDisplay, Blocks.canvas,
                Blocks.turbineCondenser, Blocks.chemicalCombustionChamber, Blocks.fluxReactor,
                Blocks.neoplasiaReactor); // Blocks.microProcessor, Blocks.logicProcessor, Blocks.hyperProcessor);
    }

    public static final ObjectSet<Block> plagueBannedPreWin;
    static {
        plagueBannedPreWin = ObjectSet.with(plagueBanned.toSeq());
        plagueBannedPreWin.addAll(Blocks.shipFabricator, Blocks.shipRefabricator, Blocks.shipAssembler);
    }

    public static int getRandomWithExclusion(int start, int end, int... exclude) {
        int random = start + Mathf.random(end - start - exclude.length);
        for (int ex : exclude) {
            if (random < ex) {
                break;
            }
            random++;
        }
        return random;
    }

}
