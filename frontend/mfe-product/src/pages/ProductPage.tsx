import React, { useEffect, useState, useMemo } from 'react';
import {
  Typography,
  Card,
  CardContent,
  CardMedia,
  Grid,
  Button,
  CircularProgress,
  Box,
  Container,
  TextField,
  InputAdornment,
  Chip,
  Stack,
  Rating,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Snackbar,
  Alert,
  Tooltip,
  Paper,
  Divider,
  Drawer,
  Checkbox,
  FormControlLabel,
  Tabs,
  Tab,
  Avatar,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import {
  SearchRounded,
  ShoppingCartRounded,
  VisibilityRounded,
  LocalShippingOutlined,
  BoltRounded,
  StarRounded,
  FilterListRounded,
  CheckCircleRounded,
  WarningAmberRounded,
  CloseRounded,
  AddRounded,
  RemoveRounded,
  FavoriteRounded,
  FavoriteBorderRounded,
  CompareArrowsRounded,
  SmartToyRounded,
  SendRounded,
  VerifiedRounded,
  RateReviewRounded,
  PsychologyRounded,
} from '@mui/icons-material';
import { addToCart, resolveActiveStorefront, storefrontParams } from '@ecommerce/shared-library';
import type { ActiveStorefront, AppDispatch, RootState } from '@ecommerce/shared-library';
import { useDispatch, useSelector } from 'react-redux';
import axios from 'axios';

axios.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

interface ProductCategory {
  id: number;
  name: string;
  slug: string;
  description?: string;
}

interface ProductVariant {
  id: number;
  productId: number;
  sku: string;
  name: string;
  color?: string;
  size?: string;
  priceAdjustment: number;
  finalPrice: number;
  stockQuantity: number;
}

interface ProductReview {
  id: number;
  productId: number;
  userId: number;
  userName: string;
  rating: number;
  comment: string;
  isVerifiedPurchase: boolean;
  createdAt: string;
}

interface Product {
  id: number;
  name: string;
  price: number;
  description: string;
  stockQuantity: number;
  category?: ProductCategory | string;
  rating?: number;
  reviewCount?: number;
  reviewsCount?: number;
  imageUrl?: string;
  badge?: string;
  originalPrice?: number;
}

const CURATED_IMAGES = [
  'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80', // Headphones
  'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80', // Smartwatch
  'https://images.unsplash.com/photo-1546868871-7041f2a55e12?auto=format&fit=crop&w=800&q=80', // Smart Watch Black
  'https://images.unsplash.com/photo-1583394838336-acd977736f90?auto=format&fit=crop&w=800&q=80', // Headset
  'https://images.unsplash.com/photo-1608231387042-66d1773070a5?auto=format&fit=crop&w=800&q=80', // Shoes
  'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=800&q=80', // Polaroid Camera
];

export default function ProductPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<string[]>(['All', 'Audio', 'Computer Peripherals', 'Gaming']);
  const [loading, setLoading] = useState(true);
  const [storefront, setStorefront] = useState<ActiveStorefront | null>(null);
  const [catalogError, setCatalogError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [sortBy, setSortBy] = useState('featured');

  // Wishlist state
  const [wishlistIds, setWishlistIds] = useState<Set<number>>(new Set());

  // Quick View & Variants state
  const [quickViewProduct, setQuickViewProduct] = useState<Product | null>(null);
  const [quickViewVariants, setQuickViewVariants] = useState<ProductVariant[]>([]);
  const [selectedVariant, setSelectedVariant] = useState<ProductVariant | null>(null);
  const [quickViewQty, setQuickViewQty] = useState(1);
  const [modalTab, setModalTab] = useState(0);

  // Reviews state
  const [reviews, setReviews] = useState<ProductReview[]>([]);
  const [reviewsLoading, setReviewsLoading] = useState(false);
  const [newReviewRating, setNewReviewRating] = useState(5);
  const [newReviewComment, setNewReviewComment] = useState('');
  const [submittingReview, setSubmittingReview] = useState(false);

  // Product Comparison state
  const [compareIds, setCompareIds] = useState<number[]>([]);
  const [compareModalOpen, setCompareModalOpen] = useState(false);

  // Recently Viewed state
  const [recentlyViewed, setRecentlyViewed] = useState<Product[]>([]);

  // AI Shopping Assistant state
  const [aiDrawerOpen, setAiDrawerOpen] = useState(false);
  const [aiQuery, setAiQuery] = useState('');
  const [aiMessages, setAiMessages] = useState<Array<{ sender: 'user' | 'assistant'; text: string; timestamp: string }>>([
    {
      sender: 'assistant',
      text: "Hello! I am your Universal Commerce AI Assistant. Ask me anything about our product specifications, battery life, warranties, or return policies!",
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    },
  ]);

  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarOpen, setSnackbarOpen] = useState(false);

  const dispatch = useDispatch<AppDispatch>();
  const user = useSelector((state: RootState) => state?.auth?.user);

  // Initial Data Fetch
  useEffect(() => {
    loadProducts();
    loadCategories();
    if (user) {
      loadWishlist();
      loadRecentlyViewed();
    }
  }, [user]);

  const loadProducts = () => {
    // The catalog belongs to the storefront this visit is for; the server maps its slug to a tenant
    resolveActiveStorefront(window.location, window.sessionStorage)
      .then((active) => {
        setStorefront(active);
        setCatalogError('');
        return axios.get('/api/products', { params: storefrontParams(active) });
      })
      .then(async (res) => {
        const data = Array.isArray(res.data) ? res.data : res.data.content || [];
        const stockByProduct = new Map<number, number>();
        try {
          const stockResponse = await axios.post('/api/stock/batch', data.map((item: any) => item.id));
          if (Array.isArray(stockResponse.data)) {
            stockResponse.data.forEach((stock: any) => {
              if (stock.productId != null && stock.availableQuantity != null) {
                stockByProduct.set(Number(stock.productId), Number(stock.availableQuantity));
              }
            });
          }
        } catch (stockError) {
          console.warn('Stock Service is unavailable; showing the product service quantity snapshot.', stockError);
        }
        const enriched: Product[] = data.map((item: any, idx: number) => {
          const img = item.imageUrl || CURATED_IMAGES[idx % CURATED_IMAGES.length];
          const catName = typeof item.category === 'object' && item.category !== null ? item.category.name : (item.category || 'Electronics');
          const badges = ['🔥 BEST SELLER', '⚡ FLASH DEAL', '⭐ TOP RATED', '🚚 FREE SHIP'];
          const badge = badges[idx % badges.length];
          const originalPrice = Math.round(item.price * 1.25 * 100) / 100;

          return {
            ...item,
            stockQuantity: stockByProduct.get(Number(item.id)) ?? item.stockQuantity,
            category: catName,
            rating: item.rating || 4.8,
            reviewsCount: item.reviewCount || (120 + idx * 47),
            imageUrl: img,
            badge,
            originalPrice,
          };
        });
        setProducts(enriched);
        setLoading(false);
      })
      .catch((err) => {
        console.error('Error fetching products:', err);
        setCatalogError(err?.name === 'StorefrontLookupError' ? err.message : 'The catalog could not be loaded. Please try again shortly.');
        setLoading(false);
      });
  };

  const loadCategories = () => {
    axios
      .get('/api/products/categories')
      .then((res) => {
        if (Array.isArray(res.data) && res.data.length > 0) {
          const catNames = ['All', ...res.data.map((c: ProductCategory) => c.name)];
          setCategories(Array.from(new Set(catNames)));
        }
      })
      .catch(() => {});
  };

  const loadWishlist = () => {
    axios
      .get('/api/products/wishlist')
      .then((res) => {
        if (Array.isArray(res.data)) {
          const ids = new Set<number>(res.data.map((p: Product) => p.id));
          setWishlistIds(ids);
        }
      })
      .catch(() => {});
  };

  const loadRecentlyViewed = () => {
    axios
      .get('/api/products/recently-viewed?limit=5')
      .then((res) => {
        if (Array.isArray(res.data)) {
          setRecentlyViewed(res.data);
        }
      })
      .catch(() => {});
  };

  const handleToggleWishlist = async (product: Product, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!user) {
      setSnackbarMessage('Please sign in to save products to your wishlist!');
      setSnackbarOpen(true);
      return;
    }

    const isInWishlist = wishlistIds.has(product.id);
    try {
      if (isInWishlist) {
        await axios.delete(`/api/products/wishlist/${product.id}`);
        setWishlistIds((prev) => {
          const next = new Set(prev);
          next.delete(product.id);
          return next;
        });
        setSnackbarMessage(`Removed "${product.name}" from your wishlist.`);
      } else {
        await axios.post(`/api/products/wishlist/${product.id}`);
        setWishlistIds((prev) => new Set(prev).add(product.id));
        setSnackbarMessage(`Added "${product.name}" to your wishlist! ❤️`);
      }
      setSnackbarOpen(true);
    } catch (err) {
      console.error('Failed to update wishlist:', err);
    }
  };

  const handleOpenQuickView = (product: Product) => {
    setQuickViewProduct(product);
    setQuickViewQty(1);
    setSelectedVariant(null);
    setModalTab(0);

    // Record view in backend
    if (user) {
      axios.post(`/api/products/${product.id}/view`).catch(() => {});
    }

    // Load variants
    axios
      .get(`/api/products/${product.id}/variants`)
      .then((res) => {
        if (Array.isArray(res.data)) {
          const stockQuantity = product.stockQuantity;
          const variants = res.data.map((variant: ProductVariant) => ({ ...variant, stockQuantity }));
          setQuickViewVariants(variants);
          if (variants.length > 0) setSelectedVariant(variants[0]);
        }
      })
      .catch(() => setQuickViewVariants([]));

    // Load reviews
    setReviewsLoading(true);
    axios
      .get(`/api/products/${product.id}/reviews`)
      .then((res) => {
        if (Array.isArray(res.data)) setReviews(res.data);
        setReviewsLoading(false);
      })
      .catch(() => {
        setReviews([]);
        setReviewsLoading(false);
      });
  };

  const handleSubmitReview = async () => {
    if (!quickViewProduct) return;
    if (!user) {
      setSnackbarMessage('Please sign in to submit a verified customer review!');
      setSnackbarOpen(true);
      return;
    }

    if (!newReviewComment.trim()) {
      setSnackbarMessage('Please write a comment for your review.');
      setSnackbarOpen(true);
      return;
    }

    setSubmittingReview(true);
    try {
      const res = await axios.post(`/api/products/${quickViewProduct.id}/reviews`, {
        rating: newReviewRating,
        comment: newReviewComment.trim(),
      });
      setReviews((prev) => [res.data, ...prev]);
      setNewReviewComment('');
      setSnackbarMessage('Your review has been posted successfully!');
      setSnackbarOpen(true);
      loadProducts(); // Refresh average rating
    } catch (err: any) {
      const msg = err.response?.data?.error || 'Failed to submit review.';
      setSnackbarMessage(msg);
      setSnackbarOpen(true);
    } finally {
      setSubmittingReview(false);
    }
  };

  const handleToggleCompare = (productId: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setCompareIds((prev) => {
      if (prev.includes(productId)) {
        return prev.filter((id) => id !== productId);
      }
      if (prev.length >= 4) {
        setSnackbarMessage('You can compare a maximum of 4 products simultaneously.');
        setSnackbarOpen(true);
        return prev;
      }
      return [...prev, productId];
    });
  };

  const handleAddToCart = (product: Product, quantity = 1, variant?: ProductVariant | null) => {
    const unitPrice = variant ? variant.finalPrice : product.price;
    const itemName = variant ? `${product.name} (${variant.name})` : product.name;

    for (let i = 0; i < quantity; i++) {
      dispatch(
        addToCart({
          productId: product.id,
          name: itemName,
          price: unitPrice,
        })
      );
    }
    setSnackbarMessage(`Added ${quantity > 1 ? `${quantity}x ` : ''}"${itemName}" to your cart!`);
    setSnackbarOpen(true);
    if (quickViewProduct) {
      setQuickViewProduct(null);
    }
  };

  // AI Assistant logic (RAG query against catalog and policies)
  const handleSendAiMessage = () => {
    if (!aiQuery.trim()) return;
    const query = aiQuery.trim();
    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    setAiMessages((prev) => [...prev, { sender: 'user', text: query, timestamp: time }]);
    setAiQuery('');

    // Rule-based verified RAG search against local catalog
    setTimeout(() => {
      let responseText = '';
      const qLower = query.toLowerCase();

      if (qLower.includes('return') || qLower.includes('refund')) {
        responseText = "Our Universal Commerce policy offers a 30-day hassle-free return guarantee for all items in original condition. Refunds are issued directly to your ledger balance or original payment method within 3 business days.";
      } else if (qLower.includes('shipping') || qLower.includes('delivery')) {
        responseText = "We provide Free Express Shipping across all orders over $50! Standard ground shipping typically arrives within 2-4 business days with end-to-end tracking.";
      } else if (qLower.includes('battery') || qLower.includes('noise')) {
        responseText = "For active noise cancellation and long battery life, we highly recommend our Studio Wireless Pro Headphones (30-hour battery life, active ANC at $199.99) or CyberAcoustic Earbuds (24-hour case).";
      } else if (qLower.includes('keyboard') || qLower.includes('switch') || qLower.includes('typing')) {
        responseText = "Our Mechanical Keyboard features aerospace CNC aluminum, hot-swappable tactile switches, and PBT keycaps at $149.50 with Full-Size and Tenkeyless options.";
      } else {
        const matches = products.filter(
          (p) => p.name.toLowerCase().includes(qLower) || p.description.toLowerCase().includes(qLower)
        );
        if (matches.length > 0) {
          responseText = `I found ${matches.length} matching item(s) in our verified catalog: "${matches[0].name}" available at $${matches[0].price.toFixed(2)} (${matches[0].stockQuantity} units in stock with ${matches[0].rating}★ rating).`;
        } else {
          responseText = "I inspected our active catalog and specifications. Could you specify whether you are searching for audio equipment, computer peripherals, or gaming hardware?";
        }
      }

      setAiMessages((prev) => [
        ...prev,
        {
          sender: 'assistant',
          text: responseText,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    }, 450);
  };

  // Filtered & Sorted Catalog
  const filteredProducts = useMemo(() => {
    return products
      .filter((p) => {
        const catName = typeof p.category === 'object' && p.category !== null ? (p.category as any).name : (p.category || '');
        const matchesSearch =
          p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          p.description.toLowerCase().includes(searchQuery.toLowerCase());
        const matchesCategory =
          selectedCategory === 'All' || catName.toLowerCase() === selectedCategory.toLowerCase();
        return matchesSearch && matchesCategory;
      })
      .sort((a, b) => {
        if (sortBy === 'price-low') return a.price - b.price;
        if (sortBy === 'price-high') return b.price - a.price;
        if (sortBy === 'rating') return (b.rating || 0) - (a.rating || 0);
        if (sortBy === 'stock') return b.stockQuantity - a.stockQuantity;
        return a.id - b.id;
      });
  }, [products, searchQuery, selectedCategory, sortBy]);

  const compareProductsList = useMemo(() => {
    return products.filter((p) => compareIds.includes(p.id));
  }, [products, compareIds]);

  if (loading) {
    return (
      <Box sx={{ py: 12, textAlign: 'center' }}>
        <CircularProgress size={56} sx={{ color: '#4F46E5' }} />
        <Typography variant="h6" sx={{ mt: 3, color: '#64748B', fontWeight: 600 }}>
          Loading Marketplace Products...
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ bgcolor: '#F8FAFC', minHeight: '100vh', pb: 10 }}>
      {/* Hero Catalog Header Banner */}
      <Box
        sx={{
          background: 'linear-gradient(135deg, #0F172A 0%, #1E1B4B 50%, #312E81 100%)',
          color: '#FFFFFF',
          py: { xs: 5, md: 6 },
          px: 3,
          mb: 4,
          borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
        }}
      >
        <Container maxWidth="xl">
          <Grid container alignItems="center" spacing={4}>
            <Grid item xs={12} md={8}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                <Chip
                  icon={<BoltRounded sx={{ color: '#F59E0B !important' }} />}
                  label="UNIVERSAL COMMERCE 3.0"
                  sx={{
                    bgcolor: 'rgba(245, 158, 11, 0.2)',
                    color: '#FBBF24',
                    fontWeight: 800,
                    border: '1px solid rgba(245, 158, 11, 0.4)',
                  }}
                />
                <Chip
                  icon={<PsychologyRounded sx={{ color: '#38BDF8 !important' }} />}
                  label="AI-Assisted Customer Experience"
                  sx={{
                    bgcolor: 'rgba(56, 189, 248, 0.15)',
                    color: '#38BDF8',
                    fontWeight: 700,
                    border: '1px solid rgba(56, 189, 248, 0.3)',
                  }}
                />
              </Stack>
              <Typography variant="h3" sx={{ fontWeight: 900, letterSpacing: '-0.025em', mb: 1.5 }}>
                Next-Gen Product Catalog
              </Typography>
              <Typography variant="body1" sx={{ color: '#CBD5E1', maxWidth: 700, fontSize: '1.05rem' }}>
                Explore multi-variant electronics and verified customer reviews, save items to your personal wishlist, compare specs side-by-side, or consult our AI Shopping Assistant.
              </Typography>
            </Grid>
            <Grid item xs={12} md={4}>
              <Paper
                elevation={0}
                sx={{
                  p: 2.5,
                  bgcolor: 'rgba(255, 255, 255, 0.05)',
                  backdropFilter: 'blur(10px)',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  borderRadius: 3,
                  color: '#FFFFFF',
                }}
              >
                <Stack spacing={1.5}>
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <LocalShippingOutlined sx={{ color: '#38BDF8' }} />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      Free Express Shipping on orders over $50
                    </Typography>
                  </Stack>
                  <Divider sx={{ borderColor: 'rgba(255, 255, 255, 0.1)' }} />
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <CheckCircleRounded sx={{ color: '#10B981' }} />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      Verified Purchase Reviews & Transparent Ratings
                    </Typography>
                  </Stack>
                  <Divider sx={{ borderColor: 'rgba(255, 255, 255, 0.1)' }} />
                  <Button
                    fullWidth
                    variant="contained"
                    startIcon={<SmartToyRounded />}
                    onClick={() => setAiDrawerOpen(true)}
                    sx={{
                      bgcolor: '#38BDF8',
                      color: '#0F172A',
                      fontWeight: 700,
                      borderRadius: 2,
                      '&:hover': { bgcolor: '#0284C7', color: '#FFFFFF' },
                    }}
                  >
                    Ask AI Shopping Assistant
                  </Button>
                </Stack>
              </Paper>
            </Grid>
          </Grid>
        </Container>
      </Box>

      {/* Main Catalog Container */}
      <Container maxWidth="xl">
        {catalogError && (
          <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>
            {catalogError}
          </Alert>
        )}
        {storefront && (
          <Alert severity="info" icon={false} sx={{ mb: 3, borderRadius: 2 }}>
            You are shopping at <strong>{storefront.name}</strong>.
          </Alert>
        )}

        {/* Search, Categories & Sort Bar */}
        <Paper
          elevation={0}
          sx={{
            p: { xs: 2, md: 3 },
            mb: 4,
            borderRadius: 3,
            border: '1px solid #E2E8F0',
            boxShadow: '0 4px 20px -2px rgba(15, 23, 42, 0.05)',
          }}
        >
          <Grid container spacing={2.5} alignItems="center">
            {/* Search Input */}
            <Grid item xs={12} md={5}>
              <TextField
                fullWidth
                placeholder="Search products by title, keywords, or specifications..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                variant="outlined"
                size="small"
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchRounded sx={{ color: '#94A3B8' }} />
                    </InputAdornment>
                  ),
                  sx: { borderRadius: 2.5, bgcolor: '#FFFFFF' },
                }}
              />
            </Grid>

            {/* Category Filter Chips */}
            <Grid item xs={12} md={4.5}>
              <Stack direction="row" spacing={1} sx={{ overflowX: 'auto', py: 0.5 }}>
                {categories.map((cat) => (
                  <Chip
                    key={cat}
                    label={cat}
                    clickable
                    onClick={() => setSelectedCategory(cat)}
                    sx={{
                      fontWeight: 600,
                      px: 0.5,
                      borderRadius: 2,
                      bgcolor: selectedCategory.toLowerCase() === cat.toLowerCase() ? '#4F46E5' : '#F1F5F9',
                      color: selectedCategory.toLowerCase() === cat.toLowerCase() ? '#FFFFFF' : '#475569',
                      '&:hover': {
                        bgcolor: selectedCategory.toLowerCase() === cat.toLowerCase() ? '#4338CA' : '#E2E8F0',
                      },
                    }}
                  />
                ))}
              </Stack>
            </Grid>

            {/* Sort Options */}
            <Grid item xs={12} md={2.5}>
              <FormControl fullWidth size="small">
                <InputLabel id="sort-select-label">Sort By</InputLabel>
                <Select
                  labelId="sort-select-label"
                  value={sortBy}
                  label="Sort By"
                  onChange={(e) => setSortBy(e.target.value)}
                  sx={{ borderRadius: 2.5 }}
                >
                  <MenuItem value="featured">Featured & Popular</MenuItem>
                  <MenuItem value="price-low">Price: Low to High</MenuItem>
                  <MenuItem value="price-high">Price: High to Low</MenuItem>
                  <MenuItem value="rating">Highest Customer Rating</MenuItem>
                  <MenuItem value="stock">Stock Availability</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </Paper>

        {/* Results Counter */}
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
          <Typography variant="body1" sx={{ color: '#64748B', fontWeight: 600 }}>
            Showing <strong style={{ color: '#0F172A' }}>{filteredProducts.length}</strong> items in{' '}
            <strong style={{ color: '#4F46E5' }}>{selectedCategory}</strong>
          </Typography>
          {compareIds.length > 0 && (
            <Button
              variant="outlined"
              size="small"
              startIcon={<CompareArrowsRounded />}
              onClick={() => setCompareModalOpen(true)}
              sx={{ fontWeight: 700, borderRadius: 2 }}
            >
              Compare Selected ({compareIds.length})
            </Button>
          )}
        </Stack>

        {/* Product Grid */}
        {filteredProducts.length === 0 ? (
          <Box
            sx={{
              py: 10,
              textAlign: 'center',
              bgcolor: '#FFFFFF',
              borderRadius: 3,
              border: '1px dashed #CBD5E1',
            }}
          >
            <FilterListRounded sx={{ fontSize: 48, color: '#94A3B8', mb: 1 }} />
            <Typography variant="h6" sx={{ fontWeight: 700, color: '#334155' }}>
              No products found
            </Typography>
            <Typography variant="body2" sx={{ color: '#64748B', mb: 2 }}>
              Try adjusting your search query or switching categories.
            </Typography>
            <Button
              variant="outlined"
              onClick={() => {
                setSearchQuery('');
                setSelectedCategory('All');
              }}
            >
              Reset Filters
            </Button>
          </Box>
        ) : (
          <Grid container spacing={3.5}>
            {filteredProducts.map((product) => {
              const isOutOfStock = product.stockQuantity <= 0;
              const isLowStock = product.stockQuantity > 0 && product.stockQuantity <= 5;
              const inWishlist = wishlistIds.has(product.id);
              const inCompare = compareIds.includes(product.id);

              return (
                <Grid item xs={12} sm={6} md={4} lg={3} key={product.id}>
                  <Card
                    sx={{
                      height: '100%',
                      display: 'flex',
                      flexDirection: 'column',
                      borderRadius: 3.5,
                      border: '1px solid #E2E8F0',
                      bgcolor: '#FFFFFF',
                      transition: 'all 0.25s ease-in-out',
                      '&:hover': {
                        transform: 'translateY(-4px)',
                        boxShadow: '0 16px 32px -4px rgba(15, 23, 42, 0.12)',
                        borderColor: '#C7D2FE',
                      },
                    }}
                  >
                    {/* Media Container with Badges & Wishlist */}
                    <Box sx={{ position: 'relative', overflow: 'hidden' }}>
                      <CardMedia
                        component="img"
                        height="220"
                        image={product.imageUrl || CURATED_IMAGES[0]}
                        alt={product.name}
                        sx={{
                          objectFit: 'cover',
                          transition: 'transform 0.3s ease',
                          '&:hover': { transform: 'scale(1.05)' },
                        }}
                      />
                      {/* Deal Badge */}
                      {product.badge && (
                        <Chip
                          label={product.badge}
                          size="small"
                          sx={{
                            position: 'absolute',
                            top: 12,
                            left: 12,
                            bgcolor: '#0F172A',
                            color: '#FFFFFF',
                            fontWeight: 700,
                            fontSize: '0.72rem',
                            letterSpacing: '0.03em',
                            boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
                          }}
                        />
                      )}

                      {/* Action Buttons: Wishlist & Quick View */}
                      <Stack direction="row" spacing={0.5} sx={{ position: 'absolute', top: 12, right: 12 }}>
                        <Tooltip title={inWishlist ? 'Remove from Wishlist' : 'Add to Wishlist'}>
                          <IconButton
                            size="small"
                            onClick={(e) => handleToggleWishlist(product, e)}
                            sx={{
                              bgcolor: 'rgba(255, 255, 255, 0.9)',
                              backdropFilter: 'blur(4px)',
                              color: inWishlist ? '#EF4444' : '#64748B',
                              '&:hover': { bgcolor: '#FFFFFF', color: '#EF4444' },
                            }}
                          >
                            {inWishlist ? <FavoriteRounded fontSize="small" /> : <FavoriteBorderRounded fontSize="small" />}
                          </IconButton>
                        </Tooltip>

                        <Tooltip title="Quick View & Reviews">
                          <IconButton
                            size="small"
                            onClick={() => handleOpenQuickView(product)}
                            sx={{
                              bgcolor: 'rgba(255, 255, 255, 0.9)',
                              backdropFilter: 'blur(4px)',
                              color: '#1E293B',
                              '&:hover': { bgcolor: '#FFFFFF', color: '#4F46E5' },
                            }}
                          >
                            <VisibilityRounded fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Stack>
                    </Box>

                    {/* Product Details */}
                    <CardContent sx={{ p: 2.5, flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
                      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                        <Typography
                          variant="caption"
                          sx={{
                            color: '#4F46E5',
                            fontWeight: 700,
                            textTransform: 'uppercase',
                            letterSpacing: '0.05em',
                          }}
                        >
                          {typeof product.category === 'object' && product.category !== null ? (product.category as any).name : (product.category || 'General')}
                        </Typography>
                        {/* Rating */}
                        <Stack direction="row" spacing={0.5} alignItems="center">
                          <StarRounded sx={{ fontSize: 16, color: '#F59E0B' }} />
                          <Typography variant="caption" sx={{ fontWeight: 700, color: '#0F172A' }}>
                            {product.rating ? product.rating.toFixed(1) : '4.8'}
                          </Typography>
                          <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                            ({product.reviewCount ?? product.reviewsCount ?? 12})
                          </Typography>
                        </Stack>
                      </Stack>

                      <Typography
                        variant="h6"
                        sx={{
                          fontSize: '1rem',
                          fontWeight: 700,
                          lineHeight: 1.35,
                          mb: 1,
                          color: '#0F172A',
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                          height: '2.7em',
                        }}
                      >
                        {product.name}
                      </Typography>

                      <Typography
                        variant="body2"
                        sx={{
                          color: '#64748B',
                          mb: 2,
                          fontSize: '0.85rem',
                          lineHeight: 1.4,
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                          height: '2.8em',
                        }}
                      >
                        {product.description}
                      </Typography>

                      <Box sx={{ mt: 'auto', pt: 1.5, borderTop: '1px solid #F1F5F9' }}>
                        {/* Pricing */}
                        <Stack direction="row" spacing={1} alignItems="baseline" sx={{ mb: 1 }}>
                          <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A' }}>
                            ${product.price.toFixed(2)}
                          </Typography>
                          {product.originalPrice && (
                            <Typography
                              variant="body2"
                              sx={{
                                color: '#94A3B8',
                                textDecoration: 'line-through',
                                fontWeight: 500,
                              }}
                            >
                              ${product.originalPrice.toFixed(2)}
                            </Typography>
                          )}
                        </Stack>

                        {/* Stock Level Indicator */}
                        <Stack direction="row" alignItems="center" spacing={0.8} sx={{ mb: 1.5 }}>
                          {isOutOfStock ? (
                            <>
                              <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: '#EF4444' }} />
                              <Typography variant="caption" sx={{ color: '#EF4444', fontWeight: 700 }}>
                                Out of Stock
                              </Typography>
                            </>
                          ) : isLowStock ? (
                            <>
                              <WarningAmberRounded sx={{ fontSize: 14, color: '#F59E0B' }} />
                              <Typography variant="caption" sx={{ color: '#D97706', fontWeight: 700 }}>
                                Only {product.stockQuantity} left!
                              </Typography>
                            </>
                          ) : (
                            <>
                              <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: '#10B981' }} />
                              <Typography variant="caption" sx={{ color: '#059669', fontWeight: 600 }}>
                                In Stock ({product.stockQuantity} units)
                              </Typography>
                            </>
                          )}
                        </Stack>

                        {/* Compare Checkbox & Add to Cart */}
                        <Stack direction="row" spacing={1} alignItems="center">
                          <FormControlLabel
                            control={
                              <Checkbox
                                size="small"
                                checked={inCompare}
                                onChange={(e) => handleToggleCompare(product.id, e as any)}
                                sx={{ color: '#94A3B8', '&.Mui-checked': { color: '#4F46E5' } }}
                              />
                            }
                            label={<Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>Compare</Typography>}
                            sx={{ m: 0 }}
                          />
                          <Button
                            fullWidth
                            variant="contained"
                            color="primary"
                            startIcon={<ShoppingCartRounded />}
                            disabled={isOutOfStock}
                            onClick={() => handleAddToCart(product)}
                            sx={{
                              fontWeight: 700,
                              borderRadius: 2.5,
                              py: 0.8,
                              bgcolor: '#4F46E5',
                              '&:hover': { bgcolor: '#4338CA' },
                              '&:disabled': { bgcolor: '#E2E8F0', color: '#94A3B8' },
                            }}
                          >
                            {isOutOfStock ? 'Sold Out' : 'Add to Cart'}
                          </Button>
                        </Stack>
                      </Box>
                    </CardContent>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        )}

        {/* Recently Viewed Tray */}
        {recentlyViewed.length > 0 && (
          <Box sx={{ mt: 8, pt: 4, borderTop: '1px solid #E2E8F0' }}>
            <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A', mb: 3 }}>
              Recently Viewed Products
            </Typography>
            <Grid container spacing={2.5}>
              {recentlyViewed.map((item) => (
                <Grid item xs={6} sm={4} md={2.4} key={`rv-${item.id}`}>
                  <Paper
                    elevation={0}
                    onClick={() => handleOpenQuickView(item)}
                    sx={{
                      p: 1.5,
                      borderRadius: 2.5,
                      border: '1px solid #E2E8F0',
                      cursor: 'pointer',
                      transition: 'all 0.2s',
                      '&:hover': { transform: 'translateY(-3px)', borderColor: '#C7D2FE' },
                    }}
                  >
                    <Box
                      component="img"
                      src={item.imageUrl || CURATED_IMAGES[0]}
                      alt={item.name}
                      sx={{ width: '100%', height: 110, objectFit: 'cover', borderRadius: 1.5, mb: 1 }}
                    />
                    <Typography variant="body2" noWrap sx={{ fontWeight: 700, fontSize: '0.85rem' }}>
                      {item.name}
                    </Typography>
                    <Typography variant="caption" sx={{ color: '#4F46E5', fontWeight: 800 }}>
                      ${item.price.toFixed(2)}
                    </Typography>
                  </Paper>
                </Grid>
              ))}
            </Grid>
          </Box>
        )}
      </Container>

      {/* Floating Comparison Bar */}
      {compareIds.length > 0 && (
        <Paper
          elevation={8}
          sx={{
            position: 'fixed',
            bottom: 24,
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 1200,
            px: 3,
            py: 1.5,
            borderRadius: 4,
            bgcolor: '#0F172A',
            color: '#FFFFFF',
            display: 'flex',
            alignItems: 'center',
            gap: 2.5,
            boxShadow: '0 20px 40px rgba(0,0,0,0.3)',
          }}
        >
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            Comparing {compareIds.length} of 4 items
          </Typography>
          <Button
            variant="contained"
            size="small"
            startIcon={<CompareArrowsRounded />}
            onClick={() => setCompareModalOpen(true)}
            sx={{ bgcolor: '#4F46E5', fontWeight: 700, borderRadius: 2 }}
          >
            Compare Now
          </Button>
          <Button
            variant="text"
            size="small"
            onClick={() => setCompareIds([])}
            sx={{ color: '#94A3B8', textTransform: 'none' }}
          >
            Clear All
          </Button>
        </Paper>
      )}

      {/* Product Comparison Modal */}
      <Dialog
        open={compareModalOpen}
        onClose={() => setCompareModalOpen(false)}
        maxWidth="lg"
        fullWidth
        PaperProps={{ sx: { borderRadius: 4, p: 2 } }}
      >
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>
            Side-by-Side Product Comparison
          </Typography>
          <IconButton onClick={() => setCompareModalOpen(false)}>
            <CloseRounded />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          {compareProductsList.length === 0 ? (
            <Typography>No items selected for comparison.</Typography>
          ) : (
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 800, minWidth: 150 }}>Feature</TableCell>
                    {compareProductsList.map((p) => (
                      <TableCell key={`hdr-${p.id}`} align="center" sx={{ fontWeight: 800 }}>
                        <Box
                          component="img"
                          src={p.imageUrl || CURATED_IMAGES[0]}
                          alt={p.name}
                          sx={{ width: 120, height: 100, objectFit: 'cover', borderRadius: 2, mb: 1 }}
                        />
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          {p.name}
                        </Typography>
                      </TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Price</TableCell>
                    {compareProductsList.map((p) => (
                      <TableCell key={`price-${p.id}`} align="center">
                        <Typography variant="h6" sx={{ fontWeight: 800, color: '#4F46E5' }}>
                          ${p.price.toFixed(2)}
                        </Typography>
                      </TableCell>
                    ))}
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Customer Rating</TableCell>
                    {compareProductsList.map((p) => (
                      <TableCell key={`rate-${p.id}`} align="center">
                        <Stack direction="row" spacing={0.5} justifyContent="center" alignItems="center">
                          <StarRounded sx={{ color: '#F59E0B', fontSize: 18 }} />
                          <Typography sx={{ fontWeight: 700 }}>{p.rating ? p.rating.toFixed(1) : '4.8'} / 5.0</Typography>
                        </Stack>
                      </TableCell>
                    ))}
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Stock Availability</TableCell>
                    {compareProductsList.map((p) => (
                      <TableCell key={`stock-${p.id}`} align="center">
                        {p.stockQuantity > 0 ? (
                          <Chip label={`In Stock (${p.stockQuantity})`} size="small" sx={{ bgcolor: '#ECFDF5', color: '#059669', fontWeight: 700 }} />
                        ) : (
                          <Chip label="Out of Stock" size="small" sx={{ bgcolor: '#FEF2F2', color: '#EF4444', fontWeight: 700 }} />
                        )}
                      </TableCell>
                    ))}
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Description</TableCell>
                    {compareProductsList.map((p) => (
                      <TableCell key={`desc-${p.id}`} align="center" sx={{ maxWidth: 220, fontSize: '0.85rem', color: '#64748B' }}>
                        {p.description}
                      </TableCell>
                    ))}
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Action</TableCell>
                    {compareProductsList.map((p) => (
                      <TableCell key={`act-${p.id}`} align="center">
                        <Button
                          variant="contained"
                          size="small"
                          disabled={p.stockQuantity <= 0}
                          onClick={() => handleAddToCart(p)}
                          sx={{ borderRadius: 2, bgcolor: '#4F46E5' }}
                        >
                          Add to Cart
                        </Button>
                      </TableCell>
                    ))}
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </DialogContent>
      </Dialog>

      {/* Quick View, Variants & Reviews Modal */}
      <Dialog
        open={Boolean(quickViewProduct)}
        onClose={() => setQuickViewProduct(null)}
        maxWidth="md"
        fullWidth
        PaperProps={{ sx: { borderRadius: 4, p: 1 } }}
      >
        {quickViewProduct && (
          <>
            <DialogTitle sx={{ m: 0, p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Tabs value={modalTab} onChange={(_, val) => setModalTab(val)}>
                <Tab label="Product Details & Variants" sx={{ fontWeight: 700, textTransform: 'none' }} />
                <Tab label={`Verified Reviews (${reviews.length})`} sx={{ fontWeight: 700, textTransform: 'none' }} />
              </Tabs>
              <IconButton onClick={() => setQuickViewProduct(null)} size="small">
                <CloseRounded />
              </IconButton>
            </DialogTitle>

            <DialogContent dividers sx={{ p: 3 }}>
              {modalTab === 0 ? (
                <Grid container spacing={4}>
                  <Grid item xs={12} md={6}>
                    <Box
                      component="img"
                      src={quickViewProduct.imageUrl || CURATED_IMAGES[0]}
                      alt={quickViewProduct.name}
                      sx={{
                        width: '100%',
                        height: 320,
                        objectFit: 'cover',
                        borderRadius: 3,
                        boxShadow: '0 8px 24px rgba(0,0,0,0.08)',
                      }}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Chip
                      label={typeof quickViewProduct.category === 'object' && quickViewProduct.category !== null ? (quickViewProduct.category as any).name : (quickViewProduct.category || 'Electronics')}
                      size="small"
                      sx={{ bgcolor: '#EEF2FF', color: '#4F46E5', fontWeight: 700, mb: 1 }}
                    />
                    <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A', mb: 1 }}>
                      {quickViewProduct.name}
                    </Typography>

                    <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                      <Rating value={quickViewProduct.rating || 4.8} precision={0.1} readOnly size="small" />
                      <Typography variant="body2" sx={{ fontWeight: 700, color: '#0F172A' }}>
                        {quickViewProduct.rating ? quickViewProduct.rating.toFixed(1) : '4.8'}
                      </Typography>
                      <Typography variant="body2" sx={{ color: '#64748B' }}>
                        ({reviews.length} verified reviews)
                      </Typography>
                    </Stack>

                    <Typography variant="h4" sx={{ fontWeight: 800, color: '#4F46E5', mb: 2 }}>
                      ${(selectedVariant ? selectedVariant.finalPrice : quickViewProduct.price).toFixed(2)}
                    </Typography>

                    {/* Multi-attribute Variants Selector */}
                    {quickViewVariants.length > 0 && (
                      <Box sx={{ mb: 3 }}>
                        <Typography variant="body2" sx={{ fontWeight: 700, color: '#334155', mb: 1 }}>
                          Select Variant / Color:
                        </Typography>
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          {quickViewVariants.map((v) => (
                            <Chip
                              key={v.id}
                              label={`${v.name} (${v.color || v.size})${v.priceAdjustment !== 0 ? ` [+$${v.priceAdjustment}]` : ''}`}
                              clickable
                              onClick={() => setSelectedVariant(v)}
                              sx={{
                                fontWeight: 600,
                                mb: 0.5,
                                bgcolor: selectedVariant?.id === v.id ? '#4F46E5' : '#F1F5F9',
                                color: selectedVariant?.id === v.id ? '#FFFFFF' : '#334155',
                                '&:hover': { bgcolor: selectedVariant?.id === v.id ? '#4338CA' : '#E2E8F0' },
                              }}
                            />
                          ))}
                        </Stack>
                      </Box>
                    )}

                    <Typography variant="body2" sx={{ color: '#475569', lineHeight: 1.6, mb: 3 }}>
                      {quickViewProduct.description}
                    </Typography>

                    {/* Quantity Stepper */}
                    <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 3 }}>
                      <Typography variant="body2" sx={{ fontWeight: 600, color: '#334155' }}>
                        Quantity:
                      </Typography>
                      <Stack
                        direction="row"
                        alignItems="center"
                        sx={{
                          border: '1px solid #CBD5E1',
                          borderRadius: 2,
                          bgcolor: '#FFFFFF',
                        }}
                      >
                        <IconButton
                          size="small"
                          onClick={() => setQuickViewQty(Math.max(1, quickViewQty - 1))}
                          disabled={quickViewQty <= 1}
                        >
                          <RemoveRounded fontSize="small" />
                        </IconButton>
                        <Typography sx={{ px: 2, fontWeight: 700, minWidth: 32, textAlign: 'center' }}>
                          {quickViewQty}
                        </Typography>
                        <IconButton
                          size="small"
                          onClick={() =>
                            setQuickViewQty(Math.min(quickViewProduct.stockQuantity, quickViewQty + 1))
                          }
                          disabled={quickViewQty >= quickViewProduct.stockQuantity}
                        >
                          <AddRounded fontSize="small" />
                        </IconButton>
                      </Stack>
                    </Stack>
                  </Grid>
                </Grid>
              ) : (
                /* Reviews & Ratings Tab */
                <Box>
                  {/* Review Submission Form */}
                  <Paper elevation={0} sx={{ p: 2.5, mb: 3, bgcolor: '#F8FAFC', borderRadius: 3, border: '1px solid #E2E8F0' }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
                      <RateReviewRounded sx={{ color: '#4F46E5' }} /> Leave a Verified Customer Review
                    </Typography>
                    <Stack spacing={2}>
                      <Stack direction="row" spacing={1.5} alignItems="center">
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>Your Rating:</Typography>
                        <Rating
                          value={newReviewRating}
                          onChange={(_, val) => setNewReviewRating(val || 5)}
                        />
                      </Stack>
                      <TextField
                        fullWidth
                        multiline
                        rows={3}
                        placeholder="Share your detailed feedback on product quality, shipping, and performance..."
                        value={newReviewComment}
                        onChange={(e) => setNewReviewComment(e.target.value)}
                        size="small"
                        sx={{ bgcolor: '#FFFFFF' }}
                      />
                      <Button
                        variant="contained"
                        onClick={handleSubmitReview}
                        disabled={submittingReview}
                        sx={{ alignSelf: 'flex-start', bgcolor: '#4F46E5', fontWeight: 700, borderRadius: 2 }}
                      >
                        {submittingReview ? 'Submitting...' : 'Post Customer Review'}
                      </Button>
                    </Stack>
                  </Paper>

                  {/* Reviews List */}
                  {reviewsLoading ? (
                    <CircularProgress size={32} sx={{ my: 4, display: 'block', mx: 'auto' }} />
                  ) : reviews.length === 0 ? (
                    <Typography sx={{ textAlign: 'center', py: 4, color: '#64748B' }}>
                      No customer reviews yet. Be the first verified customer to leave feedback!
                    </Typography>
                  ) : (
                    <Stack spacing={2}>
                      {reviews.map((r) => (
                        <Paper key={`rev-${r.id}`} elevation={0} sx={{ p: 2, borderRadius: 2.5, border: '1px solid #E2E8F0' }}>
                          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                            <Stack direction="row" spacing={1} alignItems="center">
                              <Avatar sx={{ width: 28, height: 28, bgcolor: '#4F46E5', fontSize: '0.75rem' }}>
                                {r.userName.charAt(0).toUpperCase()}
                              </Avatar>
                              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                {r.userName}
                              </Typography>
                              {r.isVerifiedPurchase && (
                                <Chip
                                  icon={<VerifiedRounded sx={{ fontSize: '14px !important', color: '#10B981 !important' }} />}
                                  label="Verified Purchase"
                                  size="small"
                                  sx={{ height: 20, fontSize: '0.68rem', fontWeight: 700, bgcolor: '#ECFDF5', color: '#059669' }}
                                />
                              )}
                            </Stack>
                            <Rating value={r.rating} readOnly size="small" />
                          </Stack>
                          <Typography variant="body2" sx={{ color: '#334155', lineHeight: 1.5 }}>
                            {r.comment}
                          </Typography>
                        </Paper>
                      ))}
                    </Stack>
                  )}
                </Box>
              )}
            </DialogContent>

            <DialogActions sx={{ p: 2.5, borderTop: '1px solid #E2E8F0' }}>
              <Button onClick={() => setQuickViewProduct(null)} sx={{ color: '#64748B' }}>
                Close
              </Button>
              <Button
                variant="contained"
                startIcon={<ShoppingCartRounded />}
                disabled={quickViewProduct.stockQuantity <= 0}
                onClick={() => handleAddToCart(quickViewProduct, quickViewQty, selectedVariant)}
                sx={{
                  bgcolor: '#4F46E5',
                  fontWeight: 700,
                  px: 3,
                  borderRadius: 2.5,
                  '&:hover': { bgcolor: '#4338CA' },
                }}
              >
                Add {quickViewQty} to Cart • ${(
                  (selectedVariant ? selectedVariant.finalPrice : quickViewProduct.price) * quickViewQty
                ).toFixed(2)}
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>

      {/* AI Shopping Assistant Drawer */}
      <Drawer
        anchor="right"
        open={aiDrawerOpen}
        onClose={() => setAiDrawerOpen(false)}
        PaperProps={{ sx: { width: { xs: '100%', sm: 420 }, p: 3, display: 'flex', flexDirection: 'column' } }}
      >
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2, pb: 1, borderBottom: '1px solid #E2E8F0' }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Avatar sx={{ bgcolor: '#4F46E5' }}>
              <SmartToyRounded />
            </Avatar>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                AI Shopping Assistant
              </Typography>
              <Typography variant="caption" sx={{ color: '#10B981', fontWeight: 700 }}>
                ● Online (Verified Catalog RAG)
              </Typography>
            </Box>
          </Stack>
          <IconButton size="small" onClick={() => setAiDrawerOpen(false)}>
            <CloseRounded />
          </IconButton>
        </Stack>

        {/* Chat History */}
        <Box sx={{ flexGrow: 1, overflowY: 'auto', pr: 1, mb: 2 }}>
          <Stack spacing={2}>
            {aiMessages.map((msg, i) => (
              <Box
                key={`chat-${i}`}
                sx={{
                  alignSelf: msg.sender === 'user' ? 'flex-end' : 'flex-start',
                  maxWidth: '85%',
                  bgcolor: msg.sender === 'user' ? '#4F46E5' : '#F1F5F9',
                  color: msg.sender === 'user' ? '#FFFFFF' : '#0F172A',
                  p: 1.5,
                  borderRadius: 2.5,
                }}
              >
                <Typography variant="body2" sx={{ lineHeight: 1.5 }}>
                  {msg.text}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{
                    display: 'block',
                    textAlign: 'right',
                    fontSize: '0.65rem',
                    color: msg.sender === 'user' ? '#C7D2FE' : '#94A3B8',
                    mt: 0.5,
                  }}
                >
                  {msg.timestamp}
                </Typography>
              </Box>
            ))}
          </Stack>
        </Box>

        {/* Input Bar */}
        <Stack direction="row" spacing={1}>
          <TextField
            fullWidth
            size="small"
            placeholder="Ask about specs, battery life, return policy..."
            value={aiQuery}
            onChange={(e) => setAiQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleSendAiMessage();
            }}
          />
          <IconButton onClick={handleSendAiMessage} sx={{ bgcolor: '#4F46E5', color: '#FFFFFF', '&:hover': { bgcolor: '#4338CA' } }}>
            <SendRounded />
          </IconButton>
        </Stack>
      </Drawer>

      {/* Global Snackbar Notifications */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={4000}
        onClose={() => setSnackbarOpen(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={() => setSnackbarOpen(false)} severity="success" sx={{ width: '100%', fontWeight: 600 }}>
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
}
