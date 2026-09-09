package com.photonspark.sparkmotors.item;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Coachwork conversion, not a powertrain conversion. The donor's complete component patch survives. */
public record BodyCratingRecipe(ShapelessRecipe delegate) implements CraftingRecipe {
    @Override public boolean matches(CraftingInput input,Level level){
        if(!delegate.matches(input,level))return false;
        var result=delegate.getResultItem(level.registryAccess());
        if(!(result.getItem() instanceof CarCrateItem target))return false;
        CarCrateItem donor=null;CarBodyKitItem kit=null;
        for(int i=0;i<input.size();i++){
            var stack=input.getItem(i);if(stack.isEmpty())continue;
            if(stack.getItem() instanceof CarCrateItem crate){if(donor!=null)return false;donor=crate;}
            else if(stack.getItem() instanceof CarBodyKitItem body){if(kit!=null)return false;kit=body;}
            else return false;
        }
        return donor!=null&&kit!=null&&donor.powertrain()==target.powertrain()
            &&kit.style()==target.bodyStyle()&&donor.bodyStyle()!=target.bodyStyle();
    }
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){
        var result=delegate.assemble(input,registries);
        for(int i=0;i<input.size();i++)if(input.getItem(i).getItem() instanceof CarCrateItem){
            result.applyComponents(input.getItem(i).getComponentsPatch());
            break;
        }
        return result;
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input){
        var result=NonNullList.withSize(input.size(),ItemStack.EMPTY);
        CarCrateItem donor=null;int kit=-1;
        for(int i=0;i<input.size();i++){
            var item=input.getItem(i).getItem();
            if(item instanceof CarCrateItem crate)donor=crate;
            if(item instanceof CarBodyKitItem)kit=i;
        }
        if(donor!=null&&kit>=0)result.set(kit,BodyStyles.kit(donor.bodyStyle()));
        return result;
    }
    @Override public boolean canCraftInDimensions(int width,int height){return delegate.canCraftInDimensions(width,height);}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return delegate.getResultItem(registries);}
    @Override public NonNullList<Ingredient> getIngredients(){return delegate.getIngredients();}
    @Override public CraftingBookCategory category(){return delegate.category();}
    @Override public String getGroup(){return delegate.getGroup();}
    @Override public RecipeSerializer<?> getSerializer(){return BodyStyles.RECIPE.get();}
    public static final class Serializer implements RecipeSerializer<BodyCratingRecipe>{
        @Override public MapCodec<BodyCratingRecipe> codec(){return ShapelessRecipe.Serializer.CODEC.xmap(BodyCratingRecipe::new,BodyCratingRecipe::delegate);}
        @Override public StreamCodec<RegistryFriendlyByteBuf,BodyCratingRecipe> streamCodec(){return ShapelessRecipe.Serializer.STREAM_CODEC.map(BodyCratingRecipe::new,BodyCratingRecipe::delegate);}
    }
}
