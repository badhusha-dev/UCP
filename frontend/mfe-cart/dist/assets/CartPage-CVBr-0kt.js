import { r as importShared } from "./_virtual___federation_fn_import-7k9UwJ4Q.js";
import { c as Typography, i as Box, l as IconButton, m as Paper, r as Button, t as Stack, u as CircularProgress, vt as require_jsx_runtime, w as createSvgIcon } from "./Stack-CWAApUdV.js";
import { a as Grid, i as Tooltip, n as axios, o as Chip, r as TextField, s as Alert, t as RefreshRounded_default } from "./RefreshRounded-q1MiYmvW.js";
import { a as AddRounded_default, c as clearCart, d as Divider, i as ArrowForwardRounded_default, l as removeFromCart, n as RemoveRounded_default, r as DeleteOutlineRounded_default, t as ShoppingCartOutlined_default, u as updateQuantity } from "./ShoppingCartOutlined-BvL5uOlN.js";
// ../node_modules/@mui/icons-material/esm/CheckCircleRounded.js
var import_jsx_runtime = require_jsx_runtime();
var CheckCircleRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2M9.29 16.29 5.7 12.7a.996.996 0 0 1 0-1.41c.39-.39 1.02-.39 1.41 0L10 14.17l6.88-6.88c.39-.39 1.02-.39 1.41 0s.39 1.02 0 1.41l-7.59 7.59c-.38.39-1.02.39-1.41 0" }), "CheckCircleRounded");
// ../node_modules/@mui/icons-material/esm/LockRounded.js
var LockRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2m-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2M9 8V6c0-1.66 1.34-3 3-3s3 1.34 3 3v2z" }), "LockRounded");
// ../node_modules/@mui/icons-material/esm/StorefrontRounded.js
var StorefrontRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "m21.9 7.89-1.05-3.37c-.22-.9-1-1.52-1.91-1.52H5.05c-.9 0-1.69.63-1.9 1.52L2.1 7.89c-.46 1.97.85 3.11.9 3.17V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-7.94c1.12-1.12 1.09-2.41.9-3.17M13 5h1.96l.54 3.52c.09.71-.39 1.48-1.28 1.48-.67 0-1.22-.59-1.22-1.31zM6.44 8.86c-.08.65-.6 1.14-1.21 1.14-.93 0-1.35-.97-1.19-1.64L5.05 5h1.97zM11 8.69c0 .72-.55 1.31-1.29 1.31-.75 0-1.3-.7-1.22-1.48L9.04 5H11zM18.77 10c-.61 0-1.14-.49-1.21-1.14L16.98 5l1.93-.01 1.05 3.37c.16.67-.25 1.64-1.19 1.64" }), "StorefrontRounded");
// ../node_modules/@mui/icons-material/esm/WarningAmberRounded.js
var WarningAmberRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M12 5.99 19.53 19H4.47zM2.74 18c-.77 1.33.19 3 1.73 3h15.06c1.54 0 2.5-1.67 1.73-3L13.73 4.99c-.77-1.33-2.69-1.33-3.46 0zM11 11v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1m0 5h2v2h-2z" }), "WarningAmberRounded");
// src/pages/CartPage.tsx
var { useState, useEffect } = await importShared("react");
var { useSelector, useDispatch } = await importShared("react-redux");
var { useNavigate } = await importShared("react-router-dom");
var DEFAULT_IMAGE = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=400&q=80";
function CartPage() {
	const dispatch = useDispatch();
	const navigate = useNavigate();
	const reduxCart = useSelector((state) => state.cart);
	useSelector((state) => state.auth?.user);
	const [backendItems, setBackendItems] = useState([]);
	const [validationItems, setValidationItems] = useState([]);
	const [loading, setLoading] = useState(true);
	const [promoCode, setPromoCode] = useState("");
	const [discountPercent, setDiscountPercent] = useState(0);
	const [promoMessage, setPromoMessage] = useState("");
	const [errorMsg, setErrorMsg] = useState("");
	const getSessionToken = () => {
		let token = localStorage.getItem("cart_session_token");
		if (!token) {
			token = "sess_" + Math.random().toString(36).substring(2, 12);
			localStorage.setItem("cart_session_token", token);
		}
		return token;
	};
	const fetchCartAndValidate = async () => {
		setLoading(true);
		setErrorMsg("");
		try {
			const sessionToken = getSessionToken();
			const jwtToken = localStorage.getItem("jwt_token");
			const headers = { "X-Cart-Session": sessionToken };
			if (jwtToken) headers["Authorization"] = `Bearer ${jwtToken}`;
			if (reduxCart?.items && reduxCart.items.length > 0) {
				const syncPayload = {
					sessionToken,
					items: reduxCart.items.map((i) => ({
						productId: i.productId,
						sku: `SKU-${i.productId}`,
						productName: i.name || i.productName || "Catalog Product",
						unitPrice: i.price || i.unitPrice || 0,
						quantity: i.quantity || 1,
						imageUrl: i.imageUrl || DEFAULT_IMAGE
					}))
				};
				await axios.post("/api/cart/sync", syncPayload, { headers });
			}
			const cartRes = await axios.get("/api/cart", { headers });
			if (cartRes.data && cartRes.data.items) setBackendItems(cartRes.data.items);
			const valRes = await axios.get("/api/cart/validate", { headers });
			if (valRes.data && valRes.data.items) setValidationItems(valRes.data.items);
		} catch (err) {
			console.warn("Backend cart service query:", err.message);
			if (reduxCart?.items) setBackendItems(reduxCart.items.map((item, idx) => ({
				id: idx + 1,
				productId: item.productId,
				sku: `SKU-${item.productId}`,
				productName: item.name || "Product",
				unitPrice: item.price || 0,
				quantity: item.quantity,
				subtotal: (item.price || 0) * item.quantity,
				imageUrl: DEFAULT_IMAGE
			})));
		} finally {
			setLoading(false);
		}
	};
	useEffect(() => {
		fetchCartAndValidate();
	}, []);
	const handleQuantityChange = async (productId, newQty, itemId) => {
		dispatch(updateQuantity({
			productId,
			quantity: newQty
		}));
		try {
			const sessionToken = getSessionToken();
			const jwtToken = localStorage.getItem("jwt_token");
			const headers = { "X-Cart-Session": sessionToken };
			if (jwtToken) headers["Authorization"] = `Bearer ${jwtToken}`;
			if (itemId) await axios.put(`/api/cart/items/${itemId}`, { quantity: newQty }, { headers });
			fetchCartAndValidate();
		} catch (err) {
			console.warn("Failed to update quantity on backend", err);
		}
	};
	const handleRemoveItem = async (productId, itemId) => {
		dispatch(removeFromCart(productId));
		try {
			const sessionToken = getSessionToken();
			const jwtToken = localStorage.getItem("jwt_token");
			const headers = { "X-Cart-Session": sessionToken };
			if (jwtToken) headers["Authorization"] = `Bearer ${jwtToken}`;
			if (itemId) await axios.delete(`/api/cart/items/${itemId}`, { headers });
			fetchCartAndValidate();
		} catch (err) {
			console.warn("Failed to remove item on backend", err);
		}
	};
	const handleClearCart = async () => {
		dispatch(clearCart());
		try {
			const sessionToken = getSessionToken();
			const jwtToken = localStorage.getItem("jwt_token");
			const headers = { "X-Cart-Session": sessionToken };
			if (jwtToken) headers["Authorization"] = `Bearer ${jwtToken}`;
			await axios.delete("/api/cart", { headers });
			setBackendItems([]);
			setValidationItems([]);
		} catch (err) {
			console.warn("Failed to clear cart on backend", err);
		}
	};
	const handleApplyPromo = () => {
		const code = promoCode.trim().toUpperCase();
		if (code === "UNIVERSAL20") {
			setDiscountPercent(20);
			setPromoMessage("20% Universal Commerce discount applied!");
			setErrorMsg("");
		} else if (code === "COMEBACK15" || code === "SAVE15") {
			setDiscountPercent(15);
			setPromoMessage("15% Customer Recovery discount applied!");
			setErrorMsg("");
		} else {
			setDiscountPercent(0);
			setErrorMsg("Invalid discount code. Try \"UNIVERSAL20\" or \"COMEBACK15\".");
			setPromoMessage("");
		}
	};
	const subtotal = backendItems.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
	const discountAmount = Math.round(subtotal * (discountPercent / 100) * 100) / 100;
	const shippingFee = subtotal > 100 || subtotal === 0 ? 0 : 9.99;
	const tax = Math.round((subtotal - discountAmount) * .0825 * 100) / 100;
	const grandTotal = Math.max(0, Math.round((subtotal - discountAmount + shippingFee + tax) * 100) / 100);
	const totalItemCount = backendItems.reduce((sum, item) => sum + item.quantity, 0);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
		sx: {
			minHeight: "80vh",
			py: 4
		},
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
			sx: {
				mb: 4,
				display: "flex",
				justifyContent: "space-between",
				alignItems: "center",
				flexWrap: "wrap",
				gap: 2
			},
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
				direction: "row",
				spacing: 1.5,
				alignItems: "center",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
					sx: {
						width: 44,
						height: 44,
						borderRadius: 2.5,
						background: "linear-gradient(135deg, #4F46E5 0%, #06B6D4 100%)",
						display: "flex",
						alignItems: "center",
						justifyContent: "center",
						boxShadow: "0 4px 14px rgba(79, 70, 229, 0.3)"
					},
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ShoppingCartOutlined_default, { sx: {
						color: "#FFFFFF",
						fontSize: 24
					} })
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					variant: "h4",
					sx: {
						fontWeight: 900,
						color: "#F8FAFC",
						letterSpacing: "-0.02em"
					},
					children: "Shopping Basket"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
					variant: "body2",
					sx: { color: "#94A3B8" },
					children: [
						totalItemCount,
						" ",
						totalItemCount === 1 ? "item" : "items",
						" in your persistent customer cart"
					]
				})] })]
			}) }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
				direction: "row",
				spacing: 1.5,
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
					variant: "outlined",
					size: "small",
					startIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RefreshRounded_default, {}),
					onClick: fetchCartAndValidate,
					sx: {
						borderColor: "#334155",
						color: "#94A3B8",
						"&:hover": {
							borderColor: "#4F46E5",
							color: "#F8FAFC"
						}
					},
					children: "Refresh Stock"
				}), backendItems.length > 0 && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
					variant: "outlined",
					color: "error",
					size: "small",
					startIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DeleteOutlineRounded_default, {}),
					onClick: handleClearCart,
					sx: {
						textTransform: "none",
						fontWeight: 600
					},
					children: "Clear Basket"
				})]
			})]
		}), loading ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
			sx: {
				display: "flex",
				justifyContent: "center",
				py: 12
			},
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(CircularProgress, { color: "primary" })
		}) : backendItems.length === 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
			sx: {
				p: 8,
				textAlign: "center",
				bgcolor: "#111827",
				borderRadius: 4,
				border: "1px dashed #334155"
			},
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
					sx: {
						width: 80,
						height: 80,
						borderRadius: "50%",
						bgcolor: "rgba(79, 70, 229, 0.1)",
						display: "flex",
						alignItems: "center",
						justifyContent: "center",
						margin: "0 auto 24px"
					},
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ShoppingCartOutlined_default, { sx: {
						fontSize: 40,
						color: "#6366F1"
					} })
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					variant: "h5",
					sx: {
						fontWeight: 800,
						color: "#F8FAFC",
						mb: 1
					},
					children: "Your Cart is Empty"
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					variant: "body1",
					sx: {
						color: "#94A3B8",
						mb: 4,
						maxWidth: 460,
						mx: "auto"
					},
					children: "Explore our curated catalog of smart electronics, audio gear, and lifestyle products to start shopping."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
					variant: "contained",
					size: "large",
					onClick: () => navigate("/products"),
					startIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(StorefrontRounded_default, {}),
					sx: {
						bgcolor: "#4F46E5",
						fontWeight: 700,
						borderRadius: 3,
						px: 4,
						py: 1.5,
						"&:hover": { bgcolor: "#4338CA" }
					},
					children: "Explore Catalog"
				})
			]
		}) : /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
			container: true,
			spacing: 4,
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
				item: true,
				xs: 12,
				lg: 8,
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stack, {
					spacing: 2.5,
					children: backendItems.map((item) => {
						const validation = validationItems.find((v) => v.productId === item.productId);
						const isAvailable = validation ? validation.inStock : true;
						const stockQty = validation ? validation.availableStock : 50;
						return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Paper, {
							sx: {
								p: 2.5,
								bgcolor: "#111827",
								borderRadius: 3,
								border: "1px solid #1E293B",
								transition: "border-color 0.2s ease",
								"&:hover": { borderColor: "#334155" }
							},
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
								container: true,
								spacing: 2.5,
								alignItems: "center",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
										item: true,
										xs: 3,
										sm: 2.5,
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
											component: "img",
											src: item.imageUrl || DEFAULT_IMAGE,
											alt: item.productName,
											sx: {
												width: "100%",
												height: 96,
												objectFit: "cover",
												borderRadius: 2.5,
												border: "1px solid #1E293B"
											}
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
										item: true,
										xs: 9,
										sm: 5,
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
												variant: "h6",
												sx: {
													fontWeight: 800,
													color: "#F8FAFC",
													fontSize: "1rem",
													mb: .5
												},
												children: item.productName
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "caption",
												sx: {
													color: "#64748B",
													display: "block",
													mb: 1
												},
												children: ["SKU: ", item.sku || `SKU-${item.productId}`]
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
												sx: { mt: .5 },
												children: isAvailable ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
													icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(CheckCircleRounded_default, { sx: {
														fontSize: "14px !important",
														color: "#10B981 !important"
													} }),
													label: `In Stock (${stockQty} units ready)`,
													size: "small",
													sx: {
														bgcolor: "rgba(16, 185, 129, 0.1)",
														color: "#34D399",
														border: "1px solid rgba(16, 185, 129, 0.2)",
														fontWeight: 700,
														fontSize: "0.72rem"
													}
												}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
													icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(WarningAmberRounded_default, { sx: {
														fontSize: "14px !important",
														color: "#EF4444 !important"
													} }),
													label: `Low Stock (Only ${stockQty} available)`,
													size: "small",
													sx: {
														bgcolor: "rgba(239, 68, 68, 0.1)",
														color: "#F87171",
														border: "1px solid rgba(239, 68, 68, 0.2)",
														fontWeight: 700,
														fontSize: "0.72rem"
													}
												})
											})
										]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
										item: true,
										xs: 6,
										sm: 2.5,
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
											direction: "row",
											alignItems: "center",
											spacing: 1,
											children: [
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
													size: "small",
													onClick: () => handleQuantityChange(item.productId, item.quantity - 1, item.id),
													sx: {
														border: "1px solid #334155",
														color: "#94A3B8",
														"&:hover": {
															bgcolor: "#1E293B",
															color: "#FFFFFF"
														}
													},
													children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RemoveRounded_default, { sx: { fontSize: 16 } })
												}),
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
													sx: {
														fontWeight: 800,
														color: "#F8FAFC",
														minWidth: 28,
														textAlign: "center"
													},
													children: item.quantity
												}),
												/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
													size: "small",
													onClick: () => handleQuantityChange(item.productId, item.quantity + 1, item.id),
													sx: {
														border: "1px solid #334155",
														color: "#94A3B8",
														"&:hover": {
															bgcolor: "#1E293B",
															color: "#FFFFFF"
														}
													},
													children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AddRounded_default, { sx: { fontSize: 16 } })
												})
											]
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
										item: true,
										xs: 6,
										sm: 2,
										sx: { textAlign: "right" },
										children: [
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "h6",
												sx: {
													fontWeight: 800,
													color: "#F8FAFC",
													fontSize: "1.05rem"
												},
												children: ["$", (item.unitPrice * item.quantity).toFixed(2)]
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
												variant: "caption",
												sx: {
													color: "#64748B",
													display: "block"
												},
												children: [
													"$",
													item.unitPrice.toFixed(2),
													" each"
												]
											}),
											/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tooltip, {
												title: "Remove item",
												children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
													size: "small",
													onClick: () => handleRemoveItem(item.productId, item.id),
													sx: {
														color: "#64748B",
														mt: .5,
														"&:hover": { color: "#EF4444" }
													},
													children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(DeleteOutlineRounded_default, { sx: { fontSize: 18 } })
												})
											})
										]
									})
								]
							})
						}, item.id || item.productId);
					})
				})
			}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
				item: true,
				xs: 12,
				lg: 4,
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
					sx: {
						p: 3.5,
						bgcolor: "#111827",
						borderRadius: 4,
						border: "1px solid #1E293B",
						boxShadow: "0 8px 32px rgba(0, 0, 0, 0.4)"
					},
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
							variant: "h6",
							sx: {
								fontWeight: 800,
								color: "#F8FAFC",
								mb: 2.5
							},
							children: "Order Summary"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
							sx: { mb: 3 },
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#94A3B8",
										fontWeight: 700,
										mb: 1,
										display: "block"
									},
									children: "HAVE A PROMO CODE?"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
									direction: "row",
									spacing: 1,
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TextField, {
										size: "small",
										fullWidth: true,
										placeholder: "e.g. UNIVERSAL20",
										value: promoCode,
										onChange: (e) => setPromoCode(e.target.value),
										sx: { "& .MuiOutlinedInput-root": {
											bgcolor: "#0B0F19",
											borderRadius: 2,
											color: "#F8FAFC",
											"& fieldset": { borderColor: "#334155" },
											"&:hover fieldset": { borderColor: "#4F46E5" }
										} }
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
										variant: "contained",
										onClick: handleApplyPromo,
										sx: {
											bgcolor: "#312E81",
											color: "#E0E7FF",
											fontWeight: 700,
											borderRadius: 2,
											px: 2.5,
											"&:hover": { bgcolor: "#4338CA" }
										},
										children: "Apply"
									})]
								}),
								promoMessage && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Alert, {
									severity: "success",
									sx: {
										mt: 1.5,
										py: .25,
										fontSize: "0.8rem",
										borderRadius: 2
									},
									children: promoMessage
								}),
								errorMsg && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Alert, {
									severity: "error",
									sx: {
										mt: 1.5,
										py: .25,
										fontSize: "0.8rem",
										borderRadius: 2
									},
									children: errorMsg
								})
							]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Divider, { sx: {
							borderColor: "#1E293B",
							mb: 2.5
						} }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
							spacing: 1.5,
							sx: { mb: 3 },
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
									sx: {
										display: "flex",
										justifyContent: "space-between"
									},
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "body2",
										sx: { color: "#94A3B8" },
										children: "Subtotal"
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "body2",
										sx: {
											fontWeight: 700,
											color: "#F8FAFC"
										},
										children: ["$", subtotal.toFixed(2)]
									})]
								}),
								discountAmount > 0 && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
									sx: {
										display: "flex",
										justifyContent: "space-between"
									},
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "body2",
										sx: {
											color: "#34D399",
											fontWeight: 600
										},
										children: [
											"Discount (",
											discountPercent,
											"%)"
										]
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "body2",
										sx: {
											fontWeight: 700,
											color: "#34D399"
										},
										children: ["-$", discountAmount.toFixed(2)]
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
									sx: {
										display: "flex",
										justifyContent: "space-between"
									},
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "body2",
										sx: { color: "#94A3B8" },
										children: "Estimated Shipping"
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "body2",
										sx: {
											fontWeight: 700,
											color: shippingFee === 0 ? "#34D399" : "#F8FAFC"
										},
										children: shippingFee === 0 ? "FREE" : `$${shippingFee.toFixed(2)}`
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
									sx: {
										display: "flex",
										justifyContent: "space-between"
									},
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
										variant: "body2",
										sx: { color: "#94A3B8" },
										children: "Estimated Tax (8.25%)"
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										variant: "body2",
										sx: {
											fontWeight: 700,
											color: "#F8FAFC"
										},
										children: ["$", tax.toFixed(2)]
									})]
								})
							]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Divider, { sx: {
							borderColor: "#1E293B",
							mb: 3
						} }),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
							sx: {
								display: "flex",
								justifyContent: "space-between",
								alignItems: "center",
								mb: 3
							},
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "h6",
								sx: {
									fontWeight: 800,
									color: "#F8FAFC"
								},
								children: "Total"
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
								variant: "h5",
								sx: {
									fontWeight: 900,
									color: "#06B6D4"
								},
								children: ["$", grandTotal.toFixed(2)]
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Button, {
							variant: "contained",
							fullWidth: true,
							size: "large",
							endIcon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ArrowForwardRounded_default, {}),
							onClick: () => navigate("/orders"),
							sx: {
								py: 1.8,
								borderRadius: 3,
								fontWeight: 800,
								fontSize: "1rem",
								background: "linear-gradient(135deg, #4F46E5 0%, #06B6D4 100%)",
								boxShadow: "0 4px 18px rgba(79, 70, 229, 0.4)",
								"&:hover": { background: "linear-gradient(135deg, #4338CA 0%, #0891B2 100%)" }
							},
							children: "Proceed to Checkout"
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
							direction: "row",
							spacing: 1,
							justifyContent: "center",
							alignItems: "center",
							sx: { mt: 2.5 },
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(LockRounded_default, { sx: {
								fontSize: 16,
								color: "#64748B"
							} }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "caption",
								sx: {
									color: "#64748B",
									fontWeight: 600
								},
								children: "256-Bit SSL Encrypted • Zero-Trust Saga Protected"
							})]
						})
					]
				})
			})]
		})]
	});
}
export { CartPage as t };
