#!/usr/bin/env python3
"""One-time canonical catalogue seed. Refuses to overwrite an existing catalogue."""
from pathlib import Path
import json,struct,gzip
R=Path(__file__).resolve().parents[1]
if (R/'assets/catalog.json').exists():
 raise SystemExit('Catalogue already exists; edit assets/catalog.json and run generate-content.py instead.')
def put(p,s):
 p=R/p;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s)
items=[]
def add(id,slot,category,family='shared',functional=False,**kw):
 d=dict(id=id,slot=slot,category=category,family=family,functional=functional,tier=1,mass_kg=1.0,model=f'autopropulsion:item/parts/{id}',**kw);items.append(d)
add('i4_block_cast','engine_block','block','piston',True,torque_limit_nm=800,rpm_limit=10000,mass_kg_override=38)
add('i4_block_billet','engine_block','block','piston',True,torque_limit_nm=1200,rpm_limit=12000)
add('stock_connecting_rods','connecting_rods','rod','piston',True,torque_limit_nm=260,rpm_limit=6800)
add('forged_connecting_rods','connecting_rods','rod','piston',True,torque_limit_nm=650,rpm_limit=9000)
add('stock_camshaft','camshaft','shaft','piston',True,rpm_limit=6800)
add('race_camshaft','camshaft','shaft','piston',True,rpm_limit=8200,race_cam=True)
add('naturally_aspirated_intake','turbo','intake','piston',True,boost_bar=0)
add('street_turbo','turbo','turbo','piston',True,boost_bar=.8)
add('race_turbo','turbo','turbo','piston',True,boost_bar=1.5)
add('stock_radiator','radiator','radiator','shared',True,radiator_w_k=110)
add('aluminium_radiator','radiator','radiator','shared',True,radiator_w_k=180)
add('street_tires','tires','tire','chassis',True,grip=1)
add('sport_tires','tires','tire','chassis',True,grip=1.2)
add('offroad_tires','tires','tire','chassis',True,grip=.85)
add('stock_ecu','ecu','electronics','shared',True,rpm_limit=6800)
add('standalone_ecu','ecu','electronics','shared',True,rpm_limit=10000)
rows='''air_filter:filter
alternator:alternator
boost_pipe:pipe
charge_pipe:pipe
rod_bearings:bearing
main_bearings:bearing
crankcase_ventilation:pipe
main_bearing_caps:bearing
main_bearing_bolts:bolt
crankshaft:crank
cylinder_head_dohc:head
cylinder_head_ohc:head
cylinder_liners:liner
harmonic_damper:pulley
exhaust_header:header
exhaust_system:exhaust
fuel_pump:pump
mpi_injection:injection
direct_injection:injection
carburetor:carb
head_bolts:bolt
head_gasket:gasket
ignition_coils:ignition
intercooler:radiator
oil_filter:filter
oil_pump:pump
oil_pan:pan
wrist_pins:pin
piston_rings:ring
piston_cooling_nozzles:pipe
cast_pistons:piston
forged_pistons:piston
starter_motor:starter
throttle_body:throttle
timing_belt:belt
timing_chain:belt
timing_gears:gear
two_step_module:electronics
valve_lifters:lifter
valve_springs:spring
valve_seals:ring
intake_valves:valve
exhaust_valves:valve
vtec_actuator:actuator
adjustable_cam_gear:gear
water_reservoir:tank
coolant_pipes:pipe
water_meth_injection:injection
nitrous_kit:bottle
nitrous_bottle:bottle
intake_manifold:intake
roots_supercharger:supercharger
centrifugal_supercharger:turbo
wastegate:valve
blow_off_valve:valve
fuel_injectors:injector
spark_plugs:plug
flywheel:disc
stock_clutch:disc
twin_plate_clutch:disc
water_pump:pump
thermostat:thermostat
cooling_fan:fan
battery:battery
fuel_tank:tank
oil_metering_pump:pump'''
for row in rows.splitlines():
 id,cat=row.split(':');add(id,id,cat,'piston' if id in ['crankshaft','cylinder_head_dohc','cylinder_head_ohc','cast_pistons','forged_pistons'] else 'shared')
for id,cat in [('rotary_block','rotary_block'),('eccentric_shaft','shaft'),('rotor','rotor'),('rotor_housing','rotary_housing'),('side_housing','disc'),('apex_seals','pin'),('stationary_gear','gear'),('side_intake_port','intake'),('bridge_intake_port','intake'),('peripheral_intake_port','intake'),('rotary_exhaust_port','header')]:add(id,id,cat,'rotary')
for id,cat in [('manual_gearbox','gearbox'),('sequential_gearbox','gearbox'),('automatic_gearbox','gearbox'),('dct_gearbox','gearbox'),('cvt_gearbox','gearbox'),('final_drive','gear'),('open_differential','differential'),('limited_slip_differential','differential'),('locked_differential','differential'),('torsen_differential','differential'),('welded_differential','differential'),('awd_transfer_case','gearbox'),('wheel_hub','hub'),('alloy_rim','rim'),('brake_pads','pad'),('brake_rotor','disc'),('brake_caliper','caliper'),('coilover','spring'),('anti_roll_bar','bar'),('steering_rack','rack'),('abs_module','electronics')]:add(id,id,cat,'chassis')
for id,cat in [('front_door_left','door'),('front_door_right','door'),('hood','hood'),('trunk','panel'),('front_bumper','bumper'),('rear_bumper','bumper'),('fender','panel'),('side_skirts','bar'),('spoiler','spoiler'),('splitter','panel'),('headlights','light'),('taillights','light'),('mirrors','mirror'),('dashboard','dash'),('bucket_seat','seat'),('roll_cage','cage'),('steering_wheel','wheel'),('shifter','shifter'),('paint_can','can'),('livery_sheet','panel')]:add(id,id,cat,'body')
for id,cat in [('obd_dongle','electronics'),('tuner_laptop','laptop'),('tune_file','electronics'),('garage_jack','jack'),('fuel_pump_station','station'),('paint_booth','booth')]:add(id,id,cat,'workshop')
items.sort(key=lambda d:d['id'])
for i,d in enumerate(items):
 if 'mass_kg_override' in d:d['mass_kg']=d.pop('mass_kg_override')
 d['model_index']=i+1
 put('mod/src/main/resources/data/autopropulsion/autopropulsion/parts/'+d['id']+'.json',json.dumps(d,indent=2)+'\n')
put('assets/catalog.json',json.dumps({'schema_version':1,'parts':items},indent=2)+'\n')
lang={'itemGroup.autopropulsion':'AutoPropulsion Age','entity.autopropulsion.hatchback':'APA H1 Hatchback',
 'item.autopropulsion.part':'Vehicle Component','item.autopropulsion.vehicle_blueprint':'H1 Vehicle Blueprint',
 'item.autopropulsion.mechanic_wrench':'Mechanic Wrench','item.autopropulsion.jerry_can':'Gasoline Jerry Can (10 L)',
 'item.autopropulsion.empty_jerry_can':'Empty Jerry Can','block.autopropulsion.garage_lift':'Garage Service Lift',
 'block.autopropulsion.dyno':'Engine Dyno Terminal','block.autopropulsion.parts_bench':'Parts Bench (Decorative)',
 'block.autopropulsion.asphalt':'Asphalt','tooltip.autopropulsion.install':'Prototype component: stop engine, then right-click car to install.',
 'tooltip.autopropulsion.catalog':'Catalogue / model only. Gameplay integration is not implemented.',
 'message.autopropulsion.clear_space':'Need a clear area about 3 x 5 blocks to deploy the car.',
 'message.autopropulsion.no_car':'No accessible car within 6 blocks.',
 'message.autopropulsion.installed':'Component installed. Previous part returned in survival.',
 'message.autopropulsion.rejected':'Cannot install: stop engine, own the car, and use a supported component.',
 'message.autopropulsion.refuel_off':'Stop the engine and leave room for a full 10 L can.',
 'screen.autopropulsion.garage':'AutoPropulsion Garage','key.categories.autopropulsion':'AutoPropulsion Age',
 'key.autopropulsion.ignition':'Toggle ignition','key.autopropulsion.shift_up':'Shift up','key.autopropulsion.shift_down':'Shift down',
 'key.autopropulsion.clutch':'Disengage clutch','key.autopropulsion.garage':'Open garage diagnostics','subtitles.autopropulsion.engine':'Engine running'}
for d in items:lang['part.autopropulsion.'+d['id']]=d['id'].replace('_',' ').title().replace('Ecu','ECU').replace('Obd','OBD').replace('Dct','DCT').replace('Cvt','CVT').replace('Abs','ABS')
put('mod/src/main/resources/assets/autopropulsion/lang/en_us.json',json.dumps(lang,indent=2)+'\n')
put('mod/src/main/resources/assets/autopropulsion/sounds.json',json.dumps({'engine_loop':{'subtitle':'subtitles.autopropulsion.engine','sounds':[{'name':'autopropulsion:engine_loop','stream':False}]}},indent=2)+'\n')
def named(t,n,p):b=n.encode();return bytes([t])+struct.pack('>H',len(b))+b+p
payload=named(3,'DataVersion',struct.pack('>i',3955))+named(9,'size',b'\x03'+struct.pack('>i3i',3,16,8,32))+named(9,'palette',b'\x0a'+struct.pack('>i',0))+named(9,'blocks',b'\x0a'+struct.pack('>i',0))+named(9,'entities',b'\x0a'+struct.pack('>i',0))+b'\0'
p=R/'mod/src/main/resources/data/autopropulsion/structure/empty.nbt';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(gzip.compress(named(10,'',payload),mtime=0))
print(f'{len(items)} catalogue entries, {sum(d["functional"] for d in items)} functional')
