package com.photonspark.sparkmotors.gametest;

import com.mojang.authlib.GameProfile;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.item.EngineItem;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class EngineGameTests {
    private CarEntity car(GameTestHelper h){var c=AutoPropulsionAge.CAR.get().create(h.getLevel());var p=h.absoluteVec(new Vec3(8,2.05,8));c.moveTo(p.x,p.y,p.z,0,0);h.getLevel().addFreshEntity(c);return c;}
    private ServerPlayer owner(GameTestHelper h,CarEntity c){var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"engine_mechanic"));p.setGameMode(GameType.SURVIVAL);p.moveTo(c.position().add(2,0,0));c.setOwner(p.getUUID());return p;}
    private void act(CarEntity c,ServerPlayer p,int action,int a,int b){c.tickCount+=4;c.action(p,action,a,b);}
    private void open(CarEntity c,ServerPlayer p){act(c,p,CarPackets.HOOD,0,0);c.hoodProgress=1;}
    @GameTest(template="test_track",timeoutTicks=50) public void hoodAccessAndAnimation(GameTestHelper h){
        var c=car(h);var p=owner(h,c);var item=AutoPropulsionAge.enginePartItem(EnginePart.FUEL,2);p.getInventory().add(new ItemStack(item));
        act(c,p,CarPackets.ENGINE_PART,1,2);h.assertTrue(p.getInventory().countItem(item)==1&&EnginePart.FUEL.variant(c.engineParts())==1,"Closed hood must reject work without consuming inventory");
        act(c,p,CarPackets.HOOD,0,0);act(c,p,CarPackets.ENGINE_PART,1,2);
        h.assertTrue(!c.panels()&&c.hoodOpen(),"Hood must open independently of doors and trunk");
        h.assertTrue(p.getInventory().countItem(item)==1,"Hood must finish opening before work");
        h.runAfterDelay(18,()->{
            act(c,p,CarPackets.ENGINE_PART,1,2);h.assertTrue(EnginePart.FUEL.variant(c.engineParts())==2&&p.getInventory().countItem(item)==0,"Fully open hood allows actual part installation");
            act(c,p,CarPackets.IGNITION,0,0);act(c,p,CarPackets.ENGINE_PART,1,0);
            h.assertTrue(c.ignition()&&EnginePart.FUEL.variant(c.engineParts())==2,"Running engine must reject disassembly");h.succeed();
        });
    }
    @GameTest(template="test_track") public void incompatibleBoostAndInvalidRequestsAreRejected(GameTestHelper h){
        var c=car(h);var p=owner(h,c);open(c,p);var turbo=AutoPropulsionAge.enginePartItem(EnginePart.INDUCTION,1);p.getInventory().add(new ItemStack(turbo));
        act(c,p,CarPackets.ENGINE_PART,5,1);h.assertTrue(EnginePart.INDUCTION.variant(c.engineParts())==0&&p.getInventory().countItem(turbo)==1,"Boost must reject insufficient fuel/internals without consuming kit");
        int before=c.engineParts();act(c,p,CarPackets.ENGINE_PART,99,99);act(c,p,CarPackets.ENGINE_SWAP,99,99);
        h.assertTrue(c.engineParts()==before&&c.engineFamily()==EngineFamily.I4,"Malformed requests must not change state");
        var stranger=owner(h,c);c.setOwner(p.getUUID());act(c,stranger,CarPackets.ENGINE_PART,1,0);
        h.assertTrue(c.engineParts()==before,"Another player must not disassemble the engine");
        p.moveTo(c.position().add(30,0,0));act(c,p,CarPackets.ENGINE_PART,1,0);h.assertTrue(c.engineParts()==before,"Remote servicing must be rejected");h.succeed();
    }
    @GameTest(template="test_track") public void allEngineItemsPreserveTheirInstalledParts(GameTestHelper h){
        var c=car(h);var p=owner(h,c);open(c,p);int cases=0;
        for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++)for(int induction=0;induction<3;induction++){
            var state=new CompoundTag();c.saveWithoutId(state);state.putInt("Assemblies",Assembly.ENGINE.with(Assembly.stock(),grade));state.putInt("EngineFamily",family.ordinal());
            int expected=EnginePart.INTAKE.with(EnginePart.boosted(induction),2);state.putInt("EngineParts",expected);state.putBoolean("HoodOpen",true);state.putFloat("EngineTemperature",104.5f);c.load(state);
            p.getInventory().clearContent();act(c,p,CarPackets.INSTALL,0,0);
            var item=AutoPropulsionAge.engineItem(family,grade);h.assertTrue(p.getInventory().countItem(item)==1,"Exactly one engine should return: "+family);
            ItemStack removed=p.getInventory().items.stream().filter(s->s.is(item)).findFirst().orElseThrow();
            h.assertTrue(EngineItem.parts(removed)==expected,"Removed engine must retain all service parts");
            var restored=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)removed.save(h.getLevel().registryAccess()));
            p.getInventory().clearContent();p.getInventory().add(restored);
            act(c,p,CarPackets.ENGINE_SWAP,family.ordinal(),grade);
            h.assertTrue(c.engineFamily()==family&&c.engineParts()==expected&&Assembly.ENGINE.variant(c.config())==grade,"Refit after item serialization must restore exact build");
            h.assertTrue(c.temperature()==104.5f,"Removed engine must retain its temperature");
            h.assertTrue(p.getInventory().countItem(item)==0,"Refitting consumes one engine");cases++;
        }
        h.assertTrue(cases==42,"All family/grade/induction cases tested");h.succeed();
    }
    @GameTest(template="test_track") public void servicePartSwapsConserveInventory(GameTestHelper h){
        var c=car(h);var p=owner(h,c);open(c,p);
        for(var slot:EnginePart.values())if(slot!=EnginePart.INDUCTION){
            var stock=AutoPropulsionAge.enginePartItem(slot,1);var upgrade=AutoPropulsionAge.enginePartItem(slot,2);p.getInventory().clearContent();p.getInventory().add(new ItemStack(upgrade));
            act(c,p,CarPackets.ENGINE_PART,slot.ordinal(),2);
            h.assertTrue(p.getInventory().countItem(stock)==1&&p.getInventory().countItem(upgrade)==0,"Upgrade returns old part and consumes new one: "+slot);
            act(c,p,CarPackets.ENGINE_PART,slot.ordinal(),0);act(c,p,CarPackets.IGNITION,0,0);
            h.assertTrue(!c.ignition(),"Missing required assembly must prevent starting: "+slot);
            act(c,p,CarPackets.ENGINE_PART,slot.ordinal(),2);h.assertTrue(slot.variant(c.engineParts())==2,"Removed part can be refitted");
        }
        for(int mode:new int[]{1,2,0}){
            if(mode>0)p.getInventory().add(new ItemStack(AutoPropulsionAge.enginePartItem(EnginePart.INDUCTION,mode)));
            act(c,p,CarPackets.ENGINE_PART,5,mode);h.assertTrue(EnginePart.INDUCTION.variant(c.engineParts())==mode,"Induction is a single exclusive slot");
        }
        h.succeed();
    }
    @GameTest(template="test_track") public void legacySavesAndMalformedState(GameTestHelper h){
        var c=car(h);var legacy=new CompoundTag();legacy.putInt("Assemblies",Assembly.stock());c.load(legacy);
        h.assertTrue(c.engineFamily()==EngineFamily.I4&&c.engineParts()==EnginePart.stock(),"0.1 cars migrate to a complete stock I4");
        legacy.putInt("EngineFamily",999);legacy.putInt("EngineParts",-1);legacy.putFloat("EngineTemperature",Float.NaN);c.load(legacy);
        h.assertTrue(c.engineFamily()==EngineFamily.I4&&Float.isFinite(c.temperature()),"Invalid family and temperature must be sanitized");
        for(var slot:EnginePart.values())h.assertTrue(slot.variant(c.engineParts())<=2,"Invalid slot bits must be sanitized");h.succeed();
    }
    @GameTest(template="test_track") public void allCraftedEngineConversionsKeepTheirParts(GameTestHelper h){
        int count=0;
        for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++){
            if(family==EngineFamily.I4&&grade==1)continue;
            var recipe=h.getLevel().getRecipeManager().byKey(AutoPropulsionAge.id(family.itemName(grade))).orElseThrow().value();
            var items=new java.util.ArrayList<ItemStack>();
            for(var ingredient:recipe.getIngredients()){
                var stack=ingredient.isEmpty()?ItemStack.EMPTY:ingredient.getItems()[0].copy();
                if(stack.getItem() instanceof EngineItem)EngineItem.withTemperature(EngineItem.withParts(stack,EnginePart.boosted(1)),104.5f);
                items.add(stack);
            }
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,items);
            var crafting=(net.minecraft.world.item.crafting.CraftingRecipe)recipe;
            h.assertTrue(crafting.matches(input,h.getLevel()),"Generated conversion recipe must match: "+family+grade);
            var result=crafting.assemble(input,h.getLevel().registryAccess());
            h.assertTrue(result.is(AutoPropulsionAge.engineItem(family,grade))&&EngineItem.parts(result)==EnginePart.boosted(1)&&EngineItem.temperature(result)==104.5f,"Crafting must retain installed kits and temperature");count++;
        }
        h.assertTrue(count==13,"All donor-engine recipes checked");h.succeed();
    }
    @GameTest(template="test_track",timeoutTicks=3100) public void all42EngineLayoutsDriveAndBrakeInWorld(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();CarEntity[] active={null};int[] ticks={0};float[] peak={0};double[] start={0};
        h.onEachTick(()->{
            int t=ticks[0]++,job=t/70,step=t%70;if(job>=42)return;
            if(step==0){
                if(active[0]!=null){p.stopRiding();active[0].discard();}
                var c=car(h);active[0]=c;var state=new CompoundTag();c.saveWithoutId(state);
                state.putInt("EngineFamily",job/6);state.putInt("Assemblies",Assembly.ENGINE.with(Assembly.stock(),1+(job/3)%2));state.putInt("EngineParts",EnginePart.boosted(job%3));c.load(state);
                p.moveTo(c.position());c.setOwner(p.getUUID());h.assertTrue(p.startRiding(c,true),"Driver must mount layout "+job);act(c,p,CarPackets.IGNITION,0,0);start[0]=c.getZ();peak[0]=0;
            }
            var c=active[0];c.receiveInput(step<35?1:4,0);peak[0]=Math.max(peak[0],Math.abs(c.speed()));
            if(step==68){
                h.assertTrue(peak[0]>3&&c.getZ()>start[0]+2,"Every family/grade/induction layout must drive: "+job);
                h.assertTrue(Math.abs(c.speed())<.3&&c.fuel()<40,"Every layout must brake and use fuel: "+job);
                if(job==41){p.stopRiding();c.discard();h.succeed();}
            }
        });
    }
    @GameTest(template="test_track") public void craftedCarCrateKeepsTheDonorEngineBuild(GameTestHelper h){
        var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)h.getLevel().getRecipeManager().byKey(AutoPropulsionAge.id("sedan_crate")).orElseThrow().value();
        var items=new java.util.ArrayList<ItemStack>();
        for(var ingredient:recipe.getIngredients()){
            var s=ingredient.getItems()[0].copy();if(s.getItem() instanceof EngineItem)EngineItem.withParts(s,EnginePart.boosted(2));items.add(s);
        }
        var result=recipe.assemble(net.minecraft.world.item.crafting.CraftingInput.of(3,3,items),h.getLevel().registryAccess());
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"crate_mechanic"));p.setGameMode(GameType.SURVIVAL);
        p.moveTo(h.absoluteVec(new Vec3(12,2,8)));p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,result);
        var floor=h.absolutePos(new net.minecraft.core.BlockPos(8,1,8));
        var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor),net.minecraft.core.Direction.UP,floor,false);
        result.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        var cars=h.getLevel().getEntitiesOfClass(CarEntity.class,new net.minecraft.world.phys.AABB(floor).inflate(4));
        h.assertTrue(cars.size()==1&&cars.getFirst().engineParts()==EnginePart.boosted(2),"Crafted crate must place the donor's exact engine build");
        h.assertTrue(result.isEmpty(),"Placing a survival crate consumes it");h.succeed();
    }
}
