"""Original low-poly vehicle geometry. Coordinates: metres, X right, Y up, Z forward.
The same indexed meshes feed Minecraft JSON, inventory icons and editable Blender scenes.
No downloaded meshes, textures or manufacturer trademarks are used.
"""
from __future__ import annotations
import math

PAINT = [0.035, 0.30, 0.46, 1]
STEEL = [0.56, 0.62, 0.68, 1]
DARK = [0.065, 0.085, 0.10, 1]
RUBBER = [0.035, 0.038, 0.045, 1]
GLASS = [0.10, 0.20, 0.25, 1]
RED = [0.58, 0.055, 0.03, 1]
BRASS = [0.63, 0.40, 0.16, 1]
WHITE = [0.93, 0.92, 0.78, 1]

class Mesh:
    def __init__(self, name: str):
        self.name, self.objects, self.locators = name, [], {}
    def add(self, name, vertices, faces, color=STEEL, group='body', pivot=(0, 0, 0)):
        self.objects.append(dict(name=name, vertices=[[round(float(c), 6) for c in v] for v in vertices],
                                 faces=faces, color=color, group=group, pivot=list(pivot)))
    def box(self, name, center, size, color=STEEL, group='body', pivot=(0, 0, 0)):
        x, y, z = center; a, b, c = [v / 2 for v in size]
        vertices = [(x-a,y-b,z-c),(x+a,y-b,z-c),(x+a,y+b,z-c),(x-a,y+b,z-c),
                    (x-a,y-b,z+c),(x+a,y-b,z+c),(x+a,y+b,z+c),(x-a,y+b,z+c)]
        self.add(name, vertices, [[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[0,1,5,4],[3,7,6,2]], color, group, pivot)
    def cylinder(self, name, center, radius, depth, axis='y', color=STEEL, segments=12, group='body', pivot=(0,0,0)):
        axis_index = {'x':0,'y':1,'z':2}[axis]
        cross = [i for i in range(3) if i != axis_index]
        vertices = []
        for side in (-1, 1):
            for i in range(segments):
                a = i * math.tau / segments; p = list(center)
                p[axis_index] += side * depth / 2
                p[cross[0]] += math.cos(a) * radius; p[cross[1]] += math.sin(a) * radius
                vertices.append(p)
        for side in (-1, 1):
            p = list(center); p[axis_index] += side * depth / 2; vertices.append(p)
        faces = []
        for i in range(segments):
            j = (i + 1) % segments
            faces.extend([[i,j,j+segments,i+segments],[2*segments,j,i],[2*segments+1,i+segments,j+segments]])
        self.add(name, vertices, faces, color, group, pivot)
    def torus(self, name, center, major, minor, axis='y', color=STEEL, n=16, m=6, group='body', pivot=(0,0,0)):
        ai = {'x':0,'y':1,'z':2}[axis]; cross = [i for i in range(3) if i != ai]
        vertices=[]
        for i in range(n):
            a=i*math.tau/n
            for j in range(m):
                b=j*math.tau/m; p=list(center); r=major+minor*math.cos(b)
                p[cross[0]]+=r*math.cos(a); p[cross[1]]+=r*math.sin(a); p[ai]+=minor*math.sin(b); vertices.append(p)
        faces=[[i*m+j,((i+1)%n)*m+j,((i+1)%n)*m+(j+1)%m,i*m+(j+1)%m] for i in range(n) for j in range(m)]
        self.add(name,vertices,faces,color,group,pivot)
    def document(self):
        return dict(format=1, name=self.name, units='metres', axes='x-right,y-up,z-forward', objects=self.objects, locators=self.locators)

def hatch() -> Mesh:
    m=Mesh('hatch_01')
    m.box('chassis_rails',(0,.37,0),(1.62,.15,3.95),DARK)
    m.box('sill_left',(-.83,.58,0),(.10,.27,1.63),PAINT)
    m.box('sill_right',(.83,.58,0),(.10,.27,1.63),PAINT)
    m.box('front_bulkhead',(0,.61,1.88),(1.72,.41,.35),PAINT)
    m.box('rear_bulkhead',(0,.66,-1.91),(1.72,.42,.30),PAINT)
    m.box('rear_deck',(0,.86,-1.48),(1.67,.16,.82),PAINT)
    for side in (-1,1):
        for z in (-1.25,1.25):
            m.box(f'arch_{side}_{z}',(side*.84,.84,z),(.16,.15,.90),PAINT)
    m.box('roof',(0,1.46,-.35),(1.44,.085,1.78),PAINT)
    m.add('windscreen',[(-.70,1.41,.51),(.70,1.41,.51),(.76,.91,1.00),(-.76,.91,1.00)],[[0,1,2,3]],GLASS)
    m.add('rear_glass',[(-.68,1.41,-1.20),(.68,1.41,-1.20),(.76,.97,-1.67),(-.76,.97,-1.67)],[[0,1,2,3]],GLASS)
    for side,label in ((-1,'left'),(1,'right')):
        x=side*.78
        m.add(f'side_glass_{label}',[(x,.96,-1.22),(side*.71,1.40,-1.15),(side*.71,1.40,.47),(x,.96,.91)],[[0,1,2,3]],GLASS)
        m.box(f'b_pillar_{label}',(side*.755,1.19,-.30),(.075,.46,.075),DARK)
        m.box(f'door_{label}',(side*.846,.77,-.10),(.065,.37,1.55),PAINT,'door_'+label,(side*.86,.85,.69))
        m.box(f'handle_{label}',(side*.889,.87,-.62),(.03,.036,.18),DARK,'door_'+label,(side*.86,.85,.69))
        m.box(f'mirror_{label}',(side*.995,1.05,.64),(.20,.12,.24),PAINT)
        m.box(f'headlamp_{label}',(side*.57,.77,2.065),(.39,.13,.035),WHITE)
        m.box(f'taillamp_{label}',(side*.60,.81,-2.072),(.32,.15,.035),RED)
    m.box('hood',(0,.91,1.40),(1.60,.065,1.15),PAINT,'hood',(0,.94,.825))
    m.box('front_bumper',(0,.48,2.045),(1.80,.16,.18),DARK)
    m.box('grille',(0,.68,2.083),(.71,.15,.022),DARK)
    for x in (-.26,-.13,0,.13,.26): m.box('grille_fin_'+str(x),(x,.68,2.10),(.018,.125,.018),STEEL)
    m.box('rear_bumper',(0,.48,-2.045),(1.80,.14,.18),DARK)
    m.box('numberplate_front',(0,.50,2.145),(.39,.10,.014),STEEL)
    m.box('numberplate_rear',(0,.69,-2.098),(.39,.11,.014),STEEL)
    m.box('engine_block',(0,.68,1.28),(.66,.34,.66),STEEL,'engine')
    m.box('engine_cover',(0,.89,1.24),(.55,.09,.61),RED,'engine')
    for z in (1.03,1.17,1.31,1.45): m.cylinder('coil_'+str(z),(.17,.955,z),.037,.06,'y',DARK,8,'engine')
    m.box('radiator_in_car',(0,.65,1.77),(.90,.33,.07),DARK,'engine')
    m.cylinder('intake_pipe',(-.47,.80,1.27),.065,.47,'z',DARK,10,'engine')
    m.box('battery_in_car',(.49,.73,.98),(.24,.23,.25),DARK,'engine')
    for x in (-.39,.39):
        m.box('seat_base_'+str(x),(x,.59,-.20),(.44,.16,.52),DARK)
        m.box('seat_back_'+str(x),(x,.93,-.46),(.43,.59,.15),DARK)
        m.box('headrest_'+str(x),(x,1.30,-.47),(.28,.18,.13),DARK)
    m.box('rear_bench',(0,.72,-1.08),(1.20,.32,.48),DARK)
    m.box('dashboard',(0,1.02,.69),(1.43,.19,.23),DARK)
    m.torus('steering_wheel',(-.39,1.04,.42),.15,.025,'z',DARK)
    for x,label in ((-.87,'l'),(.87,'r')):
        for z,end in ((1.25,'f'),(-1.25,'r')):
            group='wheel_'+end+label; pivot=(x,.34,z)
            m.torus(group+'_tire',pivot,.250,.071,'x',RUBBER,20,8,group,pivot)
            m.cylinder(group+'_rim',pivot,.195,.185,'x',STEEL,16,group,pivot)
            outer=x+math.copysign(.099,x)
            m.cylinder(group+'_hub',(outer,.34,z),.055,.018,'x',DARK,10,group,pivot)
            for a in range(5):
                t=a*math.tau/5
                m.cylinder(group+'_bolt_'+str(a),(outer+math.copysign(.012,x),.34+math.cos(t)*.095,z+math.sin(t)*.095),.018,.022,'x',DARK,6,group,pivot)
            m.locators[group]=list(pivot)
    m.cylinder('exhaust_tip',(.56,.39,-2.08),.062,.23,'z',STEEL,12)
    m.box('spoiler',(0,1.20,-1.64),(1.49,.055,.22),DARK)
    for x in (-.48,.48): m.box('spoiler_bracket_'+str(x),(x,1.09,-1.62),(.045,.21,.10),DARK)
    m.locators.update(engine_mount=[0,.67,1.25],seat_driver=[-.39,.63,-.20],seat_passenger=[.39,.63,-.20],hood_hinge=[0,.94,.825],door_left_hinge=[-.86,.85,.69],door_right_hinge=[.86,.85,.69])
    return m

def component(kind: str) -> Mesh:
    m=Mesh(kind)
    if kind.startswith('engine_block'):
        rotary='rotary' in kind
        m.box('cast_block',(0,.22,0),(.55,.40,.74 if not rotary else .48),STEEL)
        if rotary:
            for z in (-.16,.16): m.torus('housing_'+str(z),(0,.27,z),.27,.055,'z',STEEL)
        else:
            for z in (-.27,-.09,.09,.27): m.torus('bore_'+str(z),(0,.43,z),.071,.012,'y',DARK)
            m.box('sump',(0,-.02,0),(.50,.11,.66),DARK)
    elif kind in {'piston','cylinder_liner','rotor_housing','side_housing'}:
        if 'housing' in kind:
            m.torus('housing',(0,.25,0),.23,.055,'z',STEEL,24,8)
            if kind=='rotor_housing': m.torus('water_jacket',(0,.25,.035),.28,.018,'z',DARK)
        else:
            m.cylinder('skirt',(0,.13,0),.12,.24,'y',STEEL,20)
            for y in (.18,.205,.23): m.torus('ring_groove_'+str(y),(0,y,0),.12,.008,'y',DARK)
            m.cylinder('wrist_pin',(0,.08,0),.03,.29,'x',DARK,12)
    elif kind in {'connecting_rod','rod_bearing','main_bearing','main_bearing_cap'}:
        if kind=='connecting_rod':
            m.torus('big_end',(0,.06,0),.07,.019,'z',STEEL)
            m.box('forged_beam',(0,.20,0),(.055,.24,.035),STEEL)
            m.torus('small_end',(0,.34,0),.035,.013,'z',STEEL)
        else: m.torus('bearing_shell',(0,.09,0),.08,.016,'z',BRASS if 'bearing' in kind else STEEL)
    elif kind in {'crankshaft','eccentric_shaft','camshaft'}:
        m.cylinder('shaft',(0,.14,0),.04,.84,'z',STEEL)
        for i,z in enumerate((-.30,-.10,.10,.30)):
            off=.075 if i%2 else -.075
            m.cylinder('journal_'+str(i),(off,.14,z),.055,.095,'z',STEEL)
            m.cylinder('counterweight_'+str(i),(0,.14-off,z-.045),.105,.035,'z',DARK)
    elif kind in {'piston_ring','valve_seal','head_gasket','apex_seal','harmonic_damper','thermostat','stationary_gear'}:
        if kind=='head_gasket':
            for z in (-.23,-.075,.075,.23): m.torus('fire_ring_'+str(z),(0,.02,z),.069,.010,'y',DARK)
            m.box('gasket_bridge',(.084,.02,0),(.035,.008,.60),STEEL)
            m.box('gasket_bridge_left',(-.084,.02,0),(.035,.008,.60),STEEL)
        elif kind=='apex_seal': m.box('seal',(0,.04,0),(.20,.035,.014),STEEL)
        else: m.torus('ring',(0,.10,0),.085,.017,'y',DARK if 'seal' in kind else STEEL)
    elif kind in {'head_bolt','main_bearing_bolt','wrist_pin','piston_cooling_nozzle','spark_plug'}:
        m.cylinder('shank',(0,.13,0),.02,.24,'y',STEEL)
        m.cylinder('hex_head',(0,.27,0),.035,.04,'y',STEEL,6)
        for y in (.02,.04,.06,.08): m.torus('thread_'+str(y),(0,y,0),.02,.004,'y',DARK,12,4)
    elif kind in {'intake_valve','exhaust_valve','valve_lifter','valve_spring','spring','coilover','damper'}:
        if kind in {'valve_spring','spring','coilover'}:
            for y in [i*.045 for i in range(7)]: m.torus('coil_'+str(y),(0,y,0),.064,.012,'y',RED,12,5)
            m.cylinder('damper_rod',(0,.15,0),.018,.41,'y',STEEL)
            if kind=='coilover': m.cylinder('adjuster',(0,.035,0),.088,.038,'y',BRASS)
        else:
            m.cylinder('stem',(0,.20,0),.018,.40,'y',STEEL)
            m.cylinder('head',(0,.035,0),.075,.035,'y',STEEL,16)
    elif kind in {'turbocharger','supercharger','wastegate','bov'}:
        if kind=='supercharger':
            m.box('compressor',(0,.16,0),(.31,.25,.43),STEEL)
            for x in (-.08,.08): m.cylinder('rotor_'+str(x),(x,.19,0),.069,.46,'z',DARK)
        else:
            m.torus('compressor_volute',(-.08,.17,0),.11,.060,'z',STEEL,18,8)
            m.torus('turbine_volute',(.10,.17,-.14),.09,.052,'z',DARK,16,6)
            m.cylinder('inlet',(-.08,.17,.09),.07,.14,'z',STEEL)
            m.cylinder('outlet',(-.21,.26,0),.048,.17,'x',STEEL)
    elif kind in {'radiator','intercooler','cylinder_head'}:
        size=(.62,.38,.10) if kind!='cylinder_head' else (.38,.16,.66)
        m.box('core',(0,.21,0),size,STEEL)
        if kind!='cylinder_head':
            for i in range(12): m.box('fin_'+str(i),(0,.05+i*.029,.059),(.56,.009,.015),DARK)
            for x in (-.31,.31): m.cylinder('tank_'+str(x),(x,.21,0),.045,.40,'y',DARK)
        else:
            for z in (-.24,-.08,.08,.24): m.cylinder('port_'+str(z),(.205,.20,z),.045,.035,'x',DARK)
    elif kind in {'rim','tire','brake_rotor','flywheel','clutch','final_drive','camshaft_gear'}:
        if kind=='tire': m.torus('tire',(0,.32,0),.25,.07,'x',RUBBER,20,8)
        else:
            r=.21 if kind in {'rim','brake_rotor','flywheel','clutch'} else .12
            m.cylinder('disc',(0,r,0),r,.055,'z',STEEL,24)
            m.cylinder('hub',(0,r,.045),r*.28,.05,'z',DARK)
            for i in range(12):
                a=i*math.tau/12
                m.cylinder('fastener_'+str(i),(math.cos(a)*r*.73,r+math.sin(a)*r*.73,.035),r*.05,.023,'z',DARK,6)
    elif kind in {'intake_manifold','exhaust_header','exhaust_system','water_pipe','boost_pipe','charge_pipe','rotary_intake_port','rotary_exhaust_port'}:
        m.cylinder('collector',(0,.10,0),.065,.66,'z',DARK if 'exhaust' in kind else STEEL)
        for z in (-.23,-.075,.075,.23):
            m.cylinder('runner_'+str(z),(.10,.19,z),.041,.27,'x',STEEL)
            m.cylinder('flange_'+str(z),(.245,.19,z),.060,.022,'x',STEEL,8)
    elif kind in {'fuel_tank','nitrous_bottle','nitrous_kit','water_meth','water_reservoir','jerry_can'}:
        if 'nitrous' in kind:
            m.cylinder('bottle',(0,.23,0),.115,.44,'y',PAINT,16)
            m.cylinder('valve',(0,.49,0),.028,.085,'y',BRASS,8)
        else:
            m.box('reservoir',(0,.20,0),(.36,.38,.25),STEEL if kind=='fuel_tank' else PAINT)
            m.cylinder('cap',(.10,.415,0),.048,.03,'y',DARK)
    elif kind in {'manual_gearbox','sequential_gearbox','automatic_gearbox','dct_gearbox','cvt_gearbox','differential','transfer_case'}:
        m.cylinder('bellhousing',(0,.23,.20),.22,.17,'z',STEEL,12)
        m.box('cast_case',(0,.19,-.06),(.32,.31,.42),STEEL)
        m.cylinder('input_shaft',(0,.23,.33),.03,.16,'z',DARK)
        m.cylinder('output_shaft',(0,.20,-.35),.035,.25,'z',DARK)
        for z in (-.19,-.10,0,.10): m.box('rib_'+str(z),(0,.36,z),(.32,.025,.023),DARK)
    elif kind=='rotor':
        vertices=[]
        for z in (-.065,.065):
            vertices += [(math.cos(a*math.tau/3)*.24,.25+math.sin(a*math.tau/3)*.24,z) for a in range(3)]
        m.add('triangular_rotor',vertices,[[0,2,1],[3,4,5],[0,1,4,3],[1,2,5,4],[2,0,3,5]],STEEL)
        m.cylinder('eccentric_bearing',(0,.25,.075),.085,.035,'z',BRASS)
    elif kind in {'seat','dashboard','steering_wheel','shifter','door_left','door_right','hood','trunk','fender','bumper_front','bumper_rear','side_skirt','spoiler','splitter','mirror','headlight','taillight','roll_cage'}:
        if kind=='seat':
            m.box('cushion',(0,.18,0),(.45,.16,.48),DARK); m.box('backrest',(0,.54,-.18),(.45,.63,.14),DARK)
        elif kind=='steering_wheel': m.torus('grip',(0,.18,0),.17,.026,'z',DARK)
        elif kind=='shifter':
            m.cylinder('stick',(0,.15,0),.014,.30,'y',STEEL); m.cylinder('knob',(0,.32,0),.04,.06,'y',DARK)
        elif kind=='roll_cage':
            for x in (-.5,.5): m.cylinder('upright_'+str(x),(x,.5,0),.025,1,'y',STEEL)
            m.cylinder('crossbar',(0,1,0),.025,1,'x',STEEL)
        elif kind.startswith('door'):
            m.box('skin',(0,.27,0),(.06,.53,1.05),PAINT); m.box('window',(0,.76,0),(.035,.40,.89),GLASS)
        elif kind in {'headlight','taillight'}:
            m.box('housing',(0,.10,0),(.40,.19,.16),DARK); m.box('lens',(0,.10,.09),(.36,.15,.025),WHITE if kind=='headlight' else RED)
        else: m.box('panel',(0,.12,0),(1.12,.14,.40),PAINT if kind not in {'splitter','dashboard'} else DARK)
    elif kind in {'garage_lift','dyno_rollers','fuel_pump_block','part_bench','paint_booth'}:
        if kind=='garage_lift':
            for x in (-1.35,1.35):
                m.box('column_'+str(x),(x,1.30,0),(.22,2.60,.32),PAINT)
                m.box('foot_'+str(x),(x,.04,0),(.52,.08,.72),DARK)
                m.box('arm_'+str(x),(x*.52,.30,0),(1.36,.12,.14),STEEL)
            m.box('crossbeam',(0,2.52,0),(2.72,.17,.23),PAINT)
        elif kind=='dyno_rollers':
            m.box('base',(0,.10,0),(2.4,.2,1.4),DARK)
            for z in (-.3,.3): m.cylinder('roller_'+str(z),(0,.27,z),.18,2.2,'x',STEEL,20)
        elif kind=='paint_booth':
            m.box('floor',(0,.035,0),(3,.07,4.5),DARK)
            for x in (-1.4,1.4):
                for z in (-2,2): m.box('post_'+str(x)+'_'+str(z),(x,1.35,z),(.12,2.7,.12),STEEL)
            m.box('roof',(0,2.7,0),(3,.1,4.5),STEEL)
        else:
            m.box('cabinet',(0,.46,0),(.85,.90,.65),PAINT)
            m.box('worktop',(0,.95,0),(1,.10,.77),STEEL)
    elif kind in {'ecu','two_step_module','laptop','obd_dongle','battery','ignition_coil','vtec_solenoid'}:
        m.box('case',(0,.07,0),(.30,.13,.23),DARK if kind=='battery' else STEEL)
        if kind=='laptop':
            m.box('screen_frame',(0,.22,-.09),(.31,.26,.025),DARK); m.box('screen',(0,.23,-.074),(.275,.215,.006),PAINT)
        else:
            for x in (-.08,.08): m.box('connector_'+str(x),(x,.065,.145),(.10,.07,.075),DARK)
    elif kind in {'timing_belt','timing_chain','anti_roll_bar','steering_rack'}:
        if kind.startswith('timing'):
            for y in (.1,.4): m.torus('pulley_'+str(y),(0,y,0),.10,.024,'z',DARK)
            for x in (-.10,.10): m.box('belt_'+str(x),(x,.25,0),(.02,.30,.04),DARK)
        else:
            m.cylinder('bar',(0,.08,0),.022,.85,'x',STEEL)
            for x in (-.42,.42): m.cylinder('end_'+str(x),(x,.13,0),.034,.20,'y',DARK)
    else:
        # Small pumps/accessories share a family-level housing, with named ports and mounting ears.
        m.cylinder('housing',(0,.12,0),.105,.20,'z',STEEL,12)
        m.cylinder('port',(0,.13,.16),.035,.16,'z',DARK,10)
        for x in (-.12,.12): m.box('mount_'+str(x),(x,.055,0),(.065,.055,.07),STEEL)
        if kind in {'air_filter','oil_filter'}:
            for i in range(12):
                a=i*math.tau/12
                m.box('filter_pleat_'+str(i),(math.cos(a)*.095,.12+math.sin(a)*.095,0),(.017,.017,.20),WHITE)
        if kind=='cooling_fan':
            for i in range(5):
                a=i*math.tau/5
                m.box('blade_'+str(i),(math.cos(a)*.13,.12+math.sin(a)*.13,.13),(.13,.045,.028),DARK)
    m.locators['mount']=[0,0,0]
    return m
