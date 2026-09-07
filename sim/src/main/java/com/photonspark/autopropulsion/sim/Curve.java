package com.photonspark.autopropulsion.sim;
import java.util.List;

/** Immutable, endpoint-clamped, piecewise-linear curve. No implicit extrapolation. */
public record Curve(List<Point> points) {
    public record Point(double x, double y) {
        public Point { Numbers.finite(x, "x"); Numbers.finite(y, "y"); }
    }
    public Curve {
        points = List.copyOf(points);
        if (points.size() < 2 || points.size() > 256) throw new IllegalArgumentException("2..256 curve points required");
        for (int i = 1; i < points.size(); i++)
            if (points.get(i).x() <= points.get(i-1).x()) throw new IllegalArgumentException("x must increase strictly");
    }
    public double at(double x) {
        Numbers.finite(x, "x");
        if (x <= points.getFirst().x()) return points.getFirst().y();
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i-1), b = points.get(i);
            if (x <= b.x()) return a.y() + (b.y()-a.y()) * (x-a.x()) / (b.x()-a.x());
        }
        return points.getLast().y();
    }
    public static Curve of(double... xy) {
        if (xy.length % 2 != 0) throw new IllegalArgumentException("x/y pairs required");
        var p = new java.util.ArrayList<Point>();
        for (int i=0;i<xy.length;i+=2) p.add(new Point(xy[i],xy[i+1]));
        return new Curve(p);
    }
}
