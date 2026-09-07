package com.photonspark.sparkmotors.entity;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.item.ConditionData;
import com.photonspark.sparkmotors.sim.VehicleCondition.Part;
import com.photonspark.sparkmotors.sound.VehicleSounds;
import com.photonspark.sparkmotors.net.CarPackets;
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
        HEALTH=data(EntityDataSerializers.FLOAT),STEER=data(EntityDataSerializers.FLOAT),PITCH=data(EntityDataSerializers.FLOAT),ROLL=data(EntityDataSerializers.FLOAT),FINAL_DRIVE=data(EntityDataSerializers.FLOAT),TEMPERATURE=data(EntityDataSerializers.FLOAT),BOOST=data(EntityDataSerializers.FLOAT),SLIP=data(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FLAGS=data(EntityDataSerializers.INT),CONFIG=data(EntityDataSerializers.INT),PAINT=data(EntityDataSerializers.INT),GEAR=data(EntityDataSerializers.INT),LIMITER=data(EntityDataSerializers.INT),ENGINE_FAMILY=data(EntityDataSerializers.INT),ENGINE_PARTS=data(EntityDataSerializers.INT),CONTROLS=data(EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> CONDITION=data(EntityDataSerializers.COMPOUND_TAG);
    private VehicleCondition condition=new VehicleCondition();
    private CompoundTag lastConditionTag;
    private int lastImpactTick=-100;
    private static final EntityDataAccessor<Optional<UUID>> OWNER=data(EntityDataSerializers.OPTIONAL_UUID);
    private static <T> EntityDataAccessor<T> data(EntityDataSerializer<T> serializer){return SynchedEntityData.defineId(CarEntity.class,serializer);}
    private double speed,verticalSpeed,lerpX,lerpY,lerpZ;
    private float lerpYaw,lerpPitch;
    private int lerpSteps,lastInputTick=-100,inputKeys,lastActionTick=-100;
    private float inputSteer;
    public float wheelAngle,oldWheelAngle,panelProgress,oldPanelProgress;
    public float hoodProgress,oldHoodProgress,engineAngle,oldEngineAngle;

    public CarEntity(EntityType<? extends CarEntity> type,Level level){super(type,level);blocksBuilding=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        b.define(SPEED,0f);b.define(RPM,0f);b.define(FUEL,40f);b.define(HEALTH,100f);b.define(STEER,0f);b.define(PITCH,0f);b.define(ROLL,0f);
        b.define(FINAL_DRIVE,3.7f);b.define(FLAGS,0);b.define(CONFIG,Assembly.stock());b.define(PAINT,0x168A91);b.define(GEAR,1);b.define(LIMITER,6800);b.define(OWNER,Optional.empty());
        b.define(ENGINE_FAMILY,0);b.define(ENGINE_PARTS,EnginePart.stock());b.define(TEMPERATURE,20f);b.define(BOOST,0f);b.define(SLIP,0f);b.define(CONTROLS,0);b.define(CONDITION,new CompoundTag());
    }
    public float speed(){return entityData.get(SPEED);} public float rpm(){return entityData.get(RPM);} public float fuel(){return entityData.get(FUEL);}
    public float health(){return entityData.get(HEALTH);} public int config(){return entityData.get(CONFIG);} public int paint(){return entityData.get(PAINT);}
    public int gear(){return entityData.get(GEAR);} public int limiter(){return entityData.get(LIMITER);} public float finalDrive(){return entityData.get(FINAL_DRIVE);}
    public float steer(){return entityData.get(STEER);} public float roadPitch(){return entityData.get(PITCH);} public float roadRoll(){return entityData.get(ROLL);}
    public boolean ignition(){return flag(1);} public boolean lights(){return flag(2);} public boolean panels(){return flag(4);}
    public boolean grounded(){return flag(16);}
    public boolean hoodOpen(){return flag(8);}
    public EngineFamily engineFamily(){return EngineFamily.byId(entityData.get(ENGINE_FAMILY));}
    public int engineParts(){return entityData.get(ENGINE_PARTS);}
    public float temperature(){return entityData.get(TEMPERATURE);}public float boost(){return entityData.get(BOOST);}
    public VehicleCondition condition(){
        if(level().isClientSide){var tag=entityData.get(CONDITION);if(tag!=lastConditionTag){condition=ConditionData.load(tag);lastConditionTag=tag;}}
        return condition;
    }
    public void syncCondition(){if(!level().isClientSide){entityData.set(CONDITION,ConditionData.save(condition,p->true));entityData.set(HEALTH,(float)condition.average(config(),engineParts()));}}
    public float throttle(){return (entityData.get(CONTROLS)&1)!=0?1:0;}
    public boolean braking(){return (entityData.get(CONTROLS)&6)!=0;}
    public boolean handbrake(){return (entityData.get(CONTROLS)&4)!=0;}
    public float slip(){return entityData.get(SLIP);}
    public String engineProblem(){
        if(Assembly.ENGINE.variant(config())==0)return "No engine installed.";
        String missing=EnginePart.problem(engineParts());return missing.isEmpty()?condition().problem(config(),engineParts()):missing;
    }
    private boolean flag(int flag){return (entityData.get(FLAGS)&flag)!=0;}
    private void flag(int flag,boolean value){entityData.set(FLAGS,value?entityData.get(FLAGS)|flag:entityData.get(FLAGS)&~flag);}
    public void setOwner(UUID owner){entityData.set(OWNER,Optional.of(owner));}
    public boolean mayModify(Player p){return p.hasPermissions(2)||entityData.get(OWNER).map(p.getUUID()::equals).orElse(true);}
    public void setConfiguration(int c){entityData.set(CONFIG,Assembly.sanitize(c));}
    public void receiveInput(int keys,float steer){
        if(!Float.isFinite(steer))return;
        inputKeys=keys&15;inputSteer=Mth.clamp(steer,-1,1);lastInputTick=tickCount;
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
            if(ignition()&&tickCount%5==0&&(temperature()>115||condition().health(Part.ENGINE_BLOCK)<40||condition().health(Part.COOLING)<30)){
                Vec3 point=position().add(new Vec3(0,1.1,1.4).yRot((float)-Math.toRadians(getYRot())));
                level().addParticle(temperature()>115?net.minecraft.core.particles.ParticleTypes.CLOUD:net.minecraft.core.particles.ParticleTypes.SMOKE,point.x,point.y,point.z,0,.025,0);
            }
            return;
        }
        boolean driver=getControllingPassenger() instanceof Player;
        if(!driver||tickCount-lastInputTick>10){inputKeys=4;inputSteer=0;}
        if(health()<=0||fuel()<=0||isInWater()||!engineProblem().isEmpty()||temperature()>=130)flag(1,false);
        boolean reverse=(inputKeys&8)!=0;
        if(speed>1)reverse=false;
        if(speed< -1)reverse=true;
        boolean braking=(inputKeys&6)!=0;
        entityData.set(CONTROLS,inputKeys);
        var effects=condition.effects(config(),engineParts());
        var input=new VehicleDynamics.Input((inputKeys&1)!=0?1:0,inputSteer,(inputKeys&2)!=0,reverse,(inputKeys&4)!=0);
        var setup=new VehicleDynamics.Setup(config(),limiter(),finalDrive(),engineFamily(),engineParts(),temperature(),effects);
        double[] contact=wheelContacts();
        int contactCount=0;for(double h:contact)if(Double.isFinite(h))contactCount++;
        boolean grounded=onGround()||contactCount>=2;
        double grip=surfaceGrip();
        float currentFuel=fuel();
        for(int i=0;i<4;i++){
            var state=VehicleDynamics.step(speed,currentFuel,ignition(),input,setup,grip,grounded,.0125);
            speed=state.speed();currentFuel=(float)state.fuel();entityData.set(SLIP,(float)state.slip());
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
            if(verticalCollision){if(verticalSpeed< -4)applyImpact(VehicleCondition.Zone.UNDERBODY,-verticalSpeed);verticalSpeed=0;}
            if(horizontalCollision&&Math.hypot(getX()-oldX,getZ()-oldZ)<Math.hypot(move.x,move.z)*.5){
                speed=0;
                if(impact>3){
                    Vec3 blocked=new Vec3(move.x-(getX()-oldX),0,move.z-(getZ()-oldZ)).yRot((float)Math.toRadians(getYRot()));
                    applyImpact(zone(blocked),impact);
                }
            }
            grounded=onGround()||contactCount>=2;
        }
        flag(16,grounded);
        entityData.set(FUEL,currentFuel);entityData.set(SPEED,(float)speed);entityData.set(STEER,inputSteer);
        float throttle=(inputKeys&1)!=0?1:0;
        entityData.set(BOOST,ignition()?(float)EngineBuild.boost(rpm(),engineParts())*throttle*(float)effects.boost():0);
        entityData.set(TEMPERATURE,(float)EngineBuild.temperature(temperature(),rpm(),throttle,boost(),ignition(),engineParts(),.05,effects.cooling()));
        var failedBefore=condition.failed();
        condition.step(config(),engineParts(),ignition(),rpm(),throttle,speed,braking,temperature(),slip(),.05);
        var failedNow=condition.failed();failedNow.removeAll(failedBefore);playFailures(failedNow);
        if(tickCount%10==0||!failedNow.isEmpty())syncCondition();
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
        if(action==CarPackets.IGNITION){
            if(ignition()){flag(1,false);playSound(VehicleSounds.get("engine_stop"),.5f,1);return;}
            if(!Assembly.canDrive(config())){message(player,"Install an engine, transmission, wheel set, brakes and suspension first.");return;}
            if(!engineProblem().isEmpty()){message(player,engineProblem());return;}
            if(temperature()>=125){message(player,"Engine too hot. Let it cool before restarting.");return;}
            if(fuel()<=0||health()<=0||isInWater()){message(player,"Refuel or repair the car before starting.");return;}
            flag(1,true);playSound(VehicleSounds.get("starter"),.6f,1);return;
        }
        if(Math.abs(speed())>.3){message(player,"Stop the car before servicing it.");return;}
        if(action==CarPackets.PANELS){boolean open=!panels();flag(4,open);flag(8,open);return;}
        if(action==CarPackets.HOOD){flag(8,!hoodOpen());return;}
        if(ignition()){message(player,"Switch the engine off before servicing.");return;}
        boolean engineWork=action==CarPackets.ENGINE_SWAP||action==CarPackets.ENGINE_PART||(action==CarPackets.REPAIR_PART&&a>=0&&a<Part.values().length&&Part.values()[a].assembly==Assembly.ENGINE)||(action==CarPackets.INSTALL&&a==Assembly.ENGINE.ordinal());
        if(engineWork&&(!hoodOpen()||hoodProgress<.95)){message(player,"Open the hood fully before working on the engine.");return;}
        switch(action){
            case CarPackets.ENGINE_SWAP -> {
                if(a<0||a>=EngineFamily.values().length||b<1||b>2)return;
                swapEngine(player,EngineFamily.values()[a],b);
            }
            case CarPackets.ENGINE_PART -> {
                if(a<0||a>=EnginePart.values().length||b<0||b>2||Assembly.ENGINE.variant(config())==0)return;
                EnginePart part=EnginePart.values()[a];int old=part.variant(engineParts());if(old==b&&b==0)return;
                int next=part.with(engineParts(),b);
                // Disassembly is allowed. A complete but incompatible boost build is rejected.
                String problem=part.installationProblem(engineParts(),b);
                if(!problem.isEmpty()){message(player,problem);return;}
                ItemStack incoming=b==0?ItemStack.EMPTY:takePart(player,AutoPropulsionAge.enginePartItem(part,b));
                if(b>0&&incoming.isEmpty()){message(player,"Required engine part is missing from your inventory.");return;}
                ItemStack removed=old==0?ItemStack.EMPTY:ConditionData.toItem(new ItemStack(AutoPropulsionAge.enginePartItem(part,old)),condition,p->p.enginePart==part);
                condition.copyFrom(ConditionData.fromItem(incoming),p->p.enginePart==part);
                entityData.set(ENGINE_PARTS,next);syncCondition();
                if(!removed.isEmpty()&&!player.isCreative())give(player,removed);
                message(player,part.title+": "+part.label(b));
            }
            case CarPackets.REFUEL -> {
                if(fuel()>=49.9){message(player,"Fuel tank is full.");break;}
                if(consume(player,AutoPropulsionAge.FUEL_CAN.get(),1)){entityData.set(FUEL,Math.min(50,fuel()+10));message(player,"Added up to 10 L of fuel.");}
                else message(player,"You need a fuel can in your inventory.");
            }
            case CarPackets.REPAIR -> {
                Part worst=Arrays.stream(Part.values()).filter(p->p.installed(config(),engineParts()))
                    .min(Comparator.comparingDouble(condition::health)).orElse(Part.FRAME);
                repairPart(player,worst);
            }
            case CarPackets.REPAIR_PART -> {
                if(a>=0&&a<Part.values().length)repairPart(player,Part.values()[a]);
            }
            case CarPackets.INSTALL -> {
                if(a<0||a>=Assembly.values().length||b<0||b>2)return;
                Assembly slot=Assembly.values()[a];int old=slot.variant(config());if(old==b&&b==0)return;
                if(slot==Assembly.ENGINE){swapEngine(player,engineFamily(),b);return;}
                ItemStack incoming=b==0?ItemStack.EMPTY:takePart(player,AutoPropulsionAge.partItem(slot,b));
                if(b>0&&incoming.isEmpty()){message(player,"Required assembly is missing from your inventory.");return;}
                ItemStack removed=old==0?ItemStack.EMPTY:ConditionData.toItem(new ItemStack(AutoPropulsionAge.partItem(slot,old)),condition,p->p.assembly==slot);
                condition.copyFrom(ConditionData.fromItem(incoming),p->p.assembly==slot);
                setConfiguration(slot.with(config(),b));syncCondition();
                if(!removed.isEmpty()&&!player.isCreative())give(player,removed);
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
            default -> {}
        }
    }
    private void swapEngine(ServerPlayer player,EngineFamily family,int grade){
        int old=Assembly.ENGINE.variant(config());
        if(old==grade&&grade==0)return;
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
        if(!removed.isEmpty()){com.photonspark.sparkmotors.item.EngineItem.withTemperature(removed,temperature());ConditionData.toItem(removed,condition,p->p.assembly==Assembly.ENGINE);}
        condition.copyFrom(ConditionData.fromItem(incoming),p->p.assembly==Assembly.ENGINE);
        setConfiguration(Assembly.ENGINE.with(config(),grade));entityData.set(ENGINE_FAMILY,family.ordinal());
        entityData.set(ENGINE_PARTS,grade==0?EnginePart.stock():com.photonspark.sparkmotors.item.EngineItem.parts(incoming));
        entityData.set(TEMPERATURE,grade==0?20f:com.photonspark.sparkmotors.item.EngineItem.temperature(incoming));entityData.set(BOOST,0f);
        if(!removed.isEmpty()&&!player.isCreative())give(player,removed);
        syncCondition();player.getInventory().setChanged();message(player,grade==0?"Engine removed with its installed parts.":family.title+" engine installed.");
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
        if(!Float.isFinite(amount)||amount<=0)return false;
        Vec3 origin=source.getSourcePosition();
        VehicleCondition.Zone hit=origin==null?VehicleCondition.Zone.FRONT:zone(origin.subtract(position()).yRot((float)Math.toRadians(getYRot())));
        var failed=condition.impact(hit,Math.sqrt(Math.min(amount,50)/.15+9),config(),engineParts());
        playFailures(failed);syncCondition();return true;
    }
    private static VehicleCondition.Zone zone(Vec3 local){
        return Math.abs(local.z)>=Math.abs(local.x)?(local.z>=0?VehicleCondition.Zone.FRONT:VehicleCondition.Zone.REAR):
            (local.x<0?VehicleCondition.Zone.LEFT:VehicleCondition.Zone.RIGHT);
    }
    private void applyImpact(VehicleCondition.Zone zone,double deltaV){
        if(tickCount-lastImpactTick<6)return;lastImpactTick=tickCount;
        var failed=condition.impact(zone,deltaV,config(),engineParts());
        playSound(VehicleSounds.get("impact"),(float)Math.min(.85,deltaV/25),1);playFailures(failed);syncCondition();
    }
    private void playFailures(Set<Part> failed){
        if(failed.isEmpty())return;
        String sound=failed.stream().anyMatch(p->p.assembly==Assembly.WHEELS)?"tire_burst":
            failed.stream().anyMatch(p->p.assembly==Assembly.ENGINE)?"engine_failure":"part_break";
        playSound(VehicleSounds.get(sound),.65f,1);
        if(!condition.problem(config(),engineParts()).isEmpty())flag(1,false);
    }
    private void repairPart(ServerPlayer player,Part part){
        if(!part.installed(config(),engineParts())){message(player,"Install this component before repairing it.");return;}
        if(condition.health(part)>=99.999){message(player,"This component does not need repair.");return;}
        if(part.assembly==Assembly.ENGINE&&(!hoodOpen()||hoodProgress<.95)){message(player,"Open the hood fully before engine repairs.");return;}
        if(!consume(player,Items.IRON_INGOT,part.repairCost)){message(player,"Repair requires "+part.repairCost+" iron ingots.");return;}
        condition.repair(part);syncCondition();message(player,part.title+" repaired. Other components are unchanged.");
    }
    private static ItemStack takePart(Player player,Item item){
        if(player.isCreative())return new ItemStack(item);
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);if(stack.is(item)){
                var one=stack.copyWithCount(1);stack.shrink(1);player.getInventory().setChanged();return one;
            }
        }
        return ItemStack.EMPTY;
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){lerpX=x;lerpY=y;lerpZ=z;lerpYaw=yaw;lerpPitch=pitch;lerpSteps=Math.min(3,Math.max(1,steps));}
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putInt("DataVersion",3);tag.put("PartCondition",ConditionData.save(condition,p->true));tag.putInt("Assemblies",config());tag.putInt("Paint",paint());tag.putFloat("Fuel",fuel());tag.putFloat("Health",health());
        tag.putInt("EngineFamily",engineFamily().ordinal());tag.putInt("EngineParts",engineParts());tag.putFloat("EngineTemperature",temperature());tag.putBoolean("HoodOpen",hoodOpen());
        tag.putInt("Limiter",limiter());tag.putFloat("FinalDrive",finalDrive());tag.putBoolean("Lights",lights());entityData.get(OWNER).ifPresent(id->tag.putUUID("Owner",id));
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        if(tag.contains("Assemblies"))setConfiguration(tag.getInt("Assemblies"));
        if(tag.getInt("DataVersion")<3)setConfiguration(Assembly.EXHAUST.with(config(),1));
        entityData.set(ENGINE_FAMILY,EngineFamily.byId(tag.getInt("EngineFamily")).ordinal());
        entityData.set(ENGINE_PARTS,tag.contains("EngineParts")?EnginePart.sanitize(tag.getInt("EngineParts")):EnginePart.stock());
        entityData.set(TEMPERATURE,tag.contains("EngineTemperature")?(float)VehicleDynamics.clamp(tag.getFloat("EngineTemperature"),20,150):20f);
        flag(8,tag.getBoolean("HoodOpen"));hoodProgress=oldHoodProgress=hoodOpen()?1:0;
        if(tag.contains("Paint"))entityData.set(PAINT,tag.getInt("Paint")&0xFFFFFF);
        if(tag.contains("Fuel"))entityData.set(FUEL,(float)VehicleDynamics.clamp(tag.getFloat("Fuel"),0,50));
        if(tag.contains("Health"))entityData.set(HEALTH,(float)VehicleDynamics.clamp(tag.getFloat("Health"),0,100));
        if(tag.contains("Limiter"))entityData.set(LIMITER,Mth.clamp(tag.getInt("Limiter"),4000,7000));
        if(tag.contains("FinalDrive"))entityData.set(FINAL_DRIVE,(float)VehicleDynamics.clamp(tag.getFloat("FinalDrive"),2.8,4.8));
        if(tag.hasUUID("Owner"))setOwner(tag.getUUID("Owner"));
        condition=tag.contains("PartCondition",10)?ConditionData.load(tag.getCompound("PartCondition")):new VehicleCondition();
        if(!tag.contains("PartCondition",10))condition.migrateLegacy(tag.contains("Health")?tag.getFloat("Health"):100,config(),engineParts());
        syncCondition();entityData.set(CONTROLS,0);entityData.set(SLIP,0f);
        flag(2,tag.getBoolean("Lights"));speed=0;verticalSpeed=0;flag(1,false);
    }
}
