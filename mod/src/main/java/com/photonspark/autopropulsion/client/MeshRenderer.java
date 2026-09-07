package com.photonspark.autopropulsion.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.photonspark.autopropulsion.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Draws the exact indexed geometry imported by the Blender authoring pipeline. */
public final class MeshRenderer {
    private record ObjectMesh(float[][] vertices, int[][] faces, float[] color, String group, float[] pivot) {}
    private static final Map<ResourceLocation, List<ObjectMesh>> CACHE = new HashMap<>();
    private static final ResourceLocation WHITE = AutoPropulsion.id("textures/white.png");
    private MeshRenderer() {}
    public static void clear() { CACHE.clear(); }
    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()]; for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsFloat(); return values;
    }
    private static List<ObjectMesh> load(ResourceLocation model) {
        var location = ResourceLocation.fromNamespaceAndPath(model.getNamespace(), "models/" + model.getPath() + ".json");
        try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject(); List<ObjectMesh> objects = new ArrayList<>();
            if (root.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unsupported mesh format");
            for (JsonElement entry : root.getAsJsonArray("objects")) {
                JsonObject object = entry.getAsJsonObject(); JsonArray verts = object.getAsJsonArray("vertices"), faces = object.getAsJsonArray("faces");
                if (verts.size() > 50000 || faces.size() > 20000) throw new IllegalArgumentException("Mesh exceeds renderer budget");
                float[][] points = new float[verts.size()][]; int[][] indices = new int[faces.size()][];
                for (int i = 0; i < points.length; i++) points[i] = floats(verts.get(i).getAsJsonArray());
                for (int i = 0; i < indices.length; i++) {
                    JsonArray face = faces.get(i).getAsJsonArray(); indices[i] = new int[face.size()];
                    if (face.size() < 3 || face.size() > 4) throw new IllegalArgumentException("Only triangles and quads are supported");
                    for (int j = 0; j < face.size(); j++) { indices[i][j] = face.get(j).getAsInt(); if (indices[i][j] < 0 || indices[i][j] >= points.length) throw new IllegalArgumentException("Invalid vertex index"); }
                }
                objects.add(new ObjectMesh(points, indices, floats(object.getAsJsonArray("color")), object.get("group").getAsString(), floats(object.getAsJsonArray("pivot"))));
            }
            return List.copyOf(objects);
        } catch (Exception e) { AutoPropulsion.LOG.error("Unable to load original model {}", model, e); return List.of(); }
    }
    public static void render(ResourceLocation model, PoseStack pose, MultiBufferSource buffers, int light, float steer, float spin, float hood, boolean engineVisible) {
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for (ObjectMesh object : CACHE.computeIfAbsent(model, MeshRenderer::load)) {
            if (object.group.equals("engine") && !engineVisible) continue;
            pose.pushPose();
            if (object.group.startsWith("wheel_")) {
                pose.translate(object.pivot[0], object.pivot[1], object.pivot[2]);
                if (object.group.startsWith("wheel_f")) pose.mulPose(Axis.YP.rotation(-steer * .49f));
                pose.mulPose(Axis.XP.rotation(spin));
                pose.translate(-object.pivot[0], -object.pivot[1], -object.pivot[2]);
            } else if (object.group.equals("hood")) {
                pose.translate(object.pivot[0], object.pivot[1], object.pivot[2]); pose.mulPose(Axis.XP.rotationDegrees(-65 * hood));
                pose.translate(-object.pivot[0], -object.pivot[1], -object.pivot[2]);
            }
            PoseStack.Pose transform = pose.last();
            for (int[] face : object.faces) {
                float[] a = object.vertices[face[0]], b = object.vertices[face[1]], c = object.vertices[face[2]];
                float ux = b[0]-a[0], uy = b[1]-a[1], uz = b[2]-a[2], vx = c[0]-a[0], vy = c[1]-a[1], vz = c[2]-a[2];
                float nx = uy*vz-uz*vy, ny = uz*vx-ux*vz, nz = ux*vy-uy*vx;
                float length = (float)Math.sqrt(nx*nx+ny*ny+nz*nz); if (length < 1e-8) continue;
                for (int i = 0; i < 4; i++) {
                    float[] p = object.vertices[face[Math.min(i, face.length-1)]];
                    vertex.addVertex(transform, p[0], p[1], p[2]).setColor(object.color[0], object.color[1], object.color[2], object.color[3])
                        .setUv(i == 0 || i == 3 ? 0 : 1, i < 2 ? 0 : 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(transform, nx/length, ny/length, nz/length);
                }
            }
            pose.popPose();
        }
    }
}
