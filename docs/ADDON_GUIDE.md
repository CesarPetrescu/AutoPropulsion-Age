# Prototype add-on guide

This is an evolving alpha schema, not a stable public API. Back up worlds before changing definitions. The current loader is a server datapack JSON reload listener, not the complete set of dynamic registries proposed in the requirements.

## File layout

A custom part `example:rods_steel` belongs at:

```text
data/example/autopropulsion/parts/rods_steel.json
```

Its optional original/resource-pack mesh reference `example:vehicle/parts/rods_steel` maps to:

```text
assets/example/models/vehicle/parts/rods_steel.json
```

Use the appropriate Minecraft 1.21.1 pack metadata and provide client resources separately. Builtin inventory icons use a generated custom-model-data manifest; automatic custom addon icons and full server-to-client definition synchronization are not implemented. The generic item can still carry the part ID and the server owns compatibility/stat validation.

## Accepted definition

```json
{
  "id": "example:rods_steel",
  "category": "rods",
  "preferred_slot": "engine/rods",
  "tags": ["rods", "material/steel"],
  "requires_tags": ["engine/piston"],
  "excludes_tags": [],
  "modifiers": [],
  "limits": {"torque_nm": 450, "rpm": 8200},
  "slots": [],
  "mass_kg": 3.0,
  "implemented": true,
  "model": "example:vehicle/parts/rods_steel"
}
```

The `id` must match namespace/path. `category` must match the exposed slot's accepted category or the part's tags must satisfy it. Required/excluded tags are checked on ancestor parts, not arbitrary unrelated items elsewhere in the car. `preferred_slot` is currently used by right-click installation; arbitrary GUI slot targeting is future work.

Limits are separate positive numbers. Supported prototype engine limits are `torque_nm` and `rpm`; the weakest installed value wins and records its slot. Production modifiers accept `SET`, `ADD`, `MUL`, `MIN`, `MAX` and finite numeric values. Active resolved scalar names include `displacement_l`, `compression_ratio`, `idle_rpm`, `boost_bar`, `power_multiplier`, `race_cam`, `radiator_w_per_k` and `grip_multiplier`. Adding an arbitrary stat name does not make the simulator consume it.

A child slot has `id`, `accepts`, and `required`, for example:

```json
{"id":"turbo","accepts":"turbo","required":false}
```

`implemented: true` means the definition uses gameplay this prototype actually supports. It must not be used to label a future feature as complete. The builtins use `false` for model-study items, which the server rejects even when their model exists.

## Development validation

Use `/reload` and inspect logs for `APA_PART_CATALOG_LOADED`. Invalid definitions fail the reload atomically. Test incompatible category/family rejection, replacing a leaf, refusing parent removal with children present, and resolving the intended weakest limit. Spawn a new car after changing the catalogue; already-loaded assemblies do not yet rebind all definition objects automatically on reload.

Saved unknown IDs are preserved and stop the engine rather than being replaced by default hardware. For a real release, add migration/versioning and explicit client resource/definition synchronization before promising long-term addon compatibility.
