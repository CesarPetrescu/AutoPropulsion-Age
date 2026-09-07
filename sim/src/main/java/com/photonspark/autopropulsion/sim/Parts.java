package com.photonspark.autopropulsion.sim;

import java.util.*;

/** Bounded, tag-based part tree. All iteration orders used in resolution are explicit. */
public final class Parts {
    private Parts() {}
    public enum Op { SET, ADD, MUL, MIN, MAX }
    public record Modifier(String stat, Op op, double value) {
        public Modifier { Objects.requireNonNull(stat); Objects.requireNonNull(op); Numbers.finite(value, "modifier"); }
    }
    public record Slot(String id, String accepts, boolean required) {
        public Slot {
            if (id == null || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("invalid slot id");
            Objects.requireNonNull(accepts);
        }
    }
    public record Definition(String id, String category, String preferredSlot, Set<String> tags,
                             Set<String> requiresTags, Set<String> excludesTags, List<Modifier> modifiers,
                             Map<String, Double> limits, List<Slot> slots, double massKg, boolean implemented, String model) {
        public Definition {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) throw new IllegalArgumentException("invalid part id");
            Objects.requireNonNull(category); Objects.requireNonNull(preferredSlot); Objects.requireNonNull(model);
            tags = Set.copyOf(tags); requiresTags = Set.copyOf(requiresTags); excludesTags = Set.copyOf(excludesTags);
            modifiers = List.copyOf(modifiers); limits = Collections.unmodifiableMap(new TreeMap<>(limits)); slots = List.copyOf(slots);
            if (Numbers.finite(massKg, "mass") < 0) throw new IllegalArgumentException("negative mass");
            limits.forEach((k, v) -> Numbers.positive(v, "limit " + k));
            if (slots.stream().map(Slot::id).distinct().count() != slots.size()) throw new IllegalArgumentException("duplicate child slots");
        }
    }
    public record Limit(double value, String sourcePath) {}
    public record Resolved(Map<String, Double> stats, Map<String, Limit> limits, Set<String> tags, List<String> warnings) {
        public Resolved { stats = Map.copyOf(stats); limits = Map.copyOf(limits); tags = Set.copyOf(tags); warnings = List.copyOf(warnings); }
        public double value(String stat, double fallback) { return stats.getOrDefault(stat, fallback); }
        public double limit(String stat, double fallback) { return limits.containsKey(stat) ? limits.get(stat).value() : fallback; }
        public EngineSpec engineSpec(double requestedRedline) {
            double hardwareRpm = limit("rpm", value("redline_rpm", 6800));
            double redline = Math.max(value("idle_rpm", 850) + 200, Math.min(hardwareRpm, requestedRedline));
            Limit torque = limits.getOrDefault("torque_nm", new Limit(260, "engine"));
            return new EngineSpec(tags.contains("engine/rotary") ? EngineSpec.Family.ROTARY : EngineSpec.Family.PISTON,
                value("displacement_l", 2), value("compression_ratio", 10), value("idle_rpm", 850), redline,
                Math.min(3, value("boost_bar", 0)), torque.value(), value("radiator_w_per_k", 240),
                value("power_multiplier", 1), value("race_cam", 0) > .5, torque.sourcePath());
        }
    }
    public static final class Assembly {
        private final SortedMap<String, Slot> roots = new TreeMap<>();
        private final SortedMap<String, Definition> installed = new TreeMap<>();
        public Assembly(List<Slot> rootSlots) {
            for (Slot slot : rootSlots) if (roots.put(slot.id(), slot) != null) throw new IllegalArgumentException("duplicate root slot");
        }
        public Map<String, Definition> installed() { return Collections.unmodifiableMap(installed); }
        public SortedMap<String, Slot> availableSlots() {
            SortedMap<String, Slot> available = new TreeMap<>(roots);
            installed.forEach((path, part) -> part.slots().forEach(slot -> available.put(path + "/" + slot.id(), slot)));
            return available;
        }
        public List<String> reasons(String path, Definition part) {
            List<String> reasons = new ArrayList<>();
            if (path == null || path.length() > 160 || path.split("/").length > 8) return List.of("invalid_slot_path");
            Slot slot = availableSlots().get(path);
            if (slot == null) return List.of("slot_not_exposed:" + path);
            if (!slot.accepts().equals(part.category()) && !part.tags().contains(slot.accepts())) reasons.add("wrong_category:" + slot.accepts());
            if (!part.implemented()) reasons.add("model_only_part:not_installable_yet");
            Set<String> ancestorTags = new TreeSet<>();
            String parent = path;
            while (parent.contains("/")) {
                parent = parent.substring(0, parent.lastIndexOf('/'));
                Definition ancestor = installed.get(parent);
                if (ancestor != null) ancestorTags.addAll(ancestor.tags());
            }
            for (String required : new TreeSet<>(part.requiresTags())) if (!ancestorTags.contains(required)) reasons.add("requires:" + required);
            for (String excluded : new TreeSet<>(part.excludesTags())) if (ancestorTags.contains(excluded)) reasons.add("excludes:" + excluded);
            if (installed.size() >= 128 && !installed.containsKey(path)) reasons.add("part_count_limit");
            if (installed.containsKey(path) && installed.keySet().stream().anyMatch(p -> p.startsWith(path + "/"))) reasons.add("remove_children_before_replacing_parent");
            return List.copyOf(reasons);
        }
        public Definition install(String path, Definition part) {
            List<String> reasons = reasons(path, part);
            if (!reasons.isEmpty()) throw new IllegalArgumentException(String.join(", ", reasons));
            return installed.put(path, part);
        }
        public Definition remove(String path) {
            if (installed.keySet().stream().anyMatch(p -> p.startsWith(path + "/"))) throw new IllegalArgumentException("remove children first");
            return installed.remove(path);
        }
        public List<String> missingRequired() {
            return availableSlots().entrySet().stream().filter(e -> e.getValue().required() && !installed.containsKey(e.getKey())).map(Map.Entry::getKey).toList();
        }
        public Resolved resolve(Map<String, Double> base) {
            Map<String, Double> stats = new TreeMap<>(base);
            Map<String, Limit> limits = new TreeMap<>();
            Set<String> tags = new TreeSet<>();
            Map<String, List<Map.Entry<String, Modifier>>> byStat = new TreeMap<>();
            List<String> warnings = new ArrayList<>();
            installed.forEach((path, part) -> {
                tags.addAll(part.tags());
                part.limits().forEach((stat, value) -> {
                    Limit old = limits.get(stat);
                    if (old == null || value < old.value()) limits.put(stat, new Limit(value, path));
                });
                for (Modifier m : part.modifiers()) byStat.computeIfAbsent(m.stat(), k -> new ArrayList<>()).add(Map.entry(path, m));
            });
            byStat.forEach((stat, list) -> {
                double value = stats.getOrDefault(stat, 0d);
                int sets = 0;
                for (Op op : Op.values()) for (var entry : list) if (entry.getValue().op() == op) {
                    double m = entry.getValue().value();
                    value = switch (op) { case SET -> m; case ADD -> value + m; case MUL -> value * m; case MIN -> Math.min(value, m); case MAX -> Math.max(value, m); };
                    if (op == Op.SET) sets++;
                }
                if (sets > 1) warnings.add("multiple_SET:" + stat + "; lexicographically last slot wins");
                stats.put(stat, Numbers.finite(value, "resolved " + stat));
            });
            return new Resolved(stats, limits, tags, warnings);
        }
    }
}
