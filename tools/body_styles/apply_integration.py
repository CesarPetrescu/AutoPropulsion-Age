"""One-shot integration against the reviewed baseline; applied and removed by the delivery job.
Every replacement is exact and checked so source drift fails instead of silently patching elsewhere.
"""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'src/main/java/com/photonspark/sparkmotors'

def replace(path,old,new,count=1):
    text=path.read_text()
    if text.count(old)!=count:raise RuntimeError(f'{path}: expected {count} occurrences of {old[:100]!r}, got {text.count(old)}')
    path.write_text(text.replace(old,new))

p=JAVA/'AutoPropulsionAge.java'
replace(p,'com.photonspark.sparkmotors.charging.Electrification.init(bus);','com.photonspark.sparkmotors.item.BodyStyles.init();\n        com.photonspark.sparkmotors.charging.Electrification.init(bus);')
replace(p,'com.photonspark.sparkmotors.charging.Electrification.ITEMS.forEach(output::accept);','com.photonspark.sparkmotors.charging.Electrification.ITEMS.forEach(output::accept);com.photonspark.sparkmotors.item.BodyStyles.display(output);')
p=JAVA/'item/CarCrateItem.java'
replace(p,'private final com.photonspark.sparkmotors.sim.electric.Powertrain powertrain;','private final com.photonspark.sparkmotors.sim.electric.Powertrain powertrain;\n    private final com.photonspark.sparkmotors.sim.BodyStyle bodyStyle;')
replace(p,'public CarCrateItem(Properties properties,com.photonspark.sparkmotors.sim.electric.Powertrain type) { super(properties);powertrain=type; }','''public CarCrateItem(Properties properties,com.photonspark.sparkmotors.sim.electric.Powertrain type) { this(properties,type,com.photonspark.sparkmotors.sim.BodyStyle.CLASSIC_SEDAN); }
    public CarCrateItem(Properties properties,com.photonspark.sparkmotors.sim.electric.Powertrain type,com.photonspark.sparkmotors.sim.BodyStyle style) { super(properties);powertrain=type;bodyStyle=style; }
    public com.photonspark.sparkmotors.sim.BodyStyle bodyStyle(){return bodyStyle;}''')
replace(p,'car.setOwner(player.getUUID());','car.setOwner(player.getUUID());car.initializeBodyStyle(bodyStyle);')
replace(p,'Component.literal("Clear a space about 5 x 5 blocks for the sedan.")','Component.translatable("message.sparkmotors.body_obstructed")')
p=JAVA/'entity/CarEntity.java'
replace(p,'    public CarEntity(EntityType<? extends CarEntity> type,Level level)', '''    private static final EntityDataAccessor<Integer> BODY_STYLE=data(EntityDataSerializers.INT);
    public BodyStyle bodyStyle(){return entityData==null?BodyStyle.CLASSIC_SEDAN:BodyStyle.byNetworkId(entityData.get(BODY_STYLE));}
    /** Factory/load initialization; only cosmetic identity, no mechanical reinitialization. */
    public void initializeBodyStyle(BodyStyle style){
        if(level().isClientSide)return;
        entityData.set(BODY_STYLE,Objects.requireNonNull(style).networkId());setBoundingBox(makeBoundingBox());
    }
    /** Caller validates ownership/service state. Obstructed swaps restore the previous shell. */
    public boolean trySetBodyStyle(BodyStyle style){
        if(level().isClientSide)return false;
        var previous=bodyStyle();initializeBodyStyle(style);
        if(hasBodyClearance())return true;
        initializeBodyStyle(previous);return false;
    }
    @Override public net.minecraft.network.chat.Component getName(){
        return hasCustomName()?getCustomName():net.minecraft.network.chat.Component.translatable("body.sparkmotors."+bodyStyle().id());
    }
    public CarEntity(EntityType<? extends CarEntity> type,Level level)''')
replace(p,'@Override protected void defineSynchedData(SynchedEntityData.Builder b){','@Override protected void defineSynchedData(SynchedEntityData.Builder b){\n        b.define(BODY_STYLE,0);')
replace(p,'return CarGeometry.hull(Assembly.BODY.variant(config())==2,','return bodyStyle().hull(Assembly.BODY.variant(config())==2,')
replace(p,'double x=1.12*c+2.37*s,z=2.37*c+1.12*s;','var body=bodyStyle();double w=body.broadHalfWidth(),l=body.broadHalfLength();\n        double x=w*c+l*s,z=l*c+w*s;')
replace(p,'getX()+x,getY()+1.52,getZ()+z','getX()+x,getY()+bodyStyle().roof()+.15,getZ()+z')
replace(p,'new Vec3(-.40,.18,.12).yRot','new Vec3(-.40,bodyStyle().seatY(),bodyStyle().seatZ()).yRot')
replace(p,'if(entityData.get(OWNER).isEmpty())setOwner(player.getUUID());','''if(entityData.get(OWNER).isEmpty())setOwner(player.getUUID());
        if(player.getItemInHand(hand).getItem() instanceof com.photonspark.sparkmotors.item.CarBodyKitItem kit)return kit.install(sp,this,hand);''')
replace(p,'@Override public InteractionResult interactAt(Player player,Vec3 hit,InteractionHand hand){','''@Override public InteractionResult interactAt(Player player,Vec3 hit,InteractionHand hand){
        if(player.getItemInHand(hand).getItem() instanceof com.photonspark.sparkmotors.item.CarBodyKitItem)return interact(player,hand);''')
replace(p,'@Override protected void addAdditionalSaveData(CompoundTag tag){','@Override protected void addAdditionalSaveData(CompoundTag tag){\n        tag.putString("BodyStyle",bodyStyle().id());')
replace(p,'@Override protected void readAdditionalSaveData(CompoundTag tag){','@Override protected void readAdditionalSaveData(CompoundTag tag){\n        entityData.set(BODY_STYLE,BodyStyle.byId(tag.getString("BodyStyle")).networkId());')
replace(p,'super.tick();oldWheelAngle','super.tick();if(level().isClientSide)setBoundingBox(makeBoundingBox());oldWheelAngle')
replace(p,'tag.putInt("DataVersion",7)','tag.putInt("DataVersion",8)')
p=JAVA/'net/CarPackets.java';replace(p,'event.registrar("10")','event.registrar("11")')
p=JAVA/'client/CarMesh.java'
replace(p,'private static List<Chunk> chunks=List.of();','''private static List<Chunk> chunks=List.of();
    private static final Map<BodyStyle,List<Chunk>> bodyChunks=new EnumMap<>(BodyStyle.class);
    private static List<Chunk> chunks(CarEntity car){return bodyChunks.getOrDefault(car.bodyStyle(),chunks);}
    private static boolean shared(Chunk c){
        if(c.category>=13)return !(c.category==39||c.group<0&&(c.category==26||c.category==30));
        return c.category==1&&(c.name.startsWith("strut_tower_")||c.name.startsWith("wheel_tub_")||c.name.startsWith("wheel_well_")||c.name.startsWith("engine_inner_wing_")||c.name.equals("front_undertray"));
    }
    private static List<Chunk> readBody(ResourceManager resources,BodyStyle body){
        var result=new ArrayList<Chunk>();
        try(var in=new DataInputStream(new GZIPInputStream(resources.open(AutoPropulsionAge.id("models/entity/bodies/"+body.id()+".mesh.gz"))))){
            if(in.readInt()!=0x41504132)throw new IOException("Unknown body mesh format");
            int count=in.readInt();if(count<1||count>10000)throw new IOException("Invalid body chunk count");
            for(int i=0;i<count;i++){
                String name=in.readUTF();int category=in.readInt(),group=in.readInt(),variant=in.readInt(),hinge=in.readInt();
                float px=in.readFloat(),py=in.readFloat(),pz=in.readFloat(),angle=in.readFloat();
                int family=in.readInt(),slot=in.readInt(),tier=in.readInt(),induction=in.readInt(),kind=in.readInt(),n=in.readInt();
                if(group< -1||group>=Assembly.values().length||slot< -1||slot>=EnginePart.values().length||tier<0||tier>7||hinge<0||hinge>10||kind<0||kind>3||n<3||n>2000000||n%3!=0)throw new IOException("Invalid body metadata: "+name);
                float[] v=new float[n*6];int[] colors=new int[n];
                for(int j=0;j<n;j++){for(int k=0;k<6;k++){float value=in.readFloat();if(!Float.isFinite(value))throw new IOException("Non-finite body geometry");v[j*6+k]=value;}colors[j]=in.readInt();}
                var chunk=new Chunk(name,category,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,v,colors);
                result.add(chunk);levels.put(chunk,new Mesh[]{new Mesh(v,colors),new Mesh(v,colors)});
            }
            if(in.read()!=-1)throw new IOException("Trailing body mesh data");
        }catch(IOException error){throw new IllegalStateException("Could not load body "+body.id(),error);}
        return result;
    }''')
replace(p,'private record Selection(int config,','private record Selection(BodyStyle body,int config,')
replace(p,'new Selection(car.config(),','new Selection(car.bodyStyle(),car.config(),')
replace(p,'var selected=chunks.stream().filter(c->visible(c,car)).toList();','var selected=chunks(car).stream().filter(c->visible(c,car)).toList();')
replace(p,'for(int level=0;level<2;level++)readLod(resources,level);','''for(int level=0;level<2;level++)readLod(resources,level);
        bodyChunks.clear();bodyChunks.put(BodyStyle.CLASSIC_SEDAN,chunks);
        for(var body:BodyStyle.values())if(body!=BodyStyle.CLASSIC_SEDAN){
            var combined=new ArrayList<>(chunks.stream().filter(CarMesh::shared).toList());
            combined.addAll(readBody(resources,body));bodyChunks.put(body,List.copyOf(combined));
        }''')
replace(p,'return chunks.stream().filter(c->componentKey(c).equals(key)&&visible(c,car))','return chunks(car).stream().filter(c->componentKey(c).equals(key)&&visible(c,car))')
replace(p,'for(var c:chunks)if(c.group==0&&visible(c,car))','for(var c:chunks(car))if(c.group==0&&visible(c,car))')
replace(p,'for(var c:chunks)if(c.group==0&&c.family!=127&&visible(c,car))','for(var c:chunks(car))if(c.group==0&&c.family!=127&&visible(c,car))')
replace(p,'for(var c:chunks)if(componentKey(c).equals(key)&&visible(c,car))','for(var c:chunks(car))if(componentKey(c).equals(key)&&visible(c,car))')
replace(p,'if(c.hinge>0){\n                poses.translate(c.px,c.py,c.pz);\n                poses.mulPose((c.hinge<=4?Axis.YP:Axis.XP).rotationDegrees(c.angle*(c.hinge==5?hood:panel)));\n                poses.translate(-c.px,-c.py,-c.pz);\n            }','''if(c.hinge==7||c.hinge==8){
                poses.translate((c.hinge==7?-.115:.115)*panel,0,-.70*panel);
            }else if(c.hinge>0){
                poses.translate(c.px,c.py,c.pz);
                poses.mulPose((c.hinge<=4||c.hinge>=9?Axis.YP:Axis.XP).rotationDegrees(c.angle*(c.hinge==5?hood:panel)));
                poses.translate(-c.px,-c.py,-c.pz);
            }''')
replace(p,'if(!engineOnly)CockpitInstruments.render(car,poses,buffers,light);','''if(!engineOnly){poses.pushPose();poses.translate(0,car.bodyStyle().cabinY(),car.bodyStyle().cabinZ());CockpitInstruments.render(car,poses,buffers,light);poses.popPose();}''')
p=JAVA/'client/ElectricGeometry.java'
replace(p,'            box(out,poses,.963,.72,-1.53,.987,.88,-1.34,DARK,light);\n            box(out,poses,.988,.76,-1.49,1.005,.84,-1.38,car.plugged()?TEAL:ALLOY,light);','''            if(type.plugIn()){
                var body=car.bodyStyle();double x=body.chargeX(),y=body.chargeY(),z=body.chargeZ();
                box(out,poses,x-.025,y-.08,z-.095,x-.001,y+.08,z+.095,DARK,light);
                box(out,poses,x,y-.04,z-.055,x+.017,y+.04,z+.055,car.plugged()?TEAL:ALLOY,light);
            }''')
replace(p,'Vec3 start=new Vec3(1,.8,-1.44).yRot','var body=car.bodyStyle();Vec3 start=new Vec3(body.chargeX()+.012,body.chargeY(),body.chargeZ()).yRot')
p=ROOT/'sim/src/main/java/com/photonspark/sparkmotors/sim/BodyStyle.java'
replace(p,'-1.440,-.075,-.090','-1.440,-.025,-.090')
replace(p,'public double chargeY(){return Math.max(.83,belt-.155);}','public double chargeY(){return this==CLASSIC_SEDAN?.8:Math.max(.83,belt-.155);}')
replace(p,'public double chargeZ(){return -Math.min(tail-.31,1.73);}','public double chargeZ(){return this==CLASSIC_SEDAN?-1.44:-Math.min(tail-.31,1.73);}')
p=ROOT/'gradle.properties'
import re
text=p.read_text();assert len(re.findall(r'^mod_version=.*$',text,re.M))==1
p.write_text(re.sub(r'^mod_version=.*$','mod_version=0.9.0-alpha',text,flags=re.M))
print('Applied checked body-style integration; simulation power and part definitions unchanged.')
