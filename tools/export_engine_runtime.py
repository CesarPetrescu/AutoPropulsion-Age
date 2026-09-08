"""Run through Blender MCP. APA2: seven cores, removable kits and fitted port adapters.
No desktop input. The original editable kit is not modified.
"""
import bpy, gzip, struct, math, json
from pathlib import Path
from mathutils import Vector, Matrix

repo=Path(__file__).resolve().parent.parent
output=repo/'src/main/resources/assets/sparkmotors/models/entity';output.mkdir(parents=True,exist_ok=True)
manifest=json.loads((repo/'assets/modular_car_kit/parts_manifest.json').read_text())['parts']
lookup={p['id']:p for p in manifest}
scene=bpy.data.scenes['SM_01_Assembled'];bpy.context.window.scene=scene;scene.frame_set(1);bpy.context.view_layer.update()
families=['i4_engine','v6_engine','flat4_engine']+[f'rotary_{i}rotor' for i in range(1,5)]
chunks=[];temporary=[]
def owner(ob):
    while ob and 'part_id' not in ob:ob=ob.parent
    return ob
def ancestors(p):
    result=[p]
    while p.get('parent_part'):p=lookup[p['parent_part']];result.append(p)
    return [p['id'].split(':')[1] for p in result]
def xyz(v):return (v[0],v[2],-v[1])
def srgb(c):return round(max(0,min(1,12.92*c if c<=.0031308 else 1.055*c**(1/2.4)-.055))*255)
def meta(name,cat=18,group=0,variant=0,hinge=0,pivot=(0,0,0),angle=0,family=127,slot=-1,tier=0,induction=127):
    return dict(name=name,cat=cat,group=group,variant=variant,hinge=hinge,pivot=xyz(pivot),angle=angle,family=family,slot=slot,tier=tier,induction=induction)
def collect(ob,m):
    if ob.name.startswith('APA_runtime_'):me=ob.data.copy()
    else:
        deps=bpy.context.evaluated_depsgraph_get();me=bpy.data.meshes.new_from_object(ob.evaluated_get(deps),depsgraph=deps)
    me.transform(ob.matrix_world);me.calc_loop_triangles();batches={}
    for tri in me.loop_triangles:
        mat=me.materials[tri.material_index] if len(me.materials)>tri.material_index else None
        mn=mat.name if mat else '';kind=1 if mn=='SM_paint' else 2 if mn=='SM_glass' else 3 if mn in ['SM_white','SM_red','SM_amber','SM_screen'] and m['cat']==6 else 0
        rgb=tuple(srgb(c) for c in mat.diffuse_color[:3]) if mat else (170,180,190)
        rgba=(255<<24)|(rgb[0]<<16)|(rgb[1]<<8)|rgb[2];normal=xyz(tri.normal)
        for vi in tri.vertices:batches.setdefault(kind,[]).append((*xyz(me.vertices[vi].co),*normal,rgba))
    for kind,vertices in batches.items():chunks.append(dict(m,kind=kind,vertices=vertices))
    bpy.data.meshes.remove(me)

hinges=['door_front_left','door_front_right','door_rear_left','door_rear_right','hood','trunk_lid'];core_parts=[]
for part in manifest:
    if part['state']=='workshop':continue
    name=part['id'].split(':')[1];cat=part['category'];names=ancestors(part)
    family=next((i for i,n in enumerate(families) if n in names),None)
    group=-1;variant=0;slot=-1
    if family is not None:
        if cat in [25,26,27,29,30] or name.startswith(('intake_port','exhaust_port')):continue
        group=0
        if cat in [20,21,22,23] or name.startswith(('eccentric_shaft','rotor_','apex_seal','stationary_gear')):slot=4
        if name.startswith('rotor_housing'):slot=-1
        core_parts.append(name)
    elif name in ['radiator','radiator_fan','coolant_reservoir']:group=0;slot=3
    elif name=='coolant_hoses':continue
    elif cat in [31,32]:continue
    elif name=='battery':group=0;slot=2
    elif 'manual_5speed' in names:group=1
    elif cat==13:group=2
    elif cat==15:group=3
    elif cat==16:group=4
    if name in ['front_bumper','side_skirt_-1','side_skirt_1','front_fender_-1','front_fender_1']:group=5;variant=1
    if part['state']=='upgrade' and family is None:
        if name.startswith(('sport_rim_','slick_tire_')):group=2;variant=2
        elif name.startswith('sport_coilover_'):group=4;variant=2
        elif name in ['rear_spoiler','front_splitter','sport_front_bumper','widebody_fender_-1','widebody_fender_1','sport_skirt_-1','sport_skirt_1']:group=5;variant=2
        else:continue
    elif name.startswith(('rim_','tire_','coilover_')):variant=1
    if name.startswith('gear_set_') or name=='gearbox_shafts':continue
    hinge=next((hinges.index(n)+1 for n in names if n in hinges),0)
    hp=lookup['sparkmotors:'+hinges[hinge-1]] if hinge else part
    m=meta(name,cat,group,variant,hinge,hp['mount_world_m'],hp.get('hinge',{}).get('open_degrees',0) if hinge else 0,1<<family if family is not None else 127,slot)
    if name in ['radiator','radiator_fan']:m['tier']=1
    root=bpy.data.objects[part['object']]
    for ob in scene.objects:
        if ob.type in {'MESH','CURVE','FONT'} and owner(ob)==root:
            # Rebuilt below with closed mating surfaces, circular beads and continuous arch trim.
            if name.startswith(('tire_','slick_tire_','side_skirt_','sport_skirt_')) or ob.name.startswith(('Wheel arch trim','Door outer','Rocker')):continue
            target=m
            if name.startswith('rear_cv_axle_'):target=dict(m,name='cv_axle_'+('rl' if name.endswith('-1') else 'rr'))
            if name=='steering_rack' and ob.name.startswith('Tie rod'):target=dict(m,name='tie_rod_'+('fl' if sum((ob.matrix_world@Vector(v)).x for v in ob.bound_box)<0 else 'fr'))
            if name.startswith(('coilover_','sport_coilover_')):target=dict(m,name=('suspension_spring_' if 'spring' in ob.name.lower() else 'suspension_damper_')+name[-2:])
            if name=='radiator_fan' and 'shroud' in ob.name.lower():target=dict(m,name='radiator_fan_shroud')
            collect(ob,target)

def material(name,color):
    mat=bpy.data.materials.get(name) or bpy.data.materials.new(name);mat.diffuse_color=(*color,1);return mat
metal=material('APA service aluminium',(.38,.42,.46));black=material('APA service rubber',(.018,.024,.031))
blue=material('APA silicone',(.02,.17,.40));red=material('APA ignition',(.38,.025,.02))
def mesh(name,verts,faces,mat,m):
    data=bpy.data.meshes.new('APA_runtime_'+name);data.from_pydata(verts,[],faces);data.materials.append(mat)
    ob=bpy.data.objects.new('APA_runtime_'+name,data);scene.collection.objects.link(ob);temporary.append(ob)
    collect(ob,m);return ob
def box(name,center,size,mat,m):
    c=Vector(center);s=Vector(size)/2
    verts=[c+Vector((sx*s.x,sy*s.y,sz*s.z)) for sx,sy,sz in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
    return mesh(name,verts,[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)],mat,m)
def pipe(name,points,radius,mat,m,sides=10):
    verts=[];faces=[]
    for a,b in zip(points,points[1:]):
        a=Vector(a);b=Vector(b);d=(b-a).normalized();u=d.cross(Vector((0,0,1)))
        if u.length<.01:u=d.cross(Vector((1,0,0)))
        u.normalize();v=d.cross(u);start=len(verts)
        for c in [a,b]:
            for i in range(sides):verts.append(c+radius*(u*math.cos(i*math.tau/sides)+v*math.sin(i*math.tau/sides)))
        for i in range(sides):j=(i+1)%sides;faces.append((start+i,start+j,start+sides+j,start+sides+i))
        faces.append(tuple(start+i for i in reversed(range(sides))));faces.append(tuple(start+sides+i for i in range(sides)))
    return mesh(name,verts,faces,mat,m)

box('rear_valance',(0,2.115,.92),(1.76,.05,.21),bpy.data.materials['SM_paint'],meta('rear_valance',cat=3,group=-1))
box('scuttle',(0,-.77,1.008),(1.55,.11,.055),black,meta('scuttle',cat=3,group=-1))
exec(compile((repo/'tools/build_body_geometry.py').read_text(),str(repo/'tools/build_body_geometry.py'),'exec'))
# Detailed hardware is generated separately using the same native Blender mesh helpers.
exec(compile((repo/'tools/build_powertrain_hardware.py').read_text(),str(repo/'tools/build_powertrain_hardware.py'),'exec'))
# Shared cooling hoses and exhaust adapters to each family's port height and core width.
for f in range(7):
    fam=1<<f;portz=[.87,.875,.78,.62,.62,.62,.62][f];portx=[.193,.29,.34,.285,.285,.285,.285][f]
    cm=meta('service_coolant_'+str(f),family=fam,slot=3)
    pipe('coolant_feed_'+str(f),[(-.48,-1.90,.83),(-.50,-1.78,.78),(-portx,-1.68,.66)],.019,blue,dict(cm,name='coolant_upper_hose_'+str(f)))
    pipe('coolant_return_'+str(f),[(portx,-1.12,.65),(.53,-1.12,.65),(.56,-1.78,.62),(.48,-1.93,.62)],.018,blue,dict(cm,name='coolant_lower_hose_'+str(f)))
    tm=meta('turbo_header_'+str(f),family=fam,slot=5,induction=26)
    pipe('turbo_feed_'+str(f),[(portx,-1.43,portz),(.405,-1.43,.75),(.47,-1.31,.81)],.023,metal,tm)
    pipe('turbo_downpipe_'+str(f),[(.47,-1.19,.81),(.54,-1.12,.69),(.50,-1.0,.45),(.35,-.90,.29)],.025,metal,tm)

for original,offset,name,mode in [('turbocharger',(.065,0,0),'turbocharger',1),('centrifugal_supercharger',(.10,-.045,0),'supercharger',2)]:
    for ob in bpy.data.objects['SM_'+original].children_recursive:
        if ob.type!='MESH':continue
        if mode==2 and not ob.name.startswith(('Supercharger compressor','SC drive pulley')):continue
        deps=bpy.context.evaluated_depsgraph_get();data=bpy.data.meshes.new_from_object(ob.evaluated_get(deps),depsgraph=deps)
        data.transform(ob.matrix_world);data.transform(Matrix.Translation(offset))
        cp=bpy.data.objects.new('APA_runtime_'+ob.name,data);scene.collection.objects.link(cp);temporary.append(cp)
        collect(cp,meta(name,cat=31 if mode==1 else 32,slot=5,tier=mode,induction=1<<mode))
for mode in range(1,7):
    is_turbo=mode in [1,3,4]
    m=meta('boost_plumbing_'+str(mode),cat=31 if is_turbo else 32,slot=5,tier=mode,induction=1<<mode)
    source=(.48,-1.25,.85) if is_turbo else (.48,-1.69,.86)
    pipe('compressor_out_'+str(mode),[source,(.59,-1.62,.87),(.60,-1.83,.83),(.60,-2.04,.70),(.48,-2.04,.70)],.023,blue,dict(m,name='charge_pipe_'+str(mode)))
    box('intercooler_'+str(mode),(0,-2.04,.70),(.98,.038,.25),metal,dict(m,name='service_intercooler_'+str(mode)))
    for yy in [.61,.66,.71,.76,.81]:box('intercooler_fin_'+str(mode)+str(yy),(0,-2.066,yy),(.94,.01,.01),black,dict(m,name='service_intercooler_'+str(mode)))
    pipe('charge_return_'+str(mode),[(-.48,-2.04,.70),(-.60,-2.04,.70),(-.61,-1.85,.91),(-.59,-1.56,.98),(-.51,-1.30,.98),(-.425,-1.30,.94)],.023,blue,dict(m,name='charge_pipe_'+str(mode)))
    inlet=[(-.45,-1.045,.88),(-.43,-.965,.95),(.43,-.965,.95),(.62,-1.07,.97)]
    if is_turbo:inlet.extend([(.62,-1.30,.97),(.48,-1.30,.93)])
    inlet.append(source)
    pipe('filtered_inlet_'+str(mode),inlet,.019,black,dict(m,name='filtered_inlet_'+str(mode)))
    if is_turbo:
        box('wastegate',(.51,-1.40,.90),(.055,.06,.08),metal,dict(m,name='service_wastegate_'+str(mode)))
        box('blowoff_valve',(-.60,-1.80,.95),(.045,.05,.055),metal,dict(m,name='service_bov_'+str(mode)))
    else:pipe('supercharger_belt',[(0,-1.805,.55),(.48,-1.805,.89),(.50,-1.805,.84),(0,-1.805,.50),(0,-1.805,.55)],.005,black,dict(m,name='service_blower_belt_'+str(mode)),sides=6)

# Individual service mounts and cockpit instruments share the existing car coordinate frame.
exec(compile((repo/'tools/build_service_geometry.py').read_text(),str(repo/'tools/build_service_geometry.py'),'exec'))
merged={}
for c in chunks:
    key=(c['name'],c['cat'],c['group'],c['variant'],c['hinge'],*c['pivot'],c['angle'],c['family'],c['slot'],c['tier'],c['induction'],c['kind'])
    merged.setdefault(key,[]).extend(c['vertices'])
with open(output/'sedan.mesh.gz','wb') as raw:
    with gzip.GzipFile(filename='',fileobj=raw,mode='wb',compresslevel=9,mtime=0) as f:
        f.write(struct.pack('>ii',0x41504132,len(merged)))
        for key,verts in merged.items():
            name,cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind=key
            encoded=name.encode();f.write(struct.pack('>H',len(encoded)));f.write(encoded)
            f.write(struct.pack('>iiii4f6i',cat,group,variant,hinge,px,py,pz,angle,family,slot,tier,induction,kind,len(verts)))
            for v in verts:f.write(struct.pack('>6fI',*v))
report={'format':'APA2 gzip, big endian','source':'assets/modular_car_kit/sparkmotors_modular.blend','chunks':len(merged),
        'triangles':sum(len(v)//3 for v in merged.values()),'families':families,'induction':['natural','turbo','supercharger','large-turbo','twin-turbo','roots','twin-screw'],
        'runtime_file':'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz','bytes':(output/'sedan.mesh.gz').stat().st_size,
        'scope':'Seven cores, ten service slots, 42 named hardware choices with distinct geometry, seven induction configurations; internals retained for inspection.'}
(repo/'docs/runtime-assets.json').write_text(json.dumps(report,indent=2)+'\n')
checks=[]
for family in range(7):
    for induction in range(7):
        visible=[c for c in chunks if c['group']==0 and c['family']&(1<<family) and c['induction']&(1<<induction)]
        verts=[v for c in visible for v in c['vertices']]
        hood=min(1.028+(-v[2]+2.105)/1.315*.078-.011-v[1] for v in verts if -2.105<-v[2]<-.79 and abs(v[0])<.865)
        left=min(v[0] for v in verts);right=max(v[0] for v in verts)
        core_front=max(v[2] for c in visible if c['name'] in core_parts for v in c['vertices'])
        core_back=min(v[2] for c in visible if c['name'] in core_parts for v in c['vertices'])
        check={'family':families[family],'induction':['natural','turbo','supercharger','large-turbo','twin-turbo','roots','twin-screw'][induction],
               'hood_clearance_m':round(hood,6),'side_clearance_m':round(min(left+.72,.72-right),6),
               'core_to_radiator_m':round(1.881-core_front,6),'core_to_firewall_m':round(core_back-.80,6),'triangles':len(verts)//3}
        check['passed']=hood>=.005 and left>=-.72 and right<=.72 and core_front<1.86 and core_back>.81
        checks.append(check)
fit={'scope':'Union of every hardware alternative for all 49 family/induction layouts: closed hood plane, side envelope and core/radiator/firewall clearance. Pairwise intersection checks are separate.',
     'configurations':len(checks),'passed':sum(c['passed'] for c in checks),'failed':sum(not c['passed'] for c in checks),'checks':checks}
(repo/'docs/engine-fit-matrix.json').write_text(json.dumps(fit,indent=2)+'\n')
for ob in temporary:
    data=ob.data;bpy.data.objects.remove(ob,do_unlink=True)
    if data.users==0:bpy.data.meshes.remove(data)
result=dict(report,fit_passed=fit['passed'],fit_failed=fit['failed'])
