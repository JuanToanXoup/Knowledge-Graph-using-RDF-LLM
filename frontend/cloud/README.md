# frontend/cloud — 3D graph cloud view

Kotlin/JS React library that renders a knowledge graph as [term-graph](../../../term-graph)'s ink-on-paper 3D
cloud: flat discs sized by degree that drift and pull toward the focused term, curved links, camera-facing
JetBrains Mono labels, the intro spin and idle drift, click-to-focus with the side panel, and a search that
repacks the matches into their own cluster. Embedded by `:frontend:web` as the "3D Cloud" workspace tab; data
comes from the backend's `GET /graph/{id}/cloud.json` in term-graph's JSON shape.

| term-graph source | Here |
|---|---|
| `Graph.tsx` physics, motion parameters, control offsets | `Physics.kt` |
| `repack.ts` | `Repack.kt` |
| `MotionDriver.tsx` | `components/MotionDriver.kt` |
| `NodesLayer.tsx` (instanced shader discs, CPU picking) | `components/NodesLayer.kt`, shaders verbatim |
| `EdgesLayer.tsx` (shader ribbons, shockwave) | `components/EdgesLayer.kt`, shaders verbatim |
| `EdgeParticles.tsx` | `components/EdgeParticles.kt`, shaders verbatim |
| `Duotone.tsx`, `oklab.ts`, `Scene.tsx` Effects | `components/Duotone.kt`, `Oklab.kt` (`postprocessing` Effect + `EffectComposer`) |
| `LabelsLayer.tsx`, `FocusRing.tsx`, `Scene.tsx` CameraRig | same names under `components/` |
| `SearchBox.tsx`, `SidePanel.tsx`, `App.tsx` | `SearchBox.kt`, `SidePanel.kt`, `CloudView.kt` |

three.js, react-three-fiber, drei and d3-force-3d are npm dependencies with hand-written externals under
`externals/`, since JetBrains kotlin-wrappers has no bindings for them. The host stylesheet themes the overlays
(`frontend/web/src/jsMain/resources/index.css`, `.cloud-view`) and serves the fonts (`fonts/`, SIL OFL).

The scene renders through the same GPU path as term-graph: the motion driver writes live positions and animated
radii into a node data texture that the node, edge and particle shaders read, and the frame is remapped by the
duotone pass. `webpack.config.d/three.js` aliases `three` to its ES module build so fiber, drei, postprocessing
and this module share one copy.

Interaction matches the original: drag to rotate with release inertia (the drag's angular velocity, clamped to
±2.4 rad/s, decays with a 0.6 s time constant), scroll to zoom, click a term to focus it, Escape to clear the focus,
and ←/→ to step through the terms while one is focused. Keys typed into a text field are left to the field. The
pure parts of that live in `Interaction.kt` (`keyAction`, `ReleaseInertia`) and are unit tested.

Not ported: process-map and cone-tree views (`layout.ts`, `ArrowsLayer.tsx`), depth of field (disabled in the
original), dataset loading (drop, URL, picker) and the `?term=` deep link, since the host page owns the URL.
