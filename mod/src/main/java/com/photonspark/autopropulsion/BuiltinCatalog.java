package com.photonspark.autopropulsion;
/** Generated from explicit, stable model_index values in assets/catalog.json. */
public final class BuiltinCatalog {
    public static final String[] IDS={"abs_module","adjustable_cam_gear","air_filter","alloy_rim","alternator","aluminium_radiator","anti_roll_bar","apex_seals","automatic_gearbox","awd_transfer_case","battery","blow_off_valve","boost_pipe","brake_caliper","brake_pads","brake_rotor","bridge_intake_port","bucket_seat","carburetor","cast_pistons","centrifugal_supercharger","charge_pipe","coilover","coolant_pipes","cooling_fan","crankcase_ventilation","crankshaft","cvt_gearbox","cylinder_head_dohc","cylinder_head_ohc","cylinder_liners","dashboard","dct_gearbox","direct_injection","eccentric_shaft","exhaust_header","exhaust_system","exhaust_valves","fender","final_drive","flywheel","forged_connecting_rods","forged_pistons","front_bumper","front_door_left","front_door_right","fuel_injectors","fuel_pump","fuel_pump_station","fuel_tank","garage_jack","harmonic_damper","head_bolts","head_gasket","headlights","hood","i4_block_billet","i4_block_cast","ignition_coils","intake_manifold","intake_valves","intercooler","limited_slip_differential","livery_sheet","locked_differential","main_bearing_bolts","main_bearing_caps","main_bearings","manual_gearbox","mirrors","mpi_injection","naturally_aspirated_intake","nitrous_bottle","nitrous_kit","obd_dongle","offroad_tires","oil_filter","oil_metering_pump","oil_pan","oil_pump","open_differential","paint_booth","paint_can","peripheral_intake_port","piston_cooling_nozzles","piston_rings","race_camshaft","race_turbo","rear_bumper","rod_bearings","roll_cage","roots_supercharger","rotary_block","rotary_exhaust_port","rotor","rotor_housing","sequential_gearbox","shifter","side_housing","side_intake_port","side_skirts","spark_plugs","splitter","spoiler","sport_tires","standalone_ecu","starter_motor","stationary_gear","steering_rack","steering_wheel","stock_camshaft","stock_clutch","stock_connecting_rods","stock_ecu","stock_radiator","street_tires","street_turbo","taillights","thermostat","throttle_body","timing_belt","timing_chain","timing_gears","torsen_differential","trunk","tune_file","tuner_laptop","twin_plate_clutch","two_step_module","valve_lifters","valve_seals","valve_springs","vtec_actuator","wastegate","water_meth_injection","water_pump","water_reservoir","welded_differential","wheel_hub","wrist_pins"};
    public static final String[] FUNCTIONAL_IDS={"aluminium_radiator","forged_connecting_rods","i4_block_billet","i4_block_cast","naturally_aspirated_intake","offroad_tires","race_camshaft","race_turbo","sport_tires","standalone_ecu","stock_camshaft","stock_connecting_rods","stock_ecu","stock_radiator","street_tires","street_turbo"};
    public static final String[] STARTER_PARTS={"i4_block_cast","stock_connecting_rods","stock_camshaft","naturally_aspirated_intake","stock_radiator","street_tires","stock_ecu"};
    public static final java.util.Set<String> FUNCTIONAL_SLOTS=java.util.Set.of("camshaft","connecting_rods","ecu","engine_block","radiator","tires","turbo");
    public static String slotOf(String id) {return switch(id) {
        case "i4_block_cast" -> "engine_block";
        case "stock_connecting_rods" -> "connecting_rods";
        case "stock_camshaft" -> "camshaft";
        case "naturally_aspirated_intake" -> "turbo";
        case "stock_radiator" -> "radiator";
        case "street_tires" -> "tires";
        case "stock_ecu" -> "ecu";
        default -> throw new IllegalArgumentException("Unknown starter part: "+id);
    };}
    private BuiltinCatalog() {}
}
