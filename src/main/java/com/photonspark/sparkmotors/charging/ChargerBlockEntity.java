package com.photonspark.sparkmotors.charging;

import com.photonspark.sparkmotors.compat.ElnChargingPort;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.electric.ChargingModel;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One explicitly paired vehicle, no proximity auto-charging and no energy generation in survival. */
public final class ChargerBlockEntity extends BlockEntity {
    private ElnChargingPort port;
    private int retry;
    private UUID vehicle,owner;
    private boolean creativePower;
    private String status="UNPLUGGED";
    private double inputKw,deliveredJ,lossJ,inputVoltage;
    public ChargerBlockEntity(BlockPos pos,BlockState state){super(Electrification.CHARGER_ENTITY.get(),pos,state);}
    public ChargingModel.Tier tier(){return ((ChargerBlock)getBlockState().getBlock()).tier;}
    public String status(){return status;}public double inputKw(){return inputKw;}public double deliveredJ(){return deliveredJ;}
    public boolean mayUse(Player player){return owner==null||owner.equals(player.getUUID())||player.hasPermissions(2);}
    public boolean connect(Player player,CarEntity car){
        if(level==null||level.isClientSide||!mayUse(player)||!car.mayModify(player)||vehicle!=null&&!vehicle.equals(car.getUUID()))return false;
        if(!car.attachCharger(worldPosition))return false;
        owner=player.getUUID();vehicle=car.getUUID();setChanged();return true;
    }
    public void disconnect(){
        if(level instanceof ServerLevel server&&vehicle!=null&&server.getEntity(vehicle) instanceof CarEntity car)car.detachCharger(worldPosition);
        vehicle=null;inputKw=0;status="UNPLUGGED";if(port!=null)port.request(0);setChanged();
    }
    public void interact(Player player){
        if(!mayUse(player)){message(player,"This charger belongs to another player.");return;}
        if(player.isShiftKeyDown()){
            if(vehicle!=null){disconnect();message(player,"Charging cable disconnected.");return;}
            if(player.isCreative()){creativePower=!creativePower;owner=player.getUUID();setChanged();message(player,"Creative test supply: "+(creativePower?"ON (unlimited, not survival power)":"OFF"));return;}
        }
        message(player,String.format(Locale.ROOT,"%s | %.0f V | %s | %.2f kW | %.3f kWh supplied%s. Sneak-click to unplug; use a charging cable to pair.",tier().title,inputVoltage,status,inputKw,deliveredJ/3_600_000,creativePower?" | CREATIVE TEST POWER":""));
    }
    public void serverTick(){
        inputKw=0;if(!(level instanceof ServerLevel server))return;
        if(port==null&&retry--<=0){port=ElnChargingPort.open(level,worldPosition,tier().inputV,tier().inputLimitW(),tier().maxA);retry=100;}
        if(vehicle==null){if(port!=null)port.request(0);return;}
        // No chunk tickets: unplug when either endpoint unloads. Saved UUIDs do not resurrect a stale connection.
        if(!(server.getEntity(vehicle) instanceof CarEntity car)||car.isRemoved()||car.distanceToSqr(Vec3.atCenterOf(worldPosition))>36||!car.isConnectedTo(worldPosition)||!car.attachCharger(worldPosition)){
            disconnect();return;
        }
        var battery=car.tractionBattery();if(battery==null){disconnect();return;}
        if(port!=null){
            var requested=ChargingModel.step(car.powertrain(),battery,tier(),tier().inputV,tier().inputLimitW()*.05,car.chargeTarget()/100.0,true,car.ignition(),car.speed(),.05,car.ambientTemperature());
            port.request(creativePower?0:requested.inputJ()*20);
        }
        double voltage=creativePower?tier().inputV:port==null?0:port.voltage();
        double available=creativePower?tier().inputLimitW()*.05:port==null?0:port.availableJ();
        var result=ChargingModel.step(car.powertrain(),battery,tier(),voltage,available,car.chargeTarget()/100.0,true,car.ignition(),car.speed(),.05,car.ambientTemperature());
        inputVoltage=voltage;status=result.status().name();
        if(result.inputJ()>0&&(creativePower||port!=null&&port.debit(result.inputJ()))){
            car.acceptCharge(worldPosition,result);inputKw=result.inputJ()*20/1000;deliveredJ+=result.inputJ();lossJ+=result.lossJ();setChanged();
        }else car.acceptCharge(worldPosition,new ChargingModel.Result(battery,0,0,0,0,result.status()));
    }
    @Override public void setRemoved(){if(port!=null){port.close();port=null;}disconnect();super.setRemoved();}
    @Override public void onChunkUnloaded(){if(port!=null){port.close();port=null;}disconnect();super.onChunkUnloaded();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);if(owner!=null)tag.putUUID("Owner",owner);
        tag.putBoolean("CreativeTestPower",creativePower);tag.putDouble("InputJ",deliveredJ);tag.putDouble("LossJ",lossJ);
        // Deliberately do not persist a cable: both ends reconnect explicitly after reload.
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;creativePower=tag.getBoolean("CreativeTestPower");
        deliveredJ=Math.max(0,finite(tag.getDouble("InputJ")));lossJ=Math.max(0,finite(tag.getDouble("LossJ")));vehicle=null;status="UNPLUGGED";
    }
    private static double finite(double value){return Double.isFinite(value)?value:0;}
    private static void message(Player p,String text){p.displayClientMessage(Component.literal(text),false);}
}
