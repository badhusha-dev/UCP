import { r as __toESM, t as __commonJSMin } from "./rolldown-runtime-SbdDWaPq.js";
import { r as importShared } from "./_virtual___federation_fn_import-7k9UwJ4Q.js";
import { t as require_react_dom } from "./react-dom-DC8x0RCr.js";
import { D as globalCss, F as DefaultPropsProvider, I as RtlProvider, N as createTheme, P as createTypography, T as useDefaultProps, Y as useId, _t as GlobalStyles$2, bt as identifier_default, ft as styleFunctionSx, lt as GlobalStyles$1, nt as useEnhancedEffect, ut as useTheme$1, vt as require_jsx_runtime, yt as ThemeContext$1 } from "./Stack-CWAApUdV.js";
import { t as App } from "./App-uubm3Syb.js";
import { s as store } from "./ShoppingCartOutlined-BvL5uOlN.js";
// ../node_modules/react-dom/client.js
var require_client = /* @__PURE__ */ __commonJSMin(((exports) => {
	var m = require_react_dom();
	exports.createRoot = m.createRoot;
	exports.hydrateRoot = m.hydrateRoot;
}));
// ../node_modules/@mui/private-theming/useTheme/ThemeContext.js
var ThemeContext = /*#__PURE__*/ (await importShared("react")).createContext(null);
// ../node_modules/@mui/private-theming/useTheme/useTheme.js
var React$7 = await importShared("react");
function useTheme() {
	return React$7.useContext(ThemeContext);
}
var nested_default = typeof Symbol === "function" && Symbol.for ? Symbol.for("mui.nested") : "__THEME_NESTED__";
// ../node_modules/@mui/private-theming/ThemeProvider/ThemeProvider.js
var import_jsx_runtime = require_jsx_runtime();
var React$6 = await importShared("react");
function mergeOuterLocalTheme(outerTheme, localTheme) {
	if (typeof localTheme === "function") return localTheme(outerTheme);
	return {
		...outerTheme,
		...localTheme
	};
}
/**
* This component takes a `theme` prop.
* It makes the `theme` available down the React tree thanks to React context.
* This component should preferably be used at **the root of your component tree**.
*/
function ThemeProvider$2(props) {
	const { children, theme: localTheme } = props;
	const outerTheme = useTheme();
	const theme = React$6.useMemo(() => {
		const output = outerTheme === null ? { ...localTheme } : mergeOuterLocalTheme(outerTheme, localTheme);
		if (output != null) output[nested_default] = outerTheme !== null;
		return output;
	}, [localTheme, outerTheme]);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ThemeContext.Provider, {
		value: theme,
		children
	});
}
// ../node_modules/@mui/system/esm/ThemeProvider/useLayerOrder.js
/**
* This hook returns a `GlobalStyles` component that sets the CSS layer order (for server-side rendering).
* Then on client-side, it injects the CSS layer order into the document head to ensure that the layer order is always present first before other Emotion styles.
*/
await importShared("react");
function useLayerOrder(theme) {
	const upperTheme = useTheme$1();
	const id = useId() || "";
	const { modularCssLayers } = theme;
	let layerOrder = "mui.global, mui.components, mui.theme, mui.custom, mui.sx";
	if (!modularCssLayers || upperTheme !== null) layerOrder = "";
	else if (typeof modularCssLayers === "string") layerOrder = modularCssLayers.replace(/mui(?!\.)/g, layerOrder);
	else layerOrder = `@layer ${layerOrder};`;
	useEnhancedEffect(() => {
		const head = document.querySelector("head");
		if (!head) return;
		const firstChild = head.firstChild;
		if (layerOrder) {
			if (firstChild && firstChild.hasAttribute?.("data-mui-layer-order") && firstChild.getAttribute("data-mui-layer-order") === id) return;
			const styleElement = document.createElement("style");
			styleElement.setAttribute("data-mui-layer-order", id);
			styleElement.textContent = layerOrder;
			head.prepend(styleElement);
		} else head.querySelector(`style[data-mui-layer-order="${id}"]`)?.remove();
	}, [layerOrder, id]);
	if (!layerOrder) return null;
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(GlobalStyles$1, { styles: layerOrder });
}
// ../node_modules/@mui/system/esm/ThemeProvider/ThemeProvider.js
var React$5 = await importShared("react");
var EMPTY_THEME = {};
function useThemeScoping(themeId, upperTheme, localTheme, isPrivate = false) {
	return React$5.useMemo(() => {
		const resolvedTheme = themeId ? upperTheme[themeId] || upperTheme : upperTheme;
		if (typeof localTheme === "function") {
			const mergedTheme = localTheme(resolvedTheme);
			const result = themeId ? {
				...upperTheme,
				[themeId]: mergedTheme
			} : mergedTheme;
			if (isPrivate) return () => result;
			return result;
		}
		return themeId ? {
			...upperTheme,
			[themeId]: localTheme
		} : {
			...upperTheme,
			...localTheme
		};
	}, [
		themeId,
		upperTheme,
		localTheme,
		isPrivate
	]);
}
/**
* This component makes the `theme` available down the React tree.
* It should preferably be used at **the root of your component tree**.
*
* <ThemeProvider theme={theme}> // existing use case
* <ThemeProvider theme={{ id: theme }}> // theme scoping
*/
function ThemeProvider$1(props) {
	const { children, theme: localTheme, themeId } = props;
	const upperTheme = useTheme$1(EMPTY_THEME);
	const upperPrivateTheme = useTheme() || EMPTY_THEME;
	const engineTheme = useThemeScoping(themeId, upperTheme, localTheme);
	const privateTheme = useThemeScoping(themeId, upperPrivateTheme, localTheme, true);
	const rtlValue = (themeId ? engineTheme[themeId] : engineTheme).direction === "rtl";
	const layerOrder = useLayerOrder(engineTheme);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ThemeProvider$2, {
		theme: privateTheme,
		children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ThemeContext$1.Provider, {
			value: engineTheme,
			children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(RtlProvider, {
				value: rtlValue,
				children: /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(DefaultPropsProvider, {
					value: themeId ? engineTheme[themeId].components : engineTheme.components,
					children: [layerOrder, children]
				})
			})
		})
	});
}
// ../node_modules/@mui/system/esm/InitColorSchemeScript/InitColorSchemeScript.js
await importShared("react");
var DEFAULT_MODE_STORAGE_KEY = "mode";
var DEFAULT_COLOR_SCHEME_STORAGE_KEY = "color-scheme";
var DEFAULT_ATTRIBUTE = "data-color-scheme";
function InitColorSchemeScript(options) {
	const { defaultMode = "system", defaultLightColorScheme = "light", defaultDarkColorScheme = "dark", modeStorageKey = DEFAULT_MODE_STORAGE_KEY, colorSchemeStorageKey = DEFAULT_COLOR_SCHEME_STORAGE_KEY, attribute: initialAttribute = DEFAULT_ATTRIBUTE, colorSchemeNode = "document.documentElement", nonce } = options || {};
	let setter = "";
	let attribute = initialAttribute;
	if (initialAttribute === "class") attribute = ".%s";
	if (initialAttribute === "data") attribute = "[data-%s]";
	if (attribute.startsWith(".")) {
		const selector = attribute.substring(1);
		setter += `${colorSchemeNode}.classList.remove('${selector}'.replace('%s', light), '${selector}'.replace('%s', dark));
      ${colorSchemeNode}.classList.add('${selector}'.replace('%s', colorScheme));`;
	}
	const matches = attribute.match(/\[([^\]]+)\]/);
	if (matches) {
		const [attr, value] = matches[1].split("=");
		if (!value) setter += `${colorSchemeNode}.removeAttribute('${attr}'.replace('%s', light));
      ${colorSchemeNode}.removeAttribute('${attr}'.replace('%s', dark));`;
		setter += `
      ${colorSchemeNode}.setAttribute('${attr}'.replace('%s', colorScheme), ${value ? `${value}.replace('%s', colorScheme)` : "\"\""});`;
	} else setter += `${colorSchemeNode}.setAttribute('${attribute}', colorScheme);`;
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)("script", {
		suppressHydrationWarning: true,
		nonce: typeof window === "undefined" ? nonce : "",
		dangerouslySetInnerHTML: { __html: `(function() {
try {
  let colorScheme = '';
  const mode = localStorage.getItem('${modeStorageKey}') || '${defaultMode}';
  const dark = localStorage.getItem('${colorSchemeStorageKey}-dark') || '${defaultDarkColorScheme}';
  const light = localStorage.getItem('${colorSchemeStorageKey}-light') || '${defaultLightColorScheme}';
  if (mode === 'system') {
    // handle system mode
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    if (mql.matches) {
      colorScheme = dark
    } else {
      colorScheme = light
    }
  }
  if (mode === 'light') {
    colorScheme = light;
  }
  if (mode === 'dark') {
    colorScheme = dark;
  }
  if (colorScheme) {
    ${setter}
  }
} catch(e){}})();` }
	}, "mui-color-scheme-init");
}
// ../node_modules/@mui/system/esm/cssVars/localStorageManager.js
function noop$1() {}
var localStorageManager = ({ key, storageWindow }) => {
	if (!storageWindow && typeof window !== "undefined") storageWindow = window;
	return {
		get(defaultValue) {
			if (typeof window === "undefined") return;
			if (!storageWindow) return defaultValue;
			let value;
			try {
				value = storageWindow.localStorage.getItem(key);
			} catch {}
			return value || defaultValue;
		},
		set: (value) => {
			if (storageWindow) try {
				storageWindow.localStorage.setItem(key, value);
			} catch {}
		},
		subscribe: (handler) => {
			if (!storageWindow) return noop$1;
			const listener = (event) => {
				const value = event.newValue;
				if (event.key === key) handler(value);
			};
			storageWindow.addEventListener("storage", listener);
			return () => {
				storageWindow.removeEventListener("storage", listener);
			};
		}
	};
};
// ../node_modules/@mui/system/esm/cssVars/useCurrentColorScheme.js
var React$4 = await importShared("react");
function noop() {}
function getSystemMode(mode) {
	if (typeof window !== "undefined" && typeof window.matchMedia === "function" && mode === "system") {
		if (window.matchMedia("(prefers-color-scheme: dark)").matches) return "dark";
		return "light";
	}
}
function processState(state, callback) {
	if (state.mode === "light" || state.mode === "system" && state.systemMode === "light") return callback("light");
	if (state.mode === "dark" || state.mode === "system" && state.systemMode === "dark") return callback("dark");
}
function getColorScheme(state) {
	return processState(state, (mode) => {
		if (mode === "light") return state.lightColorScheme;
		if (mode === "dark") return state.darkColorScheme;
	});
}
function useCurrentColorScheme(options) {
	const { defaultMode = "light", defaultLightColorScheme, defaultDarkColorScheme, supportedColorSchemes = [], modeStorageKey = DEFAULT_MODE_STORAGE_KEY, colorSchemeStorageKey = DEFAULT_COLOR_SCHEME_STORAGE_KEY, storageWindow = typeof window === "undefined" ? void 0 : window, storageManager = localStorageManager, noSsr = false } = options;
	const joinedColorSchemes = supportedColorSchemes.join(",");
	const isMultiSchemes = supportedColorSchemes.length > 1;
	const modeStorage = React$4.useMemo(() => storageManager?.({
		key: modeStorageKey,
		storageWindow
	}), [
		storageManager,
		modeStorageKey,
		storageWindow
	]);
	const lightStorage = React$4.useMemo(() => storageManager?.({
		key: `${colorSchemeStorageKey}-light`,
		storageWindow
	}), [
		storageManager,
		colorSchemeStorageKey,
		storageWindow
	]);
	const darkStorage = React$4.useMemo(() => storageManager?.({
		key: `${colorSchemeStorageKey}-dark`,
		storageWindow
	}), [
		storageManager,
		colorSchemeStorageKey,
		storageWindow
	]);
	const [state, setState] = React$4.useState(() => {
		const initialMode = modeStorage?.get(defaultMode) || defaultMode;
		const lightColorScheme = lightStorage?.get(defaultLightColorScheme) || defaultLightColorScheme;
		const darkColorScheme = darkStorage?.get(defaultDarkColorScheme) || defaultDarkColorScheme;
		return {
			mode: initialMode,
			systemMode: getSystemMode(initialMode),
			lightColorScheme,
			darkColorScheme
		};
	});
	const [isClient, setIsClient] = React$4.useState(noSsr || !isMultiSchemes);
	React$4.useEffect(() => {
		setIsClient(true);
	}, []);
	const colorScheme = getColorScheme(state);
	const setMode = React$4.useCallback((mode) => {
		setState((currentState) => {
			if (mode === currentState.mode) return currentState;
			const newMode = mode ?? defaultMode;
			modeStorage?.set(newMode);
			return {
				...currentState,
				mode: newMode,
				systemMode: getSystemMode(newMode)
			};
		});
	}, [modeStorage, defaultMode]);
	const setColorScheme = React$4.useCallback((value) => {
		if (!value) setState((currentState) => {
			lightStorage?.set(defaultLightColorScheme);
			darkStorage?.set(defaultDarkColorScheme);
			return {
				...currentState,
				lightColorScheme: defaultLightColorScheme,
				darkColorScheme: defaultDarkColorScheme
			};
		});
		else if (typeof value === "string") {
			if (value && !joinedColorSchemes.includes(value)) console.error(`\`${value}\` does not exist in \`theme.colorSchemes\`.`);
			else setState((currentState) => {
				const newState = { ...currentState };
				processState(currentState, (mode) => {
					if (mode === "light") {
						lightStorage?.set(value);
						newState.lightColorScheme = value;
					}
					if (mode === "dark") {
						darkStorage?.set(value);
						newState.darkColorScheme = value;
					}
				});
				return newState;
			});
		} else setState((currentState) => {
			const newState = { ...currentState };
			const newLightColorScheme = value.light === null ? defaultLightColorScheme : value.light;
			const newDarkColorScheme = value.dark === null ? defaultDarkColorScheme : value.dark;
			if (newLightColorScheme) {
				if (!joinedColorSchemes.includes(newLightColorScheme)) console.error(`\`${newLightColorScheme}\` does not exist in \`theme.colorSchemes\`.`);
				else {
					newState.lightColorScheme = newLightColorScheme;
					lightStorage?.set(newLightColorScheme);
				}
			}
			if (newDarkColorScheme) {
				if (!joinedColorSchemes.includes(newDarkColorScheme)) console.error(`\`${newDarkColorScheme}\` does not exist in \`theme.colorSchemes\`.`);
				else {
					newState.darkColorScheme = newDarkColorScheme;
					darkStorage?.set(newDarkColorScheme);
				}
			}
			return newState;
		});
	}, [
		joinedColorSchemes,
		lightStorage,
		darkStorage,
		defaultLightColorScheme,
		defaultDarkColorScheme
	]);
	const handleMediaQuery = React$4.useCallback((event) => {
		if (state.mode === "system") setState((currentState) => {
			const systemMode = event?.matches ? "dark" : "light";
			if (currentState.systemMode === systemMode) return currentState;
			return {
				...currentState,
				systemMode
			};
		});
	}, [state.mode]);
	const mediaListener = React$4.useRef(handleMediaQuery);
	mediaListener.current = handleMediaQuery;
	React$4.useEffect(() => {
		if (typeof window.matchMedia !== "function" || !isMultiSchemes) return;
		const handler = (...args) => mediaListener.current(...args);
		const media = window.matchMedia("(prefers-color-scheme: dark)");
		media.addListener(handler);
		handler(media);
		return () => {
			media.removeListener(handler);
		};
	}, [isMultiSchemes]);
	React$4.useEffect(() => {
		if (isMultiSchemes) {
			const unsubscribeMode = modeStorage?.subscribe((value) => {
				if (!value || [
					"light",
					"dark",
					"system"
				].includes(value)) setMode(value || defaultMode);
			}) || noop;
			const unsubscribeLight = lightStorage?.subscribe((value) => {
				if (!value || joinedColorSchemes.match(value)) setColorScheme({ light: value });
			}) || noop;
			const unsubscribeDark = darkStorage?.subscribe((value) => {
				if (!value || joinedColorSchemes.match(value)) setColorScheme({ dark: value });
			}) || noop;
			return () => {
				unsubscribeMode();
				unsubscribeLight();
				unsubscribeDark();
			};
		}
	}, [
		setColorScheme,
		setMode,
		joinedColorSchemes,
		defaultMode,
		storageWindow,
		isMultiSchemes,
		modeStorage,
		lightStorage,
		darkStorage
	]);
	return {
		...state,
		mode: isClient ? state.mode : void 0,
		systemMode: isClient ? state.systemMode : void 0,
		colorScheme: isClient ? colorScheme : void 0,
		setMode,
		setColorScheme
	};
}
// ../node_modules/@mui/system/esm/cssVars/createCssVarsProvider.js
var React$3 = await importShared("react");
var DISABLE_CSS_TRANSITION = "*{-webkit-transition:none!important;-moz-transition:none!important;-o-transition:none!important;-ms-transition:none!important;transition:none!important}";
function createCssVarsProvider(options) {
	const { themeId, theme: defaultTheme = {}, modeStorageKey: defaultModeStorageKey = DEFAULT_MODE_STORAGE_KEY, colorSchemeStorageKey: defaultColorSchemeStorageKey = DEFAULT_COLOR_SCHEME_STORAGE_KEY, disableTransitionOnChange: designSystemTransitionOnChange = false, defaultColorScheme, resolveTheme } = options;
	const defaultContext = {
		allColorSchemes: [],
		colorScheme: void 0,
		darkColorScheme: void 0,
		lightColorScheme: void 0,
		mode: void 0,
		setColorScheme: () => {},
		setMode: () => {},
		systemMode: void 0
	};
	const ColorSchemeContext = /*#__PURE__*/ React$3.createContext(void 0);
	const useColorScheme = () => React$3.useContext(ColorSchemeContext) || defaultContext;
	const defaultColorSchemes = {};
	const defaultComponents = {};
	function CssVarsProvider(props) {
		const { children, theme: themeProp, modeStorageKey = defaultModeStorageKey, colorSchemeStorageKey = defaultColorSchemeStorageKey, disableTransitionOnChange = designSystemTransitionOnChange, storageManager, storageWindow = typeof window === "undefined" ? void 0 : window, documentNode = typeof document === "undefined" ? void 0 : document, colorSchemeNode = typeof document === "undefined" ? void 0 : document.documentElement, disableNestedContext = false, disableStyleSheetGeneration = false, defaultMode: initialMode = "system", noSsr } = props;
		const hasMounted = React$3.useRef(false);
		const upperTheme = useTheme();
		const ctx = React$3.useContext(ColorSchemeContext);
		const nested = !!ctx && !disableNestedContext;
		const initialTheme = React$3.useMemo(() => {
			if (themeProp) return themeProp;
			return typeof defaultTheme === "function" ? defaultTheme() : defaultTheme;
		}, [themeProp]);
		const scopedTheme = initialTheme[themeId];
		const restThemeProp = scopedTheme || initialTheme;
		const { colorSchemes = defaultColorSchemes, components = defaultComponents, cssVarPrefix } = restThemeProp;
		const joinedColorSchemes = Object.keys(colorSchemes).filter((k) => !!colorSchemes[k]).join(",");
		const allColorSchemes = React$3.useMemo(() => joinedColorSchemes.split(","), [joinedColorSchemes]);
		const defaultLightColorScheme = typeof defaultColorScheme === "string" ? defaultColorScheme : defaultColorScheme.light;
		const defaultDarkColorScheme = typeof defaultColorScheme === "string" ? defaultColorScheme : defaultColorScheme.dark;
		const { mode: stateMode, setMode, systemMode, lightColorScheme, darkColorScheme, colorScheme: stateColorScheme, setColorScheme } = useCurrentColorScheme({
			supportedColorSchemes: allColorSchemes,
			defaultLightColorScheme,
			defaultDarkColorScheme,
			modeStorageKey,
			colorSchemeStorageKey,
			defaultMode: colorSchemes[defaultLightColorScheme] && colorSchemes[defaultDarkColorScheme] ? initialMode : colorSchemes[restThemeProp.defaultColorScheme]?.palette?.mode || restThemeProp.palette?.mode,
			storageManager,
			storageWindow,
			noSsr
		});
		let mode = stateMode;
		let colorScheme = stateColorScheme;
		if (nested) {
			mode = ctx.mode;
			colorScheme = ctx.colorScheme;
		}
		const memoTheme = React$3.useMemo(() => {
			const calculatedColorScheme = colorScheme || restThemeProp.defaultColorScheme;
			const themeVars = restThemeProp.generateThemeVars?.() || restThemeProp.vars;
			const theme = {
				...restThemeProp,
				components,
				colorSchemes,
				cssVarPrefix,
				vars: themeVars
			};
			if (typeof theme.generateSpacing === "function") theme.spacing = theme.generateSpacing();
			if (calculatedColorScheme) {
				const scheme = colorSchemes[calculatedColorScheme];
				if (scheme && typeof scheme === "object") Object.keys(scheme).forEach((schemeKey) => {
					if (scheme[schemeKey] && typeof scheme[schemeKey] === "object") theme[schemeKey] = {
						...theme[schemeKey],
						...scheme[schemeKey]
					};
					else theme[schemeKey] = scheme[schemeKey];
				});
			}
			return resolveTheme ? resolveTheme(theme) : theme;
		}, [
			restThemeProp,
			colorScheme,
			components,
			colorSchemes,
			cssVarPrefix
		]);
		const colorSchemeSelector = restThemeProp.colorSchemeSelector;
		useEnhancedEffect(() => {
			if (colorScheme && colorSchemeNode && colorSchemeSelector && colorSchemeSelector !== "media") {
				const selector = colorSchemeSelector;
				let rule = colorSchemeSelector;
				if (selector === "class") rule = `.%s`;
				if (selector === "data") rule = `[data-%s]`;
				if (selector?.startsWith("data-") && !selector.includes("%s")) rule = `[${selector}="%s"]`;
				if (rule.startsWith(".")) {
					colorSchemeNode.classList.remove(...allColorSchemes.map((scheme) => rule.substring(1).replace("%s", scheme)));
					colorSchemeNode.classList.add(rule.substring(1).replace("%s", colorScheme));
				} else {
					const matches = rule.replace("%s", colorScheme).match(/\[([^\]]+)\]/);
					if (matches) {
						const [attr, value] = matches[1].split("=");
						if (!value) allColorSchemes.forEach((scheme) => {
							colorSchemeNode.removeAttribute(attr.replace(colorScheme, scheme));
						});
						colorSchemeNode.setAttribute(attr, value ? value.replace(/"|'/g, "") : "");
					} else colorSchemeNode.setAttribute(rule, colorScheme);
				}
			}
		}, [
			colorScheme,
			colorSchemeSelector,
			colorSchemeNode,
			allColorSchemes
		]);
		React$3.useEffect(() => {
			let timer;
			if (disableTransitionOnChange && hasMounted.current && documentNode) {
				const css = documentNode.createElement("style");
				css.appendChild(documentNode.createTextNode(DISABLE_CSS_TRANSITION));
				documentNode.head.appendChild(css);
				(() => window.getComputedStyle(documentNode.body))();
				timer = setTimeout(() => {
					documentNode.head.removeChild(css);
				}, 1);
			}
			return () => {
				clearTimeout(timer);
			};
		}, [
			colorScheme,
			disableTransitionOnChange,
			documentNode
		]);
		React$3.useEffect(() => {
			hasMounted.current = true;
			return () => {
				hasMounted.current = false;
			};
		}, []);
		const contextValue = React$3.useMemo(() => ({
			allColorSchemes,
			colorScheme,
			darkColorScheme,
			lightColorScheme,
			mode,
			setColorScheme,
			setMode,
			systemMode
		}), [
			allColorSchemes,
			colorScheme,
			darkColorScheme,
			lightColorScheme,
			mode,
			setColorScheme,
			setMode,
			systemMode,
			memoTheme.colorSchemeSelector
		]);
		let shouldGenerateStyleSheet = true;
		if (disableStyleSheetGeneration || restThemeProp.cssVariables === false || nested && upperTheme?.cssVarPrefix === cssVarPrefix) shouldGenerateStyleSheet = false;
		const element = /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(React$3.Fragment, { children: [/*#__PURE__*/ (0, import_jsx_runtime.jsx)(ThemeProvider$1, {
			themeId: scopedTheme ? themeId : void 0,
			theme: memoTheme,
			children
		}), shouldGenerateStyleSheet && /*#__PURE__*/ (0, import_jsx_runtime.jsx)(GlobalStyles$2, { styles: memoTheme.generateStyleSheets?.() || [] })] });
		if (nested) return element;
		return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ColorSchemeContext.Provider, {
			value: contextValue,
			children: element
		});
	}
	const defaultLightColorScheme = typeof defaultColorScheme === "string" ? defaultColorScheme : defaultColorScheme.light;
	const defaultDarkColorScheme = typeof defaultColorScheme === "string" ? defaultColorScheme : defaultColorScheme.dark;
	const getInitColorSchemeScript = (params) => InitColorSchemeScript({
		colorSchemeStorageKey: defaultColorSchemeStorageKey,
		defaultLightColorScheme,
		defaultDarkColorScheme,
		modeStorageKey: defaultModeStorageKey,
		...params
	});
	return {
		CssVarsProvider,
		useColorScheme,
		getInitColorSchemeScript
	};
}
// ../node_modules/@mui/material/styles/ThemeProviderNoVars.js
await importShared("react");
function ThemeProviderNoVars({ theme: themeInput, ...props }) {
	const scopedTheme = "$$material" in themeInput ? themeInput[identifier_default] : void 0;
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ThemeProvider$1, {
		...props,
		themeId: scopedTheme ? identifier_default : void 0,
		theme: scopedTheme || themeInput
	});
}
// ../node_modules/@mui/material/InitColorSchemeScript/InitColorSchemeScript.js
await importShared("react");
var defaultConfig = {
	attribute: "data-mui-color-scheme",
	colorSchemeStorageKey: "mui-color-scheme",
	defaultLightColorScheme: "light",
	defaultDarkColorScheme: "dark",
	modeStorageKey: "mui-mode"
};
// ../node_modules/@mui/material/styles/ThemeProviderWithVars.js
await importShared("react");
var { CssVarsProvider: InternalCssVarsProvider, useColorScheme, getInitColorSchemeScript: deprecatedGetInitColorSchemeScript } = createCssVarsProvider({
	themeId: identifier_default,
	theme: () => createTheme({ cssVariables: true }),
	colorSchemeStorageKey: defaultConfig.colorSchemeStorageKey,
	modeStorageKey: defaultConfig.modeStorageKey,
	defaultColorScheme: {
		light: defaultConfig.defaultLightColorScheme,
		dark: defaultConfig.defaultDarkColorScheme
	},
	resolveTheme: (theme) => {
		const newTheme = {
			...theme,
			typography: createTypography(theme.palette, theme.typography)
		};
		newTheme.unstable_sx = function sx(props) {
			return styleFunctionSx({
				sx: props,
				theme: this
			});
		};
		return newTheme;
	}
});
/**
* TODO: remove this export in v7
* @deprecated
* The `CssVarsProvider` component has been deprecated and ported into `ThemeProvider`.
*
* You should use `ThemeProvider` and `createTheme()` instead:
*
* ```diff
* - import { CssVarsProvider, extendTheme } from '@mui/material/styles';
* + import { ThemeProvider, createTheme } from '@mui/material/styles';
*
* - const theme = extendTheme();
* + const theme = createTheme({
* +   cssVariables: true,
* +   colorSchemes: { light: true, dark: true },
* + });
*
* - <CssVarsProvider theme={theme}>
* + <ThemeProvider theme={theme}>
* ```
*
* To see the full documentation, check out https://mui.com/material-ui/customization/css-theme-variables/usage/.
*/
var CssVarsProvider = InternalCssVarsProvider;
// ../node_modules/@mui/material/styles/ThemeProvider.js
var React$2 = await importShared("react");
function ThemeProvider({ theme, ...props }) {
	const noVarsTheme = React$2.useMemo(() => {
		if (typeof theme === "function") return theme;
		const muiTheme = "$$material" in theme ? theme[identifier_default] : theme;
		if (!("colorSchemes" in muiTheme)) {
			if (!("vars" in muiTheme)) return {
				...theme,
				vars: null
			};
			return theme;
		}
		return null;
	}, [theme]);
	if (noVarsTheme) return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ThemeProviderNoVars, {
		theme: noVarsTheme,
		...props
	});
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(CssVarsProvider, {
		theme,
		...props
	});
}
// ../node_modules/@mui/material/CssBaseline/CssBaseline.js
var React$1 = await importShared("react");
var isDynamicSupport = typeof globalCss({}) === "function";
var html = (theme, enableColorScheme) => ({
	WebkitFontSmoothing: "antialiased",
	MozOsxFontSmoothing: "grayscale",
	boxSizing: "border-box",
	WebkitTextSizeAdjust: "100%",
	...enableColorScheme && !theme.vars && { colorScheme: theme.palette.mode }
});
var body = (theme) => ({
	color: (theme.vars || theme).palette.text.primary,
	...theme.typography.body1,
	backgroundColor: (theme.vars || theme).palette.background.default,
	"@media print": { backgroundColor: (theme.vars || theme).palette.common.white }
});
var styles = (theme, enableColorScheme = false) => {
	const colorSchemeStyles = {};
	if (enableColorScheme && theme.colorSchemes && typeof theme.getColorSchemeSelector === "function") Object.entries(theme.colorSchemes).forEach(([key, scheme]) => {
		const selector = theme.getColorSchemeSelector(key);
		if (selector.startsWith("@")) colorSchemeStyles[selector] = { ":root": { colorScheme: scheme.palette?.mode } };
		else colorSchemeStyles[selector.replace(/\s*&/, "")] = { colorScheme: scheme.palette?.mode };
	});
	let defaultStyles = {
		html: html(theme, enableColorScheme),
		"*, *::before, *::after": { boxSizing: "inherit" },
		"strong, b": { fontWeight: theme.typography.fontWeightBold },
		body: {
			margin: 0,
			...body(theme),
			"&::backdrop": { backgroundColor: (theme.vars || theme).palette.background.default }
		},
		...colorSchemeStyles
	};
	const themeOverrides = theme.components?.MuiCssBaseline?.styleOverrides;
	if (themeOverrides) defaultStyles = [defaultStyles, themeOverrides];
	return defaultStyles;
};
var SELECTOR = "mui-ecs";
var staticStyles = (theme) => {
	const result = styles(theme, false);
	const baseStyles = Array.isArray(result) ? result[0] : result;
	if (!theme.vars && baseStyles) baseStyles.html[`:root:has(${SELECTOR})`] = { colorScheme: theme.palette.mode };
	if (theme.colorSchemes) Object.entries(theme.colorSchemes).forEach(([key, scheme]) => {
		const selector = theme.getColorSchemeSelector(key);
		if (selector.startsWith("@")) baseStyles[selector] = { [`:root:not(:has(.${SELECTOR}))`]: { colorScheme: scheme.palette?.mode } };
		else baseStyles[selector.replace(/\s*&/, "")] = { [`&:not(:has(.${SELECTOR}))`]: { colorScheme: scheme.palette?.mode } };
	});
	return result;
};
var GlobalStyles = globalCss(isDynamicSupport ? ({ theme, enableColorScheme }) => styles(theme, enableColorScheme) : ({ theme }) => staticStyles(theme));
/**
* Kickstart an elegant, consistent, and simple baseline to build upon.
*/
function CssBaseline(inProps) {
	const { children, enableColorScheme = false } = useDefaultProps({
		props: inProps,
		name: "MuiCssBaseline"
	});
	return /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(React$1.Fragment, { children: [
		isDynamicSupport && /*#__PURE__*/ (0, import_jsx_runtime.jsx)(GlobalStyles, { enableColorScheme }),
		!isDynamicSupport && !enableColorScheme && /*#__PURE__*/ (0, import_jsx_runtime.jsx)("span", {
			className: SELECTOR,
			style: { display: "none" }
		}),
		children
	] });
}
// src/main.tsx
var import_client = /* @__PURE__ */ __toESM(require_client(), 1);
var React = await importShared("react");
var { BrowserRouter } = await importShared("react-router-dom");
var { Provider } = await importShared("react-redux");
var theme = createTheme({
	palette: {
		mode: "dark",
		primary: { main: "#4F46E5" },
		secondary: { main: "#06B6D4" },
		background: {
			default: "#0B0F19",
			paper: "#111827"
		},
		text: {
			primary: "#F8FAFC",
			secondary: "#94A3B8"
		}
	},
	typography: { fontFamily: "\"Plus Jakarta Sans\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif" }
});
import_client.createRoot(document.getElementById("root")).render(/* @__PURE__ */ (0, import_jsx_runtime.jsx)(React.StrictMode, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Provider, {
	store,
	children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(ThemeProvider, {
		theme,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(CssBaseline, {}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(BrowserRouter, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(App, {}) })]
	})
}) }));
