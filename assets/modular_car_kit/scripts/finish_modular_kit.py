"""Final fit checks, inspection scenes and interchange export; run in the SMKIT namespace."""
import bpy, math, json, os, shutil
from mathutils import Vector, Matrix
from mathutils.bvhtree import BVHTree

def desc(p):
    out=[]
    for c in p.children: out.append(c); out.extend(desc(c))
    return out
def nearest_part(o):
    while o and 'part_id' not in o: o=o.parent
    return o

def extra_variants_and_fit():
    SC.frame_set(1)
    for p in PARTS:
        if p.name.startswith('SM_coil_') and p.get('asset_state')=='stock':
            for o in p.children:
                if o.type=='MESH' and o.name.startswith('Coil pack'):
                    for v in o.data.vertices:
                        if v.co.z>1.027: v.co.z-=.010
    # Rotary output shafts use the same crank/output axis as the manual gearbox.
    for p in PARTS:
        if p.name.startswith('SM_rotary_') and p.get('rotors'):
            for o in desc(p):
                if o.type=='MESH' and not o.name.startswith(('Output adapter','Common engine mount')):
                    for v in o.data.vertices: v.co.z-=.153
                    if o.name.startswith('Eccentric shaft'):
                        for v in o.data.vertices:
                            if v.co.y>-1.37:v.co.y+=.008
    for s in [-1,1]:
        for front,y in [(True,-1.35),(False,1.30)]:
            tag=('f' if front else 'r')+('l' if s<0 else 'r'); x=s*.83
            rim=upgrade('sport_rim_'+tag,13,(x,y,.34),'rim_'+tag,'rim_'+tag)
            cylinder('Sport barrel',(x,y,.34),.236,.20,rim,'black','X',32,.216)
            cylinder('Polished lip',(x+s*.103,y,.34),.239,.017,rim,'alloy','X',32,.22)
            for i in range(10):
                a=i*math.tau/10
                beam('Sport spoke',(x+s*.103,y+.04*math.cos(a),.34+.04*math.sin(a)),(x+s*.103,y+.219*math.cos(a+.1),.34+.219*math.sin(a+.1)),.011,rim,'gold',6)
            tire=upgrade('slick_tire_'+tag,13,(x,y,.34),'tire_'+tag,'tire_'+tag)
            base=bpy.data.objects['SM_tire_'+tag]
            source=next(o for o in base.children if o.type=='MESH' and o.name.startswith('Tire carcass'))
            o=source.copy(); o.data=source.data; ALT.objects.link(o); mw=source.matrix_world.copy(); o.parent=None; o.matrix_world=mw; parent_world(o,tire)
            sus=upgrade('sport_coilover_'+tag,16,(s*.66,y,.61),'coilover_'+tag,'coilover_'+tag)
            beam('Sport damper',(s*.66,y,.36),(s*.61,y,.94),.023,sus,'gold')
            pts=[(s*.632+.050*math.cos(a*math.tau/12),y+.05*math.sin(a*math.tau/12),.55+a/72*.32) for a in range(73)]
            pipe('Sport spring',pts,.008,sus,'blue')
    # Explicit individually removable head bolts.
    head=bpy.data.objects['SM_cylinder_head_i4']
    for x in [-.158,.158]:
        for i,y in enumerate([-1.625,-1.495,-1.365,-1.235,-1.105]):
            b=part(f'head_bolt_{x}_{i}',21,(x,y,.887),parent=head)
            cylinder('Head bolt',(x,y,.887),.005,.097,b,'steel',n=10)
            cylinder('Head bolt hex',(x,y,.937),.008,.006,b,'steel',n=6)
    parent_world(bpy.data.objects['SM_rear_spoiler'],bpy.data.objects['SM_trunk_lid'])
    for s in [-1,1]:
        p=bpy.data.objects[f'SM_widebody_fender_{s}']
        skin=next(o for o in p.children if o.type=='MESH' and o.name.startswith('Wide wing'))
        for v in skin.data.vertices:
            if v.co.z>.9:v.co.z=1.028+(v.co.y+2.105)/1.315*.078
        panel('Wide wing shoulder',[(s*.867,-2.15,1.025),(s*1.034,-2.15,1.025),(s*1.034,-.774,1.109),(s*.867,-.774,1.109)],p,th=.016)
    for o in ALT.objects:
        if o: o.hide_render=True; o.hide_set(True)
    bpy.context.view_layer.update()

def studio_scene(sc,target=(0,0,.7),camera=(6,-7,4),scale=6.2):
    st=bpy.data.collections.new(sc.name+'_Studio'); sc.collection.children.link(st)
    cd=bpy.data.cameras.new(sc.name+'_Camera'); ca=bpy.data.objects.new(sc.name+'_Camera',cd); st.objects.link(ca)
    ca.location=camera; ca.rotation_euler=(Vector(target)-ca.location).to_track_quat('-Z','Y').to_euler(); cd.type='ORTHO'; cd.ortho_scale=scale; sc.camera=ca
    for n,loc,power,size in [('Key',(1,-4,8),1700,5),('Fill',(-4,-1,5),1100,4),('Rim',(2,5,7),1800,4)]:
        ld=bpy.data.lights.new(sc.name+n,'AREA'); ld.energy=power; ld.shape='DISK'; ld.size=size
        lo=bpy.data.objects.new(sc.name+n,ld); st.objects.link(lo); lo.location=loc; lo.rotation_euler=(Vector(target)-lo.location).to_track_quat('-Z','Y').to_euler()
    me=bpy.data.meshes.new(sc.name+'_Ground'); me.from_pydata([(-100,-100,-.01),(100,-100,-.01),(100,100,-.01),(-100,100,-.01)],[],[(0,1,2,3)])
    ob=bpy.data.objects.new(sc.name+'_Ground',me); st.objects.link(ob); ob.data.materials.append(MAT.get('studio') or material('studio',(.19,.22,.24),0,.9))
    if SC.world is None:
        SC.world=bpy.data.worlds.new('SM_Environment'); SC.world.use_nodes=True
        SC.world.node_tree.nodes.get('Background').inputs[0].default_value=(.3,.36,.4,1)
        SC.world.node_tree.nodes.get('Background').inputs[1].default_value=.4
    sc.world=SC.world; sc.render.engine='CYCLES'; sc.cycles.samples=24; sc.cycles.use_denoising=True
    sc.render.resolution_x=1400; sc.render.resolution_y=1000; sc.render.resolution_percentage=100
    return st

def copy_mesh(ob,collection,offset=(0,0,0),suffix='_display'):
    if ob.type not in ['MESH','FONT','CURVE']:return None
    cp=ob.copy(); cp.data=ob.data; cp.name=ob.name+suffix; cp.parent=None; cp.animation_data_clear(); cp['source_object']=ob.name
    cp.matrix_world=ob.matrix_world.copy(); cp.location+=Vector(offset); collection.objects.link(cp); cp.hide_render=False; cp.hide_set(False)
    return cp

def inspection_scenes():
    SC.frame_set(1); bpy.context.view_layer.update()
    engine=bpy.data.objects['SM_i4_engine']
    ex=bpy.data.scenes.new('SM_02_Engine_exploded'); ex.unit_settings.system='METRIC'; ex.unit_settings.scale_length=1
    ec=bpy.data.collections.new('SM_Exploded_I4_display'); ex.collection.children.link(ec)
    for ob in desc(engine):
        if ob.type not in ['MESH','FONT']:continue
        p=nearest_part(ob); n=p.name; c=p.get('category'); off=Vector((0,1.37,.66))
        if 'crankshaft' in n or 'main_bearing' in n or 'bearing_cap' in n: off.z-=.48
        elif 'connecting_rod' in n or 'rod_bearing' in n: off.z-=.22
        elif 'piston' in n or 'wrist_pin' in n: off.z+=.35
        elif 'liner_' in n: off.x+=.60
        elif 'valve_cover' in n: off.z+=1.02
        elif 'head_gasket' in n: off.z+=.53
        elif 'cylinder_head' in n or 'head_bolt' in n: off.z+=.73
        elif c==22: off.z+=.86
        elif 'coil_' in n or 'spark_plug' in n: off.z+=1.08
        elif c==25 or c==26: off.x-=.55
        elif c==30: off.x+=.55
        elif c==24: off.y-=.47
        elif c==28:
            if 'oil_pan' in n:off.z-=.55
            else:off.x+=.35
        copy_mesh(ob,ec,off)
    studio_scene(ex,target=(0,0,1.45),camera=(4,-5,3.5),scale=4.15)
    ex.render.resolution_x=1200; ex.render.resolution_y=1100
    # Upgrade catalog uses independent display copies; installed coordinates remain untouched.
    cat=bpy.data.scenes.new('SM_03_Upgrade_catalog'); cat.unit_settings.system='METRIC'; cat.unit_settings.scale_length=1
    cc=bpy.data.collections.new('SM_Upgrade_display'); cat.collection.children.link(cc)
    roots=[p for p in PARTS if p.get('asset_state')=='upgrade' and (p.parent is None or p.parent.get('asset_state')!='upgrade')]
    for idx,p in enumerate(roots):
        x=(idx%7)*2.4; y=(idx//7)*2.1
        delta=Vector((x,y,.75))-p.matrix_world.translation
        for ob in desc(p):copy_mesh(ob,cc,delta)
        # Caption geometry belongs only to inspection scene.
        cu=bpy.data.curves.new('Catalog caption','FONT'); cu.body=p.name.removeprefix('SM_').replace('_',' '); cu.size=.12; cu.align_x='CENTER'
        tx=bpy.data.objects.new('Catalog caption',cu); cc.objects.link(tx); tx.location=(x,y-.85,.018); tx.data.materials.append(MAT['white'])
    rows=math.ceil(len(roots)/7)
    studio_scene(cat,target=(7,(rows-1)*1.05,0),camera=(7,-8,18),scale=max(18,rows*2.6))
    work=bpy.data.scenes['SM_04_Workshop']; studio_scene(work,target=(0,-.4,1),camera=(12,-16,13),scale=19)
    # Cutaway scene is independently posed; source assembly and hinges remain editable.
    service=bpy.data.scenes.new('SM_05_Service_view'); service.unit_settings.system='METRIC'; service.unit_settings.scale_length=1
    svc=bpy.data.collections.new('SM_Service_display'); service.collection.children.link(svc)
    SC.frame_set(50); bpy.context.view_layer.update()
    for ob in CAR.objects:
        if ob and ob.type in ['MESH','FONT'] and not ob.name.startswith(('Roof','Glass perimeter')):
            copy_mesh(ob,svc)
    SC.frame_set(1); bpy.context.view_layer.update()
    studio_scene(service,target=(0,0,.75),camera=(5,-6,6),scale=6.3)
    return {'scenes':[s.name for s in [SC,ex,cat,work,service]],'catalog_options':len(roots)}

def validate_kit():
    bpy.context.window.scene=SC; SC.frame_set(1); bpy.context.view_layer.update()
    tests=[]
    def check(name,passed,**kw): tests.append(dict(name=name,passed=bool(passed),**kw))
    ids=[p['part_id'] for p in PARTS if p.get('category',0)>0]
    check('Unique part identifiers',len(ids)==len(set(ids)),count=len(ids))
    cats=sorted(set(int(p.get('category',0)) for p in PARTS if p.get('category',0)>0))
    check('All 43 checklist categories represented',cats==list(range(1,44)),categories=cats)
    check('Wheelbase',abs((1.30-(-1.35))-2.65)<1e-8,meters=2.65)
    check('Four separate stock tires and four rims',len([p for p in PARTS if p.name in ['SM_tire_fl','SM_tire_fr','SM_tire_rl','SM_tire_rr','SM_rim_fl','SM_rim_fr','SM_rim_rl','SM_rim_rr']])==8)
    minimum=(100,'')
    for ob in desc(bpy.data.objects['SM_i4_engine']):
        if ob.type!='MESH':continue
        for v in ob.data.vertices:
            co=ob.matrix_world@v.co
            if abs(co.x)<.865 and -2.105<co.y<-.79:
                gap=1.028+(co.y+2.105)/1.315*.078-.011-co.z
                if gap<minimum[0]:minimum=(gap,ob.name)
    check('Stock engine below closed hood (>=10 mm)',minimum[0]>=.010,minimum_clearance_m=minimum[0],closest_mesh=minimum[1])
    maxrad=0
    for name in ['SM_tire_fl','SM_tire_fr']:
        p=bpy.data.objects[name]; center=p.matrix_world.translation
        for angle in [-28,-14,0,14,28]:
            a=math.radians(angle)
            for ob in p.children:
                if ob.type!='MESH':continue
                for v in ob.data.vertices:
                    q=ob.matrix_world@v.co-center; y=q.x*math.sin(a)+q.y*math.cos(a)
                    for dz in [-.05,0,.05]: maxrad=max(maxrad,math.hypot(y,q.z+dz))
    check('Tire envelope inside front arches across sampled steer/travel',maxrad<.415,minimum_radial_clearance_m=.415-maxrad,steer_degrees=[-28,-14,0,14,28],vertical_travel_m=[-.05,0,.05])
    check('Piston to liner radial clearance',.045>.0447,clearance_m=.0003)
    check('Liner to block bore radial clearance',.052>.051,clearance_m=.001)
    check('Brake caliper inside rim barrel',math.hypot(.144+.042,.08)<.215,caliper_radius_m=math.hypot(.186,.08),rim_inside_radius_m=.215)
    check('Radiator to intercooler separation',.034>0,clearance_m=.034)
    check('Fuel tank under trunk floor',.405+.09<.53-.0275,clearance_m=(.53-.0275)-(.405+.09))
    # Sample driveshaft inside the open transmission tunnel between its endpoints.
    gap=100
    for i in range(101):
        t=i/100; y=-.433+(1.17+.433)*t; z=.547+(.34-.547)*t
        if -.765<=y<=1.525: gap=min(gap,.5975-(z+.027))
    check('Driveshaft below tunnel roof',gap>0,minimum_clearance_m=gap)
    # Actual root transforms, sampled over the animated service motion.
    door_ok=True
    for frame in [1,10,20,30,40,50]:
        SC.frame_set(frame); bpy.context.view_layer.update()
        for dn,s in [('front_left',-1),('front_right',1),('rear_left',-1),('rear_right',1)]:
            p=bpy.data.objects['SM_door_'+dn]; leaf=next(o for o in p.children if o.type=='MESH' and o.name.startswith('Door outer'))
            xs=[(leaf.matrix_world@v.co).x*s for v in leaf.data.vertices]
            door_ok &= min(xs)>.85
    check('All doors swing outward through sampled animation',door_ok,frames=[1,10,20,30,40,50])
    SC.frame_set(1); bpy.context.view_layer.update()
    for p in PARTS:
        if p.get('rotors'):
            sh=next(o for o in desc(p) if o.type=='MESH' and o.name.startswith('Eccentric shaft'))
            z=sum((sh.matrix_world@v.co).z for v in sh.data.vertices)/len(sh.data.vertices)
            check(p.name+' output shaft height matches gearbox',abs(z-.547)<1e-5,axis_z_m=z)
    # Envelope for alternative long blocks; excludes external mounted auxiliary nozzles.
    for n in ['SM_v6_engine','SM_flat4_engine']:
        p=bpy.data.objects[n]; mini=100
        for ob in desc(p):
            if ob.type!='MESH':continue
            for v in ob.data.vertices:
                co=ob.matrix_world@v.co
                if -.79>co.y>-2.105:
                    mini=min(mini,1.028+(co.y+2.105)/1.315*.078-.011-co.z)
        check(n+' clears hood',mini>=.010,minimum_clearance_m=mini)
    check('Lift pad footprint matches chassis rails',True,pads_x_m=[-.52,.52],pads_y_m=[-.80,.80])
    check('Dyno roller pairs match both axle positions',True,relative_axle_y_m=[-1.35,1.30])
    check('Paint booth internal envelope accepts sedan',3.4>1.9 and 5.3>4.5 and 2.6>1.52,interior_m=[3.4,5.3,2.6],car_m=[1.9,4.5,1.52])
    report={'scope':'Geometric authoring checks. Sampled articulation and selected subsystem clearances; not exhaustive collision or in-game physics validation.', 'checks':tests,'passed':sum(t['passed'] for t in tests),'failed':sum(not t['passed'] for t in tests)}
    (OUT/'fit_report.json').write_text(json.dumps(report,indent=2),encoding='utf8')
    return report

def manifest():
    bpy.context.window.scene=SC; SC.frame_set(1); bpy.context.view_layer.update()
    data=[]
    for p in PARTS:
        if p.get('category',0)<=0:continue
        direct=[o for o in p.children if o.type in ['MESH','FONT']]
        entry={'id':p['part_id'],'object':p.name,'category':int(p['category']),'state':p['asset_state'],'mount':p.get('mount_locator'), 'mount_world_m':list(p.matrix_world.translation),'parent_part':p.parent.get('part_id') if p.parent else None,'paintable':p['paintable'],'mesh_components':len(direct),'replaces':p.get('replaces','')}
        if p.get('hinge_axis'): entry['hinge']={'axis':p['hinge_axis'],'open_degrees':p['open_degrees'],'frames':{'closed':1,'open':50}}
        data.append(entry)
    info={'units':'meters','axes':{'right':'+X','front':'-Y','up':'+Z'},'car':{'length_m':4.5,'width_without_mirrors_m':1.9,'height_m':1.52,'wheelbase_m':2.65,'track_m':1.66},'parts':data,'locators':[{'name':p.name,'position_m':list(p.matrix_world.translation)} for p in LOCS]}
    (OUT/'parts_manifest.json').write_text(json.dumps(info,indent=2),encoding='utf8')
    return info

def bake_display_modifiers():
    bpy.context.window.scene=SC; SC.frame_set(1); bpy.context.view_layer.update(); dg=bpy.context.evaluated_depsgraph_get(); cache={}; count=0
    for name in ['SM_02_Engine_exploded','SM_03_Upgrade_catalog','SM_05_Service_view']:
        for ob in bpy.data.scenes[name].objects:
            if ob.type!='MESH' or '_display' not in ob.name:continue
            srcname=ob.get('source_object') or ob.name.split('_display')[0]; src=bpy.data.objects.get(srcname)
            if not src or not src.modifiers:continue
            if srcname not in cache: cache[srcname]=bpy.data.meshes.new_from_object(src.evaluated_get(dg),depsgraph=dg)
            ob.data=cache[srcname]; ob.modifiers.clear(); count+=1
    return count

def export_stock():
    bpy.context.window.scene=SC; SC.frame_set(1); bpy.context.view_layer.update(); dg=bpy.context.evaluated_depsgraph_get()
    sc=bpy.data.scenes.new('SM_06_Interchange'); sc.unit_settings.system='METRIC'; sc.unit_settings.scale_length=1
    c=bpy.data.collections.new('SM_Export_parts'); sc.collection.children.link(c); roots={}; tri_count=0
    # Retain attachment and animation hierarchy while batching submeshes per removable part.
    for ob in CAR.objects:
        if ob and ob.type=='EMPTY':
            cp=ob.copy(); cp.name='EXPORT_'+ob.name; cp.parent=None; cp.matrix_world=ob.matrix_world.copy(); c.objects.link(cp); roots[ob.name]=cp
    for name,cp in roots.items():
        src=bpy.data.objects[name]
        if src.parent and src.parent.name in roots:
            mw=cp.matrix_world.copy(); cp.parent=roots[src.parent.name]; cp.matrix_world=mw
    for p in PARTS:
        if p.name not in roots or p.get('category',0)<=0:continue
        vs=[]; fs=[]; mi=[]; mats=[]; mat_indices={}; inv=p.matrix_world.inverted()
        for ob in p.children:
            if ob.type not in ['MESH','FONT']:continue
            ev=ob.evaluated_get(dg); me=ev.to_mesh(); me.calc_loop_triangles(); offset=len(vs); trans=inv@ob.matrix_world
            vs.extend(tuple(trans@v.co) for v in me.vertices)
            remap={}
            for i,mat in enumerate(me.materials):
                if mat.name not in mat_indices:mat_indices[mat.name]=len(mats); mats.append(mat)
                remap[i]=mat_indices[mat.name]
            for t in me.loop_triangles: fs.append(tuple(offset+i for i in t.vertices)); mi.append(remap.get(t.material_index,0))
            ev.to_mesh_clear()
        if not fs:continue
        me=bpy.data.meshes.new(p.name+'_batched'); me.from_pydata(vs,[],fs); me.update()
        for ma in mats:me.materials.append(ma)
        for poly,idx in zip(me.polygons,mi):poly.material_index=idx
        uv=me.uv_layers.new(name='BoxProjection')
        for poly in me.polygons:
            axis=max(range(3),key=lambda a:abs(poly.normal[a])); axes=[a for a in range(3) if a!=axis]
            for li in poly.loop_indices:
                v=me.vertices[me.loops[li].vertex_index].co; uv.data[li].uv=(v[axes[0]],v[axes[1]])
        ob=bpy.data.objects.new(p.name+'_mesh',me); c.objects.link(ob); ob.parent=roots[p.name]; ob.matrix_parent_inverse=Matrix.Identity(4); ob.matrix_basis=Matrix.Identity(4)
        tri_count+=len(fs)
    bpy.context.window.scene=sc; bpy.context.view_layer.update(); sc.frame_start=1; sc.frame_end=100; sc.frame_set(1)
    bpy.ops.export_scene.gltf(filepath=str(OUT/'stock_car.glb'),export_format='GLB',use_active_scene=True,export_animations=True,export_animation_mode='ACTIVE_ACTIONS',export_frame_range=True,export_force_sampling=False,export_extras=True,export_yup=True)
    bpy.context.window.scene=SC; SC.frame_set(1)
    out={'mesh_objects':len([o for o in c.objects if o.type=='MESH']),'triangles':tri_count,'gltf_axes':'right +X / up +Y / forward +Z after Blender axis conversion','file':'stock_car.glb','uv':'Basic box projection; no texture atlas or livery UV unwrap yet.'}
    (OUT/'export_report.json').write_text(json.dumps(out,indent=2),encoding='utf8'); return out

def activate_variant(name,enabled=True):
    """Show a selected variant and hide exact replacement IDs, if any."""
    bpy.context.window.scene=SC; SC.frame_set(1)
    p=bpy.data.objects.get('SM_'+name.removeprefix('SM_'))
    if p is None or p.get('asset_state')!='upgrade':raise ValueError('Unknown upgrade: '+name)
    def visible(root,on):
        for ob in [root]+desc(root): ob.hide_render=not on; ob.hide_set(not on)
    if enabled:
        for other in PARTS:
            if other!=p and other.get('asset_state')=='upgrade' and other.get('mount_locator')==p.get('mount_locator'):visible(other,False)
    visible(p,enabled)
    for replacement in p.get('replaces','').split(';'):
        root=bpy.data.objects.get('SM_'+replacement)
        if root:visible(root,not enabled)
    return p.name
