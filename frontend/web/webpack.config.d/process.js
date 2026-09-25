// three.js's CommonJS build reads `process.env.NODE_ENV`; webpack only defines it in production/development
// mode, and the Karma test bundle runs without a mode. Define it for every bundle so the tests can load three.
const webpack = require("webpack");
config.plugins.push(
    new webpack.DefinePlugin({
        "process.env.NODE_ENV": JSON.stringify(config.mode || "development"),
    }),
);
