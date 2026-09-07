package com.photonspark.autopropulsion;

import com.google.gson.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.photonspark.autopropulsion.sim.Parts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server datapack catalogue. Invalid reloads fail atomically; model studies are not installable. */
public final class PartCatalog extends SimpleJsonResourceReloadListener {
    private static final Codec<Set<String>> TAGS = Codec.STRING.listOf().xmap(Set::copyOf, ArrayList::new);
    private static final Codec<Parts.Slot> SLOT = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(Parts.Slot::id), Codec.STRING.fieldOf("accepts").forGetter(Parts.Slot::accepts),
        Codec.BOOL.optionalFieldOf("required", false).forGetter(Parts.Slot::required)).apply(i, Parts.Slot::new));
    private static final Codec<Parts.Modifier> MODIFIER = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("stat").forGetter(Parts.Modifier::stat),
        Codec.STRING.xmap(Parts.Op::valueOf, Parts.Op::name).fieldOf("op").forGetter(Parts.Modifier::op),
        Codec.DOUBLE.fieldOf("value").forGetter(Parts.Modifier::value)).apply(i, Parts.Modifier::new));
    public static final Codec<Parts.Definition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(Parts.Definition::id),
        Codec.STRING.fieldOf("category").forGetter(Parts.Definition::category),
        Codec.STRING.fieldOf("preferred_slot").forGetter(Parts.Definition::preferredSlot),
        TAGS.optionalFieldOf("tags", Set.of()).forGetter(Parts.Definition::tags),
        TAGS.optionalFieldOf("requires_tags", Set.of()).forGetter(Parts.Definition::requiresTags),
        TAGS.optionalFieldOf("excludes_tags", Set.of()).forGetter(Parts.Definition::excludesTags),
        MODIFIER.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Parts.Definition::modifiers),
        Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("limits", Map.of()).forGetter(Parts.Definition::limits),
        SLOT.listOf().optionalFieldOf("slots", List.of()).forGetter(Parts.Definition::slots),
        Codec.DOUBLE.optionalFieldOf("mass_kg", 1d).forGetter(Parts.Definition::massKg),
        Codec.BOOL.optionalFieldOf("implemented", false).forGetter(Parts.Definition::implemented),
        Codec.STRING.fieldOf("model").forGetter(Parts.Definition::model)).apply(i, Parts.Definition::new));
    private static volatile Map<String, Parts.Definition> definitions = Map.of();
    private static final Map<String, Integer> ICONS = loadManifest();
    public PartCatalog() { super(new Gson(), "autopropulsion/parts"); }
    private static Map<String, Integer> loadManifest() {
        TreeMap<String, Integer> values = new TreeMap<>();
        try (InputStream stream = PartCatalog.class.getResourceAsStream("/autopropulsion-builtin-parts.tsv")) {
            if (stream == null) throw new IllegalStateException("Run the generateAssets task before launching");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    String[] fields = line.split("\t"); if (fields.length >= 2) values.put(fields[0], Integer.parseInt(fields[1]));
                }
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return Collections.unmodifiableMap(values);
    }
    public static Set<String> builtinIds() { return ICONS.keySet(); }
    public static int icon(String id) { return ICONS.getOrDefault(id, 0); }
    public static Parts.Definition get(String id) {
        Parts.Definition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown part: " + id);
        return definition;
    }
    public static int size() { return definitions.size(); }
    @Override protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager resources, ProfilerFiller profiler) {
        if (json.size() > 4096) throw new IllegalStateException("Part catalogue exceeds 4096 definitions");
        TreeMap<String, Parts.Definition> pending = new TreeMap<>();
        json.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).forEach(entry -> {
            Parts.Definition part = CODEC.parse(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
            if (!part.id().equals(entry.getKey().toString())) throw new IllegalArgumentException("Part id differs from resource path: " + entry.getKey());
            if (part.slots().size() > 32 || part.modifiers().size() > 64) throw new IllegalArgumentException("Part budget exceeded: " + part.id());
            pending.put(part.id(), part);
        });
        definitions = Collections.unmodifiableMap(pending);
        AutoPropulsion.LOG.info("APA_PART_CATALOG_LOADED definitions={} installable={}", pending.size(), pending.values().stream().filter(Parts.Definition::implemented).count());
    }
}
