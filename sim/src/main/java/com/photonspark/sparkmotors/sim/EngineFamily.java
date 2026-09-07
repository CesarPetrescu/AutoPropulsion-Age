package com.photonspark.sparkmotors.sim;

/** Stable save IDs. Curves are gameplay calibrations, not measured production engines. */
public enum EngineFamily {
    I4("Inline-4", "i4", "i4_engine", 1, 1, 1),
    V6("V6", "v6", "v6_engine", 1.48, .95, 1.38),
    FLAT4("Flat-four", "flat4", "flat4_engine", 1.08, .98, 1.05),
    ROTOR1("1 rotor", "rotor1", "rotary_1rotor", .62, .87, .82),
    ROTOR2("2 rotors", "rotor2", "rotary_2rotor", 1.10, .87, 1.30),
    ROTOR3("3 rotors", "rotor3", "rotary_3rotor", 1.55, .87, 1.78),
    ROTOR4("4 rotors", "rotor4", "rotary_4rotor", 2.03, .87, 2.24);

    public final String title, id, model;
    public final double torqueScale, rpmScale, fuelScale;
    EngineFamily(String title, String id, String model, double torque, double rpm, double fuel) {
        this.title=title;this.id=id;this.model=model;torqueScale=torque;rpmScale=rpm;fuelScale=fuel;
    }
    public boolean rotary(){return ordinal()>=3;}
    public static EngineFamily byId(int id){return id>=0&&id<values().length?values()[id]:I4;}
    public String itemName(int grade){return (grade==2?"sport_":"stock_")+(this==I4?"engine":id+"_engine");}
}
