import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Alert,
  CircularProgress,
  List,
  ListItem,
  ListItemText,
} from '@mui/material';
import { UploadFileOutlined, CheckCircleOutline, ErrorOutline } from '@mui/icons-material';
import type { CsvImportResult } from '../types/inventory';
import { stockApi } from '../api/stockApi';

interface Props {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const CsvImportModal: React.FC<Props> = ({ open, onClose, onSuccess }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [result, setResult] = useState<CsvImportResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
      setResult(null);
      setErrorMessage(null);
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) return;

    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await stockApi.importCsv(selectedFile);
      setResult(res);
      if (res.updatedCount + res.createdCount > 0) {
        onSuccess();
      }
    } catch (err: any) {
      setErrorMessage(err?.response?.data?.message || err?.message || 'CSV upload failed');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setSelectedFile(null);
    setResult(null);
    setErrorMessage(null);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 800 }}>
        Import Stock Counts (CSV)
      </DialogTitle>
      <DialogContent dividers>
        <Typography variant="body2" sx={{ color: '#64748B', mb: 2 }}>
          Import physical on-hand quantities. SKU, warehouse, cost, and reorder settings remain managed by Inventory.
        </Typography>

        <Box sx={{ p: 2, mb: 3, bgcolor: '#F1F5F9', borderRadius: 2, fontFamily: 'monospace', fontSize: '0.8rem' }}>
          <strong>Required Header:</strong><br />
          productId,stockQuantity<br />
          <em>Example: 1,50</em>
        </Box>

        <Box
          sx={{
            border: '2px dashed #CBD5E1',
            borderRadius: 3,
            p: 4,
            textAlign: 'center',
            bgcolor: '#FAFAFA',
            cursor: 'pointer',
            '&:hover': { bgcolor: '#F8FAFC', borderColor: '#6366F1' },
          }}
          component="label"
        >
          <input
            type="file"
            accept=".csv,text/csv"
            style={{ display: 'none' }}
            onChange={handleFileChange}
          />
          <UploadFileOutlined sx={{ fontSize: 48, color: '#6366F1', mb: 1 }} />
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
            {selectedFile ? selectedFile.name : 'Click to select CSV file'}
          </Typography>
          <Typography variant="caption" sx={{ color: '#94A3B8' }}>
            {selectedFile ? `${(selectedFile.size / 1024).toFixed(1)} KB` : 'Maximum file size 5MB'}
          </Typography>
        </Box>

        {errorMessage && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {errorMessage}
          </Alert>
        )}

        {result && (
            <Box sx={{ mt: 3, p: 2, borderRadius: 2, bgcolor: result.failedCount === 0 ? '#ECFDF5' : '#FFFBEB' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              {result.failedCount === 0 ? (
                <CheckCircleOutline sx={{ color: '#10B981' }} />
              ) : (
                <ErrorOutline sx={{ color: '#F59E0B' }} />
              )}
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                Import completed: {result.createdCount + result.updatedCount} saved, {result.failedCount} errors
              </Typography>
            </Box>

            {result.errors && result.errors.length > 0 && (
              <List dense sx={{ maxHeight: 120, overflow: 'auto', mt: 1 }}>
                {result.errors.map((err, idx) => (
                  <ListItem key={idx} disableGutters>
                    <ListItemText
                      primary={err}
                      primaryTypographyProps={{ variant: 'caption', color: '#DC2626' }}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={handleClose} disabled={loading} color="inherit">
          {result ? 'Done' : 'Cancel'}
        </Button>
        {!result && (
          <Button
            onClick={handleUpload}
            variant="contained"
            color="primary"
            disabled={!selectedFile || loading}
            startIcon={loading ? <CircularProgress size={18} color="inherit" /> : null}
            sx={{ fontWeight: 700, borderRadius: 2 }}
          >
            Upload &amp; Process
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};
