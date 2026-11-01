import { useEffect, useState } from 'react';
import { Alert, Button, MenuItem, Select, Stack, TextField, Typography } from '@mui/material';
import { api, errorMessage } from '../pricingApi';
import { DataTable, Loading, shortDate, useLoad } from '../common';
import type { SectionProps } from './PriceSections';

const STACKING = [
  ['NO_STACKING', 'No stacking: only the highest-priority promotion applies'],
  ['FLAGGED_ONLY', 'Only promotions flagged "stackable" combine'],
  ['STACK_ALL', 'Every eligible promotion combines'],
];

/** Pricing rules are validated tenant policy values stored in the database; there is no free-form expression input. */
export function RulesSection({ can, settings }: SectionProps) {
  const editable = can('pricing:publish');
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
  useEffect(() => {
    if (!settings) return;
    setDraft({
      maxDiscountPercent: String(settings.maxDiscountPercent), minMarginPercent: String(settings.minMarginPercent),
      approvalThresholdPercent: String(settings.approvalThresholdPercent), defaultCampaignDays: String(settings.defaultCampaignDays),
      couponBulkMax: String(settings.couponBulkMax), couponPerCustomerMax: String(settings.couponPerCustomerMax),
      stackingPolicy: String(settings.stackingPolicy), defaultCurrency: String(settings.defaultCurrency),
    });
  }, [settings]);
  if (!settings) return <Alert severity="info">Settings are not available.</Alert>;
  const num = (k: string) => Number(draft[k]);
  const problems = [
    !(num('maxDiscountPercent') > 0 && num('maxDiscountPercent') <= 100) ? 'Max discount must be in (0, 100].' : '',
    !(num('minMarginPercent') >= 0 && num('minMarginPercent') < 100) ? 'Minimum margin must be in [0, 100).' : '',
    !(num('approvalThresholdPercent') >= 0 && num('approvalThresholdPercent') <= 100) ? 'Approval threshold must be in [0, 100].' : '',
    !(Number.isInteger(num('defaultCampaignDays')) && num('defaultCampaignDays') >= 1 && num('defaultCampaignDays') <= 366) ? 'Default campaign days must be 1-366.' : '',
    !(Number.isInteger(num('couponBulkMax')) && num('couponBulkMax') >= 1 && num('couponBulkMax') <= 10000) ? 'Bulk coupon maximum must be 1-10000.' : '',
    !(Number.isInteger(num('couponPerCustomerMax')) && num('couponPerCustomerMax') >= 1) ? 'Per-customer maximum must be at least 1.' : '',
  ].filter(Boolean);
  const field = (key: string, label: string) => (
    <TextField key={key} size="small" label={label} disabled={!editable} value={draft[key] ?? ''} onChange={(e) => setDraft({ ...draft, [key]: e.target.value })} />
  );
  const save = async () => {
    try {
      await api.saveSettings({ ...Object.fromEntries(Object.entries(draft).filter(([k]) => !['stackingPolicy', 'defaultCurrency'].includes(k)).map(([k, v]) => [k, Number(v)])),
        stackingPolicy: draft.stackingPolicy, defaultCurrency: draft.defaultCurrency });
      setMessage({ severity: 'success', text: 'Pricing rules saved. Reload to see them applied everywhere.' });
    } catch (e) { setMessage({ severity: 'error', text: errorMessage(e) }); }
  };
  return (
    <Stack spacing={2} sx={{ maxWidth: 560 }}>
      {message && <Alert severity={message.severity}>{message.text}</Alert>}
      {!editable && <Alert severity="info">Read only. Changing pricing rules requires the publish permission.</Alert>}
      {problems.length > 0 && <Alert severity="warning">{problems.map((p) => <div key={p}>{p}</div>)}</Alert>}
      {field('maxDiscountPercent', 'Maximum discount (%)')}
      {field('minMarginPercent', 'Minimum margin over cost (%)')}
      {field('approvalThresholdPercent', 'Approval threshold (% discount)')}
      {field('defaultCampaignDays', 'Default campaign duration (days)')}
      {field('couponBulkMax', 'Maximum coupons per bulk generation')}
      {field('couponPerCustomerMax', 'Maximum per-customer coupon uses')}
      <Select size="small" disabled={!editable} value={draft.stackingPolicy ?? 'FLAGGED_ONLY'} onChange={(e) => setDraft({ ...draft, stackingPolicy: e.target.value })}>
        {STACKING.map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
      </Select>
      <Typography variant="caption" color="text.secondary">Supported currencies: {(settings.supportedCurrencies as string[]).join(', ')} · Promotion types: {(settings.supportedTypes as string[]).join(', ')}</Typography>
      {editable && <Button variant="contained" disabled={problems.length > 0} onClick={() => { void save(); }}>Save rules</Button>}
    </Stack>
  );
}

export function AuditSection() {
  const [entity, setEntity] = useState('');
  const { data, loading, error } = useLoad(() => api.audit(entity || undefined), [entity]);
  return (
    <Stack spacing={1}>
      <Select size="small" displayEmpty sx={{ maxWidth: 240 }} value={entity} onChange={(e) => setEntity(e.target.value)}>
        <MenuItem value="">All activity</MenuItem>
        {['CAMPAIGN', 'COUPON', 'PRICE', 'SCHEDULED_PRICE', 'SETTINGS', 'ORDER'].map((t) => <MenuItem key={t} value={t}>{t.replace(/_/g, ' ')}</MenuItem>)}
      </Select>
      <Loading loading={loading} error={error} />
      {data && <DataTable rows={data} empty="No audit entries." columns={[
        { header: 'When', render: (r) => shortDate(r.createdAt) }, { header: 'Entity', render: (r) => `${r.entityType} ${r.entityId}` },
        { header: 'Action', render: (r) => r.action }, { header: 'By', render: (r) => r.actorEmail ?? `user ${r.actorId}` }, { header: 'Detail', render: (r) => r.detail ?? '' },
      ]} />}
    </Stack>
  );
}
