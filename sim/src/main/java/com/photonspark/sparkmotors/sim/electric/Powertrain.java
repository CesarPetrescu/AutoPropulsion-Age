package com.photonspark.sparkmotors.sim.electric;

/** Designed vehicles, not manufacturer specifications. SI except explicitly named kWh/kW. */
public enum Powertrain {
    COMBUSTION("Combustion", null, 0, 0, 1280, 0, 0, 0),
    HYBRID("Series hybrid", new BatteryModel.Spec(1.8, 220, .14, 200, 100, 45), 80, 220, 1460, 40, 0, 0),
    PLUG_IN_HYBRID("Plug-in series hybrid", new BatteryModel.Spec(18, 400, .11, 350, 100, 160), 120, 280, 1640, 55, 7.2, 30),
    ELECTRIC_400("Electric / 400 V", new BatteryModel.Spec(60, 400, .065, 550, 350, 420), 160, 310, 1730, 0, 11, 120),
    ELECTRIC_800("Electric / 800 V", new BatteryModel.Spec(85, 800, .095, 430, 450, 550), 240, 380, 1860, 0, 22, 250);

    public final String title;
    public final BatteryModel.Spec battery;
    public final double motorKw, torqueNm, massKg, generatorKw, onboardChargerKw, dcChargeKw;
    Powertrain(String title, BatteryModel.Spec battery, double motorKw, double torqueNm, double massKg,
               double generatorKw, double onboardChargerKw, double dcChargeKw) {
        this.title=title; this.battery=battery; this.motorKw=motorKw; this.torqueNm=torqueNm;
        this.massKg=massKg; this.generatorKw=generatorKw;
        this.onboardChargerKw=onboardChargerKw; this.dcChargeKw=dcChargeKw;
    }
    public boolean electric(){return this!=COMBUSTION;}
    public boolean hybrid(){return generatorKw>0;}
    public boolean plugIn(){return onboardChargerKw>0;}
    public String id(){return name().toLowerCase(java.util.Locale.ROOT);}
    public static Powertrain byId(String id){for(var value:values())if(value.id().equals(id))return value;return COMBUSTION;}
    public static Powertrain byId(int id){return id>=0&&id<values().length?values()[id]:COMBUSTION;}
}
