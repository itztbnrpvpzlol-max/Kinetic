# Kinetic v5 – motion-editor upgrade pass

This archive contains a targeted upgrade to the supplied Android/Jetpack Compose project. It is **not** a claim that the full After Effects feature brief has been completed.

## Implemented in this pass
- Removed the automatic demo shape/keyframes seeded when a composition is created. New compositions now start with zero layers and zero keyframes.
- Added model-level Null Object creation (`kind == 3`), persisted stable parent references, a circular/invalid-parent guard, and parent reassignment via `setParent`.
- Null objects are omitted from the rendered frame output, so they do not appear in the composition render.
- Added Adjustment Layer model creation (`kind == 4`) and persistence fields. The rendering pipeline does not yet apply adjustment effects; this is intentionally not represented as working compositing.
- Added persisted keyframe interpolation mode metadata and evaluation for linear, eased, and hold segments. Existing two-value keyframes remain backward-compatible and default to linear.
- Moved the transport controls (home, previous frame, play/pause, next frame, stop) from the timeline footer into the Composition panel footer. The timeline footer remains focused on layer actions and zoom.

## Important outstanding work
The requested scope is substantially larger than this patch. The following are **not implemented or not verified**:
- Truly empty projects with zero compositions and an editor-side empty-project creation flow. The current editor still assumes at least one composition; only deserialization now accepts a zero-composition project.
- Actual adjustment-layer effect compositing, effect parameter UI, masks, audio property groups.
- Full parenting transform propagation in the renderer, a dedicated parenting UI, nested hierarchy visualization, and rename-safe parent IDs.
- Complete After Effects property set (3D position, independent scale axes, anchor point, opacity), numerical drag scrubbing, exact-value editing, stopwatch UI and keyframe clipboard/multi-select operations.
- A functional graph editor with editable Bezier tangents and velocity/influence.
- Workspace docking, draggable/resizable panels, saved workspace presets, and mobile gesture arbitration.
- Full timeline features (shy, solo, labels, snapping, robust selection/reordering).
- Undo/redo, comprehensive project isolation/compatibility migration, performance profiling, and automated interaction tests.

## Verification
The source was inspected and edited. A Gradle build was attempted, but this environment has neither `gradle` nor a project `gradlew` wrapper, so compilation and runtime tests could not be performed. No feature in this pass should be treated as device-tested.
