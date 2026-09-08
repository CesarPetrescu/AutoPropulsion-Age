"""Native Blender geometry for the 42 powertrain choices. Executed by export_engine_runtime.py.
All coordinates are metres in the shared engine-bay frame. No imported generated images.
"""
gold=material('APA anodised bronze',(.46,.24,.055))
alloy=material('APA polished alloy',(.65,.69,.72))
purple=material('APA race anodising',(.24,.035,.33))

def ring(name,center,outer,inner,length,axis,mat,m,sides=24):
    c=Vector(center);d=Vector(axis).normalized();u=d.cross(Vector((0,0,1)))
    if u.length<.01:u=d.cross(Vector((1,0,0)))
    u.normalize();v=d.cross(u);verts=[];faces=[]
    for z,r in [(-length/2,outer),(length/2,outer),(-length/2,inner),(length/2,inner)]:
        for i in range(sides):verts.append(c+d*z+r*(u*math.cos(i*math.tau/sides)+v*math.sin(i*math.tau/sides)))
    for i in range(sides):
        j=(i+1)%sides
        faces.extend([(i,j,sides+j,sides+i),(2*sides+j,2*sides+i,3*sides+i,3*sides+j),(j,i,2*sides+i,2*sides+j),(sides+i,sides+j,3*sides+j,3*sides+i)])
    return mesh(name,verts,faces,mat,m)

def bolts(name,c,dx,dy,m):
    for x in [-dx,dx]:
        for y in [-dy,dy]:pipe(name,[Vector(c)+Vector((x,y,0)),Vector(c)+Vector((x,y,.008))],.004,alloy,m,6)

for family in range(7):
    f=1<<family;portx=[.193,.29,.34,.285,.285,.285,.285][family];portz=[.87,.875,.78,.62,.62,.62,.62][family]
    ys=[-1.47,-1.34,-1.21]
    for choice in range(1,5):
        m=meta(f'hardware_intake_{choice}_f{family}',cat=25,family=f,slot=0,tier=choice)
        for y in ys:
            pipe('runner',[(-.425,y,.885),(-.37,y,.85),(-portx,y,portz)],.019+choice*.0015,alloy,m,16)
            ring('runner clamp',(-.402,y,.875),.026,.022,.012,(1,0,0),metal,m,12)
        if choice==1:
            box('OEM plenum',(-.425,-1.34,.91),(.09,.39,.095),black,m)
            box('airbox lower',(-.46,-1.06,.87),(.13,.125,.08),black,m)
            box('airbox lid',(-.46,-1.06,.918),(.139,.135,.014),metal,m);bolts('airbox screws',(-.46,-1.06,.926),.05,.048,m)
        elif choice==2:
            pipe('polished plenum',[(-.425,-1.52,.91),(-.425,-1.16,.91)],.048,metal,m,24)
            for z in [.843,.858,.873,.888,.903,.918]:ring('cone pleat',(-.46,-1.065,z),.050-(z-.843)*.22,.036-(z-.843)*.22,.010,(0,0,1),red,m)
            ring('filter cap',(-.46,-1.065,.928),.031,.010,.012,(0,0,1),alloy,m)
        elif choice==3:
            pipe('throttle balance rail',[(-.43,-1.53,.89),(-.43,-1.14,.89)],.018,metal,m)
            for y in ys:
                ring('individual throttle body',(-.435,y,.921),.033,.022,.055,(1,0,0),gold,m)
                ring('trumpet bellmouth',(-.47,y,.921),.042,.034,.023,(1,0,0),alloy,m)
                pipe('butterfly shaft',[(-.43,y-.035,.92),(-.43,y+.035,.92)],.004,metal,m,8)
            box('ITB sealed air feed',(-.48,-1.065,.88),(.09,.10,.075),black,m)
        else:
            pipe('ram plenum',[(-.435,-1.53,.912),(-.435,-1.145,.912)],.064,alloy,m,32)
            for y in [-1.51,-1.42,-1.33,-1.24,-1.16]:ring('plenum rib',(-.435,y,.912),.067,.063,.007,(0,1,0),gold,m)
            box('ram filter',(-.47,-1.055,.88),(.12,.11,.085),black,m)
        pipe('air filter connection',[(-.45,-1.10,.885),(-.425,-1.19,.91)],.025,black,dict(m,induction=1),16)

        m=meta(f'hardware_fuel_system_{choice}_f{family}',cat=26,family=f,slot=1,tier=choice)
        tint=[metal,blue,gold,purple][choice-1]
        pipe('injector rail',[(-.355,-1.51,.965),(-.355,-1.115,.965)],.009+choice*.002,tint,m,16)
        for y in ys:
            pipe('injector body',[(-.355,y,.952),(-portx,y,portz)],.007+choice*.002,metal,m,12)
            ring('injector collar',(-.35,y,.95),.016,.010,.013,(0,0,1),tint,m,12)
        if choice>=3:
            pipe('fuel return',[(-.355,-1.12,.965),(-.32,-1.10,.97),(-.32,-1.53,.97),(-.355,-1.53,.965)],.006,black,m)
            pipe('pressure regulator',[(-.34,-1.56,.94),(-.34,-1.56,.981)],.021,tint,m,16)
        if choice==4:
            box('fuel distribution block',(-.30,-1.55,.977),(.045,.035,.025),purple,m);bolts('fuel studs',(-.30,-1.55,.991),.015,.01,m)

        m=meta(f'hardware_ignition_{choice}_f{family}',cat=27,family=f,slot=2,tier=choice)
        if choice==1:box('OEM coil pack',(-.43,-1.685,.955),(.12,.085,.055),black,m)
        else:
            for y in [-1.72,-1.685,-1.65]:
                pipe('individual coil',[(-.44,y,.936),(-.44,y,.982)],.018,[red,blue,purple][choice-2],m,16)
                ring('coil terminal',(-.44,y,.986),.010,.004,.005,(0,0,1),gold,m,12)
            if choice>=3:
                box('ignition controller',(-.52,-1.685,.959),(.045,.10,.048),[blue,purple][choice-3],m)
                for y in [-1.725,-1.705,-1.685,-1.665,-1.645]:box('CDI cooling fin',(-.52,y,.987),(.047,.005,.008),alloy,m)
        for y in ys:pipe('plug leads',[(-.43,-1.68,.976),(-.43,y,.99),(-portx,y,portz+.035)],.0035+choice*.0003,black,m,8)

        m=meta(f'hardware_exhaust_{choice}_f{family}',cat=30,family=f,slot=6,tier=choice)
        # The collector adapts to the common turbine inlet on turbo layouts.
        if choice==1:
            pipe('cast log',[(portx,-1.49,portz),(.39,-1.49,.735),(.39,-1.14,.735)],.028,black,m,12)
        else:
            for j,y in enumerate(ys):
                pipe('equal length runner',[(portx,y,portz),(.36+.013*j,y,.76),(.37+.014*j,y+.05*(choice-1),.64),(.39,-1.04,.55)],.014+choice*.002,[metal,alloy,gold][choice-2],m,16)
                ring('header flange',(portx,y,portz),.028,.018,.009,(1,0,0),alloy,m,12)
        pipe('NA downpipe',[(.39,-1.14,.735),(.39,-1.05,.53),(.35,-.90,.29)],.024+choice*.001,metal,dict(m,induction=101),16)

        m=meta(f'hardware_internals_{choice}_f{family}',cat=20,family=f,slot=4,tier=choice)
        # Bearing girdle and visible rod-cap hardware below the block; retained in cutaway inspection.
        for j,y in enumerate([-1.58,-1.44,-1.30,-1.16]):
            tint=[metal,gold,alloy,purple][choice-1]
            box('main bearing cap',(0,y,.435),(.18+choice*.007,.023+choice*.004,.023),tint,m)
            bolts('cap fasteners',(0,y,.447),.074,.009,m)
            if choice==4:pipe('billet girdle rail',[(-.095,-1.63,.449),(-.095,-1.11,.449)],.008,purple,m,8)
        if choice==3:
            for y in ys:ring('compression crown',(0,y,.79 if family<3 else .64),.041,.028,.014,(0,0,1),alloy,m,20)

        m=meta(f'hardware_headwork_{choice}_f{family}',cat=22,family=f,slot=8,tier=choice)
        if family<3:
            # Cam drive gears on the front of each head; lobe markers in the inspection view.
            z=[.946,.94,.83][family]
            for x in [-.075,.075]:
                ring('cam sprocket',(x,-1.725,z),.038+choice*.002,.022,.018,(0,1,0),[metal,gold,blue,purple][choice-1],m,24)
                for y in ys:pipe('cam lobe',[(x,y-.007,z-.035),(x,y+.007,z-.035)],.013+choice*.003,alloy,m,16)
        else:
            for y in ys:
                ring('rotary intake port',( -portx-.007,y,portz),.023+choice*.003,.015+choice*.003,.016,(1,0,0),[metal,gold,blue,purple][choice-1],m,24)
                if choice>=3:box('port bridge',(-portx-.016,y,portz),(.012,.009,.034),alloy,m)

# Cooling cores occupy the same radiator mount; all variants are mutually exclusive.
for choice in range(2,5):
    m=meta(f'hardware_cooling_{choice}',cat=29,slot=3,tier=choice)
    box('radiator core',(0,-1.93,.763),(1.08,.055+(choice-2)*.008,.42),black,m)
    for x in [-.545,.545]:box('radiator side tank',(x,-1.93,.763),(.035,.083,.45),alloy,m)
    for z in [.565+i*.019 for i in range(22)]:box('radiator fin',(0,-1.977,z),(1.055,.006,.005),alloy,m)
    fans=[0] if choice==2 else [-.26,.26]
    for x in fans:
        ring('fan shroud',(x,-1.878,.76),.18 if choice==2 else .16,.151 if choice==2 else .137,.023,(0,1,0),black,dict(m,name=f'fan_shroud_{choice}_{x}'),32)
        fm=dict(m,name=f'cooling_fan_{choice}_{x}',pivot=xyz((x,-1.88,.76)))
        pipe('fan hub',[(x,-1.895,.76),(x,-1.86,.76)],.033,metal,fm,20)
        for j in range(8):
            a=j*math.tau/8;c=(x+math.cos(a)*.095,-1.88,.76+math.sin(a)*.095)
            ob=box('electric fan blade',c,(.042,.008,.078),black,fm)
    if choice==4:
        pipe('radiator cap',[(.45,-1.93,.993),(.45,-1.93,1.006)],.022,purple,m,16)

for choice in range(1,5):
    m=meta(f'hardware_flywheel_{choice}',cat=20,slot=7,tier=choice)
    radius=[.118,.111,.103,.108][choice-1];length=[.045,.030,.022,.027][choice-1]
    ring('flywheel disc',(0,-.94,.53),radius,.036,length,(0,1,0),[metal,gold,alloy,purple][choice-1],m,48)
    ring('starter ring gear',(0,-.94,.53),radius+.006,radius-.006,.013,(0,1,0),alloy,m,48)
    for j in range(8):
        a=j*math.tau/8;c=(.072*math.cos(a),-.965,.53+.072*math.sin(a))
        ring('flywheel bolt',c,.009,.004,.01,(0,1,0),black,m,12)
    if choice>1:
        for j in range(10+choice*2):
            a=j*math.tau/(10+choice*2);c=(.092*math.cos(a),-.955,.53+.092*math.sin(a))
            ring('lightening recess',c,.009,.005,.012,(0,1,0),black,m,12)

    m=meta(f'hardware_oil_system_{choice}',cat=28,slot=9,tier=choice)
    box('oil sump',(0,-1.35,.355),(.34,.45,.07 if choice<4 else .038),black,m)
    for y in [-1.53,-1.44,-1.35,-1.26,-1.17]:box('sump rib',(0,y,.316),(.32,.012,.01),alloy,m)
    pipe('oil filter',[(-.18,-1.62,.47),(-.18,-1.62,.54)],.033,[metal,gold,blue,purple][choice-1],m,20)
    if choice>=2:
        pipe('oil pump',[(-.23,-1.57,.50),(-.23,-1.63,.50)],.034,alloy,m,20)
        bolts('sump fasteners',(0,-1.35,.398),.14,.19,m)
    if choice>=3:
        box('oil cooler',(0,-2.09,.435),(.50,.037,.11),metal,m)
        for z in [.398,.417,.436,.455,.474]:box('oil cooler fin',(0,-2.116,z),(.49,.009,.006),black,m)
        for x in [-.23,.23]:pipe('oil braided hose',[(x,-2.09,.435),(x*2.7,-2.02,.47),(x*2.7,-1.57,.50),(x,-1.60,.50)],.009,black,m,12)
    if choice==4:
        pipe('dry sump tank',[(-.63,-1.32,.51),(-.63,-1.32,.79)],.049,alloy,m,32)
        ring('tank strap',(-.63,-1.32,.66),.054,.049,.022,(0,0,1),black,m)
        pipe('oil cap',[(-.63,-1.32,.80),(-.63,-1.32,.815)],.019,purple,m,16)
        pipe('scavenge line',[(-.63,-1.32,.52),(-.61,-1.58,.51),(-.23,-1.61,.50)],.010,black,m,12)

def compressor(name,center,radius,length,m):
    x,y,z=center
    ring(name+' snail',center,radius,.030,length,(0,1,0),alloy,m,40)
    ring(name+' inlet',(x,y-length/2-.015,z),.042,.032,.04,(0,1,0),metal,m,24)
    for j in range(12):
        a=j*math.tau/12
        pipe('compressor blade',[(x+math.cos(a)*.011,y-length/2-.026,z+math.sin(a)*.011),(x+math.cos(a+.4)*.029,y-length/2-.021,z+math.sin(a+.4)*.029)],.0028,gold,m,6)
    pipe('compressor outlet',[(x+radius*.7,y,z+radius*.7),(x+radius*.7,y+.07,z+radius*.7)],.022,alloy,m,16)

for mode in [3,4,5,6]:
    m=meta(['','','','large_turbo','twin_turbo','roots_blower','twin_screw'][mode],cat=31 if mode in [3,4] else 32,slot=5,tier=mode,induction=1<<mode)
    if mode==3:
        compressor('large turbo',(.50,-1.28,.835),.096,.07,m)
        ring('turbine housing',(.50,-1.205,.835),.077,.027,.055,(0,1,0),black,m,32)
        pipe('turbo discharge',[(.55,-1.21,.90),(.48,-1.25,.85)],.023,alloy,m)
    elif mode==4:
        for y in [-1.28,-1.52]:
            compressor('twin turbo',(.49,y,.825),.068,.055,m)
            ring('twin turbine',(.49,y+.06,.825),.056,.024,.04,(0,1,0),black,m,28)
        pipe('twin charge merge',[(.49,-1.52,.89),(.55,-1.40,.90),(.48,-1.25,.85)],.022,alloy,m)
        pipe('second turbine feed',[(.43,-1.31,.80),(.41,-1.49,.80),(.49,-1.47,.825)],.021,metal,m)
    else:
        # Side-mounted positive-displacement unit with front belt drive and family port adapters.
        for x in [.457,.535]:pipe('blower lobe casing',[(x,-1.68,.839),(x,-1.38,.839)],.054,alloy,m,32)
        for y in [-1.67+i*.034 for i in range(9)]:
            for x in [.457,.535]:ring('casing cooling rib',(x,y,.839),.057,.053,.008,(0,1,0),[metal,gold][mode-5],m,24)
        box('blower end plate',(.496,-1.695,.839),(.16,.027,.11),[black,purple][mode-5],m)
        ring('blower pulley',(.48,-1.805,.86),.058,.02,.025,(0,1,0),black,m,32)
        pipe('blower drive shaft',[(.48,-1.80,.86),(.48,-1.68,.839)],.017,alloy,m)
        pipe('blower discharge',[(.52,-1.39,.89),(.59,-1.49,.90),(.48,-1.69,.86)],.023,alloy,m)
        if mode==6:
            for y in [-1.64,-1.55,-1.46]:box('twin screw brace',(.496,y,.897),(.11,.012,.012),purple,m)
