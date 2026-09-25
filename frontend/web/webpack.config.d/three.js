// three ships an ES module build (the one react-three-fiber and drei import) and a deprecated CommonJS build
// that calls Node-only APIs at load. Kotlin/JS emits require() calls, which would pick the CommonJS copy and
// leave two copies of three in the bundle. Point every import at the same ES module.
const path = require("path");
const threeDir = path.dirname(require.resolve("three"));
config.resolve = config.resolve || {};
config.resolve.alias = Object.assign({}, config.resolve.alias, {
    "three$": path.join(threeDir, "three.module.js"),
});
