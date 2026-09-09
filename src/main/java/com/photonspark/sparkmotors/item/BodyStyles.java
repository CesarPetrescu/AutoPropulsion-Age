package com.photonspark.sparkmotors.item;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.sim.BodyStyle;
import com.photonspark.sparkmotors.sim.electric.Powertrain;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registration only; safe to load on a dedicated server. */
public final class BodyStyles {
    public static final Map<BodyStyle,DeferredItem<Item>> KITS=new EnumMap<>(BodyStyle.class);
    public static final Map<String,DeferredItem<Item>> CRATES=new LinkedHashMap<>();
    public static final DeferredHolder<RecipeSerializer<?>,BodyCratingRecipe.Serializer> RECIPE=
        AutoPropulsionAge.RECIPES.register("body_crating",BodyCratingRecipe.Serializer::new);
    static {
        for(var style:BodyStyle.values()){
            KITS.put(style,AutoPropulsionAge.ITEMS.register(style.id()+"_body_kit",()->new CarBodyKitItem(new Item.Properties().stacksTo(1),style)));
            if(style==BodyStyle.CLASSIC_SEDAN)continue;
            for(var type:Powertrain.values()){
                String id=crateId(style,type);
                CRATES.put(id,AutoPropulsionAge.ITEMS.register(id,()->new CarCrateItem(new Item.Properties().stacksTo(1),type,style)));
            }
        }
    }
    private BodyStyles(){}
    public static void init(){}
    public static String crateId(BodyStyle style,Powertrain type){
        if(style==BodyStyle.CLASSIC_SEDAN)return type==Powertrain.COMBUSTION?"sedan_crate":type.id()+"_crate";
        return style.id()+(type==Powertrain.COMBUSTION?"":"_"+type.id())+"_crate";
    }
    public static ItemStack kit(BodyStyle style){return KITS.get(style).toStack();}
    public static void display(CreativeModeTab.Output out){CRATES.values().forEach(out::accept);KITS.values().forEach(out::accept);}
}
