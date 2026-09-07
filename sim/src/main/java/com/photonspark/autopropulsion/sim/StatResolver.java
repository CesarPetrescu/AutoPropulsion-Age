package com.photonspark.autopropulsion.sim;
import java.util.*;

/** Production modifiers and structural limits are deliberately separate APIs. */
public final class StatResolver {
    public enum Op { SET, ADD, MUL, MIN, MAX }
    public record Modifier(String source, String stat, Op op, double value) {
        public Modifier { Objects.requireNonNull(source); Objects.requireNonNull(stat); Objects.requireNonNull(op); Numbers.finite(value,"modifier"); }
    }
    public record Limit(String source, double value) {
        public Limit { Objects.requireNonNull(source); Numbers.positive(value,"limit"); }
    }
    private StatResolver() {}
    public static Map<String,Double> evaluate(Map<String,Double> base, List<Modifier> modifiers) {
        var result = new TreeMap<String,Double>(base);
        result.forEach((k,v)->Numbers.finite(v,k));
        var ordered = modifiers.stream().sorted(Comparator.comparing(Modifier::source)).toList();
        for (Op op : Op.values()) for (Modifier m : ordered) if (m.op()==op) {
            double old = result.getOrDefault(m.stat(),0.0);
            double value = switch(op) {
                case SET -> m.value(); case ADD -> old+m.value(); case MUL -> old*m.value();
                case MIN -> Math.min(old,m.value()); case MAX -> Math.max(old,m.value());
            };
            result.put(m.stat(), Numbers.finite(value,m.stat()));
        }
        return Collections.unmodifiableMap(result);
    }
    public static Limit weakest(Collection<Limit> limits) {
        return limits.stream().min(Comparator.comparingDouble(Limit::value).thenComparing(Limit::source))
            .orElseThrow(()->new IllegalArgumentException("At least one structural limit required"));
    }
}
