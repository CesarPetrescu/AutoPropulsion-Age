package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.charging.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class ElectricGameTests {
    @GameTest(template="test_track",timeoutTicks=1800) public void allTwelveElectricLayoutsDriveBrakeAndKeepMechanicalState(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();CarEntity[] active={null};int[] ticks={0};float[] peak={0};double[] start={0};
        h.onEachTick(()->{
            int t=ticks[0]++,job=t/130,step=t%130;if(job>=12)return;
            if(step==0){
                if(active[0]!=null){p.stopRiding();active[0].discard();}
                var type=Powertrain.values()[job/3+1];var c=car(h,type,.6);active[0]=c;
                c.setDriveConfig(com.photonspark.sparkmotors.sim.DriveConfig.preset(com.photonspark.sparkmotors.sim.DriveConfig.Layout.values()[job%3]));
                p.moveTo(c.position());c.setOwner(p.getUUID());p.startRiding(c,true);c.action(p,CarPackets.IGNITION,0,0);start[0]=c.tractionBattery().energyJ();peak[0]=0;
            }
            var c=active[0];c.receiveInput(step<10?4:step<65?1:2,0);peak[0]=Math.max(peak[0],c.horizontalSpeed());
            if(step==128){
                h.assertTrue(peak[0]>3&&c.horizontalSpeed()<.3,"EV matrix drive/brake case "+job+" peak="+peak[0]+" speed="+c.horizontalSpeed());
                h.assertTrue(c.tractionBattery().energyJ()<start[0]&&!c.reverseSelected(),"Energy paid and forward selection retained "+job);
                var saved=new CompoundTag();c.saveWithoutId(saved);var old=c.mechanics();c.load(saved);
                h.assertTrue(c.mechanics().equals(old)&&c.driveConfig().layout().ordinal()==job%3,"Mechanical state and layout survive electric reload");
                System.out.println("ELECTRIC_LAYOUT_SERVER_PASS "+job);
                if(job==11){p.stopRiding();c.discard();System.out.println("ELECTRIC_SERVER_MATRIX_PASS 12");h.succeed();}
            }
        });
    }
    private CarEntity car(GameTestHelper h,Powertrain type,double soc){var car=AutoPropulsionAge.CAR.get().create(h.getLevel());var pos=h.absoluteVec(new Vec3(8,2.05,8));car.moveTo(pos.x,pos.y,pos.z,0,0);car.initializePowertrain(type,soc);h.getLevel().addFreshEntity(car);return car;}
    @GameTest(template="test_track") public void tractionPersistenceAndLegacyMigration(GameTestHelper h){
        var car=car(h,Powertrain.ELECTRIC_800,.321);var tag=new CompoundTag();car.saveWithoutId(tag);
        tag.putDouble("BatteryHealth",.72);tag.putDouble("BatteryC",42);tag.putDouble("BatteryThroughputJ",123456);tag.putDouble("MotorC",93);
        var loaded=AutoPropulsionAge.CAR.get().create(h.getLevel());loaded.load(tag);var roundtrip=new CompoundTag();loaded.saveWithoutId(roundtrip);
        h.assertTrue(loaded.powertrain()==Powertrain.ELECTRIC_800,"powertrain survives load");
        h.assertTrue(Math.abs(roundtrip.getDouble("BatteryHealth")-.72)<1e-10&&roundtrip.getDouble("BatteryC")==42,"no battery rejuvenation on reload");
        h.assertTrue(roundtrip.getDouble("BatteryThroughputJ")==123456&&roundtrip.getDouble("MotorC")==93,"wear/thermal state survives reload");
        h.assertTrue(!loaded.ignition()&&!loaded.plugged(),"READY and cable do not resurrect on reload");
        tag.remove("Powertrain");tag.putInt("DataVersion",3);loaded.load(tag);h.assertTrue(loaded.powertrain()==Powertrain.COMBUSTION,"old sedan migrates to combustion, never silently EV");h.succeed();
    }
    @GameTest(template="test_track") public void craftingConversionPreservesUsedBattery(GameTestHelper h){
        var item=Electrification.PACKS.get(Powertrain.ELECTRIC_400).get();
        var pack=item.write(new ItemStack(item),new BatteryModel.State(2_345_678,38,.73,123456));
        var donor=new ItemStack(AutoPropulsionAge.CAR_CRATE.get());
        var mechanical=com.photonspark.sparkmotors.sim.MechanicalState.legacy(com.photonspark.sparkmotors.sim.Assembly.stock(),com.photonspark.sparkmotors.sim.EnginePart.stock(),103,111,77);
        com.photonspark.sparkmotors.item.MechanicalData.set(donor,mechanical);
        var donorData=new CompoundTag();donorData.putInt("EngineParts",1234);
        donor.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(donorData));
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,java.util.List.of(
            new ItemStack(net.minecraft.world.item.Items.COPPER_BLOCK),new ItemStack(net.minecraft.world.item.Items.REDSTONE_BLOCK),new ItemStack(net.minecraft.world.item.Items.QUARTZ),
            ItemStack.EMPTY,donor,ItemStack.EMPTY,ItemStack.EMPTY,pack,ItemStack.EMPTY));
        var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
        var output=recipe.value().assemble(input,h.getLevel().registryAccess());
        h.assertTrue(output.is(Electrification.CRATES.get(Powertrain.ELECTRIC_400).get()),"conversion resolves the EV recipe");
        h.assertTrue(mechanical.equals(com.photonspark.sparkmotors.item.MechanicalData.get(output)),"typed component wear/serials and fluid quantities survive electric crafting");
        var data=output.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
        h.assertTrue(data.getInt("EngineParts")==1234,"donor parts preserved");
        var stored=item.read(pack);var nested=data.getCompound("TractionBattery");
        var saved=new ItemStack(item);saved.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(nested));
        h.assertTrue(item.read(saved).equals(stored),"crafting does not refill, repair, cool or reset battery cycles");h.succeed();
    }
    @GameTest(template="test_track",timeoutTicks=150) public void electricCarDrivesWithNoFuelAndBrakes(GameTestHelper h){
        var car=car(h,Powertrain.ELECTRIC_400,.5);var p=h.makeMockServerPlayerInLevel();p.moveTo(car.position());car.setOwner(p.getUUID());p.startRiding(car,true);car.action(p,CarPackets.IGNITION,0,0);
        double initial=car.tractionBattery().energyJ(),start=car.getZ();int[] tick={0};h.onEachTick(()->{tick[0]++;car.receiveInput(tick[0]<45?1:2,0);});
        h.runAfterDelay(40,()->{h.assertTrue(car.getZ()>start+2&&car.speed()>2,"EV moves under actual server tick");h.assertTrue(car.fuel()==0&&car.rpm()==0,"BEV has no fuel consumption or combustion crank RPM");});
        h.runAfterDelay(115,()->{h.assertTrue(Math.abs(car.speed())<.15,"friction brakes stop EV");h.assertTrue(car.tractionBattery().energyJ()<initial,"acceleration/braking round trip cannot create battery energy");p.stopRiding();h.succeed();});
    }
    @GameTest(template="test_track") public void cableOwnershipAndReadyInterlock(GameTestHelper h){
        var car=car(h,Powertrain.ELECTRIC_400,.3);var p=h.makeMockServerPlayerInLevel();p.moveTo(car.position());car.setOwner(p.getUUID());
        var pos=h.absolutePos(new BlockPos(11,2,8));h.getLevel().setBlock(pos,Electrification.CHARGERS.get(ChargingModel.Tier.RAPID).get().defaultBlockState(),3);
        var charger=(ChargerBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(charger.connect(p,car),"owned EV connects");car.action(p,CarPackets.IGNITION,0,0);h.assertTrue(!car.ignition(),"connected cable blocks READY");
        var other=car(h,Powertrain.PLUG_IN_HYBRID,.2);other.setOwner(p.getUUID());h.assertTrue(!charger.connect(p,other),"occupied charger cannot transfer to another vehicle");
        charger.disconnect();h.assertTrue(!car.plugged(),"disconnect releases car immediately");car.tickCount+=4;car.action(p,CarPackets.IGNITION,0,0);h.assertTrue(car.ignition(),"unplugged EV can select READY");
        h.assertTrue(!charger.connect(p,car),"READY vehicle cannot charge");
        var hybrid=car(h,Powertrain.HYBRID,.3);hybrid.setOwner(p.getUUID());h.assertTrue(!charger.connect(p,hybrid),"non-plug-in hybrid rejects cable");h.succeed();
    }
    @GameTest(template="test_track") public void packSwapPreservesEnergyAndCondition(GameTestHelper h){
        var car=car(h,Powertrain.ELECTRIC_400,.3);var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);p.moveTo(car.position());car.setOwner(p.getUUID());
        var item=Electrification.PACKS.get(Powertrain.ELECTRIC_400).get();var replacement=new BatteryModel.State(1_000_000,35,.7,9876);p.getInventory().add(item.write(new ItemStack(item),replacement));
        double oldJ=car.tractionBattery().energyJ();car.action(p,CarPackets.HOOD,0,0);car.hoodProgress=1;car.tickCount+=4;car.action(p,CarPackets.REPLACE_BATTERY,0,0);
        h.assertTrue(car.tractionBattery().energyJ()==replacement.energyJ()&&car.packHealth()<.701,"installed used pack retains energy and wear");
        h.assertTrue(p.getInventory().countItem(item)==1,"swap returns exactly one removed pack");
        var removed=p.getInventory().items.stream().filter(s->s.is(item)).findFirst().orElseThrow();h.assertTrue(item.read(removed).energyJ()==oldJ,"removed pack retains its charge");h.succeed();
    }
    @GameTest(template="test_track",timeoutTicks=100) public void creativeChargeFixtureAndUnloadCleanup(GameTestHelper h){
        var car=car(h,Powertrain.PLUG_IN_HYBRID,.2);var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.CREATIVE);p.moveTo(car.position());car.setOwner(p.getUUID());
        var pos=h.absolutePos(new BlockPos(11,2,8));h.getLevel().setBlock(pos,Electrification.CHARGERS.get(ChargingModel.Tier.HOME).get().defaultBlockState(),3);
        var charger=(ChargerBlockEntity)h.getLevel().getBlockEntity(pos);p.setShiftKeyDown(true);charger.interact(p);p.setShiftKeyDown(false);h.assertTrue(charger.connect(p,car),"fixture pairs");double before=car.tractionBattery().energyJ();
        h.runAfterDelay(45,()->{h.assertTrue(car.tractionBattery().energyJ()>before&&charger.deliveredJ()>0,"block-entity charger advances battery and meters input");h.getLevel().removeBlock(pos,false);h.assertTrue(!car.plugged(),"breaking charger clears cable and graph load");h.succeed();});
    }
}
