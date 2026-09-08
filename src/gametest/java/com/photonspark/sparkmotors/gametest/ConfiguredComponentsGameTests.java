package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.charging.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.item.*;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class ConfiguredComponentsGameTests {
    private CarEntity car(GameTestHelper h,Powertrain type){var c=AutoPropulsionAge.CAR.get().create(h.getLevel());var pos=h.absoluteVec(new Vec3(8,2.05,8));c.moveTo(pos.x,pos.y,pos.z,0,0);c.initializePowertrain(type,.5);h.getLevel().addFreshEntity(c);return c;}
    private ServerPlayer owner(GameTestHelper h,CarEntity c){var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"configured_mechanic"));p.moveTo(c.position());p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);c.setOwner(p.getUUID());return p;}
    private void act(CarEntity c,ServerPlayer p,int action,int a,int b){c.tickCount+=4;c.action(p,action,a,b);}
    @GameTest(template="test_track") public void usedMotorTransferAndMissingMountPersist(GameTestHelper h){
        var c=car(h,Powertrain.ELECTRIC_400);var p=owner(h,c);String key="traction.motor_rear";var slot=ComponentSlot.byKey(key);int index=ComponentSlot.ALL.indexOf(slot);
        var used=c.mechanics().get(key).condition(.43,.12,0).operating(0,137);c.setMechanics(c.mechanics().with(key,used));
        p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get("service_jack").get()));act(c,p,CarPackets.JACK,0,0);act(c,p,CarPackets.COMPONENT_SWAP,index,0);
        h.assertTrue(c.mechanics().get(key)==null&&PowertrainTopology.availability(c.mechanics(),c.powertrain(),c.driveConfig())==0,"Removing the rear motor interrupts the actual RWD torque path");
        var saved=new CompoundTag();c.saveWithoutId(saved);c.load(saved);h.assertTrue(c.mechanics().get(key)==null,"Version 2 reload must not manufacture a removed motor");
        var stack=p.getInventory().items.stream().filter(s->s.is(AutoPropulsionAge.PART_ITEMS.get(slot.item()).get())).findFirst().orElseThrow();
        var decoded=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)stack.save(h.getLevel().registryAccess()));h.assertTrue(MechanicalData.part(decoded,slot).equals(used),"Used motor identity, heat and damage survive native item storage");
        act(c,p,CarPackets.COMPONENT_SWAP,index,1);h.assertTrue(c.mechanics().get(key).equals(used)&&PowertrainTopology.availability(c.mechanics(),c.powertrain(),c.driveConfig())>0,"Reinstallation restores only the used motor capability");
        System.out.println("CONFIGURED_COMPONENTS_SERVER_PASS motor_transfer");h.succeed();
    }
    @GameTest(template="test_track") public void internalAssemblyCarriesItsIndividualUsedParts(GameTestHelper h){
        var c=car(h,Powertrain.COMBUSTION);var p=owner(h,c);act(c,p,CarPackets.HOOD,0,0);c.hoodProgress=1;
        var used=c.mechanics().get("cylinder.2.rings").condition(.65,.32,PartInstance.LEAK);c.setMechanics(c.mechanics().with("cylinder.2.rings",used).with("cylinder.3.valves",null));
        double compression=InternalMechanics.compression(c.mechanics(),c.engineFamily(),2);
        act(c,p,CarPackets.ENGINE_PART,EnginePart.INTERNALS.ordinal(),0);
        h.assertTrue(c.mechanics().get("cylinder.2.rings")==null,"Removing internal assembly removes its real child parts");
        var stack=p.getInventory().items.stream().filter(s->s.is(AutoPropulsionAge.enginePartItem(EnginePart.INTERNALS,1))).findFirst().orElseThrow();
        h.assertTrue(MechanicalData.get(stack).get("cylinder.2.rings").equals(used)&&MechanicalData.get(stack).get("cylinder.3.valves")==null,"Used internal kit stores missing as well as damaged pieces");
        act(c,p,CarPackets.ENGINE_PART,EnginePart.INTERNALS.ordinal(),1);
        h.assertTrue(c.mechanics().get("cylinder.2.rings").equals(used)&&c.mechanics().get("cylinder.3.valves")==null,"Internal reinstallation cannot heal or fill a child slot");
        h.assertTrue(InternalMechanics.compression(c.mechanics(),c.engineFamily(),2)==compression,"Same rings produce same compression after transfer");
        act(c,p,CarPackets.ENGINE_PART,EnginePart.INTERNALS.ordinal(),0);
        var donor=p.getInventory().items.stream().filter(s->s.is(AutoPropulsionAge.enginePartItem(EnginePart.INTERNALS,1))).findFirst().orElseThrow();
        var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)h.getLevel().getRecipeManager().byKey(AutoPropulsionAge.id("performance_internals")).orElseThrow().value();
        var ingredients=new ArrayList<ItemStack>();for(var ingredient:recipe.getIngredients())ingredients.add(ingredient.isEmpty()?ItemStack.EMPTY:ingredient.test(donor)?donor.copy():ingredient.getItems()[0].copy());
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,ingredients);h.assertTrue(recipe.matches(input,h.getLevel()),"Used assembly matches the real upgrade recipe");
        var upgraded=recipe.assemble(input,h.getLevel().registryAccess());p.getInventory().clearContent();p.getInventory().add(upgraded);
        act(c,p,CarPackets.ENGINE_PART,EnginePart.INTERNALS.ordinal(),2);
        h.assertTrue(c.mechanics().get("engine.internals").item().equals("performance_internals")&&c.mechanics().get("cylinder.2.rings").equals(used)&&c.mechanics().get("cylinder.3.valves")==null,"Upgrade changes the root specification while preserving damaged and missing child parts");
        System.out.println("CONFIGURED_COMPONENTS_SERVER_PASS internals_transfer");h.succeed();
    }
    @GameTest(template="test_track") public void openHvCircuitRejectsChargingBeforeEnergyIsDebited(GameTestHelper h){
        var c=car(h,Powertrain.ELECTRIC_400);var p=owner(h,c);var pos=h.absolutePos(new net.minecraft.core.BlockPos(11,2,8));
        h.getLevel().setBlock(pos,Electrification.CHARGERS.get(ChargingModel.Tier.RAPID).get().defaultBlockState(),3);var charger=(ChargerBlockEntity)h.getLevel().getBlockEntity(pos);
        double stored=c.tractionBattery().energyJ();var contactor=c.mechanics().get("traction.contactor");c.setMechanics(c.mechanics().with("traction.contactor",null));
        h.assertTrue(!charger.connect(p,c)&&!c.plugged()&&charger.deliveredJ()==0&&c.tractionBattery().energyJ()==stored,"Open contactor blocks connection without taking energy");
        act(c,p,CarPackets.IGNITION,0,0);h.assertTrue(!c.ignition(),"Open contactor also blocks READY");
        c.setMechanics(c.mechanics().with("traction.contactor",contactor));h.assertTrue(charger.connect(p,c),"Targeted contactor repair restores the charge path");
        charger.disconnect();act(c,p,CarPackets.HOOD,0,0);c.hoodProgress=1;double coolant=c.coolant();
        int radiator=ComponentSlot.ALL.indexOf(ComponentSlot.engine(EnginePart.COOLING));act(c,p,CarPackets.COMPONENT_SWAP,radiator,0);
        h.assertTrue(c.mechanics().get("engine.cooling")==null,"Battery EV radiator can be physically removed through Service");
        act(c,p,CarPackets.COMPONENT_SWAP,radiator,1);h.assertTrue(c.mechanics().get("engine.cooling")!=null&&c.coolant()==coolant,"EV radiator reinstallation preserves coolant and consumes the returned radiator");
        System.out.println("CONFIGURED_COMPONENTS_SERVER_PASS hv_interlock");h.succeed();
    }
    @GameTest(template="test_track") public void cratePlacementDoesNotFillMissingVersionedParts(GameTestHelper h){
        var c=car(h,Powertrain.ELECTRIC_400);var p=owner(h,c);var m=c.mechanics().with("traction.motor_rear",null).with("wheel.fl.tire",null);c.discard();p.moveTo(c.getX()+4,c.getY(),c.getZ());
        var crate=new ItemStack(Electrification.CRATES.get(Powertrain.ELECTRIC_400).get());MechanicalData.set(crate,m);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,crate);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(8,1,8));var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false);
        var result=crate.useOn(new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        h.assertTrue(result.consumesAction()&&crate.isEmpty(),"Versioned crate is placed and consumed once");
        var placed=h.getLevel().getEntitiesOfClass(CarEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).stream().filter(v->!v.isRemoved()).findFirst().orElseThrow();
        h.assertTrue(placed.mechanics().equals(m),"Placement preserves the complete versioned component map, including missing tire and motor");
        System.out.println("CONFIGURED_COMPONENTS_SERVER_PASS crate_transfer");h.succeed();
    }

    @GameTest(template="test_track") public void everyServiceRecipeResolvesItsOwnItemIncludingMirroredPatterns(GameTestHelper h){
        int count=0;
        var itemsToCheck=new LinkedHashSet<String>();for(var slot:ComponentSlot.ALL)if(slot.hardware()==null)itemsToCheck.add(slot.item());
        itemsToCheck.addAll(List.of("sport_muffler","coolant_bottle","oil_bottle","brake_fluid_bottle","service_jack","pressure_tester","multimeter","tire_gauge","oil_pressure_gauge","compression_tester","tire_pump"));
        for(String item:itemsToCheck){
            var recipe=(net.minecraft.world.item.crafting.ShapedRecipe)h.getLevel().getRecipeManager().byKey(AutoPropulsionAge.id(item)).orElseThrow().value();
            var ingredients=new ArrayList<ItemStack>();for(var ingredient:recipe.getIngredients())ingredients.add(ingredient.isEmpty()?ItemStack.EMPTY:ingredient.getItems()[0].copy());
            for(boolean mirror:new boolean[]{false,true}){
                var items=new ArrayList<ItemStack>();for(int y=0;y<recipe.getHeight();y++)for(int x=0;x<recipe.getWidth();x++)items.add(ingredients.get(y*recipe.getWidth()+(mirror?recipe.getWidth()-1-x:x)));
                var input=net.minecraft.world.item.crafting.CraftingInput.of(recipe.getWidth(),recipe.getHeight(),items);
                var resolved=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input,h.getLevel().registryAccess());
                h.assertTrue(resolved.is(AutoPropulsionAge.PART_ITEMS.get(item).get()),"Recipe lookup returned the wrong item for "+item+" mirror="+mirror+": "+resolved);count++;
            }
        }
        h.assertTrue(count==156,"All 78 service recipes and their mirrors resolve independently");
        System.out.println("CONFIGURED_COMPONENTS_SERVER_PASS service_recipes");h.succeed();
    }
}
