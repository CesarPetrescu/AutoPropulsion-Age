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
import os
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


def make(name,vertices,faces,color=PAINT,category=3,group=-1,variant=0,hinge=0,pivot=(0,0,0),angle=0,kind=None,bevel=0.0):
    kind=(1 if color==PAINT else 2 if color==GLASS else 0) if kind is None else kind
    md=metadata(name,category,group,variant,hinge,pivot,angle,kind)
    me=bpy.data.meshes.new(name);me.from_pydata([native(v) for v in vertices],[],faces);me.update()
    bm=bmesh.new();bm.from_mesh(me)
    if bevel:
        bmesh.ops.bevel(bm,geom=list(bm.edges),offset=bevel,segments=1,affect='EDGES')
    bmesh.ops.triangulate(bm,faces=list(bm.faces))
    degenerate=[face for face in bm.faces if face.calc_area()<1e-12]
    if degenerate:bmesh.ops.delete(bm,geom=degenerate,context='FACES_ONLY')
    bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.normal_update()
    # Recalculation alone is ambiguous for a very thin closed glazing/ring component.
    # Enforce outward winding by signed volume on EACH connected closed component.
    remaining=set(bm.faces)
    while remaining:
        first=remaining.pop();component=[first];pending=[first]
        while pending:
            face=pending.pop()
            for edge in face.edges:
                for neighbour in edge.link_faces:
                    if neighbour in remaining:
                        remaining.remove(neighbour);component.append(neighbour);pending.append(neighbour)
        volume=sum(f.verts[0].co.dot(f.verts[1].co.cross(f.verts[2].co)) for f in component)/6
        if volume<0:bmesh.ops.reverse_faces(bm,faces=component)
    bm.normal_update()
    bm.to_mesh(me);bm.free()
    ob=bpy.data.objects.new(name,me);bpy.context.scene.collection.children['Coachwork'].objects.link(ob);me.materials.append(mat(color,kind))
    ob['apa_closed_shell']=True
    ob['apa_metadata']=json.dumps(md,sort_keys=True);ob['apa_color']=f'{color:08x}';ob.hide_render=variant>1;body_objects.append(ob)
    me.calc_loop_triangles();data=[]
    for tri in me.loop_triangles:
        a,b,c=(me.vertices[i].co for i in tri.vertices)
        normal=(b-a).cross(c-a)
        if normal.length_squared<4e-24:continue
        n=runtime(normal.normalized())
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
    if c['name'] in ('wheel_well_rl','wheel_well_rr','wheel_tub_rl','wheel_tub_rr'):return False
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
    ob['apa_reference_only']=reference;ob.hide_render=c['variant']>1
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
        local_dy=max(0,dy) if name.startswith(('front_seat_','floor_mat_')) or name.endswith('_pedal') else dy
        factor=(s['tail']+.025-1.30)/(2.30-1.30)
        for x,y,z,nx,ny,nz,color in c['vertices']:
            if cabin:y+=local_dy;z+=dz
            elif z< -1.30:
                z=-1.30+(z+1.30)*factor
                normal=Vector((nx,ny,nz/factor));normal.normalize();nx,ny,nz=normal
            verts.append((x,y,z,nx,ny,nz,color))
        c['vertices']=verts
        if cabin:c['py']+=local_dy;c['pz']+=dz
        elif c['pz']< -1.30:c['pz']=-1.30+(c['pz']+1.30)*factor
        body_chunks.append(c)
        import_chunk(c,bpy.context.scene.collection.children['Coachwork'])


def author(s):
    from shell_geometry import author_shell
    author_shell(s, globals())


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
            scene.render.filepath=str(REVIEWS/(s['id']+'-'+view+'.png'))
            if os.environ.get('APA_BODY_RENDER','1')!='0':bpy.ops.render.render(write_still=True)
        camera.location=(-5.7,-7,3.8);camera.rotation_euler=(Vector((0,0,s['roof']*.49))-camera.location).to_track_quat('-Z','Y').to_euler()
        # Hide stock references that represent mutually exclusive body trim upgrades.
        bpy.ops.wm.save_as_mainfile(filepath=str(ASSETS/(s['id']+'.blend')),compress=True)
        records.append(dict(s,solidParts=[json.loads(o['apa_metadata'])['name'] for o in bpy.context.scene.collection.children['Coachwork'].objects if o.get('apa_closed_shell',False)],bodyChunks=len(body_chunks),bodyTriangles=sum(len(c['vertices'])//3 for c in body_chunks),closedBounds=bounds(body_chunks),sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        print('BODY_MODEL_EXPORTED '+json.dumps(records[-1],sort_keys=True),flush=True)
    (OUT/'shell-surfaces.json').write_text(json.dumps({b['id']:sorted(set(b['solidParts'])) for b in records},indent=2)+'\n')
    (ASSETS/'profiles.json').write_text(json.dumps({'schema':1,'coordinateFrame':'runtime +Z forward, +Y up','commonWheelbaseM':2.65,'bodies':records},indent=2)+'\n')

if __name__=='__main__':main()
