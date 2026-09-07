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
        var tag=new CompoundTag();c.saveWithoutId(tag);var copy=AutoPropulsionAge.CAR.get().create(h.getLevel());copy.load(tag);h.assertTrue(copy.mechanics().equals(c.mechanics()),"Vehicle save preserves full component state");h.succeed();
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
}
