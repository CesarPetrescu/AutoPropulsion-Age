"""Spark Motors: reusable low-poly authoring kit. Run inside Blender through MCP.
Coordinates: X right, -Y front, Z up; meters. Original scenes are preserved.
"""
import bpy, bmesh, math, json, os
from mathutils import Vector, Matrix
from pathlib import Path

OUT=Path(os.environ.get('SPARKMOTORS_OUTPUT_DIR', str(Path(__file__).resolve().parent.parent)))
OUT.mkdir(parents=True, exist_ok=True)
if 'SM_01_Assembled' in bpy.data.scenes:
    raise RuntimeError('Rebuild in a new Blender file to keep existing kit data intact.')
SC=bpy.data.scenes.new('SM_01_Assembled')
bpy.context.window.scene=SC
SC.unit_settings.system='METRIC'; SC.unit_settings.scale_length=1
SC['authoring_axes']='X right / -Y forward / Z up; 1 unit = 1 meter'
SC['asset_status']='Modular low-poly authoring kit; not a Minecraft runtime export'
def col(name,scene=SC):
    c=bpy.data.collections.new(name); scene.collection.children.link(c); return c
CAR=col('SM_Stock_car'); ALT=col('SM_Optional_upgrades'); CUT=col('SM_Construction')
PARTS=[]; LOCS=[]; CHECKS=[]; MAT={}
def material(name,rgb,metal=0,rough=.4):
    m=bpy.data.materials.new('SM_'+name); m.diffuse_color=(*rgb,1); m.use_nodes=True
    bs=m.node_tree.nodes.get('Principled BSDF'); bs.inputs['Base Color'].default_value=(*rgb,1)
    bs.inputs['Metallic'].default_value=metal; bs.inputs['Roughness'].default_value=rough
    MAT[name]=m; return m
material('paint',(.025,.27,.31),.5,.27); material('alloy',(.43,.5,.54),.75,.3)
material('steel',(.12,.16,.19),.7,.38); material('black',(.018,.022,.027),.1,.52)
material('rubber',(.018,.022,.025),0,.85); material('cloth',(.048,.063,.075),0,.9)
material('glass',(.032,.105,.15),.3,.17); material('red',(.62,.025,.019),.2,.3)
material('amber',(1,.26,.025),.1,.3); material('white',(.85,.93,.92),.15,.25)
material('blue',(.03,.2,.55),.2,.35); material('gold',(.58,.36,.075),.65,.35)
material('screen',(.04,.5,.63),.2,.25); material('gasket',(.12,.1,.075),.3,.8)
bs=MAT['glass'].node_tree.nodes.get('Principled BSDF'); bs.inputs['Transmission Weight'].default_value=.32
for n in ['white','red','amber','screen']:
    bs=MAT[n].node_tree.nodes.get('Principled BSDF'); bs.inputs['Emission Color'].default_value=MAT[n].diffuse_color; bs.inputs['Emission Strength'].default_value=.18

def parent_world(o,p):
    bpy.context.view_layer.update(); mw=o.matrix_world.copy(); o.parent=p; o.matrix_world=mw
def empty(name,pos=(0,0,0),collection=CAR,parent=None):
    o=bpy.data.objects.new(name,None); collection.objects.link(o); o.location=pos; o.empty_display_size=.07; o.empty_display_type='PLAIN_AXES'
    if parent: parent_world(o,parent)
    return o
ROOT=empty('SM_vehicle_root'); ROOT['dimensions_m']=[1.9,4.5,1.52]; ROOT['wheelbase_m']=2.65
def part(name,category,pos=(0,0,0),parent=ROOT,collection=CAR,slot=None):
    p=empty('SM_'+name,pos,collection,parent)
    p['part_id']='sparkmotors:'+name; p['category']=category; p['mount_locator']=slot or name
    p['asset_state']='upgrade' if collection==ALT else 'stock'; p['paintable']=False
    p['mount_world_m']=list(pos); p['axis_forward']='-Y'; p['unit']='meter'
    PARTS.append(p); return p
def locator(name,pos,parent=ROOT):
    p=empty('locator:'+name,pos,CAR,parent); p['locator']=name; LOCS.append(p); return p
def mesh(name,verts,faces,p,mat='alloy',bevel=0):
    me=bpy.data.meshes.new(name); me.from_pydata(verts,[],faces); me.update()
    bm=bmesh.new(); bm.from_mesh(me); bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces)); bm.to_mesh(me); bm.free()
    o=bpy.data.objects.new(name,me); (ALT if p and p.get('asset_state')=='upgrade' else CAR).objects.link(o)
    if mat: o.data.materials.append(MAT[mat])
    if p: parent_world(o,p)
    if mat=='paint' and p: p['paintable']=True
    if bevel:
        b=o.modifiers.new('Small edge bevel','BEVEL'); b.width=bevel; b.segments=2
        o.modifiers.new('Weighted face normals','WEIGHTED_NORMAL')
    return o
def box(name,pos,dim,p,mat='alloy',bevel=.006):
    x,y,z=pos; a,b,c=[v/2 for v in dim]
    verts=[(x+i*a,y+j*b,z+k*c) for i,j,k in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
    return mesh(name,verts,[(0,3,2,1),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)],p,mat,bevel)
def cylinder(name,pos,r,depth,p,mat='alloy',axis='Z',n=20,inner=0):
    cen=Vector(pos)
    def xyz(a,t,rad):
        v=Vector((rad*math.cos(a),rad*math.sin(a),t))
        if axis=='X': v=Vector((t,v.x,v.y))
        if axis=='Y': v=Vector((v.x,t,v.y))
        return tuple(cen+v)
    vs=[]
    for rad in ([r,inner] if inner else [r]):
        for z in [-depth/2,depth/2]:
            vs += [xyz(i*math.tau/n,z,rad) for i in range(n)]
    fs=[]
    for i in range(n):
        j=(i+1)%n; fs.append((i,j,j+n,i+n))
        if inner:
            fs.extend([(2*n+i,3*n+i,3*n+j,2*n+j),(i,2*n+i,2*n+j,j),(i+n,j+n,3*n+j,3*n+i)])
    if not inner: fs += [tuple(reversed(range(n))),tuple(range(n,2*n))]
    return mesh(name,vs,fs,p,mat)
def beam(name,a,b,r,p,mat='steel',n=10):
    a,b=Vector(a),Vector(b); d=b-a; q=d.to_track_quat('Z','Y'); vs=[]
    for c in [a,b]:
        vs += [tuple(c+q@Vector((r*math.cos(i*math.tau/n),r*math.sin(i*math.tau/n),0))) for i in range(n)]
    fs=[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]+[tuple(reversed(range(n))),tuple(range(n,2*n))]
    return mesh(name,vs,fs,p,mat)
def pipe(name,points,r,p,mat='black'):
    for i in range(len(points)-1): beam(name+'_'+str(i),points[i],points[i+1],r,p,mat)
def panel(name,points,p,mat='paint',th=.025):
    o=mesh(name,points,[tuple(range(len(points)))],p,mat)
    s=o.modifiers.new('Panel thickness','SOLIDIFY'); s.thickness=th; s.offset=0
    b=o.modifiers.new('Panel edge','BEVEL'); b.width=.004; b.segments=2
    return o
def label(name,txt,pos,size,p,rotation=(0,0,0),mat='white'):
    cu=bpy.data.curves.new(name,'FONT'); cu.body=txt; cu.size=size; cu.align_x='CENTER'; cu.extrude=.0004
    ob=bpy.data.objects.new(name,cu); (ALT if p and p.get('asset_state')=='upgrade' else CAR).objects.link(ob)
    ob.location=pos; ob.rotation_euler=rotation; ob.data.materials.append(MAT[mat]); parent_world(ob,p); return ob
def hinge(p,axis,angle):
    p['hinge_axis']=axis; p['open_degrees']=angle; p.rotation_mode='XYZ'
    p.keyframe_insert(data_path='rotation_euler',frame=1)
    p.rotation_euler['XYZ'.index(axis)]=math.radians(angle); p.keyframe_insert(data_path='rotation_euler',frame=50)
    p.rotation_euler['XYZ'.index(axis)]=0; p.keyframe_insert(data_path='rotation_euler',frame=100)
    SC.frame_set(1)
def hole(target,pos,r,depth,axis):
    p=part('construction_cutter_'+str(len(CUT.objects)),0,pos,collection=CUT)
    o=cylinder('cutter',pos,r,depth,p,axis=axis,n=40)
    for c in list(o.users_collection): c.objects.unlink(o)
    CUT.objects.link(o)
    o.hide_render=True; o.hide_set(True); p.hide_render=True; p.hide_set(True)
    mod=target.modifiers.new('Machined opening','BOOLEAN'); mod.operation='DIFFERENCE'; mod.solver='EXACT'; mod.object=o
    return o

def chassis_body():
    p=part('chassis_shell',1)
    box('Passenger floor',(0,.38,.39),(1.63,2.29,.075),p,'steel')
    box('Trunk floor',(0,1.72,.53),(1.58,.79,.055),p,'steel')
    box('Firewall',(0,-.76,.72),(1.60,.048,.58),p,'steel')
    box('Rear bulkhead',(0,1.27,.79),(1.6,.04,.47),p,'steel')
    for x in [-.52,.52]:
        box('Front rail',(x,-1.46,.39),(.08,1.38,.12),p,'steel')
        box('Rear rail',(x,1.67,.43),(.08,1.10,.13),p,'steel')
    for s in [-1,1]:
        x=s*.89
        box('Rocker',(x,.25,.43),(.09,2.07,.15),p,'paint')
        # Thin pillars keep an actual empty cabin rather than a solid cabin cube.
        beam('A pillar',(s*.84,-.72,1.05),(s*.70,-.24,1.47),.038,p,'paint')
        beam('B pillar',(s*.89,.26,.52),(s*.74,.26,1.49),.027,p,'paint')
        beam('C pillar',(s*.86,1.32,1.05),(s*.73,.92,1.47),.045,p,'paint')
        beam('Roof rail',(s*.70,-.24,1.47),(s*.73,.92,1.47),.034,p,'paint')
    box('Roof',(0,.35,1.49),(1.44,1.19,.042),p,'paint',.018)
    # Front and rear wings are separate assets with real wheel openings.
    for s in [-1,1]:
        for ax,y,ymin,ymax in [('front',-1.35,-2.16,-.77),('rear',1.30,1.025,2.16)]:
            f=part(f'{ax}_fender_{s}',3,(s*.9,y,.7))
            ob=box('Fender skin',(s*.91,(ymin+ymax)/2,.74),(.065,ymax-ymin,.62),f,'paint')
            hole(ob,(s*.91,y,.34),.415,.3,'X')
            # Accent follows the upper half of the opening.
            pts=[(s*.948,y+math.cos(a)*.421,.34+math.sin(a)*.421) for a in [i*math.pi/20 for i in range(21)]]
            pipe('Wheel arch trim',pts,.012,f,'black')
        skirt=part(f'side_skirt_{s}',3,(s*.935,.18,.405))
        box('Side skirt',(s*.935,.18,.405),(.07,1.77,.11),skirt,'black')
        for front,y0,y1 in [(True,-.75,.245),(False,.275,1.015)]:
            dn=('front' if front else 'rear')+('_left' if s<0 else '_right')
            d=part('door_'+dn,2,(s*.896,y0,.78),slot='door_'+dn+'_hinge')
            box('Door outer',(s*.903,(y0+y1)/2,.772),(.037,y1-y0,.53),d,'paint')
            glass=part('window_'+dn,4,(s*.82,(y0+y1)/2,1.22),parent=d)
            if front: points=[(s*.88,y0+.026,1.046),(s*.88,y1-.02,1.046),(s*.735,y1-.02,1.457),(s*.71,-.224,1.457)]
            else: points=[(s*.88,y0+.025,1.046),(s*.872,y1-.016,1.046),(s*.734,.91,1.455),(s*.738,y0+.025,1.457)]
            panel('Door glass',points,glass,'glass',.006)
            for a,b in zip(points,points[1:]+points[:1]): beam('Window seal',a,b,.013,d,'black')
            trim=part('door_card_'+dn,11,(s*.865,(y0+y1)/2,.765),parent=d)
            box('Door card',(s*.867,(y0+y1)/2,.765),(.03,y1-y0-.06,.43),trim,'cloth')
            box('Armrest',(s*.823,(y0+y1)/2,.81),(.075,.38,.07),trim,'black')
            handle=part('handle_'+dn,5,(s*.932,y1-.16,.95),parent=d)
            box('Handle',(s*.932,y1-.16,.95),(.028,.14,.031),handle,'alloy')
            hinge(d,'Z',s*62)
    hood=part('hood',2,(0,-.78,1.092),slot='hood_hinge')
    panel('Hood skin',[(-.865,-2.105,1.028),(.865,-2.105,1.028),(.865,-.79,1.106),(-.865,-.79,1.106)],hood,th=.022)
    for x in [-.56,.56]: beam('Hood inner rib',(x,-1.99,1.01),(x,-.87,1.075),.012,hood,'steel')
    hinge(hood,'X',-64)
    trunk=part('trunk_lid',2,(0,1.35,1.047),slot='trunk_hinge')
    panel('Trunk skin',[(-.864,1.355,1.05),(.864,1.355,1.05),(.864,2.105,1.027),(-.864,2.105,1.027)],trunk,th=.025)
    hinge(trunk,'X',64)
    w=part('windshield',4,(0,-.5,1.28))
    panel('Windshield',[(-.818,-.745,1.079),(.818,-.745,1.079),(.683,-.25,1.457),(-.683,-.25,1.457)],w,'glass',.008)
    w=part('rear_glass',4,(0,1.13,1.28))
    panel('Rear glass',[(-.70,.946,1.461),(.70,.946,1.461),(.83,1.331,1.068),(-.83,1.331,1.068)],w,'glass',.008)
    for front,y in [(True,-2.17),(False,2.17)]:
        tag='front' if front else 'rear'; b=part(tag+'_bumper',3,(0,y,.70))
        box('Bumper',(0,y,.655),(1.88,.16,.37),b,'paint',.025)
        box('Impact strip',(0,y+(-.086 if front else .086),.57),(1.75,.035,.09),b,'black')
        for s in [-1,1]:
            lp=part(tag+'_light_'+str(s),6,(s*.63,y,.90))
            box('Lamp',(s*.63,y,.914),(.46,.13,.16),lp,'white' if front else 'red',.018)
            box('Indicator',(s*.829,y+(-.071 if front else .071),.912),(.062,.015,.14),lp,'amber',.008)
        if front:
            g=part('grille',5,(0,-2.214,.902))
            box('Grille backing',(0,-2.214,.902),(.55,.03,.16),g,'black')
            for z in [.849,.886,.923,.959]: box('Grille bar',(0,-2.234,z),(.49,.012,.009),g,'alloy',.002)
        else: box('License plate',(0,2.257,.81),(.40,.02,.12),b,'white')
    for s in [-1,1]:
        d=next(p for p in PARTS if p.name=='SM_door_front_'+('left' if s<0 else 'right'))
        m=part('mirror_'+str(s),5,(s*.89,-.62,1.085),parent=d)
        beam('Mirror stalk',(s*.89,-.62,1.085),(s*1.005,-.61,1.10),.016,m)
        box('Mirror housing',(s*1.045,-.61,1.10),(.13,.19,.092),m,'paint',.02)
        box('Mirror face',(s*1.045,-.51,1.10),(.10,.012,.06),m,'glass')
    for name,pos in [('engine_mount',(0,-1.37,.66)),('transmission_mount',(0,-.70,.56)),('fuel_tank',(0,1.65,.43)),('radiator',(0,-1.96,.78)),('battery',(-.50,-1.62,.78)),('dash',(0,-.52,.91))]: locator(name,pos)

def wheels_suspension():
    for s in [-1,1]:
        for front,y in [(True,-1.35),(False,1.30)]:
            tag=('f' if front else 'r')+('l' if s<0 else 'r'); x=s*.83; c=(x,y,.34)
            mount=locator('wheel_'+tag,c); mount['suspension_travel_m']=.10
            steer=empty('SM_steer_'+tag,c,CAR,mount); steer['max_steer_degrees']=28 if front else 0
            spin=empty('SM_spin_'+tag,c,CAR,steer); spin['spin_axis']='X'
            t=part('tire_'+tag,13,c,parent=spin)
            # A rounded tire cross-section revolved around X.
            profile=[(-.108,.235),(-.112,.292),(-.080,.332),(.080,.332),(.112,.292),(.108,.235)]
            vs=[]; n=32
            for dx,r in profile:
                vs += [(x+dx,y+r*math.cos(a*math.tau/n),.34+r*math.sin(a*math.tau/n)) for a in range(n)]
            fs=[]
            for k in range(len(profile)):
                for a in range(n): fs.append((k*n+a,k*n+(a+1)%n,((k+1)%len(profile))*n+(a+1)%n,((k+1)%len(profile))*n+a))
            mesh('Tire carcass',vs,fs,t,'rubber')
            for i in range(28):
                a=i*math.tau/28
                beam('Tread rib',(x-.072,y+.333*math.cos(a),.34+.333*math.sin(a)),(x+.072,y+.333*math.cos(a+.025),.34+.333*math.sin(a+.025)),.0035,t,'black',6)
            rim=part('rim_'+tag,13,c,parent=spin)
            cylinder('Rim barrel',c,.236,.20,rim,'alloy','X',32,.215)
            cylinder('Rim lip',(x+s*.103,y,.34),.239,.018,rim,'alloy','X',32,.22)
            for i in range(5):
                a=i*math.tau/5; beam('Alloy spoke',(x+s*.103,y+.05*math.cos(a),.34+.05*math.sin(a)),(x+s*.103,y+.217*math.cos(a),.34+.217*math.sin(a)),.020,rim,'alloy',6)
            cylinder('Center cap',(x+s*.118,y,.34),.046,.014,rim,'alloy','X')
            hub=part('hub_'+tag,14,(s*.765,y,.34),parent=steer)
            cylinder('Wheel hub',(s*.765,y,.34),.065,.09,hub,'steel','X')
            disc=part('brake_disc_'+tag,15,(s*.783,y,.34),parent=steer)
            cylinder('Vented rotor',(s*.783,y,.34),.173,.022,disc,'alloy','X',32,.047)
            cal=part('brake_caliper_'+tag,15,(s*.793,y+.144,.34),parent=steer)
            box('Caliper',(s*.793,y+.144,.34),(.10,.084,.16),cal,'red',.013)
            for side in [-1,1]:
                pad=part('brake_pad_'+tag+'_'+str(side),15,(s*.783+side*.018,y+.137,.34),parent=cal)
                box('Friction pad',tuple(pad.location),(.008,.06,.115),pad,'black') if False else box('Friction pad',(s*.783+side*.018,y+.137,.34),(.008,.06,.115),pad,'black')
            kn=part('knuckle_'+tag,14,(s*.70,y,.36),parent=steer)
            box('Steering upright',(s*.70,y,.36),(.055,.07,.21),kn,'steel')
            sus=part('coilover_'+tag,16,(s*.66,y,.61))
            beam('Damper',(s*.66,y,.36),(s*.61,y,.94),.022,sus,'alloy')
            points=[(s*.632+.053*math.cos(a*math.tau/12),y+.053*math.sin(a*math.tau/12),.55+a/72*.32) for a in range(73)]
            pipe('Spring coil',points,.008,sus,'red')
            arm=part('control_arm_'+tag,16,(s*.53,y,.30))
            for dy in [-.17,.17]: beam('Wishbone',(s*.37,y+dy,.30),(s*.70,y,.30),.018,arm,'steel')
    for y in [-1.35,1.30]:
        p=part('antiroll_'+str(y),16,(0,y,.31))
        pipe('Antiroll bar',[(-.66,y,.34),(-.49,y+.2,.30),(.49,y+.2,.30),(.66,y,.34)],.012,p,'steel')
    p=part('steering_rack',17,(0,-1.13,.38)); cylinder('Rack casing',(0,-1.13,.38),.035,1.06,p,'steel','X')
    for s in [-1,1]: beam('Tie rod',(s*.53,-1.13,.38),(s*.71,-1.28,.36),.012,p)
    pipe('Steering column',[(-.52,-1.10,.39),(-.52,-.76,.62),(-.49,-.39,.98)],.018,p,'steel')

def interior():
    for s in [-1,1]:
        seat=part('front_seat_'+str(s),8,(s*.45,.10,.44))
        box('Seat cushion',(s*.45,.04,.575),(.48,.48,.17),seat,'cloth',.04)
        box('Seat back',(s*.45,.30,.875),(.47,.115,.51),seat,'cloth',.035)
        box('Head restraint',(s*.45,.31,1.21),(.26,.105,.16),seat,'cloth',.02)
        for x in [s*.45-.15,s*.45+.15]: beam('Seat rail',(x,-.15,.44),(x,.32,.44),.012,seat)
        locator('seat_'+str(s),(s*.45,.11,.71),seat)
    rear=part('rear_bench',8,(0,.91,.56))
    box('Rear cushion',(0,.91,.605),(1.4,.47,.15),rear,'cloth',.03)
    box('Rear backrest',(0,1.16,.89),(1.4,.115,.46),rear,'cloth',.03)
    dash=part('dashboard',9,(0,-.55,.93))
    box('Dash',(0,-.57,.951),(1.55,.23,.21),dash,'black',.023)
    for x in [-.65,-.25,.30,.63]:
        box('Vent',(x,-.442,.988),(.13,.018,.05),dash,'steel')
        for xx in [-.038,0,.038]: box('Vent vane',(x+xx,-.428,.988),(.006,.008,.041),dash,'black',0)
    gauge=part('instrument_cluster',9,(-.47,-.437,1.013),parent=dash)
    box('Instrument hood',(-.47,-.447,1.045),(.43,.09,.14),gauge,'black')
    for x in [-.57,-.38]:
        cylinder('Dial',(x,-.393,1.048),.055,.008,gauge,'steel','Y',24)
        for i in range(9):
            a=(i/8*1.5+.75)*math.pi
            beam('Tick',(x+.043*math.cos(a),-.386,1.048+.043*math.sin(a)),(x+.050*math.cos(a),-.386,1.048+.050*math.sin(a)),.0015,gauge,'white',4)
        needle=part('needle_'+str(x),9,(x,-.379,1.048),parent=gauge)
        beam('Gauge needle',(x,-.379,1.048),(x-.033,-.379,1.067),.002,needle,'red',4)
    con=part('console',11,(0,.0,.62))
    box('Center console',(0,.12,.575),(.21,.86,.25),con,'black',.018)
    sw=part('steering_wheel',10,(-.48,-.30,.998))
    cylinder('Steering rim',(-.48,-.30,.998),.154,.021,sw,'rubber','Y',32,.135)
    for a in [math.pi/6,5*math.pi/6,1.5*math.pi]: beam('Steering spoke',(-.48,-.30,.998),(-.48+.137*math.cos(a),-.30,.998+.137*math.sin(a)),.012,sw,'steel')
    cylinder('Horn pad',(-.48,-.289,.998),.042,.028,sw,'black','Y')
    sh=part('shifter',10,(0,-.20,.65)); beam('Shifter stem',(0,-.20,.65),(0,-.20,.80),.012,sh)
    cylinder('Shift knob',(0,-.20,.805),.025,.047,sh,'black')
    hb=part('handbrake',10,(.10,.27,.67)); beam('Handbrake lever',(.10,.36,.65),(.10,.13,.72),.014,hb,'black')
    for x,n in [(-.64,'clutch'),(-.50,'brake'),(-.36,'throttle')]:
        ped=part(n+'_pedal',10,(x,-.66,.5)); beam('Pedal arm',(x,-.69,.65),(x,-.58,.46),.008,ped)
        box('Pedal pad',(x,-.568,.46),(.065,.018,.08),ped,'rubber')
    for s in [-1,1]:
        floor=part('floor_mat_'+str(s),11,(s*.44,-.24,.433)); box('Floor mat',(s*.44,-.24,.433),(.52,.68,.011),floor,'rubber',.005)

def engine():
    e=part('i4_engine',18,(0,-1.37,.67),slot='engine_mount'); e['family']='inline4'; e['cylinders']=4
    block=part('i4_block',18,(0,-1.37,.67),parent=e)
    ob=box('Cylinder block',(0,-1.37,.67),(.35,.62,.28),block,'alloy',.012)
    ys=[-1.5575,-1.4325,-1.3075,-1.1825]
    for y in ys: hole(ob,(0,y,.715),.052,.22,'Z')
    crank=part('crankshaft_i4',20,(0,-1.37,.547),parent=e)
    cylinder('Crank main shaft',(0,-1.37,.547),.023,.66,crank,'steel','Y')
    for i,y in enumerate(ys):
        z=.547+(.043 if i in [0,3] else -.043)
        cylinder('Counterweight',(0,y-.022,.547),.052,.025,crank,'steel','Y')
        cylinder('Crank pin',(0,y,z),.019,.04,crank,'alloy','Y')
        liner=part('liner_'+str(i),20,(0,y,.72),parent=block)
        cylinder('Cylinder liner',(0,y,.72),.051,.18,liner,'steel','Z',24,.045)
        piston_z=.743 if i in [0,3] else .657
        pi=part('piston_'+str(i),20,(0,y,piston_z),parent=e)
        cylinder('Piston',(0,y,piston_z),.0435,.058,pi,'alloy','Z',24)
        pin=part('wrist_pin_'+str(i),20,(0,y,piston_z-.012),parent=pi)
        cylinder('Wrist pin',(0,y,piston_z-.012),.01,.074,pin,'steel','Y',16,.004)
        for j in range(3):
            ring=part(f'piston_ring_{i}_{j}',21,(0,y,piston_z+.015-j*.008),parent=pi)
            cylinder('Piston ring',(0,y,piston_z+.015-j*.008),.0447,.002,ring,'steel','Z',24,.042)
        rod=part('connecting_rod_'+str(i),20,(0,y,(z+piston_z-.012)/2),parent=e)
        beam('Rod shank',(0,y,z),(0,y,piston_z-.012),.009,rod,'steel',6)
        for zz,rr in [(z,.026),(piston_z-.012,.016)]: cylinder('Rod eye',(0,y,zz),rr,.020,rod,'steel','Y',16,rr-.008)
        bear=part('rod_bearing_'+str(i),21,(0,y,z),parent=rod); cylinder('Rod bearing',(0,y,z),.020,.018,bear,'gold','Y',20,.0185)
        noz=part('oil_nozzle_'+str(i),21,(.075,y,.57),parent=e); pipe('Piston jet',[(.075,y,.57),(.055,y,.60),(.034,y,.61)],.003,noz,'gold')
    for i,y in enumerate([-1.64,-1.50,-1.37,-1.245,-1.095]):
        p=part('main_bearing_'+str(i),21,(0,y,.547),parent=crank); cylinder('Main bearing',(0,y,.547),.028,.018,p,'gold','Y',20,.0235)
        cap=part('bearing_cap_'+str(i),21,(0,y,.505),parent=block); box('Main cap',(0,y,.505),(.094,.024,.026),cap,'steel')
        for x in [-.035,.035]: cylinder('Cap bolt',(x,y,.492),.005,.025,cap,'steel','Z',6)
    head=part('cylinder_head_i4',19,(0,-1.37,.89),parent=e)
    box('Head casting',(0,-1.37,.88),(.39,.65,.136),head,'alloy',.01)
    gasket=part('head_gasket',23,(0,-1.37,.811),parent=e)
    gg=box('Head gasket',(0,-1.37,.811),(.383,.637,.003),gasket,'gasket',0)
    for y in ys: hole(gg,(0,y,.811),.046,.014,'Z')
    for s in [-1,1]:
        cam=part('camshaft_'+str(s),22,(s*.095,-1.37,.929),parent=head)
        cylinder('Cam shaft',(s*.095,-1.37,.929),.012,.64,cam,'steel','Y')
        for y in ys:
            cylinder('Cam lobe',(s*.095,y,.934),.021,.018,cam,'steel','Y',16)
            for dy in [-.027,.027]:
                v=part(f'valve_{s}_{y}_{dy}',22,(s*.092,y+dy,.865),parent=head)
                cylinder('Valve head',(s*.092,y+dy,.833),.017,.005,v,'steel')
                cylinder('Valve stem',(s*.092,y+dy,.869),.0035,.070,v,'steel',n=10)
                spr=part('valve_spring_'+str(len(PARTS)),22,(s*.092,y+dy,.899),parent=v)
                cylinder('Spring envelope',(s*.092,y+dy,.899),.009,.029,spr,'steel',inner=.006)
                seal=part('valve_seal_'+str(len(PARTS)),22,(s*.092,y+dy,.876),parent=v); cylinder('Valve seal',(s*.092,y+dy,.876),.006,.006,seal,'rubber',inner=.0035)
                lif=part('lifter_'+str(len(PARTS)),22,(s*.092,y+dy,.918),parent=v); cylinder('Bucket lifter',(s*.092,y+dy,.918),.010,.008,lif,'alloy')
    vc=part('valve_cover',19,(0,-1.37,.987),parent=e)
    box('Valve cover',(0,-1.37,.987),(.34,.626,.07),vc,'red',.016)
    label('Engine badge','SPARK 16V',(0,-1.37,1.024),.043,vc)
    for y in ys:
        ig=part('coil_'+str(y),27,(0,y,1.028),parent=e); box('Coil pack',(0,y,1.028),(.055,.062,.026),ig,'black')
        sp=part('spark_plug_'+str(y),27,(0,y,.956),parent=ig); cylinder('Spark plug',(0,y,.956),.007,.07,sp,'white',n=12)
    timing=part('timing_drive',24,(0,-1.712,.76),parent=e)
    for x,z,r in [(0,.547,.039),(-.095,.929,.049),(.095,.929,.049)]:
        cylinder('Timing sprocket',(x,-1.712,z),r,.018,timing,'steel','Y',24)
    pipe('Timing belt',[(-.039,-1.712,.54),(-.142,-1.712,.929),(.142,-1.712,.929),(.039,-1.712,.54),(-.039,-1.712,.54)],.008,timing,'rubber')
    damp=part('harmonic_damper',24,(0,-1.74,.547),parent=e); cylinder('Crank damper',(0,-1.74,.547),.068,.029,damp,'black','Y')
    tc=part('timing_cover',24,(0,-1.739,.75),parent=e); box('Timing guard',(0,-1.739,.75),(.31,.025,.47),tc,'black')
    oil=part('oil_pan',28,(0,-1.36,.45),parent=e); box('Oil sump',(0,-1.36,.45),(.31,.55,.145),oil,'steel',.016)
    pu=part('oil_pump',28,(0,-1.60,.52),parent=e); box('Oil pump',(0,-1.60,.52),(.10,.11,.065),pu,'alloy')
    of=part('oil_filter',28,(.216,-1.54,.61),parent=e); cylinder('Oil filter',(.216,-1.54,.61),.036,.085,of,'white','X')
    vent=part('crankcase_vent',28,(.15,-1.12,.98),parent=e); pipe('PCV hose',[(.15,-1.12,.98),(.25,-1.10,.98),(.30,-1.15,.87)],.012,vent,'rubber')
    intake=part('intake_manifold',25,(-.30,-1.37,.87),parent=e)
    box('Intake plenum',(-.32,-1.37,.885),(.15,.52,.13),intake,'alloy',.021)
    for y in ys: pipe('Intake runner',[(-.18,y,.877),(-.23,y,.91),(-.30,y,.91)],.022,intake,'alloy')
    throttle=part('throttle_body',25,(-.32,-1.69,.886),parent=e); cylinder('Throttle body',(-.32,-1.69,.886),.035,.085,throttle,'alloy','Y')
    af=part('air_filter_airbox',25,(-.50,-1.32,.81),parent=e)
    box('Airbox',(-.50,-1.32,.81),(.24,.30,.20),af,'black',.013)
    pipe('Intake hose',[(-.50,-1.32,.91),(-.50,-1.73,.91),(-.32,-1.74,.886)],.031,af,'rubber')
    rail=part('fuel_rail',26,(-.213,-1.37,.928),parent=e); cylinder('Fuel rail',(-.213,-1.37,.928),.009,.53,rail,'gold','Y')
    for y in ys:
        inj=part('injector_'+str(y),26,(-.213,y,.90),parent=rail); cylinder('Injector',(-.213,y,.90),.007,.046,inj,'blue')
    header=part('exhaust_header',30,(.28,-1.37,.76),parent=e)
    for y in ys: pipe('Header runner',[(.195,y,.861),(.30,y,.84),(.35,y,.66),(.34,-1.06,.55)],.021,header,'steel')
    for name,pos,r,dep in [('alternator',(.25,-1.63,.65),.067,.12),('starter',(.24,-1.09,.55),.045,.16)]:
        pp=part(name,27,pos,parent=e); cylinder(name,pos,r,dep,pp,'alloy','Y')
        cylinder(name+' pulley',(pos[0],pos[1]-.075,pos[2]),r*.6,.012,pp,'black','Y')
    wp=part('water_pump',29,(-.15,-1.65,.63),parent=e); cylinder('Pump body',(-.15,-1.65,.63),.041,.10,wp,'alloy','Y')
    therm=part('thermostat',29,(-.18,-1.1,.93),parent=e); box('Thermostat housing',(-.18,-1.1,.93),(.08,.07,.055),therm,'alloy')
    for s in [-1,1]:
        mt=part('engine_mount_'+str(s),18,(s*.24,-1.39,.54),parent=e); box('Rubber mount',(s*.24,-1.39,.54),(.10,.10,.08),mt,'rubber')
    e['max_envelope_m']=[1.15,.90,.69]

def driveline_services():
    fly=part('flywheel',35,(0,-1.032,.547)); cylinder('Flywheel',(0,-1.032,.547),.122,.022,fly,'steel','Y',32)
    cl=part('clutch_disc',35,(0,-1.010,.547)); cylinder('Clutch friction',(0,-1.010,.547),.108,.009,cl,'black','Y',32,.027)
    pp=part('pressure_plate',35,(0,-.983,.547)); cylinder('Clutch pressure plate',(0,-.983,.547),.117,.038,pp,'alloy','Y',32,.04)
    gear=part('manual_5speed',36,(0,-.69,.547),slot='transmission_mount')
    cylinder('Bellhousing',(0,-.914,.547),.143,.12,gear,'alloy','Y',20,.127)
    box('Gearbox casing',(0,-.648,.547),(.23,.40,.23),gear,'alloy',.018)
    for x in [-.13,.13]:
        for y in [-.8,-.7,-.6,-.5]: box('Gearbox rib',(x,y,.547),(.013,.025,.21),gear,'alloy')
    shaft=part('gearbox_shafts',36,(0,-.648,.547),parent=gear)
    for x in [-.055,.055]: cylinder('Shaft',(x,-.648,.547),.012,.40,shaft,'steel','Y')
    for i in range(5):
        gs=part('gear_set_'+str(i),36,(0,-.78+i*.065,.547),parent=gear)
        for x,r in [(-.055,.050-i*.004),(.055,.030+i*.004)]: cylinder('Gear',(x,-.78+i*.065,.547),r,.02,gs,'steel','Y',18,.012)
    diff=part('rear_differential',37,(0,1.30,.34)); box('Differential case',(0,1.30,.34),(.25,.26,.20),diff,'steel',.03)
    final=part('final_drive',37,(0,1.30,.34),parent=diff); cylinder('Ring gear',(0,1.30,.34),.085,.033,final,'alloy','X',24,.04)
    prop=part('driveshaft',37,(0,.44,.40)); beam('Prop shaft',(0,-.433,.547),(0,1.17,.34),.027,prop)
    for s in [-1,1]:
        ax=part('rear_cv_axle_'+str(s),37,(s*.43,1.30,.34)); beam('Half shaft',(s*.13,1.30,.34),(s*.755,1.30,.34),.016,ax)
        for x in [s*.16,s*.70]: cylinder('CV boot',(x,1.30,.34),.042,.10,ax,'rubber','X')
    tank=part('fuel_tank',26,(0,1.73,.41)); box('Fuel tank',(0,1.73,.405),(.95,.63,.18),tank,'black',.025)
    pump=part('fuel_pump',26,(.28,1.72,.487),parent=tank); cylinder('Tank pump',(.28,1.72,.487),.042,.06,pump,'alloy')
    line=part('fuel_lines',26,(-.40,.1,.44)); pipe('Fuel line',[(-.40,1.73,.45),(-.40,1.02,.43),(-.40,-.73,.44),(-.23,-1.07,.91)],.005,line,'black')
    rad=part('radiator',29,(0,-1.945,.768),slot='radiator')
    box('Radiator core',(0,-1.945,.768),(1.03,.047,.43),rad,'steel')
    for x in [-.54,.54]: box('Radiator tank',(x,-1.945,.768),(.05,.065,.45),rad,'black')
    for z in [.58+i*.014 for i in range(28)]: box('Radiator fin',(0,-1.976,z),(.99,.004,.004),rad,'alloy',0)
    fan=part('radiator_fan',29,(0,-1.902,.78),parent=rad)
    cylinder('Fan shroud',(0,-1.901,.78),.165,.022,fan,'black','Y',28,.15)
    for i in range(7):
        a=i*math.tau/7; beam('Fan blade',(0,-1.895,.78),(.145*math.cos(a),-1.895,.78+.145*math.sin(a)),.014,fan,'black',4)
    coolant=part('coolant_hoses',29,(-.3,-1.75,.84))
    pipe('Upper coolant hose',[(-.45,-1.92,.94),(-.58,-1.78,.94),(-.58,-1.0,.97),(-.18,-1.10,.93)],.016,coolant,'rubber')
    pipe('Lower coolant hose',[(.45,-1.92,.57),(.48,-1.77,.56),(-.15,-1.70,.63)],.014,coolant,'rubber')
    reservoir=part('coolant_reservoir',29,(.49,-1.10,.81)); box('Coolant reservoir',(.49,-1.10,.81),(.16,.20,.19),reservoir,'white',.02)
    cylinder('Reservoir cap',(.49,-1.10,.917),.025,.020,reservoir,'black')
    bat=part('battery',27,(-.49,-1.69,.756)); box('Battery',(-.49,-1.69,.756),(.23,.18,.16),bat,'black',.01)
    for x,mat in [(-.56,'red'),(-.43,'alloy')]: cylinder('Terminal',(x,-1.69,.846),.012,.02,bat,mat)
    wires=part('wiring_harness',27,(.42,-.9,.89)); pipe('Loom',[(.4,-.86,.94),(.20,-1.0,.95),(.17,-1.36,1.01),(.17,-1.64,1.01)],.007,wires,'black')
    exhaust=part('exhaust_system',30,(.35,.30,.29))
    pipe('Exhaust pipe',[(.34,-1.06,.55),(.37,-.72,.30),(.37,.80,.26),(.47,1.35,.26),(.47,1.8,.29)],.027,exhaust,'steel')
    muff=part('muffler',30,(.49,1.88,.29),parent=exhaust); box('Muffler',(.49,1.88,.29),(.23,.40,.13),muff,'alloy',.02)
    tip=part('exhaust_tip',30,(.49,2.18,.29),parent=exhaust); cylinder('Exhaust tip',(.49,2.18,.29),.039,.20,tip,'alloy','Y',24,.031)

def build_stock():
    chassis_body(); wheels_suspension(); interior(); engine(); driveline_services()
    SC.frame_start=1; SC.frame_end=100; SC.frame_set(1)
    for f,name in [(1,'CLOSED / driving'),(50,'OPEN / service'),(100,'CLOSED')]: SC.timeline_markers.new(name,frame=f)
    bpy.context.view_layer.update()
    return {'stock_parts':sum(p.get('category',0)>0 for p in PARTS),'scene':SC.name}

def refine_stock():
    for p in PARTS:
        if p.name.startswith('SM_door_') and p.get('category')==2:
            s=-1 if 'left' in p.name else 1
            p.rotation_euler.z=math.radians(-s*62); p.keyframe_insert(data_path='rotation_euler',frame=50); p['open_degrees']=-s*62
    SC.frame_set(1)
    shell=next(p for p in PARTS if p.name=='SM_chassis_shell')
    if not any(o.name.startswith('Right passenger floor') for o in shell.children):
        floor=next(o for o in shell.children if o.name.startswith('Passenger floor'))
        for v in floor.data.vertices:
            if v.co.x>0: v.co.x=-.18
        box('Right passenger floor',(.4975,.38,.39),(.635,2.29,.075),shell,'steel')
        box('Tunnel top',(0,.38,.61),(.36,2.29,.025),shell,'steel')
        for s in [-1,1]: box('Tunnel side',(s*.174,.38,.507),(.012,2.29,.21),shell,'steel')
    # Close intentional assembly gaps with separate, shaped wing shoulders.
    for s in [-1,1]:
        f=next(p for p in PARTS if p.name==f'SM_front_fender_{s}')
        for o in f.children:
            if o.type=='MESH' and o.name.startswith('Fender skin'):
                for v in o.data.vertices:
                    if v.co.z>.9: v.co.z=1.028+(v.co.y+2.105)/1.315*.078
        panel('Front wing shoulder',[(s*.867,-2.15,1.025),(s*.944,-2.15,1.025),(s*.944,-.774,1.109),(s*.867,-.774,1.109)],f,th=.016)
        q=next(p for p in PARTS if p.name==f'SM_rear_fender_{s}')
        panel('Rear quarter pillar',[(s*.879,1.025,1.047),(s*.84,1.337,1.063),(s*.733,.941,1.481),(s*.733,.918,1.470)],q,th=.027)
        panel('Rear wing shoulder',[(s*.865,1.34,1.05),(s*.944,1.34,1.05),(s*.944,2.15,1.026),(s*.865,2.15,1.026)],q,th=.014)
        for o in q.children:
            if o.type=='MESH' and o.name.startswith('Fender skin'):
                for v in o.data.vertices:
                    if v.co.z>.9 and v.co.y>1.3: v.co.z=1.05-(v.co.y-1.35)*.023/.75
    for p in PARTS:
        if p.name in ['SM_windshield','SM_rear_glass']:
            ob=next(o for o in p.children if o.type=='MESH')
            points=[tuple(v.co) for v in ob.data.vertices]
            for a,b in zip(points,points[1:]+points[:1]): beam('Glass perimeter seal',a,b,.010,p,'black')

def upgrade(name,cat,pos,slot,replaces=''):
    p=part(name,cat,pos,collection=ALT,slot=slot); p['replaces']=replaces; return p

def upgrades():
    # Each root remains at the installation coordinates; not a pile of installed options.
    aero=upgrade('rear_spoiler',7,(0,1.78,1.05),'spoiler')
    for x in [-.53,.53]: box('Wing pedestal',(x,1.79,1.16),(.045,.18,.21),aero,'black')
    box('Rear wing',(0,1.79,1.283),(1.64,.28,.04),aero,'black',.01)
    sp=upgrade('front_splitter',7,(0,-2.2,.45),'splitter'); box('Splitter',(0,-2.2,.45),(1.98,.33,.027),sp,'black')
    for s in [-1,1]:
        wide=upgrade('widebody_fender_'+str(s),7,(s*.96,-1.35,.7),'front_fender_'+str(s),f'front_fender_{s}')
        ob=box('Wide wing',(s*.969,-1.465,.74),(.13,1.39,.62),wide,'paint'); hole(ob,(s*.97,-1.35,.34),.445,.45,'X')
        skirt=upgrade('sport_skirt_'+str(s),7,(s*.947,.18,.405),'side_skirt_'+str(s),f'side_skirt_{s}')
        box('Sport side skirt',(s*.958,.18,.397),(.13,1.78,.13),skirt,'paint')
        fog=upgrade('fog_light_'+str(s),6,(s*.64,-2.27,.58),'fog_light_'+str(s)); cylinder('Fog lamp',(s*.64,-2.27,.58),.045,.029,fog,'white','Y')
        under=upgrade('underglow_'+str(s),6,(s*.72,.12,.32),'underglow_'+str(s)); box('LED strip',(s*.72,.12,.32),(.022,1.6,.012),under,'screen')
    bumper=upgrade('sport_front_bumper',7,(0,-2.17,.7),'front_bumper','front_bumper')
    for x in [-.83,0,.83]: box('Sport bumper rib',(x,-2.18,.66),(.13,.16,.32),bumper,'paint')
    for z in [.48,.81]: box('Sport bumper rail',(0,-2.18,z),(1.87,.16,.09),bumper,'paint')
    for x in [-.46,.46]: box('Intake mesh',(x,-2.15,.65),(.53,.015,.22),bumper,'black')
    for s in [-1,1]:
        b=upgrade('bucket_seat_'+str(s),8,(s*.45,.10,.44),'front_seat_'+str(s),'front_seat_'+str(s))
        box('Bucket base',(s*.45,.04,.565),(.46,.49,.13),b,'black',.025)
        box('Bucket back',(s*.45,.3,.93),(.43,.10,.67),b,'black',.025)
        for dx in [-.20,.20]: box('Seat bolster',(s*.45+dx,.045,.64),(.055,.43,.12),b,'cloth',.015)
        for dx in [-.17,.17]: box('Shoulder bolster',(s*.45+dx,.23,1.075),(.075,.16,.20),b,'cloth',.02)
        harness=upgrade('harness_'+str(s),12,(s*.45,.17,1.03),'harness_'+str(s))
        for dx in [-.10,.10]: beam('Harness strap',(s*.45+dx,.19,1.18),(s*.45+dx,.01,.67),.015,harness,'red',4)
        box('Harness buckle',(s*.45,.015,.68),(.07,.033,.05),harness,'alloy')
    cage=upgrade('roll_cage',12,(0,.37,.8),'roll_cage')
    for y in [-.12,.83]:
        pipe('Roll hoop',[(-.74,y,.44),(-.69,y,1.35),(-.58,y,1.41),(.58,y,1.41),(.69,y,1.35),(.74,y,.44)],.019,cage,'red')
    for s in [-1,1]: beam('Cage roof bar',(s*.64,-.12,1.40),(s*.64,.83,1.40),.019,cage,'red')
    beam('Rear diagonal',(-.69,.83,.48),(.69,.83,1.34),.018,cage,'red')
    dg=upgrade('digital_cluster',9,(-.47,-.437,1.013),'instrument_cluster','instrument_cluster')
    box('Digital dash',(-.47,-.41,1.055),(.4,.05,.12),dg,'black'); box('Digital screen',(-.47,-.38,1.055),(.35,.008,.087),dg,'screen')
    # Turbo, charge cooling and its replacement manifold.
    turbo=upgrade('turbocharger',31,(.42,-1.26,.81),'forced_induction'); turbo['exclusive_group']='forced_induction'
    for y,ma in [(-1.30,'alloy'),(-1.20,'steel')]:
        cylinder('Turbo volute',(.42,y,.81),.084,.064,turbo,ma,'Y',24,.031)
    cylinder('Turbo inlet',(.42,-1.349,.81),.037,.045,turbo,'alloy','Y',20,.027)
    turbh=upgrade('turbo_exhaust_manifold',31,(.30,-1.37,.83),'exhaust_header','exhaust_header')
    for y in [-1.5575,-1.4325,-1.3075,-1.1825]: pipe('Turbo runner',[(.195,y,.86),(.27,y,.86),(.35,-1.20,.81)],.018,turbh,'steel')
    wg=upgrade('wastegate',31,(.41,-1.09,.77),'wastegate'); cylinder('Wastegate',(.41,-1.09,.77),.027,.066,wg,'red')
    bov=upgrade('blowoff_valve',31,(-.48,-1.76,.90),'bov'); cylinder('BOV',(-.48,-1.76,.90),.023,.04,bov,'blue')
    ic=upgrade('intercooler',31,(0,-2.037,.72),'intercooler'); box('Intercooler core',(0,-2.037,.72),(.73,.05,.23),ic,'alloy')
    for z in [.62+i*.018 for i in range(12)]: box('Intercooler fin',(0,-2.066,z),(.71,.004,.004),ic,'steel',0)
    cp=upgrade('charge_pipes',31,(0,-1.8,.84),'charge_pipe')
    pipe('Hot pipe',[(.42,-1.33,.81),(.55,-1.45,.88),(.55,-1.85,.83),(.39,-2.035,.72)],.026,cp,'alloy')
    pipe('Cold pipe',[(-.39,-2.035,.72),(-.60,-1.89,.79),(-.60,-1.77,.90),(-.32,-1.73,.886)],.026,cp,'alloy')
    sc=upgrade('centrifugal_supercharger',32,(.38,-1.64,.86),'forced_induction'); sc['exclusive_group']='forced_induction'; sc['replaces']='turbocharger'
    cylinder('Supercharger compressor',(.38,-1.64,.86),.088,.08,sc,'alloy','Y',24,.026)
    cylinder('SC drive pulley',(.38,-1.738,.86),.045,.018,sc,'black','Y')
    pipe('SC belt',[(0,-1.752,.48),(.42,-1.752,.83),(.38,-1.752,.91),(-.05,-1.752,.56),(0,-1.752,.48)],.007,sc,'rubber')
    pipe('SC intake',[(.38,-1.58,.86),(.45,-1.45,.94),(.48,-1.04,.95)],.026,sc,'alloy')
    nos=upgrade('nitrous_kit',33,(-.40,1.73,.75),'nos_kit')
    cylinder('Nitrous bottle',(-.40,1.73,.75),.079,.35,nos,'blue','Y',24)
    cylinder('Bottle neck',(-.40,1.515,.75),.027,.08,nos,'alloy','Y')
    for y in [1.63,1.83]: cylinder('Bottle strap',(-.40,y,.75),.084,.022,nos,'steel','Y',24,.080)
    cylinder('NOS solenoid',(.27,-1.05,.96),.015,.04,nos,'blue')
    pipe('NOS line',[(-.40,1.50,.75),(-.62,1.38,.58),(-.62,-.75,.56),(-.27,-1.08,.90)],.003,nos,'alloy')
    wm=upgrade('water_meth_kit',34,(.50,-.89,.81),'water_meth')
    box('Water meth tank',(.50,-.89,.81),(.14,.14,.19),wm,'white',.012)
    cylinder('Water meth pump',(.50,-.89,.68),.029,.07,wm,'black','X')
    pipe('Water meth feed',[(.50,-.89,.71),(.62,-.93,.95),(.62,-1.82,.95),(-.32,-1.75,.886)],.004,wm,'blue')
    ecu=part('ecu',39,(.52,-.785,.92)); box('ECU casing',(.52,-.785,.92),(.17,.045,.115),ecu,'alloy')
    for x in [.47,.53,.59]: box('ECU connector',(x,-.818,.92),(.04,.026,.07),ecu,'black')
    obd=part('obd_port',39,(-.68,-.48,.77)); box('OBD socket',(-.68,-.48,.77),(.048,.025,.022),obd,'black')
    dongle=upgrade('obd_dongle',39,(-.68,-.453,.75),'obd_port'); box('OBD dongle',(-.68,-.453,.75),(.043,.035,.045),dongle,'blue')
    two=upgrade('two_step_module',39,(.53,-.785,.82),'two_step'); box('Two-step box',(.53,-.785,.82),(.085,.032,.048),two,'blue')
    abs=upgrade('abs_module',15,(-.56,-.9,.78),'abs'); box('ABS block',(-.56,-.9,.78),(.13,.11,.09),abs,'alloy')
    for x in [-.60,-.56,-.52]: pipe('Brake hardline',[(x,-.9,.83),(x,-.85,.90),(x,-.76,.80)],.003,abs,'steel')
    vtec=upgrade('vtec_solenoid',22,(.21,-1.12,.925),'vtec'); cylinder('VTEC solenoid',(.21,-1.12,.925),.018,.034,vtec,'gold')
    carb=upgrade('carburetor_intake',25,(-.32,-1.37,.89),'intake_manifold','intake_manifold;fuel_rail;injectors;throttle_body')
    for y in [-1.50,-1.25]: cylinder('Carb throat',(-.32,y,.922),.025,.115,carb,'alloy','Z',16,.018)
    box('Carb manifold',(-.28,-1.37,.845),(.18,.49,.07),carb,'alloy')
    # Same envelope and mounting origin for each transmission option.
    for typ in ['sequential','automatic','dct','cvt']:
        gg=upgrade(typ+'_gearbox',36,(0,-.69,.547),'transmission_mount','manual_5speed')
        cylinder('Bellhousing',(0,-.914,.547),.143,.12,gg,'alloy','Y',20,.127)
        box(typ+' housing',(0,-.64,.547),(.25,.42,.24),gg,'alloy',.019)
        if typ=='automatic': cylinder('Torque converter',(0,-.91,.547),.115,.075,gg,'steel','Y')
        if typ=='dct':
            for yy in [-.91,-.87]: cylinder('Clutch pack',(0,yy,.547),.112,.026,gg,'steel','Y')
        if typ=='cvt':
            for y in [-.74,-.54]: cylinder('CVT pulley',(0,y,.547),.08,.10,gg,'steel','X')
            pipe('CVT belt',[(0,-.74,.628),(0,-.54,.628),(0,-.54,.468),(0,-.74,.468),(0,-.74,.628)],.008,gg,'rubber')
    for typ in ['lsd','locked','torsen','welded']:
        d=upgrade(typ+'_differential',37,(0,1.30,.34),'rear_differential','rear_differential')
        box('Diff housing',(0,1.30,.34),(.25,.26,.20),d,'steel',.025)
        cylinder('Differential gear',(0,1.30,.34),.085,.06,d,'gold' if typ=='torsen' else 'alloy','X',24,.031)
    awd=upgrade('awd_transfer_kit',37,(0,-.41,.48),'transfer_case'); awd['requires']='AWD-compatible chassis configuration'
    box('Transfer case',(.16,-.42,.47),(.18,.20,.19),awd,'steel')
    beam('Front propshaft',(.21,-.48,.45),(.21,-1.28,.32),.023,awd)
    box('Front differential',(.07,-1.35,.32),(.20,.21,.17),awd,'steel')
    for s in [-1,1]: beam('Front halfshaft',(s*.10,-1.35,.32),(s*.74,-1.35,.34),.016,awd)
    dmg=upgrade('damaged_front_bumper',43,(0,-2.17,.70),'front_bumper','front_bumper')
    panel('Dented bumper',[(-.94,-2.22,.5),(.94,-2.22,.5),(.93,-2.24,.8),(.35,-2.21,.83),(.1,-2.08,.75),(-.35,-2.22,.84),(-.94,-2.22,.80)],dmg,'paint',.08)
    return {'upgrade_roots':sum(p.get('asset_state')=='upgrade' for p in PARTS)}

def alternate_engines():
    for layout,n in [('v6',6),('flat4',4)]:
        p=upgrade(layout+'_engine',18,(0,-1.37,.67),'engine_mount','i4_engine')
        p['exclusive_group']='engine_family'; p['family']=layout; p['cylinders']=n
        cylinder('Crankshaft',(0,-1.37,.547),.025,.66,p,'steel','Y')
        box('Crankcase',(0,-1.37,.576),(.31,.60,.18),p,'alloy',.015)
        box('Oil pan',(0,-1.37,.445),(.33,.55,.12),p,'steel',.014)
        count=n//2
        for s in [-1,1]:
            x=s*(.19 if layout=='v6' else .24); z=.77 if layout=='v6' else .67
            bank=upgrade(layout+'_bank_'+str(s),18,(x,-1.37,z),'engine_bank')
            parent_world(bank,p)
            ob=box('Bank casting',(x,-1.37,z),(.19,.56,.27 if layout=='v6' else .19),bank,'alloy',.01)
            head=upgrade(layout+'_head_'+str(s),19,(x,-1.37,z+.165),'cylinder_head'); parent_world(head,p)
            box('Cylinder head',(x,-1.37,z+.165),(.215,.58,.085),head,'alloy')
            cover=upgrade(layout+'_cover_'+str(s),19,(x,-1.37,z+.225),'valve_cover'); parent_world(cover,p)
            box('Valve cover',(x,-1.37,z+.225),(.20,.55,.035),cover,'red')
            for i in range(count):
                y=-1.56+i*(.38/max(count-1,1))
                cyl=upgrade(f'{layout}_cylinder_{s}_{i}',20,(x,y,z),'cylinder'); parent_world(cyl,bank)
                if layout=='v6':
                    liner=cylinder('Liner',(x,y,z+.04),.045,.18,cyl,'steel','Z',20,.040)
                    axis=Vector((s*.5,0,.866)); q=axis.to_track_quat('Z','Y')
                    c=Vector((x,y,z+.04))
                    for v in liner.data.vertices: v.co=c+q@(v.co-c)
                    beam('Connecting rod',(0,y,.565),(x,y,z+.05),.009,cyl,'steel')
                else:
                    cylinder('Horizontal liner',(x,y,z),.045,.17,cyl,'steel','X',20,.040)
                    cylinder('Flat piston',(x+s*.045,y,z),.039,.055,cyl,'alloy','X')
                    beam('Boxer rod',(0,y,.60),(x,y,z),.010,cyl,'steel')
                cylinder('Plug',(x,y,z+.237),.008,.029,cover,'white')
            cam=upgrade(layout+'_cam_'+str(s),22,(x,-1.37,z+.176),'camshaft'); parent_world(cam,head)
            cylinder('Camshaft',(x,-1.37,z+.176),.014,.56,cam,'steel','Y')
        box('Front timing guard',(0,-1.706,.70),(.54,.025,.42),p,'black')
        box('Intake plenum',(0,-1.35,.94),(.17,.47,.11),p,'alloy',.018)
        cylinder('Output flange',(0,-1.025,.547),.11,.023,p,'steel','Y')
        for s in [-1,1]: box('Common engine mount',(s*.24,-1.39,.54),(.10,.10,.08),p,'rubber')
    # Modular 1/2/3/4 rotor assemblies with the same output flange and chassis mounts.
    for count in [1,2,3,4]:
        p=upgrade(f'rotary_{count}rotor',38,(0,-1.37,.67),'engine_mount','i4_engine')
        p['family']='rotary'; p['rotors']=count; p['exclusive_group']='engine_family'
        shaft=upgrade(f'eccentric_shaft_{count}r',38,(0,-1.37,.70),'eccentric_shaft'); parent_world(shaft,p)
        cylinder('Eccentric shaft',(0,-1.37,.70),.023,.66,shaft,'steel','Y')
        for j in range(count):
            y=-1.105-j*.142
            h=upgrade(f'rotor_housing_{count}r_{j}',38,(0,y,.70),'rotor_housing'); parent_world(h,p)
            vs=[]; n=48
            for scale in [1,.78]:
                for yy in [y-.062,y+.062]:
                    vs += [((.16*math.cos(a*math.tau/n)+.02*math.cos(3*a*math.tau/n))*scale,yy,.70+(.14*math.sin(a*math.tau/n)-.018*math.sin(3*a*math.tau/n))*scale) for a in range(n)]
            fs=[]
            for a in range(n):
                b=(a+1)%n; fs.extend([(a,b,b+n,a+n),(2*n+a,3*n+a,3*n+b,2*n+b),(a,2*n+a,2*n+b,b),(a+n,b+n,3*n+b,3*n+a)])
            mesh('Rotor housing',vs,fs,h,'alloy')
            rr=upgrade(f'rotor_{count}r_{j}',38,(.012,y,.70),'rotor'); parent_world(rr,p)
            vs=[]
            for yy in [y-.045,y+.045]:
                for a in range(36):
                    t=a*math.tau/36; r=.078+.013*math.cos(3*t); vs.append((.012+r*math.cos(t),yy,.70+r*math.sin(t)))
            mesh('Triangular rotor',vs,[(a,(a+1)%36,(a+1)%36+36,a+36) for a in range(36)]+[tuple(reversed(range(36))),tuple(range(36,72))],rr,'steel')
            for a in range(3):
                t=a*math.tau/3; seal=upgrade(f'apex_seal_{count}r_{j}_{a}',38,(.012+.091*math.cos(t),y,.70+.091*math.sin(t)),'apex_seal'); parent_world(seal,rr)
                beam('Apex seal',(.012+.091*math.cos(t),y-.044,.70+.091*math.sin(t)),(.012+.091*math.cos(t),y+.044,.70+.091*math.sin(t)),.003,seal,'gold',4)
            sg=upgrade(f'stationary_gear_{count}r_{j}',38,(0,y+.047,.70),'stationary_gear'); parent_world(sg,p)
            cylinder('Stationary gear',(0,y+.047,.70),.038,.012,sg,'gold','Y',18,.024)
        for j in range(count+1):
            y=-1.035-j*.142
            sh=upgrade(f'side_housing_{count}r_{j}',38,(0,y,.70),'side_housing'); parent_world(sh,p)
            box('Side plate',(0,y,.70),(.35,.014,.30),sh,'alloy',.014)
        for typ,x in [('intake',-.19),('exhaust',.19)]:
            port=upgrade(f'{typ}_port_{count}r',38,(x,-1.30,.77),typ+'_port'); parent_world(port,p)
            cylinder(typ+' port',(x,-1.30,.77),.03,.10,port,'alloy','X',20,.022)
        pump=upgrade(f'oil_metering_pump_{count}r',38,(.22,-1.45,.58),'oil_metering_pump'); parent_world(pump,p)
        box('Metering pump',(.22,-1.45,.58),(.08,.10,.06),pump,'steel')
        cylinder('Output adapter',(0,-1.025,.547),.11,.022,p,'steel','Y')
        box('Front accessory carrier',(0,-1.72,.67),(.34,.025,.31),p,'black')
        for s in [-1,1]: box('Common engine mount',(s*.24,-1.39,.54),(.10,.10,.08),p,'rubber')
    return {'engine_configs':['inline4','v6','flat4','rotary1','rotary2','rotary3','rotary4']}

def workshop():
    global CAR
    original_car=CAR; w=bpy.data.scenes.new('SM_04_Workshop'); w.unit_settings.system='METRIC'; w.unit_settings.scale_length=1
    bpy.context.window.scene=w
    wc=bpy.data.collections.new('SM_Garage_equipment'); w.collection.children.link(wc); CAR=wc
    def wp(name,cat,pos):
        p=part(name,cat,pos,parent=None,collection=wc); p['asset_state']='workshop'; return p
    lift=wp('garage_lift',40,(0,0,0))
    for s in [-1,1]:
        box('Lift post',(s*1.60,0,1.40),(.22,.32,2.80),lift,'blue',.018)
        box('Lift base',(s*1.60,0,.06),(.48,.58,.12),lift,'steel')
        for y in [-.80,.80]:
            beam('Lift arm',(s*1.60,0,.28),(s*.52,y,.28),.055,lift,'steel',6)
            cylinder('Lifting pad',(s*.52,y,.35),.073,.033,lift,'rubber')
    box('Lift crossbar',(0,0,2.83),(3.4,.28,.18),lift,'blue')
    box('Lift controls',(1.74,-.12,1.2),(.08,.15,.22),lift,'black')
    bench=wp('machining_bench',40,(3.2,0,0))
    box('Worktop',(3.2,0,1.0),(1.70,.80,.085),bench,'alloy')
    for x in [2.48,3.92]:
        for y in [-.30,.30]: box('Bench leg',(x,y,.48),(.065,.065,.96),bench,'steel')
    box('Tool drawers',(3.25,0,.69),(.85,.64,.48),bench,'blue')
    for z in [.56,.70,.84]: box('Drawer handle',(3.25,-.34,z),(.56,.022,.018),bench,'alloy')
    vice=wp('bench_vise',40,(2.73,-.04,1.16)); box('Vise base',(2.73,-.04,1.09),(.32,.27,.09),vice,'steel')
    for x in [2.65,2.84]: box('Vise jaw',(x,-.04,1.19),(.07,.20,.13),vice,'alloy')
    cylinder('Lead screw',(2.76,-.04,1.14),.013,.35,vice,'steel','X')
    fuel=wp('fuel_pump_block',41,(-3.2,0,0))
    box('Pump plinth',(-3.2,0,.10),(.80,.65,.20),fuel,'steel')
    box('Fuel dispenser',(-3.2,0,.83),(.60,.45,1.28),fuel,'blue',.025)
    box('Display',(-3.2,-.24,1.15),(.39,.018,.18),fuel,'screen')
    pipe('Fuel hose',[(-2.89,0,1.30),(-2.65,-.10,1.00),(-2.64,-.15,.32),(-2.77,-.22,.27),(-2.84,-.25,.82)],.017,fuel,'rubber')
    nozzle=wp('fuel_nozzle',41,(-2.84,-.25,.88)); beam('Nozzle body',(-2.84,-.25,.82),(-2.84,-.25,.99),.025,nozzle,'black')
    beam('Nozzle spout',(-2.84,-.25,.99),(-2.91,-.25,1.09),.011,nozzle,'alloy')
    dyno=wp('dyno_roller_bed',41,(0,4.5,0))
    for x in [-1.0,1.0]: box('Dyno rail',(x,4.5,.12),(.13,3.95,.24),dyno,'steel')
    for y in [4.5-1.35,4.5+1.30]:
        for dy in [-.13,.13]: cylinder('Dyno roller',(0,y+dy,.15),.15,1.82,dyno,'alloy','X',32)
    for y in [2.3,6.7]: box('Drive-on ramp',(0,y,.09),(2.12,.45,.18),dyno,'steel')
    dc=wp('dyno_console',41,(1.8,4.6,0)); box('Console pedestal',(1.8,4.6,.70),(.30,.35,1.4),dc,'blue')
    box('Dyno monitor',(1.8,4.6,1.49),(.65,.13,.39),dc,'black'); box('Dyno screen',(1.8,4.527,1.49),(.58,.009,.31),dc,'screen')
    for s in [-1,1]:
        pipe('Tie down strap',[(s*.85,3.15,.23),(s*1.06,2.75,.08)],.012,dyno,'red')
    booth=wp('paint_booth',41,(0,-6,0))
    for x in [-1.70,1.70]:
        for y in [-8.65,-3.35]: box('Booth upright',(x,y,1.35),(.08,.08,2.70),booth,'steel')
        box('Booth wall',(x,-6,1.2),(.055,5.30,2.35),booth,'white')
    box('Booth ceiling',(0,-6,2.69),(3.48,5.40,.06),booth,'white')
    for x in [-1.25,1.25]: box('Booth strip light',(x,-6,2.63),(.09,4.6,.025),booth,'white')
    box('Booth control',(1.80,-3.45,1.14),(.13,.25,.32),booth,'blue')
    jack=wp('floor_jack',40,(2.7,1.3,.13)); box('Jack body',(2.7,1.3,.13),(.23,.64,.12),jack,'red',.016)
    for x in [2.57,2.83]: cylinder('Jack wheel',(x,1.5,.10),.06,.04,jack,'black','X')
    beam('Jack lift arm',(2.7,1.50,.16),(2.7,1.04,.25),.035,jack,'steel',6)
    cylinder('Jack saddle',(2.7,1.04,.28),.065,.023,jack,'rubber')
    beam('Jack handle',(2.7,1.58,.13),(2.7,1.92,.74),.014,jack,'black')
    laptop=wp('tuning_laptop',39,(3.47,.02,1.075))
    box('Laptop base',(3.47,.02,1.075),(.34,.24,.018),laptop,'steel')
    screen=box('Laptop lid',(3.47,.127,1.187),(.34,.015,.225),laptop,'black')
    box('Laptop display',(3.47,.115,1.19),(.303,.004,.184),laptop,'screen')
    for row in range(4):
        for i in range(10): box('Keyboard key',(3.34+i*.028,-.035+row*.027,1.087),(.021,.019,.002),laptop,'black',0)
    for i,(name,ma) in enumerate([('jerry_can','blue'),('oil_container','black'),('coolant_container','white'),('water_meth_container','white')]):
        x=2.65+i*.26; item=wp(name,42,(x,.20,1.20))
        box('Fluid container',(x,.20,1.20),(.17,.11,.30),item,ma,.018)
        cylinder('Container cap',(x-.045,.20,1.363),.018,.018,item,'red')
        beam('Carry handle',(x-.018,.20,1.364),(x+.065,.20,1.364),.009,item,'black')
        box('Container label',(x,.138,1.21),(.10,.005,.15),item,'white')
    CAR=original_car; bpy.context.window.scene=SC
    return {'workshop_scene':w.name,'equipment_roots':sum(p.get('asset_state')=='workshop' for p in PARTS)}

if __name__=='__main__':
    result=build_stock()
    refine_stock(); upgrades(); alternate_engines(); workshop()
