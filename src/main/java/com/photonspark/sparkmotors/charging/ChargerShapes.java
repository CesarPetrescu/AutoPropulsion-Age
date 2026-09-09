package com.photonspark.sparkmotors.charging;

import com.photonspark.sparkmotors.sim.electric.ChargingModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.EnumMap;

/** Cached outline/collision unions matching the vanilla JSON cuboids, in all four rotations.
 * Native GameTests compare these against the packaged JSON; changing a model cannot silently
 * leave a full-cube collision/occlusion proxy behind. No charging or circuit logic lives here.
 */
final class ChargerShapes {
    private static final EnumMap<ChargingModel.Tier,VoxelShape[]> SHAPES=new EnumMap<>(ChargingModel.Tier.class);
    static {
        add(ChargingModel.Tier.LV,new double[][]{
            {3,0,4,13,12,12},
            {4,5,3.7,12,11,4},
            {5,7,3.5,11,10,3.7},
            {13,2,6,14,8,8},
            {12,8,6,14,9,8}
        });
        add(ChargingModel.Tier.HOME,new double[][]{
            {3,0,4,13,15,12},
            {4,5,3.7,12,13,4},
            {5,7,3.5,11,12,3.7},
            {13,2,6,14,8,8},
            {12,8,6,14,9,8}
        });
        add(ChargingModel.Tier.RAPID,new double[][]{
            {1,0,2,15,2,14},
            {3,2,4,13,15,12},
            {4,8,3.7,12,14,4},
            {5,10,3.5,11,13,3.7},
            {13,4,5,14,12,7},
            {12,12,5,14,13,7},
            {4,3,3.8,12,3.3,4},
            {4,4,3.8,12,4.3,4},
            {4,5,3.8,12,5.3,4},
            {4,6,3.8,12,6.3,4},
            {4,7,3.8,12,7.3,4}
        });
        add(ChargingModel.Tier.ULTRA,new double[][]{
            {1,0,2,15,2,14},
            {3,2,4,13,15,12},
            {4,8,3.7,12,14,4},
            {5,10,3.5,11,13,3.7},
            {13,4,5,14,12,7},
            {12,12,5,14,13,7},
            {4,3,3.8,12,3.3,4},
            {4,4,3.8,12,4.3,4},
            {4,5,3.8,12,5.3,4},
            {4,6,3.8,12,6.3,4},
            {4,7,3.8,12,7.3,4},
            {0,2,5,3,15,13},
            {13,2,8,16,15,13},
            {3,14.7,3.6,13,15.3,4.2}
        });
    }
    private static void add(ChargingModel.Tier tier,double[][] boxes){
        VoxelShape[] rotations=new VoxelShape[4];
        for(int turn=0;turn<4;turn++){
            VoxelShape result=Shapes.empty();
            for(double[] b:boxes){
                double x0=b[0],z0=b[2],x1=b[3],z1=b[5];
                for(int i=0;i<turn;i++){double n0=16-z1,n1=16-z0;z0=x0;z1=x1;x0=n0;x1=n1;}
                result=Shapes.or(result,Block.box(x0,b[1],z0,x1,b[4],z1));
            }
            rotations[turn]=result.optimize();
        }
        SHAPES.put(tier,rotations);
    }
    static VoxelShape get(ChargingModel.Tier tier,Direction facing){
        int rotation=switch(facing){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("Horizontal charger direction required");};
        return SHAPES.get(tier)[rotation];
    }
    private ChargerShapes(){}
}
