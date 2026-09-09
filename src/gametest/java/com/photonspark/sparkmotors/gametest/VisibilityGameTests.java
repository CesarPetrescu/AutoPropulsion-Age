package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.charging.*;
import com.photonspark.sparkmotors.sim.electric.ChargingModel;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.gametest.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class VisibilityGameTests {
    @GameTest(template="test_track") public void chargersMustNotEraseTheSupportingBlockOrNeighbors(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(5,3,5));int count=0;
        for(var tier:ChargingModel.Tier.values())for(var facing:Direction.Plane.HORIZONTAL){
            var state=Electrification.CHARGERS.get(tier).get().defaultBlockState().setValue(ChargerBlock.FACING,facing);
            h.getLevel().setBlock(pos,state,3);
            h.assertTrue(!state.canOcclude(),"Incomplete charger casing must not occlude neighbors: "+tier+" "+facing);
            h.assertTrue(state.getOcclusionShape(h.getLevel(),pos).isEmpty(),"Non-full charger occlusion shape");
            for(var direction:Direction.values()){
                var neighbor=pos.relative(direction);var block=Blocks.STONE.defaultBlockState();h.getLevel().setBlock(neighbor,block,3);
                h.assertTrue(Block.shouldRenderFace(block,h.getLevel(),neighbor,direction.getOpposite(),pos),"Missing adjacent solid face: "+tier+" "+facing+" "+direction);
                h.getLevel().removeBlock(neighbor,false);count++;
            }
        }
        System.out.println("CHARGER_NEIGHBOR_VISIBILITY_PASS "+count);h.succeed();
    }
    @GameTest(template="test_track") public void chargerSelectionAndCollisionMatchPackagedCuboidsInEveryRotation(GameTestHelper h){
        int count=0;var pos=h.absolutePos(new BlockPos(5,3,5));
        for(var tier:ChargingModel.Tier.values()){
            String path="/assets/sparkmotors/models/block/"+tier.id()+".json";
            try(var stream=VisibilityGameTests.class.getResourceAsStream(path)){
                if(stream==null)throw new IOException(path);
                var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                for(var direction:Direction.Plane.HORIZONTAL){
                    double angle=Math.toRadians(switch(direction){case NORTH->0;case EAST->90;case SOUTH->180;case WEST->270;default->throw new IllegalArgumentException();});
                    VoxelShape expected=Shapes.empty();
                    for(var element:json.getAsJsonArray("elements")){
                        var obj=element.getAsJsonObject();var from=obj.getAsJsonArray("from");var to=obj.getAsJsonArray("to");
                        for(String face:new String[]{"north","south","east","west","up","down"})h.assertTrue(obj.getAsJsonObject("faces").has(face),"Unclosed charger JSON cuboid "+tier);
                        double minX=99,minZ=99,maxX=-99,maxZ=-99;
                        for(double x:new double[]{from.get(0).getAsDouble(),to.get(0).getAsDouble()})for(double z:new double[]{from.get(2).getAsDouble(),to.get(2).getAsDouble()}){
                            double rx=8+(x-8)*Math.cos(angle)-(z-8)*Math.sin(angle),rz=8+(x-8)*Math.sin(angle)+(z-8)*Math.cos(angle);
                            minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minZ=Math.min(minZ,rz);maxZ=Math.max(maxZ,rz);
                        }
                        // Round away trigonometric floating-point dust, preserving 0.001 model units.
                        expected=Shapes.or(expected,Block.box(round(minX),from.get(1).getAsDouble(),round(minZ),round(maxX),to.get(1).getAsDouble(),round(maxZ)));
                    }
                    var state=Electrification.CHARGERS.get(tier).get().defaultBlockState().setValue(ChargerBlock.FACING,direction);
                    h.getLevel().setBlock(pos,state,3);
                    h.assertTrue(!Shapes.joinIsNotEmpty(expected,state.getShape(h.getLevel(),pos),BooleanOp.NOT_SAME),"Charger outline differs from JSON "+tier+" "+direction);
                    h.assertTrue(!Shapes.joinIsNotEmpty(expected,state.getCollisionShape(h.getLevel(),pos),BooleanOp.NOT_SAME),"Charger collision differs from JSON "+tier+" "+direction);count++;
                }
            }catch(IOException e){throw new IllegalStateException(e);}
        }
        System.out.println("CHARGER_MODEL_SHAPE_PASS "+count);h.succeed();
    }
    private static double round(double value){return Math.round(value*1000)/1000.;}
}
