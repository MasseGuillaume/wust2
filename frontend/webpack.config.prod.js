const Webpack = require('webpack');
const CompressionPlugin = require("compression-webpack-plugin");
const BrotliPlugin = require('brotli-webpack-plugin');
const ClosureCompilerPlugin = require("webpack-closure-compiler");

// Load the config generated by scalajs-bundler
module.exports = require('./scalajs.webpack.config');

module.exports.plugins = module.exports.plugins || [];

module.exports.plugins.push(new Webpack.optimize.UglifyJsPlugin({
    compress: {
        warnings: false
    }
}));
// module.exports.plugins.push(new ClosureCompilerPlugin({
//   compiler: {
//     language_in: 'ECMASCRIPT5',
//     language_out: 'ECMASCRIPT5',
//     compilation_level: 'ADVANCED'
//   },
//   concurrency: 3,
// }));

module.exports.plugins.push(new Webpack.DefinePlugin({
  'process.env.NODE_ENV': JSON.stringify('production')
}));

var compressFiles = /\.js$|\.js.map$/;
module.exports.plugins.push(new CompressionPlugin({
  asset: "[path].gz[query]",
  algorithm: "zopfli",
  test: compressFiles,
  minRatio: 0.0
}));

module.exports.plugins.push(new BrotliPlugin({
  asset: '[path].br[query]',
  test: compressFiles,
  minRatio: 0.0
}));