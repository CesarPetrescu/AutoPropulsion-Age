package com.photonspark.sparkmotors.entity;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.item.MechanicalData;
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
    private static final EntityDataAccessor<CompoundTag> MECHANICS=data(EntityDataSerializers.COMPOUND_TAG);
    private MechanicalState mechanical=MechanicalState.legacy(Assembly.stock(),EnginePart.stock(),20,20,100);
    private CompoundTag lastMechanicalTag;
    private WheelDynamics.State wheelState=WheelDynamics.State.stopped();
    private TransmissionPhysics.State transmissionState=TransmissionPhysics.State.stopped();
    private int lastImpactTick=-100,pressureTestTicks;
    private double testPressure=1;
    private static final EntityDataAccessor<String> DIAGNOSTIC=data(EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> TRIP_START=data(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DRIVE_SETUP=data(EntityDataSerializers.INT),FRONT_SPLIT=data(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LATERAL_SPEED=data(EntityDataSerializers.FLOAT),YAW_RATE=data(EntityDataSerializers.FLOAT),STEERING_ANGLE=data(EntityDataSerializers.FLOAT);
    public DriveConfig driveConfig(){return DriveConfig.decode(entityData.get(DRIVE_SETUP),entityData.get(FRONT_SPLIT));}
    public void setDriveConfig(DriveConfig value){entityData.set(DRIVE_SETUP,value.packed());entityData.set(FRONT_SPLIT,value.frontPercent());}
    public float lateralSpeed(){return entityData.get(LATERAL_SPEED);}
    public float horizontalSpeed(){return (float)Math.hypot(speed(),lateralSpeed());}
    public float yawRate(){return entityData.get(YAW_RATE);}
    private static final EntityDataAccessor<org.joml.Vector3f> CLUTCH_STATE=data(EntityDataSerializers.VECTOR3);
    public float clutchSlipRpm(){return entityData.get(CLUTCH_STATE).x;}
    public float clutchTorque(){return entityData.get(CLUTCH_STATE).y;}
    public float clutchEngagement(){return entityData.get(CLUTCH_STATE).z;}
    public float steeringAngle(){return entityData.get(STEERING_ANGLE);}
    private static final EntityDataAccessor<Float> ENGINE_LOAD=data(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VENT_EVENTS=data(EntityDataSerializers.INT);
    public float engineLoad(){return entityData.get(ENGINE_LOAD);}
    public int ventEvents(){return entityData.get(VENT_EVENTS);}
    public boolean serviceBrake(){return flag(32);}
    public boolean handbrake(){return flag(64);}
    public boolean roughSurface(){return flag(128);}
    private static final EntityDataAccessor<Float> COOLANT=data(EntityDataSerializers.FLOAT),OIL_QUANTITY=data(EntityDataSerializers.FLOAT),BRAKE_FLUID=data(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CONTACTS=data(EntityDataSerializers.INT),ENGINE_MODE=data(EntityDataSerializers.INT);
    public EnginePhysics.Mode engineMode(){return EnginePhysics.Mode.values()[entityData.get(ENGINE_MODE)];}
    public boolean engineRunning(){return engineMode()==EnginePhysics.Mode.RUNNING;}
    private static final java.util.List<EntityDataAccessor<org.joml.Vector3f>> WHEELS=java.util.List.of(data(EntityDataSerializers.VECTOR3),data(EntityDataSerializers.VECTOR3),data(EntityDataSerializers.VECTOR3),data(EntityDataSerializers.VECTOR3));
    public final float[] wheelAngles=new float[4],oldWheelAngles=new float[4];
    public float coolant(){return entityData.get(COOLANT);}public float oilQuantity(){return entityData.get(OIL_QUANTITY);}public float brakeFluid(){return entityData.get(BRAKE_FLUID);}
    public String diagnostic(){return entityData.get(DIAGNOSTIC);}
    public boolean wheelContact(int c){return (entityData.get(CONTACTS)&(1<<c))!=0;}
    public float wheelOmega(int c){return entityData.get(WHEELS.get(c)).x;}
    public float wheelTravel(int c){return entityData.get(WHEELS.get(c)).y;}
    public float wheelSlip(int c){return entityData.get(WHEELS.get(c)).z;}
    public void impactComponents(String region,double speed){if(tickCount-lastImpactTick<10)return;lastImpactTick=tickCount;setMechanics(CircuitPhysics.impact(mechanics(),region,speed));}
    private void fluidReadings(){entityData.set(COOLANT,(float)mechanical.coolant());entityData.set(OIL_QUANTITY,(float)mechanical.oil());entityData.set(BRAKE_FLUID,(float)mechanical.brakeFluid());}
    public MechanicalState mechanics(){
        if(level().isClientSide){var tag=entityData.get(MECHANICS);if(tag!=lastMechanicalTag&&!tag.isEmpty()){mechanical=MechanicalData.read(tag);lastMechanicalTag=tag;}}
        return mechanical;
    }
    public void setMechanics(MechanicalState value){
        mechanical=value;
        if(!level().isClientSide){
            entityData.set(MECHANICS,MechanicalData.write(value));fluidReadings();
            entityData.set(TEMPERATURE,(float)value.coolantTemperature());entityData.set(OIL_TEMP,(float)value.oilTemperature());
            var internal=value.get("engine.internals");entityData.set(ENGINE_HEALTH,internal==null?0f:(float)((1-internal.damage())*100));
            if(engineState!=null)engineState=new EnginePhysics.State(engineState.omega(),engineState.throttle(),engineState.spool(),engineState.boost(),value.oilTemperature(),engineState.oilPressure(),engineHealth(),engineState.afr(),engineState.shaftTorque(),engineState.blowerKw(),engineState.blowOff(),engineState.mode(),engineState.startTime());
            var clutch=value.get("driveline.clutch");if(clutch!=null&&transmissionState!=null)transmissionState=transmissionState.heat(clutch.temperature());
            if(wheelState!=null)wheelState=WheelDynamics.restoreTemperatures(wheelState,value);
        }
    }
    private double tripStart;
    public double tripKm(){return Math.max(0,mechanics().distance()/1000-entityData.get(TRIP_START));}
    private double speed,verticalSpeed,lerpX,lerpY,lerpZ;
    private float lerpYaw,lerpPitch;
    private int lerpSteps,lastInputTick=-100,inputKeys,lastActionTick=-100,benchTicks,benchRecovery;
    private float inputSteer;
    private EnginePhysics.State engineState=EnginePhysics.State.stopped(20,100);
    public float fanAngle;
    public float wheelAngle,oldWheelAngle,panelProgress,oldPanelProgress;
    public float hoodProgress,oldHoodProgress,engineAngle,oldEngineAngle;

    public CarEntity(EntityType<? extends CarEntity> type,Level level){super(type,level);blocksBuilding=true;if(!level.isClientSide)setMechanics(PowertrainTopology.fresh(Powertrain.COMBUSTION,driveConfig(),engineFamily(),config(),engineParts()));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        b.define(DRIVE_SETUP,DriveConfig.stock().packed());b.define(FRONT_SPLIT,0);b.define(LATERAL_SPEED,0f);b.define(YAW_RATE,0f);b.define(STEERING_ANGLE,0f);
        b.define(CLUTCH_STATE,new org.joml.Vector3f());b.define(TRIP_START,0f);b.define(ENGINE_LOAD,0f);b.define(VENT_EVENTS,0);b.define(DIAGNOSTIC,"No test performed.");b.define(COOLANT,8f);b.define(OIL_QUANTITY,5f);b.define(BRAKE_FLUID,1f);b.define(CONTACTS,0);b.define(ENGINE_MODE,0);for(var wheel:WHEELS)b.define(wheel,new org.joml.Vector3f());b.define(MECHANICS,new CompoundTag());b.define(SPEED,0f);b.define(RPM,0f);b.define(FUEL,40f);b.define(HEALTH,100f);b.define(STEER,0f);b.define(PITCH,0f);b.define(ROLL,0f);
        b.define(CHARGER_POSITION,BlockPos.ZERO);b.define(POWERTRAIN,0);b.define(EV_MODE,0);b.define(CHARGE_TARGET,80);b.define(PLUGGED,false);b.define(GENERATOR,false);
        b.define(SOC,0f);b.define(PACK_V,0f);b.define(PACK_A,0f);b.define(PACK_KW,0f);b.define(PACK_C,20f);b.define(PACK_HEALTH,1f);b.define(MOTOR_C,20f);b.define(INVERTER_C,20f);b.define(REGEN_KW,0f);b.define(GEN_KW,0f);b.define(CHARGE_KW,0f);
        b.define(FINAL_DRIVE,3.7f);b.define(FLAGS,0);b.define(CONFIG,Assembly.stock());b.define(PAINT,0x168A91);b.define(GEAR,1);b.define(LIMITER,6800);b.define(OWNER,Optional.empty());
        b.define(ENGINE_FAMILY,0);b.define(ENGINE_PARTS,EnginePart.stock());b.define(TEMPERATURE,20f);b.define(BOOST,0f);
        b.define(OIL_TEMP,20f);b.define(OIL_PRESSURE,0f);b.define(ENGINE_HEALTH,100f);b.define(AFR,14.7f);b.define(THROTTLE,0f);b.define(SPOOL,0f);b.define(SHAFT_TORQUE,0f);b.define(BLOWER_KW,0f);b.define(BOOST_TARGET,1.4f);
    }
    public float speed(){return entityData.get(SPEED);} public float rpm(){return entityData.get(RPM);} public float fuel(){return entityData.get(FUEL);}
    public float health(){return entityData.get(HEALTH);} public int config(){return entityData.get(CONFIG);} public int paint(){return entityData.get(PAINT);}
    public boolean reverseSelected(){return flag(256);}
    public int gear(){return entityData.get(GEAR);} public int limiter(){return entityData.get(LIMITER);} public float finalDrive(){return entityData.get(FINAL_DRIVE);}
    public float steer(){return entityData.get(STEER);} public float roadPitch(){return entityData.get(PITCH);} public float roadRoll(){return entityData.get(ROLL);}
    public boolean ignition(){return flag(1);} public boolean lights(){return flag(2);} public boolean panels(){return flag(4);}
    public boolean hoodOpen(){return flag(8);}
    public boolean raised(){return flag(16);}
    public EngineFamily engineFamily(){return EngineFamily.byId(entityData.get(ENGINE_FAMILY));}
    public int engineParts(){return entityData.get(ENGINE_PARTS);}
    public float temperature(){return entityData.get(TEMPERATURE);}public float boost(){return entityData.get(BOOST);}
    public float oilTemperature(){return entityData.get(OIL_TEMP);}public float oilPressure(){return entityData.get(OIL_PRESSURE);}
    public float engineHealth(){return entityData.get(ENGINE_HEALTH);}public float afr(){return entityData.get(AFR);}
    public float throttle(){return entityData.get(THROTTLE);}public float spool(){return entityData.get(SPOOL);}
    public float shaftTorque(){return entityData.get(SHAFT_TORQUE);}public float blowerKw(){return entityData.get(BLOWER_KW);}public float boostTarget(){return entityData.get(BOOST_TARGET);}
    public String engineProblem(){if(powertrain().electric()&&!powertrain().hybrid())return "";return Assembly.ENGINE.variant(config())==0?"No engine installed.":MechanicalCapabilities.buildProblem(engineParts());}
    public double motorRpm(){double omega=(wheelOmega(0)+wheelOmega(1))*.5*driveConfig().frontFraction()+(wheelOmega(2)+wheelOmega(3))*.5*(1-driveConfig().frontFraction());return powertrain().electric()?ElectricDynamics.motorRpm(omega):0;}
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
        var fresh=PowertrainTopology.fresh(type,driveConfig(),engineFamily(),config(),engineParts());
        var prior=PowertrainTopology.migrate(mechanics(),type,driveConfig(),engineFamily(),config(),engineParts());
        for(var slot:PowertrainTopology.slots(type,driveConfig(),engineFamily()))if(prior.get(slot.key())!=null)fresh=fresh.with(slot.key(),prior.get(slot.key()));
        fresh=fresh.fluids(prior.coolant(),type.electric()&&!type.hybrid()?0:prior.oil(),prior.brakeFluid());
        entityData.set(POWERTRAIN,type.ordinal());electricState=type.electric()?ElectricDynamics.State.initial(type,soc):null;
        setMechanics(fresh);
        if(type.electric()&&!type.hybrid())entityData.set(FUEL,0f);
        syncElectric();
    }
    public double accessoryVoltage(){
        if(flag(512))return 14;
        return powertrain().electric()&&!powertrain().hybrid()?10.5+Math.min(1,CircuitPhysics.batteryCharge(mechanics())/48)*2.2:CircuitPhysics.measure(mechanics(),rpm(),engineRunning(),speed()).voltage();
    }
    public double ambientTemperature(){return VehicleDynamics.clamp(4+20*level().getBiome(blockPosition()).value().getBaseTemperature()-Math.max(0,getY()-64)*.0065,-35,45);}
    public BatteryModel.State tractionBattery(){return electricState==null?null:electricState.battery();}
    public boolean attachCharger(BlockPos pos){
        if(level().isClientSide||!powertrain().plugIn()||!PowertrainTopology.liveHv(mechanics())||ignition()||horizontalSpeed()>.1||distanceToSqr(Vec3.atCenterOf(pos))>36)return false;
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
        if(horizontalSpeed()<.5)flag(256,(keys&8)!=0);
        inputKeys=(keys&23)|(reverseSelected()?8:0);inputSteer=Mth.clamp(steer,-1,1);lastInputTick=tickCount;
    }
    @Override public void tick(){
        super.tick();oldWheelAngle=wheelAngle;oldPanelProgress=panelProgress;oldHoodProgress=hoodProgress;oldEngineAngle=engineAngle;
        wheelAngle+=speed()*.05f/.34f;for(int c=0;c<4;c++){
            oldWheelAngles[c]=wheelAngles[c];wheelAngles[c]+=wheelOmega(c)*.05f;
            float offset=(float)(Math.floor(wheelAngles[c]/(Math.PI*2))*Math.PI*2);wheelAngles[c]-=offset;oldWheelAngles[c]-=offset;
        }
        panelProgress=Mth.clamp(panelProgress+(panels()?.09f:-.09f),0,1);
        hoodProgress=Mth.clamp(hoodProgress+(hoodOpen()?.09f:-.09f),0,1);
        if(CircuitPhysics.measure(mechanics(),rpm(),ignition(),speed(),powertrain().electric()&&!powertrain().hybrid()).fan())fanAngle+=6;
        engineAngle+=rpm()*.05f*(float)(Math.PI/30);
        if(level().isClientSide){
            if(lerpSteps>0){
                setPos(getX()+(lerpX-getX())/lerpSteps,getY()+(lerpY-getY())/lerpSteps,getZ()+(lerpZ-getZ())/lerpSteps);
                setYRot(getYRot()+Mth.wrapDegrees(lerpYaw-getYRot())/lerpSteps);setXRot(lerpPitch);lerpSteps--;
                rebaseHeading();
                setBoundingBox(makeBoundingBox());
            }
            return;
        }
        if(entityData.get(MECHANICS).isEmpty())setMechanics(mechanical);
        boolean driver=getControllingPassenger() instanceof Player;
        if(!driver||tickCount-lastInputTick>10){inputKeys=20;inputSteer=0;}
        if(chargingPos!=null&&(chargeLease<tickCount||!level().hasChunkAt(chargingPos))){chargingPos=null;entityData.set(PLUGGED,false);entityData.set(CHARGE_KW,0f);}
        if(isInWater()||plugged()||powertrain().electric()&&!PowertrainTopology.liveHv(mechanics())||(!powertrain().electric()&&(engineHealth()<=5||fuel()<=0||!engineProblem().isEmpty())))flag(1,false);
        if(!ignition()){benchTicks=0;benchRecovery=0;}
        if(benchTicks>0){benchTicks--;benchRecovery=100;inputKeys=23;inputSteer=0;}
        else if(benchRecovery>0){benchRecovery--;inputKeys=22;inputSteer=0;if(rpm()<1100&&throttle()<.05)benchRecovery=0;}
        boolean reverse=reverseSelected();
        boolean braking=(inputKeys&2)!=0;flag(32,braking);flag(64,(inputKeys&4)!=0);flag(128,surfaceGrip()<.9);
        var input=new VehicleDynamics.Input((inputKeys&1)!=0?1:0,inputSteer,braking,reverse,(inputKeys&16)!=0,(inputKeys&4)!=0);
        var setup=new VehicleDynamics.Setup(config(),limiter(),finalDrive(),engineFamily(),engineParts(),temperature(),boostTarget(),mechanics(),driveConfig()).withPowertrain(powertrain());
        double[] contact=wheelContacts();
        int contactCount=0;
        float currentFuel=fuel();
        double tickImpact=0;VehicleCollision.Hit impactContact=null;
        double auxiliaryW=0;
        for(int i=0;i<4;i++){
            contact=wheelContacts();contactCount=0;
            boolean[] touching=new boolean[4];double[] travels=new double[4],gaps=new double[4],grips=new double[4];
            for(int c=0;c<4;c++){
                gaps[c]=getY()-contact[c];touching[c]=Double.isFinite(contact[c])&&!raised()&&gaps[c]<=SuspensionPhysics.REACH;
                if(touching[c])contactCount++;
                travels[c]=touching[c]?Math.clamp(-gaps[c],-SuspensionPhysics.REACH,SuspensionPhysics.MAX_BUMP):-SuspensionPhysics.REACH;
                grips[c]=touching[c]?surfaceGrip(new Vec3(wheelPoint(c).x,contact[c]-.02,wheelPoint(c).z)):0;
                if(!touching[c])gaps[c]=Double.NaN;
            }
            VehicleDynamics.State state;
            if(powertrain().electric()){
                if(electricState==null)electricState=ElectricDynamics.State.initial(powertrain(),0);
                var result=ElectricDynamics.step(powertrain(),electricState,electricMode(),speed,currentFuel,engineState,wheelState,transmissionState,ignition(),input,setup,grips,touching,travels,plugged(),.0125,ambientTemperature());
                electricState=result.electric();state=result.road();auxiliaryW+=result.auxiliaryW()/4;if(result.mechanics()!=null){mechanical=result.mechanics();setup=setup.withMechanics(mechanical);}
            }else state=VehicleDynamics.step(speed,currentFuel,engineState,wheelState,transmissionState,ignition(),input,setup,grips,touching,travels,.0125);
            if(state.engine().blowOff()&&!engineState.blowOff())entityData.set(VENT_EVENTS,ventEvents()+1);entityData.set(ENGINE_LOAD,(float)state.load());
            engineState=state.engine();wheelState=state.wheels();transmissionState=state.transmission();
            if(!powertrain().electric()&&engineState.mode()==EnginePhysics.Mode.STALLED)flag(1,false);
            speed=state.speed();currentFuel=(float)state.fuel();
            entityData.set(RPM,(float)state.rpm());entityData.set(GEAR,state.gear());
            double previousYaw=Math.toRadians(getYRot()),attemptedYaw=previousYaw+state.yawDelta();
            // The simulation already rotated velocity into the attempted body frame. Keep its
            // world momentum even when contact permits only part of the requested body rotation.
            double vx=CarGeometry.worldX(speed,transmissionState.lateralSpeed(),attemptedYaw),vz=CarGeometry.worldZ(speed,transmissionState.lateralSpeed(),attemptedYaw);
            verticalSpeed=raised()?0:VehicleDynamics.clamp(verticalSpeed+SuspensionPhysics.acceleration(gaps,verticalSpeed,mechanics(),driveConfig(),powertrain().massKg)*.0125,-30,30);
            VehicleCollision.Result resolved;
            if(contactCount==4&&Math.abs(vx)+Math.abs(vz)+Math.abs(verticalSpeed)+Math.abs(transmissionState.yawRate())<.0001){
                // Settled vehicles need no repeated broad-phase world scan. Suspension and
                // mechanics still run, so losing support or fitting a part wakes motion normally.
                resolved=new VehicleCollision.Result(getX(),getY(),getZ(),previousYaw,0,0,0,0,false,false,0,null);
            }else{
                var area=makeBoundingBox().inflate(.25).expandTowards(vx*.0125,verticalSpeed*.0125,vz*.0125);
                resolved=VehicleCollision.move(collisionHull(),collisionObstacles(area),getX(),getY(),getZ(),previousYaw,state.yawDelta(),vx,verticalSpeed,vz,transmissionState.yawRate(),powertrain().massKg,.0125);
            }
            double dy=resolved.y()-getY();
            setYRot((float)Math.toDegrees(resolved.yaw()));setPos(resolved.x(),resolved.y(),resolved.z());
            horizontalCollision=resolved.horizontal();verticalCollision=resolved.vertical();verticalCollisionBelow=verticalCollision&&verticalSpeed<0;
            setOnGround(verticalCollisionBelow||contactCount>0&&Math.abs(verticalSpeed)<.1);verticalSpeed=resolved.vy();
            var ground=getOnPos();checkFallDamage(dy,onGround(),level().getBlockState(ground),ground);
            if(resolved.impact()>tickImpact){tickImpact=resolved.impact();impactContact=resolved.contact();}
            double yaw=resolved.yaw();speed=CarGeometry.forward(resolved.vx(),resolved.vz(),yaw);
            transmissionState=transmissionState.motion(CarGeometry.lateral(resolved.vx(),resolved.vz(),yaw),resolved.yawRate(),transmissionState.steering(),transmissionState.longitudinalAcceleration(),transmissionState.lateralAcceleration());
        }
        if(tickImpact>5&&impactContact!=null&&tickCount-lastImpactTick>=10){
            var point=new Vec3(impactContact.x()-getX(),0,impactContact.z()-getZ()).yRot((float)Math.toRadians(getYRot()));
            String region=Math.abs(point.z)>1.8?(point.z>0?"front":"rear"):(point.z>=0?"f":"r")+(point.x<0?"l":"r");
            impactComponents(region,tickImpact);entityData.set(HEALTH,Math.max(0,health()-(float)(tickImpact-5)*1.3f));
            playSound(AutoPropulsionAge.MECHANICAL_SOUNDS.get("impact").get(),.5f,1);
        }
        // Rebase the old and current angle together: clients interpolate a short arc even
        // after many spins, and the server never accumulates a low-precision huge float yaw.
        rebaseHeading();
        tryCheckInsideBlocks();
        entityData.set(CLUTCH_STATE,new org.joml.Vector3f((float)transmissionState.clutchSlipRpm(),(float)transmissionState.clutchTorque(),(float)transmissionState.clutchEngagement()));
        entityData.set(LATERAL_SPEED,(float)transmissionState.lateralSpeed());entityData.set(YAW_RATE,(float)transmissionState.yawRate());entityData.set(STEERING_ANGLE,(float)transmissionState.steering());
        syncElectric();
        entityData.set(FUEL,currentFuel);entityData.set(SPEED,(float)speed);entityData.set(STEER,inputSteer);
        float throttle=(inputKeys&1)!=0?1:0;
        entityData.set(BOOST,(float)engineState.boost());entityData.set(OIL_TEMP,(float)engineState.oilTemperature());entityData.set(OIL_PRESSURE,(float)engineState.oilPressure());
        entityData.set(ENGINE_HEALTH,(float)engineState.health());entityData.set(AFR,(float)engineState.afr());entityData.set(THROTTLE,(float)engineState.throttle());entityData.set(SPOOL,(float)engineState.spool());
        entityData.set(SHAFT_TORQUE,(float)engineState.shaftTorque());entityData.set(BLOWER_KW,(float)engineState.blowerKw());
        double roadSpeed=Math.hypot(speed,transmissionState.lateralSpeed());
        boolean electricOnly=powertrain().electric()&&!powertrain().hybrid();
        mechanical=CircuitPhysics.step(mechanical,rpm(),throttle,boost(),electricOnly?ignition():engineState.mode()==EnginePhysics.Mode.RUNNING,engineState.mode()==EnginePhysics.Mode.CRANKING,lights(),roadSpeed,braking,.05,electricOnly);
        if(!powertrain().electric()||powertrain().hybrid())mechanical=InternalMechanics.step(mechanical,engineFamily(),rpm(),throttle,engineState.mode()==EnginePhysics.Mode.RUNNING,.05);
        flag(512,false);
        // 10 A auxiliary charging at 14 V is included in the traction model's 450 W accessory budget.
        if(powertrain().electric()&&ignition()&&auxiliaryW>=140&&PowertrainTopology.liveHv(mechanical)&&mechanical.capability("traction.dc_dc")>.2){
            var accessory=mechanical.get("electrical.battery");if(accessory!=null){flag(512,true);mechanical=mechanical.with("electrical.battery",accessory.operating(Math.min(48,accessory.reserve()+10*.05/3600),accessory.temperature()));}
        }
        mechanical=WheelDynamics.wear(mechanical,wheelState,roadSpeed,.05);
        var internals=mechanical.get("engine.internals");
        if(internals!=null){double damage=Math.max(internals.damage(),1-engineState.health()/100);mechanical=mechanical.with("engine.internals",internals.condition(internals.wear(),damage,internals.faults()));entityData.set(ENGINE_HEALTH,(float)(100*(1-damage)));}
        engineState=new EnginePhysics.State(engineState.omega(),engineState.throttle(),engineState.spool(),engineState.boost(),engineState.oilTemperature(),engineState.oilPressure(),engineHealth(),engineState.afr(),engineState.shaftTorque(),engineState.blowerKw(),engineState.blowOff(),engineState.mode(),engineState.startTime());
        var clutch=mechanical.get("driveline.clutch");if(clutch!=null)mechanical=mechanical.with("driveline.clutch",clutch.condition(clutch.wear()+Math.max(0,transmissionState.clutchHeat()-220)*.0000005*.05,clutch.damage(),clutch.faults()).operating(clutch.reserve(),transmissionState.clutchHeat()));
        entityData.set(ENGINE_MODE,engineState.mode().ordinal());
        entityData.set(TEMPERATURE,(float)mechanical.coolantTemperature());fluidReadings();
        int mask=0;for(int c=0;c<4;c++){var wheel=wheelState.corners().get(c);if(wheel.contact())mask|=1<<c;entityData.set(WHEELS.get(c),new org.joml.Vector3f((float)wheel.omega(),(float)wheel.travel(),(float)wheel.slip()));}entityData.set(CONTACTS,mask);
        if(tickCount%20==0)setMechanics(mechanical);
        if(pressureTestTicks>0){
            if(ignition()){pressureTestTicks=0;entityData.set(DIAGNOSTIC,"Pressure test cancelled: engine started.");}
            else{testPressure*=CircuitPhysics.pressureHold(mechanics(),.05);pressureTestTicks--;if(pressureTestTicks%20==0)entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"Cooling pressure: %.2f bar / 1.00 initial. %s",testPressure,pressureTestTicks==0?(testPressure>.90?"Holds pressure.":"Pressure loss: inspect circuit joints and radiator."):(pressureTestTicks/20)+" seconds remaining."));}
        }
        if(tickCount%10==0&&coolant()>0&&CircuitPhysics.coolantLeak(mechanics())>.005&&level() instanceof net.minecraft.server.level.ServerLevel server){var at=position().add(new Vec3(.35,.6,1.7).yRot((float)-Math.toRadians(getYRot())));server.sendParticles(net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER,at.x,at.y,at.z,2,.08,.04,.08,0);}
        double worldYaw=Math.toRadians(getYRot());setDeltaMovement(CarGeometry.worldX(speed,transmissionState.lateralSpeed(),worldYaw)/20,verticalSpeed/20,CarGeometry.worldZ(speed,transmissionState.lateralSpeed(),worldYaw)/20);
        if(contactCount==4){
            float pitch=(float)Math.toDegrees(Math.atan2((contact[0]+contact[1]-contact[2]-contact[3])/2,2.65));
            float roll=(float)Math.toDegrees(Math.atan2((contact[0]+contact[2]-contact[1]-contact[3])/2,1.66));
            float nextPitch=Mth.lerp(.25f,roadPitch(),Mth.clamp(pitch+(float)transmissionState.longitudinalAcceleration()*.5f,-18,18));
            float nextRoll=Mth.lerp(.25f,roadRoll(),Mth.clamp(roll-(float)transmissionState.lateralAcceleration()*.7f,-18,18));
            double[] travel=new double[4];for(int c=0;c<4;c++)travel[c]=wheelState.corners().get(c).travel();
            double fit=CarGeometry.tiltFraction(nextPitch,nextRoll,travel);
            entityData.set(PITCH,(float)(nextPitch*fit));entityData.set(ROLL,(float)(nextRoll*fit));
        }
    }
    private double surfaceGrip(){return surfaceGrip(position().add(0,-.2,0));}
    private double surfaceGrip(Vec3 point){
        var block=level().getBlockState(BlockPos.containing(point));
        if(block.is(net.minecraft.tags.BlockTags.ICE))return .22;
        if(block.is(net.minecraft.tags.BlockTags.SAND)||block.is(net.minecraft.tags.BlockTags.DIRT)||block.is(net.minecraft.world.level.block.Blocks.GRAVEL))return .70;
        return 1;
    }
    private Vec3 wheelPoint(int c){return position().add(new Vec3(CarGeometry.wheelX(c),0,CarGeometry.wheelZ(c)).yRot((float)-Math.toRadians(getYRot())));}
    private double[] wheelContacts(){
        double[] heights=new double[4];
        for(int c=0;c<4;c++){
            Vec3 point=wheelPoint(c),from=point.add(0,.16,0),to=point.add(0,-SuspensionPhysics.REACH,0);
            var hit=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            heights[c]=hit.getType()==HitResult.Type.BLOCK?hit.getLocation().y:Double.NaN;
        }
        return heights;
    }
    public List<VehicleCollision.Box> collisionHull(){
        double[] travels=new double[4];for(int c=0;c<4;c++)travels[c]=wheelState==null?0:level().isClientSide?wheelTravel(c):wheelState.corners().get(c).travel();
        return CarGeometry.hull(Assembly.BODY.variant(config())==2,Assembly.WHEELS.variant(config())>0,travels,level().isClientSide?steeringAngle():transmissionState.steering());
    }
    private void rebaseHeading(){float offset=getYRot()-Mth.wrapDegrees(getYRot());setYRot(getYRot()-offset);yRotO-=offset;}
    public boolean hasBodyClearance(){return VehicleCollision.clear(collisionHull(),collisionObstacles(makeBoundingBox()),getX(),getY(),getZ(),Math.toRadians(getYRot()));}
    private List<VehicleCollision.Box> collisionObstacles(AABB area){
        var boxes=new ArrayList<VehicleCollision.Box>();
        for(var shape:level().getBlockCollisions(this,area))for(var b:shape.toAabbs())boxes.add(VehicleCollision.Box.bounds(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
        for(var other:level().getEntities(this,area,e->canCollideWith(e))){
            if(other instanceof CarEntity car){for(var b:car.collisionHull())boxes.add(b.at(car.getX(),car.getY(),car.getZ(),Math.toRadians(car.getYRot())));}
            else{var b=other.getBoundingBox();boxes.add(VehicleCollision.Box.bounds(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));}
        }
        var border=level().getWorldBorder();
        if(area.minX<border.getMinX()||area.maxX>border.getMaxX()||area.minZ<border.getMinZ()||area.maxZ>border.getMaxZ())for(var b:border.getCollisionShape().toAabbs()){
            // Border voxel shapes use infinite Y; keep the SAT arithmetic finite.
            double x0=Math.max(b.minX,area.minX-10),z0=Math.max(b.minZ,area.minZ-10),x1=Math.min(b.maxX,area.maxX+10),z1=Math.min(b.maxZ,area.maxZ+10);
            if(x0<x1&&z0<z1)boxes.add(VehicleCollision.Box.bounds(x0,area.minY-10,z0,x1,area.maxY+10,z1));
        }
        return boxes;
    }
    @Override protected AABB makeBoundingBox(){
        double yaw=Math.toRadians(getYRot()),c=Math.abs(Math.cos(yaw)),s=Math.abs(Math.sin(yaw));
        double x=1.12*c+2.37*s,z=2.37*c+1.12*s;
        // Broad phase and entity queries only. Movement uses the oriented component hull.
        return new AABB(getX()-x,getY(),getZ()-z,getX()+x,getY()+1.52,getZ()+z);
    }
    @Override public float maxUpStep(){return horizontalSpeed()<4?.3f:0;}
    @Override public boolean isPickable(){return true;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeCollidedWith(){return isAlive();}
    @Override public boolean canCollideWith(Entity other){return other.canBeCollidedWith()&&!isPassengerOfSameVehicle(other);}
    @Override public boolean isControlledByLocalInstance(){return false;}
    @Override public LivingEntity getControllingPassenger(){return getFirstPassenger() instanceof LivingEntity l?l:null;}
    @Override protected boolean canAddPassenger(Entity p){return getPassengers().isEmpty()&&horizontalSpeed()<1;}
    @Override protected Vec3 getPassengerAttachmentPoint(Entity p,EntityDimensions dimensions,float scale){
        return new Vec3(-.40,.18,.12).yRot((float)-Math.toRadians(getYRot()));
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
        else if(horizontalSpeed()<1)player.startRiding(this);
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
        if(action==CarPackets.COMPONENT_SWAP&&powertrain().electric()&&!powertrain().hybrid()&&a==ComponentSlot.ALL.indexOf(ComponentSlot.engine(EnginePart.COOLING))&&b>=0&&b<=1){action=CarPackets.ENGINE_PART;a=EnginePart.COOLING.ordinal();}
        if(action==CarPackets.TRIP_RESET){tripStart=mechanics().distance();entityData.set(TRIP_START,(float)(tripStart/1000));return;}
        if(action==CarPackets.LIGHTS){flag(2,!lights());return;}
        if(action==CarPackets.HORN){playSound(AutoPropulsionAge.MECHANICAL_SOUNDS.get("horn").get(),.7f,1);return;}
        if(action==CarPackets.DIAGNOSE&&a>=3&&a<=5){
            if(a>=4&&powertrain().electric()&&!powertrain().hybrid()){message(player,"No combustion oil circuit or cylinders. Inspect drive units in Electric diagnostics.");return;}
            if(a==3&&hasTool(player,"multimeter"))entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"Battery terminals %.2f V; stored charge %.1f Ah. Mode: %s.",accessoryVoltage(),CircuitPhysics.batteryCharge(mechanics()),engineMode().name().toLowerCase(Locale.ROOT)));
            if(a==4&&hasTool(player,"oil_pressure_gauge"))entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"Mechanical oil gauge: %.2f bar at %.0f RPM. Sender bypassed by this physical test.",CircuitPhysics.measure(mechanics(),rpm(),engineRunning(),speed()).oilPressure(),rpm()));
            if(a==5&&hasTool(player,"compression_tester")){
                if(ignition()||!hoodOpen()||!MechanicalCapabilities.canCrank(mechanics(),engineFamily())){message(player,"Stop the engine, open the hood, and provide a working starter circuit.");return;}
                entityData.set(DIAGNOSTIC,InternalMechanics.report(mechanics(),engineFamily()));
            }return;
        }
        if(action==CarPackets.REV_TEST){
            if(powertrain().electric()){message(player,"Electric drive has no clutch rev test. Use Electric diagnostics for motor and generator readings.");return;}
            if(engineRunning()&&horizontalSpeed()<.3&&hoodOpen()){benchTicks=40;message(player,"Two-second rev test. Clutch disengaged and brakes held.");}
            else message(player,"Park, open the hood and start the engine before a rev test.");return;
        }
        if(action==CarPackets.IGNITION){
            if(ignition()){flag(1,false);return;}
            if(raised()){message(player,"Lower the service jack before starting.");return;}
            if(powertrain().electric()){
                if(plugged()){message(player,"Disconnect the charging cable before selecting READY.");return;}
                if(isInWater()||packHealth()<.1||!PowertrainTopology.liveHv(mechanics())){message(player,"Test the traction pack, HV harness/contactor and 12 V supply before READY.");return;}
                flag(1,true);playSound(SoundEvents.LEVER_CLICK,.5f,1.5f);return;
            }
            if(!engineProblem().isEmpty()){message(player,engineProblem());return;}
            if(!MechanicalCapabilities.canCrank(mechanics(),engineFamily())){message(player,"Starter cannot turn the engine. Test battery, starting circuit and mechanical resistance.");return;}
            if(engineHealth()<=5){message(player,"Engine worn out. Rebuild it in the engine workshop.");return;}
            if(temperature()>=125){message(player,"Engine too hot. Let it cool before restarting.");return;}
            if(fuel()<=0||isInWater()){message(player,"Refuel or repair the car before starting.");return;}
            engineState=EnginePhysics.State.stopped(oilTemperature(),engineHealth());flag(1,true);playSound(SoundEvents.PISTON_EXTEND,.7f,.7f);return;
        }
        if(horizontalSpeed()>.3){message(player,"Stop the car before servicing it.");return;}
        if(action==CarPackets.PANELS){boolean open=!panels();flag(4,open);flag(8,open);return;}
        if(action==CarPackets.HOOD){flag(8,!hoodOpen());playSound(AutoPropulsionAge.MECHANICAL_SOUNDS.get("latch").get(),.4f,1);return;}
        if(ignition()){message(player,"Switch the engine off before servicing.");return;}
        boolean engineWork=action==CarPackets.ENGINE_SWAP||action==CarPackets.ENGINE_PART||action==CarPackets.ENGINE_REBUILD||(action==CarPackets.INSTALL&&a==Assembly.ENGINE.ordinal());
        if(engineWork&&powertrain().electric()&&!powertrain().hybrid()&&!(action==CarPackets.ENGINE_PART&&a==EnginePart.COOLING.ordinal())){message(player,"This vehicle has an electric drive unit, not a combustion engine.");return;}
        if(engineWork&&(!hoodOpen()||hoodProgress<.95)){message(player,"Open the hood fully before working on the engine.");return;}
        switch(action){
            case CarPackets.DRIVE_SETUP -> {
                if(!DriveConfig.valid(a,b)){message(player,"Invalid driveline configuration.");return;}
                var next=DriveConfig.decode(a,b);if(next.equals(driveConfig()))return;
                boolean conversion=next.layout()!=driveConfig().layout()||next.differential()!=driveConfig().differential();
                if(conversion&&!raised()){message(player,"Leave the car and raise it on a service jack before converting the driveline.");return;}
                if(Assembly.TRANSMISSION.variant(config())==0||PowertrainTopology.availability(mechanics(),powertrain(),driveConfig())<=.01){message(player,"Repair the installed torque path before a driveline conversion.");return;}
                if(!convertDriveline(player,next,conversion))return;
                setDriveConfig(next);transmissionState=transmissionState.motion(0,0,0,0,0);
                message(player,next.layout()+" / "+next.differential().name().replace('_',' ')+" saved. Removed components returned; retained parts keep their condition.");
            }
            case CarPackets.DIAGNOSE -> {
                if(a==0){if(!hasTool(player,"pressure_tester"))return;if(!hoodOpen()||temperature()>60){message(player,"Open the hood and let the coolant cool below 60 C before pressure testing.");return;}pressureTestTicks=200;testPressure=1;entityData.set(DIAGNOSTIC,"Cooling circuit pumped to 1.00 bar. Holding for ten seconds.");}
                if(a==1){if(!player.isCreative()&&player.getInventory().countItem(AutoPropulsionAge.WRENCH.get())==0){message(player,"Use the garage wrench for fluid inspection.");return;}entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"Fluid inspection: coolant %.2f / 8 L; oil %.2f / 5 L; brake reservoir %.2f / 1 L.",coolant(),oilQuantity(),brakeFluid()));}
                if(a==2){if(!hasTool(player,"tire_gauge")||b<0||b>3)return;var tire=mechanics().get("wheel."+ComponentSlot.CORNERS[b]+".tire");entityData.set(DIAGNOSTIC,tire==null?"No tire at this corner.":String.format(Locale.ROOT,"%s tire pressure %.2f bar (cold target 2.30). Tread remaining %.0f%%.",ComponentSlot.CORNERS[b],tire.reserve(),(1-tire.wear())*100));}
                if(a==3){if(!hasTool(player,"multimeter"))return;entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"Battery terminals: %.2f V. Charge %.1f Ah.",CircuitPhysics.measure(mechanics(),rpm(),ignition(),speed()).voltage(),CircuitPhysics.batteryCharge(mechanics())));}
            }
            case CarPackets.CLEAR_FAULTS -> {setMechanics(mechanics().update(mechanics().parts(),coolant(),oilQuantity(),brakeFluid(),temperature(),oilTemperature(),mechanics().distance(),Set.of()));entityData.set(DIAGNOSTIC,"Stored warnings cleared. Active conditions will be logged again.");}
            case CarPackets.FLUID_SERVICE -> {
                if(a==3){
                    if(b<0||b>3||!hasTool(player,"tire_pump"))return;String key="wheel."+ComponentSlot.CORNERS[b]+".tire";var tire=mechanics().get(key);if(tire==null){message(player,"Install a tire first.");return;}
                    setMechanics(mechanics().with(key,tire.operating(2.3,tire.temperature())));entityData.set(DIAGNOSTIC,"Tire inflated to 2.30 bar. Existing punctures remain and can leak again.");return;
                }
                if(!hoodOpen()||hoodProgress<.95){message(player,"Open the hood fully for fluid service.");return;}
                if(a<0||a>2)return;
                if(a==1&&powertrain().electric()&&!powertrain().hybrid()){message(player,"This electric vehicle has no combustion oil circuit.");return;}
                if((a==0&&temperature()>60)||(a==1&&oilTemperature()>70)){message(player,"Let the fluid cool before opening its circuit.");return;}
                double current=a==0?mechanics().coolant():a==1?mechanics().oil():mechanics().brakeFluid(),capacity=a==0?8:a==1?5:1;
                if(capacity-current<.00001){message(player,"The reservoir is full.");return;}
                String item=a==0?"coolant_bottle":a==1?"oil_bottle":"brake_fluid_bottle";var bottle=takePart(player,AutoPropulsionAge.PART_ITEMS.get(item).get());
                if(bottle.isEmpty()){message(player,"A filled "+item.replace('_',' ')+" is required.");return;}
                var contents=MechanicalData.get(bottle);double available=contents==null?1:a==0?contents.coolant():a==1?contents.oil():contents.brakeFluid();
                double amount=Math.min(available,capacity-current),left=available-amount;
                setMechanics(mechanics().fluids(a==0?current+amount:mechanics().coolant(),a==1?current+amount:mechanics().oil(),a==2?current+amount:mechanics().brakeFluid()));
                if(!player.isCreative()){if(left>.00001)give(player,MechanicalData.set(bottle,MechanicalState.empty().fluids(a==0?left:0,a==1?left:0,a==2?left:0)));else give(player,new ItemStack(Items.GLASS_BOTTLE));}
                entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"Added %.2f L. Bottle retains %.2f L. Existing damage and leaks retained.",amount,left));
            }
            case CarPackets.JACK -> {
                if(!getPassengers().isEmpty()){message(player,"Leave the car before lifting it.");return;}
                if(!raised()&&!player.isCreative()&&player.getInventory().countItem(AutoPropulsionAge.PART_ITEMS.get("service_jack").get())==0){message(player,"A service jack is required.");return;}
                if(!raised()&&!level().noCollision(this,getBoundingBox().move(0,.5,0))){message(player,"Clear the space above the car first.");return;}
                setPos(getX(),getY()+(raised()?-.5:.5),getZ());flag(16,!raised());verticalSpeed=0;speed=0;transmissionState=transmissionState.motion(0,0,0,0,0);
            }
            case CarPackets.COMPONENT_SWAP -> {
                if(a<0||a>=ComponentSlot.ALL.size()||b<0||b>2)return;
                var slot=ComponentSlot.ALL.get(a);
                if(!PowertrainTopology.applicable(slot,powertrain(),driveConfig(),engineFamily())&&b!=0){message(player,"This mount is not used by the installed powertrain.");return;}
                if(slot.key().startsWith("traction.")&&plugged()){message(player,"Unplug the charging cable before electrical service.");return;}
                if(slot.hardware()!=null){message(player,"Use the Engine tab for this hardware assembly.");return;}
                if(slot.assembly().variant(config())==0){message(player,"Fit the parent assembly first.");return;}
                if(slot.access()==ComponentSlot.Access.HOOD&&(!hoodOpen()||hoodProgress<.95)){message(player,"Open the hood fully first.");return;}
                if(slot.access()==ComponentSlot.Access.LIFT&&!raised()){message(player,"Raise the parked car with a service jack first.");return;}
                if(b==2&&!slot.key().equals("exhaust.muffler"))return;
                var old=mechanics().get(slot.key());if(b==0&&old==null)return;
                ItemStack incoming=b==0?ItemStack.EMPTY:takePart(player,AutoPropulsionAge.PART_ITEMS.get(b==2?"sport_muffler":slot.item()).get());
                if(b>0&&incoming.isEmpty()){message(player,"Required component is missing from your inventory.");return;}
                var replacement=b==0?null:MechanicalData.part(incoming,slot);
                if(b>0&&replacement==null){give(player,incoming);message(player,"Invalid component data; item returned.");return;}
                setMechanics(mechanics().with(slot.key(),replacement));
                if(old!=null&&!player.isCreative())give(player,MechanicalData.single(new ItemStack(AutoPropulsionAge.PART_ITEMS.get(old.item()).get()),slot,old));
                playSound(AutoPropulsionAge.MECHANICAL_SOUNDS.get("latch").get(),.3f,1);message(player,slot.title()+": "+(b==0?"removed with its condition":"installed; existing fluid quantities retained"));
            }
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
                var slot=ComponentSlot.engine(part);ItemStack incoming=b==0?ItemStack.EMPTY:takePart(player,AutoPropulsionAge.enginePartItem(part,b));
                if(b>0&&incoming.isEmpty()){message(player,"Required engine part is missing from your inventory.");return;}
                var replacement=b==0?null:MechanicalData.part(incoming,slot);
                if(b>0&&replacement==null){give(player,incoming);message(player,"Invalid component data; item returned.");return;}
                var removed=mechanics().get(slot.key());
                if(part==EnginePart.INTERNALS){
                    var bundle=b==0?MechanicalState.empty():MechanicalData.internalBundle(incoming,engineFamily(),slot,replacement);
                    if(bundle==null){give(player,incoming);message(player,"Invalid internal assembly data; item returned.");return;}
                    var saved=mechanics().select(s->s.key().equals(slot.key())||InternalMechanics.internal(s.key()),false);
                    setMechanics(mechanics().replace(s->s.key().equals(slot.key())||InternalMechanics.internal(s.key()),bundle,false));
                    if(old>0&&removed!=null&&!player.isCreative())give(player,MechanicalData.set(new ItemStack(AutoPropulsionAge.enginePartItem(part,old)),saved));
                    removed=null;
                }else setMechanics(mechanics().with(slot.key(),replacement));
                entityData.set(ENGINE_PARTS,next);
                engineState=EnginePhysics.State.stopped(oilTemperature(),engineHealth());entityData.set(BOOST,0f);
                if(old>0&&removed!=null&&!player.isCreative())give(player,MechanicalData.single(new ItemStack(AutoPropulsionAge.enginePartItem(part,old)),slot,removed));
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
                if(consume(player,Items.IRON_INGOT,4)){entityData.set(HEALTH,100f);for(String key:java.util.List.of("body.front","body.rear")){var p=mechanics().get(key);if(p!=null)setMechanics(mechanics().with(key,p.condition(p.wear(),0,0)));}message(player,"Body panels repaired. Mechanical faults and fluid levels retained.");}
                else message(player,"Repair requires 4 iron ingots.");
            }
            case CarPackets.INSTALL -> {
                if(a<0||a>=Assembly.values().length||b<0||b>2)return;
                Assembly slot=Assembly.values()[a];int old=slot.variant(config());if(old==b)return;
                if(slot==Assembly.ENGINE){swapEngine(player,engineFamily(),b);return;}
                ItemStack incoming=b==0?ItemStack.EMPTY:takePart(player,AutoPropulsionAge.partItem(slot,b));
                if(b>0&&incoming.isEmpty()){message(player,"Required assembly is missing from your inventory.");return;}
                java.util.function.Predicate<ComponentSlot> selection=c->c.assembly()==slot;
                var replacement=b==0?MechanicalState.empty():PowertrainTopology.migrate(MechanicalData.bundle(incoming,selection,slot.with(config(),b),engineParts()),powertrain(),driveConfig(),engineFamily(),slot.with(config(),b),engineParts()).select(selection,false);
                if(!replacement.validFor(selection)){if(!incoming.isEmpty())give(player,incoming);message(player,"Invalid assembly data; item returned.");return;}
                var removed=mechanics().select(selection,false);setMechanics(mechanics().replace(selection,replacement,false));
                setConfiguration(slot.with(config(),b));
                if(old>0&&!player.isCreative())give(player,MechanicalData.set(new ItemStack(AutoPropulsionAge.partItem(slot,old)),removed));
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
                if(!needsEngineRebuild())return;
                if(consume(player,Items.IRON_INGOT,12)){var m=mechanics();for(String key:m.parts().keySet())if(key.equals("engine.internals")||InternalMechanics.keys(engineFamily()).contains(key)){var p=m.get(key);m=m.with(key,p.condition(0,0,0));}setMechanics(m);entityData.set(ENGINE_HEALTH,100f);engineState=EnginePhysics.State.stopped(oilTemperature(),100);message(player,"Installed internal parts rebuilt. Missing parts, external causes and fluid quantities retained.");}
                else message(player,"Engine rebuild requires 12 iron ingots.");
            }
            default -> {}
        }
    }
    public boolean needsEngineRebuild(){
        var internal=mechanics().get("engine.internals");
        return internal!=null&&Assembly.ENGINE.variant(config())>0&&(internal.wear()>0||internal.damage()>0||internal.faults()!=0||mechanics().parts().entrySet().stream().anyMatch(e->InternalMechanics.keys(engineFamily()).contains(e.getKey())&&(e.getValue().wear()>0||e.getValue().damage()>0||e.getValue().faults()!=0)));
    }
    /** Stage a complete inventory transaction before consuming anything or changing the car. */
    private boolean convertDriveline(ServerPlayer player,DriveConfig next,boolean paid){
        var inventory=player.getInventory();var staged=new ArrayList<ItemStack>();
        for(int i=0;i<inventory.getContainerSize();i++)staged.add(inventory.getItem(i).copy());
        var m=mechanics();var removed=new ArrayList<ItemStack>();
        for(var slot:ComponentSlot.ALL){
            if(slot.assembly()!=Assembly.TRANSMISSION)continue;
            boolean before=PowertrainTopology.applicable(slot,powertrain(),driveConfig(),engineFamily()),after=PowertrainTopology.applicable(slot,powertrain(),next,engineFamily());
            if(after&&!before&&m.get(slot.key())==null){
                PartInstance part=null;
                if(player.isCreative())part=slot.fresh(config(),engineParts());
                else for(var stack:staged)if(!stack.isEmpty()&&stack.is(AutoPropulsionAge.PART_ITEMS.get(slot.item()).get())){
                    part=MechanicalData.part(stack,slot);if(part==null){message(player,"Invalid stored component: "+slot.title());return false;}stack.shrink(1);break;
                }
                if(part==null){message(player,"Conversion needs: "+slot.title()+". Carry the required parts and 8 iron ingots; removed parts are returned.");return false;}
                m=m.with(slot.key(),part);
            }
            if(before&&!after&&m.get(slot.key())!=null){var part=m.get(slot.key());removed.add(MechanicalData.single(new ItemStack(AutoPropulsionAge.PART_ITEMS.get(part.item()).get()),slot,part));m=m.with(slot.key(),null);}
        }
        if(paid&&!player.isCreative()){
            int remaining=8;for(var stack:staged)if(stack.is(Items.IRON_INGOT)){int count=Math.min(remaining,stack.getCount());stack.shrink(count);remaining-=count;}
            if(remaining>0){message(player,"Driveline conversion requires 8 iron ingots. No items consumed.");return false;}
        }
        if(!player.isCreative()){for(int i=0;i<staged.size();i++)inventory.setItem(i,staged.get(i));for(var stack:removed)give(player,stack);inventory.setChanged();}
        setMechanics(m);return true;
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
        java.util.function.Predicate<ComponentSlot> selection=c->c.assembly()==Assembly.ENGINE;
        var replacement=grade==0?MechanicalState.empty():InternalMechanics.migrateBundle(MechanicalData.bundle(incoming,selection,Assembly.ENGINE.with(config(),grade),com.photonspark.sparkmotors.item.EngineItem.parts(incoming)),family);
        if(!replacement.validFor(selection)){if(!incoming.isEmpty())give(player,incoming);message(player,"Invalid engine data; item returned.");return;}
        ItemStack removed=old==0?ItemStack.EMPTY:com.photonspark.sparkmotors.item.EngineItem.withParts(new ItemStack(AutoPropulsionAge.engineItem(engineFamily(),old)),engineParts());
        if(!removed.isEmpty())com.photonspark.sparkmotors.item.EngineItem.withTemperature(removed,temperature());
        if(!removed.isEmpty())com.photonspark.sparkmotors.item.EngineItem.withCondition(removed,oilTemperature(),engineHealth());
        if(!removed.isEmpty())MechanicalData.set(removed,mechanics().select(selection,true));
        setMechanics(mechanics().replace(selection,replacement,true));
        setConfiguration(Assembly.ENGINE.with(config(),grade));entityData.set(ENGINE_FAMILY,family.ordinal());
        entityData.set(ENGINE_PARTS,grade==0?EnginePart.stock():com.photonspark.sparkmotors.item.EngineItem.parts(incoming));
        entityData.set(TEMPERATURE,grade==0?20f:com.photonspark.sparkmotors.item.EngineItem.temperature(incoming));entityData.set(BOOST,0f);
        entityData.set(OIL_TEMP,grade==0?20f:com.photonspark.sparkmotors.item.EngineItem.oilTemperature(incoming));entityData.set(ENGINE_HEALTH,grade==0?100f:com.photonspark.sparkmotors.item.EngineItem.health(incoming));
        engineState=EnginePhysics.State.stopped(oilTemperature(),engineHealth());
        if(!removed.isEmpty()&&!player.isCreative())give(player,removed);
        player.getInventory().setChanged();message(player,grade==0?"Engine removed with its installed parts.":family.title+" engine installed.");
    }
    private static boolean hasTool(Player p,String name){if(p.isCreative()||p.getInventory().countItem(AutoPropulsionAge.PART_ITEMS.get(name).get())>0)return true;message(p,"Required tool: "+name.replace('_',' '));return false;}
    private static ItemStack takePart(Player p,Item item){
        if(p.isCreative())return new ItemStack(item);
        for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(stack.is(item)){var result=stack.copyWithCount(1);stack.shrink(1);p.getInventory().setChanged();return result;}}
        return ItemStack.EMPTY;
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
        var sourcePos=source.getSourcePosition();String region="front";
        if(sourcePos!=null){var local=sourcePos.subtract(position()).yRot((float)Math.toRadians(getYRot()));region=Math.abs(local.x)>Math.abs(local.z)*.6?(local.z>=0?"f":"r")+(local.x<0?"l":"r"):local.z<0?"rear":"front";}
        impactComponents(region,Math.sqrt(25+Math.min(50,amount)*9));entityData.set(HEALTH,Math.max(0,health()-Math.min(amount,50)));return true;
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){lerpX=x;lerpY=y;lerpZ=z;lerpYaw=yaw;lerpPitch=pitch;lerpSteps=Math.min(3,Math.max(1,steps));}
    // Vanilla combines position-only and rotation-only packets with these targets.
    // Returning the intermediate rendered pose discards an unfinished move/turn whenever
    // the next packet updates only the other half, leaving a stationary car facing stale yaw.
    @Override public double lerpTargetX(){return lerpSteps>0?lerpX:getX();}
    @Override public double lerpTargetY(){return lerpSteps>0?lerpY:getY();}
    @Override public double lerpTargetZ(){return lerpSteps>0?lerpZ:getZ();}
    @Override public float lerpTargetYRot(){return lerpSteps>0?lerpYaw:getYRot();}
    @Override public float lerpTargetXRot(){return lerpSteps>0?lerpPitch:getXRot();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putDouble("TripStart",tripStart);tag.put("Mechanics",MechanicalData.write(mechanics()));tag.putInt("DataVersion",7);tag.putInt("DriveSetup",driveConfig().packed());tag.putInt("FrontSplit",driveConfig().frontPercent());tag.putInt("Assemblies",config());tag.putInt("Paint",paint());tag.putFloat("Fuel",fuel());tag.putFloat("Health",health());
        tag.putInt("EngineFamily",engineFamily().ordinal());tag.putInt("EngineParts",engineParts());tag.putFloat("EngineTemperature",temperature());tag.putBoolean("HoodOpen",hoodOpen());tag.putBoolean("Raised",raised());
        tag.putString("Powertrain",powertrain().id());tag.putInt("ElectricMode",entityData.get(EV_MODE));tag.putInt("ChargeTarget",chargeTarget());
        if(electricState!=null){var e=electricState;var b=e.battery();tag.putDouble("BatteryJ",b.energyJ());tag.putDouble("BatteryC",b.temperatureC());tag.putDouble("BatteryHealth",b.health());tag.putDouble("BatteryThroughputJ",b.throughputJ());tag.putDouble("MotorC",e.motorC());tag.putDouble("InverterC",e.inverterC());}
        tag.putFloat("EngineHealth",engineHealth());tag.putFloat("OilTemperature",oilTemperature());tag.putFloat("BoostTarget",boostTarget());
        tag.putInt("Limiter",limiter());tag.putFloat("FinalDrive",finalDrive());tag.putBoolean("Lights",lights());tag.putBoolean("ReverseSelected",reverseSelected());entityData.get(OWNER).ifPresent(id->tag.putUUID("Owner",id));
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        setDriveConfig(tag.contains("DriveSetup")?DriveConfig.decode(tag.getInt("DriveSetup"),tag.getInt("FrontSplit")):DriveConfig.stock());
        entityData.set(LATERAL_SPEED,0f);entityData.set(YAW_RATE,0f);entityData.set(STEERING_ANGLE,0f);
        if(tag.contains("Assemblies"))setConfiguration(tag.getInt("Assemblies"));
        entityData.set(ENGINE_FAMILY,EngineFamily.byId(tag.getInt("EngineFamily")).ordinal());
        entityData.set(ENGINE_PARTS,tag.contains("EngineParts")?(tag.getInt("DataVersion")<3?EnginePart.fromLegacy(tag.getInt("EngineParts")):EnginePart.sanitize(tag.getInt("EngineParts"))):EnginePart.stock());
        entityData.set(ENGINE_HEALTH,tag.contains("EngineHealth")?(float)VehicleDynamics.clamp(tag.getFloat("EngineHealth"),0,100):100f);
        entityData.set(OIL_TEMP,tag.contains("OilTemperature")?(float)VehicleDynamics.clamp(tag.getFloat("OilTemperature"),20,180):20f);
        entityData.set(BOOST_TARGET,tag.contains("BoostTarget")?(float)VehicleDynamics.clamp(tag.getFloat("BoostTarget"),.2,1.4):1.4f);
        engineState=EnginePhysics.State.stopped(oilTemperature(),engineHealth());entityData.set(BOOST,0f);entityData.set(RPM,0f);entityData.set(SPEED,0f);entityData.set(OIL_PRESSURE,0f);entityData.set(SPOOL,0f);entityData.set(THROTTLE,0f);entityData.set(SHAFT_TORQUE,0f);entityData.set(BLOWER_KW,0f);entityData.set(AFR,14.7f);
        entityData.set(TEMPERATURE,tag.contains("EngineTemperature")?(float)VehicleDynamics.clamp(tag.getFloat("EngineTemperature"),20,150):20f);
        flag(16,tag.getBoolean("Raised"));flag(8,tag.getBoolean("HoodOpen"));hoodProgress=oldHoodProgress=hoodOpen()?1:0;
        if(tag.contains("Paint"))entityData.set(PAINT,tag.getInt("Paint")&0xFFFFFF);
        if(tag.contains("Fuel"))entityData.set(FUEL,(float)VehicleDynamics.clamp(tag.getFloat("Fuel"),0,50));
        if(tag.contains("Health"))entityData.set(HEALTH,(float)VehicleDynamics.clamp(tag.getFloat("Health"),0,100));
        if(tag.contains("Limiter"))entityData.set(LIMITER,Mth.clamp(tag.getInt("Limiter"),4000,7000));
        if(tag.contains("FinalDrive"))entityData.set(FINAL_DRIVE,(float)VehicleDynamics.clamp(tag.getFloat("FinalDrive"),2.8,4.8));
        if(tag.hasUUID("Owner"))setOwner(tag.getUUID("Owner"));
        wheelState=WheelDynamics.State.stopped();transmissionState=TransmissionPhysics.State.stopped();
        if(tag.contains("Mechanics",10))setMechanics(MechanicalData.read(tag.getCompound("Mechanics")));
        else setMechanics(MechanicalState.legacy(config(),engineParts(),temperature(),oilTemperature(),engineHealth()));
        tripStart=VehicleDynamics.clamp(tag.getDouble("TripStart"),0,mechanics().distance());entityData.set(TRIP_START,(float)(tripStart/1000));entityData.set(ENGINE_MODE,0);flag(2,tag.getBoolean("Lights"));flag(256,tag.getBoolean("ReverseSelected"));speed=0;verticalSpeed=0;benchTicks=0;benchRecovery=0;flag(1,false);
        var type=Powertrain.byId(tag.getString("Powertrain"));entityData.set(POWERTRAIN,type.ordinal());electricState=type.electric()?ElectricDynamics.State.initial(type,0):null;
        setMechanics(PowertrainTopology.migrate(mechanics(),type,driveConfig(),engineFamily(),config(),engineParts()));
        entityData.set(EV_MODE,Mth.clamp(tag.getInt("ElectricMode"),0,2));entityData.set(CHARGE_TARGET,tag.contains("ChargeTarget")?Mth.clamp(tag.getInt("ChargeTarget"),50,100):80);
        if(type.electric()){
            var battery=new BatteryModel.State(tag.getDouble("BatteryJ"),tag.contains("BatteryC")?tag.getDouble("BatteryC"):20,tag.contains("BatteryHealth")?tag.getDouble("BatteryHealth"):1,tag.getDouble("BatteryThroughputJ")).normalized(type.battery);
            electricState=new ElectricDynamics.State(battery,tag.contains("MotorC")?VehicleDynamics.clamp(tag.getDouble("MotorC"),-60,250):20,tag.contains("InverterC")?VehicleDynamics.clamp(tag.getDouble("InverterC"),-60,250):20,false,0,0,BatteryModel.ocv(type.battery,battery),0,0);syncElectric();
        }
        chargingPos=null;chargeLease=0;entityData.set(PLUGGED,false);entityData.set(CHARGE_KW,0f);
    }
}
