package com.photonspark.autopropulsion;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import com.photonspark.autopropulsion.sim.*;
import java.util.*;

/** Server datapack catalogue. Reload swaps the complete validated snapshot, never a half-loaded map. */
public final class PartCatalog extends SimpleJsonResourceReloadListener {
    public record Entry(String id,String slot,String family,boolean functional,int tier,double mass,
                        double torqueLimit,double rpmLimit,double boost,double radiator,double grip,boolean raceCam) {}
    private static volatile Map<String,Entry> entries=Map.of();
    public PartCatalog(){super(new Gson(),"autopropulsion/parts");}
    public static Entry get(String id){return entries.get(id);}
    public static int size(){return entries.size();}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager resources,ProfilerFiller profiler) {
        if(files.size()>512)throw new IllegalArgumentException("At most 512 part definitions supported in alpha");
        var next=new TreeMap<String,Entry>();
        files.forEach((id,json)->{
            JsonObject o=json.getAsJsonObject();String key=id.toString();
            String slot=str(o,"slot"),family=str(o,"family");
            if(!slot.matches("[a-z0-9_]+")||!Set.of("piston","rotary","shared","body","chassis","workshop").contains(family))throw new IllegalArgumentException("Invalid part "+key);
            double torque=num(o,"torque_limit_nm",10000),rpm=num(o,"rpm_limit",20000),boost=num(o,"boost_bar",0),rad=num(o,"radiator_w_k",110),grip=num(o,"grip",1);
            if(torque<=0||rpm<=0||boost<0||boost>3||rad<=0||grip<.02||grip>2)throw new IllegalArgumentException("Out-of-range statistics: "+key);
            next.put(key,new Entry(key,slot,family,o.get("functional").getAsBoolean(),o.get("tier").getAsInt(),num(o,"mass_kg",1),torque,rpm,boost,rad,grip,o.has("race_cam")&&o.get("race_cam").getAsBoolean()));
        });
        for(String id:BuiltinCatalog.STARTER_PARTS)if(!next.containsKey("autopropulsion:"+id))throw new IllegalArgumentException("Missing starter definition: "+id);
        entries=Map.copyOf(next);AutoPropulsion.LOG.info("Loaded {} AutoPropulsion part definitions",next.size());
    }
    private static String str(JsonObject o,String k){String s=o.get(k).getAsString();if(s.length()>128)throw new IllegalArgumentException("String too long");return s;}
    private static double num(JsonObject o,String k,double d){return o.has(k)?Numbers.finite(o.get(k).getAsDouble(),k):d;}
}
