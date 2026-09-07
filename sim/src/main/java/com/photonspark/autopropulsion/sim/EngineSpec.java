package com.photonspark.autopropulsion.sim;

/** SI units except explicitly named litres, RPM and gauge bar. */
public record EngineSpec(double litres, double compression, Curve ve, double powerMultiplier,
                         double rpmLimit, double torqueLimitNm, String bottleneck,
                         double maxBoostBar, double radiatorWK) {
    public EngineSpec {
        Numbers.positive(litres,"litres"); Numbers.positive(compression,"compression");
        if(compression<=1 || compression>25 || litres>20) throw new IllegalArgumentException("Engine dimensions out of range");
        java.util.Objects.requireNonNull(ve); java.util.Objects.requireNonNull(bottleneck);
        Numbers.positive(powerMultiplier,"powerMultiplier"); Numbers.positive(rpmLimit,"rpmLimit");
        Numbers.positive(torqueLimitNm,"torqueLimit"); Numbers.positive(radiatorWK,"radiator");
        if(Numbers.finite(maxBoostBar,"boost")<0||maxBoostBar>3) throw new IllegalArgumentException("boost out of range");
    }
    public static EngineSpec reference() {
        return new EngineSpec(2,10,Curve.of(800,.48,1500,.63,2500,.79,3500,.86,4500,.90,5500,.91,6000,.92,6500,.86,7000,.64),
            1,6800,260,"engine/connecting_rods",0,110);
    }
    public EngineSpec withUpgrades(boolean raceCam, double boost, boolean forged, boolean cooling) {
        Curve c = raceCam ? Curve.of(800,.35,1500,.45,2500,.64,3500,.77,4500,.89,5500,.96,6000,1.01,6500,1.035,7000,1.07,7400,1.02,7800,.8) : reference().ve();
        return new EngineSpec(litres,compression,c,powerMultiplier,raceCam?7800:6800,forged?650:260,
            "engine/connecting_rods",boost,cooling?180:110);
    }
}
