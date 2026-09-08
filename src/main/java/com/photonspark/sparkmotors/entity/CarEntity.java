package com.photonspark.sparkmotors.entity;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.*;

public final class CarEntity extends Entity {
    private static final EntityDataAccessor<Float> SPEED=data(EntityDataSerializers.FLOAT),RPM=data(EntityDataSerializers.FLOAT),FUEL=data(EntityDataSerializers.FLOAT),
        HEALTH=data(EntityDataSerializers.FLOAT),STEER=data(EntityDataSerializers.FLOAT),PITCH=data(EntityDataSerializers.FLOAT),ROLL=data(EntityDataSerializers.FLOAT),FINAL_DRIVE=data(EntityDataSerializers.FLOAT),TEMPERATURE=data(EntityDataSerializers.FLOAT),BOOST=data(EntityDataSerializers.FLOAT),
        OIL_TEMP=data(EntityDataSerializers.FLOAT),OIL_PRESSURE=data(EntityDataSerializers.FLOAT),ENGINE_HEALTH=data(EntityDataSerializers.FLOAT),AFR=data(EntityDataSerializers.FLOAT),THROTTLE=data(EntityDataSerializers.FLOAT),SPOOL=data(EntityDataSerializers.FLOAT),SHAFT_TORQUE=data(EntityDataSerializers.FLOAT),BLOWER_KW=data(EntityDataSerializers.FLOAT),BOOST_TARGET=data(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FLAGS=data(EntityDataSerializers.INT),CONFIG=data(EntityDataSerializers.INT),PAINT=data(EntityDataSerializers.INT),GEAR=data(EntityDataSerializers.INT),LIMITER=data(EntityDataSerializers.INT),ENGINE_FAMILY=data(EntityDataSerializers.INT),ENGINE_PARTS=data(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POWERTRAIN=data(EntityDataSerializers.INT),EV_MODE=data(EntityDataSerializers.INT),CHARGE_TARGET=data(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PLUGGED=data(EntityDataSerializers.BOOLEAN),GENERATOR=data(EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SOC=data(EntityDataSerializers.FLOAT),PACK_V=data(EntityDataSerializers.FLOAT),PACK_A=data(EntityDataSerializers.FLOAT),PACK_KW=data(EntityDataSerializers.FLOAT),PACK_C=data(EntityDataSerializers.FLOAT),PACK_HEALTH=data(EntityDataSerializers.FLOAT),MOTOR_C=data(EntityDataSerializers.FLOAT),INVERTER_C=data(EntityDataSerializers.FLOAT),REGEN_KW=data(EntityDataSerializers.FLOAT),GEN_KW=data(EntityDataSerializers.FLOAT),CHARGE_KW=data(EntityDataSerializers.FLOAT);
    private ElectricDynamics.State electricState;
    private double chargingTerminalW,chargingCurrentA,chargingVoltageV;
    private static final EntityDataAccessor<BlockPos> CHARGER_POSITION=data(EntityDataSerializers.BLOCK_POS);
    private BlockPos chargingPos;
    private int chargeLease;
    private static final EntityDataAccessor<Optional<UUID>> OWNER=data(EntityDataSerializers.OPTIONAL_UUID);
    private static <T> EntityDataAccessor<T> data(EntityDataSerializer<T> serializer){return SynchedEntityData.defineId(CarEntity.class,serializer);}
    private double speed,verticalSpeed,lerpX,lerpY,lerpZ;
    private float lerpYaw,lerpPitch;
    private int lerpSteps,lastInputTick=-100,inputKeys,lastActionTick=-100,benchTicks;
    private float inputSteer;
    private EnginePhysics.State engineState=EnginePhysics.State.stopped(20,100);
    public float wheelAngle,oldWheelAngle,panelProgress,oldPanelProgress;
    public float hoodProgress,oldHoodProgress,engineAngle,oldEngineAngle;

    public CarEntity(EntityType<? extends CarEntity> type,Level level){super(type,level);blocksBuilding=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        b.define(CHARGER_POSITION,BlockPos.ZERO);b.define(POWERTRAIN,0);b.define(EV_MODE,0);b.define(CHARGE_TARGET,80);b.define(PLUGGED,false);b.define(GENERATOR,false);
        b.define(SOC,0f);b.define(PACK_V,0f);b.define(PACK_A,0f);b.define(PACK_KW,0f);b.define(PACK_C,20f);b.define(PACK_HEALTH,1f);b.define(MOTOR_C,20f);b.define(INVERTER_C,20f);b.define(REGEN_KW,0f);b.define(GEN_KW,0f);b.define(CHARGE_KW,0f);
        b.define(SPEED,0f);b.define(RPM,0f);b.define(FUEL,40f);b.define(HEALTH,100f);b.define(STEER,0f);b.define(PITCH,0f);b.define(ROLL,0f);
        b.define(FINAL_DRIVE,3.7f);b.define(FLAGS,0);b.define(CONFIG,Assembly.stock());b.define(PAINT,0x168A91);b.define(GEAR,1);b.define(LIMITER,6800);b.define(OWNER,Optional.empty());
        b.define(ENGINE_FAMILY,0);b.define(ENGINE_PARTS,EnginePart.stock());b.define(TEMPERATURE,20f);b.define(BOOST,0f);
        b.define(OIL_TEMP,20f);b.define(OIL_PRESSURE,0f);b.define(ENGINE_HEALTH,100f);b.define(AFR,14.7f);b.define(THROTTLE,0f);b.define(SPOOL,0f);b.define(SHAFT_TORQUE,0f);b.define(BLOWER_KW,0f);b.define(BOOST_TARGET,1.4f);
    }
    public float speed(){return entityData.get(SPEED);} public float rpm(){return entityData.get(RPM);} public float fuel(){return entityData.get(FUEL);}
    public float health(){return entityData.get(HEALTH);} public int config(){return entityData.get(CONFIG);} public int paint(){return entityData.get(PAINT);}
    public int gear(){return entityData.get(GEAR);} public int limiter(){return entityData.get(LIMITER);} public float finalDrive(){return entityData.get(FINAL_DRIVE);}
    public float steer(){return entityData.get(STEER);} public float roadPitch(){return entityData.get(PITCH);} public float roadRoll(){return entityData.get(ROLL);}
    public boolean ignition(){return flag(1);} public boolean lights(){return flag(2);} public boolean panels(){return flag(4);}
    public boolean hoodOpen(){return flag(8);}
    public EngineFamily engineFamily(){return EngineFamily.byId(entityData.get(ENGINE_FAMILY));}
    public int engineParts(){return entityData.get(ENGINE_PARTS);}
    public float temperature(){return entityData.get(TEMPERATURE);}public float boost(){return entityData.get(BOOST);}
    public float oilTemperature(){return entityData.get(OIL_TEMP);}public float oilPressure(){return entityData.get(OIL_PRESSURE);}
    public float engineHealth(){return entityData.get(ENGINE_HEALTH);}public float afr(){return entityData.get(AFR);}
    public float throttle(){return entityData.get(THROTTLE);}public float spool(){return entityData.get(SPOOL);}
    public float shaftTorque(){return entityData.get(SHAFT_TORQUE);}public float blowerKw(){return entityData.get(BLOWER_KW);}public float boostTarget(){return entityData.get(BOOST_TARGET);}
    public String engineProblem(){if(powertrain().electric()&&!powertrain().hybrid())return "";return Assembly.ENGINE.variant(config())==0?"No engine installed.":EnginePart.problem(engineParts());}
    public Powertrain powertrain(){return Powertrain.values()[Mth.clamp(entityData.get(POWERTRAIN),0,Powertrain.values().length-1)];}
    public ElectricDynamics.Mode electricMode(){return ElectricDynamics.Mode.values()[Mth.clamp(entityData.get(EV_MODE),0,2)];}
    public int chargeTarget(){return entityData.get(CHARGE_TARGET);}
    public BlockPos chargerPosition(){return entityData.get(CHARGER_POSITION);}
    public boolean plugged(){return entityData.get(PLUGGED);}
    public boolean generatorRunning(){return entityData.get(GENERATOR);}
    public boolean combustionRunning(){return ignition()&&(!powertrain().electric()||generatorRunning());}
    public float stateOfCharge(){return entityData.get(SOC);}public float packVoltage(){return entityData.get(PACK_V);}
    public float packCurrent(){return entityData.get(PACK_A);}public float packKw(){return entityData.get(PACK_KW);}
    public float packTemperature(){return entityData.get(PACK_C);}public float packHealth(){return entityData.get(PACK_HEALTH);}
    public float motorTemperature(){return entityData.get(MOTOR_C);}public float inverterTemperature(){return entityData.get(INVERTER_C);}
    public float regenKw(){return entityData.get(REGEN_KW);}public float generatorKw(){return entityData.get(GEN_KW);}public float chargeKw(){return entityData.get(CHARGE_KW);}
    /** Factory/test initialization, never exposed as a client packet or a free survival conversion. */
    public void initializePowertrain(Powertrain type,double soc){
        if(level().isClientSide)return;
        entityData.set(POWERTRAIN,type.ordinal());electricState=type.electric()?ElectricDynamics.State.initial(type,soc):null;
        if(type.electric()&&!type.hybrid())entityData.set(FUEL,0f);
        syncElectric();
    }
    public double ambientTemperature(){return VehicleDynamics.clamp(4+20*level().getBiome(blockPosition()).value().getBaseTemperature()-Math.max(0,getY()-64)*.0065,-35,45);}
    public BatteryModel.State tractionBattery(){return electricState==null?null:electricState.battery();}
    public boolean attachCharger(BlockPos pos){
        if(level().isClientSide||!powertrain().plugIn()||ignition()||Math.abs(speed())>.1||distanceToSqr(Vec3.atCenterOf(pos))>36)return false;
        if(chargingPos!=null&&!chargingPos.equals(pos)&&chargeLease>=tickCount)return false;
        chargingPos=pos.immutable();entityData.set(CHARGER_POSITION,chargingPos);chargeLease=tickCount+40;entityData.set(PLUGGED,true);return true;
    }
    public boolean isConnectedTo(BlockPos pos){return chargingPos!=null&&chargingPos.equals(pos)&&plugged();}
    public void detachCharger(BlockPos pos){if(isConnectedTo(pos)){chargingPos=null;entityData.set(PLUGGED,false);entityData.set(CHARGE_KW,0f);}}
    public void acceptCharge(BlockPos pos,ChargingModel.Result result){
        if(level().isClientSide||!isConnectedTo(pos)||ignition()||electricState==null)return;
        electricState=electricState.withBattery(result.battery());
        chargingTerminalW=result.terminalJ()*20;chargingCurrentA=result.currentA();
        chargingVoltageV=result.voltageV()>0?result.voltageV():BatteryModel.ocv(powertrain().battery,result.battery());
        entityData.set(CHARGE_KW,(float)(result.inputJ()*20/1000));syncElectric();
    }
    private void syncElectric(){
        if(electricState==null)return;var e=electricState;var b=e.battery();
        entityData.set(SOC,(float)b.soc(powertrain().battery));entityData.set(PACK_C,(float)b.temperatureC());entityData.set(PACK_HEALTH,(float)b.health());
        entityData.set(PACK_V,(float)(plugged()&&chargingVoltageV>0?chargingVoltageV:e.voltageV()));
        entityData.set(PACK_A,(float)(plugged()?chargingCurrentA:e.currentA()));
        entityData.set(PACK_KW,(float)((plugged()?chargingTerminalW:e.packW())/1000));
        entityData.set(MOTOR_C,(float)e.motorC());entityData.set(INVERTER_C,(float)e.inverterC());entityData.set(GENERATOR,e.generating());
        entityData.set(REGEN_KW,(float)(e.regenW()/1000));entityData.set(GEN_KW,(float)(e.generatorW()/1000));
    }
    private boolean flag(int flag){return (entityData.get(FLAGS)&flag)!=0;}
    private void flag(int flag,boolean value){entityData.set(FLAGS,value?entityData.get(FLAGS)|flag:entityData.get(FLAGS)&~flag);}
    public void setOwner(UUID owner){entityData.set(OWNER,Optional.of(owner));}
    public boolean mayModify(Player p){return p.hasPermissions(2)||entityData.get(OWNER).map(p.getUUID()::equals).orElse(true);}
    public void setConfiguration(int c){entityData.set(CONFIG,Assembly.sanitize(c));}
    public void receiveInput(int keys,float steer){
        if(!Float.isFinite(steer))return;
        inputKeys=keys&31;inputSteer=Mth.clamp(steer,-1,1);lastInputTick=tickCount;
    }
    @Override public void tick(){
        super.tick();oldWheelAngle=wheelAngle;oldPanelProgress=panelProgress;oldHoodProgress=hoodProgress;oldEngineAngle=engineAngle;
        wheelAngle+=speed()*.05f/.34f;
        panelProgress=Mth.clamp(panelProgress+(panels()?.09f:-.09f),0,1);
        hoodProgress=Mth.clamp(hoodProgress+(hoodOpen()?.09f:-.09f),0,1);
        engineAngle+=rpm()*.05f*(float)(Math.PI/30);
        if(level().isClientSide){
            if(lerpSteps>0){
                setPos(getX()+(lerpX-getX())/lerpSteps,getY()+(lerpY-getY())/lerpSteps,getZ()+(lerpZ-getZ())/lerpSteps);
                setYRot(getYRot()+Mth.wrapDegrees(lerpYaw-getYRot())/lerpSteps);setXRot(lerpPitch);lerpSteps--;
                setBoundingBox(makeBoundingBox());
            }
            return;
        }
        boolean driver=getControllingPassenger() instanceof Player;
        if(!driver||tickCount-lastInputTick>10){inputKeys=4;inputSteer=0;}
        if(chargingPos!=null&&(chargeLease<tickCount||!level().hasChunkAt(chargingPos))){chargingPos=null;entityData.set(PLUGGED,false);entityData.set(CHARGE_KW,0f);}
        if(health()<=0||isInWater()||plugged()||(!powertrain().electric()&&(engineHealth()<=5||fuel()<=0||!engineProblem().isEmpty()||temperature()>=130)))flag(1,false);
        if(!ignition())benchTicks=0;
        if(benchTicks>0){benchTicks--;inputKeys=21;inputSteer=0;}
        boolean reverse=(inputKeys&8)!=0;
        if(speed>1)reverse=false;
        if(speed< -1)reverse=true;
        boolean braking=(inputKeys&6)!=0;
        var input=new VehicleDynamics.Input((inputKeys&1)!=0?1:0,inputSteer,braking,reverse,(inputKeys&16)!=0);
        var setup=new VehicleDynamics.Setup(config(),limiter(),finalDrive(),engineFamily(),engineParts(),temperature(),boostTarget());
        double[] contact=wheelContacts();
        int contactCount=0;for(double h:contact)if(Double.isFinite(h))contactCount++;
        boolean grounded=onGround()||contactCount>=2;
        double grip=surfaceGrip();
        float currentFuel=fuel();
        for(int i=0;i<4;i++){
            VehicleDynamics.State state;
            if(powertrain().electric()){
                if(electricState==null)electricState=ElectricDynamics.State.initial(powertrain(),0);
                var result=ElectricDynamics.step(powertrain(),electricState,electricMode(),speed,currentFuel,engineState,ignition(),input,setup,grip,grounded,plugged(),.0125,ambientTemperature());
                electricState=result.electric();state=result.road();
            }else state=VehicleDynamics.step(speed,currentFuel,engineState,ignition(),input,setup,grip,grounded,.0125);
            engineState=state.engine();
            speed=state.speed();currentFuel=(float)state.fuel();
            entityData.set(RPM,(float)state.rpm());entityData.set(GEAR,state.gear());
            float previousYaw=getYRot();
            setYRot(previousYaw+(float)Math.toDegrees(state.yawDelta()));
            AABB nextBox=makeBoundingBox();
            if(!level().noCollision(this,nextBox.deflate(.015)))setYRot(previousYaw);
            setBoundingBox(makeBoundingBox());
            double yaw=Math.toRadians(getYRot());
            verticalSpeed=Math.max(-30,verticalSpeed-9.81*.0125);
            Vec3 move=new Vec3(-Math.sin(yaw)*speed*.0125,verticalSpeed*.0125,Math.cos(yaw)*speed*.0125);
            double oldX=getX(),oldZ=getZ(),impact=Math.abs(speed);
            move(MoverType.SELF,move);
            if(verticalCollision)verticalSpeed=0;
            if(horizontalCollision&&Math.hypot(getX()-oldX,getZ()-oldZ)<Math.hypot(move.x,move.z)*.5){
                speed=0;
                if(impact>5){entityData.set(HEALTH,Math.max(0,health()-(float)(impact-5)*1.3f));playSound(SoundEvents.ANVIL_LAND,.45f,1.3f);}
            }
            grounded=onGround()||contactCount>=2;
        }
        syncElectric();
        entityData.set(FUEL,currentFuel);entityData.set(SPEED,(float)speed);entityData.set(STEER,inputSteer);
        float throttle=(inputKeys&1)!=0?1:0;
        entityData.set(BOOST,(float)engineState.boost());entityData.set(OIL_TEMP,(float)engineState.oilTemperature());entityData.set(OIL_PRESSURE,(float)engineState.oilPressure());
        entityData.set(ENGINE_HEALTH,(float)engineState.health());entityData.set(AFR,(float)engineState.afr());entityData.set(THROTTLE,(float)engineState.throttle());entityData.set(SPOOL,(float)engineState.spool());
        entityData.set(SHAFT_TORQUE,(float)engineState.shaftTorque());entityData.set(BLOWER_KW,(float)engineState.blowerKw());
        entityData.set(TEMPERATURE,(float)EngineBuild.temperature(temperature(),rpm(),throttle,boost(),combustionRunning(),engineParts(),speed,.05));
        setDeltaMovement(-Math.sin(Math.toRadians(getYRot()))*speed/20,verticalSpeed/20,Math.cos(Math.toRadians(getYRot()))*speed/20);
        if(contactCount==4){
            float pitch=(float)Math.toDegrees(Math.atan2((contact[0]+contact[1]-contact[2]-contact[3])/2,2.65));
            float roll=(float)Math.toDegrees(Math.atan2((contact[0]+contact[2]-contact[1]-contact[3])/2,1.66));
            entityData.set(PITCH,Mth.lerp(.25f,roadPitch(),Mth.clamp(pitch,-14,14)));
            entityData.set(ROLL,Mth.lerp(.25f,roadRoll(),Mth.clamp(roll,-10,10)));
        }
    }
    private double surfaceGrip(){
        var block=level().getBlockState(blockPosition().below());
        if(block.is(net.minecraft.tags.BlockTags.ICE))return .22;
        if(block.is(net.minecraft.tags.BlockTags.SAND)||block.is(net.minecraft.tags.BlockTags.DIRT))return .70;
        return 1;
    }
    private double[] wheelContacts(){
        double[] heights=new double[4];int i=0;
        for(double z:new double[]{1.35,-1.30})for(double x:new double[]{-.83,.83}){
            Vec3 local=new Vec3(x,0,z).yRot((float)-Math.toRadians(getYRot()));
            Vec3 from=position().add(local).add(0,.75,0),to=from.add(0,-1.05,0);
            var hit=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            heights[i++]=hit.getType()==HitResult.Type.BLOCK?hit.getLocation().y:Double.NaN;
        }
        return heights;
    }
    @Override protected AABB makeBoundingBox(){
        double yaw=Math.toRadians(getYRot()),c=Math.abs(Math.cos(yaw)),s=Math.abs(Math.sin(yaw));
        double x=.98*c+2.25*s,z=2.25*c+.98*s;
        return new AABB(getX()-x,getY(),getZ()-z,getX()+x,getY()+1.52,getZ()+z);
    }
    @Override public float maxUpStep(){return Math.abs(speed())<7?.6f:0;}
    @Override public boolean isPickable(){return true;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return isAlive();}
    @Override public boolean canCollideWith(Entity other){return other.canBeCollidedWith()&&!isPassengerOfSameVehicle(other);}
    @Override public boolean isControlledByLocalInstance(){return false;}
    @Override public LivingEntity getControllingPassenger(){return getFirstPassenger() instanceof LivingEntity l?l:null;}
    @Override protected boolean canAddPassenger(Entity p){return getPassengers().isEmpty()&&Math.abs(speed())<1;}
    @Override protected Vec3 getPassengerAttachmentPoint(Entity p,EntityDimensions dimensions,float scale){
        return new Vec3(-.40,.26,.12).yRot((float)-Math.toRadians(getYRot()));
    }
    @Override protected void positionRider(Entity p,MoveFunction move){
        super.positionRider(p,move);
        p.setYBodyRot(getYRot());
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger){
        for(double side:new double[]{-1,1})for(double z:new double[]{0,-2.7,2.7}){
            Vec3 point=position().add(new Vec3(side*1.55,0,z).yRot((float)-Math.toRadians(getYRot())));
            var hit=level().clip(new ClipContext(point.add(0,2,0),point.add(0,-2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(hit.getType()!=HitResult.Type.BLOCK)continue;
            Vec3 floor=hit.getLocation().add(0,.05,0);
            if(level().noCollision(passenger,passenger.getDimensions(Pose.STANDING).makeBoundingBox(floor)))return floor;
        }
        return position().add(0,1.6,0);
    }
    @Override public InteractionResult interact(Player player,InteractionHand hand){
        if(level().isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer sp))return InteractionResult.PASS;
        if(!mayModify(player)){message(player,"This car belongs to another player.");return InteractionResult.CONSUME;}
        if(entityData.get(OWNER).isEmpty())setOwner(player.getUUID());
        if(player.getItemInHand(hand).getItem() instanceof com.photonspark.sparkmotors.charging.ChargingCableItem){
            com.photonspark.sparkmotors.charging.ChargingCableItem.connect(sp,this,player.getItemInHand(hand));return InteractionResult.CONSUME;
        }
        if(player.getItemInHand(hand).is(AutoPropulsionAge.FUEL_CAN.get()))action(sp,CarPackets.REFUEL,0,0);
        else if(player.isSecondaryUseActive()||player.getItemInHand(hand).is(AutoPropulsionAge.WRENCH.get()))CarPackets.open(sp,this);
        else if(Math.abs(speed())<1)player.startRiding(this);
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResult interactAt(Player player,Vec3 hit,InteractionHand hand){
        Vec3 local=hit.yRot((float)Math.toRadians(getYRot()));
        if(local.z>.55&&(player.isSecondaryUseActive()||player.getItemInHand(hand).is(AutoPropulsionAge.WRENCH.get()))){
            if(!level().isClientSide&&player instanceof ServerPlayer sp&&mayModify(player))CarPackets.openEngine(sp,this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }
    public void action(ServerPlayer player,int action,int a,int b){
        if(!mayModify(player)||distanceToSqr(player)>100||tickCount-lastActionTick<3)return;
        lastActionTick=tickCount;
        if(action==CarPackets.LIGHTS){flag(2,!lights());return;}
        if(action==CarPackets.HORN){playSound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(),1,.8f);return;}
        if(action==CarPackets.REV_TEST){
            if(ignition()&&Math.abs(speed())<.3&&hoodOpen()){benchTicks=40;message(player,"Two-second rev test. Clutch disengaged and brakes held.");}
            else message(player,"Park, open the hood and start the engine before a rev test.");return;
        }
        if(action==CarPackets.IGNITION){
            if(ignition()){flag(1,false);return;}
            if(powertrain().electric()){
                if(plugged()){message(player,"Disconnect the charging cable before selecting READY.");return;}
                if(health()<=0||isInWater()||packHealth()<.1){message(player,"Repair the car or service the traction battery first.");return;}
                if(Assembly.WHEELS.variant(config())==0||Assembly.BRAKES.variant(config())==0||Assembly.SUSPENSION.variant(config())==0){message(player,"Fit wheels, brakes and suspension first.");return;}
                flag(1,true);playSound(SoundEvents.LEVER_CLICK,.5f,1.5f);return;
            }
            if(!Assembly.canDrive(config())){message(player,"Install an engine, transmission, wheel set, brakes and suspension first.");return;}
            if(!engineProblem().isEmpty()){message(player,engineProblem());return;}
            if(engineHealth()<=5){message(player,"Engine worn out. Rebuild it in the engine workshop.");return;}
            if(temperature()>=125){message(player,"Engine too hot. Let it cool before restarting.");return;}
            if(fuel()<=0||health()<=0||isInWater()){message(player,"Refuel or repair the car before starting.");return;}
            flag(1,true);playSound(SoundEvents.PISTON_EXTEND,.7f,.7f);return;
        }
        if(Math.abs(speed())>.3){message(player,"Stop the car before servicing it.");return;}
        if(action==CarPackets.PANELS){boolean open=!panels();flag(4,open);flag(8,open);return;}
        if(action==CarPackets.HOOD){flag(8,!hoodOpen());return;}
        if(ignition()){message(player,"Switch the engine off before servicing.");return;}
        boolean engineWork=action==CarPackets.ENGINE_SWAP||action==CarPackets.ENGINE_PART||action==CarPackets.ENGINE_REBUILD||(action==CarPackets.INSTALL&&a==Assembly.ENGINE.ordinal());
        if(engineWork&&powertrain().electric()&&!powertrain().hybrid()){message(player,"This vehicle has an electric drive unit, not a combustion engine.");return;}
        if(engineWork&&(!hoodOpen()||hoodProgress<.95)){message(player,"Open the hood fully before working on the engine.");return;}
        switch(action){
            case CarPackets.REPLACE_BATTERY -> {
                if(!powertrain().electric()||plugged()||!hoodOpen()||hoodProgress<.95){message(player,"Park, disconnect charging, switch READY off and open the hood to replace the pack.");return;}
                var item=com.photonspark.sparkmotors.charging.Electrification.PACKS.get(powertrain()).get();
                ItemStack incoming=ItemStack.EMPTY;
                for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(stack.is(item)){incoming=stack.copyWithCount(1);stack.shrink(1);break;}}
                if(incoming.isEmpty()){message(player,"Carry a matching traction battery pack. New packs are uncharged.");return;}
                var removed=item.write(new ItemStack(item),electricState.battery());
                electricState=electricState.withBattery(item.read(incoming));give(player,removed);syncElectric();player.getInventory().setChanged();
                message(player,"Pack replaced; the removed pack retains its charge, temperature and condition.");
            }
            case CarPackets.ELECTRIC_MODE -> {if(powertrain().hybrid())entityData.set(EV_MODE,Mth.clamp(a,0,2));}
            case CarPackets.CHARGE_TARGET -> {if(powertrain().plugIn())entityData.set(CHARGE_TARGET,Mth.clamp(a,50,100));}
            case CarPackets.ENGINE_SWAP -> {
                if(a<0||a>=EngineFamily.values().length||b<1||b>2)return;
                swapEngine(player,EngineFamily.values()[a],b);
            }
            case CarPackets.ENGINE_PART -> {
                if(a<0||a>=EnginePart.values().length||b<0||b>EnginePart.values()[a].maxVariant()||Assembly.ENGINE.variant(config())==0)return;
                EnginePart part=EnginePart.values()[a];int old=part.variant(engineParts());if(old==b)return;
                int next=part.with(engineParts(),b);
                // Disassembly is allowed. A complete but incompatible boost build is rejected.
                String problem=part.installationProblem(engineParts(),b);
                if(!problem.isEmpty()){message(player,problem);return;}
                if(b>0&&!consume(player,AutoPropulsionAge.enginePartItem(part,b),1)){message(player,"Required engine part is missing from your inventory.");return;}
                entityData.set(ENGINE_PARTS,next);
                engineState=EnginePhysics.State.stopped(oilTemperature(),engineHealth());entityData.set(BOOST,0f);
                if(old>0&&!player.isCreative())give(player,new ItemStack(AutoPropulsionAge.enginePartItem(part,old)));
                message(player,part.title+": "+part.label(b));
            }
            case CarPackets.REFUEL -> {
                if(powertrain().electric()&&!powertrain().hybrid()){message(player,"Electric vehicles require a charger, not fuel.");break;}
                if(fuel()>=49.9){message(player,"Fuel tank is full.");break;}
                if(consume(player,AutoPropulsionAge.FUEL_CAN.get(),1)){entityData.set(FUEL,Math.min(50,fuel()+10));message(player,"Added up to 10 L of fuel.");}
                else message(player,"You need a fuel can in your inventory.");
            }
            case CarPackets.REPAIR -> {
                if(health()>=100)break;
                if(consume(player,Items.IRON_INGOT,4)){entityData.set(HEALTH,100f);message(player,"Car repaired.");}
                else message(player,"Repair requires 4 iron ingots.");
            }
            case CarPackets.INSTALL -> {
                if(a<0||a>=Assembly.values().length||b<0||b>2)return;
                Assembly slot=Assembly.values()[a];int old=slot.variant(config());if(old==b)return;
                if(slot==Assembly.ENGINE){swapEngine(player,engineFamily(),b);return;}
                if(b>0&&!consume(player,AutoPropulsionAge.partItem(slot,b),1)){message(player,"Required assembly is missing from your inventory.");return;}
                setConfiguration(slot.with(config(),b));
                if(old>0&&!player.isCreative())give(player,new ItemStack(AutoPropulsionAge.partItem(slot,old)));
                message(player,slot.title+": "+(b==0?"removed":b==1?"stock installed":"sport installed"));
            }
            case CarPackets.PAINT -> {
                int rgb=a&0xFFFFFF;if(rgb==paint())return;
                boolean paid=player.isCreative();
                if(!paid)for(int i=0;i<player.getInventory().getContainerSize();i++){
                    ItemStack stack=player.getInventory().getItem(i);if(stack.getItem() instanceof DyeItem){stack.shrink(1);paid=true;break;}
                }
                if(paid){entityData.set(PAINT,rgb);player.getInventory().setChanged();}else message(player,"Painting requires one dye of any color.");
            }
            case CarPackets.TUNE -> {
                entityData.set(LIMITER,Mth.clamp(a,4000,7000));entityData.set(FINAL_DRIVE,Mth.clamp(b/100f,2.8f,4.8f));
                message(player,"Tune saved: "+limiter()+" RPM limiter, "+String.format(Locale.ROOT,"%.2f",finalDrive())+" final drive.");
            }
            case CarPackets.BOOST_TUNE -> {entityData.set(BOOST_TARGET,Mth.clamp(a/1000f,.2f,1.4f));message(player,"Boost target saved. Hardware limits still apply.");}
            case CarPackets.ENGINE_REBUILD -> {
                if(engineHealth()>=100||Assembly.ENGINE.variant(config())==0)return;
                if(consume(player,Items.IRON_INGOT,12)){entityData.set(ENGINE_HEALTH,100f);engineState=EnginePhysics.State.stopped(oilTemperature(),100);message(player,"Engine rebuilt. Temperatures and installed parts retained.");}
                else message(player,"Engine rebuild requires 12 iron ingots.");
            }
            default -> {}
        }
    }
    private void swapEngine(ServerPlayer player,EngineFamily family,int grade){
        int old=Assembly.ENGINE.variant(config());
        if(old==grade&&engineFamily()==family)return;
        ItemStack incoming=ItemStack.EMPTY;
        if(grade>0){
            Item wanted=AutoPropulsionAge.engineItem(family,grade);
            if(player.isCreative())incoming=new ItemStack(wanted);
            else for(int i=0;i<player.getInventory().getContainerSize();i++){
                var s=player.getInventory().getItem(i);if(s.is(wanted)){incoming=s.copyWithCount(1);s.shrink(1);break;}
            }
            if(incoming.isEmpty()){message(player,"Required engine assembly is missing from your inventory.");return;}
        }
        ItemStack removed=old==0?ItemStack.EMPTY:com.photonspark.sparkmotors.item.EngineItem.withParts(new ItemStack(AutoPropulsionAge.engineItem(engineFamily(),old)),engineParts());
        if(!removed.isEmpty())com.photonspark.sparkmotors.item.EngineItem.withTemperature(removed,temperature());
        if(!removed.isEmpty())com.photonspark.sparkmotors.item.EngineItem.withCondition(removed,oilTemperature(),engineHealth());
        setConfiguration(Assembly.ENGINE.with(config(),grade));entityData.set(ENGINE_FAMILY,family.ordinal());
        entityData.set(ENGINE_PARTS,grade==0?EnginePart.stock():com.photonspark.sparkmotors.item.EngineItem.parts(incoming));
        entityData.set(TEMPERATURE,grade==0?20f:com.photonspark.sparkmotors.item.EngineItem.temperature(incoming));entityData.set(BOOST,0f);
        entityData.set(OIL_TEMP,grade==0?20f:com.photonspark.sparkmotors.item.EngineItem.oilTemperature(incoming));entityData.set(ENGINE_HEALTH,grade==0?100f:com.photonspark.sparkmotors.item.EngineItem.health(incoming));
        engineState=EnginePhysics.State.stopped(oilTemperature(),engineHealth());
        if(!removed.isEmpty()&&!player.isCreative())give(player,removed);
        player.getInventory().setChanged();message(player,grade==0?"Engine removed with its installed parts.":family.title+" engine installed.");
    }
    private static boolean consume(Player p,Item item,int amount){
        if(p.isCreative())return true;
        int total=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))total+=p.getInventory().getItem(i).getCount();
        if(total<amount)return false;
        for(int i=0;i<p.getInventory().getContainerSize()&&amount>0;i++){
            var stack=p.getInventory().getItem(i);if(stack.is(item)){int take=Math.min(stack.getCount(),amount);stack.shrink(take);amount-=take;}
        }
        p.getInventory().setChanged();return true;
    }
    private static void give(Player p,ItemStack stack){if(!p.getInventory().add(stack))p.drop(stack,false);p.getInventory().setChanged();}
    private static void message(Player p,String text){p.displayClientMessage(Component.literal(text),true);}
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide||isInvulnerableTo(source))return false;
        if(source.getEntity() instanceof Player p&&p.isCreative()&&mayModify(p)&&getPassengers().isEmpty()){discard();return true;}
        entityData.set(HEALTH,Math.max(0,health()-Math.min(amount,50)));return true;
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){lerpX=x;lerpY=y;lerpZ=z;lerpYaw=yaw;lerpPitch=pitch;lerpSteps=Math.min(3,Math.max(1,steps));}
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putInt("DataVersion",4);
        tag.putString("Powertrain",powertrain().id());tag.putInt("ElectricMode",entityData.get(EV_MODE));tag.putInt("ChargeTarget",chargeTarget());
        if(electricState!=null){var e=electricState;var b=e.battery();tag.putDouble("BatteryJ",b.energyJ());tag.putDouble("BatteryC",b.temperatureC());tag.putDouble("BatteryHealth",b.health());tag.putDouble("BatteryThroughputJ",b.throughputJ());tag.putDouble("MotorC",e.motorC());tag.putDouble("InverterC",e.inverterC());}
        tag.putInt("Assemblies",config());tag.putInt("Paint",paint());tag.putFloat("Fuel",fuel());tag.putFloat("Health",health());
        tag.putInt("EngineFamily",engineFamily().ordinal());tag.putInt("EngineParts",engineParts());tag.putFloat("EngineTemperature",temperature());tag.putBoolean("HoodOpen",hoodOpen());
        tag.putFloat("EngineHealth",engineHealth());tag.putFloat("OilTemperature",oilTemperature());tag.putFloat("BoostTarget",boostTarget());
        tag.putInt("Limiter",limiter());tag.putFloat("FinalDrive",finalDrive());tag.putBoolean("Lights",lights());entityData.get(OWNER).ifPresent(id->tag.putUUID("Owner",id));
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        if(tag.contains("Assemblies"))setConfiguration(tag.getInt("Assemblies"));
        entityData.set(ENGINE_FAMILY,EngineFamily.byId(tag.getInt("EngineFamily")).ordinal());
        entityData.set(ENGINE_PARTS,tag.contains("EngineParts")?(tag.getInt("DataVersion")<3?EnginePart.fromLegacy(tag.getInt("EngineParts")):EnginePart.sanitize(tag.getInt("EngineParts"))):EnginePart.stock());
        entityData.set(ENGINE_HEALTH,tag.contains("EngineHealth")?(float)VehicleDynamics.clamp(tag.getFloat("EngineHealth"),0,100):100f);
        entityData.set(OIL_TEMP,tag.contains("OilTemperature")?(float)VehicleDynamics.clamp(tag.getFloat("OilTemperature"),20,180):20f);
        entityData.set(BOOST_TARGET,tag.contains("BoostTarget")?(float)VehicleDynamics.clamp(tag.getFloat("BoostTarget"),.2,1.4):1.4f);
        engineState=EnginePhysics.State.stopped(oilTemperature(),engineHealth());entityData.set(BOOST,0f);entityData.set(RPM,0f);entityData.set(SPEED,0f);entityData.set(OIL_PRESSURE,0f);entityData.set(SPOOL,0f);entityData.set(THROTTLE,0f);entityData.set(SHAFT_TORQUE,0f);entityData.set(BLOWER_KW,0f);entityData.set(AFR,14.7f);
        entityData.set(TEMPERATURE,tag.contains("EngineTemperature")?(float)VehicleDynamics.clamp(tag.getFloat("EngineTemperature"),20,150):20f);
        flag(8,tag.getBoolean("HoodOpen"));hoodProgress=oldHoodProgress=hoodOpen()?1:0;
        if(tag.contains("Paint"))entityData.set(PAINT,tag.getInt("Paint")&0xFFFFFF);
        if(tag.contains("Fuel"))entityData.set(FUEL,(float)VehicleDynamics.clamp(tag.getFloat("Fuel"),0,50));
        if(tag.contains("Health"))entityData.set(HEALTH,(float)VehicleDynamics.clamp(tag.getFloat("Health"),0,100));
        if(tag.contains("Limiter"))entityData.set(LIMITER,Mth.clamp(tag.getInt("Limiter"),4000,7000));
        if(tag.contains("FinalDrive"))entityData.set(FINAL_DRIVE,(float)VehicleDynamics.clamp(tag.getFloat("FinalDrive"),2.8,4.8));
        if(tag.hasUUID("Owner"))setOwner(tag.getUUID("Owner"));
        var type=Powertrain.byId(tag.getString("Powertrain"));initializePowertrain(type,0);
        entityData.set(EV_MODE,Mth.clamp(tag.getInt("ElectricMode"),0,2));entityData.set(CHARGE_TARGET,tag.contains("ChargeTarget")?Mth.clamp(tag.getInt("ChargeTarget"),50,100):80);
        if(type.electric()){
            var battery=new BatteryModel.State(tag.getDouble("BatteryJ"),tag.contains("BatteryC")?tag.getDouble("BatteryC"):20,tag.contains("BatteryHealth")?tag.getDouble("BatteryHealth"):1,tag.getDouble("BatteryThroughputJ")).normalized(type.battery);
            electricState=new ElectricDynamics.State(battery,tag.contains("MotorC")?VehicleDynamics.clamp(tag.getDouble("MotorC"),-60,250):20,tag.contains("InverterC")?VehicleDynamics.clamp(tag.getDouble("InverterC"),-60,250):20,false,0,0,BatteryModel.ocv(type.battery,battery),0,0);syncElectric();
        }
        chargingPos=null;chargeLease=0;entityData.set(PLUGGED,false);entityData.set(CHARGE_KW,0f);
        flag(2,tag.getBoolean("Lights"));speed=0;verticalSpeed=0;benchTicks=0;flag(1,false);
    }
}
