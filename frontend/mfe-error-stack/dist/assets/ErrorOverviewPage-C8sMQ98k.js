import { r as importShared } from "./_virtual___federation_fn_import-DYD12ulU.js";
import { $ as generateUtilityClasses, A as styled, E as useDefaultProps, H as composeClasses, T as createSvgIcon, _ as Paper, c as Chip, d as Alert, et as generateUtilityClass, f as IconButton, i as LinearProgress, n as Stack, o as Grid, r as Tooltip, s as Box, st as require_jsx_runtime, t as axios, tt as clsx, u as Typography } from "./axios-2qcjEZNH.js";
import { a as TableContainer, c as Table, i as TableHead, n as OpenInNewRounded_default, o as TableCell, r as TableRow, s as TableBody, t as RefreshRounded_default } from "./RefreshRounded-DSckIcKG.js";
import { t as BugReportRounded_default } from "./BugReportRounded-7-8H7FS2.js";
// ../node_modules/@mui/material/Card/cardClasses.js
function getCardUtilityClass(slot) {
	return generateUtilityClass("MuiCard", slot);
}
generateUtilityClasses("MuiCard", ["root"]);
// ../node_modules/@mui/material/Card/Card.js
var import_jsx_runtime = require_jsx_runtime();
var React$1 = await importShared("react");
var useUtilityClasses$1 = (ownerState) => {
	const { classes } = ownerState;
	return composeClasses({ root: ["root"] }, getCardUtilityClass, classes);
};
var CardRoot = styled(Paper, {
	name: "MuiCard",
	slot: "Root",
	overridesResolver: (props, styles) => styles.root
})({ overflow: "hidden" });
var Card = /*#__PURE__*/ React$1.forwardRef(function Card(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiCard"
	});
	const { className, raised = false, ...other } = props;
	const ownerState = {
		...props,
		raised
	};
	const classes = useUtilityClasses$1(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(CardRoot, {
		className: clsx(classes.root, className),
		elevation: raised ? 8 : void 0,
		ref,
		ownerState,
		...other
	});
});
// ../node_modules/@mui/material/CardContent/cardContentClasses.js
function getCardContentUtilityClass(slot) {
	return generateUtilityClass("MuiCardContent", slot);
}
generateUtilityClasses("MuiCardContent", ["root"]);
// ../node_modules/@mui/material/CardContent/CardContent.js
var React = await importShared("react");
var useUtilityClasses = (ownerState) => {
	const { classes } = ownerState;
	return composeClasses({ root: ["root"] }, getCardContentUtilityClass, classes);
};
var CardContentRoot = styled("div", {
	name: "MuiCardContent",
	slot: "Root",
	overridesResolver: (props, styles) => styles.root
})({
	padding: 16,
	"&:last-child": { paddingBottom: 24 }
});
var CardContent = /*#__PURE__*/ React.forwardRef(function CardContent(inProps, ref) {
	const props = useDefaultProps({
		props: inProps,
		name: "MuiCardContent"
	});
	const { className, component = "div", ...other } = props;
	const ownerState = {
		...props,
		component
	};
	const classes = useUtilityClasses(ownerState);
	return /*#__PURE__*/ (0, import_jsx_runtime.jsx)(CardContentRoot, {
		as: component,
		className: clsx(classes.root, className),
		ownerState,
		ref,
		...other
	});
});
// ../node_modules/@mui/icons-material/esm/CheckCircleOutlineRounded.js
var CheckCircleOutlineRounded_default = createSvgIcon(/*#__PURE__*/ (0, import_jsx_runtime.jsx)("path", { d: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2m0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8m3.88-11.71L10 14.17l-1.88-1.88a.996.996 0 0 0-1.41 0c-.39.39-.39 1.02 0 1.41l2.59 2.59c.39.39 1.02.39 1.41 0L17.3 9.7c.39-.39.39-1.02 0-1.41s-1.03-.39-1.42 0" }), "CheckCircleOutlineRounded");
// src/pages/ErrorOverviewPage.tsx
var { useEffect, useState } = await importShared("react");
var API_BASE = typeof window !== "undefined" ? `http://${window.location.hostname || "localhost"}:8080` : "http://localhost:8080";
function ErrorOverviewPage({ onSelectGroup }) {
	const [data, setData] = useState(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState(null);
	const fetchOverview = async () => {
		setLoading(true);
		setError(null);
		try {
			const res = await axios.get(`${API_BASE}/api/errors/admin/overview`);
			setData(res.data);
		} catch (err) {
			setError(err?.response?.data?.message || "Failed to load error stack metrics");
		} finally {
			setLoading(false);
		}
	};
	useEffect(() => {
		fetchOverview();
	}, []);
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
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
					variant: "h5",
					sx: {
						fontWeight: 800,
						color: "#F8FAFC",
						display: "flex",
						alignItems: "center",
						gap: 1
					},
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(BugReportRounded_default, { sx: { color: "#EF4444" } }), " Error Stack & Diagnostics Intelligence"]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
					variant: "body2",
					sx: { color: "#94A3B8" },
					children: "Authoritative error tracking, root-cause diagnostics, and triage desk for platform microservices"
				})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Tooltip, {
					title: "Refresh metrics",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
						onClick: fetchOverview,
						sx: {
							bgcolor: "rgba(255,255,255,0.05)",
							color: "#94A3B8"
						},
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(RefreshRounded_default, {})
					})
				})]
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
			data && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
					container: true,
					spacing: 2.5,
					sx: { mb: 3 },
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 12,
							sm: 6,
							md: 2.4,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
								sx: {
									bgcolor: "#111827",
									border: "1px solid #1F2937",
									borderRadius: 3
								},
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, {
									sx: { p: 2.5 },
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: {
												color: "#94A3B8",
												fontWeight: 700,
												textTransform: "uppercase"
											},
											children: "Total Occurrences"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "h4",
											sx: {
												fontWeight: 900,
												color: "#F8FAFC",
												my: .5
											},
											children: data.totalOccurrences.toLocaleString()
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: { color: "#64748B" },
											children: "Across all environments"
										})
									]
								})
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 12,
							sm: 6,
							md: 2.4,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
								sx: {
									bgcolor: "#111827",
									border: "1px solid #1F2937",
									borderRadius: 3
								},
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, {
									sx: { p: 2.5 },
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: {
												color: "#94A3B8",
												fontWeight: 700,
												textTransform: "uppercase"
											},
											children: "Active Groups"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "h4",
											sx: {
												fontWeight: 900,
												color: "#818CF8",
												my: .5
											},
											children: data.totalGroups
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: { color: "#64748B" },
											children: "Clustered fingerprints"
										})
									]
								})
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 12,
							sm: 6,
							md: 2.4,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
								sx: {
									bgcolor: "#111827",
									border: "1px solid #1F2937",
									borderRadius: 3
								},
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, {
									sx: { p: 2.5 },
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: {
												color: "#EF4444",
												fontWeight: 700,
												textTransform: "uppercase"
											},
											children: "Unresolved Critical"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "h4",
											sx: {
												fontWeight: 900,
												color: "#EF4444",
												my: .5
											},
											children: data.unresolvedCriticalCount
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: { color: "#64748B" },
											children: "Immediate triage required"
										})
									]
								})
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 12,
							sm: 6,
							md: 2.4,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
								sx: {
									bgcolor: "#111827",
									border: "1px solid #1F2937",
									borderRadius: 3
								},
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, {
									sx: { p: 2.5 },
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: {
												color: "#F59E0B",
												fontWeight: 700,
												textTransform: "uppercase"
											},
											children: "New Unassigned"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "h4",
											sx: {
												fontWeight: 900,
												color: "#F59E0B",
												my: .5
											},
											children: data.newErrorsCount
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: { color: "#64748B" },
											children: "Pending review"
										})
									]
								})
							})
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
							item: true,
							xs: 12,
							sm: 6,
							md: 2.4,
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Card, {
								sx: {
									bgcolor: "#111827",
									border: "1px solid #1F2937",
									borderRadius: 3
								},
								children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(CardContent, {
									sx: { p: 2.5 },
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: {
												color: "#10B981",
												fontWeight: 700,
												textTransform: "uppercase"
											},
											children: "Last 24 Hours"
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "h4",
											sx: {
												fontWeight: 900,
												color: "#10B981",
												my: .5
											},
											children: data.last24HoursCount
										}),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: { color: "#64748B" },
											children: "Recent error velocity"
										})
									]
								})
							})
						})
					]
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Grid, {
					container: true,
					spacing: 3,
					sx: { mb: 3 },
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
						item: true,
						xs: 12,
						md: 6,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
							sx: {
								p: 3,
								bgcolor: "#111827",
								border: "1px solid #1F2937",
								borderRadius: 3,
								height: "100%"
							},
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "subtitle1",
								sx: {
									fontWeight: 800,
									color: "#F8FAFC",
									mb: 2
								},
								children: "Errors by Affected Service"
							}), Object.keys(data.serviceBreakdown).length === 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
								variant: "body2",
								sx: { color: "#64748B" },
								children: "No service errors recorded yet."
							}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stack, {
								spacing: 2,
								children: Object.entries(data.serviceBreakdown).map(([srv, count]) => {
									const max = Math.max(...Object.values(data.serviceBreakdown), 1);
									const pct = Math.round(count / max * 100);
									return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Stack, {
										direction: "row",
										justifyContent: "space-between",
										sx: { mb: .5 },
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "body2",
											sx: {
												fontWeight: 600,
												color: "#E2E8F0"
											},
											children: srv
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Typography, {
											variant: "body2",
											sx: {
												fontWeight: 700,
												color: "#818CF8"
											},
											children: [count, " groups"]
										})]
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(LinearProgress, {
										variant: "determinate",
										value: pct,
										sx: {
											height: 8,
											borderRadius: 4,
											bgcolor: "rgba(255,255,255,0.05)",
											"& .MuiLinearProgress-bar": {
												bgcolor: "#4F46E5",
												borderRadius: 4
											}
										}
									})] }, srv);
								})
							})]
						})
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Grid, {
						item: true,
						xs: 12,
						md: 6,
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
							sx: {
								p: 3,
								bgcolor: "#111827",
								border: "1px solid #1F2937",
								borderRadius: 3,
								height: "100%"
							},
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "subtitle1",
									sx: {
										fontWeight: 800,
										color: "#F8FAFC",
										mb: 2
									},
									children: "Severity & Triage Distribution"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#94A3B8",
										fontWeight: 700,
										textTransform: "uppercase"
									},
									children: "By Severity Level"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stack, {
									direction: "row",
									spacing: 1.5,
									sx: {
										mt: 1,
										mb: 3,
										flexWrap: "wrap",
										gap: 1
									},
									children: Object.entries(data.severityBreakdown).map(([sev, count]) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
										label: `${sev}: ${count}`,
										sx: {
											bgcolor: "rgba(255,255,255,0.05)",
											border: `1px solid ${getSeverityColor(sev)}`,
											color: getSeverityColor(sev),
											fontWeight: 700
										}
									}, sev))
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
									variant: "caption",
									sx: {
										color: "#94A3B8",
										fontWeight: 700,
										textTransform: "uppercase"
									},
									children: "By Triage Status"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stack, {
									direction: "row",
									spacing: 1.5,
									sx: {
										mt: 1,
										flexWrap: "wrap",
										gap: 1
									},
									children: Object.entries(data.statusBreakdown).map(([st, count]) => {
										const col = getStatusColor(st);
										return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
											label: `${st}: ${count}`,
											sx: {
												bgcolor: col.bg,
												color: col.text,
												fontWeight: 700
											}
										}, st);
									})
								})
							]
						})
					})]
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Paper, {
					sx: {
						p: 3,
						bgcolor: "#111827",
						border: "1px solid #1F2937",
						borderRadius: 3
					},
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
						variant: "subtitle1",
						sx: {
							fontWeight: 800,
							color: "#F8FAFC",
							mb: 2
						},
						children: "Recently Active Error Groups"
					}), data.recentGroups.length === 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Box, {
						sx: {
							p: 4,
							textAlign: "center",
							color: "#64748B"
						},
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(CheckCircleOutlineRounded_default, { sx: {
							fontSize: 48,
							color: "#10B981",
							mb: 1
						} }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
							variant: "body1",
							children: "Zero errors currently recorded. Platform is healthy!"
						})]
					}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableContainer, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(Table, {
						size: "small",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableHead, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableRow, {
							sx: { "& th": {
								color: "#64748B",
								fontWeight: 700,
								borderColor: "#1F2937"
							} },
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Error Type & Message" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Service" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Severity" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: "Status" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
									align: "right",
									children: "Occurrences"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
									align: "right",
									children: "Last Seen"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, {
									align: "center",
									children: "Action"
								})
							]
						}) }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableBody, { children: data.recentGroups.map((g) => {
							const stCol = getStatusColor(g.status);
							return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableRow, {
								sx: { "& td": {
									borderColor: "#1F2937",
									color: "#E2E8F0"
								} },
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)(TableCell, {
										sx: { maxWidth: 400 },
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "body2",
											sx: {
												fontWeight: 700,
												color: "#F8FAFC"
											},
											children: g.errorType
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Typography, {
											variant: "caption",
											sx: {
												color: "#94A3B8",
												display: "block",
												textOverflow: "ellipsis",
												overflow: "hidden",
												whiteSpace: "nowrap"
											},
											children: g.message
										})]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(TableCell, { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Chip, {
										size: "small",
										label: g.serviceName,
										sx: {
											bgcolor: "rgba(79,70,229,0.15)",
											color: "#818CF8",
											fontWeight: 600
										}
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
										children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconButton, {
											size: "small",
											onClick: () => onSelectGroup?.(g.fingerprint),
											sx: { color: "#818CF8" },
											children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(OpenInNewRounded_default, { fontSize: "small" })
										})
									})
								]
							}, g.fingerprint);
						}) })]
					}) })]
				})
			] })
		]
	});
}
export { ErrorOverviewPage as t };
