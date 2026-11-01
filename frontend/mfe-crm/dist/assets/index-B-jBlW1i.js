import { t as __commonJSMin } from "./rolldown-runtime-SbdDWaPq.js";
import { r as importShared } from "./_virtual___federation_fn_import-CjzUj5bf.js";
import { t as require_react_dom } from "./react-dom-DC8x0RCr.js";
import { n as require_jsx_runtime, t as App } from "./App-CbZCTTEM.js";
// src/main.tsx
var import_client = (/* @__PURE__ */ __commonJSMin(((exports) => {
	var m = require_react_dom();
	exports.createRoot = m.createRoot;
	exports.hydrateRoot = m.hydrateRoot;
})))();
var import_jsx_runtime = require_jsx_runtime();
var React = await importShared("react");
var { BrowserRouter } = await importShared("react-router-dom");
(0, import_client.createRoot)(document.getElementById("root")).render(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(React.StrictMode, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(BrowserRouter, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(App, {}) }) }));
