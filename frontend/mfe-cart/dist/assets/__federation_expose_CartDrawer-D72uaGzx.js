import { r as importShared } from "./_virtual___federation_fn_import-7k9UwJ4Q.js";
import { A as rootShouldForwardProp, C as debounce_default, E as memoTheme, H as composeClasses, L as useRtl, M as useTheme, O as capitalize_default, R as getReactElementRef, S as ownerWindow_default, T as useDefaultProps, _ as Transition, at as generateUtilityClasses, c as Typography, g as reflow, h as getTransitionProps, i as Box, k as styled, l as IconButton, m as Paper, n as Modal, ot as generateUtilityClass, p as useSlot, r as Button, st as clsx, t as Stack, v as mergeSlotProps, vt as require_jsx_runtime, w as createSvgIcon, y as useForkRef_default } from "./Stack-CWAApUdV.js";
import { a as AddRounded_default, d as Divider, i as ArrowForwardRounded_default, l as removeFromCart, n as RemoveRounded_default, r as DeleteOutlineRounded_default, t as ShoppingCartOutlined_default, u as updateQuantity } from "./ShoppingCartOutlined-BvL5uOlN.js";
// ../node_modules/@mui/material/Slide/Slide.js
var import_jsx_runtime = require_jsx_runtime();
var React$1 = await importShared("react");
function getTranslateValue(direction, node, resolvedContainer) {
	const rect = node.getBoundingClientRect();
	const containerRect = resolvedContainer && resolvedContainer.getBoundingClientRect();
	const containerWindow = ownerWindow_default(node);
	let transform;
	if (node.fakeTransform) transform = node.fakeTransform;
	else {
		const computedStyle = containerWindow.getComputedStyle(node);
		transform = computedStyle.getPropertyValue("-webkit-transform") || computedStyle.getPropertyValue("transform");
	}
	let offsetX = 0;
	let offsetY = 0;
	if (transform && transform !== "none" && typeof transform === "string") {
		const transformValues = transform.split("(")[1].split(")")[0].split(",");
		offsetX = parseInt(transformValues[4], 10);
		offsetY = parseInt(transformValues[5], 10);
	}
	if (direction === "left") {
		if (containerRect) return `translateX(${containerRect.right + offsetX - rect.left}px)`;
		return `translateX(${containerWindow.innerWidth + offsetX - rect.left}px)`;
	}
	if (direction === "right") {
		if (containerRect) return `translateX(-${rect.right - containerRect.left - offsetX}px)`;
		return `translateX(-${rect.left + rect.width - offsetX}px)`;
	}
	if (direction === "up") {
		if (containerRect) return `translateY(${containerRect.bottom + offsetY - rect.top}px)`;
		return `translateY(${containerWindow.innerHeight + offsetY - rect.top}px)`;
	}
	if (containerRect) return `translateY(-${rect.top - containerRect.top + rect.height - offsetY}px)`;
	return `translateY(-${rect.top + rect.height - offsetY}px)`;
}
function resolveContainer(containerPropProp) {
	return typeof containerPropProp === "function" ? containerPropProp() : containerPropProp;
}
function setTranslateValue(direction, node, containerProp) {
	const transform = getTranslateValue(direction, node, resolveContainer(containerProp));
	if (transform) {
		node.style.webkitTransform = transform;
		node.style.transform = transform;
	}
}
/**
* The Slide transition is used by the [Drawer](/material-ui/react-drawer/) component.
* It uses [react-transition-group](https://github.com/reactjs/react-transition-group) internally.
*/
var Slide = /*#__PURE__*/ React$1.forwardRef(function Slide(props, ref) {
	const theme = useTheme();
	const defaultEasing = {
		enter: theme.transitions.easing.easeOut,
		exit: theme.transitions.easing.sharp
	};
	const defaultTimeout = {
		enter: theme.transitions.duration.enteringScreen,
		exit: theme.transitions.duration.leavingScreen
	};
	const { addEndListener, appear = true, children, container: containerProp, direction = "down", easing: easingProp = defaultEasing, in: inProp, onEnter, onEntered, onEntering, onExit, onExited, onExiting, style, timeout = defaultTimeout, TransitionComponent = Transition, ...other } = props;
	const childrenRef = React$1.useRef(null);
	const handleRef = useForkRef_default(getReactElementRef(children), childrenRef, ref);
	const normalizedTransitionCallback = (callback) => (isAppearing) => {
		if (callback) {
			if (isAppearing === void 0) callback(childrenRef.current);
			else callback(childrenRef.current, isAppearing);
		}
	};
	const handleEnter = normalizedTransitionCallback((node, isAppearing) => {
		setTranslateValue(direction, node, containerProp);
		reflow(node);
		if (onEnter) onEnter(node, isAppearing);
	});
	const handleEntering = normalizedTransitionCallback((node, isAppearing) => {
		const transitionProps = getTransitionProps({
			timeout,
			style,
			easing: easingProp
		}, { mode: "enter" });
		node.style.webkitTransition = theme.transitions.create("-webkit-transform", { ...transitionProps });
		node.style.transition = theme.transitions.create("transform", { ...transitionProps });
		node.style.webkitTransform = "none";
		node.style.transform = "none";
		if (onEntering) onEntering(node, isAppearing);
	});
	const handleEntered = normalizedTransitionCallback(onEntered);
	const handleExiting = normalizedTransitionCallback(onExiting);
	const handleExit = normalizedTransitionCallback((node) => {
		const transitionProps = getTransitionProps({
			timeout,
			style,
			easing: easingProp
		}, { mode: "exit" });
		node.style.webkitTransition = theme.transitions.create("-webkit-transform", transitionProps);
		node.style.transition = theme.transitions.create("transform", transitionProps);
		setTranslateValue(direction, node, containerProp);
		if (onExit) onExit(node);
	});
	const handleExited = normalizedTransitionCallback((node) => {
		node.style.webkitTransition = "";
		node.style.transition = "";
		if (onExited) onExited(node);
	});
	const handleAddEndListener = (next) => {
		if (addEndListener) addEndListener(childrenRef.current, next);
	};
	const updatePosition = React$1.useCallback(() => {
		if (childrenRef.current) setTranslateValue(direction, childrenRef.current, containerProp);
	}, [direction, containerProp]);
	React$1.useEffect(() => {
		if (inProp || direction === "down" || direction === "right") return;
		const handleResize = debounce_default(() => {
			if (childrenRef.current) setTranslateValue(direction, childrenRef.current, containerProp);
		});
		const containerWindow = ownerWindow_default(childrenRef.current);
		containerWindow.addEventListener("resize", handleResize);
		return () => {
			handleResize.clear();
			containerWindow.removeEventListener("resize", handleResize);
		};
	}, [
		direction,
		inProp,
		containerProp
	]);
	React$1.useEffect(() => {
		if (!inProp) updatePosition();
	}, [inProp, updatePosition]);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TransitionComponent, {
		nodeRef: childrenRef,
		onEnter: handleEnter,
		onEntered: handleEntered,
		onEntering: handleEntering,
		onExit: handleExit,
		onExited: handleExited,
		onExiting: handleExiting,
		addEndListener: handleAddEndListener,
		appear,
		in: inProp,
		timeout,
		...other,
		children: (state, { ownerState, ...restChildProps }) => {
			return /*#__PURE__*/ React$1.cloneElement(children, {
				ref: handleRef,
				style: {
					visibility: state === "exited" && !inProp ? "hidden" : void 0,
					...style,
					...children.props.style
				},
				...restChildProps
			});
		}
	});
});
// ../node_modules/@mui/material/Drawer/drawerClasses.js
function getDrawerUtilityClass(slot) {
	return generateUtilityClass("MuiDrawer", slot);
}
generateUtilityClasses("MuiDrawer", [
	"root",
	"docked",
	"paper",
	"anchorLeft",
	"anchorRight",
	"anchorTop",
	"anchorBottom",
	"paperAnchorLeft",
	"paperAnchorRight",
	"paperAnchorTop",
	"paperAnchorBottom",
	"paperAnchorDockedLeft",
	"paperAnchorDockedRight",
	"paperAnchorDockedTop",
	"paperAnchorDockedBottom",
	"modal"
]);
// ../node_modules/@mui/material/Drawer/Drawer.js
var React = await importShared("react");
var overridesResolver = (props, styles) => {
	const { ownerState } = props;
	return [
		styles.root,
		(ownerState.variant === "permanent" || ownerState.variant === "persistent") && styles.docked,
		styles.modal
	];
};
var useUtilityClasses = (ownerState) => {
	const { classes, anchor, variant } = ownerState;
	const slots = {
		root: ["root", `anchor${capitalize_default(anchor)}`],
		docked: [(variant === "permanent" || variant === "persistent") && "docked"],
		modal: ["modal"],
		paper: [
			"paper",
			`paperAnchor${capitalize_default(anchor)}`,
			variant !== "temporary" && `paperAnchorDocked${capitalize_default(anchor)}`
		]
	};
	return composeClasses(slots, getDrawerUtilityClass, classes);
};
var DrawerRoot = styled(Modal, {
	name: "MuiDrawer",
	slot: "Root",
	overridesResolver
})(memoTheme(({ theme }) => ({ zIndex: (theme.vars || theme).zIndex.drawer })));
var DrawerDockedRoot = styled("div", {
	shouldForwardProp: rootShouldForwardProp,
	name: "MuiDrawer",
	slot: "Docked",
	skipVariantsResolver: false,
	overridesResolver
})({ flex: "0 0 auto" });
var DrawerPaper = styled(Paper, {
	name: "MuiDrawer",
	slot: "Paper",
	overridesResolver: (props, styles) => {
		const { ownerState } = props;
		return [
			styles.paper,
			styles[`paperAnchor${capitalize_default(ownerState.anchor)}`],
			ownerState.variant !== "temporary" && styles[`paperAnchorDocked${capitalize_default(ownerState.anchor)}`]
		];
	}
})(memoTheme(({ theme }) => ({
	overflowY: "auto",
	display: "flex",
	flexDirection: "column",
	height: "100%",
	flex: "1 0 auto",
	zIndex: (theme.vars || theme).zIndex.drawer,
	WebkitOverflowScrolling: "touch",
	position: "fixed",
	top: 0,
	outline: 0,
	variants: [
		{
			props: { anchor: "left" },
			style: { left: 0 }
		},
		{
			props: { anchor: "top" },
			style: {
				top: 0,
				left: 0,
				right: 0,
				height: "auto",
				maxHeight: "100%"
			}
		},
		{
			props: { anchor: "right" },
			style: { right: 0 }
		},
		{
			props: { anchor: "bottom" },
			style: {
				top: "auto",
				left: 0,
				bottom: 0,
				right: 0,
				height: "auto",
				maxHeight: "100%"
			}
		},
		{
			props: ({ ownerState }) => ownerState.anchor === "left" && ownerState.variant !== "temporary",
			style: { borderRight: `1px solid ${(theme.vars || theme).palette.divider}` }
		},
		{
			props: ({ ownerState }) => ownerState.anchor === "top" && ownerState.variant !== "temporary",
			style: { borderBottom: `1px solid ${(theme.vars || theme).palette.divider}` }
		},
		{
			props: ({ ownerState }) => ownerState.anchor === "right" && ownerState.variant !== "temporary",
			style: { borderLeft: `1px solid ${(theme.vars || theme).palette.divider}` }
		},
		{
			props: ({ ownerState }) => ownerState.anchor === "bottom" && ownerState.variant !== "temporary",
			style: { borderTop: `1px solid ${(theme.vars || theme).palette.divider}` }
		}
	]
})));
var oppositeDirection = {
	left: "right",
	right: "left",
	top: "down",
	bottom: "up"
};
function isHorizontal(anchor) {
	return ["left", "right"].includes(anchor);
}
function getAnchor({ direction }, anchor) {
	return direction === "rtl" && isHorizontal(anchor) ? oppositeDirection[anchor] : anchor;
}
/**
* The props of the [Modal](/material-ui/api/modal/) component are available
* when `variant="temporary"` is set.
*/
var Drawer = /*#__PURE__*/ React.forwardRef(function Drawer(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiDrawer"
	});
	const theme = useTheme();
	const isRtl = useRtl();
	const defaultTransitionDuration = {
		enter: theme.transitions.duration.enteringScreen,
		exit: theme.transitions.duration.leavingScreen
	};
	const { anchor: anchorProp = "left", BackdropProps, children, className, elevation = 16, hideBackdrop = false, ModalProps: { BackdropProps: BackdropPropsProp, ...ModalProps } = {}, onClose, open = false, PaperProps = {}, SlideProps, TransitionComponent, transitionDuration = defaultTransitionDuration, variant = "temporary", slots = {}, slotProps = {}, ...other } = props;
	const mounted = React.useRef(false);
	React.useEffect(() => {
		mounted.current = true;
	}, []);
	const anchorInvariant = getAnchor({ direction: isRtl ? "rtl" : "ltr" }, anchorProp);
	const anchor = anchorProp;
	const ownerState = {
		...props,
		anchor,
		elevation,
		open,
		variant,
		...other
	};
	const classes = useUtilityClasses(ownerState);
	const externalForwardedProps = {
		slots: {
			transition: TransitionComponent,
			...slots
		},
		slotProps: {
			paper: PaperProps,
			transition: SlideProps,
			...slotProps,
			backdrop: mergeSlotProps(slotProps.backdrop || {
				...BackdropProps,
				...BackdropPropsProp
			}, { transitionDuration })
		}
	};
	const [RootSlot, rootSlotProps] = useSlot("root", {
		ref,
		elementType: DrawerRoot,
		className: clsx(classes.root, classes.modal, className),
		shouldForwardComponentProp: true,
		ownerState,
		externalForwardedProps: {
			...externalForwardedProps,
			...other,
			...ModalProps
		},
		additionalProps: {
			open,
			onClose,
			hideBackdrop,
			slots: { backdrop: externalForwardedProps.slots.backdrop },
			slotProps: { backdrop: externalForwardedProps.slotProps.backdrop }
		}
	});
	const [PaperSlot, paperSlotProps] = useSlot("paper", {
		elementType: DrawerPaper,
		shouldForwardComponentProp: true,
		className: clsx(classes.paper, PaperProps.className),
		ownerState,
		externalForwardedProps,
		additionalProps: {
			elevation: variant === "temporary" ? elevation : 0,
			square: true
		}
	});
	const [DockedSlot, dockedSlotProps] = useSlot("docked", {
		elementType: DrawerDockedRoot,
		ref,
		className: clsx(classes.root, classes.docked, className),
		ownerState,
		externalForwardedProps,
		additionalProps: other
	});
	const [TransitionSlot, transitionSlotProps] = useSlot("transition", {
		elementType: Slide,
		ownerState,
		externalForwardedProps,
		additionalProps: {
			in: open,
			direction: oppositeDirection[anchorInvariant],
			timeout: transitionDuration,
			appear: mounted.current
		}
	});
	const drawer = /*#__PURE__*/ (0, import_jsx_runtime.jsx)(PaperSlot, {
		...paperSlotProps,
		children
	});
	if (variant === "permanent") return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(DockedSlot, {
		...dockedSlotProps,
		children: drawer
	});
	const slidingDrawer = /*#__PURE__*/ (0, import_jsx_runtime.jsx)(TransitionSlot, {
		...transitionSlotProps,
		children: drawer
	});
	if (variant === "persistent") return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(DockedSlot, {
		...dockedSlotProps,
		children: slidingDrawer
	});
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(RootSlot, {
		...rootSlotProps,
		children: slidingDrawer
	});
});
// ../node_modules/@mui/icons-material/esm/CloseRounded.js
var CloseRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M18.3 5.71a.996.996 0 0 0-1.41 0L12 10.59 7.11 5.7a.996.996 0 0 0-1.41 0c-.39.39-.39 1.02 0 1.41L10.59 12 5.7 16.89c-.39.39-.39 1.02 0 1.41s1.02.39 1.41 0L12 13.41l4.89 4.89c.39.39 1.02.39 1.41 0s.39-1.02 0-1.41L13.41 12l4.89-4.89c.38-.38.38-1.02 0-1.4" }), "CloseRounded");
// src/components/CartDrawer.tsx
var { useEffect, useState } = await importShared("react");
var { useSelector, useDispatch } = await importShared("react-redux");
var { useNavigate } = await importShared("react-router-dom");
function CartDrawer({ open, onClose }) {
	const dispatch = useDispatch();
	const navigate = useNavigate();
	const cart = useSelector((state) => state.cart);
	const subtotal = cart.totalAmount || 0;
	const totalItems = cart.items?.reduce((sum, item) => sum + item.quantity, 0) || 0;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Drawer, {
		anchor: "right",
		open,
		onClose,
		PaperProps: { sx: {
			width: {
				xs: "100%",
				sm: 400
			},
			bgcolor: "#0F172A",
			color: "#F8FAFC",
			borderLeft: "1px solid #1E293B",
			p: 3,
			display: "flex",
			flexDirection: "column"
		} },
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
				sx: {
					display: "flex",
					justifyContent: "space-between",
					alignItems: "center",
					mb: 2
				},
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
					direction: "row",
					spacing: 1,
					alignItems: "center",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ShoppingCartOutlined_default, { sx: { color: "#06B6D4" } }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
						variant: "h6",
						sx: { fontWeight: 800 },
						children: [
							"Your Basket (",
							totalItems,
							")"
						]
					})]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
					onClick: onClose,
					sx: { color: "#94A3B8" },
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(CloseRounded_default, {})
				})]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Divider, { sx: {
				borderColor: "#1E293B",
				mb: 2
			} }),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
				sx: {
					flexGrow: 1,
					overflowY: "auto",
					pr: .5
				},
				children: !cart.items || cart.items.length === 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
					sx: {
						py: 8,
						textAlign: "center"
					},
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
						variant: "body1",
						sx: { color: "#94A3B8" },
						children: "Your basket is currently empty."
					})
				}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stack, {
					spacing: 2,
					children: cart.items.map((item) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
						sx: {
							p: 1.5,
							bgcolor: "#111827",
							borderRadius: 2,
							border: "1px solid #1E293B"
						},
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
							sx: {
								display: "flex",
								justifyContent: "space-between",
								alignItems: "flex-start",
								mb: 1
							},
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "subtitle2",
								sx: {
									fontWeight: 700,
									color: "#F8FAFC"
								},
								children: item.name
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
								size: "small",
								onClick: () => dispatch(removeFromCart(item.productId)),
								sx: {
									color: "#64748B",
									p: .25
								},
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DeleteOutlineRounded_default, { sx: { fontSize: 16 } })
							})]
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
							sx: {
								display: "flex",
								justifyContent: "space-between",
								alignItems: "center"
							},
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
								variant: "body2",
								sx: {
									color: "#06B6D4",
									fontWeight: 700
								},
								children: ["$", (item.price * item.quantity).toFixed(2)]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
								direction: "row",
								spacing: .5,
								alignItems: "center",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
										size: "small",
										onClick: () => dispatch(updateQuantity({
											productId: item.productId,
											quantity: item.quantity - 1
										})),
										sx: {
											color: "#94A3B8",
											p: .5,
											border: "1px solid #334155"
										},
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RemoveRounded_default, { sx: { fontSize: 14 } })
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "body2",
										sx: {
											minWidth: 20,
											textAlign: "center",
											fontWeight: 700
										},
										children: item.quantity
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
										size: "small",
										onClick: () => dispatch(updateQuantity({
											productId: item.productId,
											quantity: item.quantity + 1
										})),
										sx: {
											color: "#94A3B8",
											p: .5,
											border: "1px solid #334155"
										},
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AddRounded_default, { sx: { fontSize: 14 } })
									})
								]
							})]
						})]
					}, item.productId))
				})
			}),
			cart.items && cart.items.length > 0 && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
				sx: {
					pt: 2,
					borderTop: "1px solid #1E293B"
				},
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
						sx: {
							display: "flex",
							justifyContent: "space-between",
							mb: 2
						},
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
							variant: "body1",
							sx: { color: "#94A3B8" },
							children: "Subtotal"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
							variant: "h6",
							sx: {
								fontWeight: 800,
								color: "#F8FAFC"
							},
							children: ["$", subtotal.toFixed(2)]
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
						variant: "contained",
						fullWidth: true,
						onClick: () => {
							onClose();
							navigate("/cart");
						},
						sx: {
							mb: 1.5,
							py: 1.2,
							bgcolor: "#1E293B",
							color: "#F8FAFC",
							fontWeight: 700,
							"&:hover": { bgcolor: "#334155" }
						},
						children: "View Full Cart"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
						variant: "contained",
						fullWidth: true,
						endIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ArrowForwardRounded_default, {}),
						onClick: () => {
							onClose();
							navigate("/orders");
						},
						sx: {
							py: 1.4,
							fontWeight: 800,
							background: "linear-gradient(135deg, #4F46E5 0%, #06B6D4 100%)",
							"&:hover": { background: "linear-gradient(135deg, #4338CA 0%, #0891B2 100%)" }
						},
						children: "Checkout Now"
					})
				]
			})
		]
	});
}
export { CartDrawer as default };
