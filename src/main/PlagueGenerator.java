package main;

import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.content.SectorPresets;
import mindustry.game.Team;
import mindustry.maps.Map;
import mindustry.maps.filters.FilterOption;
import mindustry.maps.filters.GenerateFilter;
import mindustry.maps.filters.GenerateFilter.GenerateInput;
import mindustry.maps.filters.OreFilter;
import mindustry.maps.generators.BaseGenerator;
import mindustry.type.Sector;
import mindustry.type.SectorPreset;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.environment.StaticWall;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static mindustry.Vars.*;


public class PlagueGenerator{

    public static final int size = 601;

    public static final int[] a = IntStream.range(1, 100).toArray();

    public static void defaultOres(Tiles tiles, boolean isSerpulo){

        Seq<GenerateFilter> filters = new Seq<>();
        int seedOffset = (int) System.currentTimeMillis();
        if(isSerpulo){ // Serpulo ores
            Log.info("Serpulo");
            maps.addDefaultOres(filters);


            for(GenerateFilter o : filters){
                ((OreFilter) o).threshold -= 0.05f;
                o.seed = seedOffset++;
            }

            filters.insert(0, new OreFilter() {{
                ore = Blocks.oreScrap;
            }});
        }else{
            filters = PlagueData.erekirFilters.toSeq();
        }


        GenerateInput in = new GenerateInput();
        in.width = tiles.width;
        in.height = tiles.height;

        for (int x = 0; x < tiles.width; x++) {
            for (int y = 0; y < tiles.height; y++) {
                in.overlay = Blocks.air;
                in.floor = Blocks.darksand;
                in.x = x;
                in.y = y;

                if(tiles.get(x,y).floor().isLiquid || !tiles.get(x,y).floor().placeableOn) continue;

                for (GenerateFilter f : filters) {

                    if(f instanceof OreFilter){
                        OreFilter of = (OreFilter) f;
                        if(tiles.get(x,y).build != null ||
                            tiles.get(x,y).block() == Blocks.air &&
                            (of.ore == Blocks.graphiticWall
                            ||
                            (of.ore instanceof OreBlock && ((OreBlock) of.ore).wallOre))
                        ) {
                            continue;
                        }
                    }


                    f.apply(in);
                }

                if(in.floor != Blocks.darksand && in.floor == Blocks.carbonVent){
                    if(x != tiles.width-1 && y != tiles.height-1 && x != 0 && y != 0){
                        // 3x3 shape
                        for(int i=-1; i < 2; i++){
                            for(int j=-1; j < 2; j++){
                                tiles.get(x+i,y+j).setFloor((Floor) in.floor);
                            }
                        }
                    }

                }

                if(in.overlay instanceof StaticWall){
                    tiles.get(x,y).setBlock(in.overlay);
                }else if(in.overlay instanceof Floor && ((Floor) in.overlay).isLiquid){
                    tiles.get(x,y).setFloor((Floor) in.overlay);
                }else{
                    tiles.get(x,y).setOverlay(in.overlay);
                }

            }
        }
    }

}


