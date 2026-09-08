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
    private static final double[][] CURVE_RPM={
        {0,800,1500,2500,3500,4500,5500,6000,6500,7200}, // I4
        {0,750,1400,2200,3200,4100,5000,5700,6400,7200}, // V6
        {0,850,1600,2400,3300,4200,5100,5900,6600,7200}, // FLAT4
        {0,900,1800,2700,3600,4400,5300,6100,6700,7400}, // ROTOR1
        {0,850,1700,2600,3500,4500,5400,6200,6800,7400}, // ROTOR2
        {0,850,1800,2800,3700,4600,5500,6300,6900,7400}, // ROTOR3
        {0,900,1900,2900,3800,4700,5600,6400,6950,7400}, // ROTOR4
    };
    private static final double[][] CURVE_TORQUE={
        {0,85,125,153,173,180,178,174,153,0}, // I4
        {0,137,205,248,279,290,278,258,222,0}, // V6
        {0,94,138,167,192,203,201,186,157,0}, // FLAT4
        {0,58,80,94,105,116,124,129,120,0}, // ROTOR1
        {0,77,110,139,168,197,218,232,216,0}, // ROTOR2
        {0,103,151,198,239,281,314,338,313,0}, // ROTOR3
        {0,133,195,257,313,370,417,446,410,0}, // ROTOR4
    };
    /** Independent gameplay torque maps, Nm at crank before hardware/boost/condition modifiers.
     * This keeps family shape explicit and reviewable; none is advertised as a measured OEM dyno. */
    public double torque(double rpm,boolean sport){
        double[] speeds=CURVE_RPM[ordinal()];
        double[] torque=CURVE_TORQUE[ordinal()];
        if(!Double.isFinite(rpm)||rpm<0)return 0;
        for(int i=1;i<speeds.length;i++)if(rpm<=speeds[i])return (torque[i-1]+(torque[i]-torque[i-1])*(rpm-speeds[i-1])/(speeds[i]-speeds[i-1]))*(sport?1.38:1);
        return 0;
    }
    public boolean rotary(){return ordinal()>=3;}
    public static EngineFamily byId(int id){return id>=0&&id<values().length?values()[id]:I4;}
    public String itemName(int grade){return (grade==2?"sport_":"stock_")+(this==I4?"engine":id+"_engine");}
}
