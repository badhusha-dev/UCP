import { r as importShared } from "./_virtual___federation_fn_import-DYD12ulU.js";
import { a as TableBody, b as Paper, c as LinearProgress, d as Card, dt as require_jsx_runtime, f as Box, h as Alert, i as TableCell, l as Grid, m as Typography, n as TableRow, o as Table, p as Chip, r as TableHead, s as Skeleton, t as loyaltyApi, u as CardContent } from "./loyaltyApi-DQKJ10CA.js";
// src/pages/LoyaltyDashboardPage.tsx
var import_jsx_runtime = require_jsx_runtime();
var { useEffect, useState } = await importShared("react");
var TIER_COLORS = {
	BRONZE: "#CD7F32",
	SILVER: "#C0C0C0",
	GOLD: "#FFD700",
	PLATINUM: "#E5E4E2"
};
var TIER_GRADIENTS = {
	BRONZE: "linear-gradient(135deg, #7c4a0a 0%, #CD7F32 100%)",
	SILVER: "linear-gradient(135deg, #555 0%, #C0C0C0 100%)",
	GOLD: "linear-gradient(135deg, #8B6914 0%, #FFD700 100%)",
	PLATINUM: "linear-gradient(135deg, #374151 0%, #9CA3AF 100%)"
};
function TierBadge({ tier }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
		sx: {
			display: "inline-flex",
			alignItems: "center",
			justifyContent: "center",
			background: TIER_GRADIENTS[tier] || "#333",
			borderRadius: "50%",
			width: 96,
			height: 96,
			boxShadow: `0 0 24px ${TIER_COLORS[tier]}66`,
			mb: 1
		},
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
			variant: "h6",
			sx: {
				color: "#fff",
				fontWeight: 700,
				letterSpacing: 1
			},
			children: tier
		})
	});
}
function txColor(type) {
	if (type === "EARN" || type === "BONUS") return "#4ade80";
	if (type === "REDEEM") return "#f87171";
	return "#94a3b8";
}
function LoyaltyDashboardPage({ userId: propUserId }) {
	const userId = propUserId ?? (() => {
		const explicitId = localStorage.getItem("userId");
		if (explicitId && Number(explicitId) > 0) return Number(explicitId);
		try {
			const token = localStorage.getItem("jwt_token");
			if (!token) return 1;
			const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
			return Number(payload.id ?? payload.userId ?? 1);
		} catch {
			return 1;
		}
	})();
	const [account, setAccount] = useState(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	useEffect(() => {
		if (!userId) {
			setError("Not logged in");
			setLoading(false);
			return;
		}
		loyaltyApi.getProfile(userId).then(setAccount).catch((e) => setError(e.response?.data?.message || "Failed to load loyalty profile")).finally(() => setLoading(false));
	}, [userId]);
	if (loading) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
		p: 4,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Skeleton, {
			variant: "rectangular",
			height: 300
		})
	});
	if (error) return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
		p: 4,
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Alert, {
			severity: "error",
			children: error
		})
	});
	if (!account) return null;
	const progressToNext = account.nextTier && account.pointsToNextTier !== void 0 ? Math.min(100, account.lifetimePoints / (account.lifetimePoints + account.pointsToNextTier) * 100) : 100;
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
		sx: {
			bgcolor: "#0B0F19",
			minHeight: "100vh",
			p: 3
		},
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
			variant: "h4",
			sx: {
				color: "#fff",
				fontWeight: 700,
				mb: 3
			},
			children: "🎖 My Loyalty Rewards"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
			container: true,
			spacing: 3,
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
					item: true,
					xs: 12,
					md: 4,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
						sx: {
							bgcolor: "#111827",
							border: `1px solid ${TIER_COLORS[account.tier]}44`,
							borderRadius: 3
						},
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, {
							sx: {
								textAlign: "center",
								py: 4
							},
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TierBadge, { tier: account.tier }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
									variant: "h5",
									sx: {
										color: TIER_COLORS[account.tier],
										fontWeight: 700,
										mt: 1
									},
									children: [account.tier, " MEMBER"]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
									sx: {
										color: "#94a3b8",
										mt: .5,
										fontSize: 13
									},
									children: [account.tierMultiplier, "x points multiplier"]
								}),
								account.nextTier && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
									sx: { mt: 2 },
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
										sx: {
											color: "#6b7280",
											fontSize: 12,
											mb: .5
										},
										children: [
											account.pointsToNextTier?.toLocaleString(),
											" pts to ",
											account.nextTier
										]
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(LinearProgress, {
										variant: "determinate",
										value: progressToNext,
										sx: {
											height: 8,
											borderRadius: 4,
											bgcolor: "#1E293B",
											"& .MuiLinearProgress-bar": { bgcolor: TIER_COLORS[account.tier] }
										}
									})]
								}),
								!account.nextTier && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
									label: "MAX TIER 🏆",
									sx: {
										mt: 2,
										bgcolor: "#FFD70022",
										color: "#FFD700"
									}
								})
							]
						})
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
					item: true,
					xs: 12,
					md: 8,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
						container: true,
						spacing: 2,
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
								item: true,
								xs: 12,
								sm: 6,
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
									sx: {
										bgcolor: "#111827",
										borderRadius: 3,
										border: "1px solid #1E293B"
									},
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, { children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											sx: {
												color: "#94a3b8",
												fontSize: 13,
												mb: 1
											},
											children: "Current Balance"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "h3",
											sx: {
												color: "#4F46E5",
												fontWeight: 700
											},
											children: account.pointsBalance.toLocaleString()
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											sx: {
												color: "#06B6D4",
												fontSize: 13
											},
											children: "points"
										})
									] })
								})
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
								item: true,
								xs: 12,
								sm: 6,
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
									sx: {
										bgcolor: "#111827",
										borderRadius: 3,
										border: "1px solid #1E293B"
									},
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, { children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											sx: {
												color: "#94a3b8",
												fontSize: 13,
												mb: 1
											},
											children: "Cash Value"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
											variant: "h3",
											sx: {
												color: "#10b981",
												fontWeight: 700
											},
											children: ["$", Number(account.cashValue).toFixed(2)]
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											sx: {
												color: "#6b7280",
												fontSize: 13
											},
											children: "redeemable value"
										})
									] })
								})
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
								item: true,
								xs: 12,
								sm: 6,
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
									sx: {
										bgcolor: "#111827",
										borderRadius: 3,
										border: "1px solid #1E293B"
									},
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, { children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											sx: {
												color: "#94a3b8",
												fontSize: 13,
												mb: 1
											},
											children: "Lifetime Points"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "h4",
											sx: {
												color: "#F59E0B",
												fontWeight: 700
											},
											children: account.lifetimePoints.toLocaleString()
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											sx: {
												color: "#6b7280",
												fontSize: 13
											},
											children: "total earned"
										})
									] })
								})
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
								item: true,
								xs: 12,
								sm: 6,
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
									sx: {
										bgcolor: "#111827",
										borderRadius: 3,
										border: "1px solid #1E293B"
									},
									children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, { children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											sx: {
												color: "#94a3b8",
												fontSize: 13,
												mb: 1
											},
											children: "Points Rate"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "h4",
											sx: {
												color: "#a78bfa",
												fontWeight: 700
											},
											children: "10"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
											sx: {
												color: "#6b7280",
												fontSize: 13
											},
											children: [
												"pts per $1 spent × ",
												account.tierMultiplier,
												"x"
											]
										})
									] })
								})
							})
						]
					})
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
					item: true,
					xs: 12,
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
						sx: {
							bgcolor: "#111827",
							borderRadius: 3,
							border: "1px solid #1E293B",
							overflow: "hidden"
						},
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Box, {
							sx: {
								p: 2,
								borderBottom: "1px solid #1E293B"
							},
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "h6",
								sx: {
									color: "#fff",
									fontWeight: 600
								},
								children: "Recent Transactions"
							})
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Table, {
							size: "small",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableHead, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableRow, { children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
									sx: {
										color: "#6b7280",
										borderColor: "#1E293B"
									},
									children: "Date"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
									sx: {
										color: "#6b7280",
										borderColor: "#1E293B"
									},
									children: "Type"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
									sx: {
										color: "#6b7280",
										borderColor: "#1E293B"
									},
									children: "Description"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
									align: "right",
									sx: {
										color: "#6b7280",
										borderColor: "#1E293B"
									},
									children: "Points"
								})
							] }) }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableBody, { children: [(account.recentTransactions || []).length === 0 && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableRow, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
								colSpan: 4,
								sx: {
									color: "#4b5563",
									textAlign: "center",
									borderColor: "#1E293B"
								},
								children: "No transactions yet. Start shopping to earn points!"
							}) }), (account.recentTransactions || []).map((tx) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableRow, {
								hover: true,
								sx: { "&:hover": { bgcolor: "#1E293B33" } },
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
										sx: {
											color: "#9ca3af",
											fontSize: 12,
											borderColor: "#1E293B"
										},
										children: new Date(tx.createdAt).toLocaleDateString()
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
										sx: { borderColor: "#1E293B" },
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
											label: tx.transactionType,
											size: "small",
											sx: {
												bgcolor: `${txColor(tx.transactionType)}22`,
												color: txColor(tx.transactionType),
												fontSize: 10
											}
										})
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
										sx: {
											color: "#d1d5db",
											fontSize: 13,
											borderColor: "#1E293B"
										},
										children: tx.description || tx.referenceId || "—"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
										align: "right",
										sx: { borderColor: "#1E293B" },
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
											sx: {
												color: txColor(tx.transactionType),
												fontWeight: 700,
												fontSize: 14
											},
											children: [tx.points > 0 ? "+" : "", tx.points.toLocaleString()]
										})
									})
								]
							}, tx.id))] })]
						})]
					})
				})
			]
		})]
	});
}
export { LoyaltyDashboardPage as t };
