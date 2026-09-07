"""Original procedural low-poly assets. Run: blender -b --python assets/blender/build_assets.py
Blender is only an authoring dependency. Runtime uses exported mesh JSON and NeoForge OBJ models.
"""
import bpy, math, json, struct, zlib, hashlib
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'mod/src/main/resources/assets/autopropulsion'
SRC=ROOT/'assets/source';PREVIEW=ROOT/'docs/images'
for p in [SRC,PREVIEW,OUT/'models/vehicle',OUT/'models/item/parts',OUT/'textures/entity',OUT/'textures/block/palette',OUT/'textures/item']:
    p.mkdir(parents=True,exist_ok=True)
PALETTE={'paint':(0.72,.14,.085),'dark':(.026,.038,.048),'rubber':(.045,.050,.057),'metal':(.56,.61,.64),
         'alloy':(.78,.81,.83),'glass':(.055,.17,.21),'red':(.70,.03,.025),'amber':(1,.43,.045),
         'white':(.92,.98,1),'blue':(.06,.30,.47),'green':(.10,.45,.29),'bronze':(.56,.35,.13),'cloth':(.07,.09,.12)}
MATS={}
def png(path,color,size=16):
    rgb=bytes(round(max(0,min(1,c))*255) for c in color[:3]);row=b'\0'+rgb*size
    def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',size,size,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(row*size))+chunk(b'IEND',b''))
for name,rgb in PALETTE.items():
    m=bpy.data.materials.new(name);m.diffuse_color=(*rgb,1);m.use_nodes=True
    bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=(*rgb,1);bs.inputs['Roughness'].default_value=.32 if name in ('paint','alloy','glass') else .62
    bs.inputs['Metallic'].default_value=.75 if name in ('metal','alloy','bronze') else 0
    MATS[name]=m;png(OUT/f'textures/block/palette/{name}.png',rgb)
png(OUT/'textures/entity/white.png',(1,1,1));png(OUT/'textures/item/part.png',PALETTE['metal'])
GROUP='body';OBJECTS=[]
def reset():
    global OBJECTS
    bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False);OBJECTS=[]
def finish(obj,name,mat):
    obj.name=name;obj.data.materials.append(MATS[mat]);obj['group']=GROUP;OBJECTS.append(obj);return obj
def box(name,loc,scale,mat='metal',bevel=0,rot=(0,0,0)):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc,rotation=rot);o=finish(bpy.context.object,name,mat);o.scale=scale
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if bevel:
        mod=o.modifiers.new('machined_edges','BEVEL');mod.width=bevel;mod.segments=1
        # Blender <=4.0 requires Auto Smooth for weighted normals; newer versions removed this property.
        if hasattr(o.data,'use_auto_smooth'):o.data.use_auto_smooth=True
        o.modifiers.new('weighted_normals','WEIGHTED_NORMAL')
    return o
def cylinder(name,loc,radius,depth,mat='metal',axis='Z',vertices=12):
    rot={'Z':(0,0,0),'X':(0,math.pi/2,0),'Y':(math.pi/2,0,0)}[axis]
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices,radius=radius,depth=depth,location=loc,rotation=rot)
    return finish(bpy.context.object,name,mat)
def ring(name,loc,radius,tube,mat='metal',axis='Z'):
    rot={'Z':(0,0,0),'X':(0,math.pi/2,0),'Y':(math.pi/2,0,0)}[axis]
    bpy.ops.mesh.primitive_torus_add(major_segments=16,minor_segments=6,location=loc,rotation=rot,major_radius=radius,minor_radius=tube)
    return finish(bpy.context.object,name,mat)
def mesh(name,verts,faces,mat):
    data=bpy.data.meshes.new(name);data.from_pydata(verts,[],faces);data.update();o=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(o);return finish(o,name,mat)
def pipe(name,a,b,r=.035,mat='metal'):
    a,b=Vector(a),Vector(b);o=cylinder(name,(a+b)*.5,r,(b-a).length,mat);o.rotation_euler=(b-a).to_track_quat('Z','Y').to_euler();return o
def bolt(loc,r=.022):return cylinder('bolt',loc,r,.04,'alloy',vertices=6)
def triangulated(objects):
    bpy.context.view_layer.update()
    deps=bpy.context.evaluated_depsgraph_get()
    for ob in objects:
        if ob.type!='MESH':continue
        ev=ob.evaluated_get(deps);me=ev.to_mesh();me.calc_loop_triangles();matrix=ob.matrix_world
        for tri in me.loop_triangles:
            points=[matrix@me.vertices[i].co for i in tri.vertices]
            n=(points[1]-points[0]).cross(points[2]-points[0]).normalized()
            mat=ob.data.materials[tri.material_index].name if len(ob.data.materials) else 'metal'
            yield ob,points,n,mat
        ev.to_mesh_clear()
def mc(v):return [round(v.x,5),round(v.z,5),round(-v.y,5)]
def scene(camera_loc,target,scale,width,height):
    sc=bpy.context.scene;sc.render.engine='CYCLES';sc.cycles.device='CPU';sc.cycles.samples=32
    # Distribution Blender builds may omit OpenImageDenoise. Previews must not depend on it.
    # This is authoring-only: exported runtime geometry is unaffected. See docs/decisions/0001-testing.md.
    sc.cycles.use_denoising=False
    for layer in sc.view_layers:
        if hasattr(layer.cycles,'use_denoising'):layer.cycles.use_denoising=False
    sc.render.resolution_x=width;sc.render.resolution_y=height;sc.render.resolution_percentage=100
    sc.render.image_settings.file_format='PNG';sc.world.color=(.12,.12,.12)
    bpy.ops.object.camera_add(location=camera_loc);camera=bpy.context.object;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=scale;sc.camera=camera
    for loc,power,size in [((2,-4,8),1500,7),((-5,2,5),1100,5),((4,6,5),1700,4)]:
        bpy.ops.object.light_add(type='AREA',location=loc);o=bpy.context.object;o.data.energy=power;o.data.shape='DISK';o.data.size=size;o.rotation_euler=(Vector(target)-o.location).to_track_quat('-Z','Y').to_euler()
    sc.view_settings.view_transform='Standard'
    return sc

def build_car():
    global GROUP
    reset();GROUP='body'
    box('unibody_lower',(0,0,.51),(1.72,4.02,.40),'paint',.075)
    box('undertray',(0,0,.29),(1.58,3.82,.11),'dark',.02)
    box('rear_quarters',(0,1.31,.82),(1.72,1.50,.34),'paint',.06)
    box('roof',(0,.12,1.43),(1.46,1.42,.10),'paint',.055)
    for x in (-.77,.77):
        pipe('a_pillar',(x,-.83,.88),(x,-.54,1.43),.046,'paint');pipe('c_pillar',(x,1.12,.91),(x,.80,1.43),.055,'paint')
        box('b_pillar',(x,.2,1.14),(.075,.085,.51),'dark')
        mesh('front_side_glass',[(x,-.78,.91),(x,.15,.91),(x,.15,1.38),(x,-.51,1.38)],[(0,1,2,3)],'glass')
        mesh('rear_side_glass',[(x,.25,.91),(x,1.05,.91),(x,.77,1.38),(x,.25,1.38)],[(0,1,2,3)],'glass')
        box('door_skin',(x,.0,.73),(.12,1.76,.28),'paint',.025)
        box('rocker',(x,0,.34),(.15,2.3,.10),'dark')
        box('door_handle',(x*1.07,.41,.9),(.035,.17,.035),'alloy',.01)
        box('mirror',(x*1.19,-.66,1.03),(.24,.22,.10),'dark',.025)
    mesh('windshield',[(-.72,-.85,.91),(.72,-.85,.91),(.70,-.53,1.39),(-.70,-.53,1.39)],[(0,1,2,3)],'glass')
    mesh('rear_window',[(-.70,.80,1.39),(.70,.80,1.39),(.74,1.20,.95),(-.74,1.20,.95)],[(0,1,2,3)],'glass')
    box('hatch',(0,1.68,.95),(1.55,.71,.10),'paint',.04)
    for y in (-2.04,2.04):box('bumper',(0,y,.52),(1.76,.19,.23),'dark',.04)
    box('grille',(0,-2.145,.68),(.64,.035,.19),'dark')
    for x in (-.63,.63):
        box('headlamp',(x,-2.125,.76),(.42,.055,.18),'white',.02)
        box('tail_light',(x,2.125,.77),(.39,.05,.19),'red',.02)
        box('indicator',(x,-2.15,.65),(.20,.03,.045),'amber')
    for x in [-.23,-.115,0,.115,.23]:box('grille_slat',(x,-2.168,.68),(.018,.02,.15),'metal')
    box('front_plate',(0,-2.16,.44),(.48,.025,.11),'white')
    box('rear_plate',(0,2.16,.55),(.48,.025,.11),'white')
    for x in (-.36,.36):
        box('seat_base',(x,.14,.56),(.53,.61,.13),'cloth',.045)
        box('seat_back',(x,.43,.88),(.50,.15,.65),'cloth',.04,rot=(math.radians(8),0,0))
        box('headrest',(x,.48,1.22),(.29,.15,.15),'dark',.035)
    box('dashboard',(0,-.57,.94),(1.35,.35,.19),'dark',.04)
    ring('steering_wheel',(-.37,-.40,1.04),.135,.016,'rubber','Y')
    cylinder('exhaust_tip',(-.64,2.12,.30),.055,.28,'alloy','Y')
    pivots={'body':Vector((0,0,0)),'enginebay':Vector((0,0,0)),'hood':Vector((0,-.69,.91))}
    GROUP='enginebay';box('engine_block',(0,-1.20,.73),(.67,.61,.40),'metal',.035)
    box('valve_cover',(0,-1.20,.96),(.57,.58,.09),'red',.02)
    for x in (-.21,-.07,.07,.21):box('cover_rib',(x,-1.2,1.015),(.045,.45,.02),'alloy')
    box('battery',(-.54,-1.40,.78),(.27,.31,.24),'dark',.015)
    box('radiator',(0,-1.82,.65),(1.15,.09,.48),'metal')
    pipe('intake_hose',(.38,-1.23,.93),(.52,-1.68,.84),.055,'dark')
    GROUP='hood';box('hood',(0,-1.35,.91),(1.65,1.28,.07),'paint',.035)
    for x in (-.85,.85):
        for y in (-1.28,1.28):
            name='wheel_'+('front' if y<0 else 'rear')+'_'+('left' if x<0 else 'right');GROUP=name;pivots[name]=Vector((x,y,.34))
            ring('tire',(x,y,.34),.235,.090,'rubber','X')
            cylinder('rim',(x,y,.34),.215,.205,'alloy','X',16)
            side=x+math.copysign(.111,x);cylinder('rim_inset',(side,y,.34),.180,.008,'dark','X',16)
            for i in range(5):
                a=i*math.tau/5
                box('spoke',(side,y+math.sin(a)*.088,.34+math.cos(a)*.088),(.015,.043,.19),'alloy',.004,rot=(a,0,0))
            cylinder('hub',(side,y,.34),.058,.023,'metal','X',12)
    objects=list(OBJECTS);groups={}
    for ob,points,n,mat in triangulated(objects):
        name=ob['group'];pivot=pivots[name];rgb=PALETTE[mat];hexcol=''.join(f'{round(c*255):02x}' for c in rgb)
        groups.setdefault(name,{'name':name,'pivot':mc(pivot),'faces':[]})['faces'].append({'v':[mc(p-pivot) for p in points],'n':mc(n),'c':hexcol})
    payload={'format':1,'authoring':'Blender '+bpy.app.version_string,'units':'metres','front':'+Z','groups':list(groups.values()),'locators':{k:mc(v) for k,v in pivots.items()}}
    (OUT/'models/vehicle/hatchback.mesh.json').write_text(json.dumps(payload,separators=(',',':'))+'\n')
    bpy.ops.wm.save_as_mainfile(filepath=str(SRC/'h1-hatchback.blend'),compress=True)
    bpy.ops.object.select_all(action='DESELECT')
    for o in objects:o.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(SRC/'h1-hatchback.glb'),export_format='GLB',use_selection=True)
    GROUP='stage';box('floor',(0,0,-.07),(200,200,.1),'dark')
    sc=scene((6,-7,4.4),(0,0,.65),6.5,1200,760);sc.render.filepath=str(PREVIEW/'h1-hatchback.png');bpy.ops.render.render(write_still=True)
    return sum(len(g['faces']) for g in groups.values())

def part_geometry(cat):
    if cat in ('rod','pin','valve','plug','injector','lifter','bolt'):
        if cat=='rod':
            ring('big_end',(0,0,.12),.105,.032);box('beam',(0,0,.34),(.075,.065,.35),'metal',.012);ring('small_end',(0,0,.54),.055,.020)
        elif cat=='valve':cylinder('stem',(0,0,.30),.025,.45);cylinder('valve_head',(0,0,.07),.125,.035,'alloy')
        elif cat=='plug':cylinder('ceramic',(0,0,.33),.045,.24,'white');cylinder('hex',(0,0,.18),.07,.09,vertices=6);cylinder('thread',(0,0,.07),.04,.12)
        else:cylinder('shaft',(0,0,.23),.045,.42);cylinder('head',(0,0,.45),.08,.055,'alloy',vertices=6)
    elif cat in ('piston','liner','filter','starter','alternator','bottle','can'):
        radius=.17 if cat in ('piston','liner') else .13
        cylinder('body',(0,0,.25),radius,.40,'alloy' if cat=='piston' else 'metal')
        for z in (.34,.38,.42):ring('groove',(0,0,z),radius+.001,.009,'dark')
        if cat=='piston':cylinder('wrist_pin',(0,0,.16),.045,.38,'dark','X')
        if cat in ('bottle','can'):cylinder('neck',(0,0,.5),.055,.12,'bronze');box('valve',(0,0,.58),(.17,.035,.035),'red')
        if cat in ('starter','alternator'):cylinder('drive',(0,0,.49),.045,.16,'alloy')
    elif cat in ('ring','bearing','pulley','disc','gear','hub','tire','rim','wheel'):
        if cat in ('tire','rim','wheel'):ring('outer',(0,0,.12),.27,.075,'rubber' if cat!='rim' else 'alloy');cylinder('hub',(0,0,.12),.06,.14)
        else:
            ring('outer',(0,0,.10),.22,.035,'metal');cylinder('centre',(0,0,.10),.075,.08,'dark')
            if cat=='gear':
                for i in range(16):
                    a=i*math.tau/16;box('tooth',(.24*math.cos(a),.24*math.sin(a),.10),(.07,.065,.06),'alloy',rot=(0,0,a))
            else:
                for i in range(5):
                    a=i*math.tau/5;pipe('spoke',(0,0,.10),(.20*math.cos(a),.20*math.sin(a),.10),.022)
    elif cat in ('shaft','crank','rack','bar'):
        cylinder('main',(0,0,.18),.045,.85,'alloy','Y')
        for y in (-.3,-.1,.1,.3):cylinder('lobe',(.025,y,.18),.10,.06,'metal','Y')
        if cat=='crank':
            for y in (-.25,.05,.30):box('counterweight',(.09,y,.18),(.21,.075,.20),'dark',.025)
    elif cat in ('block','head','rotary_block','rotary_housing','rotor'):
        if cat=='rotor':cylinder('triangular_rotor',(0,0,.10),.32,.16,'alloy',vertices=3);cylinder('eccentric_bore',(0,0,.19),.07,.015,'dark')
        elif cat in ('rotary_block','rotary_housing'):
            cylinder('housing',(0,0,.2),.36,.28,'metal',vertices=12);cylinder('rotor',(0,0,.36),.26,.06,'dark',vertices=3)
        else:
            box('casting',(0,0,.22),(.55,.78,.40),'metal',.035)
            for y in (-.27,-.09,.09,.09+.18):cylinder('bore',(0,y,.425),.075,.014,'dark')
            for x in (-.28,.28):
                for y in (-.28,.28):bolt((x,y,.43))
    elif cat in ('turbo','supercharger','pump','carb','throttle','actuator','thermostat'):
        ring('volute',(0,0,.24),.145,.065,'metal','Y');cylinder('compressor',(0,-.065,.24),.11,.05,'dark','Y')
        pipe('outlet',(.14,0,.25),(.28,0,.40),.055,'alloy');box('mount',(0,.08,.08),(.25,.24,.06),'dark')
        if cat=='supercharger':cylinder('rotor_case',(0,.21,.25),.16,.45,'metal','Y')
    elif cat in ('pipe','header','exhaust','intake','injection'):
        for i,x in enumerate((-.22,-.07,.07,.22)):
            pipe('runner',(x,-.20,.08),(x,.10,.30),.035);pipe('runner_bend',(x,.10,.30),(x,.32,.30),.035)
        cylinder('collector',(0,.32,.30),.065,.60,'metal','X')
    elif cat in ('spring','belt','fan'):
        if cat=='spring':
            cylinder('damper',(0,0,.30),.045,.55,'alloy')
            points=[(.1*math.cos(i*.32),.1*math.sin(i*.32),.08+i*.004) for i in range(115)]
            for a,b in zip(points,points[1:]):pipe('coil',a,b,.013,'red')
        elif cat=='fan':
            cylinder('motor',(0,0,.10),.06,.15,'dark')
            for i in range(7):a=i*math.tau/7;box('blade',(.12*math.cos(a),.12*math.sin(a),.12),(.25,.07,.02),'dark',rot=(0,0,a))
        else:ring('belt',(0,0,.06),.28,.025,'dark')
    elif cat in ('radiator','battery','electronics','gearbox','differential','tank','pan','pad','caliper','gasket'):
        dims=(.62,.12,.43) if cat=='radiator' else (.42,.32,.20 if cat in ('electronics','gasket','pad') else .34)
        box('case',(0,0,dims[2]/2),dims,'blue' if cat=='electronics' else 'metal',.02)
        if cat=='radiator':
            for x in [i*.035-.26 for i in range(16)]:box('fin',(x,-.07,.22),(.013,.015,.36),'dark')
        elif cat=='electronics':
            for x in (-.14,.14):box('connector',(x,-.19,.09),(.12,.08,.06),'dark')
            box('label',(0,0,.212),(.24,.18,.01),'blue')
        else:
            for x in (-.17,.17):
                for y in (-.12,.12):bolt((x,y,dims[2]+.01))
    elif cat in ('door','hood','panel','bumper','spoiler','dash','light','mirror','seat','cage','shifter','laptop','jack','station','booth'):
        if cat=='seat':box('cushion',(0,0,.12),(.40,.43,.13),'cloth',.035);box('back',(0,.16,.38),(.40,.12,.50),'cloth',.035)
        elif cat=='laptop':box('keyboard',(0,0,.055),(.6,.43,.04),'dark');box('screen',(0,.20,.27),(.6,.025,.42),'blue')
        elif cat=='cage':
            for x in (-.28,.28):pipe('hoop_leg',(x,0,0),(x,0,.65),.025);pipe('brace',(x,0,.65),(x,.4,0),.025)
            pipe('hoop_top',(-.28,0,.65),(.28,0,.65),.025)
        elif cat=='door':box('skin',(0,0,.25),(.09,.7,.43),'paint',.02);box('glass',(0,0,.60),(.04,.62,.27),'glass')
        elif cat=='spoiler':box('wing',(0,0,.3),(.9,.2,.06),'dark');box('stand_l',(-.28,0,.15),(.035,.10,.3));box('stand_r',(.28,0,.15),(.035,.10,.3))
        elif cat in ('station','booth'):box('base',(0,0,.40),(.45,.45,.8),'paint',.03);box('display',(0,-.235,.59),(.27,.02,.15),'glass')
        elif cat=='shifter':cylinder('stick',(0,0,.21),.02,.34);cylinder('knob',(0,0,.4),.06,.09,'dark')
        else:box('panel',(0,0,.18),(.65,.30,.16),'white' if cat=='light' else 'paint',.025)
    else:
        box('component',(0,0,.16),(.37,.28,.30),'metal',.02);bolt((-.13,-.08,.33));bolt((.13,.08,.33))

def export_part(part,objects):
    triangles=list(triangulated(objects));points=[p for _,ps,_,_ in triangles for p in ps]
    lo=Vector([min(p[i] for p in points) for i in range(3)]);hi=Vector([max(p[i] for p in points) for i in range(3)]);center=(lo+hi)/2;scale=.80/max(hi-lo)
    lines=[f'# AutoPropulsion Age: {part["id"]}; generated in Blender {bpy.app.version_string}',f'mtllib {part["id"]}.mtl','vt 0.5 0.5'];index=1;materials=set()
    for _,ps,n,mat in triangles:
        materials.add(mat);lines.append('usemtl '+mat)
        for p in ps:
            q=mc((p-center)*scale);lines.append('v '+' '.join(f'{v+.5:.5f}' for v in q))
        lines.append(f'f {index}/1 {index+1}/1 {index+2}/1');index+=3
    path=OUT/f'models/item/parts/{part["id"]}.obj';path.write_text('\n'.join(lines)+'\n')
    path.with_suffix('.mtl').write_text('\n'.join(f'newmtl {m}\nKd 1 1 1\nd 1\nmap_Kd #mat_{m}\n' for m in sorted(materials)))
    model={'loader':'neoforge:obj','model':f'autopropulsion:models/item/parts/{part["id"]}.obj','automatic_culling':False,
           'textures':{'particle':'autopropulsion:block/palette/metal',**{'mat_'+m:'autopropulsion:block/palette/'+m for m in sorted(materials)}},
           'display':{'gui':{'rotation':[30,225,0],'scale':[1,1,1]},'ground':{'translation':[0,3,0],'scale':[.45,.45,.45]},
                      'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.5,.5,.5]},
                      'firstperson_righthand':{'rotation':[0,45,0],'scale':[.6,.6,.6]},'fixed':{'scale':[.7,.7,.7]}}}
    path.with_suffix('.json').write_text(json.dumps(model,indent=2)+'\n')
    return len(triangles)

def build_parts():
    global GROUP
    reset();catalog=json.loads((ROOT/'assets/catalog.json').read_text())['parts'];counts={}
    for i,part in enumerate(catalog):
        GROUP=part['id'];start=len(OBJECTS);part_geometry(part['category']);objects=OBJECTS[start:]
        counts[part['id']]=export_part(part,objects)
        collection=bpy.data.collections.new(part['id']);bpy.context.scene.collection.children.link(collection)
        offset=Vector(((i%14)*1.2,(i//14)*1.2,0))
        for obj in objects:
            for c in list(obj.users_collection):c.objects.unlink(obj)
            collection.objects.link(obj);obj.location+=offset
        collection['part_id']='autopropulsion:'+part['id'];collection['gameplay_status']='functional' if part['functional'] else 'catalogue-only'
    bpy.ops.wm.save_as_mainfile(filepath=str(SRC/'component-catalogue.blend'),compress=True)
    GROUP='stage';box('floor',(7.8,5.4,-.075),(30,30,.1),'dark')
    sc=scene((17,-9,25),(7.8,5.4,0),19,1600,1150)
    for o in bpy.data.objects:
        if o.type=='LIGHT':o.location+=Vector((7.8,5.4,8));o.data.energy*=8;o.data.size=12;o.rotation_euler=(Vector((7.8,5.4,0))-o.location).to_track_quat('-Z','Y').to_euler()
    sc.render.filepath=str(PREVIEW/'component-catalogue.png');bpy.ops.render.render(write_still=True)
    (OUT/'models/item/part.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'autopropulsion:item/part'},'overrides':[{'predicate':{'custom_model_data':p['model_index']},'model':p['model']} for p in sorted(catalog,key=lambda p:p['model_index'])]},indent=2)+'\n')
    return counts

car_triangles=build_car();counts=build_parts()
manifest={'generator':'assets/blender/build_assets.py','blender_version':bpy.app.version_string,'car_triangles':car_triangles,'part_count':len(counts),'part_triangles':counts,
          'preview_render':{'engine':'CYCLES','device':'CPU','samples':32,'denoising':False},
          'provenance':'Original procedural meshes; category-level reuse and proxy geometry, not hand-sculpted production art.',
          'runtime_formats':['vehicle mesh JSON','NeoForge OBJ/MTL'],'authoring_units':'metres',
          'source_files':['assets/source/h1-hatchback.blend','assets/source/component-catalogue.blend','assets/source/h1-hatchback.glb']}
(ROOT/'assets/manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('AUTOPROPULSION_ASSETS_PASS',car_triangles,'car triangles',len(counts),'component models')
