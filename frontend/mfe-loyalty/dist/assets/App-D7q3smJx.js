import { r as importShared } from "./_virtual___federation_fn_import-DYD12ulU.js";
import { $ as createStyled, B as useRtl, D as createSvgIcon, F as useTheme$1, G as composeClasses, M as styled$1, O as useDefaultProps, Q as resolveProps, T as useEventCallback_default, b as Paper, ct as capitalize, dt as require_jsx_runtime, et as generateUtilityClasses, f as Box, it as useTheme, j as capitalize_default, k as memoTheme, m as Typography, nt as clsx, ot as createTheme, tt as generateUtilityClass, v as ButtonBase, y as useSlot } from "./loyaltyApi-DQKJ10CA.js";
import { a as ownerDocument_default, i as ownerWindow_default, n as Button, o as debounce_default, r as useEnhancedEffect_default, s as useSlotProps, t as LoyaltyAdminPage } from "./LoyaltyAdminPage-CxlTDdVL.js";
import { t as LoyaltyDashboardPage } from "./LoyaltyDashboardPage-BULN_IBc.js";
// ../node_modules/@mui/system/esm/styled/styled.js
var styled = createStyled();
// ../node_modules/@mui/system/esm/useThemeProps/getThemeProps.js
function getThemeProps(params) {
	const { theme, name, props } = params;
	if (!theme || !theme.components || !theme.components[name] || !theme.components[name].defaultProps) return props;
	return resolveProps(theme.components[name].defaultProps, props);
}
// ../node_modules/@mui/system/esm/useThemeProps/useThemeProps.js
function useThemeProps({ props, name, defaultTheme, themeId }) {
	let theme = useTheme(defaultTheme);
	if (themeId) theme = theme[themeId] || theme;
	return getThemeProps({
		theme,
		name,
		props
	});
}
// ../node_modules/@mui/system/esm/Container/createContainer.js
var import_jsx_runtime = require_jsx_runtime();
var React$4 = await importShared("react");
var defaultTheme = createTheme();
var defaultCreateStyledComponent = styled("div", {
	name: "MuiContainer",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.root,
			styles[`maxWidth${capitalize(String(ownerState.maxWidth))}`],
			ownerState.fixed && styles.fixed,
			ownerState.disableGutters && styles.disableGutters
		];
	}
});
var useThemePropsDefault = (inProps) => useThemeProps({
	props: inProps,
	name: "MuiContainer",
	defaultTheme
});
var useUtilityClasses$3 = (ownerState, componentName) => {
	const getContainerUtilityClass = (slot) => {
		return generateUtilityClass(componentName, slot);
	};
	const { classes, fixed, disableGutters, maxWidth } = ownerState;
	const slots = { root: [
		"root",
		maxWidth && `maxWidth${capitalize(String(maxWidth))}`,
		fixed && "fixed",
		disableGutters && "disableGutters"
	] };
	return composeClasses(slots, getContainerUtilityClass, classes);
};
function createContainer(options = {}) {
	const { createStyledComponent = defaultCreateStyledComponent, useThemeProps = useThemePropsDefault, componentName = "MuiContainer" } = options;
	const ContainerRoot = createStyledComponent(({ theme, ownerState }) => ({
		width: "100%",
		marginLeft: "auto",
		boxSizing: "border-box",
		marginRight: "auto",
		...!ownerState.disableGutters && {
			paddingLeft: theme.spacing(2),
			paddingRight: theme.spacing(2),
			[theme.breakpoints.up("sm")]: {
				paddingLeft: theme.spacing(3),
				paddingRight: theme.spacing(3)
			}
		}
	}), ({ theme, ownerState }) => ownerState.fixed && Object.keys(theme.breakpoints.values).reduce((acc, breakpointValueKey) => {
		const breakpoint = breakpointValueKey;
		const value = theme.breakpoints.values[breakpoint];
		if (value !== 0) acc[theme.breakpoints.up(breakpoint)] = { maxWidth: `${value}${theme.breakpoints.unit}` };
		return acc;
	}, {}), ({ theme, ownerState }) => ({
		...ownerState.maxWidth === "xs" && { [theme.breakpoints.up("xs")]: { maxWidth: Math.max(theme.breakpoints.values.xs, 444) } },
		...ownerState.maxWidth && ownerState.maxWidth !== "xs" && { [theme.breakpoints.up(ownerState.maxWidth)]: { maxWidth: `${theme.breakpoints.values[ownerState.maxWidth]}${theme.breakpoints.unit}` } }
	}));
	return /* @__PURE__ */ React$4.forwardRef(function Container(inProps, ref) {
		const props = useThemeProps(inProps);
		const { className, component = "div", disableGutters = false, fixed = false, maxWidth = "lg", classes: classesProp, ...other } = props;
		const ownerState = {
			...props,
			component,
			disableGutters,
			fixed,
			maxWidth
		};
		const classes = useUtilityClasses$3(ownerState, componentName);
		return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ContainerRoot, {
			as: component,
			ownerState,
			className: clsx(classes.root, className),
			ref,
			...other
		});
	});
}
// ../node_modules/@mui/material/Container/Container.js
var Container = createContainer({
	createStyledComponent: styled$1("div", {
		name: "MuiContainer",
		slot: "Root",
		overridesResolver: (props, styles) => {
			const { ownerState } = props;
			return [
				styles.root,
				styles[`maxWidth${capitalize_default(String(ownerState.maxWidth))}`],
				ownerState.fixed && styles.fixed,
				ownerState.disableGutters && styles.disableGutters
			];
		}
	}),
	useThemeProps: (inProps) => useDefaultProps({
		props: inProps,
		name: "MuiContainer"
	})
});
// ../node_modules/@mui/material/Tab/tabClasses.js
function getTabUtilityClass(slot) {
	return generateUtilityClass("MuiTab", slot);
}
var tabClasses = generateUtilityClasses("MuiTab", [
	"root",
	"labelIcon",
	"textColorInherit",
	"textColorPrimary",
	"textColorSecondary",
	"selected",
	"disabled",
	"fullWidth",
	"wrapped",
	"iconWrapper",
	"icon"
]);
// ../node_modules/@mui/material/Tab/Tab.js
var React$3 = await importShared("react");
var useUtilityClasses$2 = (ownerState) => {
	const { classes, textColor, fullWidth, wrapped, icon, label, selected, disabled } = ownerState;
	const slots = {
		root: [
			"root",
			icon && label && "labelIcon",
			`textColor${capitalize_default(textColor)}`,
			fullWidth && "fullWidth",
			wrapped && "wrapped",
			selected && "selected",
			disabled && "disabled"
		],
		icon: ["iconWrapper", "icon"]
	};
	return composeClasses(slots, getTabUtilityClass, classes);
};
var TabRoot = styled$1(ButtonBase, {
	name: "MuiTab",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.root,
			ownerState.label && ownerState.icon && styles.labelIcon,
			styles[`textColor${capitalize_default(ownerState.textColor)}`],
			ownerState.fullWidth && styles.fullWidth,
			ownerState.wrapped && styles.wrapped,
			{ [`& .${tabClasses.iconWrapper}`]: styles.iconWrapper },
			{ [`& .${tabClasses.icon}`]: styles.icon }
		];
	}
})(memoTheme(({ theme }) => ({
	...theme.typography.button,
	maxWidth: 360,
	minWidth: 90,
	position: "relative",
	minHeight: 48,
	flexShrink: 0,
	padding: "12px 16px",
	overflow: "hidden",
	whiteSpace: "normal",
	textAlign: "center",
	lineHeight: 1.25,
	variants: [
		{
			props: ({ ownerState }) => ownerState.label && (ownerState.iconPosition === "top" || ownerState.iconPosition === "bottom"),
			style: { flexDirection: "column" }
		},
		{
			props: ({ ownerState }) => ownerState.label && ownerState.iconPosition !== "top" && ownerState.iconPosition !== "bottom",
			style: { flexDirection: "row" }
		},
		{
			props: ({ ownerState }) => ownerState.icon && ownerState.label,
			style: {
				minHeight: 72,
				paddingTop: 9,
				paddingBottom: 9
			}
		},
		{
			props: ({ ownerState, iconPosition }) => ownerState.icon && ownerState.label && iconPosition === "top",
			style: { [`& > .${tabClasses.icon}`]: { marginBottom: 6 } }
		},
		{
			props: ({ ownerState, iconPosition }) => ownerState.icon && ownerState.label && iconPosition === "bottom",
			style: { [`& > .${tabClasses.icon}`]: { marginTop: 6 } }
		},
		{
			props: ({ ownerState, iconPosition }) => ownerState.icon && ownerState.label && iconPosition === "start",
			style: { [`& > .${tabClasses.icon}`]: { marginRight: theme.spacing(1) } }
		},
		{
			props: ({ ownerState, iconPosition }) => ownerState.icon && ownerState.label && iconPosition === "end",
			style: { [`& > .${tabClasses.icon}`]: { marginLeft: theme.spacing(1) } }
		},
		{
			props: { textColor: "inherit" },
			style: {
				color: "inherit",
				opacity: .6,
				[`&.${tabClasses.selected}`]: { opacity: 1 },
				[`&.${tabClasses.disabled}`]: { opacity: (theme.vars || theme).palette.action.disabledOpacity }
			}
		},
		{
			props: { textColor: "primary" },
			style: {
				color: (theme.vars || theme).palette.text.secondary,
				[`&.${tabClasses.selected}`]: { color: (theme.vars || theme).palette.primary.main },
				[`&.${tabClasses.disabled}`]: { color: (theme.vars || theme).palette.text.disabled }
			}
		},
		{
			props: { textColor: "secondary" },
			style: {
				color: (theme.vars || theme).palette.text.secondary,
				[`&.${tabClasses.selected}`]: { color: (theme.vars || theme).palette.secondary.main },
				[`&.${tabClasses.disabled}`]: { color: (theme.vars || theme).palette.text.disabled }
			}
		},
		{
			props: ({ ownerState }) => ownerState.fullWidth,
			style: {
				flexShrink: 1,
				flexGrow: 1,
				flexBasis: 0,
				maxWidth: "none"
			}
		},
		{
			props: ({ ownerState }) => ownerState.wrapped,
			style: { fontSize: theme.typography.pxToRem(12) }
		}
	]
})));
var Tab = /*#__PURE__*/ React$3.forwardRef(function Tab(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTab"
	});
	const { className, disabled = false, disableFocusRipple = false, fullWidth, icon: iconProp, iconPosition = "top", indicator, label, onChange, onClick, onFocus, selected, selectionFollowsFocus, textColor = "inherit", value, wrapped = false, ...other } = props;
	const ownerState = {
		...props,
		disabled,
		disableFocusRipple,
		selected,
		icon: !!iconProp,
		iconPosition,
		label: !!label,
		fullWidth,
		textColor,
		wrapped
	};
	const classes = useUtilityClasses$2(ownerState);
	const icon = iconProp && label && /*#__PURE__*/ React$3.isValidElement(iconProp) ? /*#__PURE__*/ React$3.cloneElement(iconProp, { className: clsx(classes.icon, iconProp.props.className) }) : iconProp;
	const handleClick = (event) => {
		if (!selected && onChange) onChange(event, value);
		if (onClick) onClick(event);
	};
	const handleFocus = (event) => {
		if (selectionFollowsFocus && !selected && onChange) onChange(event, value);
		if (onFocus) onFocus(event);
	};
	return /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(TabRoot, {
		focusRipple: !disableFocusRipple,
		className: clsx(classes.root, className),
		ref,
		role: "tab",
		"aria-selected": selected,
		disabled,
		onClick: handleClick,
		onFocus: handleFocus,
		ownerState,
		tabIndex: selected ? 0 : -1,
		...other,
		children: [iconPosition === "top" || iconPosition === "start" ? /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(React$3.Fragment, { children: [icon, label] }) : /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(React$3.Fragment, { children: [label, icon] }), indicator]
	});
});
// ../node_modules/@mui/material/internal/svg-icons/KeyboardArrowLeft.js
/**
* @ignore - internal component.
*/
await importShared("react");
var KeyboardArrowLeft_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M15.41 16.09l-4.58-4.59 4.58-4.59L14 5.5l-6 6 6 6z" }), "KeyboardArrowLeft");
// ../node_modules/@mui/material/internal/svg-icons/KeyboardArrowRight.js
/**
* @ignore - internal component.
*/
await importShared("react");
var KeyboardArrowRight_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M8.59 16.34l4.58-4.59-4.58-4.59L10 5.75l6 6-6 6z" }), "KeyboardArrowRight");
// ../node_modules/@mui/material/internal/animate.js
function easeInOutSin(time) {
	return (1 + Math.sin(Math.PI * time - Math.PI / 2)) / 2;
}
function animate(property, element, to, options = {}, cb = () => {}) {
	const { ease = easeInOutSin, duration = 300 } = options;
	let start = null;
	const from = element[property];
	let cancelled = false;
	const cancel = () => {
		cancelled = true;
	};
	const step = (timestamp) => {
		if (cancelled) {
			cb(/* @__PURE__ */ new Error("Animation cancelled"));
			return;
		}
		if (start === null) start = timestamp;
		const time = Math.min(1, (timestamp - start) / duration);
		element[property] = ease(time) * (to - from) + from;
		if (time >= 1) {
			requestAnimationFrame(() => {
				cb(null);
			});
			return;
		}
		requestAnimationFrame(step);
	};
	if (from === to) {
		cb(/* @__PURE__ */ new Error("Element already at target position"));
		return cancel;
	}
	requestAnimationFrame(step);
	return cancel;
}
// ../node_modules/@mui/material/Tabs/ScrollbarSize.js
var React$2 = await importShared("react");
var styles = {
	width: 99,
	height: 99,
	position: "absolute",
	top: -9999,
	overflow: "scroll"
};
/**
* @ignore - internal component.
* The component originates from https://github.com/STORIS/react-scrollbar-size.
* It has been moved into the core in order to minimize the bundle size.
*/
function ScrollbarSize(props) {
	const { onChange, ...other } = props;
	const scrollbarHeight = React$2.useRef();
	const nodeRef = React$2.useRef(null);
	const setMeasurements = () => {
		scrollbarHeight.current = nodeRef.current.offsetHeight - nodeRef.current.clientHeight;
	};
	useEnhancedEffect_default(() => {
		const handleResize = debounce_default(() => {
			const prevHeight = scrollbarHeight.current;
			setMeasurements();
			if (prevHeight !== scrollbarHeight.current) onChange(scrollbarHeight.current);
		});
		const containerWindow = ownerWindow_default(nodeRef.current);
		containerWindow.addEventListener("resize", handleResize);
		return () => {
			handleResize.clear();
			containerWindow.removeEventListener("resize", handleResize);
		};
	}, [onChange]);
	React$2.useEffect(() => {
		setMeasurements();
		onChange(scrollbarHeight.current);
	}, [onChange]);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)("div", {
		style: styles,
		...other,
		ref: nodeRef
	});
}
// ../node_modules/@mui/material/TabScrollButton/tabScrollButtonClasses.js
function getTabScrollButtonUtilityClass(slot) {
	return generateUtilityClass("MuiTabScrollButton", slot);
}
var tabScrollButtonClasses = generateUtilityClasses("MuiTabScrollButton", [
	"root",
	"vertical",
	"horizontal",
	"disabled"
]);
// ../node_modules/@mui/material/TabScrollButton/TabScrollButton.js
var React$1 = await importShared("react");
var useUtilityClasses$1 = (ownerState) => {
	const { classes, orientation, disabled } = ownerState;
	return composeClasses({ root: [
		"root",
		orientation,
		disabled && "disabled"
	] }, getTabScrollButtonUtilityClass, classes);
};
var TabScrollButtonRoot = styled$1(ButtonBase, {
	name: "MuiTabScrollButton",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [styles.root, ownerState.orientation && styles[ownerState.orientation]];
	}
})({
	width: 40,
	flexShrink: 0,
	opacity: .8,
	[`&.${tabScrollButtonClasses.disabled}`]: { opacity: 0 },
	variants: [{
		props: { orientation: "vertical" },
		style: {
			width: "100%",
			height: 40,
			"& svg": { transform: "var(--TabScrollButton-svgRotate)" }
		}
	}]
});
var TabScrollButton = /*#__PURE__*/ React$1.forwardRef(function TabScrollButton(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTabScrollButton"
	});
	const { className, slots = {}, slotProps = {}, direction, orientation, disabled, ...other } = props;
	const isRtl = useRtl();
	const ownerState = {
		isRtl,
		...props
	};
	const classes = useUtilityClasses$1(ownerState);
	const StartButtonIcon = slots.StartScrollButtonIcon ?? KeyboardArrowLeft_default;
	const EndButtonIcon = slots.EndScrollButtonIcon ?? KeyboardArrowRight_default;
	const startButtonIconProps = useSlotProps({
		elementType: StartButtonIcon,
		externalSlotProps: slotProps.startScrollButtonIcon,
		additionalProps: { fontSize: "small" },
		ownerState
	});
	const endButtonIconProps = useSlotProps({
		elementType: EndButtonIcon,
		externalSlotProps: slotProps.endScrollButtonIcon,
		additionalProps: { fontSize: "small" },
		ownerState
	});
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TabScrollButtonRoot, {
		component: "div",
		className: clsx(classes.root, className),
		ref,
		role: null,
		ownerState,
		tabIndex: null,
		...other,
		style: {
			...other.style,
			...orientation === "vertical" && { "--TabScrollButton-svgRotate": `rotate(${isRtl ? -90 : 90}deg)` }
		},
		children: direction === "left" ? /*#__PURE__*/ (0, import_jsx_runtime.jsx)(StartButtonIcon, { ...startButtonIconProps }) : /*#__PURE__*/ (0, import_jsx_runtime.jsx)(EndButtonIcon, { ...endButtonIconProps })
	});
});
// ../node_modules/@mui/material/Tabs/tabsClasses.js
function getTabsUtilityClass(slot) {
	return generateUtilityClass("MuiTabs", slot);
}
var tabsClasses = generateUtilityClasses("MuiTabs", [
	"root",
	"vertical",
	"list",
	"flexContainer",
	"flexContainerVertical",
	"centered",
	"scroller",
	"fixed",
	"scrollableX",
	"scrollableY",
	"hideScrollbar",
	"scrollButtons",
	"scrollButtonsHideMobile",
	"indicator"
]);
// ../node_modules/@mui/material/Tabs/Tabs.js
var React = await importShared("react");
var nextItem = (list, item) => {
	if (list === item) return list.firstChild;
	if (item && item.nextElementSibling) return item.nextElementSibling;
	return list.firstChild;
};
var previousItem = (list, item) => {
	if (list === item) return list.lastChild;
	if (item && item.previousElementSibling) return item.previousElementSibling;
	return list.lastChild;
};
var moveFocus = (list, currentFocus, traversalFunction) => {
	let wrappedOnce = false;
	let nextFocus = traversalFunction(list, currentFocus);
	while (nextFocus) {
		if (nextFocus === list.firstChild) {
			if (wrappedOnce) return;
			wrappedOnce = true;
		}
		const nextFocusDisabled = nextFocus.disabled || nextFocus.getAttribute("aria-disabled") === "true";
		if (!nextFocus.hasAttribute("tabindex") || nextFocusDisabled) nextFocus = traversalFunction(list, nextFocus);
		else {
			nextFocus.focus();
			return;
		}
	}
};
var useUtilityClasses = (ownerState) => {
	const { vertical, fixed, hideScrollbar, scrollableX, scrollableY, centered, scrollButtonsHideMobile, classes } = ownerState;
	return composeClasses({
		root: ["root", vertical && "vertical"],
		scroller: [
			"scroller",
			fixed && "fixed",
			hideScrollbar && "hideScrollbar",
			scrollableX && "scrollableX",
			scrollableY && "scrollableY"
		],
		list: [
			"list",
			"flexContainer",
			vertical && "flexContainerVertical",
			vertical && "vertical",
			centered && "centered"
		],
		indicator: ["indicator"],
		scrollButtons: ["scrollButtons", scrollButtonsHideMobile && "scrollButtonsHideMobile"],
		scrollableX: [scrollableX && "scrollableX"],
		hideScrollbar: [hideScrollbar && "hideScrollbar"]
	}, getTabsUtilityClass, classes);
};
var TabsRoot = styled$1("div", {
	name: "MuiTabs",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			{ [`& .${tabsClasses.scrollButtons}`]: styles.scrollButtons },
			{ [`& .${tabsClasses.scrollButtons}`]: ownerState.scrollButtonsHideMobile && styles.scrollButtonsHideMobile },
			styles.root,
			ownerState.vertical && styles.vertical
		];
	}
})(memoTheme(({ theme }) => ({
	overflow: "hidden",
	minHeight: 48,
	WebkitOverflowScrolling: "touch",
	display: "flex",
	variants: [{
		props: ({ ownerState }) => ownerState.vertical,
		style: { flexDirection: "column" }
	}, {
		props: ({ ownerState }) => ownerState.scrollButtonsHideMobile,
		style: { [`& .${tabsClasses.scrollButtons}`]: { [theme.breakpoints.down("sm")]: { display: "none" } } }
	}]
})));
var TabsScroller = styled$1("div", {
	name: "MuiTabs",
	slot: "Scroller",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.scroller,
			ownerState.fixed && styles.fixed,
			ownerState.hideScrollbar && styles.hideScrollbar,
			ownerState.scrollableX && styles.scrollableX,
			ownerState.scrollableY && styles.scrollableY
		];
	}
})({
	position: "relative",
	display: "inline-block",
	flex: "1 1 auto",
	whiteSpace: "nowrap",
	variants: [
		{
			props: ({ ownerState }) => ownerState.fixed,
			style: {
				overflowX: "hidden",
				width: "100%"
			}
		},
		{
			props: ({ ownerState }) => ownerState.hideScrollbar,
			style: {
				scrollbarWidth: "none",
				"&::-webkit-scrollbar": { display: "none" }
			}
		},
		{
			props: ({ ownerState }) => ownerState.scrollableX,
			style: {
				overflowX: "auto",
				overflowY: "hidden"
			}
		},
		{
			props: ({ ownerState }) => ownerState.scrollableY,
			style: {
				overflowY: "auto",
				overflowX: "hidden"
			}
		}
	]
});
var List = styled$1("div", {
	name: "MuiTabs",
	slot: "List",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.list,
			styles.flexContainer,
			ownerState.vertical && styles.flexContainerVertical,
			ownerState.centered && styles.centered
		];
	}
})({
	display: "flex",
	variants: [{
		props: ({ ownerState }) => ownerState.vertical,
		style: { flexDirection: "column" }
	}, {
		props: ({ ownerState }) => ownerState.centered,
		style: { justifyContent: "center" }
	}]
});
var TabsIndicator = styled$1("span", {
	name: "MuiTabs",
	slot: "Indicator",
	overridesResolver: (props, styles) => styles.indicator
})(memoTheme(({ theme }) => ({
	position: "absolute",
	height: 2,
	bottom: 0,
	width: "100%",
	transition: theme.transitions.create(),
	variants: [
		{
			props: { indicatorColor: "primary" },
			style: { backgroundColor: (theme.vars || theme).palette.primary.main }
		},
		{
			props: { indicatorColor: "secondary" },
			style: { backgroundColor: (theme.vars || theme).palette.secondary.main }
		},
		{
			props: ({ ownerState }) => ownerState.vertical,
			style: {
				height: "100%",
				width: 2,
				right: 0
			}
		}
	]
})));
var TabsScrollbarSize = styled$1(ScrollbarSize)({
	overflowX: "auto",
	overflowY: "hidden",
	scrollbarWidth: "none",
	"&::-webkit-scrollbar": { display: "none" }
});
var defaultIndicatorStyle = {};
var Tabs = /*#__PURE__*/ React.forwardRef(function Tabs(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTabs"
	});
	const theme = useTheme$1();
	const isRtl = useRtl();
	const { "aria-label": ariaLabel, "aria-labelledby": ariaLabelledBy, action, centered = false, children: childrenProp, className, component = "div", allowScrollButtonsMobile = false, indicatorColor = "primary", onChange, orientation = "horizontal", ScrollButtonComponent, scrollButtons = "auto", selectionFollowsFocus, slots = {}, slotProps = {}, TabIndicatorProps = {}, TabScrollButtonProps = {}, textColor = "primary", value, variant = "standard", visibleScrollbar = false, ...other } = props;
	const scrollable = variant === "scrollable";
	const vertical = orientation === "vertical";
	const scrollStart = vertical ? "scrollTop" : "scrollLeft";
	const start = vertical ? "top" : "left";
	const end = vertical ? "bottom" : "right";
	const clientSize = vertical ? "clientHeight" : "clientWidth";
	const size = vertical ? "height" : "width";
	const ownerState = {
		...props,
		component,
		allowScrollButtonsMobile,
		indicatorColor,
		orientation,
		vertical,
		scrollButtons,
		textColor,
		variant,
		visibleScrollbar,
		fixed: !scrollable,
		hideScrollbar: scrollable && !visibleScrollbar,
		scrollableX: scrollable && !vertical,
		scrollableY: scrollable && vertical,
		centered: centered && !scrollable,
		scrollButtonsHideMobile: !allowScrollButtonsMobile
	};
	const classes = useUtilityClasses(ownerState);
	const startScrollButtonIconProps = useSlotProps({
		elementType: slots.StartScrollButtonIcon,
		externalSlotProps: slotProps.startScrollButtonIcon,
		ownerState
	});
	const endScrollButtonIconProps = useSlotProps({
		elementType: slots.EndScrollButtonIcon,
		externalSlotProps: slotProps.endScrollButtonIcon,
		ownerState
	});
	const [mounted, setMounted] = React.useState(false);
	const [indicatorStyle, setIndicatorStyle] = React.useState(defaultIndicatorStyle);
	const [displayStartScroll, setDisplayStartScroll] = React.useState(false);
	const [displayEndScroll, setDisplayEndScroll] = React.useState(false);
	const [updateScrollObserver, setUpdateScrollObserver] = React.useState(false);
	const [scrollerStyle, setScrollerStyle] = React.useState({
		overflow: "hidden",
		scrollbarWidth: 0
	});
	const valueToIndex = /* @__PURE__ */ new Map();
	const tabsRef = React.useRef(null);
	const tabListRef = React.useRef(null);
	const externalForwardedProps = {
		slots,
		slotProps: {
			indicator: TabIndicatorProps,
			scrollButton: TabScrollButtonProps,
			...slotProps
		}
	};
	const getTabsMeta = () => {
		const tabsNode = tabsRef.current;
		let tabsMeta;
		if (tabsNode) {
			const rect = tabsNode.getBoundingClientRect();
			tabsMeta = {
				clientWidth: tabsNode.clientWidth,
				scrollLeft: tabsNode.scrollLeft,
				scrollTop: tabsNode.scrollTop,
				scrollWidth: tabsNode.scrollWidth,
				top: rect.top,
				bottom: rect.bottom,
				left: rect.left,
				right: rect.right
			};
		}
		let tabMeta;
		if (tabsNode && value !== false) {
			const children = tabListRef.current.children;
			if (children.length > 0) {
				const tab = children[valueToIndex.get(value)];
				tabMeta = tab ? tab.getBoundingClientRect() : null;
			}
		}
		return {
			tabsMeta,
			tabMeta
		};
	};
	const updateIndicatorState = useEventCallback_default(() => {
		const { tabsMeta, tabMeta } = getTabsMeta();
		let startValue = 0;
		let startIndicator;
		if (vertical) {
			startIndicator = "top";
			if (tabMeta && tabsMeta) startValue = tabMeta.top - tabsMeta.top + tabsMeta.scrollTop;
		} else {
			startIndicator = isRtl ? "right" : "left";
			if (tabMeta && tabsMeta) startValue = (isRtl ? -1 : 1) * (tabMeta[startIndicator] - tabsMeta[startIndicator] + tabsMeta.scrollLeft);
		}
		const newIndicatorStyle = {
			[startIndicator]: startValue,
			[size]: tabMeta ? tabMeta[size] : 0
		};
		if (typeof indicatorStyle[startIndicator] !== "number" || typeof indicatorStyle[size] !== "number") setIndicatorStyle(newIndicatorStyle);
		else {
			const dStart = Math.abs(indicatorStyle[startIndicator] - newIndicatorStyle[startIndicator]);
			const dSize = Math.abs(indicatorStyle[size] - newIndicatorStyle[size]);
			if (dStart >= 1 || dSize >= 1) setIndicatorStyle(newIndicatorStyle);
		}
	});
	const scroll = (scrollValue, { animation = true } = {}) => {
		if (animation) animate(scrollStart, tabsRef.current, scrollValue, { duration: theme.transitions.duration.standard });
		else tabsRef.current[scrollStart] = scrollValue;
	};
	const moveTabsScroll = (delta) => {
		let scrollValue = tabsRef.current[scrollStart];
		if (vertical) scrollValue += delta;
		else scrollValue += delta * (isRtl ? -1 : 1);
		scroll(scrollValue);
	};
	const getScrollSize = () => {
		const containerSize = tabsRef.current[clientSize];
		let totalSize = 0;
		const children = Array.from(tabListRef.current.children);
		for (let i = 0; i < children.length; i += 1) {
			const tab = children[i];
			if (totalSize + tab[clientSize] > containerSize) {
				if (i === 0) totalSize = containerSize;
				break;
			}
			totalSize += tab[clientSize];
		}
		return totalSize;
	};
	const handleStartScrollClick = () => {
		moveTabsScroll(-1 * getScrollSize());
	};
	const handleEndScrollClick = () => {
		moveTabsScroll(getScrollSize());
	};
	const [ScrollbarSlot, { onChange: scrollbarOnChange, ...scrollbarSlotProps }] = useSlot("scrollbar", {
		className: clsx(classes.scrollableX, classes.hideScrollbar),
		elementType: TabsScrollbarSize,
		shouldForwardComponentProp: true,
		externalForwardedProps,
		ownerState
	});
	const handleScrollbarSizeChange = React.useCallback((scrollbarWidth) => {
		scrollbarOnChange?.(scrollbarWidth);
		setScrollerStyle({
			overflow: null,
			scrollbarWidth
		});
	}, [scrollbarOnChange]);
	const [ScrollButtonsSlot, scrollButtonSlotProps] = useSlot("scrollButtons", {
		className: clsx(classes.scrollButtons, TabScrollButtonProps.className),
		elementType: TabScrollButton,
		externalForwardedProps,
		ownerState,
		additionalProps: {
			orientation,
			slots: {
				StartScrollButtonIcon: slots.startScrollButtonIcon || slots.StartScrollButtonIcon,
				EndScrollButtonIcon: slots.endScrollButtonIcon || slots.EndScrollButtonIcon
			},
			slotProps: {
				startScrollButtonIcon: startScrollButtonIconProps,
				endScrollButtonIcon: endScrollButtonIconProps
			}
		}
	});
	const getConditionalElements = () => {
		const conditionalElements = {};
		conditionalElements.scrollbarSizeListener = scrollable ? /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ScrollbarSlot, {
			...scrollbarSlotProps,
			onChange: handleScrollbarSizeChange
		}) : null;
		const showScrollButtons = scrollable && (scrollButtons === "auto" && (displayStartScroll || displayEndScroll) || scrollButtons === true);
		conditionalElements.scrollButtonStart = showScrollButtons ? /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ScrollButtonsSlot, {
			direction: isRtl ? "right" : "left",
			onClick: handleStartScrollClick,
			disabled: !displayStartScroll,
			...scrollButtonSlotProps
		}) : null;
		conditionalElements.scrollButtonEnd = showScrollButtons ? /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ScrollButtonsSlot, {
			direction: isRtl ? "left" : "right",
			onClick: handleEndScrollClick,
			disabled: !displayEndScroll,
			...scrollButtonSlotProps
		}) : null;
		return conditionalElements;
	};
	const scrollSelectedIntoView = useEventCallback_default((animation) => {
		const { tabsMeta, tabMeta } = getTabsMeta();
		if (!tabMeta || !tabsMeta) return;
		if (tabMeta[start] < tabsMeta[start]) {
			const nextScrollStart = tabsMeta[scrollStart] + (tabMeta[start] - tabsMeta[start]);
			scroll(nextScrollStart, { animation });
		} else if (tabMeta[end] > tabsMeta[end]) {
			const nextScrollStart = tabsMeta[scrollStart] + (tabMeta[end] - tabsMeta[end]);
			scroll(nextScrollStart, { animation });
		}
	});
	const updateScrollButtonState = useEventCallback_default(() => {
		if (scrollable && scrollButtons !== false) setUpdateScrollObserver(!updateScrollObserver);
	});
	React.useEffect(() => {
		const handleResize = debounce_default(() => {
			if (tabsRef.current) updateIndicatorState();
		});
		let resizeObserver;
		/**
		* @type {MutationCallback}
		*/
		const handleMutation = (records) => {
			records.forEach((record) => {
				record.removedNodes.forEach((item) => {
					resizeObserver?.unobserve(item);
				});
				record.addedNodes.forEach((item) => {
					resizeObserver?.observe(item);
				});
			});
			handleResize();
			updateScrollButtonState();
		};
		const win = ownerWindow_default(tabsRef.current);
		win.addEventListener("resize", handleResize);
		let mutationObserver;
		if (typeof ResizeObserver !== "undefined") {
			resizeObserver = new ResizeObserver(handleResize);
			Array.from(tabListRef.current.children).forEach((child) => {
				resizeObserver.observe(child);
			});
		}
		if (typeof MutationObserver !== "undefined") {
			mutationObserver = new MutationObserver(handleMutation);
			mutationObserver.observe(tabListRef.current, { childList: true });
		}
		return () => {
			handleResize.clear();
			win.removeEventListener("resize", handleResize);
			mutationObserver?.disconnect();
			resizeObserver?.disconnect();
		};
	}, [updateIndicatorState, updateScrollButtonState]);
	/**
	* Toggle visibility of start and end scroll buttons
	* Using IntersectionObserver on first and last Tabs.
	*/
	React.useEffect(() => {
		const tabListChildren = Array.from(tabListRef.current.children);
		const length = tabListChildren.length;
		if (typeof IntersectionObserver !== "undefined" && length > 0 && scrollable && scrollButtons !== false) {
			const firstTab = tabListChildren[0];
			const lastTab = tabListChildren[length - 1];
			const observerOptions = {
				root: tabsRef.current,
				threshold: .99
			};
			const handleScrollButtonStart = (entries) => {
				setDisplayStartScroll(!entries[0].isIntersecting);
			};
			const firstObserver = new IntersectionObserver(handleScrollButtonStart, observerOptions);
			firstObserver.observe(firstTab);
			const handleScrollButtonEnd = (entries) => {
				setDisplayEndScroll(!entries[0].isIntersecting);
			};
			const lastObserver = new IntersectionObserver(handleScrollButtonEnd, observerOptions);
			lastObserver.observe(lastTab);
			return () => {
				firstObserver.disconnect();
				lastObserver.disconnect();
			};
		}
	}, [
		scrollable,
		scrollButtons,
		updateScrollObserver,
		childrenProp?.length
	]);
	React.useEffect(() => {
		setMounted(true);
	}, []);
	React.useEffect(() => {
		updateIndicatorState();
	});
	React.useEffect(() => {
		scrollSelectedIntoView(defaultIndicatorStyle !== indicatorStyle);
	}, [scrollSelectedIntoView, indicatorStyle]);
	React.useImperativeHandle(action, () => ({
		updateIndicator: updateIndicatorState,
		updateScrollButtons: updateScrollButtonState
	}), [updateIndicatorState, updateScrollButtonState]);
	const [IndicatorSlot, indicatorSlotProps] = useSlot("indicator", {
		className: clsx(classes.indicator, TabIndicatorProps.className),
		elementType: TabsIndicator,
		externalForwardedProps,
		ownerState,
		additionalProps: { style: indicatorStyle }
	});
	const indicator = /*#__PURE__*/ (0, import_jsx_runtime.jsx)(IndicatorSlot, { ...indicatorSlotProps });
	let childIndex = 0;
	const children = React.Children.map(childrenProp, (child) => {
		if (!/*#__PURE__*/ React.isValidElement(child)) return null;
		const childValue = child.props.value === void 0 ? childIndex : child.props.value;
		valueToIndex.set(childValue, childIndex);
		const selected = childValue === value;
		childIndex += 1;
		return /*#__PURE__*/ React.cloneElement(child, {
			fullWidth: variant === "fullWidth",
			indicator: selected && !mounted && indicator,
			selected,
			selectionFollowsFocus,
			onChange,
			textColor,
			value: childValue,
			...childIndex === 1 && value === false && !child.props.tabIndex ? { tabIndex: 0 } : {}
		});
	});
	const handleKeyDown = (event) => {
		if (event.altKey || event.shiftKey || event.ctrlKey || event.metaKey) return;
		const list = tabListRef.current;
		const currentFocus = ownerDocument_default(list).activeElement;
		if (currentFocus.getAttribute("role") !== "tab") return;
		let previousItemKey = orientation === "horizontal" ? "ArrowLeft" : "ArrowUp";
		let nextItemKey = orientation === "horizontal" ? "ArrowRight" : "ArrowDown";
		if (orientation === "horizontal" && isRtl) {
			previousItemKey = "ArrowRight";
			nextItemKey = "ArrowLeft";
		}
		switch (event.key) {
			case previousItemKey:
				event.preventDefault();
				moveFocus(list, currentFocus, previousItem);
				break;
			case nextItemKey:
				event.preventDefault();
				moveFocus(list, currentFocus, nextItem);
				break;
			case "Home":
				event.preventDefault();
				moveFocus(list, null, nextItem);
				break;
			case "End":
				event.preventDefault();
				moveFocus(list, null, previousItem);
		}
	};
	const conditionalElements = getConditionalElements();
	const [RootSlot, rootSlotProps] = useSlot("root", {
		ref,
		className: clsx(classes.root, className),
		elementType: TabsRoot,
		externalForwardedProps: {
			...externalForwardedProps,
			...other,
			component
		},
		ownerState
	});
	const [ScrollerSlot, scrollerSlotProps] = useSlot("scroller", {
		ref: tabsRef,
		className: classes.scroller,
		elementType: TabsScroller,
		externalForwardedProps,
		ownerState,
		additionalProps: { style: {
			overflow: scrollerStyle.overflow,
			[vertical ? `margin${isRtl ? "Left" : "Right"}` : "marginBottom"]: visibleScrollbar ? void 0 : -scrollerStyle.scrollbarWidth
		} }
	});
	const [ListSlot, listSlotProps] = useSlot("list", {
		ref: tabListRef,
		className: clsx(classes.list, classes.flexContainer),
		elementType: List,
		externalForwardedProps,
		ownerState,
		getSlotProps: (handlers) => ({
			...handlers,
			onKeyDown: (event) => {
				handleKeyDown(event);
				handlers.onKeyDown?.(event);
			}
		})
	});
	return /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(RootSlot, {
		...rootSlotProps,
		children: [
			conditionalElements.scrollButtonStart,
			conditionalElements.scrollbarSizeListener,
			/*#__PURE__*/ (0, import_jsx_runtime.jsxs)(ScrollerSlot, {
				...scrollerSlotProps,
				children: [/*#__PURE__*/ (0, import_jsx_runtime.jsx)(ListSlot, {
					"aria-label": ariaLabel,
					"aria-labelledby": ariaLabelledBy,
					"aria-orientation": orientation === "vertical" ? "vertical" : null,
					role: "tablist",
					...listSlotProps,
					children
				}), mounted && indicator]
			}),
			conditionalElements.scrollButtonEnd
		]
	});
});
// src/App.tsx
await importShared("react");
var { Route, Routes, useNavigate } = await importShared("react-router-dom");
function App() {
	const navigate = useNavigate();
	const admin = window.location.pathname.includes("/admin");
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Container, {
		maxWidth: "lg",
		sx: { py: 3 },
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
			sx: {
				p: 2,
				mb: 3,
				borderRadius: 3
			},
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
				display: "flex",
				alignItems: "center",
				justifyContent: "space-between",
				flexWrap: "wrap",
				gap: 2,
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					variant: "h4",
					fontWeight: 800,
					children: "Rewards & Loyalty"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					color: "text.secondary",
					children: "Track points, membership tier, and rewards."
				})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
					variant: "outlined",
					onClick: () => navigate(admin ? "/loyalty" : "/loyalty/admin"),
					children: admin ? "My rewards" : "Loyalty administration"
				})]
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Tabs, {
				value: admin ? 1 : 0,
				onChange: (_, value) => navigate(value ? "/loyalty/admin" : "/loyalty"),
				sx: { mt: 2 },
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tab, { label: "My rewards" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tab, { label: "Administration" })]
			})]
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Routes, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Route, {
			path: "/admin",
			element: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(LoyaltyAdminPage, {})
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Route, {
			path: "*",
			element: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(LoyaltyDashboardPage, {})
		})] })]
	});
}
export { App as t };
