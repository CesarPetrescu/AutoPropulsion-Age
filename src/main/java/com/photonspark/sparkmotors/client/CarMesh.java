package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.Assembly;
import com.photonspark.sparkmotors.sim.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public final class CarMesh {
    private static final net.minecraft.resources.ResourceLocation WHITE=AutoPropulsionAge.id("textures/entity/white.png");
    private static List<Chunk> chunks=List.of();
    // Only independently validated closed shell solids use culling. Legacy component rendering
    // remains unchanged; this is not a global no-cull workaround for missing body surfaces.
    private static final Set<Chunk> closedShells=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String,Set<String>> shellSurfaces=new HashMap<>();
    private static final Set<String> REPLACED_WHEELHOUSES=Set.of("wheel_well_rl","wheel_well_rr","wheel_tub_rl","wheel_tub_rr");
    private static final Map<BodyStyle,List<Chunk>> bodyChunks=new EnumMap<>(BodyStyle.class);
    private static List<Chunk> chunks(CarEntity car){return bodyChunks.getOrDefault(car.bodyStyle(),chunks);}
    private static boolean shared(Chunk c){
        if(REPLACED_WHEELHOUSES.contains(c.name))return false;
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
                result.add(chunk);if(shellSurfaces.getOrDefault(body.id(),Set.of()).contains(name))closedShells.add(chunk);levels.put(chunk,new Mesh[]{new Mesh(v,colors),new Mesh(v,colors)});
            }
            if(in.read()!=-1)throw new IOException("Trailing body mesh data");
        }catch(IOException error){throw new IllegalStateException("Could not load body "+body.id(),error);}
        return result;
    }
    private static final boolean PROFILE=Boolean.getBoolean("sparkmotors.profileCars");
    private static final long[] profileTimes=new long[8192];
    private static int profileCount;
    private static long profileTriangles;
    public static String profileReport(){
        var samples=Arrays.copyOf(profileTimes,profileCount);Arrays.sort(samples);
        return String.format(Locale.ROOT,"{\"scope\":\"CPU world car vertex submission, excludes GPU and other rendering\",\"samples\":%d,\"meanMs\":%.4f,\"p95Ms\":%.4f,\"meanVisibleTriangles\":%.0f}",profileCount,Arrays.stream(samples).average().orElse(0)/1e6,profileCount==0?0:samples[(int)(profileCount*.95)]/1e6,profileTriangles/(double)Math.max(1,profileCount));
    }
    private static final Assembly[] ASSEMBLIES=Assembly.values();
    private static final EnginePart[] ENGINE_PARTS=EnginePart.values();
    private record Selection(BodyStyle body,int config,int parts,EngineFamily family,DriveConfig drive,
                             com.photonspark.sparkmotors.sim.electric.Powertrain type,boolean raised,MechanicalState mechanics) {}
    private record Visible(Selection selection,List<Chunk> chunks) {}
    private static final Map<CarEntity,Visible> visibleCars=new WeakHashMap<>();
    private static List<Chunk> selected(CarEntity car){
        var key=new Selection(car.bodyStyle(),car.config(),car.engineParts(),car.engineFamily(),car.driveConfig(),car.powertrain(),car.raised(),car.mechanics());
        var cached=visibleCars.get(car);
        if(cached!=null&&cached.selection.equals(key))return cached.chunks;
        var selected=chunks(car).stream().filter(c->visible(c,car)).toList();
        visibleCars.put(car,new Visible(key,selected));return selected;
    }
    private static Map<String,String> authoredComponents=Map.of();
    private record Mesh(float[] vertices,int[] colors) {}
    private static final Map<Chunk,Mesh[]> levels=new IdentityHashMap<>();
    private record Chunk(String name,int category,int group,int variant,int hinge,float px,float py,float pz,float angle,int family,int slot,int tier,int induction,int kind,float[] vertices,int[] colors){}
    public static void reload(ResourceManager resources){
        closedShells.clear();shellSurfaces.clear();
        try(var reader=resources.openAsReader(AutoPropulsionAge.id("models/entity/bodies/shell-surfaces.json"))){
            net.minecraft.util.GsonHelper.parse(reader).entrySet().forEach(e->{
                var names=new HashSet<String>();e.getValue().getAsJsonArray().forEach(v->names.add(v.getAsString()));
                shellSurfaces.put(e.getKey(),Set.copyOf(names));
            });
        }catch(IOException e){throw new IllegalStateException("Missing closed coachwork surface manifest",e);}
        var result=new ArrayList<Chunk>();
        try(var reader=resources.openAsReader(AutoPropulsionAge.id("models/entity/mechanical-models.json"))){
            var map=new HashMap<String,String>();net.minecraft.util.GsonHelper.parse(reader).entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));authoredComponents=Map.copyOf(map);
        }catch(IOException e){throw new IllegalStateException("Missing mechanical model bindings",e);}
        try(var in=new DataInputStream(new GZIPInputStream(resources.open(AutoPropulsionAge.id("models/entity/sedan.mesh.gz"))))){
            if(in.readInt()!=0x41504132)throw new IOException("Unknown car mesh format");
            int count=in.readInt();if(count<0||count>10000)throw new IOException("Invalid chunk count");
            for(int i=0;i<count;i++){
                String name=in.readUTF();int cat=in.readInt(),group=in.readInt(),variant=in.readInt(),hinge=in.readInt();
                float px=in.readFloat(),py=in.readFloat(),pz=in.readFloat(),angle=in.readFloat();
                int family=in.readInt(),slot=in.readInt(),tier=in.readInt(),induction=in.readInt(),kind=in.readInt(),n=in.readInt();
                if(group< -1||group>=Assembly.values().length||slot< -1||slot>=EnginePart.values().length||tier<0||tier>7)throw new IOException("Invalid mesh visibility metadata");
                if(n<0||n>2000000||n%3!=0)throw new IOException("Invalid vertex count");
                float[] v=new float[n*6];int[] colors=new int[n];
                for(int j=0;j<n;j++){for(int k=0;k<6;k++)v[j*6+k]=in.readFloat();colors[j]=in.readInt();}
                result.add(new Chunk(name,cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,v,colors));
            }
        }catch(IOException e){throw new IllegalStateException("Could not load AutoPropulsion car geometry",e);}
        chunks=List.copyOf(result);componentKeys.clear();visibleCars.clear();levels.clear();
        for(int level=0;level<2;level++)readLod(resources,level);
        bodyChunks.clear();bodyChunks.put(BodyStyle.CLASSIC_SEDAN,chunks);
        for(var body:BodyStyle.values())if(body!=BodyStyle.CLASSIC_SEDAN){
            var combined=new ArrayList<>(chunks.stream().filter(CarMesh::shared).toList());
            combined.addAll(readBody(resources,body));bodyChunks.put(body,List.copyOf(combined));
        }
    }
    private static void readLod(ResourceManager resources,int level){
        try(var in=new DataInputStream(new GZIPInputStream(resources.open(AutoPropulsionAge.id("models/entity/sedan-lod"+(level+1)+".mesh.gz"))))){
            if(in.readInt()!=0x41504C31||in.readInt()!=chunks.size())throw new IOException("LOD mesh does not match canonical chunks");
            for(var c:chunks){
                if(!in.readUTF().equals(c.name))throw new IOException("LOD chunk order mismatch");
                in.skipNBytes(52);int n=in.readInt();
                Mesh geometry;
                if(n==-1)geometry=new Mesh(c.vertices,c.colors);
                else{
                    if(n<=0||n>c.colors.length||n%3!=0)throw new IOException("Invalid LOD vertex count");
                    float[] v=new float[n*6];int[] colors=new int[n];
                    for(int j=0;j<n;j++){for(int k=0;k<6;k++)v[j*6+k]=in.readFloat();colors[j]=in.readInt();}
                    geometry=new Mesh(v,colors);
                }
                levels.computeIfAbsent(c,ignored->new Mesh[2])[level]=geometry;
            }
        }catch(IOException e){throw new IllegalStateException("Could not load car world LOD",e);}
    }
    public static void render(CarEntity car,float partial,PoseStack poses,MultiBufferSource buffers,int light,boolean preview){
        render(car,partial,poses,buffers,light,preview,false,false);
    }
    public static void render(CarEntity car,float partial,PoseStack poses,MultiBufferSource buffers,int light,boolean preview,boolean engineOnly){
        render(car,partial,poses,buffers,light,preview,engineOnly,false);
    }
    private static final Map<String,String> componentKeys=new HashMap<>();
    private static String componentKey(Chunk c){return componentKeys.computeIfAbsent(c.name,name->{
        if(name.startsWith("pt|"))return name.split("\\|")[3];
        if(authoredComponents.containsKey(name))return authoredComponents.get(name);
        int corner=CarGeometry.corner(name);
        if(corner>=0){
            String part=name.contains("tire")?"tire":name.contains("rim")?"rim":name.contains("brake_disc")?"disc":name.contains("brake_pad")?"pad":name.contains("caliper")?"caliper":name.contains("hub")||name.contains("bearing")?"bearing":name.startsWith("suspension_spring_")?"spring":name.contains("coilover")||name.startsWith("suspension_damper_")?"damper":name.startsWith("brake_hose_")?"brake_hose":name.contains("knuckle")||name.contains("tie_rod")||name.contains("control_arm")?"link":null;
            if(part!=null)return "wheel."+ComponentSlot.CORNERS[corner]+"."+part;
        }
        if(name.startsWith("coolant_upper_hose"))return "cooling.upper_hose";
        if(name.startsWith("coolant_lower_hose"))return "cooling.lower_hose";
        if(name.startsWith("cooling_fan_")||name.startsWith("fan_shroud_")||name.equals("radiator_fan_shroud"))return "cooling.fan";
        if(name.startsWith("charge_pipe_"))return "induction.pipe";
        if(name.startsWith("service_intercooler_"))return "induction.intercooler";
        if(name.startsWith("service_wastegate_"))return "induction.wastegate";
        if(name.startsWith("service_bov_"))return "induction.bov";
        if(name.startsWith("service_blower_belt_"))return "induction.belt";
        if(name.startsWith("filtered_inlet_"))return "engine.intake";
        if(name.equals("oil_supply_line"))return "oil.feed";
        if(name.equals("oil_sender"))return "oil.sender";
        if(name.equals("coolant_sender"))return "cooling.sender";
        if(name.equals("main_fuse"))return "electrical.fuse";
        if(name.equals("essential_wiring"))return "electrical.wiring";
        if(name.equals("accessory_belt"))return "electrical.belt";
        if(name.equals("clutch_disc"))return "driveline.clutch";
        if(name.equals("manual_5speed"))return "driveline.gearbox";
        if(name.equals("rear_differential"))return "driveline.differential";
        if(name.contains("driveshaft")||name.equals("propeller_shaft")||name.startsWith("cv_axle_"))return "driveline.shaft";
        if(name.equals("exhaust_system")||name.equals("exhaust_tip"))return "exhaust.pipe";
        if(name.startsWith("gauge_")||name.startsWith("needle_")||name.equals("instrument_cluster"))return "body.instruments";
        if(name.contains("bumper")||name.equals("hood"))return name.startsWith("rear")?"body.rear":"body.front";
        if(c.kind==3&&c.category==6)return "body.lamps";
        if(name.equals("radiator_fan"))return "cooling.fan";
        if(name.equals("water_pump"))return "cooling.pump";
        if(name.equals("thermostat"))return "cooling.thermostat";
        if(name.equals("oil_filter"))return "oil.filter";
        if(name.equals("oil_pump"))return "oil.pump";
        if(java.util.List.of("battery","starter","alternator").contains(name))return "electrical."+name;
        if(name.equals("muffler"))return "exhaust.muffler";
        if(name.equals("exhaust_pipe"))return "exhaust.pipe";
        if(c.slot>=0)return ComponentSlot.engine(ENGINE_PARTS[c.slot]).key();
        return "";
    });}
    public static int visibleComponentTriangles(CarEntity car,String key){return chunks(car).stream().filter(c->componentKey(c).equals(key)&&visible(c,car)).mapToInt(c->c.colors.length/3).sum();}
    public static int visibleWorldComponentTriangles(CarEntity car,String key,int level){return selected(car).stream().filter(c->componentKey(c).equals(key)).mapToInt(c->levels.get(c)[level].colors.length/3).sum();}
    private static boolean visible(Chunk c,CarEntity car){
        if(c.name.equals("service_jack")&&!car.raised())return false;
        String component=componentKey(c);if(!component.isEmpty()&&car.mechanics().get(component)==null)return false;
        boolean topology=c.name.startsWith("pt|");
        if(topology){var fields=c.name.split("\\|");int type=car.powertrain().hybrid()?4:car.powertrain().electric()?2:1;if((Integer.parseInt(fields[1])&type)==0||(Integer.parseInt(fields[2])&(1<<car.driveConfig().layout().ordinal()))==0)return false;}
        if(!topology&&(c.group==1||component.startsWith("driveline.")))return false;
        if(!component.isEmpty()&&!PowertrainTopology.applicable(ComponentSlot.byKey(component),car.powertrain(),car.driveConfig(),car.engineFamily()))return false;
        if(car.powertrain().electric()&&!car.powertrain().hybrid()&&(c.group==0&&!component.startsWith("cooling.")&&!component.equals("engine.cooling")||c.name.contains("exhaust")||c.name.contains("muffler")||c.name.contains("fuel_tank")))return false;
        int selected=c.group>=0?ASSEMBLIES[c.group].variant(car.config()):1;
        if(c.group>=0&&(selected==0||(c.variant>0&&selected!=c.variant)))return false;
        if((c.family&(1<<car.engineFamily().ordinal()))==0||(c.induction&(1<<EnginePart.INDUCTION.variant(car.engineParts())))==0)return false;
        if(c.slot>=0){int v=ENGINE_PARTS[c.slot].variant(car.engineParts());if(v==0||c.tier>0&&c.tier!=v)return false;}
        return true;
    }
    public static Map<String,Integer> visibleEngineParts(CarEntity car){
        var result=new LinkedHashMap<String,Integer>();for(var c:chunks(car))if(c.group==0&&visible(c,car))result.merge(c.name,c.colors.length/3,Integer::sum);return result;
    }
    public static int visibleEngineFamilies(CarEntity car){int mask=0;for(var c:chunks(car))if(c.group==0&&c.family!=127&&visible(c,car))mask|=c.family;return mask;}
    public static void render(CarEntity car,float partial,PoseStack poses,MultiBufferSource buffers,int light,boolean preview,boolean engineOnly,boolean cutaway){
        render(car,partial,poses,buffers,light,preview,engineOnly,cutaway,"");
    }
    public static void render(CarEntity car,float partial,PoseStack poses,MultiBufferSource buffers,int light,boolean preview,boolean engineOnly,boolean cutaway,String highlight){
        long started=PROFILE&&!preview?System.nanoTime():0;int submitted=0;
        try(var glass=CarGlass.begin(preview,buffers)){
        ElectricGeometry.render(car,poses,buffers,light,preview,engineOnly);
        float panel=Mth.lerp(partial,car.oldPanelProgress,car.panelProgress);
        float hood=Mth.lerp(partial,car.oldHoodProgress,car.hoodProgress);
        float engine=Mth.lerp(partial,car.oldEngineAngle,car.engineAngle);
        float wheel=Mth.lerp(partial,car.oldWheelAngle,car.wheelAngle);
        var readings=CockpitInstruments.read(car);
        var inverseBody=new org.joml.Quaternionf();
        if(!preview)inverseBody.rotationZ((float)Math.toRadians(-car.roadRoll())).rotateX((float)Math.toRadians(car.roadPitch()));
        var moving=new org.joml.Vector3f();
        int level=!preview&&net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(car.position())>256?1:0;
        for(Chunk c:selected(car)){
            int selected=c.group>=0?ASSEMBLIES[c.group].variant(car.config()):1;
            if(engineOnly&&c.group!=0&&!(car.powertrain().electric()&&c.name.startsWith("pt|")))continue;
            if(c.group==0&&!preview&&hood<.05)continue;
            if(cutaway&&!componentKey(c).equals(highlight)&&c.group==0&&(c.category==18||c.category==19||c.category==21||c.category==24||c.category==28||c.name.contains("housing")||c.name.startsWith("rotary_")))continue;
            var geometry=preview?new Mesh(c.vertices,c.colors):levels.get(c)[level];
            var vertices=geometry.vertices;var colors=geometry.colors;
            if(started!=0)submitted+=colors.length/3;
            poses.pushPose();
            String component=componentKey(c);var part=component.isEmpty()?null:car.mechanics().get(component);
            int corner=CarGeometry.corner(c.name);
            boolean rigidWheel=corner>=0&&CarGeometry.steers(c.name);
            boolean flexible=corner>=0&&!rigidWheel&&(component.startsWith("wheel.")||component.startsWith("driveline.cv_"));
            float travel=corner<0?0:car.wheelTravel(corner);
            if(rigidWheel){
                // Unsprung parts stay on the tire contact frame while the shell pitches/rolls.
                poses.mulPose(inverseBody);poses.translate(0,travel,0);
            }
            if(c.hinge==7||c.hinge==8){
                poses.translate((c.hinge==7?-.115:.115)*panel,0,-.70*panel);
            }else if(c.hinge>0){
                poses.translate(c.px,c.py,c.pz);
                poses.mulPose((c.hinge<=4||c.hinge>=9?Axis.YP:Axis.XP).rotationDegrees(c.angle*(c.hinge==5?hood:panel)));
                poses.translate(-c.px,-c.py,-c.pz);
            }
            if(c.name.equals("radiator_fan")||c.name.startsWith("cooling_fan_")||c.name.equals("harmonic_damper")||engineOnly&&cutaway&&(c.name.startsWith("crankshaft")||c.name.startsWith("eccentric_shaft")||c.name.matches("rotor_[1-4]r_.*"))){
                poses.translate(c.px,c.py,c.pz);poses.mulPose(Axis.ZP.rotation(((c.name.equals("radiator_fan")||c.name.startsWith("cooling_fan_"))?car.fanAngle:engine)*(c.name.startsWith("rotor_")?1f/3:1)));poses.translate(-c.px,-c.py,-c.pz);
            }
            if(c.name.startsWith("needle_")||c.name.startsWith("gauge_needle_")){
                String gauge=c.name.startsWith("needle_")?(c.name.contains("0.57")?"speed":"rpm"):c.name.substring("gauge_needle_".length());
                poses.translate(c.px,c.py,c.pz);poses.mulPose(Axis.ZP.rotationDegrees((float)(-140+CockpitInstruments.fraction(car,readings,gauge)*280)));poses.translate(-c.px,-c.py,-c.pz);
            }
            if(c.name.equals("steering_wheel")){poses.translate(c.px,c.py,c.pz);poses.mulPose(Axis.ZP.rotationDegrees(car.steer()*125));poses.translate(-c.px,-c.py,-c.pz);}
            if(c.name.endsWith("_pedal")){float depressed=c.name.startsWith("throttle")?car.throttle():c.name.startsWith("brake")&&car.serviceBrake()?1:0;poses.translate(0,-depressed*.025,depressed*.02);}
            if(rigidWheel){
                    float x=(float)CarGeometry.wheelX(corner),z=(float)CarGeometry.wheelZ(corner);
                    poses.translate(x,.34,z);
                    if(corner<2)poses.mulPose(Axis.YP.rotation(-car.steeringAngle()));
                    float individual=Mth.lerp(partial,car.oldWheelAngles[corner],car.wheelAngles[corner]);
                    if(part!=null&&component.endsWith(".tire"))poses.scale(1,(float)(.78+.22*Math.min(1,part.reserve()/2.3)),1);
                    if(CarGeometry.spins(c.name))poses.mulPose(Axis.XP.rotation(individual));
                    if(part!=null&&component.endsWith(".rim")&&(part.faults()&PartInstance.BENT)!=0)poses.mulPose(Axis.YP.rotationDegrees((float)(Math.sin(individual*2)*part.damage()*8)));
                    poses.translate(-x,-.34,-z);
            }
            boolean closed=closedShells.contains(c);
            VertexConsumer buffer=c.kind==2?glass.buffer(closed)
                :buffers.getBuffer(closed?RenderType.entityCutout(WHITE):RenderType.entityCutoutNoCull(WHITE));
            var pose=poses.last();int brightness=c.kind==3&&car.lights()&&car.mechanics().capability("body.lamps")>.2&&CircuitPhysics.batteryCharge(car.mechanics())>.1?LightTexture.FULL_BRIGHT:light;
            for(int triangle=0;triangle<colors.length;triangle+=3){
                if(c.kind==2&&closed&&!preview&&!CarGlass.frontFacing(pose.pose(),vertices,triangle))continue;
                for(int k=0;k<4;k++){
                    int i=triangle+Math.min(k,2),v=i*6;
                    int color=c.kind==1?0xFF000000|car.paint():colors[i];
                    if(!highlight.isEmpty()&&component.equals(highlight))color=0xFFFFBC58;
                    if(component.equals("exhaust.muffler")&&part!=null&&part.item().equals("sport_muffler"))color=0xFFAFB9C5;
                    if(c.kind==2)color=(color&0xFFFFFF)|0x30000000;
                    if(c.group==0&&selected==2&&(c.category==19||c.name.startsWith("rotor_housing")))color=0xFFDBAC4C;
                    if(c.group==3&&selected==2&&c.name.startsWith("brake_caliper"))color=0xFF3FA7F5;
                    float vx=vertices[v],vy=vertices[v+1],vz=vertices[v+2];
                    if(flexible){
                        float weight=component.startsWith("driveline.cv_")?Mth.clamp((Math.abs(vx)-.13f)/.625f,0,1):c.name.startsWith("tie_rod_")?Mth.clamp((Math.abs(vx)-.53f)/.18f,0,1):c.name.startsWith("control_arm_")?Mth.clamp((Math.abs(vx)-.37f)/.33f,0,1):
                            c.name.startsWith("brake_hose_")?Mth.clamp((.59f-vy)/.23f,0,1):Mth.clamp((.94f-vy)/.60f,0,1);
                        moving.set(vx,vy,vz);
                        if(corner<2){float cx=(float)CarGeometry.wheelX(corner),cz=(float)CarGeometry.wheelZ(corner);moving.sub(cx,.34f,cz).rotateY(-car.steeringAngle()).add(cx,.34f,cz);}
                        moving.add(0,travel,0).rotate(inverseBody);
                        vx=Mth.lerp(weight,vx,moving.x);vy=Mth.lerp(weight,vy,moving.y);vz=Mth.lerp(weight,vz,moving.z);
                    }
                    if(part!=null&&component.equals("body.front")){vy-=(float)(part.damage()*.08);vz-=(float)(part.damage()*.10);}
                    buffer.addVertex(pose,vx,vy,vz).setColor(color).setUv(.5f,.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(brightness).setNormal(pose,vertices[v+3],vertices[v+4],vertices[v+5]);
                }
            }
            poses.popPose();
        }
        if(!engineOnly){poses.pushPose();poses.translate(0,car.bodyStyle().cabinY(),car.bodyStyle().cabinZ());CockpitInstruments.render(car,poses,buffers,light);poses.popPose();}
        } // Flush preview glass only after the complete opaque model and cockpit instruments.
        if(started!=0&&profileCount<profileTimes.length){profileTimes[profileCount++]=System.nanoTime()-started;profileTriangles+=submitted;}
    }
    public static net.minecraft.world.phys.Vec3 componentCenter(CarEntity car,String key){
        double x=0,y=0,z=0;int count=0;for(var c:chunks(car))if(componentKey(c).equals(key)&&visible(c,car)){for(int i=0;i<c.vertices.length;i+=6){x+=c.vertices[i];y+=c.vertices[i+1];z+=c.vertices[i+2];count++;}}
        return count==0?new net.minecraft.world.phys.Vec3(0,.65,0):new net.minecraft.world.phys.Vec3(x/count,y/count,z/count);
    }
}
