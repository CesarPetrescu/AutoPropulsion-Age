package com.photonspark.sparkmotors.item;

import com.photonspark.sparkmotors.sim.VehicleCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;

public final class ConditionedPartItem extends Item {
    public ConditionedPartItem(Properties properties){super(properties);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        var condition=ConditionData.fromItem(stack);
        for(var part:VehicleCondition.Part.values())if(condition.health(part)<99.95)
            lines.add(Component.literal(part.title+": "+Math.round(condition.health(part))+"% (wear "+Math.round(condition.state(part).wear())+" / damage "+Math.round(condition.state(part).damage())+")"));
    }
}
