"""Blender export helpers: connected, selectable drivetrain parts in the runtime metre frame.

Type masks: 1 combustion, 2 battery EV, 4 parallel hybrid. Layout masks: 1 RWD, 2 FWD, 4 AWD.
Each mesh root names its real service slot. No changes to the original kit datablocks.
"""
orange=material('APA HV orange',(.80,.16,.015))
drive_report=[]
def ptmeta(key,types=7,layouts=7):
    name=f'pt|{types}|{layouts}|{key}'
    return meta(name,cat=17,group=1)
def ptbox(key,center,size,mat=metal,types=7,layouts=7):
    m=ptmeta(key,types,layouts);x,y,z=center;dx,dy,dz=size
    box(m['name'],(x,-z,y),(dx,dz,dy),mat,m)
def ptpipe(key,points,radius,mat=metal,types=7,layouts=7,sides=24):
    m=ptmeta(key,types,layouts)
    pipe(m['name'],[(x,-z,y) for x,y,z in points],radius,mat,m,sides)
def housing(key,center,size,types=7,layouts=7):
    ptbox(key,center,size,metal,types,layouts)
    x,y,z=center;dx,dy,dz=size
    for i in range(6):ptbox(key,(x-dx*.42+i*dx*.168,y+dy*.51,z),(.012,.022,dz*.86),black,types,layouts)
    for side in [-1,1]:
        for end in [-1,1]:ptpipe(key,[(x+side*dx*.4,y-dy*.51,z+end*dz*.35),(x+side*dx*.4,y-dy*.56,z+end*dz*.35)],.012,black,types,layouts,12)

# Longitudinal combustion gearbox; FWD transaxle ends before the cabin, RWD/AWD outputs aft.
housing('driveline.gearbox',(0,.40,.74),(.32,.24,.43),5,7)
ptpipe('driveline.clutch',[(0,.52,.95),(0,.52,1.04)],.13,metal,5,7,48)
ptpipe('driveline.gearbox',[(0,.52,.95),(0,.40,.87)],.06,metal,5,7)
housing('driveline.transfer',(0,.34,.40),(.35,.20,.22),5,4)
ptpipe('driveline.shaft',[(0,.31,.52),(0,.30,-1.30)],.033,metal,5,5)
for z in [.48,-1.16]:ptpipe('driveline.shaft',[(0,.30,z-.045),(0,.30,z+.045)],.052,black,5,5)
# Front final-drive link is part of the transaxle, not a rear propeller shaft on FWD.
ptpipe('driveline.gearbox',[(.16,.34,.76),(.16,.34,1.35),(0,.34,1.35)],.035,metal,5,6)
for axle,z in [('front',1.35),('rear',-1.30)]:
    layouts=6 if axle=='front' else 5
    key='driveline.front_differential' if axle=='front' else 'driveline.differential'
    housing(key,(0,.34,z),(.30,.22,.27),7,layouts)
    for side,x in [('l',-.83),('r',.83)]:
        cv='driveline.cv_'+('f' if axle=='front' else 'r')+side
        sign=-1 if x<0 else 1
        ptpipe(cv,[(sign*.14,.34,z),(x,.34,z)],.021,metal,7,layouts)
        for at in [.20,.68]:
            for ring in range(6):ptpipe(cv,[(sign*(at+ring*.012),.34,z),(sign*(at+ring*.012+.008),.34,z)],.047-ring*.002,black,7,layouts,20)
    # Two independent e-axles on AWD; only the selected axle on FWD/RWD. Same total vehicle power budget.
    motor_z=z+(.29 if axle=='front' else -.32)
    motor_y=.22 if axle=='front' else .34
    motor_radius=.097 if axle=='front' else .112
    ptpipe('traction.motor_'+axle,[(-.30,motor_y,motor_z),(.16,motor_y,motor_z)],motor_radius,metal,6,layouts,48)
    for i in range(8):ptpipe('traction.motor_'+axle,[(-.28+i*.055,motor_y,motor_z),(-.27+i*.055,motor_y,motor_z)],motor_radius+.005,black,6,layouts,32)
    housing('traction.reduction_'+axle,(.19,(.34+motor_y)*.5,(z+motor_z)*.5),(.16,.22,abs(z-motor_z)+.15),6,layouts)
    housing('traction.inverter_'+axle,(-.54 if axle=='front' else -.16,.51,motor_z),(.20 if axle=='front' else .34,.095,.21),6,layouts)
    ptpipe('traction.hv_cable',[(-.58,.44,motor_z),(-.40,.55,motor_z),(-.30,.55,motor_z)],.013,orange,6,layouts)
    drive_report.append({'axle':axle,'layout_mask':layouts,'hub_x':[-.83,.83],'axle_y':.34,'axle_z':z,'motor_center':[0,motor_y,motor_z]})
ptpipe('traction.hv_cable',[(-.58,.44,-1.65),(-.58,.44,1.64)],.015,orange,6,7)
housing('traction.contactor',(-.50,.47,.44),(.18,.09,.18),6,7)
housing('traction.dc_dc',(-.50,.48,.68),(.18,.10,.20),6,7)
ptpipe('traction.generator',[(.43,.60,1.20),(.64,.60,1.20)],.11,metal,4,7,48)
ptpipe('traction.hv_cable',[(.64,.67,1.20),(.64,.91,.98),(-.58,.91,.98),(-.58,.44,.90)],.013,orange,4,7)
(repo/'docs/drivetrain-geometry.json').write_text(json.dumps({'source':'tools/build_drivetrain_geometry.py','axles':drive_report,'scope':'Authored runtime e-axles, differentials, CV shafts and ICE gearbox/transfer/propeller paths. Masks select actual installed topology.'},indent=2)+'\n')
