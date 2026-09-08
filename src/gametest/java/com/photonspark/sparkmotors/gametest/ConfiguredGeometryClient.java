package com.photonspark.sparkmotors.gametest;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.client.CarMesh;
import com.photonspark.sparkmotors.item.MechanicalData;
import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.electric.Powertrain;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
/** Uses the resource-loaded production renderer on native client entities; does not mutate the played car. */
final class ConfiguredGeometryClient {
    static void verify(Minecraft mc){
        int cases=0,removals=0;
        for(var type:Powertrain.values())for(var layout:DriveConfig.Layout.values())for(var family:EngineFamily.values()){
            var car=AutoPropulsionAge.CAR.get().create(mc.level);var drive=DriveConfig.preset(layout);
            var mechanical=PowertrainTopology.fresh(type,drive,family,Assembly.stock(),EnginePart.stock());
            var tag=new CompoundTag();tag.putInt("DataVersion",7);tag.putString("Powertrain",type.id());tag.putInt("DriveSetup",drive.packed());tag.putInt("FrontSplit",drive.frontPercent());tag.putInt("EngineFamily",family.ordinal());tag.putInt("EngineParts",EnginePart.stock());tag.putInt("Assemblies",Assembly.stock());tag.put("Mechanics",MechanicalData.write(mechanical));car.load(tag);
            for(var slot:ComponentSlot.ALL)if(slot.detailed()){
                int visible=CarMesh.visibleComponentTriangles(car,slot.key());boolean expected=PowertrainTopology.applicable(slot,type,drive,family);
                if(expected!=(visible>0))throw new IllegalStateException("Configured geometry mismatch "+type+" "+layout+" "+family+" "+slot.key()+" triangles="+visible);
                for(int lod=0;lod<2;lod++)if((CarMesh.visibleWorldComponentTriangles(car,slot.key(),lod)>0)!=expected)throw new IllegalStateException("World LOD topology mismatch: "+slot.key());
                if(expected){car.setMechanics(mechanical.with(slot.key(),null));if(CarMesh.visibleComponentTriangles(car,slot.key())!=0)throw new IllegalStateException("Removed component still visible: "+slot.key());
                    for(int lod=0;lod<2;lod++)if(CarMesh.visibleWorldComponentTriangles(car,slot.key(),lod)!=0)throw new IllegalStateException("World LOD retained removed part: "+slot.key());
                    car.setMechanics(mechanical);
                    for(int lod=0;lod<2;lod++)if(CarMesh.visibleWorldComponentTriangles(car,slot.key(),lod)==0)throw new IllegalStateException("World LOD did not restore installed part: "+slot.key());
                    removals++;}
            }
            cases++;
        }
        System.out.println("CONFIGURED_GEOMETRY_CLIENT_PASS cases="+cases+" removals="+removals);
    }
}
