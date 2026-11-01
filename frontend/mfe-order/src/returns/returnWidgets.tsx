import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from '@mui/material';

/** Small confirmation dialog; asks for a reason when the API requires one. */
export function ReasonDialog({ title, label, required, confirmLabel, tone, onConfirm, onClose, children }: {
  title: string; label?: string; required: boolean; confirmLabel: string; tone?: 'primary' | 'error'; onConfirm: (reason: string) => Promise<void>; onClose: () => void; children?: ReactNode;
}) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const missing = required && !reason.trim();
  return (
    <Dialog open onClose={busy ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {children}
          <TextField label={label ?? (required ? 'Reason (required)' : 'Note (optional)')} value={reason} onChange={(e) => setReason(e.target.value)} multiline minRows={2} required={required}
            slotProps={{ htmlInput: { maxLength: 500 } }} error={required && reason.length > 0 && missing} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" color={tone ?? 'primary'} disabled={busy || missing} onClick={async () => { setBusy(true); try { await onConfirm(reason.trim()); } finally { setBusy(false); } }}>{confirmLabel}</Button>
      </DialogActions>
    </Dialog>
  );
}

export function Fact({ label, children }: { label: string; children: ReactNode }) {
  return <Box sx={{ minWidth: 150 }}><Typography variant="caption" color="text.secondary" display="block">{label}</Typography><Typography variant="body2" component="div">{children ?? '—'}</Typography></Box>;
}

export function Problems({ items }: { items: string[] }) {
  if (items.length === 0) return null;
  return <Alert severity="warning" sx={{ my: 1 }}>{items.map((p) => <div key={p}>{p}</div>)}</Alert>;
}
