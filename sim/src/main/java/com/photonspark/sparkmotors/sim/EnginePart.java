package com.photonspark.sparkmotors.sim;

/** Stable three-bit slot IDs. Values 1 and 2 retain the original item registry IDs. */
public enum EnginePart {
    INTAKE("Intake", "intake", "Airbox", "Cold-air", "Individual throttles", "Ram plenum"),
    FUEL("Fuel system", "fuel_system", "Port injection", "High-flow rail", "Return fuel rail", "Race injection"),
    IGNITION("Ignition", "ignition", "Coil pack", "Performance coils", "CDI ignition", "Multi-spark"),
    COOLING("Cooling", "cooling", "OEM radiator", "Aluminium radiator", "Dual electric fans", "Race radiator"),
    INTERNALS("Rotating assembly", "internals", "Cast assembly", "Forged assembly", "High compression", "Billet assembly"),
    INDUCTION("Forced induction", "induction", "Street turbo", "Centrifugal blower", "Large turbo", "Twin turbos", "Roots blower", "Twin-screw blower"),
    EXHAUST("Exhaust", "exhaust", "Cast manifold", "4-2-1 headers", "Equal-length tubes", "Race collector"),
    FLYWHEEL("Flywheel", "flywheel", "OEM flywheel", "Light steel", "Aluminium flywheel", "Billet flywheel"),
    HEADWORK("Cams / rotary ports", "headwork", "OEM cams / side ports", "Street cams / ports", "Race cams / bridge", "High-lift / peripheral"),
    OIL("Oil system", "oil_system", "Wet sump", "Baffled sump", "Oil cooler", "Dry sump");
    public final String title, id;
    private final String[] options;
    EnginePart(String title,String id,String... options){this.title=title;this.id=id;this.options=options;}
    public int maxVariant(){return options.length;}
    public int variant(int parts){return (parts>>>(ordinal()*3))&7;}
    public int with(int parts,int variant){
        if(variant<0||variant>maxVariant())throw new IllegalArgumentException("Unknown "+id+" option: "+variant);
        return (parts&~(7<<(ordinal()*3)))|(variant<<(ordinal()*3));
    }
    public String label(int v){return v==0?(this==INDUCTION?"Naturally aspirated":"Missing"):v<=options.length?options[v-1]:"Unknown";}
    public String itemName(int v){
        if(v<1||v>maxVariant())throw new IllegalArgumentException("No item for "+id+" option "+v);
        if(this==INDUCTION)return new String[]{"turbo_kit","supercharger_kit","large_turbo_kit","twin_turbo_kit","roots_blower_kit","twin_screw_kit"}[v-1];
        return v<=2?(v==2?"performance_":"stock_")+id:id+"_"+v;
    }
    public String description(int v){
        if(v==0)return this==INDUCTION?"Remove the compressor and its plumbing.":"Remove this assembly. The engine cannot start without it.";
        return switch(this){
            case INTAKE -> new String[]{"Quiet airbox; balanced low-speed response.","Larger cone filter and smooth intake tube.","Separate throttle butterflies; sharper response and high-RPM flow.","Long runners and a large plenum; stronger mid-range."}[v-1];
            case FUEL -> "Injector capacity: "+new int[]{220,360,520,760}[v-1]+" Nm per reference engine. Undersized injectors run lean under load.";
            case IGNITION -> new String[]{"Standard coil and plug energy.","Higher-energy coil bank for boost.","Capacitor discharge with stronger high-RPM spark.","Multiple spark drivers and a separate control module."}[v-1];
            case COOLING -> new String[]{"Standard radiator with thermostatic electric fan.","Thicker aluminium core; improved heat rejection.","Two electric fans improve cooling while stationary.","Large finned core and high-output fans for sustained boost."}[v-1];
            case INTERNALS -> new String[]{"Cast rods / OEM seals; natural aspiration only.","Forged rods / reinforced seals; street boost.","High compression / high-compression rotors; natural aspiration only.","Billet crank and rods / race shaft and seals; high boost."}[v-1];
            case INDUCTION -> new String[]{"0.65 bar; early spool. Requires high-flow fuel and forged internals.","0.65 bar; boost rises with RPM; belt drive consumes shaft power.","1.40 bar; later spool and more lag. Requires race support parts.","1.15 bar; two smaller turbos. Requires race support parts.","0.80 bar; immediate low-RPM boost with substantial belt load.","1.10 bar; positive-displacement boost with better compressor efficiency."}[v-1];
            case EXHAUST -> new String[]{"Compact cast manifold. The separate muffler controls exhaust sound.","Paired runners improve low and medium RPM torque.","Individual equal-length pipes improve high RPM flow.","Large collector: strongest high RPM flow, less low-end torque."}[v-1];
            case FLYWHEEL -> "Flywheel inertia: "+new String[]{"0.22","0.13","0.065","0.09"}[v-1]+" kg m2. Lower inertia revs faster and stores less launch energy.";
            case HEADWORK -> new String[]{"Broad stock torque curve.","Mild cams / street ports; balanced road use.","Long-duration cams / bridge ports; trades low-end torque for top-end.","High lift / peripheral ports; pronounced high-RPM power band."}[v-1];
            case OIL -> new String[]{"Standard pump and sump.","Baffles and higher-pressure pump.","External oil cooler and braided feed lines.","Scavenge pump, external oil tank and cooler; strongest pressure support."}[v-1];
        };
    }
    public static int stock(){int p=0;for(var s:values())if(s!=INDUCTION)p=s.with(p,1);return p;}
    public static int sanitize(int parts){int p=0;for(var s:values())p=s.with(p,Math.min(s.maxVariant(),s.variant(parts)));return p;}
    /** New slots are supplied as OEM parts when loading an alpha 0.1/0.2 car or traded engine. */
    public static int fromLegacy(int parts){int p=stock();for(int i=0;i<6;i++)p=values()[i].with(p,Math.min(2,(parts>>>(i*2))&3));return p;}
    public static String compatibilityProblem(int parts){
        int mode=INDUCTION.variant(parts),fuel=FUEL.variant(parts),internals=INTERNALS.variant(parts);
        if(mode==0)return "";
        if(fuel<2||internals!=2&&internals!=4)return "Boost requires high-flow fuel and forged or billet internals.";
        if((mode==3||mode==4)&&(fuel<3||internals!=4||COOLING.variant(parts)<2||IGNITION.variant(parts)<2))return "Large/twin turbos need return/race fuel, billet internals, upgraded cooling and ignition.";
        return "";
    }
    public static String problem(int parts){
        for(var s:values())if(s!=INDUCTION&&s.variant(parts)==0)return "Missing "+s.title.toLowerCase(java.util.Locale.ROOT)+".";
        return compatibilityProblem(parts);
    }
    public static boolean ready(int parts){return problem(parts).isEmpty();}
    public String installationProblem(int parts,int value){return value==0?"":compatibilityProblem(with(parts,value));}
    public static int boosted(int kind){int p=stock();p=FUEL.with(p,kind==3||kind==4?3:2);p=INTERNALS.with(p,kind==3||kind==4?4:2);p=COOLING.with(p,2);p=IGNITION.with(p,2);return INDUCTION.with(p,kind);}
}
