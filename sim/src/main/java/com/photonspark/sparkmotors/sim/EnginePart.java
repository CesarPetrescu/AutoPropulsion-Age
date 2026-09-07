package com.photonspark.sparkmotors.sim;

/** Two bits per service assembly. Induction is exclusive: 0 NA, 1 turbo, 2 supercharger. */
public enum EnginePart {
    INTAKE("Intake", "intake"), FUEL("Fuel system", "fuel_system"),
    IGNITION("Ignition", "ignition"), COOLING("Cooling", "cooling"),
    INTERNALS("Internals", "internals"), INDUCTION("Induction", "induction");
    public final String title, id;
    EnginePart(String title,String id){this.title=title;this.id=id;}
    public int variant(int parts){return (parts>>>(ordinal()*2))&3;}
    public int with(int parts,int variant){
        if(variant<0||variant>2)throw new IllegalArgumentException("Unknown engine part variant");
        return (parts&~(3<<(ordinal()*2)))|(variant<<(ordinal()*2));
    }
    public String label(int v){
        if(this==INDUCTION)return v==0?"Natural":v==1?"Turbo":"Supercharger";
        return v==0?"Missing":v==1?"Stock":switch(this){
            case INTERNALS -> "Forged";case FUEL -> "High flow";case COOLING -> "Heavy duty";default -> "Performance";
        };
    }
    public String itemName(int v){return this==INDUCTION?(v==1?"turbo_kit":"supercharger_kit"):(v==2?"performance_":"stock_")+id;}
    public static int stock(){int p=0;for(var s:values())if(s!=INDUCTION)p=s.with(p,1);return p;}
    public static int sanitize(int parts){int p=0;for(var s:values())p=s.with(p,Math.min(2,s.variant(parts)));return p;}
    public static String problem(int parts){
        for(var s:values())if(s!=INDUCTION&&s.variant(parts)==0)return "Missing "+s.title.toLowerCase(java.util.Locale.ROOT)+".";
        if(INDUCTION.variant(parts)>0&&(FUEL.variant(parts)!=2||INTERNALS.variant(parts)!=2))
            return "Boost requires high-flow fuel and forged internals.";
        return "";
    }
    public static boolean ready(int parts){return problem(parts).isEmpty();}
    public String installationProblem(int parts,int value){
        int next=with(parts,value);
        if(value>0&&INDUCTION.variant(next)>0&&(this==INDUCTION||this==FUEL||this==INTERNALS)&&
            (FUEL.variant(next)!=2||INTERNALS.variant(next)!=2))return "Boost requires high-flow fuel and forged internals.";
        return "";
    }
    public static int boosted(int kind){int p=stock();p=FUEL.with(p,2);p=INTERNALS.with(p,2);p=COOLING.with(p,2);return INDUCTION.with(p,kind);}
}
