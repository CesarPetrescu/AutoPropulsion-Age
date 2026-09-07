"""Content manifest. MODEL_ONLY parts have assets, not secretly inert installed stats."""
FAMILIES = '''engine_block_i4 engine_block_v8 engine_block_rotary air_filter alternator boost_pipe charge_pipe camshaft rod_bearing main_bearing connecting_rod crankcase_vent main_bearing_cap main_bearing_bolt crankshaft cylinder_head cylinder_liner harmonic_damper ecu exhaust_header exhaust_system fuel_pump injector carburetor head_bolt head_gasket ignition_coil intercooler oil_filter oil_pump oil_pan wrist_pin piston_ring piston_cooling_nozzle piston starter throttle_body timing_belt timing_chain two_step_module valve_lifter valve_spring valve_seal intake_valve exhaust_valve vtec_solenoid camshaft_gear water_reservoir water_pipe water_meth nitrous_kit nitrous_bottle intake_manifold turbocharger wastegate bov supercharger flywheel clutch manual_gearbox sequential_gearbox automatic_gearbox dct_gearbox cvt_gearbox final_drive differential transfer_case radiator thermostat cooling_fan battery fuel_tank eccentric_shaft rotor rotor_housing side_housing apex_seal stationary_gear rotary_intake_port rotary_exhaust_port oil_metering_pump rim tire brake_rotor brake_pad brake_caliper spring damper coilover steering_rack anti_roll_bar seat door_left door_right hood trunk bumper_front bumper_rear fender side_skirt spoiler splitter headlight taillight mirror dashboard roll_cage steering_wheel shifter garage_lift dyno_rollers fuel_pump_block part_bench paint_booth laptop obd_dongle jerry_can spark_plug'''.split()

def slot(id, accepts, required=True): return dict(id=id, accepts=accepts, required=required)
def modifier(stat, value, op='SET'): return dict(stat=stat, op=op, value=value)
def part(id, category, preferred, model, *, tags=(), requires=(), mods=(), limits=None, slots=(), mass=1):
    return dict(id='autopropulsion:'+id, category=category, preferred_slot=preferred, tags=list(tags), requires_tags=list(requires),
                excludes_tags=[], modifiers=list(mods), limits=limits or {}, slots=list(slots), mass_kg=mass, implemented=True,
                model='autopropulsion:vehicle/parts/'+model)

def definitions():
    values = [
        part('ref_i4','engine_block','engine','engine_block_i4',tags=['engine/piston','cylinders/4'],
             mods=[modifier('displacement_l',2),modifier('compression_ratio',10),modifier('idle_rpm',850)],
             limits={'torque_nm':800,'rpm':9000},slots=[slot('rods','rods'),slot('camshaft','camshaft'),slot('turbo','turbo',False),slot('ecu','ecu')],mass=130),
        part('ref_rotary','engine_block','engine','engine_block_rotary',tags=['engine/rotary','rotors/2'],
             mods=[modifier('displacement_l',2.616),modifier('compression_ratio',10),modifier('idle_rpm',1000)],
             limits={'torque_nm':600,'rpm':10000},slots=[slot('apex_seals','apex_seals'),slot('intake_port','rotary_port'),slot('turbo','turbo',False),slot('ecu','ecu')],mass=105),
        part('stock_rods','rods','engine/rods','connecting_rod',requires=['engine/piston'],limits={'torque_nm':260,'rpm':7600},mass=3.2),
        part('forged_rods','rods','engine/rods','connecting_rod',requires=['engine/piston'],limits={'torque_nm':650,'rpm':9000},mass=2.8),
        part('stock_cam','camshaft','engine/camshaft','camshaft',requires=['engine/piston'],limits={'rpm':7200},mass=4),
        part('race_cam','camshaft','engine/camshaft','camshaft',requires=['engine/piston'],mods=[modifier('race_cam',1)],limits={'rpm':8800},mass=3.8),
        part('turbo_street','turbo','engine/turbo','turbocharger',mods=[modifier('boost_bar',.7)],mass=8),
        part('turbo_race','turbo','engine/turbo','turbocharger',mods=[modifier('boost_bar',1.5)],mass=11),
        part('stock_ecu','ecu','engine/ecu','ecu',limits={'rpm':6800},mass=.8),
        part('standalone_ecu','ecu','engine/ecu','ecu',tags=['ecu/tunable'],limits={'rpm':10000},mass=.8),
        part('manual_5speed','gearbox','gearbox','manual_gearbox',mass=44),
        part('street_wheels','wheels','wheels','tire',mods=[modifier('grip_multiplier',1)],mass=68),
        part('sport_wheels','wheels','wheels','rim',mods=[modifier('grip_multiplier',1.15)],mass=62),
        part('stock_radiator','radiator','radiator','radiator',mods=[modifier('radiator_w_per_k',240)],mass=8),
        part('race_radiator','radiator','radiator','radiator',mods=[modifier('radiator_w_per_k',460)],mass=11),
        part('stock_apex_seals','apex_seals','engine/apex_seals','apex_seal',requires=['engine/rotary'],limits={'torque_nm':290,'rpm':9500},mass=.2),
        part('rotary_side_port','rotary_port','engine/intake_port','rotary_intake_port',requires=['engine/rotary'],mass=1),
        part('rotary_peripheral_port','rotary_port','engine/intake_port','rotary_intake_port',requires=['engine/rotary'],mods=[modifier('idle_rpm',1500),modifier('power_multiplier',1.12,'MUL')],mass=1),
    ]
    for family in FAMILIES:
        values.append(dict(id='autopropulsion:model_'+family,category=family,preferred_slot=family,tags=[family],requires_tags=[],excludes_tags=[],modifiers=[],limits={},slots=[],mass_kg=1,implemented=False,model='autopropulsion:vehicle/parts/'+family))
    return sorted(values,key=lambda value:value['id'])
