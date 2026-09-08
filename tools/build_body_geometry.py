"""Fitted shell closures, wheel wells and tires for the existing sedan rig.

Executed by export_engine_runtime.py with its native Blender mesh helpers.
All dimensions are metres in the original authoring frame (front = -Y, up = Z).
The source kit remains intact; these replace its incomplete runtime surfaces.
"""
paint=bpy.data.materials['SM_paint']
rubber=material('APA tire rubber',(.009,.011,.014))
undercoat=material('APA underbody coating',(.035,.043,.048))
steel=bpy.data.materials['SM_steel']
# A continuous panel behind the lamps/grille closes the old 12 cm holes between them.
box('front_fascia',(0,-2.127,.93),(1.79,.038,.195),paint,meta('front_fascia',cat=3,group=-1))
box('bonnet_front_seal',(0,-2.108,1.014),(1.74,.025,.010),black,meta('bonnet_front_seal',cat=3,group=-1))
box('rear_lamp_panel',(0,2.11,.938),(1.84,.036,.177),paint,meta('rear_lamp_panel',cat=3,group=-1))
for side in [-1,1]:
    # Floor-to-rocker returns and door aperture rebates remove see-through sill seams.
    box('floor_sill_return_'+str(side),(side*.837,.035,.386),(.14,1.68,.07),undercoat,meta('floor_sill_return_'+str(side),cat=1,group=-1))
    box('sill_closure_'+str(side),(side*.872,.035,.465),(.05,1.68,.08),paint,meta('sill_closure_'+str(side),cat=1,group=-1))
    box('rocker_return_'+str(side),(side*.89,.035,.43),(.09,1.68,.15),paint,meta('rocker_return_'+str(side),cat=1,group=-1))
    box('side_skirt_'+str(side),(side*.935,.0975,.405),(.07,1.605,.11),black,meta('side_skirt_'+str(side),cat=3,group=5,variant=1))
    box('sport_skirt_'+str(side),(side*.958,.0925,.397),(.13,1.605,.13),paint,meta('sport_skirt_'+str(side),cat=7,group=5,variant=2))
    for yy in [-.76,.26,1.024]:
        box('door_jamb_'+str(side)+'_'+str(yy),(side*.875,yy,.755),(.047,.024,.56),black,meta('door_jamb_'+str(side)+'_'+str(yy),cat=1,group=-1))
    for yy in [-.251,.269]:
        box('door_seal_'+str(side)+'_'+str(yy),(side*.890,yy,.756),(.014,.012,.56),black,meta('door_seal_'+str(side)+'_'+str(yy),cat=1,group=-1))
    for front in [True,False]:
        name='door_'+('front_' if front else 'rear_')+('left' if side<0 else 'right')
        p=lookup['sparkmotors:'+name]
        md=meta(name,cat=2,group=-1,hinge=hinges.index(name)+1,pivot=p['mount_world_m'],angle=p['hinge']['open_degrees'])
        if front:boundary=[(-.758,.471),(.254,.471),(.254,1.037),(-.758,1.037)]
        else:
            # The original rectangular rear door intersected the leading edge of the tire.
            radius=.425;begin=math.pi-math.asin((.471-.34)/radius);end=math.acos((1.023-1.30)/radius)
            boundary=[(.266,.471)]+[(1.30+radius*math.cos(begin+(end-begin)*i/12),.34+radius*math.sin(begin+(end-begin)*i/12)) for i in range(13)]+[(1.023,1.037),(.266,1.037)]
        n=len(boundary);verts=[(side*x,y,z) for x in [.8845,.9215] for y,z in boundary]
        faces=[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
        if side<0:faces=[tuple(reversed(f)) for f in faces]
        mesh(name,verts,faces,paint,md)

for corner,(x,y) in {'fl':(-.83,-1.35),'fr':(.83,-1.35),'rl':(-.83,1.30),'rr':(.83,1.30)}.items():
    side=-1 if x<0 else 1
    # Wheel well follows the opening, with a return to the inner wing. No dangling trim ends.
    vertices=[];segments=48;start=math.asin((.435-.34)/.421)
    inner=.49 if corner[0]=='f' else .55
    for xx,r in [(side*inner,.416),(side*.936,.416),(side*.936,.427),(side*inner,.427)]:
        for i in range(segments+1):
            a=start+(math.pi-2*start)*i/segments;vertices.append((xx,y+r*math.cos(a),.34+r*math.sin(a)))
    stride=segments+1
    faces=[(ring*stride+i,ring*stride+i+1,((ring+1)%4)*stride+i+1,((ring+1)%4)*stride+i) for ring in range(4) for i in range(segments)]
    faces.extend([(0,stride,2*stride,3*stride),(segments,3*stride+segments,2*stride+segments,stride+segments)])
    mesh('wheel_well_'+corner,vertices,faces,undercoat,meta('wheel_well_'+corner,cat=1,group=-1))
    # Inner tub wall behind the tire; the old empty arch exposed the opposite side of the bay.
    tub=[(side*(inner-.009),y+.427*math.cos(start+(math.pi-2*start)*i/48),.34+.427*math.sin(start+(math.pi-2*start)*i/48)) for i in range(49)]
    mesh('wheel_tub_'+corner,tub,[tuple(range(49))],undercoat,meta('wheel_tub_'+corner,cat=1,group=-1))
    for variant in [1,2]:
        if corner[0]=='r' and variant==2:continue
        group=5 if corner[0]=='f' else -1
        offset=.948 if variant==1 else 1.038
        points=[(side*offset,y+.421*math.cos(start+(math.pi-2*start)*i/48),.34+.421*math.sin(start+(math.pi-2*start)*i/48)) for i in range(49)]
        pipe('fender_lip_'+corner+'_'+str(variant),points,.008,black,meta('fender_lip_'+corner,cat=3,group=group,variant=variant if group==5 else 0),sides=6)
    for variant in [1,2]:
        name=('tire_' if variant==1 else 'slick_tire_')+corner
        md=meta(name,cat=13,group=2,variant=variant,pivot=(x,y,.34))
        # A closed 64-segment carcass seats over the 239 mm rim lip on both sides.
        profile=[(-.108,.233),(-.113,.245),(-.117,.286),(-.102,.313),(-.08,.334),(-.047,.338),(.047,.338),(.08,.334),(.102,.313),(.117,.286),(.113,.245),(.108,.233)]
        n=64;verts=[(x+dx,y+r*math.cos(i*math.tau/n),.34+r*math.sin(i*math.tau/n)) for dx,r in profile for i in range(n)]
        faces=[(k*n+i,k*n+(i+1)%n,((k+1)%len(profile))*n+(i+1)%n,((k+1)%len(profile))*n+i) for k in range(len(profile)) for i in range(n)]
        mesh(name,verts,faces,rubber,md)
        if variant==1:
            for band in [-.048,.048]:
                # Thin circumferential grooves stay below the tread crown.
                pipe(name+'_groove',[(x+band,y+.3382*math.cos(i*math.tau/64),.34+.3382*math.sin(i*math.tau/64)) for i in range(65)],.0017,black,md,sides=4)
        # Rim bead seats bridge the lip/barrel join; the five-lug pattern follows wheel spin.
        rm=meta(('rim_' if variant==1 else 'sport_rim_')+corner,cat=13,group=2,variant=variant,pivot=(x,y,.34))
        for i in range(5):
            a=i*math.tau/5;yy=y+.066*math.cos(a);zz=.34+.066*math.sin(a)
            pipe('wheel_lug_'+corner,[(x+side*.105,yy,zz),(x+side*.121,yy,zz)],.009,steel,rm,sides=6)
    # Load-bearing mount cups meet the tops of the struts instead of hovering above them.
    box('strut_tower_'+corner,(side*.614,y,.937),(.20,.23,.026),undercoat,meta('strut_tower_'+corner,cat=1,group=-1))

# Pressed floor reinforcement and inner wing closures, leaving the serviceable driveline exposed.
for side in [-1,1]:
    for yy in [-.52,.20,.85]:
        box('floor_rib_'+str(side)+'_'+str(yy),(side*.495,yy,.344),(.57,.033,.018),undercoat,meta('floor_rib_'+str(side)+'_'+str(yy),cat=1,group=-1))
    box('engine_inner_wing_'+str(side),(side*.719,-1.39,.757),(.018,1.14,.044),undercoat,meta('engine_inner_wing_'+str(side),cat=1,group=-1))
    box('rear_floor_return_'+str(side),(side*.82,1.79,.52),(.075,.66,.045),undercoat,meta('rear_floor_return_'+str(side),cat=1,group=-1))
box('front_undertray',(0,-1.96,.327),(1.18,.35,.018),undercoat,meta('front_undertray',cat=1,group=-1))
