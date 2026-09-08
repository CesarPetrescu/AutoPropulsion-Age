"""Bind the original engine art to individual service slots, using authored mount coordinates."""
import re
def internal_binding(name,cat,family,part):
    if family is None:return None
    if name.startswith(('crankshaft','main_bearing','bearing_cap')):return 'internal.crank'
    if name.startswith(('camshaft','v6_cam','flat4_cam','timing_')):return 'internal.timing'
    if name.startswith('eccentric_shaft'):return 'internal.eccentric'
    match=re.match(r'(rotor_housing|rotor|apex_seal|stationary_gear)_[1-4]r_(\d+)',name)
    if match:return f"rotor.{int(match[2])+1}."+('seals' if match[1]=='apex_seal' else 'bearing' if match[1]=='stationary_gear' else 'housing')
    match=re.match(r'(piston_ring|piston|wrist_pin|connecting_rod|rod_bearing)_(\d+)',name)
    if match:return f"cylinder.{int(match[2])+1}."+('rings' if match[1]=='piston_ring' else 'bearing' if match[1] in ['connecting_rod','rod_bearing'] else 'piston')
    match=re.match(r'(v6|flat4)_cylinder_(-?1)_(\d+)',name)
    if match:return f"cylinder.{int(match[3])+1+(0 if match[2]=='-1' else 3 if match[1]=='v6' else 2)}.piston"
    if family==0 and name.startswith(('valve_','valve_spring','valve_seal','lifter_')):
        y=part['mount_world_m'][1];n=min(range(4),key=lambda n:abs(y-(-1.5575+n*.125)))+1
        return f'cylinder.{n}.valves'
    return None
