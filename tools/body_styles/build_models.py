"""Blender 4.2: five distinct coachworks for the shared AutoPropulsion mechanical platform.

Run: blender --background --python tools/body_styles/build_models.py
Body meshes contain only coachwork and explicitly fitted cosmetic components. The original
engine/axle/suspension geometry and physics definitions are not rewritten. Editable .blend
files retain named panels, hinge metadata and a stock mechanical fit-reference collection.
"""
from __future__ import annotations
import bpy
import bmesh
import sys
import math
import json
import re
import hashlib
from pathlib import Path
from mathutils import Vector
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mesh_io import read,write,bounds

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/assets/sparkmotors/models/entity/bodies'
ASSETS=ROOT/'assets/body_styles'
REVIEWS=ROOT/'docs/body-styles/blender'
PAINT=0xFF218B92;BLACK=0xFF151D22;PLASTIC=0xFF29363D;ALLOY=0xFFA7B3BD
GLASS=0xFF70959F;CHROME=0xFFD2DDE2;WHITE=0xFFEAF5F8;RED=0xFFB82B35;AMBER=0xFFE9A13A
FIELDS=('nose','tail','halfWidth','belt','roof','cabinFront','roofFront','roofRear','cabinRear','cabinY','cabinZ')
source=(ROOT/'sim/src/main/java/com/photonspark/sparkmotors/sim/BodyStyle.java').read_text()
styles=[]
for enum,num,key,title,numbers in re.findall(r'^\s*([A-Z_]+)\((\d+),"([^"]+)","([^"]+)",([^)]*)\)',source,re.M):
    values=[float(v) for v in numbers.split(',')]
    assert len(values)==len(FIELDS),(key,values)
    styles.append(dict(zip(FIELDS,values),id=key,title=title,networkId=int(num)))
assert len(styles)==6
canonical=read(ROOT/'src/main/resources/assets/sparkmotors/models/entity/sedan.mesh.gz')
materials={};body_objects=[];body_chunks=[]


def native(point):return (point[0],-point[2],point[1])
def runtime(point):return (point[0],point[2],-point[1])
def linear(c):
    c=c/255
    return c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4

def mat(color,kind=0):
    key=(color,kind)
    if key not in materials:
        m=bpy.data.materials.new(('Paint' if kind==1 else 'Glass' if kind==2 else 'Lamp' if kind==3 else 'Material')+f'_{color:08x}')
        rgb=tuple(linear((color>>shift)&255) for shift in (16,8,0))
        m.diffuse_color=(*rgb,.28 if kind==2 else 1)
        m.use_nodes=True;bsdf=m.node_tree.nodes.get('Principled BSDF')
        bsdf.inputs['Base Color'].default_value=(*rgb,1)
        bsdf.inputs['Roughness'].default_value=.25 if kind in (1,2) else .48
        bsdf.inputs['Metallic'].default_value=.28 if kind==1 else .65 if color in (ALLOY,CHROME) else 0
        if kind==2:bsdf.inputs['Alpha'].default_value=.28
        materials[key]=m
    return materials[key]


def metadata(name,category=3,group=-1,variant=0,hinge=0,pivot=(0,0,0),angle=0,kind=0):
    return dict(name=name,category=category,group=group,variant=variant,hinge=hinge,px=pivot[0],py=pivot[1],pz=pivot[2],angle=angle,family=127,slot=-1,tier=0,induction=127,kind=kind)


def make(name,vertices,faces,color=PAINT,category=3,group=-1,variant=0,hinge=0,pivot=(0,0,0),angle=0,kind=None):
    kind=(1 if color==PAINT else 2 if color==GLASS else 0) if kind is None else kind
    md=metadata(name,category,group,variant,hinge,pivot,angle,kind)
    me=bpy.data.meshes.new(name);me.from_pydata([native(v) for v in vertices],[],faces);me.update()
    bm=bmesh.new();bm.from_mesh(me);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
    ob=bpy.data.objects.new(name,me);bpy.context.scene.collection.children['Coachwork'].objects.link(ob);me.materials.append(mat(color,kind))
    ob['apa_metadata']=json.dumps(md,sort_keys=True);ob['apa_color']=int(color);body_objects.append(ob)
    me.calc_loop_triangles();data=[]
    for tri in me.loop_triangles:
        n=runtime(tri.normal)
        for vi in tri.vertices:data.append((*runtime(me.vertices[vi].co),*n,color))
    body_chunks.append(dict(md,vertices=data))
    return ob


def box(name,lo,hi,color=PAINT,**kw):
    x0,y0,z0=lo;x1,y1,z1=hi
    return make(name,[(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)],[(0,3,2,1),(4,5,6,7),(0,4,7,3),(1,2,6,5),(3,7,6,2),(0,1,5,4)],color,**kw)


def tube(name,points,radius,color=BLACK,sides=8,**kw):
    verts=[];faces=[]
    for a,b in zip(points,points[1:]):
        a=Vector(a);b=Vector(b);d=(b-a).normalized();u=d.cross(Vector((0,1,0)))
        if u.length<.01:u=d.cross(Vector((1,0,0)))
        u.normalize();v=d.cross(u);start=len(verts)
        for c in (a,b):
            for i in range(sides):verts.append(c+radius*(u*math.cos(i*math.tau/sides)+v*math.sin(i*math.tau/sides)))
        for i in range(sides):j=(i+1)%sides;faces.append((start+i,start+j,start+sides+j,start+sides+i))
        faces += [tuple(start+i for i in reversed(range(sides))),tuple(start+sides+i for i in range(sides))]
    return make(name,verts,faces,color,**kw)


def side_poly(name,side,points,thickness=.028,color=PAINT,**kw):
    # Points are runtime (x magnitude, height, longitudinal position), a closed Y/Z perimeter.
    verts=[(side*(x-d),y,z) for d in (0,thickness) for x,y,z in points]
    n=len(points);faces=[tuple(range(n)),tuple(reversed(range(n,2*n)))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
    return make(name,verts,faces,color,**kw)


def shared(c):
    if c['category']>=13:return not (c['category']==39 or c['group']<0 and c['category'] in (26,30))
    return c['category']==1 and (c['name'].startswith(('strut_tower_','wheel_tub_','wheel_well_','engine_inner_wing_')) or c['name']=='front_undertray')


def import_chunk(c,collection,reference=False):
    # De-duplicate vertices without losing per-face colors. Normal/tangent data are recomputed by Blender.
    verts=[];faces=[];indices={};face_colors=[]
    for t in range(0,len(c['vertices']),3):
        face=[]
        for v in c['vertices'][t:t+3]:
            point=tuple(v[:3]);index=indices.get(point)
            if index is None:index=len(verts);indices[point]=index;verts.append(native(point))
            face.append(index)
        if len(set(face))==3:faces.append(face);face_colors.append(c['vertices'][t][6])
    me=bpy.data.meshes.new(c['name']);me.from_pydata(verts,[],faces);me.update()
    ob=bpy.data.objects.new(c['name'],me);collection.objects.link(ob)
    lookup={}
    for color in dict.fromkeys(face_colors):lookup[color]=len(me.materials);me.materials.append(mat(color,c['kind']))
    for poly,color in zip(me.polygons,face_colors):poly.material_index=lookup[color]
    ob['apa_metadata']=json.dumps({k:v for k,v in c.items() if k!='vertices'},sort_keys=True)
    ob['apa_reference_only']=reference
    return ob


def stock_visible(c):
    if c['name']=='service_jack':return False
    if c['family']&1==0 or c['induction']&1==0 or c['tier']>1 or c['variant']>1:return False
    if c['name'].startswith('pt|'):
        _,power,drive,*_=c['name'].split('|')
        return int(power)&1!=0 and int(drive)&1!=0
    return not (c['group']==1 or c['name'] in ('driveshaft','rear_differential','cv_axle_rl','cv_axle_rr','clutch_disc'))


def copy_fitted(s):
    dy=s['cabinY'];dz=s['cabinZ']
    for original in canonical:
        cat=original['category'];name=original['name']
        cabin=cat in (8,9,10,11,39)
        underbody=cat in (26,30) and original['group']<0
        if not cabin and not underbody:continue
        if name.startswith('door_card_'):continue # Reauthored with the correct new door pivots.
        if s['id'] in ('van','sports_car') and name=='rear_bench':continue
        c=dict(original);verts=[]
        factor=(s['tail']+.025-1.30)/(2.30-1.30)
        for x,y,z,nx,ny,nz,color in c['vertices']:
            if cabin:y+=dy;z+=dz
            elif z< -1.30:
                z=-1.30+(z+1.30)*factor
                normal=Vector((nx,ny,nz/factor));normal.normalize();nx,ny,nz=normal
            verts.append((x,y,z,nx,ny,nz,color))
        c['vertices']=verts
        if cabin:c['py']+=dy;c['pz']+=dz
        elif c['pz']< -1.30:c['pz']=-1.30+(c['pz']+1.30)*factor
        body_chunks.append(c)
        import_chunk(c,bpy.context.scene.collection.children['Coachwork'])


def author(s):
    name=s['id'];w=s['halfWidth'];nose=s['nose'];tail=s['tail'];belt=s['belt'];roof=s['roof']
    cf=s['cabinFront'];cr=s['cabinRear'];rf=s['roofFront'];rr=s['roofRear'];floor=.43+s['cabinY']
    # Open-center floor keeps the gearbox/shaft tunnel and every traction pack unobstructed.
    for side in (-1,1):
        x0,x1=(.23,w-.06) if side>0 else (-w+.06,-.23)
        box('floor_pan_'+str(side),(x0,floor-.025,-1.01),(x1,floor,.80),PLASTIC,category=1)
        box('frame_rail_'+str(side),(side*.77-.034,.335,-tail+.18),(side*.77+.034,floor,-.93),PLASTIC,category=1)
        box('rocker_'+str(side),(side*(w-.035)-.036,.415,-.90),(side*(w-.035)+.036,max(floor+.02,.49),.85),PAINT,category=1)
    box('transmission_tunnel',(-.22,max(floor,.44),-.87),(.22,max(floor+.22,.75),.795),PLASTIC,category=1)
    box('firewall',(-.73,.33,.801),(.73,belt+.015,.826),PLASTIC,category=1)
    box('luggage_floor',(-.72,max(floor,.52)-.03,-tail+.11),(.72,max(floor,.52),-1.02),PLASTIC,category=1)
    for z in (-.7,0,.7):box('floor_crossmember_'+str(z),(-.75,.322,z-.019),(.75,.345,z+.019),PLASTIC,category=1)
    # Side skins are sampled around both actual wheels; no rectangular panel fills a wheel arch.
    def bottom(z):
        result=.435
        for axle in (1.35,-1.30):
            if abs(z-axle)<.444:result=max(result,.34+math.sqrt(.444**2-(z-axle)**2))
        return result
    def skin(label,side,z0,z1,hinge=0,pivot=(0,0,0),angle=0):
        n=max(3,int((z1-z0)/.018));zs=[z0+(z1-z0)*i/n for i in range(n+1)]
        points=[(w-.012,bottom(z),z) for z in zs]+[(w,belt,z1),(w,belt,z0)]
        side_poly(label,side,points,hinge=hinge,pivot=pivot,angle=angle,category=2 if hinge else 3)
    def top(z):
        if z>rf:return belt+(roof-belt)*(cf-z)/(cf-rf)
        if z<rr:return belt+(roof-belt)*(z-cr)/(rr-cr)
        return roof
    def side_x(y):return w-.033-.115*max(0,min(1,(y-belt)/(roof-belt)))
    for side in (-1,1):
        left=side<0;suffix='left' if left else 'right';front_h=1 if left else 2
        rear_h=(7 if left else 8) if name=='van' else (3 if left else 4)
        front_end=-.86 if name=='sports_car' else -.25
        rear_end=-1.87 if name=='van' else max(cr+.11,-1.075)
        front_p=(side*w,belt,cf);rear_p=(side*w,belt,front_end-.025)
        angle=68 if left else -68
        skin('front_fender_'+suffix,side,cf+.012,nose-.055)
        skin('door_front_'+suffix,side,front_end+.006,cf-.012,front_h,front_p,angle)
        if name!='sports_car':skin('door_rear_'+suffix,side,rear_end+.006,front_end-.012,rear_h,rear_p,angle)
        skin('rear_quarter_'+suffix,side,-tail+.06,front_end-.014 if name=='sports_car' else rear_end-.012)
        # Door edge rebates and sill seals are inside the aperture, never floating on the tire.
        for z in (cf,front_end,rear_end):
            if z< -tail:continue
            tube('aperture_seal_'+suffix+str(z),[(side*(w-.025),bottom(z)+.005,z),(side*(w-.025),belt,z)],.008,BLACK)
        # Profile follows the windscreen and rear roof taper; pillars separate glass panes.
        divisions=[(front_end+.025,cf-.03,front_h,front_p)]
        if name!='sports_car':divisions.append((rear_end+.035,front_end-.028,rear_h,rear_p))
        if name!='van':divisions.append((cr+.038,(front_end if name=='sports_car' else rear_end)-.025,0,(0,0,0)))
        for i,(a,b,h,pivot) in enumerate(divisions):
            a=max(a,cr+.026);b=min(b,cf-.026)
            if b-a<.06:continue
            if name=='van' and i>0:
                box('cargo_panel_rebate_'+suffix,(side*(w-.011)-.008,belt+.08,a+.06),(side*(w-.011)+.008,roof-.10,b-.06),PAINT,category=2,hinge=h,pivot=pivot,angle=angle)
                # Tall solid pressed cargo panel connects to the roof, not a leftover sedan window.
                side_poly('cargo_upper_'+suffix,side,[(w,belt,a),(w,belt,b),(w-.07,roof-.026,b),(w-.07,roof-.026,a)],hinge=h,pivot=pivot,angle=angle,category=2)
                continue
            zpoints=sorted(set([a,b]+[v for v in (rf,rr) if a<v<b]))
            points=[(side_x(belt+.024),belt+.024,a),(side_x(belt+.024),belt+.024,b)]+[(side_x(top(z)-.034),top(z)-.034,z) for z in reversed(zpoints)]
            side_poly('window_'+suffix+'_'+str(i),side,points,thickness=.006,color=GLASS,hinge=h,pivot=pivot,angle=angle,category=4)
            outline=[(side*x,y,z) for x,y,z in points];outline.append(outline[0])
            tube('window_frame_'+suffix+'_'+str(i),outline,.014,BLACK,hinge=h,pivot=pivot,angle=angle,category=5)
        # A/C pillars are structural. Door glass has its own smaller seals above.
        tube('a_pillar_'+suffix,[(side*(w-.021),belt-.015,cf),(side*(w-.153),roof-.012,rf)],.034,PAINT,category=1)
        tube('c_pillar_'+suffix,[(side*(w-.153),roof-.012,rr),(side*(w-.021),belt-.015,cr)],.046,PAINT,category=1)
        if name!='sports_car':tube('b_pillar_'+suffix,[(side*(w-.025),belt,front_end),(side*(w-.15),top(front_end),front_end)],.030,BLACK,category=1)
        # Handles and interior cards follow the actual door's animation, including van sliders.
        for i,(a,b,h,pivot) in enumerate([(front_end,cf,front_h,front_p)]+([] if name=='sports_car' else [(rear_end,front_end-.025,rear_h,rear_p)])):
            z=a+.17;yy=belt-.09
            box('handle_'+suffix+'_'+str(i),(side*(w+.014)-.015,yy-.018,z-.09),(side*(w+.014)+.015,yy+.018,z+.09),CHROME,category=5,hinge=h,pivot=pivot,angle=angle)
            bottom_card=max(floor+.055,bottom((a+b)*.5)+.045)
            box('door_card_'+suffix+'_'+str(i),(side*(w-.060)-.013,bottom_card,a+.09),(side*(w-.060)+.013,belt-.065,b-.06),PLASTIC,category=11,hinge=h,pivot=pivot,angle=angle)
            tube('door_armrest_'+suffix+'_'+str(i),[(side*(w-.095),belt-.24,a+.19),(side*(w-.095),belt-.24,b-.19)],.026,BLACK,category=11,hinge=h,pivot=pivot,angle=angle)
        # Correctly seated mirrors, style-specific silhouette details and arch trim.
        tube('mirror_mount_'+suffix,[(side*(w-.03),belt+.035,.64),(side*(w+.068),belt+.065,.62)],.019,BLACK,category=5)
        box('mirror_'+suffix,(side*(w+.07)-.073,belt+.025,.53),(side*(w+.07)+.073,belt+.126,.71),PLASTIC,category=5)
        box('mirror_glass_'+suffix,(side*(w+.07)-.056,belt+.043,.526),(side*(w+.07)+.056,belt+.111,.531),ALLOY,category=5)
        for axle,corner in ((1.35,'f'),(-1.30,'r')):
            start=math.asin((.435-.34)/.444)
            points=[(side*(w+.002),.34+.444*math.sin(start+(math.pi-2*start)*i/40),axle+.444*math.cos(start+(math.pi-2*start)*i/40)) for i in range(41)]
            tube('arch_trim_'+corner+suffix,points,.010 if name not in ('suv','van') else .024,BLACK)
            tube('sport_arch_'+corner+suffix,[(x+side*.018,y+.006,z) for x,y,z in points],.018,PAINT,group=5,variant=2)
        if name=='suv':
            tube('roof_rail_'+suffix,[(side*(w-.16),roof+.035,rr+.08),(side*(w-.16),roof+.105,rr+.18),(side*(w-.16),roof+.105,rf-.09),(side*(w-.16),roof+.025,rf-.035)],.023,ALLOY)
            box('suv_rocker_guard_'+suffix,(side*(w+.01)-.018,.46,-.82),(side*(w+.01)+.018,.57,.84),BLACK)
        if name=='van':
            tube('sliding_track_'+suffix,[(side*(w+.025),belt+.06,-2.2),(side*(w+.025),belt+.06,-.31)],.012,ALLOY)
            tube('roof_gutter_'+suffix,[(side*(w-.07),roof+.018,rr),(side*(w-.07),roof+.018,rf)],.015,BLACK)
        box('sport_skirt_'+suffix,(side*(w+.021)-.016,.41,-.84),(side*(w+.021)+.016,.485,.84),PLASTIC,group=5,variant=2)
    # Roof panel with a shallow crown and chamfered rails, not a scaled rectangular sedan cabin.
    roof_verts=[]
    for z in (rr-.022,rf+.022):
        for x,y in [(-w+.148,roof-.010),(-w+.23,roof+.012),(0,roof+.024),(w-.23,roof+.012),(w-.148,roof-.010)]:roof_verts.append((x,y,z))
    make('roof',roof_verts,[(i,i+1,i+6,i+5) for i in range(4)]+[(0,5,9,4),(0,1,2,3,4),(5,6,7,8,9)],PAINT,category=1)
    # Windscreen and seal meet the A pillars and raised van/SUV dashboards.
    windshield=[(-w+.061,belt+.018,cf-.012),(w-.061,belt+.018,cf-.012),(w-.171,roof-.035,rf+.009),(-w+.171,roof-.035,rf+.009)]
    make('windshield',windshield,[(0,1,2,3)],GLASS,category=4)
    tube('windshield_seal',windshield+[windshield[0]],.014,BLACK,category=5)
    for x in (-.42,.13):tube('wiper_'+str(x),[(x,belt+.033,cf+.005),(x+.27,belt+.075,cf-.055)],.009,BLACK,category=5)
    # Bonnet is kept above the tallest of all existing cylinder heads and induction kits.
    hood_back=cf-.005;hood_front=nose-.085
    hood_y=max(belt+.015,1.057);pivot=(0,hood_y,hood_back)
    hood_vertices=[]
    for z in (hood_back,1.62,hood_front):
        for x in (-.77,-.65,0,.65,.77):hood_vertices.append((x,hood_y+(.030 if abs(x)<.67 else 0),z))
    make('hood',hood_vertices,[(r*5+i,r*5+i+1,(r+1)*5+i+1,(r+1)*5+i) for r in range(2) for i in range(4)],PAINT,category=2,hinge=5,pivot=pivot,angle=-65)
    for side in (-1,1):
        tube('hood_edge_'+str(side),[(side*.772,hood_y,hood_back),(side*.772,hood_y,hood_front)],.007,BLACK,hinge=5,pivot=pivot,angle=-65)
        make('bonnet_shoulder_'+str(side),[(side*.778,hood_y,cf),(side*w,belt,cf),(side*w,belt,nose-.065),(side*.778,hood_y,nose-.065)],[(0,1,2,3)],PAINT)
    tube('bonnet_front_seal',[(-.775,hood_y,hood_front),(0,hood_y+.031,hood_front),(.775,hood_y,hood_front)],.007,BLACK,hinge=5,pivot=pivot,angle=-65)
    box('front_fascia',(-w+.025,.76,nose-.066),(w-.025,hood_y+.010,nose-.03),PAINT)
    for variant in (1,2):
        # Chamfered bumper cheek shape; the sports coupe receives a wider low grille.
        low=.395 if variant==2 else .44
        box('front_bumper',(-w+.012,low,nose-.12),(w-.012,.765,nose),PLASTIC if name in ('suv','van') else PAINT,group=5,variant=variant)
        box('front_lower_grille',(-.51,.49,nose+.001),(.51,.675,nose+.015),BLACK,group=5,variant=variant)
        for zline in (.515,.55,.585,.62,.655):box('grille_slat_'+str(zline),(-.485,zline,nose+.014),(.485,zline+.007,nose+.019),PLASTIC,group=5,variant=variant)
        if variant==2:box('front_splitter',(-w-.02,.389,nose-.07),(w+.02,.413,nose+.041),BLACK,group=5,variant=2)
    # Unique lighting signatures: swept coupe strips, SUV rectangles, van vertical elements.
    for side in (-1,1):
        xx=side*(w-.25)
        height=.075 if name=='sports_car' else .16 if name=='van' else .11
        yy=.91 if name=='sports_car' else min(belt-.13,1.05)
        box('front_light_housing_'+str(side),(xx-.18,yy-height*.65,nose-.015),(xx+.18,yy+height*.65,nose+.016),BLACK,category=6)
        box('front_light_'+str(side),(xx-.155,yy-height*.40,nose+.017),(xx+.155,yy+height*.40,nose+.023),WHITE,category=6,kind=3)
        box('front_indicator_'+str(side),(xx-.14,yy-height*.55,nose+.024),(xx+.14,yy-height*.43,nose+.027),AMBER,category=6,kind=3)
    box('front_badge',(-.036,.94,nose+.022),(.036,1.002,nose+.03),CHROME,category=5)
    box('front_plate',(-.24,.685,nose+.018),(.24,.755,nose+.025),WHITE,category=5)
    box('rear_bumper',(-w+.02,.43,-tail),(w-.02,.78,-tail+.10),PLASTIC if name in ('suv','van') else PAINT)
    box('rear_plate_recess',(-.28,.79,-tail-.007),(.28,.91,-tail+.019),BLACK,category=5)
    box('rear_plate',(-.23,.815,-tail-.013),(.23,.89,-tail-.008),WHITE,category=5)
    for side in (-1,1):
        xx=side*(w-.16);yy=belt-.11;hh=.20 if name in ('suv','van') else .075
        box('rear_light_housing_'+str(side),(xx-.11,yy-hh/2,-tail-.006),(xx+.11,yy+hh/2,-tail+.022),BLACK,category=6)
        box('rear_light_'+str(side),(xx-.088,yy-hh*.36,-tail-.013),(xx+.088,yy+hh*.36,-tail-.007),RED,category=6,kind=3)
        box('rear_reverse_'+str(side),(xx-.068,yy-hh*.12,-tail-.017),(xx+.068,yy,-tail-.013),WHITE,category=6,kind=3)
    # Hatchbacks/SUVs lift their rear glazing and lower panel together; vans use barn doors.
    if name in ('hatchback','suv'):
        pivot=(0,roof-.01,rr);md=dict(hinge=6,pivot=pivot,angle=82)
        rear=[(-w+.09,belt+.025,cr),(w-.09,belt+.025,cr),(w-.18,roof-.042,rr-.012),(-w+.18,roof-.042,rr-.012)]
        make('rear_glass',rear,[(0,1,2,3)],GLASS,category=4,**md);tube('hatch_seal',rear+[rear[0]],.023,BLACK,**md)
        make('trunk_lid',[(-w+.075,.78,-tail+.025),(w-.075,.78,-tail+.025),(w-.075,belt+.018,cr+.008),(-w+.075,belt+.018,cr+.008)],[(0,1,2,3)],PAINT,category=2,**md)
        for side in (-1,1):make('hatch_side_'+str(side),[(side*(w-.075),.78,-tail+.025),(side*(w-.075),belt+.018,cr+.008),(side*(w-.18),roof-.042,rr-.012),(side*(w-.075),roof-.06,-tail+.065)],[(0,1,2),(0,2,3)],PAINT,category=2,**md)
        box('hatch_handle',(-.13,belt-.095,cr-.012),(.13,belt-.056,cr+.025),BLACK,category=5,**md)
        box('roof_spoiler',(-w+.12,roof+.002,rr-.145),(w-.12,roof+.042,rr+.020),PAINT,group=5,variant=2)
    elif name=='van':
        for side in (-1,1):
            h=9 if side<0 else 10;pivot=(side*(w-.07),1.2,-tail+.026);angle=105 if side<0 else -105
            a,b=(-w+.075,-.010) if side<0 else (.010,w-.075)
            box('barn_door_'+str(side),(a,.795,-tail+.017),(b,roof-.047,-tail+.044),PAINT,category=2,hinge=h,pivot=pivot,angle=angle)
            box('barn_inset_'+str(side),(a+.075,1.05,-tail+.009),(b-.075,roof-.15,-tail+.016),PLASTIC,category=5,hinge=h,pivot=pivot,angle=angle)
            box('barn_handle_'+str(side),(side*.095-.018,1.10,-tail-.015),(side*.095+.018,1.30,-tail+.008),BLACK,category=5,hinge=h,pivot=pivot,angle=angle)
        for side in (-1,1):
            side_poly('cargo_rear_quarter_'+str(side),side,[(w,belt,-tail+.06),(w,belt,rr),(w-.075,roof-.025,rr),(w-.075,roof-.025,-tail+.06)],category=1)
        box('rear_roof_header',(-w+.05,roof-.05,-tail+.03),(w-.05,roof+.004,rr),PAINT,category=1)
    else:
        rear=[(-w+.06,belt+.018,cr),(w-.06,belt+.018,cr),(w-.17,roof-.031,rr),(-w+.17,roof-.031,rr)]
        make('rear_glass',rear,[(0,1,2,3)],GLASS,category=4);tube('rear_glass_seal',rear+[rear[0]],.018,BLACK)
        for side in (-1,1):make('rear_deck_shoulder_'+str(side),[(side*.76,belt,cr),(side*w,belt,cr),(side*w,belt,-tail+.07),(side*.76,belt,-tail+.07)],[(0,1,2,3)],PAINT)
        box('trunk_lid',(-.752,belt-.012,-tail+.058),(.752,belt+.021,cr-.015),PAINT,category=2,hinge=6,pivot=(0,belt,cr-.015),angle=64)
        box('rear_lamp_panel',(-w+.08,.78,-tail+.025),(w-.08,belt-.012,-tail+.053),PAINT)
        box('rear_spoiler',(-.84,belt+.06,-tail+.22),(.84,belt+.10,-tail+.075),BLACK,group=5,variant=2)
    # Roof-rear transition surfaces close the C-pillar wedge and wheelhouse shoulder.
    if name in ('hatchback','suv'):
        for side in (-1,1):
            side_poly('rear_fixed_pillar_'+str(side),side,[(w-.02,belt,cr-.025),(w-.02,belt,-tail+.055),(w-.075,roof-.055,-tail+.065),(w-.15,roof-.018,rr)],category=1)
    copy_fitted(s)


def setup_scene(s):
    bpy.ops.wm.read_factory_settings(use_empty=True);materials.clear();body_objects.clear();body_chunks.clear()
    scene=bpy.context.scene;scene.name='APA_'+s['id'];scene.unit_settings.system='METRIC'
    for name in ('Coachwork','Shared mechanical fit reference','Studio'):
        scene.collection.children.link(bpy.data.collections.new(name))
    scene['body_profile']=json.dumps(s,sort_keys=True)
    scene['authoring_notes']='Local Blender -Y is front, Z is up. APA hinge metadata uses runtime +Z front/+Y up. Shared references are not exported. Engine, battery and tire specifications remain unchanged.'
    return scene


def studio(scene,s):
    coll=scene.collection.children['Studio']
    me=bpy.data.meshes.new('Ground');me.from_pydata([(-20,-20,-.012),(20,-20,-.012),(20,20,-.012),(-20,20,-.012)],[],[(0,1,2,3)])
    ground=bpy.data.objects.new('Ground',me);coll.objects.link(ground);me.materials.append(mat(0xFF38444A))
    world=bpy.data.worlds.new('Studio world');world.use_nodes=True;world.node_tree.nodes['Background'].inputs[0].default_value=(.18,.20,.23,1);world.node_tree.nodes['Background'].inputs[1].default_value=.55;scene.world=world
    for name,location,energy,size in [('Key',(-4,-5,7),1350,5),('Fill',(5,-1,4),1100,4),('Rim',(0,5,6),1450,4)]:
        light=bpy.data.lights.new(name,'AREA');light.energy=energy;light.shape='DISK';light.size=size
        ob=bpy.data.objects.new(name,light);coll.objects.link(ob);ob.location=location;ob.rotation_euler=(Vector((0,0,.8))-ob.location).to_track_quat('-Z','Y').to_euler()
    cam=bpy.data.cameras.new('Review camera');ob=bpy.data.objects.new('Review camera',cam);coll.objects.link(ob);scene.camera=ob;cam.type='ORTHO';cam.ortho_scale=6.55
    scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
    scene.render.resolution_x=1120;scene.render.resolution_y=800;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.render.film_transparent=False
    scene.view_settings.view_transform='AgX'
    return ob


def main():
    OUT.mkdir(parents=True,exist_ok=True);ASSETS.mkdir(parents=True,exist_ok=True);REVIEWS.mkdir(parents=True,exist_ok=True)
    records=[]
    for s in styles[1:]:
        scene=setup_scene(s);author(s)
        path=OUT/(s['id']+'.mesh.gz');write(path,body_chunks)
        for c in canonical:
            if shared(c) and stock_visible(c):import_chunk(c,scene.collection.children['Shared mechanical fit reference'],True)
        camera=studio(scene,s)
        for view,loc in [('front',(-5.7,-7.0,3.8)),('rear',(5.7,7.0,3.5)),('side',(-8,0,2.5))]:
            camera.location=loc;camera.rotation_euler=(Vector((0,(s['tail']-s['nose'])/2,s['roof']*.49))-camera.location).to_track_quat('-Z','Y').to_euler()
            scene.render.filepath=str(REVIEWS/(s['id']+'-'+view+'.png'));bpy.ops.render.render(write_still=True)
        camera.location=(-5.7,-7,3.8);camera.rotation_euler=(Vector((0,0,s['roof']*.49))-camera.location).to_track_quat('-Z','Y').to_euler()
        # Hide stock references that represent mutually exclusive body trim upgrades.
        bpy.ops.wm.save_as_mainfile(filepath=str(ASSETS/(s['id']+'.blend')),compress=True)
        records.append(dict(s,bodyChunks=len(body_chunks),bodyTriangles=sum(len(c['vertices'])//3 for c in body_chunks),closedBounds=bounds(body_chunks),sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        print('BODY_MODEL_EXPORTED '+json.dumps(records[-1],sort_keys=True),flush=True)
    (ASSETS/'profiles.json').write_text(json.dumps({'schema':1,'coordinateFrame':'runtime +Z forward, +Y up','commonWheelbaseM':2.65,'bodies':records},indent=2)+'\n')

if __name__=='__main__':main()
