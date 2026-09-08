package com.photonspark.sparkmotors.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.*;

public final class ServicePartItem extends Item {
    public ServicePartItem(Properties p){super(p);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flag){
        var state=MechanicalData.get(stack);if(state==null){text.add(Component.literal("Unused component / assembly"));return;}
        if(state.parts().isEmpty())text.add(Component.literal(String.format(Locale.ROOT,"Stored fluid: %.2f L",state.coolant()+state.oil()+state.brakeFluid())));
        for(var e:state.parts().entrySet()){
            var p=e.getValue();text.add(Component.literal(String.format(Locale.ROOT,"%s | wear %.0f%% / damage %.0f%%",e.getKey(),p.wear()*100,p.damage()*100)));
            if(p.faults()!=0)text.add(Component.literal("Retained faults: "+p.faults()));
            if(flag.isAdvanced())text.add(Component.literal("Serial "+p.id()));
        }
    }
}
