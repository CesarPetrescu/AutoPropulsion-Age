package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.item.*;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class BodyStyleGameTests {
    private CarEntity fresh(GameTestHelper h,BodyStyle body,Powertrain type){
        var c=AutoPropulsionAge.CAR.get().create(h.getLevel());var pos=h.absoluteVec(new Vec3(8,2.05,8));
        c.moveTo(pos.x,pos.y,pos.z,0,0);c.initializePowertrain(type,.321);c.initializeBodyStyle(body);return c;
    }
    private static CompoundTag saved(CarEntity c){var tag=new CompoundTag();c.saveWithoutId(tag);return tag;}
    private static ItemStack crate(BodyStyle body,Powertrain type){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("sparkmotors",BodyStyles.crateId(body,type))));}

    @GameTest(template="test_track") public void allThirtyBodyPowertrainIdentitiesPersistWithoutRefreshingUsedState(GameTestHelper h){
        int count=0;
        for(var body:BodyStyle.values())for(var type:Powertrain.values()){
            var c=fresh(h,body,type);var tag=saved(c);
            tag.putFloat("Fuel",type.electric()&&!type.hybrid()?0:12.75f);
            tag.putDouble("BatteryHealth",.72);tag.putDouble("BatteryC",41);tag.putDouble("BatteryThroughputJ",6789123);tag.putDouble("MotorC",92);
            c.load(tag);var before=saved(c);var loaded=AutoPropulsionAge.CAR.get().create(h.getLevel());loaded.load(before);var after=saved(loaded);
            h.assertTrue(loaded.bodyStyle()==body&&loaded.powertrain()==type,"Body/powertrain persistence "+body+" "+type);
            h.assertTrue(c.mechanics().equals(loaded.mechanics())&&c.fuel()==loaded.fuel(),"Used mechanical state survives "+body+" "+type);
            if(type.electric())for(String field:List.of("BatteryJ","BatteryC","BatteryHealth","BatteryThroughputJ","MotorC","InverterC"))
                h.assertTrue(before.getDouble(field)==after.getDouble(field),"Energy/thermal/wear changed "+field+" "+body);
            h.assertTrue(!loaded.ignition()&&!loaded.plugged(),"Reload cannot resurrect READY or a cable");count++;
        }
        System.out.println("BODY_PERSISTENCE_SERVER_PASS "+count);h.succeed();
    }
    @GameTest(template="test_track") public void unknownAndLegacyBodyIdsDoNotConvertPowertrains(GameTestHelper h){
        var c=fresh(h,BodyStyle.SUV,Powertrain.PLUG_IN_HYBRID);var tag=saved(c);var original=c.mechanics();
        tag.remove("BodyStyle");c.load(tag);h.assertTrue(c.bodyStyle()==BodyStyle.CLASSIC_SEDAN&&c.powertrain()==Powertrain.PLUG_IN_HYBRID,"Missing body ID fallback");
        tag.putString("BodyStyle","unknown-future-model");c.load(tag);
        h.assertTrue(c.bodyStyle()==BodyStyle.CLASSIC_SEDAN&&c.mechanics().equals(original),"Unknown body ID must not manufacture or remove powertrain parts");h.succeed();
    }
    @GameTest(template="test_track") public void allPaidCrateConversionsPreserveComponentsAndReturnOneOldShell(GameTestHelper h){
        int count=0;
        for(var type:Powertrain.values())for(var source:BodyStyle.values())for(var target:BodyStyle.values()){
            if(source==target)continue;
            var donor=crate(source,type);h.assertTrue(donor.getItem() instanceof CarCrateItem,"Registered body crate "+source+" "+type);
            var state=fresh(h,source,type).mechanics();MechanicalData.set(donor,state);
            var data=donor.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            var pack=new CompoundTag();pack.putDouble("EnergyJ",1234567);pack.putDouble("TemperatureC",39);pack.putDouble("Health",.71);pack.putDouble("ThroughputJ",9876543);data.put("TractionBattery",pack);data.putString("AuditIdentity","used-donor-never-reset");
            donor.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            var input=CraftingInput.of(2,1,List.of(donor,BodyStyles.kit(target)));
            var found=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
            h.assertTrue(found.value() instanceof BodyCratingRecipe,"Body conversion must use the preserving serializer");
            var output=found.value().assemble(input,h.getLevel().registryAccess());var item=(CarCrateItem)output.getItem();
            h.assertTrue(item.bodyStyle()==target&&item.powertrain()==type,"Body conversion cannot change the powertrain");
            h.assertTrue(output.getComponentsPatch().equals(donor.getComponentsPatch()),"Complete donor data patch changed "+source+" -> "+target);
            h.assertTrue(MechanicalData.get(output).equals(state),"Serials/condition/fluids changed in crate conversion");
            var remaining=found.value().getRemainingItems(input);
            h.assertTrue(remaining.get(0).isEmpty()&&remaining.get(1).is(BodyStyles.KITS.get(source).get())&&remaining.get(1).getCount()==1,"Exactly one old shell returned, not another car");count++;
        }
        System.out.println("BODY_CRATE_CONVERSION_SERVER_PASS "+count);h.succeed();
    }
    @GameTest(template="test_track") public void bodyKitIsPaidAndDoesNotRepairOrRechargeTheVehicle(GameTestHelper h){
        var c=fresh(h,BodyStyle.CLASSIC_SEDAN,Powertrain.ELECTRIC_800);h.getLevel().addFreshEntity(c);
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);p.moveTo(c.position().add(3,0,0));c.setOwner(p.getUUID());
        p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get("service_jack").get()));
        var kit=BodyStyles.kit(BodyStyle.VAN);p.setItemInHand(InteractionHand.MAIN_HAND,kit);
        c.interact(p,InteractionHand.MAIN_HAND);h.assertTrue(c.bodyStyle()==BodyStyle.CLASSIC_SEDAN&&kit.getCount()==1,"Kit without lift must be refused and retained");
        c.tickCount+=4;c.action(p,CarPackets.JACK,0,0);h.assertTrue(c.raised(),"Fixture raises using a real owned jack");
        var before=saved(c);var mechanical=c.mechanics();c.interact(p,InteractionHand.MAIN_HAND);var after=saved(c);
        h.assertTrue(c.bodyStyle()==BodyStyle.VAN&&kit.isEmpty(),"Paid installation changes shell and consumes the kit");
        h.assertTrue(p.getInventory().countItem(BodyStyles.KITS.get(BodyStyle.CLASSIC_SEDAN).get())==1,"Old shell returned exactly once");
        h.assertTrue(c.mechanics().equals(mechanical),"No part replacement/repair during body swap");
        for(String field:List.of("BatteryJ","BatteryC","BatteryHealth","BatteryThroughputJ","MotorC","InverterC"))h.assertTrue(before.getDouble(field)==after.getDouble(field),"Swap changed "+field);
        h.assertTrue(before.getFloat("Fuel")==after.getFloat("Fuel")&&before.getFloat("Health")==after.getFloat("Health"),"Swap cannot heal or refuel");
        System.out.println("BODY_PAID_INSTALL_SERVER_PASS");h.succeed();
    }
    @GameTest(template="test_track") public void blockedOrUnauthorizedBodySwapLeavesBothWorldAndInventoryUnchanged(GameTestHelper h){
        var c=fresh(h,BodyStyle.HATCHBACK,Powertrain.PLUG_IN_HYBRID);h.getLevel().addFreshEntity(c);
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);p.moveTo(c.position().add(3,0,0));c.setOwner(UUID.randomUUID());
        var kit=BodyStyles.kit(BodyStyle.VAN);p.setItemInHand(InteractionHand.MAIN_HAND,kit);
        c.interact(p,InteractionHand.MAIN_HAND);h.assertTrue(c.bodyStyle()==BodyStyle.HATCHBACK&&kit.getCount()==1,"Unauthorized body change");
        c.setOwner(p.getUUID());p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get("service_jack").get()));
        c.tickCount+=4;c.action(p,CarPackets.JACK,0,0);h.assertTrue(c.raised(),"Fixture jack");
        // Wall beyond the hatch's rear but inside the longer van shell.
        h.setBlock(new BlockPos(8,3,5),Blocks.STONE);
        var before=c.mechanics();c.interact(p,InteractionHand.MAIN_HAND);
        h.assertTrue(c.bodyStyle()==BodyStyle.HATCHBACK&&kit.getCount()==1&&c.mechanics().equals(before),"Obstructed expansion must roll back without consuming or repairing anything");
        h.succeed();
    }
    @GameTest(template="test_track",timeoutTicks=4200) public void thirtyBodyPowertrainVariantsDriveAndBrakeThroughActualServerTicks(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();CarEntity[] active={null};int[] ticks={0};double[] start={0};float[] peak={0};
        h.onEachTick(()->{
            int t=ticks[0]++,job=t/130,step=t%130;if(job>=30)return;
            if(step==0){
                if(active[0]!=null){p.stopRiding();active[0].discard();}
                var type=Powertrain.values()[job%5];var body=BodyStyle.values()[job/5];var c=fresh(h,body,type);active[0]=c;
                c.setDriveConfig(DriveConfig.preset(DriveConfig.Layout.values()[job%3]));c.initializePowertrain(type,.6);
                h.getLevel().addFreshEntity(c);p.moveTo(c.position());c.setOwner(p.getUUID());p.startRiding(c,true);c.action(p,CarPackets.IGNITION,0,0);
                peak[0]=0;start[0]=type.electric()?c.tractionBattery().energyJ():c.fuel();
            }
            var c=active[0];c.receiveInput(step<10?4:step<60?1:2,0);peak[0]=Math.max(peak[0],c.horizontalSpeed());
            if(step==128){
                h.assertTrue(peak[0]>2&&c.horizontalSpeed()<.3,"Body drive/brake "+job+" peak="+peak[0]+" final="+c.horizontalSpeed());
                h.assertTrue(c.bodyStyle()==BodyStyle.values()[job/5]&&!c.reverseSelected(),"Body identity or direction changed while driving");
                if(c.powertrain().electric())h.assertTrue(c.tractionBattery().energyJ()<start[0],"Body acceleration/braking created energy");
                else h.assertTrue(c.fuel()<start[0],"Combustion body drove without spending fuel");
                System.out.println("BODY_DRIVING_SERVER_CASE_PASS "+job+" "+c.bodyStyle()+" "+c.powertrain());
                if(job==29){p.stopRiding();c.discard();System.out.println("BODY_DRIVING_SERVER_MATRIX_PASS 30");h.succeed();}
            }
        });
    }
}
