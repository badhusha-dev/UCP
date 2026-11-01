import { r as importShared } from "./_virtual___federation_fn_import-DYD12ulU.js";
import { $ as generateUtilityClasses, A as styled, B as useSlotProps, D as memoTheme, E as useDefaultProps, H as composeClasses, J as alpha, N as useTheme, Q as resolveProps, R as useRtl, S as useEventCallback_default, T as createSvgIcon, _ as Paper, c as Chip, d as Alert, et as generateUtilityClass, f as IconButton, g as useSlot, h as ButtonBase, i as LinearProgress, j as rootShouldForwardProp, k as capitalize_default, m as createSimplePaletteValueFilter, n as Stack, o as Grid, p as CircularProgress, r as Tooltip, s as Box, st as require_jsx_runtime, t as axios, tt as clsx, u as Typography, w as useId_default } from "./axios-2qcjEZNH.js";
import { a as InputLabel, c as ownerWindow_default, l as ownerDocument_default, n as Select, o as FormControl, r as MenuItem, s as useEnhancedEffect_default, t as TextField, u as debounce_default } from "./TextField-CFS9Y6-t.js";
import { t as BugReportRounded_default } from "./BugReportRounded-7-8H7FS2.js";
// ../node_modules/@mui/material/Button/buttonClasses.js
function getButtonUtilityClass(slot) {
	return generateUtilityClass("MuiButton", slot);
}
var buttonClasses = generateUtilityClasses("MuiButton", [
	"root",
	"text",
	"textInherit",
	"textPrimary",
	"textSecondary",
	"textSuccess",
	"textError",
	"textInfo",
	"textWarning",
	"outlined",
	"outlinedInherit",
	"outlinedPrimary",
	"outlinedSecondary",
	"outlinedSuccess",
	"outlinedError",
	"outlinedInfo",
	"outlinedWarning",
	"contained",
	"containedInherit",
	"containedPrimary",
	"containedSecondary",
	"containedSuccess",
	"containedError",
	"containedInfo",
	"containedWarning",
	"disableElevation",
	"focusVisible",
	"disabled",
	"colorInherit",
	"colorPrimary",
	"colorSecondary",
	"colorSuccess",
	"colorError",
	"colorInfo",
	"colorWarning",
	"textSizeSmall",
	"textSizeMedium",
	"textSizeLarge",
	"outlinedSizeSmall",
	"outlinedSizeMedium",
	"outlinedSizeLarge",
	"containedSizeSmall",
	"containedSizeMedium",
	"containedSizeLarge",
	"sizeMedium",
	"sizeSmall",
	"sizeLarge",
	"fullWidth",
	"startIcon",
	"endIcon",
	"icon",
	"iconSizeSmall",
	"iconSizeMedium",
	"iconSizeLarge",
	"loading",
	"loadingWrapper",
	"loadingIconPlaceholder",
	"loadingIndicator",
	"loadingPositionCenter",
	"loadingPositionStart",
	"loadingPositionEnd"
]);
// ../node_modules/@mui/material/ButtonGroup/ButtonGroupContext.js
/**
* @ignore - internal component.
*/
var ButtonGroupContext = /*#__PURE__*/ (await importShared("react")).createContext({});
// ../node_modules/@mui/material/ButtonGroup/ButtonGroupButtonContext.js
/**
* @ignore - internal component.
*/
var ButtonGroupButtonContext = /*#__PURE__*/ (await importShared("react")).createContext(void 0);
// ../node_modules/@mui/material/Button/Button.js
var import_jsx_runtime = require_jsx_runtime();
var React$4 = await importShared("react");
var useUtilityClasses$3 = (ownerState) => {
	const { color, disableElevation, fullWidth, size, variant, loading, loadingPosition, classes } = ownerState;
	const slots = {
		root: [
			"root",
			loading && "loading",
			variant,
			`${variant}${capitalize_default(color)}`,
			`size${capitalize_default(size)}`,
			`${variant}Size${capitalize_default(size)}`,
			`color${capitalize_default(color)}`,
			disableElevation && "disableElevation",
			fullWidth && "fullWidth",
			loading && `loadingPosition${capitalize_default(loadingPosition)}`
		],
		startIcon: [
			"icon",
			"startIcon",
			`iconSize${capitalize_default(size)}`
		],
		endIcon: [
			"icon",
			"endIcon",
			`iconSize${capitalize_default(size)}`
		],
		loadingIndicator: ["loadingIndicator"],
		loadingWrapper: ["loadingWrapper"]
	};
	const composedClasses = composeClasses(slots, getButtonUtilityClass, classes);
	return {
		...classes,
		...composedClasses
	};
};
var commonIconStyles = [
	{
		props: { size: "small" },
		style: { "& > *:nth-of-type(1)": { fontSize: 18 } }
	},
	{
		props: { size: "medium" },
		style: { "& > *:nth-of-type(1)": { fontSize: 20 } }
	},
	{
		props: { size: "large" },
		style: { "& > *:nth-of-type(1)": { fontSize: 22 } }
	}
];
var ButtonRoot = styled(ButtonBase, {
	shouldForwardProp: (prop) => rootShouldForwardProp(prop) || prop === "classes",
	name: "MuiButton",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.root,
			styles[ownerState.variant],
			styles[`${ownerState.variant}${capitalize_default(ownerState.color)}`],
			styles[`size${capitalize_default(ownerState.size)}`],
			styles[`${ownerState.variant}Size${capitalize_default(ownerState.size)}`],
			ownerState.color === "inherit" && styles.colorInherit,
			ownerState.disableElevation && styles.disableElevation,
			ownerState.fullWidth && styles.fullWidth,
			ownerState.loading && styles.loading
		];
	}
})(memoTheme(({ theme }) => {
	const inheritContainedBackgroundColor = theme.palette.mode === "light" ? theme.palette.grey[300] : theme.palette.grey[800];
	const inheritContainedHoverBackgroundColor = theme.palette.mode === "light" ? theme.palette.grey.A100 : theme.palette.grey[700];
	return {
		...theme.typography.button,
		minWidth: 64,
		padding: "6px 16px",
		border: 0,
		borderRadius: (theme.vars || theme).shape.borderRadius,
		transition: theme.transitions.create([
			"background-color",
			"box-shadow",
			"border-color",
			"color"
		], { duration: theme.transitions.duration.short }),
		"&:hover": { textDecoration: "none" },
		[`&.${buttonClasses.disabled}`]: { color: (theme.vars || theme).palette.action.disabled },
		variants: [
			{
				props: { variant: "contained" },
				style: {
					color: `var(--variant-containedColor)`,
					backgroundColor: `var(--variant-containedBg)`,
					boxShadow: (theme.vars || theme).shadows[2],
					"&:hover": {
						boxShadow: (theme.vars || theme).shadows[4],
						"@media (hover: none)": { boxShadow: (theme.vars || theme).shadows[2] }
					},
					"&:active": { boxShadow: (theme.vars || theme).shadows[8] },
					[`&.${buttonClasses.focusVisible}`]: { boxShadow: (theme.vars || theme).shadows[6] },
					[`&.${buttonClasses.disabled}`]: {
						color: (theme.vars || theme).palette.action.disabled,
						boxShadow: (theme.vars || theme).shadows[0],
						backgroundColor: (theme.vars || theme).palette.action.disabledBackground
					}
				}
			},
			{
				props: { variant: "outlined" },
				style: {
					padding: "5px 15px",
					border: "1px solid currentColor",
					borderColor: `var(--variant-outlinedBorder, currentColor)`,
					backgroundColor: `var(--variant-outlinedBg)`,
					color: `var(--variant-outlinedColor)`,
					[`&.${buttonClasses.disabled}`]: { border: `1px solid ${(theme.vars || theme).palette.action.disabledBackground}` }
				}
			},
			{
				props: { variant: "text" },
				style: {
					padding: "6px 8px",
					color: `var(--variant-textColor)`,
					backgroundColor: `var(--variant-textBg)`
				}
			},
			...Object.entries(theme.palette).filter(createSimplePaletteValueFilter()).map(([color]) => ({
				props: { color },
				style: {
					"--variant-textColor": (theme.vars || theme).palette[color].main,
					"--variant-outlinedColor": (theme.vars || theme).palette[color].main,
					"--variant-outlinedBorder": theme.vars ? `rgba(${theme.vars.palette[color].mainChannel} / 0.5)` : alpha(theme.palette[color].main, .5),
					"--variant-containedColor": (theme.vars || theme).palette[color].contrastText,
					"--variant-containedBg": (theme.vars || theme).palette[color].main,
					"@media (hover: hover)": { "&:hover": {
						"--variant-containedBg": (theme.vars || theme).palette[color].dark,
						"--variant-textBg": theme.vars ? `rgba(${theme.vars.palette[color].mainChannel} / ${theme.vars.palette.action.hoverOpacity})` : alpha(theme.palette[color].main, theme.palette.action.hoverOpacity),
						"--variant-outlinedBorder": (theme.vars || theme).palette[color].main,
						"--variant-outlinedBg": theme.vars ? `rgba(${theme.vars.palette[color].mainChannel} / ${theme.vars.palette.action.hoverOpacity})` : alpha(theme.palette[color].main, theme.palette.action.hoverOpacity)
					} }
				}
			})),
			{
				props: { color: "inherit" },
				style: {
					color: "inherit",
					borderColor: "currentColor",
					"--variant-containedBg": theme.vars ? theme.vars.palette.Button.inheritContainedBg : inheritContainedBackgroundColor,
					"@media (hover: hover)": { "&:hover": {
						"--variant-containedBg": theme.vars ? theme.vars.palette.Button.inheritContainedHoverBg : inheritContainedHoverBackgroundColor,
						"--variant-textBg": theme.vars ? `rgba(${theme.vars.palette.text.primaryChannel} / ${theme.vars.palette.action.hoverOpacity})` : alpha(theme.palette.text.primary, theme.palette.action.hoverOpacity),
						"--variant-outlinedBg": theme.vars ? `rgba(${theme.vars.palette.text.primaryChannel} / ${theme.vars.palette.action.hoverOpacity})` : alpha(theme.palette.text.primary, theme.palette.action.hoverOpacity)
					} }
				}
			},
			{
				props: {
					size: "small",
					variant: "text"
				},
				style: {
					padding: "4px 5px",
					fontSize: theme.typography.pxToRem(13)
				}
			},
			{
				props: {
					size: "large",
					variant: "text"
				},
				style: {
					padding: "8px 11px",
					fontSize: theme.typography.pxToRem(15)
				}
			},
			{
				props: {
					size: "small",
					variant: "outlined"
				},
				style: {
					padding: "3px 9px",
					fontSize: theme.typography.pxToRem(13)
				}
			},
			{
				props: {
					size: "large",
					variant: "outlined"
				},
				style: {
					padding: "7px 21px",
					fontSize: theme.typography.pxToRem(15)
				}
			},
			{
				props: {
					size: "small",
					variant: "contained"
				},
				style: {
					padding: "4px 10px",
					fontSize: theme.typography.pxToRem(13)
				}
			},
			{
				props: {
					size: "large",
					variant: "contained"
				},
				style: {
					padding: "8px 22px",
					fontSize: theme.typography.pxToRem(15)
				}
			},
			{
				props: { disableElevation: true },
				style: {
					boxShadow: "none",
					"&:hover": { boxShadow: "none" },
					[`&.${buttonClasses.focusVisible}`]: { boxShadow: "none" },
					"&:active": { boxShadow: "none" },
					[`&.${buttonClasses.disabled}`]: { boxShadow: "none" }
				}
			},
			{
				props: { fullWidth: true },
				style: { width: "100%" }
			},
			{
				props: { loadingPosition: "center" },
				style: {
					transition: theme.transitions.create([
						"background-color",
						"box-shadow",
						"border-color"
					], { duration: theme.transitions.duration.short }),
					[`&.${buttonClasses.loading}`]: { color: "transparent" }
				}
			}
		]
	};
}));
var ButtonStartIcon = styled("span", {
	name: "MuiButton",
	slot: "StartIcon",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.startIcon,
			ownerState.loading && styles.startIconLoadingStart,
			styles[`iconSize${capitalize_default(ownerState.size)}`]
		];
	}
})(({ theme }) => ({
	display: "inherit",
	marginRight: 8,
	marginLeft: -4,
	variants: [
		{
			props: { size: "small" },
			style: { marginLeft: -2 }
		},
		{
			props: {
				loadingPosition: "start",
				loading: true
			},
			style: {
				transition: theme.transitions.create(["opacity"], { duration: theme.transitions.duration.short }),
				opacity: 0
			}
		},
		{
			props: {
				loadingPosition: "start",
				loading: true,
				fullWidth: true
			},
			style: { marginRight: -8 }
		},
		...commonIconStyles
	]
}));
var ButtonEndIcon = styled("span", {
	name: "MuiButton",
	slot: "EndIcon",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.endIcon,
			ownerState.loading && styles.endIconLoadingEnd,
			styles[`iconSize${capitalize_default(ownerState.size)}`]
		];
	}
})(({ theme }) => ({
	display: "inherit",
	marginRight: -4,
	marginLeft: 8,
	variants: [
		{
			props: { size: "small" },
			style: { marginRight: -2 }
		},
		{
			props: {
				loadingPosition: "end",
				loading: true
			},
			style: {
				transition: theme.transitions.create(["opacity"], { duration: theme.transitions.duration.short }),
				opacity: 0
			}
		},
		{
			props: {
				loadingPosition: "end",
				loading: true,
				fullWidth: true
			},
			style: { marginLeft: -8 }
		},
		...commonIconStyles
	]
}));
var ButtonLoadingIndicator = styled("span", {
	name: "MuiButton",
	slot: "LoadingIndicator",
	overridesResolver: (props, styles) => styles.loadingIndicator
})(({ theme }) => ({
	display: "none",
	position: "absolute",
	visibility: "visible",
	variants: [
		{
			props: { loading: true },
			style: { display: "flex" }
		},
		{
			props: { loadingPosition: "start" },
			style: { left: 14 }
		},
		{
			props: {
				loadingPosition: "start",
				size: "small"
			},
			style: { left: 10 }
		},
		{
			props: {
				variant: "text",
				loadingPosition: "start"
			},
			style: { left: 6 }
		},
		{
			props: { loadingPosition: "center" },
			style: {
				left: "50%",
				transform: "translate(-50%)",
				color: (theme.vars || theme).palette.action.disabled
			}
		},
		{
			props: { loadingPosition: "end" },
			style: { right: 14 }
		},
		{
			props: {
				loadingPosition: "end",
				size: "small"
			},
			style: { right: 10 }
		},
		{
			props: {
				variant: "text",
				loadingPosition: "end"
			},
			style: { right: 6 }
		},
		{
			props: {
				loadingPosition: "start",
				fullWidth: true
			},
			style: {
				position: "relative",
				left: -10
			}
		},
		{
			props: {
				loadingPosition: "end",
				fullWidth: true
			},
			style: {
				position: "relative",
				right: -10
			}
		}
	]
}));
var ButtonLoadingIconPlaceholder = styled("span", {
	name: "MuiButton",
	slot: "LoadingIconPlaceholder",
	overridesResolver: (props, styles) => styles.loadingIconPlaceholder
})({
	display: "inline-block",
	width: "1em",
	height: "1em"
});
var Button = /*#__PURE__*/ React$4.forwardRef(function Button(inProps, ref) {
	const contextProps = React$4.useContext(ButtonGroupContext);
	const buttonGroupButtonContextPositionClassName = React$4.useContext(ButtonGroupButtonContext);
	const resolvedProps = resolveProps(contextProps, inProps);
	const props = useDefaultProps({
		props: resolvedProps,
		name: "MuiButton"
	});
	const { children, color = "primary", component = "button", className, disabled = false, disableElevation = false, disableFocusRipple = false, endIcon: endIconProp, focusVisibleClassName, fullWidth = false, id: idProp, loading = null, loadingIndicator: loadingIndicatorProp, loadingPosition = "center", size = "medium", startIcon: startIconProp, type, variant = "text", ...other } = props;
	const loadingId = useId_default(idProp);
	const loadingIndicator = loadingIndicatorProp ?? /*#__PURE__*/ (0, import_jsx_runtime.jsx)(CircularProgress, {
		"aria-labelledby": loadingId,
		color: "inherit",
		size: 16
	});
	const ownerState = {
		...props,
		color,
		component,
		disabled,
		disableElevation,
		disableFocusRipple,
		fullWidth,
		loading,
		loadingIndicator,
		loadingPosition,
		size,
		type,
		variant
	};
	const classes = useUtilityClasses$3(ownerState);
	const startIcon = (startIconProp || loading && loadingPosition === "start") && /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ButtonStartIcon, {
		className: classes.startIcon,
		ownerState,
		children: startIconProp || /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ButtonLoadingIconPlaceholder, {
			className: classes.loadingIconPlaceholder,
			ownerState
		})
	});
	const endIcon = (endIconProp || loading && loadingPosition === "end") && /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ButtonEndIcon, {
		className: classes.endIcon,
		ownerState,
		children: endIconProp || /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ButtonLoadingIconPlaceholder, {
			className: classes.loadingIconPlaceholder,
			ownerState
		})
	});
	const positionClassName = buttonGroupButtonContextPositionClassName || "";
	const loader = typeof loading === "boolean" ? /*#__PURE__*/ (0, import_jsx_runtime.jsx)("span", {
		className: classes.loadingWrapper,
		style: { display: "contents" },
		children: loading && /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ButtonLoadingIndicator, {
			className: classes.loadingIndicator,
			ownerState,
			children: loadingIndicator
		})
	}) : null;
	return /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(ButtonRoot, {
		ownerState,
		className: clsx(contextProps.className, classes.root, className, positionClassName),
		component,
		disabled: disabled || loading,
		focusRipple: !disableFocusRipple,
		focusVisibleClassName: clsx(classes.focusVisible, focusVisibleClassName),
		ref,
		type,
		id: loading ? loadingId : idProp,
		...other,
		classes,
		children: [
			startIcon,
			loadingPosition !== "end" && loader,
			children,
			loadingPosition === "end" && loader,
			endIcon
		]
	});
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
var TabRoot = styled(ButtonBase, {
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
var TabScrollButtonRoot = styled(ButtonBase, {
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
var TabsRoot = styled("div", {
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
var TabsScroller = styled("div", {
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
var List = styled("div", {
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
var TabsIndicator = styled("span", {
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
var TabsScrollbarSize = styled(ScrollbarSize)({
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
	const theme = useTheme();
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
// ../node_modules/@mui/icons-material/esm/ArrowBackRounded.js
var ArrowBackRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M19 11H7.83l4.88-4.88c.39-.39.39-1.03 0-1.42a.996.996 0 0 0-1.41 0l-6.59 6.59c-.39.39-.39 1.02 0 1.41l6.59 6.59c.39.39 1.02.39 1.41 0s.39-1.02 0-1.41L7.83 13H19c.55 0 1-.45 1-1s-.45-1-1-1" }), "ArrowBackRounded");
// ../node_modules/@mui/icons-material/esm/CodeRounded.js
var CodeRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M8.7 15.9 4.8 12l3.9-3.9c.39-.39.39-1.01 0-1.4a.984.984 0 0 0-1.4 0l-4.59 4.59c-.39.39-.39 1.02 0 1.41l4.59 4.6c.39.39 1.01.39 1.4 0s.39-1.01 0-1.4m6.6 0 3.9-3.9-3.9-3.9a.984.984 0 0 1 0-1.4c.39-.39 1.01-.39 1.4 0l4.59 4.59c.39.39.39 1.02 0 1.41l-4.59 4.6c-.39.39-1.01.39-1.4 0a.984.984 0 0 1 0-1.4" }), "CodeRounded");
// ../node_modules/@mui/icons-material/esm/ContentCopyRounded.js
var ContentCopyRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M15 20H5V7c0-.55-.45-1-1-1s-1 .45-1 1v13c0 1.1.9 2 2 2h10c.55 0 1-.45 1-1s-.45-1-1-1m5-4V4c0-1.1-.9-2-2-2H9c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h9c1.1 0 2-.9 2-2m-2 0H9V4h9z" }), "ContentCopyRounded");
// ../node_modules/@mui/icons-material/esm/HttpRounded.js
var HttpRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M4.5 11h-2V9.75c0-.41-.34-.75-.75-.75S1 9.34 1 9.75v4.5c0 .41.34.75.75.75s.75-.34.75-.75V12.5h2v1.75c0 .41.34.75.75.75s.75-.34.75-.75v-4.5C6 9.34 5.66 9 5.25 9s-.75.34-.75.75zm3.25-.5h.75v3.75c0 .41.34.75.75.75s.75-.34.75-.75V10.5h.75c.41 0 .75-.34.75-.75S11.16 9 10.75 9h-3c-.41 0-.75.34-.75.75s.34.75.75.75m5.5 0H14v3.75c0 .41.34.75.75.75s.75-.34.75-.75V10.5h.75c.41 0 .75-.34.75-.75S16.66 9 16.25 9h-3c-.41 0-.75.34-.75.75s.34.75.75.75M21.5 9H19c-.55 0-1 .45-1 1v4.25c0 .41.34.75.75.75s.75-.34.75-.75V13h2c.83 0 1.5-.68 1.5-1.5v-1c0-.82-.67-1.5-1.5-1.5m0 2.5h-2v-1h2z" }), "HttpRounded");
// ../node_modules/@mui/icons-material/esm/TimelineRounded.js
var TimelineRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M23 8c0 1.1-.9 2-2 2-.18 0-.35-.02-.51-.07l-3.56 3.55c.05.16.07.34.07.52 0 1.1-.9 2-2 2s-2-.9-2-2c0-.18.02-.36.07-.52l-2.55-2.55c-.16.05-.34.07-.52.07s-.36-.02-.52-.07l-4.55 4.56c.05.16.07.33.07.51 0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2c.18 0 .35.02.51.07l4.56-4.55C8.02 9.36 8 9.18 8 9c0-1.1.9-2 2-2s2 .9 2 2c0 .18-.02.36-.07.52l2.55 2.55c.16-.05.34-.07.52-.07s.36.02.52.07l3.55-3.56C19.02 8.35 19 8.18 19 8c0-1.1.9-2 2-2s2 .9 2 2m0 0c0 1.1-.9 2-2 2-.18 0-.35-.02-.51-.07l-3.56 3.55c.05.16.07.34.07.52 0 1.1-.9 2-2 2s-2-.9-2-2c0-.18.02-.36.07-.52l-2.55-2.55c-.16.05-.34.07-.52.07s-.36-.02-.52-.07l-4.55 4.56c.05.16.07.33.07.51 0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2c.18 0 .35.02.51.07l4.56-4.55C8.02 9.36 8 9.18 8 9c0-1.1.9-2 2-2s2 .9 2 2c0 .18-.02.36-.07.52l2.55 2.55c.16-.05.34-.07.52-.07s.36.02.52.07l3.55-3.56C19.02 8.35 19 8.18 19 8c0-1.1.9-2 2-2s2 .9 2 2" }), "TimelineRounded");
// src/pages/ErrorDetailPage.tsx
var { useEffect, useState } = await importShared("react");
var API_BASE = typeof window !== "undefined" ? `http://${window.location.hostname || "localhost"}:8080` : "http://localhost:8080";
function ErrorDetailPage({ fingerprint, onBack }) {
	const [group, setGroup] = useState(null);
	const [occurrences, setOccurrences] = useState([]);
	const [selectedRecord, setSelectedRecord] = useState(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState(null);
	const [tab, setTab] = useState("stack");
	const [status, setStatus] = useState("NEW");
	const [assignedTo, setAssignedTo] = useState("");
	const [savingTriage, setSavingTriage] = useState(false);
	const [triageSuccess, setTriageSuccess] = useState(false);
	const fetchDetail = async () => {
		setLoading(true);
		setError(null);
		try {
			const res = await axios.get(`${API_BASE}/api/errors/admin/groups/${fingerprint}`);
			const g = res.data.group;
			const occs = res.data.recentOccurrences || [];
			setGroup(g);
			setOccurrences(occs);
			if (occs.length > 0) setSelectedRecord(occs[0]);
			setStatus(g.status);
			setAssignedTo(g.assignedTo || "");
		} catch (err) {
			setError(err?.response?.data?.message || "Failed to load error details");
		} finally {
			setLoading(false);
		}
	};
	useEffect(() => {
		fetchDetail();
	}, [fingerprint]);
	const handleSaveTriage = async () => {
		setSavingTriage(true);
		setTriageSuccess(false);
		try {
			await axios.patch(`${API_BASE}/api/errors/admin/groups/${fingerprint}/triage`, {
				status,
				assignedTo: assignedTo.trim() || null
			});
			setTriageSuccess(true);
			setTimeout(() => setTriageSuccess(false), 3e3);
			fetchDetail();
		} catch (err) {
			setError(err?.response?.data?.message || "Failed to update triage");
		} finally {
			setSavingTriage(false);
		}
	};
	const copyToClipboard = (text) => {
		navigator.clipboard.writeText(text);
	};
	const getSeverityColor = (sev) => {
		switch (sev) {
			case "CRITICAL": return "#EF4444";
			case "ERROR": return "#F59E0B";
			case "WARNING": return "#EAB308";
			default: return "#3B82F6";
		}
	};
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
		sx: {
			p: 3,
			maxWidth: 1400,
			mx: "auto"
		},
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stack, {
				direction: "row",
				spacing: 2,
				alignItems: "center",
				sx: { mb: 3 },
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
					startIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ArrowBackRounded_default, {}),
					onClick: onBack,
					sx: {
						color: "#94A3B8",
						"&:hover": { color: "#F8FAFC" }
					},
					children: "Back to Groups"
				})
			}),
			loading && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(LinearProgress, { sx: {
				mb: 2,
				borderRadius: 1
			} }),
			error && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Alert, {
				severity: "error",
				sx: { mb: 3 },
				children: error
			}),
			group && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Paper, {
				sx: {
					p: 3,
					mb: 3,
					bgcolor: "#111827",
					border: "1px solid #1F2937",
					borderRadius: 3
				},
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
					direction: {
						xs: "column",
						md: "row"
					},
					justifyContent: "space-between",
					spacing: 2,
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
						sx: { maxWidth: 850 },
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								direction: "row",
								spacing: 1.5,
								alignItems: "center",
								sx: { mb: 1 },
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
										size: "small",
										label: group.severity,
										sx: {
											border: `1px solid ${getSeverityColor(group.severity)}`,
											color: getSeverityColor(group.severity),
											fontWeight: 800,
											bgcolor: "transparent"
										}
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
										size: "small",
										label: group.serviceName,
										sx: {
											bgcolor: "rgba(79,70,229,0.15)",
											color: "#818CF8",
											fontWeight: 700
										}
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
										size: "small",
										label: group.environment,
										sx: {
											bgcolor: "rgba(255,255,255,0.06)",
											color: "#94A3B8"
										}
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "caption",
										sx: {
											color: "#64748B",
											fontFamily: "monospace"
										},
										children: ["Fingerprint: ", group.fingerprint]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tooltip, {
										title: "Copy Fingerprint",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
											size: "small",
											onClick: () => copyToClipboard(group.fingerprint),
											sx: { color: "#64748B" },
											children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ContentCopyRounded_default, { fontSize: "inherit" })
										})
									})
								]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "h5",
								sx: {
									fontWeight: 900,
									color: "#F8FAFC",
									mb: 1
								},
								children: group.errorType
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "body1",
								sx: {
									color: "#CBD5E1",
									mb: 2
								},
								children: group.message
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								direction: "row",
								spacing: 3,
								sx: { color: "#94A3B8" },
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "caption",
										children: ["Occurrences: ", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", {
											style: { color: "#F8FAFC" },
											children: group.occurrencesCount.toLocaleString()
										})]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "caption",
										children: ["First Seen: ", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", {
											style: { color: "#F8FAFC" },
											children: new Date(group.firstSeen).toLocaleString()
										})]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "caption",
										children: ["Last Seen: ", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", {
											style: { color: "#F8FAFC" },
											children: new Date(group.lastSeen).toLocaleString()
										})]
									})
								]
							})
						]
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
						sx: {
							p: 2.5,
							bgcolor: "#0B0F19",
							border: "1px solid #1F2937",
							borderRadius: 2.5,
							minWidth: 260
						},
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
							variant: "subtitle2",
							sx: {
								fontWeight: 800,
								color: "#F8FAFC",
								mb: 1.5
							},
							children: "Triage & Assignment"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
							spacing: 2,
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(FormControl, {
									fullWidth: true,
									size: "small",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(InputLabel, {
										sx: { color: "#94A3B8" },
										children: "Status"
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Select, {
										value: status,
										label: "Status",
										onChange: (e) => setStatus(e.target.value),
										sx: {
											color: "#F8FAFC",
											"& fieldset": { borderColor: "#374151" }
										},
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
												value: "NEW",
												children: "NEW"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
												value: "INVESTIGATING",
												children: "INVESTIGATING"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
												value: "RESOLVED",
												children: "RESOLVED"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
												value: "IGNORED",
												children: "IGNORED"
											})
										]
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TextField, {
									size: "small",
									label: "Assignee",
									placeholder: "e.g. admin@ecommerce.com",
									value: assignedTo,
									onChange: (e) => setAssignedTo(e.target.value),
									sx: {
										"& .MuiOutlinedInput-root": {
											color: "#F8FAFC",
											"& fieldset": { borderColor: "#374151" }
										},
										"& .MuiInputLabel-root": { color: "#94A3B8" }
									}
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
									variant: "contained",
									onClick: handleSaveTriage,
									disabled: savingTriage,
									sx: {
										bgcolor: "#4F46E5",
										fontWeight: 700,
										"&:hover": { bgcolor: "#4338CA" }
									},
									children: savingTriage ? "Saving..." : "Update Triage"
								}),
								triageSuccess && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#10B981",
										textAlign: "center"
									},
									children: "Triage updated successfully!"
								})
							]
						})]
					})]
				})
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
				sx: {
					mb: 3,
					bgcolor: "#111827",
					border: "1px solid #1F2937",
					borderRadius: 3
				},
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
						sx: {
							borderBottom: "1px solid #1F2937",
							px: 2
						},
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Tabs, {
							value: tab,
							onChange: (_, v) => setTab(v),
							sx: {
								"& .MuiTab-root": {
									color: "#94A3B8",
									fontWeight: 700
								},
								"& .Mui-selected": { color: "#818CF8" },
								"& .MuiTabs-indicator": { bgcolor: "#4F46E5" }
							},
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tab, {
									icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(CodeRounded_default, {}),
									iconPosition: "start",
									label: "Stack Trace",
									value: "stack"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tab, {
									icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(HttpRounded_default, {}),
									iconPosition: "start",
									label: "Diagnostic Context",
									value: "context"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tab, {
									icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TimelineRounded_default, {}),
									iconPosition: "start",
									label: `Breadcrumbs (${selectedRecord?.breadcrumbs?.length || 0})`,
									value: "breadcrumbs"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tab, {
									icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(BugReportRounded_default, {}),
									iconPosition: "start",
									label: `History (${occurrences.length})`,
									value: "history"
								})
							]
						})
					}),
					tab === "stack" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
						sx: { p: 3 },
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								direction: "row",
								justifyContent: "space-between",
								alignItems: "center",
								sx: { mb: 1.5 },
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "subtitle2",
									sx: {
										fontWeight: 800,
										color: "#94A3B8"
									},
									children: "Sanitized Stack Trace (Sensitive Values Redacted)"
								}), selectedRecord?.stackTrace && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
									size: "small",
									startIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ContentCopyRounded_default, {}),
									onClick: () => copyToClipboard(selectedRecord.stackTrace || ""),
									sx: { color: "#818CF8" },
									children: "Copy Trace"
								})]
							}),
							selectedRecord?.stackTrace ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Paper, {
								sx: {
									p: 2.5,
									bgcolor: "#0B0F19",
									border: "1px solid #1F2937",
									borderRadius: 2,
									fontFamily: "Consolas, monospace",
									fontSize: "0.85rem",
									color: "#F87171",
									whiteSpace: "pre-wrap",
									wordBreak: "break-all",
									maxHeight: 500,
									overflowY: "auto"
								},
								children: selectedRecord.stackTrace
							}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "body2",
								sx: { color: "#64748B" },
								children: "No stack trace provided for this occurrence."
							}),
							selectedRecord?.exceptionChain && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
								sx: { mt: 3 },
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "subtitle2",
									sx: {
										fontWeight: 800,
										color: "#94A3B8",
										mb: 1
									},
									children: "Exception Root Cause Chain"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Paper, {
									sx: {
										p: 2,
										bgcolor: "#0B0F19",
										border: "1px solid #1F2937",
										borderRadius: 2,
										fontFamily: "Consolas, monospace",
										fontSize: "0.82rem",
										color: "#FBBF24",
										whiteSpace: "pre-wrap"
									},
									children: selectedRecord.exceptionChain
								})]
							})
						]
					}),
					tab === "context" && selectedRecord && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
						sx: { p: 3 },
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
							container: true,
							spacing: 2.5,
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
									item: true,
									xs: 12,
									sm: 6,
									md: 4,
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
										sx: {
											p: 2,
											bgcolor: "#0B0F19",
											border: "1px solid #1F2937",
											borderRadius: 2
										},
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
												variant: "caption",
												sx: {
													color: "#64748B",
													fontWeight: 700
												},
												children: "HTTP REQUEST"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "body1",
												sx: {
													fontWeight: 700,
													color: "#F8FAFC",
													mt: .5
												},
												children: [
													selectedRecord.requestMethod || "N/A",
													" ",
													selectedRecord.requestUri || "N/A"
												]
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "caption",
												sx: { color: "#94A3B8" },
												children: ["Response Status: ", selectedRecord.responseStatus || "N/A"]
											})
										]
									})
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
									item: true,
									xs: 12,
									sm: 6,
									md: 4,
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
										sx: {
											p: 2,
											bgcolor: "#0B0F19",
											border: "1px solid #1F2937",
											borderRadius: 2
										},
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: {
												color: "#64748B",
												fontWeight: 700
											},
											children: "DISTRIBUTED TRACE ID"
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "body2",
											sx: {
												fontFamily: "monospace",
												color: "#818CF8",
												mt: .5,
												wordBreak: "break-all"
											},
											children: selectedRecord.traceId || selectedRecord.correlationId || "N/A"
										})]
									})
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
									item: true,
									xs: 12,
									sm: 6,
									md: 4,
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
										sx: {
											p: 2,
											bgcolor: "#0B0F19",
											border: "1px solid #1F2937",
											borderRadius: 2
										},
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
												variant: "caption",
												sx: {
													color: "#64748B",
													fontWeight: 700
												},
												children: "CLIENT & OS"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "body2",
												sx: {
													color: "#F8FAFC",
													mt: .5
												},
												children: ["Browser: ", selectedRecord.browser || "Server Backend"]
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "caption",
												sx: { color: "#94A3B8" },
												children: [
													"OS: ",
													selectedRecord.os || "Linux Container",
													" | Route: ",
													selectedRecord.frontendRoute || "N/A"
												]
											})
										]
									})
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
									item: true,
									xs: 12,
									sm: 6,
									md: 4,
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
										sx: {
											p: 2,
											bgcolor: "#0B0F19",
											border: "1px solid #1F2937",
											borderRadius: 2
										},
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
												variant: "caption",
												sx: {
													color: "#64748B",
													fontWeight: 700
												},
												children: "USER CONTEXT"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "body2",
												sx: {
													color: "#F8FAFC",
													mt: .5
												},
												children: ["User ID: ", selectedRecord.userId ? `#${selectedRecord.userId}` : "Anonymous / System"]
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "caption",
												sx: { color: "#94A3B8" },
												children: ["Role: ", selectedRecord.userRole || "NONE"]
											})
										]
									})
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
									item: true,
									xs: 12,
									sm: 6,
									md: 4,
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
										sx: {
											p: 2,
											bgcolor: "#0B0F19",
											border: "1px solid #1F2937",
											borderRadius: 2
										},
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
												variant: "caption",
												sx: {
													color: "#64748B",
													fontWeight: 700
												},
												children: "VERSION & RELEASE"
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "body2",
												sx: {
													color: "#F8FAFC",
													mt: .5
												},
												children: ["Version: ", selectedRecord.version || "1.0.0"]
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "caption",
												sx: { color: "#94A3B8" },
												children: ["Release: ", selectedRecord.releaseTag || "production-v2.0.0"]
											})
										]
									})
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
									item: true,
									xs: 12,
									sm: 6,
									md: 4,
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
										sx: {
											p: 2,
											bgcolor: "#0B0F19",
											border: "1px solid #1F2937",
											borderRadius: 2
										},
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: {
												color: "#64748B",
												fontWeight: 700
											},
											children: "EVENT TIMESTAMP"
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "body2",
											sx: {
												color: "#F8FAFC",
												mt: .5
											},
											children: new Date(selectedRecord.createdAt).toLocaleString()
										})]
									})
								})
							]
						})
					}),
					tab === "breadcrumbs" && selectedRecord && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
						sx: { p: 3 },
						children: !selectedRecord.breadcrumbs || selectedRecord.breadcrumbs.length === 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
							variant: "body2",
							sx: { color: "#64748B" },
							children: "No breadcrumb telemetry was recorded prior to this event."
						}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stack, {
							spacing: 1.5,
							children: selectedRecord.breadcrumbs.map((b, idx) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
								sx: {
									p: 1.5,
									bgcolor: "#0B0F19",
									border: "1px solid #1F2937",
									borderRadius: 2,
									display: "flex",
									alignItems: "center",
									gap: 2
								},
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
										size: "small",
										label: b.category,
										sx: {
											bgcolor: "rgba(255,255,255,0.06)",
											color: "#818CF8",
											fontWeight: 600
										}
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "body2",
										sx: {
											color: "#E2E8F0",
											flex: 1
										},
										children: b.message
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "caption",
										sx: { color: "#64748B" },
										children: b.timestamp ? new Date(b.timestamp).toLocaleTimeString() : ""
									})
								]
							}, idx))
						})
					}),
					tab === "history" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
						sx: { p: 3 },
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
							variant: "subtitle2",
							sx: {
								color: "#94A3B8",
								fontWeight: 700,
								mb: 2
							},
							children: "Last 20 Occurrences of this Error Fingerprint"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stack, {
							spacing: 1.5,
							children: occurrences.map((occ) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
								onClick: () => setSelectedRecord(occ),
								sx: {
									p: 2,
									bgcolor: selectedRecord?.id === occ.id ? "#1E1B4B" : "#0B0F19",
									border: `1px solid ${selectedRecord?.id === occ.id ? "#4F46E5" : "#1F2937"}`,
									borderRadius: 2,
									cursor: "pointer",
									display: "flex",
									justifyContent: "space-between",
									alignItems: "center",
									"&:hover": { borderColor: "#4F46E5" }
								},
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
									variant: "body2",
									sx: {
										fontWeight: 700,
										color: "#F8FAFC"
									},
									children: [
										"Record #",
										occ.id,
										" — ",
										occ.requestMethod || "INTERNAL",
										" ",
										occ.requestUri || ""
									]
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
									variant: "caption",
									sx: { color: "#94A3B8" },
									children: [
										"Trace ID: ",
										occ.traceId || occ.correlationId || "N/A",
										" | Environment: ",
										occ.environment
									]
								})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#CBD5E1",
										fontWeight: 600
									},
									children: new Date(occ.createdAt).toLocaleString()
								})]
							}, occ.id))
						})]
					})
				]
			})] })
		]
	});
}
export { Tabs as n, Tab as r, ErrorDetailPage as t };
