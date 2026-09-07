package com.photonspark.autopropulsion;
import com.photonspark.autopropulsion.sim.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public final class VehicleEntity extends Entity {
    private static final EntityDataAccessor<Float> RPM=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FUEL=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TEMP=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOOST=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STEER=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GEAR=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RUNNING=SynchedEntityData.defineId(VehicleEntity.class,EntityDataSerializers.BOOLEAN);
    private final VehicleSimulation simulation=new VehicleSimulation(EngineSpec.reference(),SimulationConfig.defaults());
    private final TreeMap<String,String> installed=new TreeMap<>();
    private UUID owner;
    private VehicleInput input=VehicleInput.PARKED;
    private long lastInput=-100,lastAction=-100,packetTick=-1;
    private int packetsThisTick,lerpSteps;
    private double lerpX,lerpY,lerpZ;private float lerpYaw,lerpPitch;
    private double boostSetting=.65;
    public VehicleEntity(EntityType<? extends VehicleEntity> type,Level level) {
        super(type,level);blocksBuilding=true;
        for(String id:BuiltinCatalog.STARTER_PARTS){String slot=BuiltinCatalog.slotOf(id);installed.put(slot,"autopropulsion:"+id);}
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(RPM,0f);b.define(SPEED,0f);b.define(FUEL,45f);b.define(TEMP,20f);b.define(HEALTH,100f);b.define(BOOST,0f);b.define(STEER,0f);b.define(GEAR,0);b.define(RUNNING,false);
    }
    public void setOwner(UUID id){owner=id;}
    public UUID owner(){return owner;}
    public boolean canManage(Player p){return owner==null||owner.equals(p.getUUID())||p.hasPermissions(2);}
    public boolean acceptsDriver(Player p){return getControllingPassenger()==p;}
    public VehicleSimulation simulation(){return simulation;}
    public Map<String,String> installed(){return Collections.unmodifiableMap(installed);}
    public float rpm(){return entityData.get(RPM);}public float speed(){return entityData.get(SPEED);}public float fuel(){return entityData.get(FUEL);}
    public float temp(){return entityData.get(TEMP);}public float health(){return entityData.get(HEALTH);}public float boost(){return entityData.get(BOOST);}
    public int gear(){return entityData.get(GEAR);}public boolean running(){return entityData.get(RUNNING);}public float steer(){return entityData.get(STEER);}
    public boolean receiveInput(Player p,VehicleInput newInput) {
        if(!acceptsDriver(p))return false;
        long tick=level().getGameTime();if(packetTick!=tick){packetTick=tick;packetsThisTick=0;}
        if(++packetsThisTick>2)return false;
        input=newInput;lastInput=tick;return true;
    }
    public boolean action(Player p,int action,double value) {
        if(distanceToSqr(p)>64||(!canManage(p)&&!(action>=1&&action<=3&&acceptsDriver(p)&&Config.GUEST_DRIVING.get())))return false;
        long tick=level().getGameTime();if(tick-lastAction<3)return false;
        if(action!=0&&getControllingPassenger()!=null&&getControllingPassenger()!=p)return false;
        if(action<0||action>4||!Double.isFinite(value))return false;
        lastAction=tick;
        switch(action) {
            case 0 -> {if(p instanceof ServerPlayer sp)openGarage(sp);}
            case 1 -> simulation.toggleEngine();
            case 2 -> {if(acceptsDriver(p))simulation.shift(1);}
            case 3 -> {if(acceptsDriver(p))simulation.shift(-1);}
            case 4 -> {
                if(simulation.state().running()||Math.abs(simulation.state().speedMs())>.1)return false;
                var turbo=PartCatalog.get(installed.getOrDefault("turbo",""));
                boostSetting=Numbers.clamp(value,0,turbo==null?0:turbo.boost());resolve();
                if(p instanceof ServerPlayer sp)openGarage(sp);
            }
        }
        return true;
    }
    private void resolve() {
        boolean race=false;double torque=800,rpmLimit=10000,maxBoost=0,radiator=110;
        for(String id:installed.values()) {
            var part=PartCatalog.get(id);if(part==null)continue;
            torque=Math.min(torque,part.torqueLimit());rpmLimit=Math.min(rpmLimit,part.rpmLimit());
            race|=part.raceCam();maxBoost=Math.max(maxBoost,part.boost());if(part.slot().equals("radiator"))radiator=part.radiator();
        }
        var ref=EngineSpec.reference().withUpgrades(race,Math.min(boostSetting,maxBoost),torque>260,radiator>110);
        simulation.configure(new EngineSpec(ref.litres(),ref.compression(),ref.ve(),ref.powerMultiplier(),Math.min(ref.rpmLimit(),rpmLimit),torque,"engine/connecting_rods",ref.maxBoostBar(),radiator),Config.simulation());
    }
    public boolean installPart(Player p,ItemStack stack) {
        if(!stack.is(Content.PART.get())||!canManage(p)||simulation.state().running()||Math.abs(simulation.state().speedMs())>.1||distanceToSqr(p)>64)return false;
        var def=PartCatalog.get(ComponentItem.definition(stack));
        if(def==null||!def.functional()||def.family().equals("rotary"))return false;
        if(!BuiltinCatalog.FUNCTIONAL_SLOTS.contains(def.slot()))return false;
        String old=installed.put(def.slot(),def.id());
        if(!p.getAbilities().instabuild) {
            stack.shrink(1);
            if(old!=null){ItemStack back=ComponentItem.stack(old);if(!p.addItem(back))p.drop(back,false);}
        }
        resolve();return true;
    }
    @Override public InteractionResult interact(Player p,InteractionHand hand) {
        if(level().isClientSide)return InteractionResult.SUCCESS;
        ItemStack held=p.getItemInHand(hand);
        if(held.is(Content.PART.get())) {
            boolean ok=installPart(p,held);p.displayClientMessage(Component.translatable(ok?"message.autopropulsion.installed":"message.autopropulsion.rejected"),true);
        } else if(held.is(Content.JERRY_CAN.get())) {
            if(canManage(p)&&!simulation.state().running()&&simulation.state().fuelL()<=50) {
                simulation.refuel(10);
                if(!p.getAbilities().instabuild){held.shrink(1);if(!p.addItem(new ItemStack(Content.EMPTY_CAN.get())))p.drop(new ItemStack(Content.EMPTY_CAN.get()),false);}
            } else p.displayClientMessage(Component.translatable("message.autopropulsion.refuel_off"),true);
        } else if(held.is(Content.WRENCH.get())||p.isShiftKeyDown()) {
            if(canManage(p)&&p instanceof ServerPlayer sp)openGarage(sp);
        } else if(canManage(p)||Config.GUEST_DRIVING.get()) {
            if(owner==null)owner=p.getUUID();p.startRiding(this);
        }
        return InteractionResult.CONSUME;
    }
    public void openGarage(ServerPlayer p) {
        if(!canManage(p)||distanceToSqr(p)>64)return;
        var slots=new StringBuilder();installed.forEach((slot,id)->slots.append(slot).append('=').append(id).append('\n'));
        var curve=new StringBuilder();
        for(int r=1000;r<=8000;r+=250){var s=EngineModel.sample(simulation.engine(),r,1,simulation.engine().maxBoostBar());curve.append(String.format(Locale.ROOT,"%d,%.2f,%.2f;",r,s.torqueNm(),s.powerKw()));}
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new Packets.Garage(getId(),slots.toString(),curve.toString(),simulation.state().fault(),(float)boostSetting));
    }
    @Override public void tick() {
        super.tick();
        if(level().isClientSide) {
            if(lerpSteps>0){double f=1.0/lerpSteps;setPos(Mth.lerp(f,getX(),lerpX),Mth.lerp(f,getY(),lerpY),Mth.lerp(f,getZ(),lerpZ));setYRot(getYRot()+Mth.wrapDegrees(lerpYaw-getYRot())*(float)f);setXRot(Mth.lerp((float)f,getXRot(),lerpPitch));lerpSteps--;}
            refreshBounds();return;
        }
        if(tickCount==1||tickCount%20==0)resolve();
        if(level().getGameTime()-lastInput>5||getControllingPassenger()==null)input=VehicleInput.PARKED;
        if(getControllingPassenger()==null&&tickCount%100==0)simulation.stopEngine();
        double grip=surfaceGrip();
        simulation.step(.05,input,grip);
        var before=simulation.state();
        setYRot(getYRot()+(float)Math.toDegrees(simulation.yawRate(input.steer())*.05));refreshBounds();
        double angle=Math.toRadians(getYRot());double dx=-Math.sin(angle)*before.speedMs()*.05,dz=Math.cos(angle)*before.speedMs()*.05;
        double vy=onGround()?-.02:Math.max(-1.5,getDeltaMovement().y-.04);
        double ox=getX(),oz=getZ();move(MoverType.SELF,new Vec3(dx,vy,dz));
        setDeltaMovement(0,onGround()?0:vy,0);
        double actual=((getX()-ox)*-Math.sin(angle)+(getZ()-oz)*Math.cos(angle))/.05;
        double lost=Math.abs(before.speedMs())-Math.abs(actual);simulation.reconcileSpeed(actual);
        if(lost>3&&Config.DAMAGE.get()>0){var s=simulation.state();simulation.restore(new VehicleSimulation.State(s.speedMs(),s.rpm(),s.gear(),s.fuelL(),s.coolantK(),s.oilK(),s.boostBar(),Math.max(0,s.health()-lost*2*Config.DAMAGE.get()),s.odometerM(),s.running(),"AP0100 COLLISION"));}
        syncState();
    }
    private double surfaceGrip() {
        double grip=0;double angle=Math.toRadians(getYRot());
        for(double x:new double[]{-.74,.74})for(double z:new double[]{-1.27,1.27}) {
            Vec3 start=position().add(x*Math.cos(angle)-z*Math.sin(angle),.7,x*Math.sin(angle)+z*Math.cos(angle));
            var hit=level().clip(new ClipContext(start,start.add(0,-1.3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(hit.getType()==HitResult.Type.MISS)continue;
            var block=level().getBlockState(hit.getBlockPos());
            grip+=block.is(Blocks.ICE)||block.is(Blocks.PACKED_ICE)?.1:block.is(Blocks.GRASS_BLOCK)||block.is(Blocks.DIRT)?.6:1;
        }
        var tire=PartCatalog.get(installed.getOrDefault("tires",""));
        return Math.max(.02,grip/4)*(tire==null?1:tire.grip());
    }
    private void syncState(){var s=simulation.state();entityData.set(RPM,(float)s.rpm());entityData.set(SPEED,(float)s.speedMs());entityData.set(FUEL,(float)s.fuelL());entityData.set(TEMP,(float)(s.coolantK()-273.15));entityData.set(HEALTH,(float)s.health());entityData.set(BOOST,(float)s.boostBar());entityData.set(STEER,(float)input.steer());entityData.set(GEAR,s.gear());entityData.set(RUNNING,s.running());}
    public void refreshBounds(){setBoundingBox(makeBoundingBox());}
    @Override protected AABB makeBoundingBox(){double a=Math.toRadians(getYRot()),x=Math.abs(Math.cos(a))*.9+Math.abs(Math.sin(a))*2.12,z=Math.abs(Math.cos(a))*2.12+Math.abs(Math.sin(a))*.9;return new AABB(getX()-x,getY(),getZ()-z,getX()+x,getY()+1.4,getZ()+z);}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){lerpX=x;lerpY=y;lerpZ=z;lerpYaw=yaw;lerpPitch=pitch;lerpSteps=Math.max(1,Math.min(steps,5));}
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean isPushable(){return false;}
    @Override protected boolean canAddPassenger(Entity p){return getPassengers().size()<2;}
    @Override public LivingEntity getControllingPassenger(){return getFirstPassenger() instanceof LivingEntity living?living:null;}
    @Override protected void positionRider(Entity passenger,MoveFunction move){if(!hasPassenger(passenger))return;int n=getPassengers().indexOf(passenger);double a=Math.toRadians(getYRot()),x=n==0?-.36:.36,z=.2;move.accept(passenger,getX()+x*Math.cos(a)-z*Math.sin(a),getY()-.18,getZ()+x*Math.sin(a)+z*Math.cos(a));}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("DataVersionAP",1);if(owner!=null)tag.putUUID("Owner",owner);tag.putDouble("BoostSetting",boostSetting);
        CompoundTag parts=new CompoundTag();installed.forEach(parts::putString);tag.put("Parts",parts);
        var s=simulation.state();tag.putDouble("Speed",s.speedMs());tag.putDouble("RPM",s.rpm());tag.putInt("Gear",s.gear());tag.putDouble("Fuel",s.fuelL());tag.putDouble("Coolant",s.coolantK());tag.putDouble("Oil",s.oilK());tag.putDouble("Boost",s.boostBar());tag.putDouble("Health",s.health());tag.putDouble("Odometer",s.odometerM());tag.putBoolean("Running",s.running());tag.putString("Fault",s.fault());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if(tag.hasUUID("Owner"))owner=tag.getUUID("Owner");
        if(tag.contains("Parts",Tag.TAG_COMPOUND)){CompoundTag parts=tag.getCompound("Parts");for(String slot:BuiltinCatalog.FUNCTIONAL_SLOTS)if(parts.contains(slot)){String id=parts.getString(slot);if(id.length()<=128)installed.put(slot,id);}}
        if(tag.contains("DataVersionAP"))try{
            boostSetting=Numbers.clamp(tag.getDouble("BoostSetting"),0,3);
            simulation.restore(new VehicleSimulation.State(0,tag.getDouble("RPM"),tag.getInt("Gear"),tag.getDouble("Fuel"),tag.getDouble("Coolant"),tag.getDouble("Oil"),0,tag.getDouble("Health"),tag.getDouble("Odometer"),false,tag.getString("Fault")));
        }catch(IllegalArgumentException e){AutoPropulsion.LOG.warn("Invalid vehicle state for {}; using safe defaults",getUUID());}
        input=VehicleInput.PARKED;syncState();
    }
}
