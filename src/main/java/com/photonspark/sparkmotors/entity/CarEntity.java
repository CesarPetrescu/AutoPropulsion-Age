package com.photonspark.sparkmotors.entity;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.item.MechanicalData;
import com.photonspark.sparkmotors.sim.*;
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

    public CarEntity(EntityType<? extends CarEntity> type,Level level){super(type,level);blocksBuilding=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        b.define(DRIVE_SETUP,DriveConfig.stock().packed());b.define(FRONT_SPLIT,0);b.define(LATERAL_SPEED,0f);b.define(YAW_RATE,0f);b.define(STEERING_ANGLE,0f);
        b.define(CLUTCH_STATE,new org.joml.Vector3f());b.define(TRIP_START,0f);b.define(ENGINE_LOAD,0f);b.define(VENT_EVENTS,0);b.define(DIAGNOSTIC,"No test performed.");b.define(COOLANT,8f);b.define(OIL_QUANTITY,5f);b.define(BRAKE_FLUID,1f);b.define(CONTACTS,0);b.define(ENGINE_MODE,0);for(var wheel:WHEELS)b.define(wheel,new org.joml.Vector3f());b.define(MECHANICS,new CompoundTag());b.define(SPEED,0f);b.define(RPM,0f);b.define(FUEL,40f);b.define(HEALTH,100f);b.define(STEER,0f);b.define(PITCH,0f);b.define(ROLL,0f);
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
    public String engineProblem(){return Assembly.ENGINE.variant(config())==0?"No engine installed.":MechanicalCapabilities.buildProblem(engineParts());}
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
        wheelAngle+=speed()*.05f/.34f;for(int c=0;c<4;c++){oldWheelAngles[c]=wheelAngles[c];wheelAngles[c]+=wheelOmega(c)*.05f;}
        panelProgress=Mth.clamp(panelProgress+(panels()?.09f:-.09f),0,1);
        hoodProgress=Mth.clamp(hoodProgress+(hoodOpen()?.09f:-.09f),0,1);
        if(CircuitPhysics.measure(mechanics(),rpm(),ignition(),speed()).fan())fanAngle+=6;
        engineAngle+=rpm()*.05f*(float)(Math.PI/30);
        if(level().isClientSide){
            if(lerpSteps>0){
                setPos(getX()+(lerpX-getX())/lerpSteps,getY()+(lerpY-getY())/lerpSteps,getZ()+(lerpZ-getZ())/lerpSteps);
                setYRot(getYRot()+Mth.wrapDegrees(lerpYaw-getYRot())/lerpSteps);setXRot(lerpPitch);lerpSteps--;
                setBoundingBox(makeBoundingBox());
            }
            return;
        }
        if(entityData.get(MECHANICS).isEmpty())setMechanics(mechanical);
        boolean driver=getControllingPassenger() instanceof Player;
        if(!driver||tickCount-lastInputTick>10){inputKeys=20;inputSteer=0;}
        if(engineHealth()<=5||fuel()<=0||isInWater()||!engineProblem().isEmpty())flag(1,false);
        if(!ignition()){benchTicks=0;benchRecovery=0;}
        if(benchTicks>0){benchTicks--;benchRecovery=100;inputKeys=23;inputSteer=0;}
        else if(benchRecovery>0){benchRecovery--;inputKeys=22;inputSteer=0;if(rpm()<1100&&throttle()<.05)benchRecovery=0;}
        boolean reverse=reverseSelected();
        boolean braking=(inputKeys&2)!=0;flag(32,braking);flag(64,(inputKeys&4)!=0);flag(128,surfaceGrip()<.9);
        var input=new VehicleDynamics.Input((inputKeys&1)!=0?1:0,inputSteer,braking,reverse,(inputKeys&16)!=0,(inputKeys&4)!=0);
        var setup=new VehicleDynamics.Setup(config(),limiter(),finalDrive(),engineFamily(),engineParts(),temperature(),boostTarget(),mechanics(),driveConfig());
        double[] contact=wheelContacts();
        int contactCount=0;
        float currentFuel=fuel();
        for(int i=0;i<4;i++){
            contact=wheelContacts();contactCount=0;
            boolean[] touching=new boolean[4];double[] travels=new double[4],gaps=new double[4],grips=new double[4];
            for(int c=0;c<4;c++){
                gaps[c]=getY()-contact[c];touching[c]=Double.isFinite(contact[c])&&!raised()&&gaps[c]<=SuspensionPhysics.REACH;
                if(touching[c])contactCount++;
                travels[c]=touching[c]?Math.clamp(-gaps[c],-.28,.16):-.28;
                grips[c]=touching[c]?surfaceGrip(new Vec3(wheelPoint(c).x,contact[c]-.02,wheelPoint(c).z)):0;
                if(!touching[c])gaps[c]=Double.NaN;
            }
            var state=VehicleDynamics.step(speed,currentFuel,engineState,wheelState,transmissionState,ignition(),input,setup,grips,touching,travels,.0125);
            if(state.engine().blowOff()&&!engineState.blowOff())entityData.set(VENT_EVENTS,ventEvents()+1);entityData.set(ENGINE_LOAD,(float)state.load());
            engineState=state.engine();wheelState=state.wheels();transmissionState=state.transmission();
            if(engineState.mode()==EnginePhysics.Mode.STALLED)flag(1,false);
            speed=state.speed();currentFuel=(float)state.fuel();
            entityData.set(RPM,(float)state.rpm());entityData.set(GEAR,state.gear());
            float previousYaw=getYRot(),attemptedYaw=previousYaw+(float)Math.toDegrees(state.yawDelta());
            setYRot(attemptedYaw);
            double yaw=Math.toRadians(attemptedYaw),vx=-Math.sin(yaw)*speed-Math.cos(yaw)*transmissionState.lateralSpeed(),vz=Math.cos(yaw)*speed-Math.sin(yaw)*transmissionState.lateralSpeed();
            if(!level().noCollision(this,makeBoundingBox().deflate(.015))){setYRot(previousYaw);transmissionState=transmissionState.motion(transmissionState.lateralSpeed(),0,transmissionState.steering(),0,0);}
            setBoundingBox(makeBoundingBox());
            verticalSpeed=raised()?0:VehicleDynamics.clamp(verticalSpeed+SuspensionPhysics.acceleration(gaps,verticalSpeed,mechanics(),driveConfig())*.0125,-30,30);
            Vec3 move=new Vec3(vx*.0125,verticalSpeed*.0125,vz*.0125);
            double oldX=getX(),oldZ=getZ();
            move(MoverType.SELF,move);
            if(verticalCollision)verticalSpeed=0;
            double lostX=Math.abs(getX()-oldX-move.x)>.0001?vx:0,lostZ=Math.abs(getZ()-oldZ-move.z)>.0001?vz:0;
            if(lostX!=0)vx=0;if(lostZ!=0)vz=0;
            double impact=Math.hypot(lostX,lostZ);
            if(impact>5){impactComponents(speed>=0?"front":"rear",impact);entityData.set(HEALTH,Math.max(0,health()-(float)(impact-5)*1.3f));if(tickCount%5==0)playSound(AutoPropulsionAge.MECHANICAL_SOUNDS.get("impact").get(),.5f,1);}
            yaw=Math.toRadians(getYRot());speed=-Math.sin(yaw)*vx+Math.cos(yaw)*vz;
            transmissionState=transmissionState.motion(-Math.cos(yaw)*vx-Math.sin(yaw)*vz,impact>0?transmissionState.yawRate()*.5:transmissionState.yawRate(),transmissionState.steering(),transmissionState.longitudinalAcceleration(),transmissionState.lateralAcceleration());
        }
        entityData.set(CLUTCH_STATE,new org.joml.Vector3f((float)transmissionState.clutchSlipRpm(),(float)transmissionState.clutchTorque(),(float)transmissionState.clutchEngagement()));
        entityData.set(LATERAL_SPEED,(float)transmissionState.lateralSpeed());entityData.set(YAW_RATE,(float)transmissionState.yawRate());entityData.set(STEERING_ANGLE,(float)transmissionState.steering());
        entityData.set(FUEL,currentFuel);entityData.set(SPEED,(float)speed);entityData.set(STEER,inputSteer);
        float throttle=(inputKeys&1)!=0?1:0;
        entityData.set(BOOST,(float)engineState.boost());entityData.set(OIL_TEMP,(float)engineState.oilTemperature());entityData.set(OIL_PRESSURE,(float)engineState.oilPressure());
        entityData.set(ENGINE_HEALTH,(float)engineState.health());entityData.set(AFR,(float)engineState.afr());entityData.set(THROTTLE,(float)engineState.throttle());entityData.set(SPOOL,(float)engineState.spool());
        entityData.set(SHAFT_TORQUE,(float)engineState.shaftTorque());entityData.set(BLOWER_KW,(float)engineState.blowerKw());
        double roadSpeed=Math.hypot(speed,transmissionState.lateralSpeed());
        mechanical=CircuitPhysics.step(mechanical,rpm(),throttle,boost(),engineState.mode()==EnginePhysics.Mode.RUNNING,engineState.mode()==EnginePhysics.Mode.CRANKING,lights(),roadSpeed,braking,.05);
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
        double worldYaw=Math.toRadians(getYRot());setDeltaMovement((-Math.sin(worldYaw)*speed-Math.cos(worldYaw)*transmissionState.lateralSpeed())/20,verticalSpeed/20,(Math.cos(worldYaw)*speed-Math.sin(worldYaw)*transmissionState.lateralSpeed())/20);
        if(contactCount==4){
            float pitch=(float)Math.toDegrees(Math.atan2((contact[0]+contact[1]-contact[2]-contact[3])/2,2.65));
            float roll=(float)Math.toDegrees(Math.atan2((contact[0]+contact[2]-contact[1]-contact[3])/2,1.66));
            entityData.set(PITCH,Mth.lerp(.25f,roadPitch(),Mth.clamp(pitch+(float)transmissionState.longitudinalAcceleration()*.5f,-18,18)));
            entityData.set(ROLL,Mth.lerp(.25f,roadRoll(),Mth.clamp(roll-(float)transmissionState.lateralAcceleration()*.7f,-18,18)));
        }
    }
    private double surfaceGrip(){return surfaceGrip(position().add(0,-.2,0));}
    private double surfaceGrip(Vec3 point){
        var block=level().getBlockState(BlockPos.containing(point));
        if(block.is(net.minecraft.tags.BlockTags.ICE))return .22;
        if(block.is(net.minecraft.tags.BlockTags.SAND)||block.is(net.minecraft.tags.BlockTags.DIRT)||block.is(net.minecraft.world.level.block.Blocks.GRAVEL))return .70;
        return 1;
    }
    private Vec3 wheelPoint(int c){return position().add(new Vec3(c%2==0?-.83:.83,0,c<2?1.35:-1.30).yRot((float)-Math.toRadians(getYRot())));}
    private double[] wheelContacts(){
        double[] heights=new double[4];
        for(int c=0;c<4;c++){
            Vec3 point=wheelPoint(c),from=point.add(0,.16,0),to=point.add(0,-SuspensionPhysics.REACH,0);
            var hit=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            heights[c]=hit.getType()==HitResult.Type.BLOCK?hit.getLocation().y:Double.NaN;
        }
        return heights;
    }
    @Override protected AABB makeBoundingBox(){
        double yaw=Math.toRadians(getYRot()),c=Math.abs(Math.cos(yaw)),s=Math.abs(Math.sin(yaw));
        double x=.98*c+2.25*s,z=2.25*c+.98*s;
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
        if(action==CarPackets.TRIP_RESET){tripStart=mechanics().distance();entityData.set(TRIP_START,(float)(tripStart/1000));return;}
        if(action==CarPackets.LIGHTS){flag(2,!lights());return;}
        if(action==CarPackets.HORN){playSound(AutoPropulsionAge.MECHANICAL_SOUNDS.get("horn").get(),.7f,1);return;}
        if(action==CarPackets.DIAGNOSE&&a>=3&&a<=5){
            if(a==3&&hasTool(player,"multimeter"))entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"Battery terminals %.2f V; stored charge %.1f Ah. Mode: %s.",CircuitPhysics.measure(mechanics(),rpm(),engineRunning(),speed()).voltage(),CircuitPhysics.batteryCharge(mechanics()),engineMode().name().toLowerCase(Locale.ROOT)));
            if(a==4&&hasTool(player,"oil_pressure_gauge"))entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"Mechanical oil gauge: %.2f bar at %.0f RPM. Sender bypassed by this physical test.",CircuitPhysics.measure(mechanics(),rpm(),engineRunning(),speed()).oilPressure(),rpm()));
            if(a==5&&hasTool(player,"compression_tester")){
                if(ignition()||!hoodOpen()||!MechanicalCapabilities.canCrank(mechanics())){message(player,"Stop the engine, open the hood, and provide a working starter circuit.");return;}
                var internal=mechanics().get("engine.internals");double compression=internal==null?0:internal.capability()*((internal.faults()&PartInstance.MISFIRE)!=0?.7:1)*(engineFamily().rotary()?8.5:12);
                entityData.set(DIAGNOSTIC,String.format(Locale.ROOT,"%s assembly compression: %.2f bar equivalent. This grouped test does not identify an individual seal or cylinder.",engineFamily().rotary()?"Rotary chamber":"Piston",compression));
            }return;
        }
        if(action==CarPackets.REV_TEST){
            if(engineRunning()&&horizontalSpeed()<.3&&hoodOpen()){benchTicks=40;message(player,"Two-second rev test. Clutch disengaged and brakes held.");}
            else message(player,"Park, open the hood and start the engine before a rev test.");return;
        }
        if(action==CarPackets.IGNITION){
            if(ignition()){flag(1,false);return;}
            if(raised()){message(player,"Lower the service jack before starting.");return;}
            if(!engineProblem().isEmpty()){message(player,engineProblem());return;}
            if(!MechanicalCapabilities.canCrank(mechanics())){message(player,"Starter cannot turn the engine. Test battery, starting circuit and mechanical resistance.");return;}
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
        if(engineWork&&(!hoodOpen()||hoodProgress<.95)){message(player,"Open the hood fully before working on the engine.");return;}
        switch(action){
            case CarPackets.DRIVE_SETUP -> {
                if(!DriveConfig.valid(a,b)){message(player,"Invalid driveline configuration.");return;}
                var next=DriveConfig.decode(a,b);if(next.equals(driveConfig()))return;
                boolean conversion=next.layout()!=driveConfig().layout()||next.differential()!=driveConfig().differential();
                if(conversion&&!raised()){message(player,"Leave the car and raise it on a service jack before converting the driveline.");return;}
                if(Assembly.TRANSMISSION.variant(config())==0||MechanicalCapabilities.transmission(mechanics())<=.01){message(player,"Fit a working gearbox, shaft and differential first.");return;}
                if(conversion&&!consume(player,Items.IRON_INGOT,8)){message(player,"Driveline conversion requires 8 iron ingots.");return;}
                setDriveConfig(next);transmissionState=transmissionState.motion(0,0,0,0,0);
                message(player,next.layout()+" / "+next.differential().name().replace('_',' ')+" saved. Installed part condition is retained.");
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
                var removed=mechanics().get(slot.key());setMechanics(mechanics().with(slot.key(),replacement));
                entityData.set(ENGINE_PARTS,next);
                engineState=EnginePhysics.State.stopped(oilTemperature(),engineHealth());entityData.set(BOOST,0f);
                if(old>0&&removed!=null&&!player.isCreative())give(player,MechanicalData.single(new ItemStack(AutoPropulsionAge.enginePartItem(part,old)),slot,removed));
                message(player,part.title+": "+part.label(b));
            }
            case CarPackets.REFUEL -> {
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
                var replacement=b==0?MechanicalState.empty():MechanicalData.bundle(incoming,selection,slot.with(config(),b),engineParts());
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
                if(consume(player,Items.IRON_INGOT,12)){var internal=mechanics().get("engine.internals");if(internal!=null)setMechanics(mechanics().with("engine.internals",internal.condition(0,0,0)));entityData.set(ENGINE_HEALTH,100f);engineState=EnginePhysics.State.stopped(oilTemperature(),100);message(player,"Engine rebuilt. Temperatures and installed parts retained.");}
                else message(player,"Engine rebuild requires 12 iron ingots.");
            }
            default -> {}
        }
    }
    public boolean needsEngineRebuild(){
        var internal=mechanics().get("engine.internals");
        return internal!=null&&Assembly.ENGINE.variant(config())>0&&(internal.wear()>0||internal.damage()>0||internal.faults()!=0);
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
        var replacement=grade==0?MechanicalState.empty():MechanicalData.bundle(incoming,selection,Assembly.ENGINE.with(config(),grade),com.photonspark.sparkmotors.item.EngineItem.parts(incoming));
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
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putDouble("TripStart",tripStart);tag.put("Mechanics",MechanicalData.write(mechanics()));tag.putInt("DataVersion",5);tag.putInt("DriveSetup",driveConfig().packed());tag.putInt("FrontSplit",driveConfig().frontPercent());tag.putInt("Assemblies",config());tag.putInt("Paint",paint());tag.putFloat("Fuel",fuel());tag.putFloat("Health",health());
        tag.putInt("EngineFamily",engineFamily().ordinal());tag.putInt("EngineParts",engineParts());tag.putFloat("EngineTemperature",temperature());tag.putBoolean("HoodOpen",hoodOpen());tag.putBoolean("Raised",raised());
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
    }
}
