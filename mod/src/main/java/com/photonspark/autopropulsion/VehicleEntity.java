package com.photonspark.autopropulsion;

import com.photonspark.autopropulsion.sim.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** First playable chassis: server-driven bicycle motion, four ground probes and vanilla swept collision. */
public final class VehicleEntity extends Entity {
    private static final EntityDataAccessor<Float> RPM = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOOST = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COOLANT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WHEEL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GEAR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RUNNING = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HOOD = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DTC = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PARTS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final TagKey<Block> ASPHALT = TagKey.create(Registries.BLOCK, AutoPropulsion.id("surface/asphalt"));
    public static final List<Parts.Slot> ROOT_SLOTS = List.of(new Parts.Slot("engine", "engine_block", true), new Parts.Slot("gearbox", "gearbox", true), new Parts.Slot("radiator", "radiator", true), new Parts.Slot("wheels", "wheels", true));
    private Parts.Assembly assembly = new Parts.Assembly(ROOT_SLOTS);
    private final Map<String, String> unavailableParts = new TreeMap<>();
    private final VehicleModel model = new VehicleModel(EngineSpec.reference());
    private VehicleInput input = VehicleInput.PARKED;
    private UUID owner;
    private boolean initialized;
    private long lastInputTick = Long.MIN_VALUE;
    private double requestedRedline = 6800, gripMultiplier = 1;
    private int interpolationSteps;
    private double targetX, targetY, targetZ;
    private float targetYaw;
    public float hoodAngle, oldHoodAngle;
    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) { super(type, level); blocksBuilding = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RPM, 0f); builder.define(SPEED, 0f); builder.define(BOOST, 0f); builder.define(FUEL, 30f);
        builder.define(COOLANT, 293.15f); builder.define(HEALTH, 100f); builder.define(STEER, 0f); builder.define(WHEEL, 0f);
        builder.define(GEAR, 0); builder.define(RUNNING, false); builder.define(HOOD, false); builder.define(DTC, ""); builder.define(PARTS, "");
    }
    public VehicleModel simulation() { return model; }
    public Parts.Assembly assembly() { return assembly; }
    public void setOwner(UUID id) { owner = id; }
    public boolean canModify(Player player) { return player != null && (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)); }
    public float rpm() { return entityData.get(RPM); }
    public float speed() { return entityData.get(SPEED); }
    public float fuel() { return entityData.get(FUEL); }
    public float coolantK() { return entityData.get(COOLANT); }
    public float health() { return entityData.get(HEALTH); }
    public float boost() { return entityData.get(BOOST); }
    public float steering() { return entityData.get(STEER); }
    public float wheelAngle() { return entityData.get(WHEEL); }
    public int gear() { return entityData.get(GEAR); }
    public boolean running() { return entityData.get(RUNNING); }
    public boolean hoodOpen() { return entityData.get(HOOD); }
    public String dtc() { return entityData.get(DTC); }
    public String partsDisplay() { return entityData.get(PARTS); }
    public void installDefaults() {
        if (initialized) return;
        for (String id : List.of("ref_i4", "stock_rods", "stock_cam", "stock_ecu", "manual_5speed", "street_wheels", "stock_radiator")) {
            Parts.Definition part = PartCatalog.get(AutoPropulsion.ID + ":" + id);
            assembly.install(part.preferredSlot(), part);
        }
        initialized = true; rebuild();
    }
    private Parts.Resolved resolve(Parts.Assembly candidate) {
        return candidate.resolve(Map.of("power_multiplier", 1d, "grip_multiplier", 1d, "radiator_w_per_k", 240d, "idle_rpm", 850d, "compression_ratio", 10d, "displacement_l", 2d));
    }
    private void rebuild() {
        Parts.Resolved resolved = resolve(assembly);
        model.setSpec(resolved.engineSpec(requestedRedline));
        gripMultiplier = Numbers.clamp(resolved.value("grip_multiplier", 1), .1, 1.5);
        if (!assembly.missingRequired().isEmpty() || !unavailableParts.isEmpty()) model.setRunning(false);
        StringBuilder parts = new StringBuilder();
        assembly.installed().forEach((path, part) -> parts.append(path).append('=').append(part.id()).append('\n'));
        unavailableParts.forEach((path, id) -> parts.append(path).append("=!missing:").append(id).append('\n'));
        entityData.set(PARTS, parts.toString());
        sync();
    }
    public boolean installPart(Player player, ItemStack stack) {
        if (level().isClientSide || !canModify(player) || player.distanceToSqr(this) > 64 || model.snapshot().running()) return false;
        try {
            Parts.Definition part = PartCatalog.get(PartItem.partId(stack));
            Parts.Assembly candidate = new Parts.Assembly(ROOT_SLOTS);
            assembly.installed().forEach(candidate::install);
            Parts.Definition previous = candidate.install(part.preferredSlot(), part);
            resolve(candidate).engineSpec(requestedRedline); // Validate the full result before inventory mutation.
            assembly = candidate; unavailableParts.remove(part.preferredSlot()); rebuild();
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (previous != null) returnItem(player, PartItem.stack(previous.id()));
            }
            player.displayClientMessage(Component.literal("Installed " + part.id() + " in " + part.preferredSlot()), true);
            return true;
        } catch (IllegalArgumentException ex) {
            player.displayClientMessage(Component.literal(ex.getMessage()), true); return false;
        }
    }
    private static void returnItem(Player player, ItemStack stack) { if (!player.getInventory().add(stack)) player.drop(stack, false); }
    public boolean removePart(Player player, String path) {
        if (level().isClientSide || !canModify(player) || player.distanceToSqr(this) > 64 || model.snapshot().running()) return false;
        try {
            Parts.Definition old = assembly.remove(path);
            if (old == null) return false;
            rebuild(); returnItem(player, PartItem.stack(old.id())); return true;
        } catch (IllegalArgumentException e) { player.displayClientMessage(Component.literal(e.getMessage()), true); return false; }
    }
    public void toggleEngine(Player player) {
        if (!canModify(player)) return;
        if (!model.snapshot().running() && (!assembly.missingRequired().isEmpty() || !unavailableParts.isEmpty())) {
            player.displayClientMessage(Component.literal("Missing required parts: " + assembly.missingRequired()), true); return;
        }
        model.setRunning(!model.snapshot().running()); sync();
    }
    public void adjustRedline(Player player, int delta) {
        if (!canModify(player) || model.snapshot().running()) return;
        Parts.Resolved resolved = resolve(assembly);
        if (!resolved.tags().contains("ecu/tunable")) { player.displayClientMessage(Component.literal("Install a standalone ECU to change its limiter."), true); return; }
        requestedRedline = Numbers.clamp(requestedRedline + Math.max(-200, Math.min(200, delta)), 2000, Math.min(10000, resolved.limit("rpm", 6800)));
        rebuild();
    }
    public void acceptInput(Player player, InputPayload packet) {
        if (level().isClientSide || getFirstPassenger() != player || !canModify(player)) return;
        long now = level().getGameTime();
        if (now == lastInputTick) return;
        if (!VehicleInput.validPacket(packet.throttle(), packet.brake(), packet.steer(), packet.clutch(), packet.gear())) return;
        lastInputTick = now;
        input = new VehicleInput(packet.throttle(), packet.brake(), packet.steer(), packet.clutch(), packet.gear(), (packet.flags() & 1) != 0);
        if ((packet.flags() & 2) != 0) toggleEngine(player);
        if ((packet.flags() & 4) != 0) entityData.set(HOOD, !hoodOpen());
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!canModify(player)) { player.displayClientMessage(Component.literal("This car belongs to another player."), true); return InteractionResult.FAIL; }
        if (held.is(AutoPropulsion.PART.get())) return installPart(player, held) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        if (held.is(AutoPropulsion.FUEL_CAN.get())) {
            model.refuel(10); if (!player.getAbilities().instabuild) held.shrink(1); sync(); return InteractionResult.CONSUME;
        }
        if (held.is(AutoPropulsion.LAPTOP.get()) || held.is(AutoPropulsion.WRENCH.get())) {
            if (player.isShiftKeyDown()) entityData.set(HOOD, !hoodOpen()); else openGarage(player, false);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) { entityData.set(HOOD, !hoodOpen()); return InteractionResult.CONSUME; }
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
    public void openGarage(Player player, boolean dyno) {
        if (player instanceof ServerPlayer serverPlayer && canModify(player) && player.distanceToSqr(this) <= 64) {
            List<String> paths = List.copyOf(assembly.installed().keySet());
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GarageMenu(id, inventory, this, paths, dyno), Component.literal(dyno ? "Engine Dyno" : "AutoPropulsion Workshop")), buffer -> {
                buffer.writeVarInt(getId()); buffer.writeBoolean(dyno); buffer.writeVarInt(paths.size()); paths.forEach(path -> buffer.writeUtf(path, 160));
            });
        }
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        Vec3 seat = localPoint(-.39, .63, -.20);
        move.accept(passenger, seat.x, seat.y, seat.z);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for (double side : new double[]{-1.7, 1.7}) {
            Vec3 at = localPoint(side, .1, 0);
            if (level().noCollision(passenger, passenger.getBoundingBox().move(at.subtract(passenger.position())))) return at;
        }
        return position().add(0, 1.6, 0);
    }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public float maxUpStep() { return .5f; }
    @Override protected AABB makeBoundingBox() {
        double a = Math.toRadians(getYRot()), sin = Math.abs(Math.sin(a)), cos = Math.abs(Math.cos(a));
        double x = .94 * cos + 2.16 * sin, z = 2.16 * cos + .94 * sin;
        return new AABB(getX() - x, getY(), getZ() - z, getX() + x, getY() + 1.52, getZ() + z);
    }
    private Vec3 localPoint(double x, double y, double z) {
        double a = Math.toRadians(getYRot());
        return position().add(x * Math.cos(a) - z * Math.sin(a), y, x * Math.sin(a) + z * Math.cos(a));
    }
    private double surfaceGrip() {
        double total = 0;
        for (double x : new double[]{-.82, .82}) for (double z : new double[]{-1.25, 1.25}) {
            Vec3 top = localPoint(x, .60, z), bottom = localPoint(x, -.22, z);
            BlockHitResult hit = level().clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.MISS) continue;
            var state = level().getBlockState(hit.getBlockPos());
            double mu = state.is(ASPHALT) ? 1 : state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) ? .15 : state.is(Blocks.SAND) || state.is(Blocks.SNOW_BLOCK) ? .5 : .72;
            total += mu;
        }
        return Math.min(2, total / 4 * gripMultiplier);
    }
    @Override public void tick() {
        super.tick();
        oldHoodAngle = hoodAngle; hoodAngle += ((hoodOpen() ? 1 : 0) - hoodAngle) * .24f;
        if (level().isClientSide) {
            if (interpolationSteps > 0) {
                setPos(getX() + (targetX - getX()) / interpolationSteps, getY() + (targetY - getY()) / interpolationSteps, getZ() + (targetZ - getZ()) / interpolationSteps);
                setYRot(getYRot() + Mth.wrapDegrees(targetYaw - getYRot()) / interpolationSteps); interpolationSteps--;
            }
            return;
        }
        if (!initialized && PartCatalog.size() > 0) installDefaults();
        if (getFirstPassenger() == null || lastInputTick == Long.MIN_VALUE || level().getGameTime() - lastInputTick > 10) input = VehicleInput.PARKED;
        model.setYaw(Math.toRadians(getYRot()));
        model.tick(input, ApaConfig.SUBSTEPS.get(), surfaceGrip(), ApaConfig.DAMAGE.get(), ApaConfig.FUEL.get());
        var state = model.snapshot(); setYRot((float)Math.toDegrees(state.yaw()));
        setBoundingBox(makeBoundingBox());
        double dx = -Math.sin(state.yaw()) * state.speed() * .05, dz = Math.cos(state.yaw()) * state.speed() * .05;
        double dy = isNoGravity() ? 0 : Math.max(-2, getDeltaMovement().y - 9.81 * .05 * .05);
        Vec3 before = position(); setDeltaMovement(dx, dy, dz); move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) {
            Vec3 actual = position().subtract(before);
            model.constrainSpeed((-Math.sin(state.yaw()) * actual.x + Math.cos(state.yaw()) * actual.z) / .05);
        }
        if (isInWater()) { model.setRunning(false); input = VehicleInput.PARKED; }
        sync();
    }
    private void sync() {
        var s = model.snapshot();
        entityData.set(RPM, (float)s.rpm()); entityData.set(SPEED, (float)s.speed()); entityData.set(BOOST, (float)s.boost());
        entityData.set(FUEL, (float)s.fuel()); entityData.set(COOLANT, (float)s.coolantK()); entityData.set(HEALTH, (float)s.health());
        entityData.set(STEER, (float)input.steer()); entityData.set(WHEEL, (float)s.wheelAngle()); entityData.set(GEAR, s.gear());
        entityData.set(RUNNING, s.running()); entityData.set(DTC, s.dtc());
    }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        targetX = x; targetY = y; targetZ = z; targetYaw = yaw; interpolationSteps = Math.max(1, Math.min(5, steps));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("ApaSchema", 1); if (owner != null) tag.putUUID("Owner", owner);
        tag.putBoolean("Initialized", initialized); tag.putBoolean("Hood", hoodOpen()); tag.putDouble("EcuRedline", requestedRedline);
        ListTag parts = new ListTag();
        Map<String, String> ids = new TreeMap<>(unavailableParts); assembly.installed().forEach((path, p) -> ids.put(path, p.id()));
        ids.forEach((path, id) -> { CompoundTag p = new CompoundTag(); p.putString("Slot", path); p.putString("Part", id); parts.add(p); });
        tag.put("Parts", parts);
        var s = model.snapshot();
        tag.putDouble("Speed", s.speed()); tag.putDouble("Rpm", s.rpm()); tag.putDouble("Boost", s.boost()); tag.putDouble("Fuel", s.fuel());
        tag.putDouble("Coolant", s.coolantK()); tag.putDouble("Oil", s.oilK()); tag.putDouble("Health", s.health());
        tag.putDouble("Odometer", s.odometer()); tag.putDouble("Wheel", s.wheelAngle()); tag.putInt("Gear", s.gear()); tag.putString("Dtc", s.dtc());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        initialized = tag.getBoolean("Initialized"); entityData.set(HOOD, tag.getBoolean("Hood"));
        requestedRedline = tag.contains("EcuRedline") && Double.isFinite(tag.getDouble("EcuRedline")) ? Numbers.clamp(tag.getDouble("EcuRedline"), 2000, 10000) : 6800;
        assembly = new Parts.Assembly(ROOT_SLOTS); unavailableParts.clear();
        ListTag list = tag.getList("Parts", Tag.TAG_COMPOUND);
        TreeMap<String, String> ids = new TreeMap<>();
        for (int i = 0; i < Math.min(128, list.size()); i++) { var p = list.getCompound(i); ids.put(p.getString("Slot"), p.getString("Part")); }
        ids.forEach((path, id) -> { try { assembly.install(path, PartCatalog.get(id)); } catch (IllegalArgumentException e) { unavailableParts.put(path, id); AutoPropulsion.LOG.warn("Preserving unavailable saved part {} in {}", id, path); } });
        rebuild();
        if (tag.contains("Fuel")) {
            try {
                model.restore(new VehicleModel.Snapshot(0, Math.toRadians(getYRot()), 0, 0, tag.getDouble("Fuel"), tag.getDouble("Coolant"), tag.getDouble("Oil"), tag.getDouble("Health"), tag.getDouble("Odometer"), tag.getDouble("Wheel"), false, 0, tag.getString("Dtc")));
            } catch (IllegalArgumentException e) { AutoPropulsion.LOG.error("Invalid saved vehicle state; vehicle remains safely stopped", e); model.setRunning(false); }
        }
        sync();
    }
}
