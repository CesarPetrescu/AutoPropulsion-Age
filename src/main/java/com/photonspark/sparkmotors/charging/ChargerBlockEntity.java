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
    private net.minecraft.world.item.ItemStack storedCable=net.minecraft.world.item.ItemStack.EMPTY;
    private double soc=-1,packC;
    private int target=80,syncTicks;
    private boolean connected;
    private String vehicleName="No vehicle",lastSnapshot="";
    public ChargerBlockEntity(BlockPos pos,BlockState state){super(Electrification.CHARGER_ENTITY.get(),pos,state);}
    public ChargingModel.Tier tier(){return ((ChargerBlock)getBlockState().getBlock()).tier;}
    public String status(){return status;}public double inputKw(){return inputKw;}public double deliveredJ(){return deliveredJ;}
    public double inputVoltage(){return inputVoltage;}public double lossJ(){return lossJ;}
    public double soc(){return soc;}public double packC(){return packC;}
    public int target(){return target;}public boolean connected(){return connected;}public boolean creativePower(){return creativePower;}
    public boolean hasCable(){return !storedCable.isEmpty();}public String vehicleName(){return vehicleName;}
    public boolean mayUse(Player player){return owner==null||owner.equals(player.getUUID())||player.hasPermissions(2);}
    public boolean connect(Player player,CarEntity car){
        if(level==null||level.isClientSide||!mayUse(player)||!car.mayModify(player)||vehicle!=null&&!vehicle.equals(car.getUUID()))return false;
        if(!car.attachCharger(worldPosition))return false;
        owner=player.getUUID();vehicle=car.getUUID();connected=true;readCar(car);setChanged();sync();return true;
    }
    /** Physical cable escrow: only a successful new connection consumes one stack instance. */
    public boolean connectCable(Player player,CarEntity car,net.minecraft.world.item.ItemStack cable){
        if(vehicle!=null||hasCable()||!cable.is(Electrification.CABLE.get())||!connect(player,car))return false;
        storedCable=cable.split(1);setChanged();sync();return true;
    }
    public void unplug(Player player){
        if(level==null||level.isClientSide||!mayUse(player)||player.distanceToSqr(Vec3.atCenterOf(worldPosition))>100)return;
        disconnect();
        if(hasCable()){
            var returned=storedCable;storedCable=net.minecraft.world.item.ItemStack.EMPTY;
            ChargingCableItem.clearPairing(returned);setChanged();
            // Inventory.add discards overflow in creative; this physical item must survive in either mode.
            int free=player.getInventory().getFreeSlot();
            if(free>=0)player.getInventory().setItem(free,returned);else player.drop(returned,false);
            message(player,"Cable returned. If your inventory is full, it is at your feet.");
        }else message(player,"Disconnected. No cable stored here; older connections kept the pairing cable in your inventory.");
        sync();
    }
    public void dropCable(){
        if(level==null||level.isClientSide)return;
        disconnect();if(hasCable()){
            var returned=storedCable;storedCable=net.minecraft.world.item.ItemStack.EMPTY;ChargingCableItem.clearPairing(returned);
            net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,returned);setChanged();
        }
    }
    public void disconnect(){
        if(level instanceof ServerLevel server&&vehicle!=null&&server.getEntity(vehicle) instanceof CarEntity car)car.detachCharger(worldPosition);
        vehicle=null;connected=false;inputKw=0;soc=-1;vehicleName="No vehicle";status="UNPLUGGED";if(port!=null)port.request(0);setChanged();sync();
    }
    public void interact(Player player){
        if(!mayUse(player)){message(player,"This charger belongs to another player.");return;}
        if(player.isShiftKeyDown()){
            if(vehicle!=null||hasCable()){unplug(player);return;}
            if(player.isCreative()){creativePower=!creativePower;owner=player.getUUID();setChanged();message(player,"Creative test supply: "+(creativePower?"ON (unlimited, not survival power)":"OFF"));return;}
        }
        if(player instanceof net.minecraft.server.level.ServerPlayer sp){sync();com.photonspark.sparkmotors.net.CarPackets.openCharger(sp,this);}
    }
    public void serverTick(){
        inputKw=0;if(!(level instanceof ServerLevel server))return;
        if(port==null&&retry--<=0){port=ElnChargingPort.open(level,worldPosition,tier().inputV,tier().inputLimitW(),tier().maxA);retry=100;}
        if(vehicle==null){if(port!=null)port.request(0);inputVoltage=creativePower?tier().inputV:port==null?0:port.voltage();if(++syncTicks%5==0)sync();return;}
        // No chunk tickets: unplug when either endpoint unloads. Saved UUIDs do not resurrect a stale connection.
        if(!(server.getEntity(vehicle) instanceof CarEntity car)||car.isRemoved()||car.distanceToSqr(Vec3.atCenterOf(worldPosition))>36||!car.isConnectedTo(worldPosition)||!car.attachCharger(worldPosition)){
            disconnect();return;
        }
        var battery=car.tractionBattery();if(battery==null){disconnect();return;}
        if(port!=null){
            var requested=ChargingModel.step(car.powertrain(),battery,tier(),tier().inputV,tier().inputLimitW()*.05,car.chargeTarget()/100.0,true,car.ignition(),car.horizontalSpeed(),.05,car.ambientTemperature());
            port.request(creativePower?0:requested.inputJ()*20);
        }
        double voltage=creativePower?tier().inputV:port==null?0:port.voltage();
        double available=creativePower?tier().inputLimitW()*.05:port==null?0:port.availableJ();
        var result=ChargingModel.step(car.powertrain(),battery,tier(),voltage,available,car.chargeTarget()/100.0,true,car.ignition(),car.horizontalSpeed(),.05,car.ambientTemperature());
        inputVoltage=voltage;status=result.status().name();
        if(result.inputJ()>0&&(creativePower||port!=null&&port.debit(result.inputJ()))){
            car.acceptCharge(worldPosition,result);inputKw=result.inputJ()*20/1000;deliveredJ+=result.inputJ();lossJ+=result.lossJ();setChanged();
        }else car.acceptCharge(worldPosition,new ChargingModel.Result(battery,0,0,0,0,result.status()));
        readCar(car);if(++syncTicks%5==0)sync();
    }
    private void readCar(CarEntity car){soc=car.stateOfCharge();packC=car.packTemperature();target=car.chargeTarget();vehicleName=car.powertrain().title;}
    private CompoundTag snapshot(){
        var tag=new CompoundTag();tag.putBoolean("Display",true);tag.putString("Status",status);tag.putBoolean("Connected",connected);
        tag.putBoolean("Cable",hasCable());tag.putBoolean("CreativeTestPower",creativePower);tag.putString("VehicleName",vehicleName);
        tag.putDouble("InputV",Math.round(inputVoltage));tag.putDouble("InputKw",Math.round(inputKw*100)/100d);
        tag.putDouble("InputJ",Math.round(deliveredJ/3600)*3600d);tag.putDouble("LossJ",Math.round(lossJ/3600)*3600d);
        tag.putDouble("Soc",Math.round(soc*1000)/1000d);tag.putDouble("PackC",Math.round(packC*10)/10d);tag.putInt("Target",target);return tag;
    }
    private void sync(){
        if(level==null||level.isClientSide||isRemoved())return;
        String next=snapshot().toString();if(!next.equals(lastSnapshot)){lastSnapshot=next;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return snapshot();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void setRemoved(){if(port!=null){port.close();port=null;}disconnect();super.setRemoved();}
    @Override public void onChunkUnloaded(){if(port!=null){port.close();port=null;}disconnect();super.onChunkUnloaded();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);if(owner!=null)tag.putUUID("Owner",owner);
        tag.putBoolean("CreativeTestPower",creativePower);tag.putDouble("InputJ",deliveredJ);tag.putDouble("LossJ",lossJ);
        if(hasCable())tag.put("StoredCable",storedCable.save(registries));
        // Connection leases expire on unload; the physical item stays recoverable after reload.
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.getBoolean("Display")){
            status=tag.getString("Status");connected=tag.getBoolean("Connected");storedCable=tag.getBoolean("Cable")?Electrification.CABLE.toStack():net.minecraft.world.item.ItemStack.EMPTY;
            creativePower=tag.getBoolean("CreativeTestPower");vehicleName=tag.getString("VehicleName");inputVoltage=tag.getDouble("InputV");inputKw=tag.getDouble("InputKw");
            deliveredJ=tag.getDouble("InputJ");lossJ=tag.getDouble("LossJ");soc=tag.getDouble("Soc");packC=tag.getDouble("PackC");target=tag.getInt("Target");return;
        }
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;creativePower=tag.getBoolean("CreativeTestPower");
        deliveredJ=Math.max(0,finite(tag.getDouble("InputJ")));lossJ=Math.max(0,finite(tag.getDouble("LossJ")));vehicle=null;status="UNPLUGGED";
        storedCable=net.minecraft.world.item.ItemStack.parseOptional(registries,tag.getCompound("StoredCable"));connected=false;
    }
    private static double finite(double value){return Double.isFinite(value)?value:0;}
    private static void message(Player p,String text){p.displayClientMessage(Component.literal(text),false);}
}
