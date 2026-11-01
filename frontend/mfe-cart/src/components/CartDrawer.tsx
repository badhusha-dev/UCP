import React, { useEffect, useState } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  Stack,
  Button,
  Chip,
} from '@mui/material';
import {
  CloseRounded,
  ShoppingCartOutlined,
  DeleteOutlineRounded,
  AddRounded,
  RemoveRounded,
  ArrowForwardRounded,
} from '@mui/icons-material';
import { useSelector, useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import type { RootState, AppDispatch } from '@ecommerce/shared-library';
import { updateQuantity, removeFromCart, clearCart } from '@ecommerce/shared-library';
import axios from 'axios';

interface CartDrawerProps {
  open: boolean;
  onClose: () => void;
}

export default function CartDrawer({ open, onClose }: CartDrawerProps) {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const cart = useSelector((state: RootState) => state.cart);

  const subtotal = cart.totalAmount || 0;
  const totalItems = cart.items?.reduce((sum: number, item: any) => sum + item.quantity, 0) || 0;

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: {
          width: { xs: '100%', sm: 400 },
          bgcolor: '#0F172A',
          color: '#F8FAFC',
          borderLeft: '1px solid #1E293B',
          p: 3,
          display: 'flex',
          flexDirection: 'column',
        },
      }}
    >
      {/* Top Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Stack direction="row" spacing={1} alignItems="center">
          <ShoppingCartOutlined sx={{ color: '#06B6D4' }} />
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Your Basket ({totalItems})
          </Typography>
        </Stack>
        <IconButton onClick={onClose} sx={{ color: '#94A3B8' }}>
          <CloseRounded />
        </IconButton>
      </Box>

      <Divider sx={{ borderColor: '#1E293B', mb: 2 }} />

      {/* Item List */}
      <Box sx={{ flexGrow: 1, overflowY: 'auto', pr: 0.5 }}>
        {(!cart.items || cart.items.length === 0) ? (
          <Box sx={{ py: 8, textAlign: 'center' }}>
            <Typography variant="body1" sx={{ color: '#94A3B8' }}>
              Your basket is currently empty.
            </Typography>
          </Box>
        ) : (
          <Stack spacing={2}>
            {cart.items.map((item: any) => (
              <Box
                key={item.productId}
                sx={{
                  p: 1.5,
                  bgcolor: '#111827',
                  borderRadius: 2,
                  border: '1px solid #1E293B',
                }}
              >
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, color: '#F8FAFC' }}>
                    {item.name}
                  </Typography>
                  <IconButton
                    size="small"
                    onClick={() => dispatch(removeFromCart(item.productId))}
                    sx={{ color: '#64748B', p: 0.25 }}
                  >
                    <DeleteOutlineRounded sx={{ fontSize: 16 }} />
                  </IconButton>
                </Box>

                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="body2" sx={{ color: '#06B6D4', fontWeight: 700 }}>
                    ${(item.price * item.quantity).toFixed(2)}
                  </Typography>

                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <IconButton
                      size="small"
                      onClick={() => dispatch(updateQuantity({ productId: item.productId, quantity: item.quantity - 1 }))}
                      sx={{ color: '#94A3B8', p: 0.5, border: '1px solid #334155' }}
                    >
                      <RemoveRounded sx={{ fontSize: 14 }} />
                    </IconButton>
                    <Typography variant="body2" sx={{ minWidth: 20, textAlign: 'center', fontWeight: 700 }}>
                      {item.quantity}
                    </Typography>
                    <IconButton
                      size="small"
                      onClick={() => dispatch(updateQuantity({ productId: item.productId, quantity: item.quantity + 1 }))}
                      sx={{ color: '#94A3B8', p: 0.5, border: '1px solid #334155' }}
                    >
                      <AddRounded sx={{ fontSize: 14 }} />
                    </IconButton>
                  </Stack>
                </Box>
              </Box>
            ))}
          </Stack>
        )}
      </Box>

      {/* Footer Summary & Checkout */}
      {cart.items && cart.items.length > 0 && (
        <Box sx={{ pt: 2, borderTop: '1px solid #1E293B' }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
            <Typography variant="body1" sx={{ color: '#94A3B8' }}>Subtotal</Typography>
            <Typography variant="h6" sx={{ fontWeight: 800, color: '#F8FAFC' }}>
              ${subtotal.toFixed(2)}
            </Typography>
          </Box>

          <Button
            variant="contained"
            fullWidth
            onClick={() => {
              onClose();
              navigate('/cart');
            }}
            sx={{
              mb: 1.5,
              py: 1.2,
              bgcolor: '#1E293B',
              color: '#F8FAFC',
              fontWeight: 700,
              '&:hover': { bgcolor: '#334155' },
            }}
          >
            View Full Cart
          </Button>

          <Button
            variant="contained"
            fullWidth
            endIcon={<ArrowForwardRounded />}
            onClick={() => {
              onClose();
              navigate('/orders');
            }}
            sx={{
              py: 1.4,
              fontWeight: 800,
              background: 'linear-gradient(135deg, #4F46E5 0%, #06B6D4 100%)',
              '&:hover': { background: 'linear-gradient(135deg, #4338CA 0%, #0891B2 100%)' },
            }}
          >
            Checkout Now
          </Button>
        </Box>
      )}
    </Drawer>
  );
}
