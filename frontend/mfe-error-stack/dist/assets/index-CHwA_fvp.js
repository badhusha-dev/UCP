import { i as __toESM, n as __commonJSMin } from "./react-CjlMMrAI.js";
import { r as importShared } from "./_virtual___federation_fn_import-DYD12ulU.js";
import { t as require_react_dom } from "./react-dom-C5Au2D7d.js";
import { st as require_jsx_runtime } from "./axios-2qcjEZNH.js";
import { t as App } from "./App-taAWodzw.js";
// src/main.tsx
var import_client = /* @__PURE__ */ __toESM((/* @__PURE__ */ __commonJSMin(((exports) => {
	var m = require_react_dom();
	exports.createRoot = m.createRoot;
	exports.hydrateRoot = m.hydrateRoot;
})))(), 1);
var import_jsx_runtime = require_jsx_runtime();
var React = await importShared("react");
import_client.createRoot(document.getElementById("root")).render(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(React.StrictMode, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(App, {}) }));
