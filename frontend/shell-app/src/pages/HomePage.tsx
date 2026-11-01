import React from 'react';
import {
  ArrowForwardRounded,
  DevicesRounded,
  HeadphonesRounded,
  HomeWorkRounded,
  LocalShippingRounded,
  LockRounded,
  ShoppingBagRounded,
  ShoppingBasketRounded,
} from '@mui/icons-material';
import {
  Box,
  Button,
  Card,
  CardActionArea,
  Container,
  Grid,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';

const categories = [
  { title: 'Tech & electronics', description: 'Everyday essentials, upgraded.', icon: <DevicesRounded />, color: '#4F46E5', tint: '#EEF2FF' },
  { title: 'Audio & gaming', description: 'Find your next favorite sound.', icon: <HeadphonesRounded />, color: '#7C3AED', tint: '#F5F3FF' },
  { title: 'Style & apparel', description: 'A little something for your look.', icon: <ShoppingBagRounded />, color: '#D97706', tint: '#FFFBEB' },
  { title: 'Home & workspace', description: 'Make your space work for you.', icon: <HomeWorkRounded />, color: '#059669', tint: '#ECFDF5' },
];

export default function HomePage() {
  const { user } = useSelector((state: RootState) => state.auth);
  const firstName = user?.name?.trim().split(/\s+/)[0] || user?.email?.split('@')[0];

  return (
    <Box sx={{ pb: { xs: 5, md: 8 } }}>
      <Container disableGutters maxWidth="xl">
        <Paper
          elevation={0}
          sx={{
            overflow: 'hidden',
            position: 'relative',
            borderRadius: { xs: 4, md: 6 },
            background: 'linear-gradient(112deg, #111827 0%, #1E1B4B 58%, #4338CA 100%)',
            color: 'white',
            px: { xs: 3, sm: 5, md: 8 },
            py: { xs: 5, md: 8 },
            minHeight: { md: 390 },
            display: 'flex',
            alignItems: 'center',
            boxShadow: '0 24px 60px -28px rgba(49, 46, 129, .65)',
          }}
        >
          <Box
            aria-hidden="true"
            sx={{
              position: 'absolute',
              width: { xs: 280, md: 440 },
              height: { xs: 280, md: 440 },
              borderRadius: '50%',
              right: { xs: -130, md: 60 },
              top: { xs: '48%', md: '50%' },
              transform: 'translateY(-50%)',
              background: 'radial-gradient(circle, rgba(129,140,248,.28) 0%, rgba(99,102,241,.12) 45%, transparent 70%)',
            }}
          />
          <Grid container spacing={4} alignItems="center" sx={{ position: 'relative', zIndex: 1 }}>
            <Grid item xs={12} md={7}>
              <Typography
                variant="overline"
                sx={{ color: '#C7D2FE', fontWeight: 800, letterSpacing: '.14em', display: 'block', mb: 1.5 }}
              >
                A marketplace for your everyday
              </Typography>
              <Typography
                component="h1"
                sx={{
                  fontSize: { xs: '2.65rem', sm: '3.35rem', md: '4rem' },
                  lineHeight: 1.04,
                  letterSpacing: '-.045em',
                  fontWeight: 850,
                  maxWidth: 650,
                  mb: 2,
                }}
              >
                Good finds. <Box component="span" sx={{ color: '#FCD34D' }}>Made for you.</Box>
              </Typography>
              <Typography sx={{ color: '#D1D5E3', fontSize: { xs: '1rem', md: '1.125rem' }, lineHeight: 1.7, maxWidth: 520, mb: 3.5 }}>
                Browse thoughtful picks for your tech, your style, and the spaces you love.{firstName ? ` Welcome back, ${firstName}.` : ''}
              </Typography>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                <Button
                  component={RouterLink}
                  to="/products"
                  variant="contained"
                  color="secondary"
                  size="large"
                  endIcon={<ArrowForwardRounded />}
                  sx={{ px: 3, py: 1.35, fontWeight: 800, borderRadius: 2.5, width: { xs: '100%', sm: 'auto' } }}
                >
                  Explore the collection
                </Button>
                <Button
                  component={RouterLink}
                  to="/orders"
                  variant="outlined"
                  size="large"
                  sx={{ color: 'white', borderColor: 'rgba(255,255,255,.4)', px: 2.5, py: 1.35, borderRadius: 2.5, width: { xs: '100%', sm: 'auto' }, '&:hover': { borderColor: 'white', bgcolor: 'rgba(255,255,255,.08)' } }}
                >
                  View your orders
                </Button>
              </Stack>
            </Grid>
            <Grid item xs={12} md={5} sx={{ display: { xs: 'none', md: 'flex' }, justifyContent: 'center' }}>
              <Box aria-hidden="true" sx={{ width: 300, height: 260, position: 'relative', display: 'grid', placeItems: 'center' }}>
                <Box sx={{ position: 'absolute', width: 224, height: 224, borderRadius: '50%', bgcolor: 'rgba(255,255,255,.08)', border: '1px solid rgba(255,255,255,.14)' }} />
                <Paper elevation={0} sx={{ width: 158, height: 172, borderRadius: 5, transform: 'rotate(-8deg)', display: 'grid', placeItems: 'center', background: 'linear-gradient(145deg,#FDE68A,#F59E0B)', boxShadow: '0 24px 50px rgba(8,12,50,.28)' }}>
                  <ShoppingBasketRounded sx={{ color: '#312E81', fontSize: 82 }} />
                </Paper>
                <Box sx={{ position: 'absolute', right: 2, top: 26, width: 72, height: 72, borderRadius: 4, bgcolor: '#C4B5FD', display: 'grid', placeItems: 'center', transform: 'rotate(9deg)', boxShadow: '0 15px 35px rgba(8,12,50,.2)' }}>
                  <DevicesRounded sx={{ color: '#4C1D95', fontSize: 38 }} />
                </Box>
                <Box sx={{ position: 'absolute', left: 8, bottom: 18, width: 64, height: 64, borderRadius: 3.5, bgcolor: '#A7F3D0', display: 'grid', placeItems: 'center', transform: 'rotate(-10deg)', boxShadow: '0 15px 35px rgba(8,12,50,.2)' }}>
                  <HeadphonesRounded sx={{ color: '#065F46', fontSize: 34 }} />
                </Box>
              </Box>
            </Grid>
          </Grid>
        </Paper>

        <Box component="section" aria-labelledby="categories-heading" sx={{ mt: { xs: 6, md: 8 } }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'end' }} spacing={2} sx={{ mb: 3 }}>
            <Box>
              <Typography variant="overline" sx={{ color: 'primary.main', fontWeight: 800, letterSpacing: '.12em' }}>A good place to start</Typography>
              <Typography id="categories-heading" component="h2" variant="h4" sx={{ fontWeight: 800, letterSpacing: '-.03em', mt: .25 }}>
                Find your kind of thing
              </Typography>
            </Box>
            <Button component={RouterLink} to="/products" endIcon={<ArrowForwardRounded />} sx={{ fontWeight: 750, px: 0.5 }}>
              Browse everything
            </Button>
          </Stack>

          <Grid container spacing={2}>
            {categories.map((category) => (
              <Grid item xs={12} sm={6} lg={3} key={category.title}>
                <Card elevation={0} sx={{ height: '100%', bgcolor: 'background.paper', borderColor: '#E8EAF2', '&:hover .category-icon': { transform: 'scale(1.08)' } }}>
                  <CardActionArea component={RouterLink} to="/products" sx={{ p: 2.5, minHeight: 172, display: 'flex', flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'flex-start' }}>
                      <Box className="category-icon" sx={{ width: 52, height: 52, borderRadius: 3, bgcolor: category.tint, color: category.color, display: 'grid', placeItems: 'center', transition: 'transform .2s ease', '& svg': { fontSize: 29 } }}>
                        {category.icon}
                      </Box>
                      <ArrowForwardRounded sx={{ color: '#94A3B8', fontSize: 20, mt: .5 }} />
                    </Box>
                    <Box sx={{ mt: 2 }}>
                      <Typography variant="subtitle1" sx={{ color: 'text.primary', fontWeight: 800 }}>{category.title}</Typography>
                      <Typography variant="body2" sx={{ color: 'text.secondary', mt: .25 }}>{category.description}</Typography>
                    </Box>
                  </CardActionArea>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Box>

        <Paper
          component="section"
          elevation={0}
          sx={{ mt: { xs: 5, md: 7 }, borderRadius: 4, border: '1px solid #E8EAF2', bgcolor: '#FFFFFF', p: { xs: 2.5, md: 3.5 } }}
        >
          <Grid container spacing={3}>
            <Grid item xs={12} md={4}>
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Box sx={{ width: 44, height: 44, borderRadius: 2.5, bgcolor: '#EEF2FF', color: '#4F46E5', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
                  <LockRounded />
                </Box>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Shop with confidence</Typography>
                  <Typography variant="body2" color="text.secondary">Your account stays protected.</Typography>
                </Box>
              </Stack>
            </Grid>
            <Grid item xs={12} md={4}>
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Box sx={{ width: 44, height: 44, borderRadius: 2.5, bgcolor: '#ECFDF5', color: '#059669', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
                  <LocalShippingRounded />
                </Box>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Keep track of your orders</Typography>
                  <Typography variant="body2" color="text.secondary">Find updates in one place.</Typography>
                </Box>
              </Stack>
            </Grid>
            <Grid item xs={12} md={4}>
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Box sx={{ width: 44, height: 44, borderRadius: 2.5, bgcolor: '#FFFBEB', color: '#D97706', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
                  <ShoppingBagRounded />
                </Box>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Everything in one shop</Typography>
                  <Typography variant="body2" color="text.secondary">Explore collections at your pace.</Typography>
                </Box>
              </Stack>
            </Grid>
          </Grid>
        </Paper>
      </Container>
    </Box>
  );
}
