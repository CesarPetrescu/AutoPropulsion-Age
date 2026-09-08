package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.item.*;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class MechanicalGameTests {
    @GameTest(template="test_track") public void fullMechanicalSnapshotIsBoundedAndRoundTrips(GameTestHelper h){
        var state=MechanicalState.legacy(Assembly.stock(),EnginePart.boosted(1),85,90,100);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            buffer.writeNbt(MechanicalData.write(state));int bytes=buffer.readableBytes();
            h.assertTrue(bytes<20000,"Full tracked component snapshot stays below 20 KB");
            h.assertTrue(MechanicalData.read(buffer.readNbt()).equals(state),"Full tracked snapshot round trips exactly");
            String report="{\"scope\":\"One healthy boosted car's serialized mechanical NBT; excludes other entity packets and compression\",\"parts\":"+state.parts().size()+",\"snapshotBytes\":"+bytes+",\"periodicSnapshotsPerSecond\":1}";
            var out=java.nio.file.Path.of("mechanics-network-size.json");java.nio.file.Files.writeString(out,report);System.out.println("MECHANICS_NETWORK_SIZE "+report);h.succeed();
        }catch(java.io.IOException ex){throw new RuntimeException(ex);}finally{buffer.release();}
    }
    private CarEntity car(GameTestHelper h){var c=AutoPropulsionAge.CAR.get().create(h.getLevel());var p=h.absoluteVec(new Vec3(8,2.05,8));c.moveTo(p.x,p.y,p.z,0,0);h.getLevel().addFreshEntity(c);return c;}
    private ServerPlayer owner(GameTestHelper h,CarEntity c){var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"component_mechanic"));p.moveTo(c.position());p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);c.setOwner(p.getUUID());return p;}
    private void act(CarEntity c,ServerPlayer p,int action,int a,int b){c.tickCount+=4;c.action(p,action,a,b);}
    private void hood(CarEntity c,ServerPlayer p){act(c,p,CarPackets.HOOD,0,0);c.hoodProgress=1;}
    @GameTest(template="test_track") public void wornHoseRoundTripsItemNetworkSaveAndAnotherCar(GameTestHelper h){
        var c=car(h);var p=owner(h,c);hood(c,p);var slot=ComponentSlot.byKey("cooling.upper_hose");int index=ComponentSlot.ALL.indexOf(slot);
        var used=c.mechanics().get(slot.key()).condition(.42,.65,PartInstance.LEAK);c.setMechanics(c.mechanics().with(slot.key(),used).fluids(2.4,3.2,.7));
        act(c,p,CarPackets.COMPONENT_SWAP,index,0);h.assertTrue(c.mechanics().get(slot.key())==null,"Removed actual hose mount is empty");
        var stack=p.getInventory().items.stream().filter(s->s.is(AutoPropulsionAge.PART_ITEMS.get(slot.item()).get())).findFirst().orElseThrow();
        var saved=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)stack.save(h.getLevel().registryAccess()));
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{AutoPropulsionAge.MECHANICAL_DATA.get().streamCodec().encode(buffer,MechanicalData.get(saved));var decoded=AutoPropulsionAge.MECHANICAL_DATA.get().streamCodec().decode(buffer);h.assertTrue(decoded.parts().get(slot.key()).equals(used),"Typed persistent and network codecs retain every field");}finally{buffer.release();}
        act(c,p,CarPackets.COMPONENT_SWAP,index,1);h.assertTrue(c.mechanics().get(slot.key()).equals(used),"Reinstalled hose retains identity, wear and leak");
        h.assertTrue(c.mechanics().coolant()==2.4&&c.mechanics().oil()==3.2,"Part work cannot refill fluids");
        var m=c.mechanics();m=m.with("wheel.fl.disc",m.get("wheel.fl.disc").operating(0,315)).with("driveline.clutch",m.get("driveline.clutch").operating(0,190)).with("wheel.fl.tire",m.get("wheel.fl.tire").operating(2.3,75));c.setMechanics(m);
        var tag=new CompoundTag();c.saveWithoutId(tag);var copy=AutoPropulsionAge.CAR.get().create(h.getLevel());copy.load(tag);h.assertTrue(copy.mechanics().equals(c.mechanics()),"Vehicle save preserves full component state");copy.setUUID(UUID.randomUUID());h.assertTrue(h.getLevel().addFreshEntity(copy),"Reload fixture joins the ticking level");
        h.runAfterDelay(3,()->{h.assertTrue(copy.tickCount>0,"Reload fixture actually simulated ticks");h.assertTrue(copy.mechanics().get("wheel.fl.disc").temperature()>300&&copy.mechanics().get("driveline.clutch").temperature()>180&&copy.mechanics().get("wheel.fl.tire").temperature()>70,"Resumed simulation does not instantly cool stored brakes, clutch or tires");h.succeed();});
    }
    @GameTest(template="test_track") public void wheelAssembliesAndCraftingCannotRefreshUsedTires(GameTestHelper h){
        var c=car(h);var p=owner(h,c);String key="wheel.fl.tire";var used=c.mechanics().get(key).condition(.8,.2,PartInstance.LEAK).operating(1.1,67);c.setMechanics(c.mechanics().with(key,used));
        act(c,p,CarPackets.INSTALL,Assembly.WHEELS.ordinal(),0);
        var stack=p.getInventory().items.stream().filter(s->s.is(AutoPropulsionAge.partItem(Assembly.WHEELS,1))).findFirst().orElseThrow();
        h.assertTrue(MechanicalData.get(stack).get(key).equals(used),"Wheel set carries each corner's state");
        var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)h.getLevel().getRecipeManager().byKey(AutoPropulsionAge.id("sport_wheels")).orElseThrow().value();
        var ingredients=new ArrayList<ItemStack>();for(var ingredient:recipe.getIngredients())ingredients.add(ingredient.isEmpty()?ItemStack.EMPTY:ingredient.test(stack)?stack.copy():ingredient.getItems()[0].copy());
        var result=recipe.assemble(net.minecraft.world.item.crafting.CraftingInput.of(3,3,ingredients),h.getLevel().registryAccess());
        h.assertTrue(MechanicalData.get(result).get(key).equals(used),"Sport assembly crafting retains tire state");
        p.getInventory().clearContent();p.getInventory().add(result);act(c,p,CarPackets.INSTALL,Assembly.WHEELS.ordinal(),2);
        h.assertTrue(c.mechanics().get(key).equals(used)&&p.getInventory().countItem(AutoPropulsionAge.partItem(Assembly.WHEELS,2))==0,"Used assembly reinstall consumes exactly one");h.succeed();
    }
    @GameTest(template="test_track") public void engineConversionRetainsIndividualDamageAndFluids(GameTestHelper h){
        var c=car(h);var p=owner(h,c);hood(c,p);var used=c.mechanics().get("oil.feed").damage(.8,PartInstance.LEAK);c.setMechanics(c.mechanics().with("oil.feed",used).fluids(1.3,2.7,1));
        act(c,p,CarPackets.INSTALL,Assembly.ENGINE.ordinal(),0);
        var engine=p.getInventory().items.stream().filter(s->s.getItem() instanceof EngineItem).findFirst().orElseThrow();
        for(String recipeId:List.of("stock_v6_engine","stock_flat4_engine","stock_rotor4_engine","sedan_crate")){
            var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)h.getLevel().getRecipeManager().byKey(AutoPropulsionAge.id(recipeId)).orElseThrow().value();
            var ingredients=new ArrayList<ItemStack>();for(var i:recipe.getIngredients())ingredients.add(i.isEmpty()?ItemStack.EMPTY:i.test(engine)?engine.copy():i.getItems()[0].copy());
            var result=recipe.assemble(net.minecraft.world.item.crafting.CraftingInput.of(3,3,ingredients),h.getLevel().registryAccess());var state=MechanicalData.get(result);
            h.assertTrue(state.get("oil.feed").equals(used)&&state.oil()==2.7&&state.coolant()==1.3,"Conversion retains used components and conserved fluids: "+recipeId);
        }
        act(c,p,CarPackets.ENGINE_SWAP,0,1);h.assertTrue(c.mechanics().get("oil.feed").equals(used),"Refitted engine retains damaged oil supply");h.succeed();
    }
    @GameTest(template="test_track") public void legacyMigrationCreatesDistinctCornersOnceAndServiceChecksOwnership(GameTestHelper h){
        var c=car(h);var p=owner(h,c);var tag=new CompoundTag();tag.putInt("DataVersion",3);tag.putInt("Assemblies",Assembly.stock());tag.putInt("EngineParts",EnginePart.stock());tag.putFloat("EngineHealth",37);c.load(tag);c.setOwner(p.getUUID());
        h.assertTrue(c.mechanics().get("engine.internals").damage()==.63,"Legacy permanent engine damage migrates");
        var ids=new HashSet<UUID>();for(var part:c.mechanics().parts().values())h.assertTrue(ids.add(part.id()),"Every legacy instance is distinct");
        int slot=ComponentSlot.ALL.indexOf(ComponentSlot.byKey("cooling.upper_hose"));var before=c.mechanics();
        act(c,p,CarPackets.COMPONENT_SWAP,slot,0);h.assertTrue(c.mechanics().equals(before),"Closed hood rejects service");hood(c,p);
        var stranger=owner(h,car(h));stranger.moveTo(c.position());act(c,stranger,CarPackets.COMPONENT_SWAP,slot,0);h.assertTrue(c.mechanics().equals(before),"Non-owner cannot remove components");h.succeed();
    }

    @GameTest(template="test_track",timeoutTicks=500) public void pressureTestRepairRefillAndVerificationRunOnServer(GameTestHelper h){
        var c=car(h);var p=owner(h,c);hood(c,p);p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get("pressure_tester").get()));
        c.impactComponents("front",18);var hit=c.mechanics();c.impactComponents("front",18);h.assertTrue(c.mechanics().equals(hit),"Repeated substeps cannot allocate the same impact twice");
        c.setMechanics(c.mechanics().fluids(2.4,5,1));act(c,p,CarPackets.DIAGNOSE,0,0);
        h.runAfterDelay(205,()->{
            h.assertTrue(c.diagnostic().contains("Pressure loss"),"Real timed pressure test detects leakage");
            double remaining=c.coolant();var internal=c.mechanics().get("engine.internals");var slot=ComponentSlot.byKey("cooling.upper_hose");
            p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get(slot.item()).get()));act(c,p,CarPackets.COMPONENT_SWAP,ComponentSlot.ALL.indexOf(slot),1);
            h.assertTrue(Math.abs(c.coolant()-remaining)<.001&&c.mechanics().get("engine.internals").equals(internal),"Targeted replacement does not refill or heal internals");
            double before=c.coolant();p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get("coolant_bottle").get()));act(c,p,CarPackets.FLUID_SERVICE,0,0);
            h.assertTrue(Math.abs(c.coolant()-before-1)<.001&&p.getInventory().countItem(Items.GLASS_BOTTLE)==1,"One litre transferred and one empty bottle returned");
            for(int bottle=0;bottle<6&&c.coolant()<8;bottle++){p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get("coolant_bottle").get()));act(c,p,CarPackets.FLUID_SERVICE,0,0);}
            h.assertTrue(c.coolant()==8,"Survival fluid service completes a full reservoir refill");
            act(c,p,CarPackets.DIAGNOSE,0,0);
        });
        h.runAfterDelay(415,()->{h.assertTrue(c.diagnostic().contains("Holds pressure"),"Actual ten-second verification test passes after replacing the cause");h.succeed();});
    }
    @GameTest(template="test_track") public void fluidBottleRemainderAndTireCornerTransferAreConserved(GameTestHelper h){
        var c=car(h);var p=owner(h,c);hood(c,p);c.setMechanics(c.mechanics().fluids(7.7,5,1));
        var bottle=AutoPropulsionAge.PART_ITEMS.get("coolant_bottle").get();p.getInventory().add(new ItemStack(bottle));act(c,p,CarPackets.FLUID_SERVICE,0,0);
        h.assertTrue(c.coolant()==8,"Reservoir stops at capacity");var remainder=p.getInventory().items.stream().filter(i->i.is(bottle)).findFirst().orElseThrow();h.assertTrue(Math.abs(MechanicalData.get(remainder).coolant()-.7)<.00001,"Unused 0.7 L stays in bottle");
        var from=ComponentSlot.byKey("wheel.fl.tire");var to=ComponentSlot.byKey("wheel.rr.tire");var used=c.mechanics().get(from.key()).condition(.7,.2,PartInstance.LEAK).operating(1.1,55);c.setMechanics(c.mechanics().with(from.key(),used));
        act(c,p,CarPackets.COMPONENT_SWAP,ComponentSlot.ALL.indexOf(from),0);h.assertTrue(c.mechanics().get(from.key())!=null,"Corner service requires raised jack");
        p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get("service_jack").get()));act(c,p,CarPackets.JACK,0,0);h.assertTrue(c.raised(),"Inventory jack physically raises the car");
        act(c,p,CarPackets.COMPONENT_SWAP,ComponentSlot.ALL.indexOf(from),0);act(c,p,CarPackets.COMPONENT_SWAP,ComponentSlot.ALL.indexOf(to),1);
        h.assertTrue(c.mechanics().get(to.key()).equals(used)&&c.mechanics().get(from.key())==null,"Same used tire can move across corners without refreshing pressure, wear or identity");h.succeed();
    }

    @GameTest(template="test_track",timeoutTicks=170) public void electricalAndOilFailuresReachTheRunningServerEngine(GameTestHelper h){
        var c=car(h);var p=owner(h,c);hood(c,p);
        var battery=c.mechanics().get("electrical.battery");c.setMechanics(c.mechanics().with("electrical.battery",battery.operating(0,20)));act(c,p,CarPackets.IGNITION,0,0);h.assertTrue(!c.ignition(),"Flat battery prevents actual starting");
        c.setMechanics(c.mechanics().with("electrical.battery",battery).with("oil.pump",null));act(c,p,CarPackets.IGNITION,0,0);
        h.runAfterDelay(18,()->{h.assertTrue(c.engineRunning(),"Engine starts with oil pump missing, allowing causal starvation");act(c,p,CarPackets.REV_TEST,0,0);});
        h.runAfterDelay(48,()->{h.assertTrue(c.oilPressure()==0&&c.mechanics().faultHistory().contains("OIL_PRESSURE_LOW"),"Actual engine measurements and warnings reflect missing pump");h.assertTrue(c.mechanics().get("engine.internals").wear()>0,"Running without oil pressure wears the actual internal assembly");act(c,p,CarPackets.IGNITION,0,0);});
        h.runAfterDelay(80,()->{
            var slot=ComponentSlot.byKey("oil.pump");p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get(slot.item()).get()));act(c,p,CarPackets.COMPONENT_SWAP,ComponentSlot.ALL.indexOf(slot),1);act(c,p,CarPackets.IGNITION,0,0);
        });
        h.runAfterDelay(110,()->{h.assertTrue(c.oilPressure()>.5,"Targeted oil-pump replacement restores measured pressure");h.assertTrue(c.mechanics().get("engine.internals").wear()>0,"Replacement retains pre-existing internal wear");act(c,p,CarPackets.IGNITION,0,0);});
        h.runAfterDelay(140,()->{
            double oil=c.oilQuantity();p.getInventory().add(new ItemStack(Items.IRON_INGOT,12));act(c,p,CarPackets.ENGINE_REBUILD,0,0);
            h.assertTrue(c.mechanics().get("engine.internals").wear()==0,"Rebuild repairs wear even when structural engine health is still 100 percent");
            h.assertTrue(c.oilQuantity()==oil&&p.getInventory().countItem(Items.IRON_INGOT)==0,"Internal rebuild consumes its materials and retains oil quantity");h.succeed();
        });
    }
}
