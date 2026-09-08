package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.item.EngineItem;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class PowertrainGameTests {
    private CarEntity car(GameTestHelper h){var c=AutoPropulsionAge.CAR.get().create(h.getLevel());var p=h.absoluteVec(new Vec3(8,2.05,8));c.moveTo(p.x,p.y,p.z,0,0);h.getLevel().addFreshEntity(c);return c;}
    private ServerPlayer owner(GameTestHelper h,CarEntity c){var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"powertrain_mechanic"));p.moveTo(c.position());c.setOwner(p.getUUID());return p;}
    private void act(CarEntity c,ServerPlayer p,int action,int a,int b){c.tickCount+=4;c.action(p,action,a,b);}
    @GameTest(template="test_track") public void everyHardwareItemCanBeCraftedAndInstalledWithoutDuplication(GameTestHelper h){
        var c=car(h);var p=owner(h,c);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);int count=0;
        for(var slot:EnginePart.values())for(int v=1;v<=slot.maxVariant();v++){
            var tag=new CompoundTag();c.saveWithoutId(tag);tag.remove("Mechanics");tag.putInt("EngineParts",slot.with(EnginePart.boosted(3),0));tag.putBoolean("HoodOpen",true);
            // Test each supporting choice on a natural build so high-compression internals remain valid.
            if(slot!=EnginePart.INDUCTION)tag.putInt("EngineParts",slot.with(EnginePart.stock(),0));c.load(tag);
            var item=AutoPropulsionAge.enginePartItem(slot,v);
            var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)h.getLevel().getRecipeManager().byKey(AutoPropulsionAge.id(slot.itemName(v))).orElseThrow().value();
            var ingredients=new java.util.ArrayList<ItemStack>();for(var i:recipe.getIngredients())ingredients.add(i.isEmpty()?ItemStack.EMPTY:i.getItems()[0].copy());
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,ingredients);h.assertTrue(recipe.matches(input,h.getLevel()),"Recipe must match "+slot.itemName(v));
            var output=recipe.assemble(input,h.getLevel().registryAccess());h.assertTrue(output.is(item)&&output.getCount()==1,"Recipe returns exactly the catalog item");
            p.getInventory().clearContent();p.getInventory().add(output);act(c,p,CarPackets.ENGINE_PART,slot.ordinal(),v);
            h.assertTrue(slot.variant(c.engineParts())==v&&p.getInventory().countItem(item)==0,"Install consumes one "+slot.itemName(v));
            act(c,p,CarPackets.ENGINE_PART,slot.ordinal(),0);h.assertTrue(p.getInventory().countItem(item)==1,"Remove returns one "+slot.itemName(v));count++;
        }
        h.assertTrue(count==42,"All 42 registered hardware choices tested");h.succeed();
    }
    @GameTest(template="test_track") public void alpha02CarsAndEngineItemsMigrateEveryServiceSlot(GameTestHelper h){
        var c=car(h);int old=(2)|(2<<2)|(1<<4)|(2<<6)|(2<<8)|(1<<10);
        var tag=new CompoundTag();tag.putInt("DataVersion",2);tag.putInt("Assemblies",Assembly.stock());tag.putInt("EngineParts",old);tag.putFloat("EngineTemperature",108);c.load(tag);
        h.assertTrue(c.engineParts()==EnginePart.fromLegacy(old)&&c.temperature()==108,"Version 2 cars retain the original six slots and heat");
        var item=new ItemStack(AutoPropulsionAge.engineItem(EngineFamily.I4,1));var data=new CompoundTag();data.putInt("EngineDataVersion",1);data.putInt("EngineParts",old);
        item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(data));
        h.assertTrue(EngineItem.parts(item)==c.engineParts(),"Legacy traded engines migrate identically");h.succeed();
    }
    @GameTest(template="test_track") public void boostTuneAndRebuildAreBoundedAndPreserved(GameTestHelper h){
        var c=car(h);var p=owner(h,c);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var tag=new CompoundTag();c.saveWithoutId(tag);tag.remove("Mechanics");tag.putFloat("EngineHealth",3);tag.putFloat("OilTemperature",112);tag.putBoolean("HoodOpen",true);c.load(tag);
        act(c,p,CarPackets.IGNITION,0,0);h.assertTrue(!c.ignition(),"Worn out engine refuses to start");
        act(c,p,CarPackets.ENGINE_REBUILD,0,0);h.assertTrue(c.engineHealth()==3,"Cannot rebuild for free");
        p.getInventory().add(new ItemStack(Items.IRON_INGOT,12));act(c,p,CarPackets.ENGINE_REBUILD,0,0);
        h.assertTrue(c.engineHealth()==100&&c.oilTemperature()==112&&p.getInventory().countItem(Items.IRON_INGOT)==0,"Rebuild consumes exact materials and retains oil heat");
        act(c,p,CarPackets.BOOST_TUNE,Integer.MAX_VALUE,0);h.assertTrue(Math.abs(c.boostTarget()-1.4)<.001,"Clamp maximum boost target");
        act(c,p,CarPackets.BOOST_TUNE,Integer.MIN_VALUE,0);h.assertTrue(Math.abs(c.boostTarget()-.2)<.001,"Clamp minimum boost target");
        c.saveWithoutId(tag);var copy=AutoPropulsionAge.CAR.get().create(h.getLevel());copy.load(tag);h.assertTrue(copy.boostTarget()==c.boostTarget()&&copy.engineHealth()==100,"Tune and engine condition persist");h.succeed();
    }
    @GameTest(template="test_track",timeoutTicks=180) public void clutchFreeRevsAndReconnectsTheRealDriveline(GameTestHelper h){
        var c=car(h);var p=h.makeMockServerPlayerInLevel();p.moveTo(c.position());c.setOwner(p.getUUID());p.startRiding(c,true);act(c,p,CarPackets.IGNITION,0,0);int[] ticks={0};
        h.onEachTick(()->{ticks[0]++;c.receiveInput(ticks[0]<40?17:ticks[0]<90?1:2,0);});
        h.runAfterDelay(35,()->{h.assertTrue(c.rpm()>4000&&Math.abs(c.speed())<.1,"C+W must free-rev the crank without propelling the car");h.assertTrue(c.oilPressure()>1,"Running oil pressure is synchronized");});
        h.runAfterDelay(85,()->h.assertTrue(c.speed()>5,"Releasing clutch reconnects the drivetrain"));
        h.runAfterDelay(160,()->{h.assertTrue(Math.abs(c.speed())<.3&&c.fuel()<40,"Brakes stop the new drivetrain");p.stopRiding();h.succeed();});
    }
    @GameTest(template="test_track",timeoutTicks=180) public void workshopRevTestTimesOutAndKeepsTheCarParked(GameTestHelper h){
        var c=car(h);var p=owner(h,c);act(c,p,CarPackets.HOOD,0,0);c.hoodProgress=1;act(c,p,CarPackets.IGNITION,0,0);h.runAfterDelay(15,()->act(c,p,CarPackets.REV_TEST,0,0));
        double start=c.getZ();
        h.runAfterDelay(45,()->h.assertTrue(c.rpm()>3000&&Math.abs(c.speed())<.01,"Bench test revs a parked, unoccupied engine"));
        h.runAfterDelay(150,()->{h.assertTrue(c.throttle()<.01&&Math.abs(c.speed())<.01&&Math.abs(c.getZ()-start)<.01,"Bench timer releases throttle without rolling the car");h.succeed();});
    }
}
