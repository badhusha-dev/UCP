import { r as importShared } from "./_virtual___federation_fn_import-DYD12ulU.js";
import { $ as generateUtilityClasses, A as styled, D as memoTheme, E as useDefaultProps, G as useControlled, H as composeClasses, J as alpha, R as useRtl, T as createSvgIcon, _ as Paper, c as Chip, d as Alert, et as generateUtilityClass, f as IconButton, g as useSlot, h as ButtonBase, i as LinearProgress, k as capitalize_default, m as createSimplePaletteValueFilter, n as Stack, o as Grid, r as Tooltip, s as Box, st as require_jsx_runtime, t as axios, tt as clsx, u as Typography } from "./axios-2qcjEZNH.js";
import { a as InputLabel, i as Menu, n as Select, o as FormControl, r as MenuItem, t as TextField } from "./TextField-CFS9Y6-t.js";
import { a as TableContainer, c as Table, i as TableHead, n as OpenInNewRounded_default, o as TableCell, r as TableRow, s as TableBody, t as RefreshRounded_default } from "./RefreshRounded-DSckIcKG.js";
// ../node_modules/@mui/material/Pagination/paginationClasses.js
var import_jsx_runtime = require_jsx_runtime();
function getPaginationUtilityClass(slot) {
	return generateUtilityClass("MuiPagination", slot);
}
generateUtilityClasses("MuiPagination", [
	"root",
	"ul",
	"outlined",
	"text"
]);
// ../node_modules/@mui/material/usePagination/usePagination.js
function usePagination(props = {}) {
	const { boundaryCount = 1, componentName = "usePagination", count = 1, defaultPage = 1, disabled = false, hideNextButton = false, hidePrevButton = false, onChange: handleChange, page: pageProp, showFirstButton = false, showLastButton = false, siblingCount = 1, ...other } = props;
	const [page, setPageState] = useControlled({
		controlled: pageProp,
		default: defaultPage,
		name: componentName,
		state: "page"
	});
	const handleClick = (event, value) => {
		if (!pageProp) setPageState(value);
		if (handleChange) handleChange(event, value);
	};
	const range = (start, end) => {
		const length = end - start + 1;
		return Array.from({ length }, (_, i) => start + i);
	};
	const startPages = range(1, Math.min(boundaryCount, count));
	const endPages = range(Math.max(count - boundaryCount + 1, boundaryCount + 1), count);
	const siblingsStart = Math.max(Math.min(page - siblingCount, count - boundaryCount - siblingCount * 2 - 1), boundaryCount + 2);
	const siblingsEnd = Math.min(Math.max(page + siblingCount, boundaryCount + siblingCount * 2 + 2), count - boundaryCount - 1);
	const itemList = [
		...showFirstButton ? ["first"] : [],
		...hidePrevButton ? [] : ["previous"],
		...startPages,
		...siblingsStart > boundaryCount + 2 ? ["start-ellipsis"] : boundaryCount + 1 < count - boundaryCount ? [boundaryCount + 1] : [],
		...range(siblingsStart, siblingsEnd),
		...siblingsEnd < count - boundaryCount - 1 ? ["end-ellipsis"] : count - boundaryCount > boundaryCount ? [count - boundaryCount] : [],
		...endPages,
		...hideNextButton ? [] : ["next"],
		...showLastButton ? ["last"] : []
	];
	const buttonPage = (type) => {
		switch (type) {
			case "first": return 1;
			case "previous": return page - 1;
			case "next": return page + 1;
			case "last": return count;
			default: return null;
		}
	};
	return {
		items: itemList.map((item) => {
			return typeof item === "number" ? {
				onClick: (event) => {
					handleClick(event, item);
				},
				type: "page",
				page: item,
				selected: item === page,
				disabled,
				"aria-current": item === page ? "page" : void 0
			} : {
				onClick: (event) => {
					handleClick(event, buttonPage(item));
				},
				type: item,
				page: buttonPage(item),
				selected: false,
				disabled: disabled || !item.includes("ellipsis") && (item === "next" || item === "last" ? page >= count : page <= 1)
			};
		}),
		...other
	};
}
// ../node_modules/@mui/material/PaginationItem/paginationItemClasses.js
function getPaginationItemUtilityClass(slot) {
	return generateUtilityClass("MuiPaginationItem", slot);
}
var paginationItemClasses = generateUtilityClasses("MuiPaginationItem", [
	"root",
	"page",
	"sizeSmall",
	"sizeLarge",
	"text",
	"textPrimary",
	"textSecondary",
	"outlined",
	"outlinedPrimary",
	"outlinedSecondary",
	"rounded",
	"ellipsis",
	"firstLast",
	"previousNext",
	"focusVisible",
	"disabled",
	"selected",
	"icon",
	"colorPrimary",
	"colorSecondary"
]);
// ../node_modules/@mui/material/internal/svg-icons/FirstPage.js
/**
* @ignore - internal component.
*/
await importShared("react");
var FirstPage_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M18.41 16.59L13.82 12l4.59-4.59L17 6l-6 6 6 6zM6 6h2v12H6z" }), "FirstPage");
// ../node_modules/@mui/material/internal/svg-icons/LastPage.js
/**
* @ignore - internal component.
*/
await importShared("react");
var LastPage_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M5.59 7.41L10.18 12l-4.59 4.59L7 18l6-6-6-6zM16 6h2v12h-2z" }), "LastPage");
// ../node_modules/@mui/material/internal/svg-icons/NavigateBefore.js
/**
* @ignore - internal component.
*/
await importShared("react");
var NavigateBefore_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" }), "NavigateBefore");
// ../node_modules/@mui/material/internal/svg-icons/NavigateNext.js
/**
* @ignore - internal component.
*/
await importShared("react");
var NavigateNext_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" }), "NavigateNext");
// ../node_modules/@mui/material/PaginationItem/PaginationItem.js
var React$1 = await importShared("react");
var overridesResolver = (props, styles) => {
	const { ownerState } = props;
	return [
		styles.root,
		styles[ownerState.variant],
		styles[`size${capitalize_default(ownerState.size)}`],
		ownerState.variant === "text" && styles[`text${capitalize_default(ownerState.color)}`],
		ownerState.variant === "outlined" && styles[`outlined${capitalize_default(ownerState.color)}`],
		ownerState.shape === "rounded" && styles.rounded,
		ownerState.type === "page" && styles.page,
		(ownerState.type === "start-ellipsis" || ownerState.type === "end-ellipsis") && styles.ellipsis,
		(ownerState.type === "previous" || ownerState.type === "next") && styles.previousNext,
		(ownerState.type === "first" || ownerState.type === "last") && styles.firstLast
	];
};
var useUtilityClasses$1 = (ownerState) => {
	const { classes, color, disabled, selected, size, shape, type, variant } = ownerState;
	const slots = {
		root: [
			"root",
			`size${capitalize_default(size)}`,
			variant,
			shape,
			color !== "standard" && `color${capitalize_default(color)}`,
			color !== "standard" && `${variant}${capitalize_default(color)}`,
			disabled && "disabled",
			selected && "selected",
			{
				page: "page",
				first: "firstLast",
				last: "firstLast",
				"start-ellipsis": "ellipsis",
				"end-ellipsis": "ellipsis",
				previous: "previousNext",
				next: "previousNext"
			}[type]
		],
		icon: ["icon"]
	};
	return composeClasses(slots, getPaginationItemUtilityClass, classes);
};
var PaginationItemEllipsis = styled("div", {
	name: "MuiPaginationItem",
	slot: "Root",
	overridesResolver
})(memoTheme(({ theme }) => ({
	...theme.typography.body2,
	borderRadius: 16,
	textAlign: "center",
	boxSizing: "border-box",
	minWidth: 32,
	padding: "0 6px",
	margin: "0 3px",
	color: (theme.vars || theme).palette.text.primary,
	height: "auto",
	[`&.${paginationItemClasses.disabled}`]: { opacity: (theme.vars || theme).palette.action.disabledOpacity },
	variants: [{
		props: { size: "small" },
		style: {
			minWidth: 26,
			borderRadius: 13,
			margin: "0 1px",
			padding: "0 4px"
		}
	}, {
		props: { size: "large" },
		style: {
			minWidth: 40,
			borderRadius: 20,
			padding: "0 10px",
			fontSize: theme.typography.pxToRem(15)
		}
	}]
})));
var PaginationItemPage = styled(ButtonBase, {
	name: "MuiPaginationItem",
	slot: "Root",
	overridesResolver
})(memoTheme(({ theme }) => ({
	...theme.typography.body2,
	borderRadius: 16,
	textAlign: "center",
	boxSizing: "border-box",
	minWidth: 32,
	height: 32,
	padding: "0 6px",
	margin: "0 3px",
	color: (theme.vars || theme).palette.text.primary,
	[`&.${paginationItemClasses.focusVisible}`]: { backgroundColor: (theme.vars || theme).palette.action.focus },
	[`&.${paginationItemClasses.disabled}`]: { opacity: (theme.vars || theme).palette.action.disabledOpacity },
	transition: theme.transitions.create(["color", "background-color"], { duration: theme.transitions.duration.short }),
	"&:hover": {
		backgroundColor: (theme.vars || theme).palette.action.hover,
		"@media (hover: none)": { backgroundColor: "transparent" }
	},
	[`&.${paginationItemClasses.selected}`]: {
		backgroundColor: (theme.vars || theme).palette.action.selected,
		"&:hover": {
			backgroundColor: theme.vars ? `rgba(${theme.vars.palette.action.selectedChannel} / calc(${theme.vars.palette.action.selectedOpacity} + ${theme.vars.palette.action.hoverOpacity}))` : alpha(theme.palette.action.selected, theme.palette.action.selectedOpacity + theme.palette.action.hoverOpacity),
			"@media (hover: none)": { backgroundColor: (theme.vars || theme).palette.action.selected }
		},
		[`&.${paginationItemClasses.focusVisible}`]: { backgroundColor: theme.vars ? `rgba(${theme.vars.palette.action.selectedChannel} / calc(${theme.vars.palette.action.selectedOpacity} + ${theme.vars.palette.action.focusOpacity}))` : alpha(theme.palette.action.selected, theme.palette.action.selectedOpacity + theme.palette.action.focusOpacity) },
		[`&.${paginationItemClasses.disabled}`]: {
			opacity: 1,
			color: (theme.vars || theme).palette.action.disabled,
			backgroundColor: (theme.vars || theme).palette.action.selected
		}
	},
	variants: [
		{
			props: { size: "small" },
			style: {
				minWidth: 26,
				height: 26,
				borderRadius: 13,
				margin: "0 1px",
				padding: "0 4px"
			}
		},
		{
			props: { size: "large" },
			style: {
				minWidth: 40,
				height: 40,
				borderRadius: 20,
				padding: "0 10px",
				fontSize: theme.typography.pxToRem(15)
			}
		},
		{
			props: { shape: "rounded" },
			style: { borderRadius: (theme.vars || theme).shape.borderRadius }
		},
		{
			props: { variant: "outlined" },
			style: {
				border: theme.vars ? `1px solid rgba(${theme.vars.palette.common.onBackgroundChannel} / 0.23)` : `1px solid ${theme.palette.mode === "light" ? "rgba(0, 0, 0, 0.23)" : "rgba(255, 255, 255, 0.23)"}`,
				[`&.${paginationItemClasses.selected}`]: { [`&.${paginationItemClasses.disabled}`]: {
					borderColor: (theme.vars || theme).palette.action.disabledBackground,
					color: (theme.vars || theme).palette.action.disabled
				} }
			}
		},
		{
			props: { variant: "text" },
			style: { [`&.${paginationItemClasses.selected}`]: { [`&.${paginationItemClasses.disabled}`]: { color: (theme.vars || theme).palette.action.disabled } } }
		},
		...Object.entries(theme.palette).filter(createSimplePaletteValueFilter(["dark", "contrastText"])).map(([color]) => ({
			props: {
				variant: "text",
				color
			},
			style: { [`&.${paginationItemClasses.selected}`]: {
				color: (theme.vars || theme).palette[color].contrastText,
				backgroundColor: (theme.vars || theme).palette[color].main,
				"&:hover": {
					backgroundColor: (theme.vars || theme).palette[color].dark,
					"@media (hover: none)": { backgroundColor: (theme.vars || theme).palette[color].main }
				},
				[`&.${paginationItemClasses.focusVisible}`]: { backgroundColor: (theme.vars || theme).palette[color].dark },
				[`&.${paginationItemClasses.disabled}`]: { color: (theme.vars || theme).palette.action.disabled }
			} }
		})),
		...Object.entries(theme.palette).filter(createSimplePaletteValueFilter(["light"])).map(([color]) => ({
			props: {
				variant: "outlined",
				color
			},
			style: { [`&.${paginationItemClasses.selected}`]: {
				color: (theme.vars || theme).palette[color].main,
				border: `1px solid ${theme.vars ? `rgba(${theme.vars.palette[color].mainChannel} / 0.5)` : alpha(theme.palette[color].main, .5)}`,
				backgroundColor: theme.vars ? `rgba(${theme.vars.palette[color].mainChannel} / ${theme.vars.palette.action.activatedOpacity})` : alpha(theme.palette[color].main, theme.palette.action.activatedOpacity),
				"&:hover": {
					backgroundColor: theme.vars ? `rgba(${theme.vars.palette[color].mainChannel} / calc(${theme.vars.palette.action.activatedOpacity} + ${theme.vars.palette.action.focusOpacity}))` : alpha(theme.palette[color].main, theme.palette.action.activatedOpacity + theme.palette.action.focusOpacity),
					"@media (hover: none)": { backgroundColor: "transparent" }
				},
				[`&.${paginationItemClasses.focusVisible}`]: { backgroundColor: theme.vars ? `rgba(${theme.vars.palette[color].mainChannel} / calc(${theme.vars.palette.action.activatedOpacity} + ${theme.vars.palette.action.focusOpacity}))` : alpha(theme.palette[color].main, theme.palette.action.activatedOpacity + theme.palette.action.focusOpacity) }
			} }
		}))
	]
})));
var PaginationItemPageIcon = styled("div", {
	name: "MuiPaginationItem",
	slot: "Icon",
	overridesResolver: (props, styles) => styles.icon
})(memoTheme(({ theme }) => ({
	fontSize: theme.typography.pxToRem(20),
	margin: "0 -8px",
	variants: [{
		props: { size: "small" },
		style: { fontSize: theme.typography.pxToRem(18) }
	}, {
		props: { size: "large" },
		style: { fontSize: theme.typography.pxToRem(22) }
	}]
})));
var PaginationItem = /*#__PURE__*/ React$1.forwardRef(function PaginationItem(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiPaginationItem"
	});
	const { className, color = "standard", component, components = {}, disabled = false, page, selected = false, shape = "circular", size = "medium", slots = {}, slotProps = {}, type = "page", variant = "text", ...other } = props;
	const ownerState = {
		...props,
		color,
		disabled,
		selected,
		shape,
		size,
		type,
		variant
	};
	const isRtl = useRtl();
	const classes = useUtilityClasses$1(ownerState);
	const externalForwardedProps = {
		slots: {
			previous: slots.previous ?? components.previous,
			next: slots.next ?? components.next,
			first: slots.first ?? components.first,
			last: slots.last ?? components.last
		},
		slotProps
	};
	const [PreviousSlot, previousSlotProps] = useSlot("previous", {
		elementType: NavigateBefore_default,
		externalForwardedProps,
		ownerState
	});
	const [NextSlot, nextSlotProps] = useSlot("next", {
		elementType: NavigateNext_default,
		externalForwardedProps,
		ownerState
	});
	const [FirstSlot, firstSlotProps] = useSlot("first", {
		elementType: FirstPage_default,
		externalForwardedProps,
		ownerState
	});
	const [LastSlot, lastSlotProps] = useSlot("last", {
		elementType: LastPage_default,
		externalForwardedProps,
		ownerState
	});
	const rtlAwareType = isRtl ? {
		previous: "next",
		next: "previous",
		first: "last",
		last: "first"
	}[type] : type;
	const IconSlot = {
		previous: PreviousSlot,
		next: NextSlot,
		first: FirstSlot,
		last: LastSlot
	}[rtlAwareType];
	const iconSlotProps = {
		previous: previousSlotProps,
		next: nextSlotProps,
		first: firstSlotProps,
		last: lastSlotProps
	}[rtlAwareType];
	return type === "start-ellipsis" || type === "end-ellipsis" ? /*#__PURE__*/ (0, import_jsx_runtime.jsx)(PaginationItemEllipsis, {
		ref,
		ownerState,
		className: clsx(classes.root, className),
		children: "…"
	}) : /*#__PURE__*/ (0, import_jsx_runtime.jsxs)(PaginationItemPage, {
		ref,
		ownerState,
		component,
		disabled,
		className: clsx(classes.root, className),
		...other,
		children: [type === "page" && page, IconSlot ? /*#__PURE__*/ (0, import_jsx_runtime.jsx)(PaginationItemPageIcon, {
			...iconSlotProps,
			className: classes.icon,
			as: IconSlot
		}) : null]
	});
});
// ../node_modules/@mui/material/Pagination/Pagination.js
var React = await importShared("react");
var useUtilityClasses = (ownerState) => {
	const { classes, variant } = ownerState;
	return composeClasses({
		root: ["root", variant],
		ul: ["ul"]
	}, getPaginationUtilityClass, classes);
};
var PaginationRoot = styled("nav", {
	name: "MuiPagination",
	slot: "Root",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [styles.root, styles[ownerState.variant]];
	}
})({});
var PaginationUl = styled("ul", {
	name: "MuiPagination",
	slot: "Ul",
	overridesResolver: (props, styles) => styles.ul
})({
	display: "flex",
	flexWrap: "wrap",
	alignItems: "center",
	padding: 0,
	margin: 0,
	listStyle: "none"
});
function defaultGetAriaLabel(type, page, selected) {
	if (type === "page") return `${selected ? "" : "Go to "}page ${page}`;
	return `Go to ${type} page`;
}
var Pagination = /*#__PURE__*/ React.forwardRef(function Pagination(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiPagination"
	});
	const { boundaryCount = 1, className, color = "standard", count = 1, defaultPage = 1, disabled = false, getItemAriaLabel = defaultGetAriaLabel, hideNextButton = false, hidePrevButton = false, onChange, page, renderItem = (item) => /*#__PURE__*/ (0, import_jsx_runtime.jsx)(PaginationItem, { ...item }), shape = "circular", showFirstButton = false, showLastButton = false, siblingCount = 1, size = "medium", variant = "text", ...other } = props;
	const { items } = usePagination({
		...props,
		componentName: "Pagination"
	});
	const ownerState = {
		...props,
		boundaryCount,
		color,
		count,
		defaultPage,
		disabled,
		getItemAriaLabel,
		hideNextButton,
		hidePrevButton,
		renderItem,
		shape,
		showFirstButton,
		showLastButton,
		siblingCount,
		size,
		variant
	};
	const classes = useUtilityClasses(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(PaginationRoot, {
		"aria-label": "pagination navigation",
		className: clsx(classes.root, className),
		ownerState,
		ref,
		...other,
		children: /*#__PURE__*/ (0, import_jsx_runtime.jsx)(PaginationUl, {
			className: classes.ul,
			ownerState,
			children: items.map((item, index) => /*#__PURE__*/ (0, import_jsx_runtime.jsx)("li", { children: renderItem({
				...item,
				color,
				"aria-label": getItemAriaLabel(item.type, item.page, item.selected),
				shape,
				size,
				variant
			}) }, index))
		})
	});
});
// ../node_modules/@mui/icons-material/esm/MoreVertRounded.js
var MoreVertRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2m0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2m0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2" }), "MoreVertRounded");
// ../node_modules/@mui/icons-material/esm/SearchRounded.js
var SearchRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M15.5 14h-.79l-.28-.27c1.2-1.4 1.82-3.31 1.48-5.34-.47-2.78-2.79-5-5.59-5.34-4.23-.52-7.79 3.04-7.27 7.27.34 2.8 2.56 5.12 5.34 5.59 2.03.34 3.94-.28 5.34-1.48l.27.28v.79l4.25 4.25c.41.41 1.08.41 1.49 0s.41-1.08 0-1.49zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14" }), "SearchRounded");
// src/pages/ErrorGroupsPage.tsx
var { useEffect, useState, useCallback } = await importShared("react");
var API_BASE = typeof window !== "undefined" ? `http://${window.location.hostname || "localhost"}:8080` : "http://localhost:8080";
function ErrorGroupsPage({ onSelectGroup }) {
	const [groups, setGroups] = useState([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState(null);
	const [search, setSearch] = useState("");
	const [service, setService] = useState("");
	const [severity, setSeverity] = useState("");
	const [status, setStatus] = useState("");
	const [timeRange, setTimeRange] = useState("LAST_7_DAYS");
	const [page, setPage] = useState(1);
	const [totalPages, setTotalPages] = useState(1);
	const [totalElements, setTotalElements] = useState(0);
	const [anchorEl, setAnchorEl] = useState(null);
	const [selectedFingerprint, setSelectedFingerprint] = useState(null);
	const fetchGroups = useCallback(async () => {
		setLoading(true);
		setError(null);
		try {
			const params = {
				page: page - 1,
				size: 15,
				timeRange,
				sortBy: "lastSeen",
				sortDir: "DESC"
			};
			if (search.trim()) params.search = search.trim();
			if (service) params.service = service;
			if (severity) params.severity = severity;
			if (status) params.status = status;
			const res = await axios.get(`${API_BASE}/api/errors/admin/groups`, { params });
			setGroups(res.data.content || []);
			setTotalPages(res.data.totalPages || 1);
			setTotalElements(res.data.totalElements || 0);
		} catch (err) {
			setError(err?.response?.data?.message || "Failed to query error groups");
		} finally {
			setLoading(false);
		}
	}, [
		page,
		search,
		service,
		severity,
		status,
		timeRange
	]);
	useEffect(() => {
		fetchGroups();
	}, [fetchGroups]);
	const handleTriage = async (newStatus) => {
		if (!selectedFingerprint) return;
		try {
			await axios.patch(`${API_BASE}/api/errors/admin/groups/${selectedFingerprint}/triage`, { status: newStatus });
			setAnchorEl(null);
			fetchGroups();
		} catch (err) {
			setError(err?.response?.data?.message || "Failed to update error triage status");
		}
	};
	const getSeverityColor = (sev) => {
		switch (sev) {
			case "CRITICAL": return "#EF4444";
			case "ERROR": return "#F59E0B";
			case "WARNING": return "#EAB308";
			default: return "#3B82F6";
		}
	};
	const getStatusColor = (st) => {
		switch (st) {
			case "NEW": return {
				bg: "#FEE2E2",
				text: "#DC2626"
			};
			case "INVESTIGATING": return {
				bg: "#FEF3C7",
				text: "#D97706"
			};
			case "RESOLVED": return {
				bg: "#DCFCE7",
				text: "#16A34A"
			};
			case "IGNORED": return {
				bg: "#F1F5F9",
				text: "#64748B"
			};
			default: return {
				bg: "#E2E8F0",
				text: "#475569"
			};
		}
	};
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
		sx: {
			p: 3,
			maxWidth: 1400,
			mx: "auto"
		},
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
				direction: "row",
				justifyContent: "space-between",
				alignItems: "center",
				sx: { mb: 3 },
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					variant: "h5",
					sx: {
						fontWeight: 800,
						color: "#F8FAFC"
					},
					children: "Error Groups & Triage Registry"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
					variant: "body2",
					sx: { color: "#94A3B8" },
					children: [totalElements, " clustered error fingerprints detected"]
				})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tooltip, {
					title: "Refresh list",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
						onClick: fetchGroups,
						sx: {
							bgcolor: "rgba(255,255,255,0.05)",
							color: "#94A3B8"
						},
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RefreshRounded_default, {})
					})
				})]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Paper, {
				sx: {
					p: 2.5,
					mb: 3,
					bgcolor: "#111827",
					border: "1px solid #1F2937",
					borderRadius: 3
				},
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
					container: true,
					spacing: 2,
					alignItems: "center",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 12,
							md: 3.5,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TextField, {
								fullWidth: true,
								size: "small",
								placeholder: "Search error message, type, service...",
								value: search,
								onChange: (e) => {
									setSearch(e.target.value);
									setPage(1);
								},
								InputProps: { startAdornment: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SearchRounded_default, {
									sx: {
										color: "#64748B",
										mr: 1
									},
									fontSize: "small"
								}) },
								sx: { "& .MuiOutlinedInput-root": {
									bgcolor: "#0B0F19",
									color: "#F8FAFC",
									"& fieldset": { borderColor: "#1F2937" }
								} }
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 6,
							sm: 3,
							md: 2,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(FormControl, {
								fullWidth: true,
								size: "small",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(InputLabel, {
									sx: { color: "#94A3B8" },
									children: "Service"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Select, {
									value: service,
									label: "Service",
									onChange: (e) => {
										setService(e.target.value);
										setPage(1);
									},
									sx: {
										bgcolor: "#0B0F19",
										color: "#F8FAFC",
										"& fieldset": { borderColor: "#1F2937" }
									},
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "",
											children: "All Services"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "order-service",
											children: "order-service"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "user-service",
											children: "user-service"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "product-service",
											children: "product-service"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "cart-service",
											children: "cart-service"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "inventory-service",
											children: "inventory-service"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "loyalty-service",
											children: "loyalty-service"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "accounting-service",
											children: "accounting-service"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "analytics-service",
											children: "analytics-service"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "shell-app",
											children: "shell-app (frontend)"
										})
									]
								})]
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 6,
							sm: 3,
							md: 2,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(FormControl, {
								fullWidth: true,
								size: "small",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(InputLabel, {
									sx: { color: "#94A3B8" },
									children: "Severity"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Select, {
									value: severity,
									label: "Severity",
									onChange: (e) => {
										setSeverity(e.target.value);
										setPage(1);
									},
									sx: {
										bgcolor: "#0B0F19",
										color: "#F8FAFC",
										"& fieldset": { borderColor: "#1F2937" }
									},
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "",
											children: "All Severities"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "CRITICAL",
											children: "Critical"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "ERROR",
											children: "Error"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "WARNING",
											children: "Warning"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "INFO",
											children: "Info"
										})
									]
								})]
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 6,
							sm: 3,
							md: 2,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(FormControl, {
								fullWidth: true,
								size: "small",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(InputLabel, {
									sx: { color: "#94A3B8" },
									children: "Status"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Select, {
									value: status,
									label: "Status",
									onChange: (e) => {
										setStatus(e.target.value);
										setPage(1);
									},
									sx: {
										bgcolor: "#0B0F19",
										color: "#F8FAFC",
										"& fieldset": { borderColor: "#1F2937" }
									},
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "",
											children: "All Statuses"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "NEW",
											children: "New"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "INVESTIGATING",
											children: "Investigating"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "RESOLVED",
											children: "Resolved"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "IGNORED",
											children: "Ignored"
										})
									]
								})]
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 6,
							sm: 3,
							md: 2.5,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(FormControl, {
								fullWidth: true,
								size: "small",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(InputLabel, {
									sx: { color: "#94A3B8" },
									children: "Time Window"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Select, {
									value: timeRange,
									label: "Time Window",
									onChange: (e) => {
										setTimeRange(e.target.value);
										setPage(1);
									},
									sx: {
										bgcolor: "#0B0F19",
										color: "#F8FAFC",
										"& fieldset": { borderColor: "#1F2937" }
									},
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "LAST_1_HOUR",
											children: "Last 1 Hour"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "LAST_24_HOURS",
											children: "Last 24 Hours"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "LAST_7_DAYS",
											children: "Last 7 Days"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
											value: "LAST_30_DAYS",
											children: "Last 30 Days"
										})
									]
								})]
							})
						})
					]
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
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
				sx: {
					bgcolor: "#111827",
					border: "1px solid #1F2937",
					borderRadius: 3,
					overflow: "hidden"
				},
				children: [groups.length === 0 && !loading ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
					sx: {
						p: 6,
						textAlign: "center",
						color: "#64748B"
					},
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
						variant: "body1",
						children: "No error groups match the specified filters."
					})
				}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableContainer, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Table, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableHead, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableRow, {
					sx: { "& th": {
						color: "#64748B",
						fontWeight: 700,
						borderColor: "#1F2937"
					} },
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Error Signature" }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Service & Env" }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Severity" }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Status" }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Assignee" }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							align: "right",
							children: "Occurrences"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							align: "right",
							children: "Last Active"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
							align: "center",
							children: "Actions"
						})
					]
				}) }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableBody, { children: groups.map((g) => {
					const stCol = getStatusColor(g.status);
					return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableRow, {
						hover: true,
						sx: { "& td": {
							borderColor: "#1F2937",
							color: "#E2E8F0"
						} },
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableCell, {
								sx: { maxWidth: 420 },
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "subtitle2",
										sx: {
											fontWeight: 800,
											color: "#F8FAFC"
										},
										children: g.errorType
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "body2",
										sx: {
											color: "#94A3B8",
											textOverflow: "ellipsis",
											overflow: "hidden",
											whiteSpace: "nowrap"
										},
										children: g.message
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "caption",
										sx: {
											color: "#64748B",
											fontFamily: "monospace"
										},
										children: ["#", g.fingerprint.substring(0, 12)]
									})
								]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								spacing: .5,
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
									size: "small",
									label: g.serviceName,
									sx: {
										bgcolor: "rgba(79,70,229,0.15)",
										color: "#818CF8",
										fontWeight: 600,
										width: "fit-content"
									}
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: { color: "#64748B" },
									children: g.environment
								})]
							}) }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
								size: "small",
								label: g.severity,
								sx: {
									border: `1px solid ${getSeverityColor(g.severity)}`,
									color: getSeverityColor(g.severity),
									fontWeight: 700,
									bgcolor: "transparent"
								}
							}) }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
								size: "small",
								label: g.status,
								sx: {
									bgcolor: stCol.bg,
									color: stCol.text,
									fontWeight: 700
								}
							}) }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "caption",
								sx: { color: g.assignedTo ? "#E2E8F0" : "#64748B" },
								children: g.assignedTo || "Unassigned"
							}) }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
								align: "right",
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
									size: "small",
									label: g.occurrencesCount.toLocaleString(),
									sx: {
										bgcolor: "rgba(255,255,255,0.08)",
										fontWeight: 800
									}
								})
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
								align: "right",
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: { color: "#94A3B8" },
									children: new Date(g.lastSeen).toLocaleString()
								})
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
								align: "center",
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
									direction: "row",
									spacing: .5,
									justifyContent: "center",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tooltip, {
										title: "View Occurrence Details",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
											size: "small",
											onClick: () => onSelectGroup?.(g.fingerprint),
											sx: { color: "#818CF8" },
											children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(OpenInNewRounded_default, { fontSize: "small" })
										})
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tooltip, {
										title: "Triage Status",
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
											size: "small",
											onClick: (e) => {
												setAnchorEl(e.currentTarget);
												setSelectedFingerprint(g.fingerprint);
											},
											sx: { color: "#94A3B8" },
											children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MoreVertRounded_default, { fontSize: "small" })
										})
									})]
								})
							})
						]
					}, g.fingerprint);
				}) })] }) }), totalPages > 1 && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
					sx: {
						p: 2,
						display: "flex",
						justifyContent: "center",
						borderTop: "1px solid #1F2937"
					},
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Pagination, {
						count: totalPages,
						page,
						onChange: (_, p) => setPage(p),
						sx: {
							"& .MuiPaginationItem-root": { color: "#94A3B8" },
							"& .Mui-selected": {
								bgcolor: "#4F46E5 !important",
								color: "#fff"
							}
						}
					})
				})]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Menu, {
				anchorEl,
				open: Boolean(anchorEl),
				onClose: () => setAnchorEl(null),
				PaperProps: { sx: {
					bgcolor: "#1F2937",
					color: "#F8FAFC",
					border: "1px solid #374151"
				} },
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
						onClick: () => handleTriage("INVESTIGATING"),
						children: "Mark as INVESTIGATING"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
						onClick: () => handleTriage("RESOLVED"),
						children: "Mark as RESOLVED"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
						onClick: () => handleTriage("IGNORED"),
						children: "Mark as IGNORED"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(MenuItem, {
						onClick: () => handleTriage("NEW"),
						children: "Reopen as NEW"
					})
				]
			})
		]
	});
}
export { ErrorGroupsPage as t };
