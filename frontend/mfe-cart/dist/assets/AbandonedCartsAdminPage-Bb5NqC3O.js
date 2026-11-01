import { r as importShared } from "./_virtual___federation_fn_import-7k9UwJ4Q.js";
import { $ as alpha, E as memoTheme, H as composeClasses, M as useTheme, O as capitalize_default, T as useDefaultProps, Y as useId, a as Backdrop, at as generateUtilityClasses, c as Typography, et as darken, i as Box, k as styled, m as Paper, n as Modal, o as Fade, ot as generateUtilityClass, p as useSlot, r as Button, st as clsx, t as Stack, tt as lighten, u as CircularProgress, vt as require_jsx_runtime, w as createSvgIcon } from "./Stack-CWAApUdV.js";
import { a as Grid, i as Tooltip, n as axios, o as Chip, r as TextField, s as Alert, t as RefreshRounded_default } from "./RefreshRounded-q1MiYmvW.js";
// ../node_modules/@mui/material/internal/svg-icons/Person.js
var import_jsx_runtime = require_jsx_runtime();
await importShared("react");
var Person_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" }), "Person");
// ../node_modules/@mui/material/Avatar/avatarClasses.js
function getAvatarUtilityClass(slot) {
	return generateUtilityClass("MuiAvatar", slot);
}
generateUtilityClasses("MuiAvatar", [
	"root",
	"colorDefault",
	"circular",
	"rounded",
	"square",
	"img",
	"fallback"
]);
// ../node_modules/@mui/material/Avatar/Avatar.js
var React$10 = await importShared("react");
var useUtilityClasses$10 = (ownerState) => {
	const { classes, variant, colorDefault } = ownerState;
	return composeClasses({
		root: [
			"root",
			variant,
			colorDefault && "colorDefault"
		],
		img: ["img"],
		fallback: ["fallback"]
	}, getAvatarUtilityClass, classes);
};
var AvatarRoot = styled("div", {
	name: "MuiAvatar",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.root,
			styles[ownerState.variant],
			ownerState.colorDefault && styles.colorDefault
		];
	}
})(memoTheme(({ theme }) => ({
	position: "relative",
	display: "flex",
	alignItems: "center",
	justifyContent: "center",
	flexShrink: 0,
	width: 40,
	height: 40,
	fontFamily: theme.typography.fontFamily,
	fontSize: theme.typography.pxToRem(20),
	lineHeight: 1,
	borderRadius: "50%",
	overflow: "hidden",
	userSelect: "none",
	variants: [
		{
			props: { variant: "rounded" },
			style: { borderRadius: (theme.vars || theme).shape.borderRadius }
		},
		{
			props: { variant: "square" },
			style: { borderRadius: 0 }
		},
		{
			props: { colorDefault: true },
			style: {
				color: (theme.vars || theme).palette.background.default,
				...theme.vars ? { backgroundColor: theme.vars.palette.Avatar.defaultBg } : {
					backgroundColor: theme.palette.grey[400],
					...theme.applyStyles("dark", { backgroundColor: theme.palette.grey[600] })
				}
			}
		}
	]
})));
var AvatarImg = styled("img", {
	name: "MuiAvatar",
	slot: "Img",
	overridesResolver: (props, styles) => styles.img
})({
	width: "100%",
	height: "100%",
	textAlign: "center",
	objectFit: "cover",
	color: "transparent",
	textIndent: 1e4
});
var AvatarFallback = styled(Person_default, {
	name: "MuiAvatar",
	slot: "Fallback",
	overridesResolver: (props, styles) => styles.fallback
})({
	width: "75%",
	height: "75%"
});
function useLoaded({ crossOrigin, referrerPolicy, src, srcSet }) {
	const [loaded, setLoaded] = React$10.useState(false);
	React$10.useEffect(() => {
		if (!src && !srcSet) return;
		setLoaded(false);
		let active = true;
		const image = new Image();
		image.onload = () => {
			if (!active) return;
			setLoaded("loaded");
		};
		image.onerror = () => {
			if (!active) return;
			setLoaded("error");
		};
		image.crossOrigin = crossOrigin;
		image.referrerPolicy = referrerPolicy;
		image.src = src;
		if (srcSet) image.srcset = srcSet;
		return () => {
			active = false;
		};
	}, [
		crossOrigin,
		referrerPolicy,
		src,
		srcSet
	]);
	return loaded;
}
var Avatar = /*#__PURE__*/ React$10.forwardRef(function Avatar(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiAvatar"
	});
	const { alt, children: childrenProp, className, component = "div", slots = {}, slotProps = {}, imgProps, sizes, src, srcSet, variant = "circular", ...other } = props;
	let children = null;
	const ownerState = {
		...props,
		component,
		variant
	};
	const loaded = useLoaded({
		...imgProps,
		...typeof slotProps.img === "function" ? slotProps.img(ownerState) : slotProps.img,
		src,
		srcSet
	});
	const hasImg = src || srcSet;
	const hasImgNotFailing = hasImg && loaded !== "error";
	ownerState.colorDefault = !hasImgNotFailing;
	delete ownerState.ownerState;
	const classes = useUtilityClasses$10(ownerState);
	const [ImgSlot, imgSlotProps] = useSlot("img", {
		className: classes.img,
		elementType: AvatarImg,
		externalForwardedProps: {
			slots,
			slotProps: { img: {
				...imgProps,
				...slotProps.img
			} }
		},
		additionalProps: {
			alt,
			src,
			srcSet,
			sizes
		},
		ownerState
	});
	if (hasImgNotFailing) children = /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ImgSlot, { ...imgSlotProps });
	else if (!!childrenProp || childrenProp === 0) children = childrenProp;
	else if (hasImg && alt) children = alt[0];
	else children = /*#__PURE__*/ (0, import_jsx_runtime.jsx)(AvatarFallback, {
		ownerState,
		className: classes.fallback
	});
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(AvatarRoot, {
		as: component,
		className: clsx(classes.root, className),
		ref,
		...other,
		ownerState,
		children
	});
});
// ../node_modules/@mui/material/Dialog/dialogClasses.js
function getDialogUtilityClass(slot) {
	return generateUtilityClass("MuiDialog", slot);
}
var dialogClasses = generateUtilityClasses("MuiDialog", [
	"root",
	"scrollPaper",
	"scrollBody",
	"container",
	"paper",
	"paperScrollPaper",
	"paperScrollBody",
	"paperWidthFalse",
	"paperWidthXs",
	"paperWidthSm",
	"paperWidthMd",
	"paperWidthLg",
	"paperWidthXl",
	"paperFullWidth",
	"paperFullScreen"
]);
// ../node_modules/@mui/material/Dialog/DialogContext.js
var DialogContext = /*#__PURE__*/ (await importShared("react")).createContext({});
// ../node_modules/@mui/material/Dialog/Dialog.js
var React$9 = await importShared("react");
var DialogBackdrop = styled(Backdrop, {
	name: "MuiDialog",
	slot: "Backdrop",
	overrides: (props, styles) => styles.backdrop
})({ zIndex: -1 });
var useUtilityClasses$9 = (ownerState) => {
	const { classes, scroll, maxWidth, fullWidth, fullScreen } = ownerState;
	const slots = {
		root: ["root"],
		container: ["container", `scroll${capitalize_default(scroll)}`],
		paper: [
			"paper",
			`paperScroll${capitalize_default(scroll)}`,
			`paperWidth${capitalize_default(String(maxWidth))}`,
			fullWidth && "paperFullWidth",
			fullScreen && "paperFullScreen"
		]
	};
	return composeClasses(slots, getDialogUtilityClass, classes);
};
var DialogRoot = styled(Modal, {
	name: "MuiDialog",
	slot: "Root",
	overridesResolver: (props, styles) => styles.root
})({ "@media print": { position: "absolute !important" } });
var DialogContainer = styled("div", {
	name: "MuiDialog",
	slot: "Container",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [styles.container, styles[`scroll${capitalize_default(ownerState.scroll)}`]];
	}
})({
	height: "100%",
	"@media print": { height: "auto" },
	outline: 0,
	variants: [{
		props: { scroll: "paper" },
		style: {
			display: "flex",
			justifyContent: "center",
			alignItems: "center"
		}
	}, {
		props: { scroll: "body" },
		style: {
			overflowY: "auto",
			overflowX: "hidden",
			textAlign: "center",
			"&::after": {
				content: "\"\"",
				display: "inline-block",
				verticalAlign: "middle",
				height: "100%",
				width: "0"
			}
		}
	}]
});
var DialogPaper = styled(Paper, {
	name: "MuiDialog",
	slot: "Paper",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.paper,
			styles[`scrollPaper${capitalize_default(ownerState.scroll)}`],
			styles[`paperWidth${capitalize_default(String(ownerState.maxWidth))}`],
			ownerState.fullWidth && styles.paperFullWidth,
			ownerState.fullScreen && styles.paperFullScreen
		];
	}
})(memoTheme(({ theme }) => ({
	margin: 32,
	position: "relative",
	overflowY: "auto",
	"@media print": {
		overflowY: "visible",
		boxShadow: "none"
	},
	variants: [
		{
			props: { scroll: "paper" },
			style: {
				display: "flex",
				flexDirection: "column",
				maxHeight: "calc(100% - 64px)"
			}
		},
		{
			props: { scroll: "body" },
			style: {
				display: "inline-block",
				verticalAlign: "middle",
				textAlign: "initial"
			}
		},
		{
			props: ({ ownerState }) => !ownerState.maxWidth,
			style: { maxWidth: "calc(100% - 64px)" }
		},
		{
			props: { maxWidth: "xs" },
			style: {
				maxWidth: theme.breakpoints.unit === "px" ? Math.max(theme.breakpoints.values.xs, 444) : `max(${theme.breakpoints.values.xs}${theme.breakpoints.unit}, 444px)`,
				[`&.${dialogClasses.paperScrollBody}`]: { [theme.breakpoints.down(Math.max(theme.breakpoints.values.xs, 444) + 64)]: { maxWidth: "calc(100% - 64px)" } }
			}
		},
		...Object.keys(theme.breakpoints.values).filter((maxWidth) => maxWidth !== "xs").map((maxWidth) => ({
			props: { maxWidth },
			style: {
				maxWidth: `${theme.breakpoints.values[maxWidth]}${theme.breakpoints.unit}`,
				[`&.${dialogClasses.paperScrollBody}`]: { [theme.breakpoints.down(theme.breakpoints.values[maxWidth] + 64)]: { maxWidth: "calc(100% - 64px)" } }
			}
		})),
		{
			props: ({ ownerState }) => ownerState.fullWidth,
			style: { width: "calc(100% - 64px)" }
		},
		{
			props: ({ ownerState }) => ownerState.fullScreen,
			style: {
				margin: 0,
				width: "100%",
				maxWidth: "100%",
				height: "100%",
				maxHeight: "none",
				borderRadius: 0,
				[`&.${dialogClasses.paperScrollBody}`]: {
					margin: 0,
					maxWidth: "100%"
				}
			}
		}
	]
})));
/**
* Dialogs are overlaid modal paper based components with a backdrop.
*/
var Dialog = /*#__PURE__*/ React$9.forwardRef(function Dialog(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiDialog"
	});
	const theme = useTheme();
	const defaultTransitionDuration = {
		enter: theme.transitions.duration.enteringScreen,
		exit: theme.transitions.duration.leavingScreen
	};
	const { "aria-describedby": ariaDescribedby, "aria-labelledby": ariaLabelledbyProp, "aria-modal": ariaModal = true, BackdropComponent, BackdropProps, children, className, disableEscapeKeyDown = false, fullScreen = false, fullWidth = false, maxWidth = "sm", onBackdropClick, onClick, onClose, open, PaperComponent = Paper, PaperProps = {}, scroll = "paper", slots = {}, slotProps = {}, TransitionComponent = Fade, transitionDuration = defaultTransitionDuration, TransitionProps, ...other } = props;
	const ownerState = {
		...props,
		disableEscapeKeyDown,
		fullScreen,
		fullWidth,
		maxWidth,
		scroll
	};
	const classes = useUtilityClasses$9(ownerState);
	const backdropClick = React$9.useRef();
	const handleMouseDown = (event) => {
		backdropClick.current = event.target === event.currentTarget;
	};
	const handleBackdropClick = (event) => {
		if (onClick) onClick(event);
		if (!backdropClick.current) return;
		backdropClick.current = null;
		if (onBackdropClick) onBackdropClick(event);
		if (onClose) onClose(event, "backdropClick");
	};
	const ariaLabelledby = useId(ariaLabelledbyProp);
	const dialogContextValue = React$9.useMemo(() => {
		return { titleId: ariaLabelledby };
	}, [ariaLabelledby]);
	const externalForwardedProps = {
		slots: {
			transition: TransitionComponent,
			...slots
		},
		slotProps: {
			transition: TransitionProps,
			paper: PaperProps,
			backdrop: BackdropProps,
			...slotProps
		}
	};
	const [RootSlot, rootSlotProps] = useSlot("root", {
		elementType: DialogRoot,
		shouldForwardComponentProp: true,
		externalForwardedProps,
		ownerState,
		className: clsx(classes.root, className),
		ref
	});
	const [BackdropSlot, backdropSlotProps] = useSlot("backdrop", {
		elementType: DialogBackdrop,
		shouldForwardComponentProp: true,
		externalForwardedProps,
		ownerState
	});
	const [PaperSlot, paperSlotProps] = useSlot("paper", {
		elementType: DialogPaper,
		shouldForwardComponentProp: true,
		externalForwardedProps,
		ownerState,
		className: clsx(classes.paper, PaperProps.className)
	});
	const [ContainerSlot, containerSlotProps] = useSlot("container", {
		elementType: DialogContainer,
		externalForwardedProps,
		ownerState,
		className: clsx(classes.container)
	});
	const [TransitionSlot, transitionSlotProps] = useSlot("transition", {
		elementType: Fade,
		externalForwardedProps,
		ownerState,
		additionalProps: {
			appear: true,
			in: open,
			timeout: transitionDuration,
			role: "presentation"
		}
	});
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(RootSlot, {
		closeAfterTransition: true,
		slots: { backdrop: BackdropSlot },
		slotProps: { backdrop: {
			transitionDuration,
			as: BackdropComponent,
			...backdropSlotProps
		} },
		disableEscapeKeyDown,
		onClose,
		open,
		onClick: handleBackdropClick,
		...rootSlotProps,
		...other,
		children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TransitionSlot, {
			...transitionSlotProps,
			children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(ContainerSlot, {
				onMouseDown: handleMouseDown,
				...containerSlotProps,
				children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(PaperSlot, {
					as: PaperComponent,
					elevation: 24,
					role: "dialog",
					"aria-describedby": ariaDescribedby,
					"aria-labelledby": ariaLabelledby,
					"aria-modal": ariaModal,
					...paperSlotProps,
					children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(DialogContext.Provider, {
						value: dialogContextValue,
						children
					})
				})
			})
		})
	});
});
// ../node_modules/@mui/material/DialogActions/dialogActionsClasses.js
function getDialogActionsUtilityClass(slot) {
	return generateUtilityClass("MuiDialogActions", slot);
}
generateUtilityClasses("MuiDialogActions", ["root", "spacing"]);
// ../node_modules/@mui/material/DialogActions/DialogActions.js
var React$8 = await importShared("react");
var useUtilityClasses$8 = (ownerState) => {
	const { classes, disableSpacing } = ownerState;
	return composeClasses({ root: ["root", !disableSpacing && "spacing"] }, getDialogActionsUtilityClass, classes);
};
var DialogActionsRoot = styled("div", {
	name: "MuiDialogActions",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [styles.root, !ownerState.disableSpacing && styles.spacing];
	}
})({
	display: "flex",
	alignItems: "center",
	padding: 8,
	justifyContent: "flex-end",
	flex: "0 0 auto",
	variants: [{
		props: ({ ownerState }) => !ownerState.disableSpacing,
		style: { "& > :not(style) ~ :not(style)": { marginLeft: 8 } }
	}]
});
var DialogActions = /*#__PURE__*/ React$8.forwardRef(function DialogActions(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiDialogActions"
	});
	const { className, disableSpacing = false, ...other } = props;
	const ownerState = {
		...props,
		disableSpacing
	};
	const classes = useUtilityClasses$8(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(DialogActionsRoot, {
		className: clsx(classes.root, className),
		ownerState,
		ref,
		...other
	});
});
// ../node_modules/@mui/material/DialogContent/dialogContentClasses.js
function getDialogContentUtilityClass(slot) {
	return generateUtilityClass("MuiDialogContent", slot);
}
generateUtilityClasses("MuiDialogContent", ["root", "dividers"]);
// ../node_modules/@mui/material/DialogTitle/dialogTitleClasses.js
function getDialogTitleUtilityClass(slot) {
	return generateUtilityClass("MuiDialogTitle", slot);
}
var dialogTitleClasses = generateUtilityClasses("MuiDialogTitle", ["root"]);
// ../node_modules/@mui/material/DialogContent/DialogContent.js
var React$7 = await importShared("react");
var useUtilityClasses$7 = (ownerState) => {
	const { classes, dividers } = ownerState;
	return composeClasses({ root: ["root", dividers && "dividers"] }, getDialogContentUtilityClass, classes);
};
var DialogContentRoot = styled("div", {
	name: "MuiDialogContent",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [styles.root, ownerState.dividers && styles.dividers];
	}
})(memoTheme(({ theme }) => ({
	flex: "1 1 auto",
	WebkitOverflowScrolling: "touch",
	overflowY: "auto",
	padding: "20px 24px",
	variants: [{
		props: ({ ownerState }) => ownerState.dividers,
		style: {
			padding: "16px 24px",
			borderTop: `1px solid ${(theme.vars || theme).palette.divider}`,
			borderBottom: `1px solid ${(theme.vars || theme).palette.divider}`
		}
	}, {
		props: ({ ownerState }) => !ownerState.dividers,
		style: { [`.${dialogTitleClasses.root} + &`]: { paddingTop: 0 } }
	}]
})));
var DialogContent = /*#__PURE__*/ React$7.forwardRef(function DialogContent(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiDialogContent"
	});
	const { className, dividers = false, ...other } = props;
	const ownerState = {
		...props,
		dividers
	};
	const classes = useUtilityClasses$7(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(DialogContentRoot, {
		className: clsx(classes.root, className),
		ownerState,
		ref,
		...other
	});
});
// ../node_modules/@mui/material/DialogTitle/DialogTitle.js
var React$6 = await importShared("react");
var useUtilityClasses$6 = (ownerState) => {
	const { classes } = ownerState;
	return composeClasses({ root: ["root"] }, getDialogTitleUtilityClass, classes);
};
var DialogTitleRoot = styled(Typography, {
	name: "MuiDialogTitle",
	slot: "Root",
	overridesResolver: (props, styles) => styles.root
})({
	padding: "16px 24px",
	flex: "0 0 auto"
});
var DialogTitle = /*#__PURE__*/ React$6.forwardRef(function DialogTitle(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiDialogTitle"
	});
	const { className, id: idProp, ...other } = props;
	const ownerState = props;
	const classes = useUtilityClasses$6(ownerState);
	const { titleId = idProp } = React$6.useContext(DialogContext);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(DialogTitleRoot, {
		component: "h2",
		className: clsx(classes.root, className),
		ownerState,
		ref,
		variant: "h6",
		id: idProp ?? titleId,
		...other
	});
});
// ../node_modules/@mui/material/Table/TableContext.js
/**
* @ignore - internal component.
*/
var TableContext = /*#__PURE__*/ (await importShared("react")).createContext();
// ../node_modules/@mui/material/Table/tableClasses.js
function getTableUtilityClass(slot) {
	return generateUtilityClass("MuiTable", slot);
}
generateUtilityClasses("MuiTable", ["root", "stickyHeader"]);
// ../node_modules/@mui/material/Table/Table.js
var React$5 = await importShared("react");
var useUtilityClasses$5 = (ownerState) => {
	const { classes, stickyHeader } = ownerState;
	return composeClasses({ root: ["root", stickyHeader && "stickyHeader"] }, getTableUtilityClass, classes);
};
var TableRoot = styled("table", {
	name: "MuiTable",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [styles.root, ownerState.stickyHeader && styles.stickyHeader];
	}
})(memoTheme(({ theme }) => ({
	display: "table",
	width: "100%",
	borderCollapse: "collapse",
	borderSpacing: 0,
	"& caption": {
		...theme.typography.body2,
		padding: theme.spacing(2),
		color: (theme.vars || theme).palette.text.secondary,
		textAlign: "left",
		captionSide: "bottom"
	},
	variants: [{
		props: ({ ownerState }) => ownerState.stickyHeader,
		style: { borderCollapse: "separate" }
	}]
})));
var defaultComponent$3 = "table";
var Table = /*#__PURE__*/ React$5.forwardRef(function Table(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTable"
	});
	const { className, component = defaultComponent$3, padding = "normal", size = "medium", stickyHeader = false, ...other } = props;
	const ownerState = {
		...props,
		component,
		padding,
		size,
		stickyHeader
	};
	const classes = useUtilityClasses$5(ownerState);
	const table = React$5.useMemo(() => ({
		padding,
		size,
		stickyHeader
	}), [
		padding,
		size,
		stickyHeader
	]);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TableContext.Provider, {
		value: table,
		children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TableRoot, {
			as: component,
			role: component === defaultComponent$3 ? null : "table",
			ref,
			className: clsx(classes.root, className),
			ownerState,
			...other
		})
	});
});
// ../node_modules/@mui/material/Table/Tablelvl2Context.js
/**
* @ignore - internal component.
*/
var Tablelvl2Context = /*#__PURE__*/ (await importShared("react")).createContext();
// ../node_modules/@mui/material/TableBody/tableBodyClasses.js
function getTableBodyUtilityClass(slot) {
	return generateUtilityClass("MuiTableBody", slot);
}
generateUtilityClasses("MuiTableBody", ["root"]);
// ../node_modules/@mui/material/TableBody/TableBody.js
var React$4 = await importShared("react");
var useUtilityClasses$4 = (ownerState) => {
	const { classes } = ownerState;
	return composeClasses({ root: ["root"] }, getTableBodyUtilityClass, classes);
};
var TableBodyRoot = styled("tbody", {
	name: "MuiTableBody",
	slot: "Root",
	overridesResolver: (props, styles) => styles.root
})({ display: "table-row-group" });
var tablelvl2$1 = { variant: "body" };
var defaultComponent$2 = "tbody";
var TableBody = /*#__PURE__*/ React$4.forwardRef(function TableBody(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTableBody"
	});
	const { className, component = defaultComponent$2, ...other } = props;
	const ownerState = {
		...props,
		component
	};
	const classes = useUtilityClasses$4(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(Tablelvl2Context.Provider, {
		value: tablelvl2$1,
		children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TableBodyRoot, {
			className: clsx(classes.root, className),
			as: component,
			ref,
			role: component === defaultComponent$2 ? null : "rowgroup",
			ownerState,
			...other
		})
	});
});
// ../node_modules/@mui/material/TableCell/tableCellClasses.js
function getTableCellUtilityClass(slot) {
	return generateUtilityClass("MuiTableCell", slot);
}
var tableCellClasses = generateUtilityClasses("MuiTableCell", [
	"root",
	"head",
	"body",
	"footer",
	"sizeSmall",
	"sizeMedium",
	"paddingCheckbox",
	"paddingNone",
	"alignLeft",
	"alignCenter",
	"alignRight",
	"alignJustify",
	"stickyHeader"
]);
// ../node_modules/@mui/material/TableCell/TableCell.js
var React$3 = await importShared("react");
var useUtilityClasses$3 = (ownerState) => {
	const { classes, variant, align, padding, size, stickyHeader } = ownerState;
	const slots = { root: [
		"root",
		variant,
		stickyHeader && "stickyHeader",
		align !== "inherit" && `align${capitalize_default(align)}`,
		padding !== "normal" && `padding${capitalize_default(padding)}`,
		`size${capitalize_default(size)}`
	] };
	return composeClasses(slots, getTableCellUtilityClass, classes);
};
var TableCellRoot = styled("td", {
	name: "MuiTableCell",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.root,
			styles[ownerState.variant],
			styles[`size${capitalize_default(ownerState.size)}`],
			ownerState.padding !== "normal" && styles[`padding${capitalize_default(ownerState.padding)}`],
			ownerState.align !== "inherit" && styles[`align${capitalize_default(ownerState.align)}`],
			ownerState.stickyHeader && styles.stickyHeader
		];
	}
})(memoTheme(({ theme }) => ({
	...theme.typography.body2,
	display: "table-cell",
	verticalAlign: "inherit",
	borderBottom: theme.vars ? `1px solid ${theme.vars.palette.TableCell.border}` : `1px solid
    ${theme.palette.mode === "light" ? lighten(alpha(theme.palette.divider, 1), .88) : darken(alpha(theme.palette.divider, 1), .68)}`,
	textAlign: "left",
	padding: 16,
	variants: [
		{
			props: { variant: "head" },
			style: {
				color: (theme.vars || theme).palette.text.primary,
				lineHeight: theme.typography.pxToRem(24),
				fontWeight: theme.typography.fontWeightMedium
			}
		},
		{
			props: { variant: "body" },
			style: { color: (theme.vars || theme).palette.text.primary }
		},
		{
			props: { variant: "footer" },
			style: {
				color: (theme.vars || theme).palette.text.secondary,
				lineHeight: theme.typography.pxToRem(21),
				fontSize: theme.typography.pxToRem(12)
			}
		},
		{
			props: { size: "small" },
			style: {
				padding: "6px 16px",
				[`&.${tableCellClasses.paddingCheckbox}`]: {
					width: 24,
					padding: "0 12px 0 16px",
					"& > *": { padding: 0 }
				}
			}
		},
		{
			props: { padding: "checkbox" },
			style: {
				width: 48,
				padding: "0 0 0 4px"
			}
		},
		{
			props: { padding: "none" },
			style: { padding: 0 }
		},
		{
			props: { align: "left" },
			style: { textAlign: "left" }
		},
		{
			props: { align: "center" },
			style: { textAlign: "center" }
		},
		{
			props: { align: "right" },
			style: {
				textAlign: "right",
				flexDirection: "row-reverse"
			}
		},
		{
			props: { align: "justify" },
			style: { textAlign: "justify" }
		},
		{
			props: ({ ownerState }) => ownerState.stickyHeader,
			style: {
				position: "sticky",
				top: 0,
				zIndex: 2,
				backgroundColor: (theme.vars || theme).palette.background.default
			}
		}
	]
})));
/**
* The component renders a `<th>` element when the parent context is a header
* or otherwise a `<td>` element.
*/
var TableCell = /*#__PURE__*/ React$3.forwardRef(function TableCell(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTableCell"
	});
	const { align = "inherit", className, component: componentProp, padding: paddingProp, scope: scopeProp, size: sizeProp, sortDirection, variant: variantProp, ...other } = props;
	const table = React$3.useContext(TableContext);
	const tablelvl2 = React$3.useContext(Tablelvl2Context);
	const isHeadCell = tablelvl2 && tablelvl2.variant === "head";
	let component;
	if (componentProp) component = componentProp;
	else component = isHeadCell ? "th" : "td";
	let scope = scopeProp;
	if (component === "td") scope = void 0;
	else if (!scope && isHeadCell) scope = "col";
	const variant = variantProp || tablelvl2 && tablelvl2.variant;
	const ownerState = {
		...props,
		align,
		component,
		padding: paddingProp || (table && table.padding ? table.padding : "normal"),
		size: sizeProp || (table && table.size ? table.size : "medium"),
		sortDirection,
		stickyHeader: variant === "head" && table && table.stickyHeader,
		variant
	};
	const classes = useUtilityClasses$3(ownerState);
	let ariaSort = null;
	if (sortDirection) ariaSort = sortDirection === "asc" ? "ascending" : "descending";
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TableCellRoot, {
		as: component,
		ref,
		className: clsx(classes.root, className),
		"aria-sort": ariaSort,
		scope,
		ownerState,
		...other
	});
});
// ../node_modules/@mui/material/TableContainer/tableContainerClasses.js
function getTableContainerUtilityClass(slot) {
	return generateUtilityClass("MuiTableContainer", slot);
}
generateUtilityClasses("MuiTableContainer", ["root"]);
// ../node_modules/@mui/material/TableContainer/TableContainer.js
var React$2 = await importShared("react");
var useUtilityClasses$2 = (ownerState) => {
	const { classes } = ownerState;
	return composeClasses({ root: ["root"] }, getTableContainerUtilityClass, classes);
};
var TableContainerRoot = styled("div", {
	name: "MuiTableContainer",
	slot: "Root",
	overridesResolver: (props, styles) => styles.root
})({
	width: "100%",
	overflowX: "auto"
});
var TableContainer = /*#__PURE__*/ React$2.forwardRef(function TableContainer(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTableContainer"
	});
	const { className, component = "div", ...other } = props;
	const ownerState = {
		...props,
		component
	};
	const classes = useUtilityClasses$2(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TableContainerRoot, {
		ref,
		as: component,
		className: clsx(classes.root, className),
		ownerState,
		...other
	});
});
// ../node_modules/@mui/material/TableHead/tableHeadClasses.js
function getTableHeadUtilityClass(slot) {
	return generateUtilityClass("MuiTableHead", slot);
}
generateUtilityClasses("MuiTableHead", ["root"]);
// ../node_modules/@mui/material/TableHead/TableHead.js
var React$1 = await importShared("react");
var useUtilityClasses$1 = (ownerState) => {
	const { classes } = ownerState;
	return composeClasses({ root: ["root"] }, getTableHeadUtilityClass, classes);
};
var TableHeadRoot = styled("thead", {
	name: "MuiTableHead",
	slot: "Root",
	overridesResolver: (props, styles) => styles.root
})({ display: "table-header-group" });
var tablelvl2 = { variant: "head" };
var defaultComponent$1 = "thead";
var TableHead = /*#__PURE__*/ React$1.forwardRef(function TableHead(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTableHead"
	});
	const { className, component = defaultComponent$1, ...other } = props;
	const ownerState = {
		...props,
		component
	};
	const classes = useUtilityClasses$1(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(Tablelvl2Context.Provider, {
		value: tablelvl2,
		children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TableHeadRoot, {
			as: component,
			className: clsx(classes.root, className),
			ref,
			role: component === defaultComponent$1 ? null : "rowgroup",
			ownerState,
			...other
		})
	});
});
// ../node_modules/@mui/material/TableRow/tableRowClasses.js
function getTableRowUtilityClass(slot) {
	return generateUtilityClass("MuiTableRow", slot);
}
var tableRowClasses = generateUtilityClasses("MuiTableRow", [
	"root",
	"selected",
	"hover",
	"head",
	"footer"
]);
// ../node_modules/@mui/material/TableRow/TableRow.js
var React = await importShared("react");
var useUtilityClasses = (ownerState) => {
	const { classes, selected, hover, head, footer } = ownerState;
	return composeClasses({ root: [
		"root",
		selected && "selected",
		hover && "hover",
		head && "head",
		footer && "footer"
	] }, getTableRowUtilityClass, classes);
};
var TableRowRoot = styled("tr", {
	name: "MuiTableRow",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.root,
			ownerState.head && styles.head,
			ownerState.footer && styles.footer
		];
	}
})(memoTheme(({ theme }) => ({
	color: "inherit",
	display: "table-row",
	verticalAlign: "middle",
	outline: 0,
	[`&.${tableRowClasses.hover}:hover`]: { backgroundColor: (theme.vars || theme).palette.action.hover },
	[`&.${tableRowClasses.selected}`]: {
		backgroundColor: theme.vars ? `rgba(${theme.vars.palette.primary.mainChannel} / ${theme.vars.palette.action.selectedOpacity})` : alpha(theme.palette.primary.main, theme.palette.action.selectedOpacity),
		"&:hover": { backgroundColor: theme.vars ? `rgba(${theme.vars.palette.primary.mainChannel} / calc(${theme.vars.palette.action.selectedOpacity} + ${theme.vars.palette.action.hoverOpacity}))` : alpha(theme.palette.primary.main, theme.palette.action.selectedOpacity + theme.palette.action.hoverOpacity) }
	}
})));
var defaultComponent = "tr";
/**
* Will automatically set dynamic row height
* based on the material table element parent (head, body, etc).
*/
var TableRow = /*#__PURE__*/ React.forwardRef(function TableRow(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiTableRow"
	});
	const { className, component = defaultComponent, hover = false, selected = false, ...other } = props;
	const tablelvl2 = React.useContext(Tablelvl2Context);
	const ownerState = {
		...props,
		component,
		hover,
		selected,
		head: tablelvl2 && tablelvl2.variant === "head",
		footer: tablelvl2 && tablelvl2.variant === "footer"
	};
	const classes = useUtilityClasses(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TableRowRoot, {
		as: component,
		ref,
		className: clsx(classes.root, className),
		role: component === defaultComponent ? null : "row",
		ownerState,
		...other
	});
});
// ../node_modules/@mui/icons-material/esm/AccessTimeRounded.js
var AccessTimeRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2M12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8m-.22-13h-.06c-.4 0-.72.32-.72.72v4.72c0 .35.18.68.49.86l4.15 2.49c.34.2.78.1.98-.24.21-.34.1-.79-.25-.99l-3.87-2.3V7.72c0-.4-.32-.72-.72-.72" }), "AccessTimeRounded");
// ../node_modules/@mui/icons-material/esm/AttachMoneyRounded.js
var AttachMoneyRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.42 0 2.13.54 2.39 1.4.12.4.45.7.87.7h.3c.66 0 1.13-.65.9-1.27-.42-1.18-1.4-2.16-2.96-2.54V4.5c0-.83-.67-1.5-1.5-1.5S10 3.67 10 4.5v.66c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-1.65 0-2.5-.59-2.83-1.43-.15-.39-.49-.67-.9-.67h-.28c-.67 0-1.14.68-.89 1.3.57 1.39 1.9 2.21 3.4 2.53v.67c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-.65c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4" }), "AttachMoneyRounded");
// ../node_modules/@mui/icons-material/esm/MarkEmailReadRounded.js
var MarkEmailReadRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M18.05 21.29c-.39.39-1.02.39-1.41 0l-2.12-2.12a.996.996 0 0 1 0-1.41c.39-.39 1.02-.39 1.41 0l1.41 1.41 3.54-3.54c.39-.39 1.02-.39 1.41 0s.39 1.02 0 1.41zM12.08 20H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2h16c1.1 0 2 .9 2 2v6.68c-.91-.43-1.92-.68-3-.68-3.87 0-7 3.13-7 7 0 .34.03.67.08 1m-.61-7.33c.32.2.74.2 1.06 0l7.07-4.42c.25-.16.4-.43.4-.72 0-.67-.73-1.07-1.3-.72L12 11 5.3 6.81c-.57-.35-1.3.05-1.3.72 0 .29.15.56.4.72z" }), "MarkEmailReadRounded");
// ../node_modules/@mui/icons-material/esm/SendRounded.js
var SendRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "m3.4 20.4 17.45-7.48c.81-.35.81-1.49 0-1.84L3.4 3.6c-.66-.29-1.39.2-1.39.91L2 9.12c0 .5.37.93.87.99L17 12 2.87 13.88c-.5.07-.87.5-.87 1l.01 4.61c0 .71.73 1.2 1.39.91" }), "SendRounded");
// ../node_modules/@mui/icons-material/esm/ShoppingCartCheckoutRounded.js
var ShoppingCartCheckoutRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2m10 0c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2m2-2c0-.55-.45-1-1-1H7l1.1-2h7.45c.75 0 1.41-.41 1.75-1.03l3.24-6.14c.25-.48.08-1.08-.4-1.34-.49-.27-1.1-.08-1.36.41L15.55 11H8.53L4.54 2.57c-.16-.35-.52-.57-.9-.57H2c-.55 0-1 .45-1 1s.45 1 1 1h1l3.6 7.59-1.35 2.44C4.52 15.37 5.48 17 7 17h11c.55 0 1-.45 1-1M11.29 2.71c.39-.39 1.02-.39 1.41 0l2.59 2.59c.39.39.39 1.02 0 1.41L12.7 9.3c-.39.39-1.02.39-1.41 0a.996.996 0 0 1 0-1.41l.88-.89H9c-.55 0-1-.45-1-1s.45-1 1-1h3.17l-.88-.88a.996.996 0 0 1 0-1.41" }), "ShoppingCartCheckoutRounded");
// ../node_modules/@mui/icons-material/esm/TrendingDownRounded.js
var TrendingDownRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "m16.85 17.15 1.44-1.44-4.88-4.88-3.29 3.29c-.39.39-1.02.39-1.41 0l-6-6.01a.996.996 0 0 1 0-1.41c.39-.39 1.02-.39 1.41 0L9.41 12l3.29-3.29c.39-.39 1.02-.39 1.41 0l5.59 5.58 1.44-1.44c.31-.31.85-.09.85.35v4.29c0 .28-.22.5-.5.5H17.2c-.44.01-.66-.53-.35-.84" }), "TrendingDownRounded");
// src/pages/AbandonedCartsAdminPage.tsx
var { useState, useEffect } = await importShared("react");
function AbandonedCartsAdminPage() {
	const [carts, setCarts] = useState([]);
	const [funnel, setFunnel] = useState(null);
	const [loading, setLoading] = useState(true);
	const [selectedCart, setSelectedCart] = useState(null);
	const [discountCode, setDiscountCode] = useState("COMEBACK15");
	const [discountPercent, setDiscountPercent] = useState(15);
	const [sending, setSending] = useState(false);
	const [toastMsg, setToastMsg] = useState("");
	const fetchData = async () => {
		setLoading(true);
		try {
			const token = localStorage.getItem("jwt_token");
			const headers = token ? { Authorization: `Bearer ${token}` } : {};
			const [funnelRes, cartsRes] = await Promise.all([axios.get("/api/cart/admin/funnel", { headers }), axios.get("/api/cart/admin/abandoned?inactiveHours=1", { headers })]);
			setFunnel(funnelRes.data);
			setCarts(cartsRes.data);
		} catch (err) {
			console.warn("Failed to fetch abandoned cart analytics", err.message);
		} finally {
			setLoading(false);
		}
	};
	useEffect(() => {
		fetchData();
	}, []);
	const handleSendReminder = async () => {
		if (!selectedCart) return;
		setSending(true);
		try {
			const token = localStorage.getItem("jwt_token");
			const headers = token ? { Authorization: `Bearer ${token}` } : {};
			await axios.post(`/api/cart/admin/abandoned/${selectedCart.cartId}/remind`, {
				discountCode,
				discountPercent
			}, { headers });
			setToastMsg(`Successfully sent ${discountPercent}% recovery reminder to ${selectedCart.customerEmail}`);
			setSelectedCart(null);
			fetchData();
		} catch (err) {
			alert(`Error sending reminder: ${err.message}`);
		} finally {
			setSending(false);
		}
	};
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
		sx: { py: 3 },
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
				sx: {
					mb: 4,
					display: "flex",
					justifyContent: "space-between",
					alignItems: "center"
				},
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					variant: "h4",
					sx: {
						fontWeight: 900,
						color: "#F8FAFC",
						letterSpacing: "-0.02em"
					},
					children: "Abandoned Cart Recovery & Funnel"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					variant: "body2",
					sx: { color: "#94A3B8" },
					children: "Track abandoned customer sessions, potential lost revenue, and dispatch personalized win-back promos"
				})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
					variant: "outlined",
					startIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RefreshRounded_default, {}),
					onClick: fetchData,
					sx: {
						borderColor: "#334155",
						color: "#94A3B8",
						"&:hover": {
							borderColor: "#4F46E5",
							color: "#F8FAFC"
						}
					},
					children: "Refresh"
				})]
			}),
			toastMsg && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Alert, {
				severity: "success",
				onClose: () => setToastMsg(""),
				sx: {
					mb: 3,
					borderRadius: 2
				},
				children: toastMsg
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
				container: true,
				spacing: 3,
				sx: { mb: 4 },
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
						item: true,
						xs: 12,
						sm: 6,
						md: 3,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
							sx: {
								p: 3,
								bgcolor: "#111827",
								borderRadius: 3,
								border: "1px solid #1E293B"
							},
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								direction: "row",
								justifyContent: "space-between",
								alignItems: "center",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#94A3B8",
										fontWeight: 700
									},
									children: "ABANDONED CARTS"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "h4",
									sx: {
										fontWeight: 900,
										color: "#F87171",
										mt: .5
									},
									children: funnel?.abandonedCarts ?? 0
								})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Avatar, {
									sx: {
										bgcolor: "rgba(239, 68, 68, 0.1)",
										color: "#F87171",
										width: 48,
										height: 48
									},
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TrendingDownRounded_default, {})
								})]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
								variant: "caption",
								sx: {
									color: "#64748B",
									mt: 1,
									display: "block"
								},
								children: [
									"Abandonment rate: ",
									funnel?.abandonedRatePercent ?? 0,
									"%"
								]
							})]
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
						item: true,
						xs: 12,
						sm: 6,
						md: 3,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
							sx: {
								p: 3,
								bgcolor: "#111827",
								borderRadius: 3,
								border: "1px solid #1E293B"
							},
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								direction: "row",
								justifyContent: "space-between",
								alignItems: "center",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#94A3B8",
										fontWeight: 700
									},
									children: "REVENUE AT RISK"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
									variant: "h4",
									sx: {
										fontWeight: 900,
										color: "#FBBF24",
										mt: .5
									},
									children: ["$", (funnel?.abandonedCartValue ?? 0).toFixed(2)]
								})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Avatar, {
									sx: {
										bgcolor: "rgba(245, 158, 11, 0.1)",
										color: "#FBBF24",
										width: 48,
										height: 48
									},
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AttachMoneyRounded_default, {})
								})]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
								variant: "caption",
								sx: {
									color: "#64748B",
									mt: 1,
									display: "block"
								},
								children: ["Active in-flight carts: $", (funnel?.activeCartValue ?? 0).toFixed(2)]
							})]
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
						item: true,
						xs: 12,
						sm: 6,
						md: 3,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
							sx: {
								p: 3,
								bgcolor: "#111827",
								borderRadius: 3,
								border: "1px solid #1E293B"
							},
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								direction: "row",
								justifyContent: "space-between",
								alignItems: "center",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#94A3B8",
										fontWeight: 700
									},
									children: "CHECKOUT CONVERSIONS"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "h4",
									sx: {
										fontWeight: 900,
										color: "#34D399",
										mt: .5
									},
									children: funnel?.convertedCarts ?? 0
								})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Avatar, {
									sx: {
										bgcolor: "rgba(16, 185, 129, 0.1)",
										color: "#34D399",
										width: 48,
										height: 48
									},
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ShoppingCartCheckoutRounded_default, {})
								})]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
								variant: "caption",
								sx: {
									color: "#64748B",
									mt: 1,
									display: "block"
								},
								children: [
									"Conversion rate: ",
									funnel?.conversionRatePercent ?? 0,
									"%"
								]
							})]
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
						item: true,
						xs: 12,
						sm: 6,
						md: 3,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
							sx: {
								p: 3,
								bgcolor: "#111827",
								borderRadius: 3,
								border: "1px solid #1E293B"
							},
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								direction: "row",
								justifyContent: "space-between",
								alignItems: "center",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#94A3B8",
										fontWeight: 700
									},
									children: "RECOVERY EMAILS SENT"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "h4",
									sx: {
										fontWeight: 900,
										color: "#A78BFA",
										mt: .5
									},
									children: funnel?.remindersSent ?? 0
								})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Avatar, {
									sx: {
										bgcolor: "rgba(124, 58, 237, 0.1)",
										color: "#A78BFA",
										width: 48,
										height: 48
									},
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MarkEmailReadRounded_default, {})
								})]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "caption",
								sx: {
									color: "#64748B",
									mt: 1,
									display: "block"
								},
								children: "Automated Kafka recovery triggers"
							})]
						})
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
				sx: {
					bgcolor: "#111827",
					borderRadius: 3,
					border: "1px solid #1E293B",
					overflow: "hidden"
				},
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
					sx: {
						p: 2.5,
						borderBottom: "1px solid #1E293B"
					},
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
						variant: "h6",
						sx: {
							fontWeight: 800,
							color: "#F8FAFC"
						},
						children: "Untouched Inactive Carts"
					})
				}), loading ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
					sx: {
						display: "flex",
						justifyContent: "center",
						py: 8
					},
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(CircularProgress, { color: "primary" })
				}) : carts.length === 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
					sx: {
						p: 6,
						textAlign: "center"
					},
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
						variant: "body1",
						sx: { color: "#94A3B8" },
						children: "No abandoned carts found! All shoppers are actively converting."
					})
				}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableContainer, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Table, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableHead, {
					sx: { bgcolor: "#0B0F19" },
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableRow, { children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							sx: {
								color: "#94A3B8",
								fontWeight: 700
							},
							children: "CART ID"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							sx: {
								color: "#94A3B8",
								fontWeight: 700
							},
							children: "CUSTOMER / SESSION"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							sx: {
								color: "#94A3B8",
								fontWeight: 700
							},
							children: "ABANDONED ITEMS"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							sx: {
								color: "#94A3B8",
								fontWeight: 700
							},
							children: "TOTAL VALUE"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							sx: {
								color: "#94A3B8",
								fontWeight: 700
							},
							children: "INACTIVE TIME"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							sx: {
								color: "#94A3B8",
								fontWeight: 700
							},
							children: "RECOVERY ACTION"
						})
					] })
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableBody, { children: carts.map((cart) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableRow, {
					hover: true,
					sx: { "&:hover": { bgcolor: "#1E293B" } },
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableCell, {
							sx: {
								color: "#F8FAFC",
								fontWeight: 700
							},
							children: ["#", cart.cartId]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableCell, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
							variant: "body2",
							sx: {
								fontWeight: 700,
								color: "#F8FAFC"
							},
							children: cart.customerEmail
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
							variant: "caption",
							sx: { color: "#64748B" },
							children: [
								"Token: ",
								cart.sessionToken?.substring(0, 16),
								"..."
							]
						})] }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
							direction: "row",
							spacing: 1,
							alignItems: "center",
							children: [cart.items?.slice(0, 3).map((item, idx) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tooltip, {
								title: `${item.productName} (${item.quantity}x)`,
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Avatar, {
									src: item.imageUrl,
									variant: "rounded",
									sx: {
										width: 36,
										height: 36,
										bgcolor: "#1E293B"
									},
									children: item.productName[0]
								})
							}, idx)), cart.items && cart.items.length > 3 && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
								label: `+${cart.items.length - 3}`,
								size: "small",
								sx: {
									bgcolor: "#1E293B",
									color: "#94A3B8",
									fontSize: "0.7rem"
								}
							})]
						}) }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableCell, {
							sx: {
								color: "#06B6D4",
								fontWeight: 800
							},
							children: ["$", cart.totalAmount?.toFixed(2)]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
							icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AccessTimeRounded_default, { sx: { fontSize: "14px !important" } }),
							label: `${cart.inactiveHours} hrs ago`,
							size: "small",
							sx: {
								bgcolor: "rgba(239, 68, 68, 0.1)",
								color: "#F87171",
								fontWeight: 700
							}
						}) }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
							variant: "contained",
							size: "small",
							startIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SendRounded_default, {}),
							onClick: () => setSelectedCart(cart),
							sx: {
								bgcolor: "#4F46E5",
								fontWeight: 700,
								borderRadius: 2,
								textTransform: "none",
								fontSize: "0.8rem",
								"&:hover": { bgcolor: "#4338CA" }
							},
							children: "Send Promo"
						}) })
					]
				}, cart.cartId)) })] }) })]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Dialog, {
				open: Boolean(selectedCart),
				onClose: () => setSelectedCart(null),
				PaperProps: { sx: {
					bgcolor: "#111827",
					color: "#F8FAFC",
					borderRadius: 3,
					border: "1px solid #1E293B",
					minWidth: 420
				} },
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(DialogTitle, {
						sx: { fontWeight: 800 },
						children: "Dispatch Win-Back Recovery Campaign"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DialogContent, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
						variant: "body2",
						sx: {
							color: "#94A3B8",
							mb: 3
						},
						children: [
							"Send an automated notification with an exclusive discount code to entice",
							" ",
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", { children: selectedCart?.customerEmail }),
							" to finalize checkout on Cart #",
							selectedCart?.cartId,
							"."
						]
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
						spacing: 2.5,
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TextField, {
							label: "Promo Code",
							fullWidth: true,
							value: discountCode,
							onChange: (e) => setDiscountCode(e.target.value.toUpperCase()),
							sx: {
								"& .MuiInputLabel-root": { color: "#94A3B8" },
								"& .MuiOutlinedInput-root": {
									bgcolor: "#0B0F19",
									color: "#F8FAFC",
									"& fieldset": { borderColor: "#334155" }
								}
							}
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TextField, {
							label: "Discount Percentage (%)",
							type: "number",
							fullWidth: true,
							value: discountPercent,
							onChange: (e) => setDiscountPercent(Number(e.target.value)),
							sx: {
								"& .MuiInputLabel-root": { color: "#94A3B8" },
								"& .MuiOutlinedInput-root": {
									bgcolor: "#0B0F19",
									color: "#F8FAFC",
									"& fieldset": { borderColor: "#334155" }
								}
							}
						})]
					})] }),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(DialogActions, {
						sx: { p: 2.5 },
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
							onClick: () => setSelectedCart(null),
							sx: { color: "#94A3B8" },
							children: "Cancel"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
							variant: "contained",
							disabled: sending,
							onClick: handleSendReminder,
							startIcon: sending ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(CircularProgress, {
								size: 16,
								color: "inherit"
							}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SendRounded_default, {}),
							sx: {
								bgcolor: "#4F46E5",
								fontWeight: 700,
								"&:hover": { bgcolor: "#4338CA" }
							},
							children: sending ? "Sending..." : "Dispatch Reminder"
						})]
					})
				]
			})
		]
	});
}
export { MarkEmailReadRounded_default as n, AbandonedCartsAdminPage as t };
