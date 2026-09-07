package com.photonspark.sparkmotors.item;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Shaped engine conversions preserve the donor's installed kits instead of deleting them. */
public record EngineCraftingRecipe(ShapedRecipe delegate) implements CraftingRecipe {
    @Override public boolean matches(CraftingInput input,Level level){return delegate.matches(input,level);}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){
        var result=delegate.assemble(input,registries);
        boolean crate=result.is(AutoPropulsionAge.CAR_CRATE.get());
        var combined=crate?com.photonspark.sparkmotors.sim.MechanicalState.legacy(com.photonspark.sparkmotors.sim.Assembly.stock(),com.photonspark.sparkmotors.sim.EnginePart.stock(),20,20,100):null;
        for(int i=0;i<input.size();i++){
            var donor=input.getItem(i);var mechanical=MechanicalData.get(donor);
            if(donor.getItem() instanceof EngineItem){
                var data=donor.get(DataComponents.CUSTOM_DATA);if(data!=null)result.set(DataComponents.CUSTOM_DATA,data);
                if(crate){var bundle=MechanicalData.bundle(donor,s->s.assembly()==com.photonspark.sparkmotors.sim.Assembly.ENGINE,com.photonspark.sparkmotors.sim.Assembly.stock(),EngineItem.parts(donor));combined=combined.replace(s->s.assembly()==com.photonspark.sparkmotors.sim.Assembly.ENGINE,bundle,true);}
                else if(mechanical!=null)MechanicalData.set(result,mechanical);
            }else if(mechanical!=null){
                if(crate){
                    for(var group:java.util.List.of(com.photonspark.sparkmotors.sim.Assembly.WHEELS,com.photonspark.sparkmotors.sim.Assembly.TRANSMISSION))
                        if(donor.is(AutoPropulsionAge.partItem(group,1)))combined=combined.replace(s->s.assembly()==group,mechanical,false);
                }else{
                    // Reworking a used single part changes its specification, never its identity or condition.
                    if(mechanical.parts().size()==1){var entry=mechanical.parts().entrySet().iterator().next();var p=entry.getValue();String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
                        mechanical=mechanical.with(entry.getKey(),new com.photonspark.sparkmotors.sim.PartInstance(p.id(),item,p.wear(),p.damage(),p.faults(),p.reserve(),p.temperature()));}
                    MechanicalData.set(result,mechanical);
                }
            }
        }
        if(crate)MechanicalData.set(result,combined);
        return result;
    }
    @Override public boolean canCraftInDimensions(int width,int height){return delegate.canCraftInDimensions(width,height);}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return delegate.getResultItem(registries);}
    @Override public NonNullList<Ingredient> getIngredients(){return delegate.getIngredients();}
    @Override public CraftingBookCategory category(){return delegate.category();}
    @Override public String getGroup(){return delegate.getGroup();}
    @Override public RecipeSerializer<?> getSerializer(){return AutoPropulsionAge.ENGINE_RECIPE.get();}
    public static final class Serializer implements RecipeSerializer<EngineCraftingRecipe> {
        @Override public MapCodec<EngineCraftingRecipe> codec(){return ShapedRecipe.Serializer.CODEC.xmap(EngineCraftingRecipe::new,EngineCraftingRecipe::delegate);}
        @Override public StreamCodec<RegistryFriendlyByteBuf,EngineCraftingRecipe> streamCodec(){return ShapedRecipe.Serializer.STREAM_CODEC.map(EngineCraftingRecipe::new,EngineCraftingRecipe::delegate);}
    }
}
