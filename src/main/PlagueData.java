package main;

import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.*;
import mindustry.content.*;
import mindustry.maps.filters.*;
import mindustry.type.*;
import mindustry.world.Block;

public class PlagueData {
    /** block.emoji() wasnt working idk */
    public static final ObjectMap<Block, Character> emojiMap = ObjectMap.of(Blocks.foreshadow, '', Blocks.cyclone, '',
            Blocks.swarmer, '', Blocks.duo, '');

    public static final Seq<ItemStack> survivorLoadoutSerpulo = ItemStack.list(Items.copper, 3600, Items.lead, 3600,
            Items.graphite, 1800, Items.titanium, 1800, Items.silicon, 900, Items.metaglass, 900, Items.thorium, 500);

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
            Blocks.logicDisplay, Blocks.largeLogicDisplay, Blocks.canvas, // Blocks.microProcessor,
                                                                          // Blocks.logicProcessor,
                                                                          // Blocks.hyperProcessor,
            Blocks.exponentialReconstructor, Blocks.tetrativeReconstructor,
            Blocks.shipAssembler, Blocks.mechAssembler, Blocks.tankAssembler, Blocks.tankFabricator,
            Blocks.shipFabricator,
            Blocks.mechFabricator, Blocks.tankRefabricator, Blocks.mechRefabricator, Blocks.shipRefabricator,
            Blocks.primeRefabricator, Blocks.scathe);

    public static final ObjectSet<Block> plagueBanned;
    static {
        plagueBanned = ObjectSet.with(Blocks.duo, Blocks.scatter, Blocks.scorch, Blocks.lancer, Blocks.arc,
                Blocks.swarmer, Blocks.salvo, Blocks.fuse, Blocks.cyclone, Blocks.spectre, Blocks.meltdown,
                Blocks.hail, Blocks.ripple, Blocks.shockMine, Blocks.parallax, Blocks.segment, Blocks.tsunami,
                Blocks.foreshadow,
                Blocks.battery, Blocks.batteryLarge, Blocks.combustionGenerator, Blocks.thermalGenerator,
                Blocks.steamGenerator, Blocks.differentialGenerator, Blocks.rtgGenerator, Blocks.solarPanel,
                Blocks.largeSolarPanel, Blocks.thoriumReactor, Blocks.impactReactor,
                Blocks.surgeWall, Blocks.surgeWallLarge, Blocks.thoriumWall, Blocks.thoriumWallLarge, Blocks.phaseWall,
                Blocks.phaseWallLarge, Blocks.titaniumWall, Blocks.titaniumWallLarge, Blocks.copperWallLarge,
                Blocks.copperWall, Blocks.door, Blocks.doorLarge, Blocks.plastaniumWall, Blocks.plastaniumWallLarge,
                Blocks.logicDisplay, Blocks.largeLogicDisplay, Blocks.canvas,
                Blocks.breach, Blocks.diffuse, Blocks.sublimate, Blocks.titan, Blocks.disperse, Blocks.afflict,
                Blocks.lustre,
                Blocks.scathe, Blocks.smite, Blocks.malign,
                Blocks.turbineCondenser, Blocks.chemicalCombustionChamber, Blocks.fluxReactor,
                Blocks.neoplasiaReactor); // Blocks.microProcessor, Blocks.logicProcessor, Blocks.hyperProcessor);
    }

    public static final ObjectSet<Block> plagueBannedPreWin;
    static {
        plagueBannedPreWin = ObjectSet.with(plagueBanned.toSeq());
        plagueBannedPreWin.addAll(Blocks.shipFabricator, Blocks.shipRefabricator, Blocks.shipAssembler);
    }

    public static final ObjectSet<GenerateFilter> erekirFilters;
    static {
        final int[] seedOffset = { (int) System.currentTimeMillis() };
        OreFilter berylliumFloorFilter = new OreFilter() {
            {
                ore = Blocks.oreBeryllium;
                seed = seedOffset[0]++;
            }
        };

        OreFilter berylliumWallFilter = new OreFilter() {
            {
                ore = Blocks.wallOreBeryllium;
                seed = seedOffset[0]++;
                threshold -= 0.1;
            }
        };

        OreFilter tungstenFloorFilter = new OreFilter() {
            {
                ore = Blocks.oreTungsten;
                seed = seedOffset[0]++;
            }
        };
        OreFilter tungstenWallFilter = new OreFilter() {
            {
                ore = Blocks.wallOreTungsten;
                seed = seedOffset[0]++;
                threshold -= 0.1;
            }
        };

        OreFilter thoriumFloorFilter = new OreFilter() {
            {
                ore = Blocks.oreCrystalThorium;
                seed = seedOffset[0]++;
            }
        };
        OreFilter thoriumWallFilter = new OreFilter() {
            {
                ore = Blocks.wallOreThorium;
                seed = seedOffset[0]++;
                threshold -= 0.1;
            }
        };

        OreFilter graphiteWallFilter = new OreFilter() {
            {
                ore = Blocks.graphiticWall;
                seed = seedOffset[0]++;
                threshold -= 0.2;
            }
        };

        OreFilter slagOreFilter = new OreFilter() {
            {
                ore = Blocks.slag;
                seed = seedOffset[0]++;
                threshold += 0.06;
            }
        };

        OreFilter arkyciteOreFilter = new OreFilter() {
            {
                ore = Blocks.arkyciteFloor;
                seed = seedOffset[0]++;
                threshold += 0.06;
            }
        };

        ScatterFilter ventFilter = new ScatterFilter() {
            {
                floor = Blocks.carbonVent;
                chance = 0.0015f;
            }
        };

        erekirFilters = ObjectSet.with(
                berylliumFloorFilter, berylliumWallFilter,
                tungstenFloorFilter, tungstenWallFilter,
                thoriumFloorFilter, thoriumWallFilter,
                graphiteWallFilter,
                slagOreFilter, arkyciteOreFilter,
                ventFilter);
    }
}