package com.photonspark.sparkmotors.gametest;

import com.mojang.authlib.GameProfile;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.item.*;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.VehicleCondition.Part;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class DamageGameTests {
    private CarEntity car(GameTestHelper h){var c=AutoPropulsionAge.CAR.get().create(h.getLevel());var p=h.absoluteVec(new Vec3(8,2.05,8));c.moveTo(p.x,p.y,p.z,0,0);h.getLevel().addFreshEntity(c);return c;}
    private ServerPlayer owner(GameTestHelper h,CarEntity c){var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"damage_mechanic"));p.setGameMode(GameType.SURVIVAL);p.moveTo(c.position().add(2,0,0));c.setOwner(p.getUUID());return p;}
    private void act(CarEntity c,ServerPlayer p,int action,int a,int b){c.tickCount+=4;c.action(p,action,a,b);}
    private void open(CarEntity c,ServerPlayer p){act(c,p,CarPackets.HOOD,0,0);c.hoodProgress=1;}
    private void near(GameTestHelper h,double a,double b,String message){h.assertTrue(Math.abs(a-b)<.001,message+": "+a+" vs "+b);}
    @GameTest(template="test_track") public void allComponentStateSurvivesEntityAndItemSerialization(GameTestHelper h){
        var c=car(h);for(var part:Part.values())c.condition().restore(part,part.ordinal()*.7,part.ordinal()*.9);c.syncCondition();
        var tag=new CompoundTag();c.saveWithoutId(tag);var restored=car(h);restored.load(tag);
        for(var part:Part.values()){near(h,restored.condition().state(part).wear(),c.condition().state(part).wear(),"Wear round trip "+part);near(h,restored.condition().state(part).damage(),c.condition().state(part).damage(),"Damage round trip "+part);}
        var item=ConditionData.toItem(new ItemStack(AutoPropulsionAge.engineItem(EngineFamily.I4,1)),c.condition(),p->p.assembly==Assembly.ENGINE);
        var parsed=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)item.save(h.getLevel().registryAccess()));
        near(h,ConditionData.fromItem(parsed).health(Part.INTERNALS),c.condition().health(Part.INTERNALS),"Engine item keeps internal condition");h.succeed();
    }
    @GameTest(template="test_track") public void legacyHealthAndMufflerMigrateWithoutHealingWrecks(GameTestHelper h){
        var c=car(h);var old=new CompoundTag();old.putInt("DataVersion",2);old.putInt("Assemblies",Assembly.EXHAUST.with(Assembly.stock(),0));old.putFloat("Health",0);c.load(old);
        h.assertTrue(Assembly.EXHAUST.variant(c.config())==1,"Legacy cars receive a stock muffler");near(h,c.health(),0,"A legacy wreck stays wrecked");
        h.assertTrue(!c.engineProblem().isEmpty(),"Wrecked engine cannot start");
        c.setConfiguration(Assembly.EXHAUST.with(c.config(),0));var saved=new CompoundTag();c.saveWithoutId(saved);c.load(saved);
        h.assertTrue(Assembly.EXHAUST.variant(c.config())==0,"Version 3 deliberately open exhaust stays open");h.succeed();
    }
    @GameTest(template="test_track") public void selectiveRepairsEnforceCostsOwnershipAndHoodAccess(GameTestHelper h){
        var c=car(h);var p=owner(h,c);c.condition().damage(Part.TIRE_FL,65);c.condition().damage(Part.COOLING,45);c.syncCondition();
        act(c,p,CarPackets.REPAIR_PART,Part.TIRE_FL.ordinal(),0);near(h,c.condition().health(Part.TIRE_FL),35,"No free repairs");
        p.getInventory().add(new ItemStack(Items.IRON_INGOT,10));act(c,p,CarPackets.REPAIR_PART,Part.TIRE_FL.ordinal(),0);
        near(h,c.condition().health(Part.TIRE_FL),100,"Selected tire repaired");near(h,c.condition().health(Part.COOLING),55,"Other components untouched");
        h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT)==9,"Only selected part cost consumed");
        act(c,p,CarPackets.REPAIR_PART,Part.COOLING.ordinal(),0);h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT)==9,"Closed hood rejects engine repair without consuming iron");
        open(c,p);var stranger=owner(h,c);c.setOwner(p.getUUID());stranger.getInventory().add(new ItemStack(Items.IRON_INGOT,64));act(c,stranger,CarPackets.REPAIR_PART,Part.COOLING.ordinal(),0);
        near(h,c.condition().health(Part.COOLING),55,"Non-owner cannot repair");
        act(c,p,CarPackets.REPAIR_PART,999,0);act(c,p,CarPackets.REPAIR_PART,Part.COOLING.ordinal(),0);near(h,c.condition().health(Part.COOLING),100,"Open hood allows selected repair");
        h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT)==6,"Cooling repair costs three iron");h.succeed();
    }
    @GameTest(template="test_track") public void wheelSwapRoundTripAndSameGradeReplacementPreserveCondition(GameTestHelper h){
        var c=car(h);var p=owner(h,c);c.condition().restore(Part.TIRE_FL,22,31);c.condition().damage(Part.TIRE_RR,80);c.condition().damage(Part.TRANSMISSION,42);c.syncCondition();
        act(c,p,CarPackets.INSTALL,Assembly.WHEELS.ordinal(),0);var wheel=AutoPropulsionAge.partItem(Assembly.WHEELS,1);
        var removed=p.getInventory().items.stream().filter(s->s.is(wheel)).findFirst().orElseThrow();near(h,ConditionData.fromItem(removed).health(Part.TIRE_FL),47,"Removed wheel set keeps each corner");
        act(c,p,CarPackets.INSTALL,Assembly.WHEELS.ordinal(),1);near(h,c.condition().health(Part.TIRE_RR),20,"Refitting does not heal");
        p.getInventory().add(new ItemStack(wheel));act(c,p,CarPackets.INSTALL,Assembly.WHEELS.ordinal(),1);near(h,c.condition().health(Part.TIRE_FL),100,"Same grade NEW part can replace damaged one");
        near(h,c.condition().health(Part.TRANSMISSION),58,"Wheel replacement cannot repair transmission");h.assertTrue(p.getInventory().countItem(wheel)==1,"One worn set returned, one fresh set consumed");h.succeed();
    }
    @GameTest(template="test_track") public void engineAndServicePartSwapsKeepWearWithoutHealingOtherParts(GameTestHelper h){
        var c=car(h);var p=owner(h,c);open(c,p);c.condition().restore(Part.INTAKE,10,55);c.condition().restore(Part.ENGINE_BLOCK,15,40);c.syncCondition();
        act(c,p,CarPackets.ENGINE_PART,EnginePart.INTAKE.ordinal(),0);act(c,p,CarPackets.ENGINE_PART,EnginePart.INTAKE.ordinal(),1);near(h,c.condition().health(Part.INTAKE),35,"Service part round trip retains condition");
        act(c,p,CarPackets.INSTALL,Assembly.ENGINE.ordinal(),0);act(c,p,CarPackets.ENGINE_SWAP,EngineFamily.I4.ordinal(),1);
        near(h,c.condition().health(Part.ENGINE_BLOCK),45,"Whole engine round trip retains block condition");near(h,c.condition().health(Part.INTAKE),35,"Whole engine retains installed service condition");
        c.condition().damage(Part.INTERNALS,100);c.syncCondition();act(c,p,CarPackets.IGNITION,0,0);h.assertTrue(!c.ignition(),"Failed internals prevent starting");h.succeed();
    }
    @GameTest(template="test_track") public void upgradeCraftingPreservesEveryDonorPartCondition(GameTestHelper h){
        int checked=0;
        for(String id:new String[]{"sport_wheels","sport_brakes","sport_suspension","sport_transmission","sport_body","sport_exhaust","performance_intake","performance_ignition","performance_cooling","performance_fuel_system","performance_internals","sport_engine"}){
            var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)h.getLevel().getRecipeManager().byKey(AutoPropulsionAge.id(id)).orElseThrow().value();
            var stacks=new ArrayList<ItemStack>();var condition=new VehicleCondition();for(var part:Part.values())condition.restore(part,13,24);
            for(var ingredient:recipe.getIngredients()){
                var stack=ingredient.isEmpty()?ItemStack.EMPTY:ingredient.getItems()[0].copy();
                if(AutoPropulsionAge.PART_ITEMS.values().stream().anyMatch(item->stack.is(item.get())))ConditionData.toItem(stack,condition,p->true);
                stacks.add(stack);
            }
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,stacks);h.assertTrue(recipe.matches(input,h.getLevel()),"Upgrade recipe matches "+id);
            var result=recipe.assemble(input,h.getLevel().registryAccess());near(h,ConditionData.fromItem(result).health(Part.TIRE_FL),63,"Crafting retains donor condition "+id);checked++;
        }
        h.assertTrue(checked==12,"All non-family upgrade routes checked");h.succeed();
    }
    @GameTest(template="test_track") public void allSoundEventsAreRegisteredOnDedicatedServer(GameTestHelper h){
        for(String sound:VehicleAudio.events())h.assertTrue(com.photonspark.sparkmotors.sound.VehicleSounds.get(sound)!=null,"Sound registered "+sound);
        h.assertTrue(VehicleAudio.events().size()==58,"58 sound events");h.succeed();
    }
}
