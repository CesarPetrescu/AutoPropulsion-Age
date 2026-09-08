package com.photonspark.sparkmotors.gametest;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.lang.reflect.*;

/** Test-only ideal supply, one REAL ELN cable and the production charger load. Not in the mod jar. */
public final class ElnCircuitFixture implements AutoCloseable {
    private final Object api,load,source,node;
    private final Level level;
    private final BlockPos wire,power;
    private final int dimension;
    private final Class<?> apiClass,loadType,sourceType,nodeType;
    private ElnCircuitFixture(Level level,BlockPos charger,ServerPlayer player,double volts) throws ReflectiveOperationException {
        this.level=level;
        apiClass=Class.forName("mods.eln.api.v1.electrical.ElectricalIntegration");api=apiClass.getField("INSTANCE").get(null);
        loadType=Class.forName(apiClass.getName()+"$Load");sourceType=Class.forName(apiClass.getName()+"$VoltageSource");nodeType=Class.forName(apiClass.getName()+"$BlockNode");
        dimension=((Number)Class.forName("mods.eln.misc.DimensionIds").getMethod("id",Level.class).invoke(null,level)).intValue();
        power=charger.north(2);wire=charger.north();
        level.setBlock(power,Blocks.COPPER_BLOCK.defaultBlockState(),3);level.setBlock(wire.below(),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        load=apiClass.getMethod("createAndRegisterLoad",String.class).invoke(api,"sparkmotors.testSupply");
        node=apiClass.getMethod("createAndRegisterBlockNode",int.class,int.class,int.class,int.class,loadType,int.class).invoke(api,dimension,power.getX(),power.getY(),power.getZ(),load,1);
        source=apiClass.getMethod("createAndRegisterVoltageSource",loadType,loadType,double.class).invoke(api,load,null,volts);
        // Legacy tier cables are rated only 1/2/5/15 kW and are not 60/320 kW feeders.
        // Use the current copper utility-conductor physics (175 A catalog rating).
        // ELN's largest insulated catalog voltage is 1 kV; the 3.2 kV fixture is a bare bus.
        String name="Copper 2/0 AWG Cable "+(volts<=1000?"1000V":"Bare");
        ItemStack stack=(ItemStack)Class.forName("mods.eln.Eln").getMethod("findItemStack",String.class,int.class).invoke(null,name,1);
        if(stack==null||stack.isEmpty())throw new IllegalStateException("Missing actual ElectricalAge cable "+name);
        Item cable=stack.getItem();
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atCenterOf(wire.below()).add(0,.5,0),Direction.UP,wire.below(),false);
        var result=cable.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        if(!result.consumesAction())throw new IllegalStateException("Could not place actual ELN cable: "+name+" result="+result);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        if(level.getBlockEntity(wire)==null||!level.getBlockEntity(wire).getClass().getName().contains("SixNode"))
            throw new IllegalStateException("ELN cable use returned "+result+" without a native cable entity: "+level.getBlockState(wire)+" item="+name);
        System.out.println("ELN_FIXTURE_PLACED "+describe());
    }
    public static ElnCircuitFixture power(Level level,BlockPos charger,ServerPlayer player,double volts){
        try{return new ElnCircuitFixture(level,charger,player,volts);}catch(ReflectiveOperationException e){throw new IllegalStateException("ElectricalAge circuit fixture contract failed",e);}
    }
    public double sourceWatts(){try{return Math.abs(((Number)sourceType.getMethod("getPower").invoke(source)).doubleValue());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public String describe(){try{
        Class<?> manager=Class.forName("mods.eln.node.NodeManager"),coordinate=Class.forName("mods.eln.misc.Coordinate");
        Object m=manager.getField("instance").get(null);StringBuilder links=new StringBuilder();
        for(BlockPos p:new BlockPos[]{power,wire,wire.south()}){
            Object n=manager.getMethod("getNodeFromCoordonate",coordinate).invoke(m,coordinate.getConstructor(int.class,int.class,int.class,int.class).newInstance(p.getX(),p.getY(),p.getZ(),dimension));
            links.append(n==null?-1:((java.util.List<?>)n.getClass().getField("nodeConnectionList").get(n)).size()).append('/');
        }
        return "sourceV="+loadType.getMethod("getVoltage").invoke(load)+" sourceW="+sourceWatts()+" connections="+links+" wire="+level.getBlockState(wire);
    }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public void voltage(double volts){try{sourceType.getMethod("setVoltage",double.class).invoke(source,volts);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    @Override public void close(){try{
        apiClass.getMethod("unregisterComponent",sourceType).invoke(api,source);
        apiClass.getMethod("unregisterBlockNode",nodeType).invoke(api,node);apiClass.getMethod("unregisterLoad",loadType).invoke(api,load);
    }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
}
