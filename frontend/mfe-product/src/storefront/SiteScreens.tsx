import { useState } from 'react';
import {
  Alert, Box, Button, Checkbox, Chip, FormControlLabel, Grid, MenuItem, Paper, Select, Stack, TextField, Typography,
} from '@mui/material';
import { DataTable, Loading, shortDate, useLoad } from '../pricing/common';
import { DocumentPanel, StatusChip, findDoc, useDocuments, useNotice } from './common';
import type { ScreenProps } from './common';
import { sf } from './storefrontApi';
import type { Row } from './storefrontApi';
import { FONTS, contrastRatio, countNavItems, isSafeUrl, openingHoursProblems, seoPreview, validateNav, validateTheme } from './storefrontModel';
import type { NavItem } from './storefrontModel';

const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

export function DashboardScreen({ storefront }: ScreenProps) {
  const { data, loading, error } = useLoad(() => sf.dashboard(storefront.id), [storefront.id]);
  const tiles: [string, string][] = [['publishedPages', 'Published pages'], ['landingPages', 'Landing pages'], ['pendingReview', 'Awaiting review'], ['awaitingPublish', 'Approved, not live'],
    ['scheduled', 'Scheduled to publish'], ['activeBanners', 'Active banners'], ['mediaFiles', 'Images'], ['verifiedDomains', 'Verified domains'], ['pendingDomains', 'Domains to verify']];
  return (
    <>
      <Loading loading={loading} error={error} />
      {data && <Grid container spacing={2}>{tiles.map(([k, label]) => (
        <Grid item xs={6} md={4} key={k}><Paper variant="outlined" sx={{ p: 2 }}><Typography variant="caption" color="text.secondary">{label}</Typography><Typography variant="h5">{String(data[k])}</Typography></Paper></Grid>
      ))}</Grid>}
    </>
  );
}

export function OverviewScreen({ storefront }: ScreenProps) {
  const docs = useDocuments(storefront.id);
  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle1">{storefront.name}</Typography>
        <Typography variant="body2" color="text.secondary">Type: {storefront.storefrontType} · Store: {storefront.storeId} · Slug: {storefront.slug} · Locale: {storefront.defaultLocale}</Typography>
        <Typography variant="body2" color="text.secondary">Preview address on the platform: <code>/api/products/public/storefront/{storefront.slug}</code></Typography>
      </Paper>
      <Loading loading={docs.loading} error={docs.error} />
      {docs.data && <DataTable rows={docs.data} empty="" columns={[
        { header: 'Content', render: (r) => r.title }, { header: 'Type', render: (r) => r.docType },
        { header: 'Live', render: (r) => (r.publishedVersionNo ? `v${r.publishedVersionNo}` : 'Not published') },
        { header: 'Latest version', render: (r) => `v${r.latestVersion.versionNo}` }, { header: 'State', render: (r) => <StatusChip status={r.latestVersion.status} /> },
        { header: 'Updated', render: (r) => shortDate(r.latestVersion.updatedAt) },
      ]} />}
    </Stack>
  );
}

// ---- banners & media -------------------------------------------------------------------------------------------------

const MEDIA_KINDS = ['BANNER_DESKTOP', 'BANNER_MOBILE', 'LOGO', 'FAVICON', 'SOCIAL', 'PRODUCT', 'GENERAL'];
const KIND_HINT: Record<string, string> = {
  BANNER_DESKTOP: '1200-4000 × 200-2000 px', BANNER_MOBILE: '400-1600 × 200-2400 px', LOGO: '64-2000 × 32-1000 px', FAVICON: 'square PNG, 32-512 px',
  SOCIAL: 'at least 600 × 315 px', PRODUCT: '200-4000 px', GENERAL: '50-4000 px',
};

export function MediaLibrary({ storefront, can }: ScreenProps) {
  const media = useLoad(() => sf.media(storefront.id), [storefront.id]);
  const [kind, setKind] = useState('BANNER_DESKTOP');
  const [alt, setAlt] = useState('');
  const { run, view } = useNotice();
  const upload = (file: File | undefined) => {
    if (!file) return;
    if (!['image/png', 'image/jpeg', 'image/gif'].includes(file.type)) { void run(() => Promise.reject(new Error('Only PNG, JPEG and GIF images are accepted.')), ''); return; }
    if (file.size > 5 * 1024 * 1024) { void run(() => Promise.reject(new Error('Images may be at most 5 MB.')), ''); return; }
    void run(() => sf.upload(storefront.id, file, kind, alt), 'Image uploaded.', () => { setAlt(''); void media.reload(); });
  };
  return (
    <Stack spacing={2}>
      {view}
      {can('storefront:media') && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems="flex-start">
            <Select size="small" value={kind} onChange={(e) => setKind(e.target.value)}>{MEDIA_KINDS.map((k) => <MenuItem key={k} value={k}>{k.replace('_', ' ')}</MenuItem>)}</Select>
            <TextField size="small" label="Alt text (required)" helperText={`Allowed size: ${KIND_HINT[kind]}; PNG, JPEG or GIF up to 5 MB`} value={alt} onChange={(e) => setAlt(e.target.value)} />
            <Button variant="contained" component="label" disabled={alt.trim() === ''}>Choose image<input hidden type="file" accept="image/png,image/jpeg,image/gif" onChange={(e) => { upload(e.target.files?.[0]); e.target.value = ''; }} /></Button>
          </Stack>
        </Paper>
      )}
      <Loading loading={media.loading} error={media.error} />
      {media.data && <DataTable rows={media.data} empty="No images uploaded yet." columns={[
        { header: 'Preview', render: (r) => <img alt={r.altText} src={`/api/products/public/storefront/media/${r.publicId}`} style={{ maxWidth: 96, maxHeight: 48 }} /> },
        { header: 'File', render: (r) => r.fileName }, { header: 'Use', render: (r) => r.kind }, { header: 'Size', render: (r) => `${r.width}×${r.height} · ${Math.round(r.sizeBytes / 1024)} KB` }, { header: 'Alt text', render: (r) => r.altText },
        { header: '', render: (r) => can('storefront:media') && <Button size="small" color="error" onClick={() => { if (window.confirm(`Delete ${r.fileName}?`)) void run(() => sf.deleteMedia(r.id), 'Image deleted.', () => { void media.reload(); }); }}>Delete</Button> },
      ]} />}
    </Stack>
  );
}

export function BannersScreen({ storefront, can }: ScreenProps) {
  const banners = useLoad(() => sf.banners(storefront.id), [storefront.id]);
  const media = useLoad(() => sf.media(storefront.id), [storefront.id]);
  const { run, view } = useNotice();
  const blank = { name: '', placement: 'HERO', desktopMediaId: '', mobileMediaId: '', altText: '', destinationUrl: '', startsAt: '', endsAt: '', priority: '100', active: true };
  const [form, setForm] = useState(blank);
  const problems = [form.name.trim() === '' && 'Name is required.', form.desktopMediaId === '' && 'Choose a desktop image.', form.altText.trim() === '' && 'Alt text is required.',
    form.destinationUrl !== '' && !isSafeUrl(form.destinationUrl) && 'Link must be a path starting with / or an https:// link.',
    form.startsAt && form.endsAt && new Date(form.endsAt) <= new Date(form.startsAt) && 'End must be after start.'].filter(Boolean) as string[];
  const pick = (key: 'desktopMediaId' | 'mobileMediaId', kind: string) => (
    <Select size="small" displayEmpty value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value as string })} sx={{ minWidth: 200 }}>
      <MenuItem value="">{kind === 'BANNER_DESKTOP' ? 'Desktop image…' : 'Mobile image (optional)…'}</MenuItem>
      {(media.data ?? []).filter((m) => m.kind === kind).map((m) => <MenuItem key={m.id} value={String(m.id)}>{m.fileName}</MenuItem>)}
    </Select>
  );
  const move = (list: Row[], i: number, d: number) => {
    const ids = list.map((b) => b.id as number); const j = i + d;
    if (j < 0 || j >= ids.length) return;
    [ids[i], ids[j]] = [ids[j], ids[i]];
    void run(() => sf.reorderBanners(storefront.id, ids), 'Order saved.', () => { void banners.reload(); });
  };
  return (
    <Stack spacing={2}>
      {view}
      {can('storefront:edit') && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          {problems.length > 0 && form.name !== '' && <Alert severity="warning" sx={{ mb: 1 }}>{problems.map((p) => <div key={p}>{p}</div>)}</Alert>}
          <Stack spacing={1}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
              <TextField size="small" label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              <Select size="small" value={form.placement} onChange={(e) => setForm({ ...form, placement: e.target.value })}>{['HERO', 'PROMO', 'STRIP'].map((p) => <MenuItem key={p} value={p}>{p}</MenuItem>)}</Select>
              {pick('desktopMediaId', 'BANNER_DESKTOP')}{pick('mobileMediaId', 'BANNER_MOBILE')}
              <TextField size="small" label="Alt text" value={form.altText} onChange={(e) => setForm({ ...form, altText: e.target.value })} />
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
              <TextField size="small" label="Click destination" value={form.destinationUrl} onChange={(e) => setForm({ ...form, destinationUrl: e.target.value })} />
              <TextField size="small" type="datetime-local" label="Starts" InputLabelProps={{ shrink: true }} value={form.startsAt} onChange={(e) => setForm({ ...form, startsAt: e.target.value })} />
              <TextField size="small" type="datetime-local" label="Ends" InputLabelProps={{ shrink: true }} value={form.endsAt} onChange={(e) => setForm({ ...form, endsAt: e.target.value })} />
              <TextField size="small" label="Priority" sx={{ width: 100 }} value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })} />
              <FormControlLabel control={<Checkbox checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />} label="Active" />
              <Button variant="contained" disabled={problems.length > 0} onClick={() => run(() => sf.createBanner(storefront.id, {
                name: form.name, placement: form.placement, desktopMediaId: Number(form.desktopMediaId), mobileMediaId: form.mobileMediaId ? Number(form.mobileMediaId) : null, altText: form.altText,
                destinationUrl: form.destinationUrl || null, startsAt: form.startsAt ? new Date(form.startsAt).toISOString() : null, endsAt: form.endsAt ? new Date(form.endsAt).toISOString() : null,
                priority: Number(form.priority), active: form.active }), 'Banner created.', () => { setForm(blank); void banners.reload(); })}>Add banner</Button>
            </Stack>
          </Stack>
        </Paper>
      )}
      <Loading loading={banners.loading} error={banners.error} />
      {banners.data && <DataTable rows={banners.data} empty="No banners yet." columns={[
        { header: 'Name', render: (r) => r.name }, { header: 'Placement', render: (r) => r.placement },
        { header: 'Window', render: (r) => `${r.startsAt ? shortDate(r.startsAt) : 'now'} – ${r.endsAt ? shortDate(r.endsAt) : 'open'}` },
        { header: 'Link', render: (r) => r.destinationUrl ?? '—' }, { header: 'Active', render: (r) => (r.active ? <Chip size="small" color="success" label="Yes" /> : <Chip size="small" label="No" />) },
        { header: '', render: (r) => { const i = banners.data!.indexOf(r); return can('storefront:edit') && (
          <Stack direction="row" spacing={1}>
            <Button size="small" onClick={() => move(banners.data!, i, -1)}>Up</Button><Button size="small" onClick={() => move(banners.data!, i, 1)}>Down</Button>
            <Button size="small" onClick={() => run(() => sf.updateBanner(r.id, { ...r, active: !r.active }), r.active ? 'Banner deactivated.' : 'Banner activated.', () => { void banners.reload(); })}>{r.active ? 'Deactivate' : 'Activate'}</Button>
            <Button size="small" color="error" onClick={() => { if (window.confirm(`Delete banner "${r.name}"?`)) void run(() => sf.deleteBanner(r.id), 'Banner deleted.', () => { void banners.reload(); }); }}>Delete</Button>
          </Stack>); } },
      ]} />}
    </Stack>
  );
}

// ---- navigation -------------------------------------------------------------------------------------------------------

function NavEditor({ items, onChange, editable, depth = 1 }: { items: NavItem[]; onChange: (i: NavItem[]) => void; editable: boolean; depth?: number }) {
  const upd = (i: number, patch: Partial<NavItem>) => onChange(items.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  return (
    <Stack spacing={1} sx={{ pl: depth > 1 ? 3 : 0 }}>
      {items.map((it, i) => (
        <Box key={i}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <TextField size="small" label="Label" disabled={!editable} value={it.label} onChange={(e) => upd(i, { label: e.target.value })} />
            <TextField size="small" label="Link (/path or https://…)" disabled={!editable} value={it.url ?? ''} onChange={(e) => upd(i, { url: e.target.value })} />
            <TextField size="small" label="Category ID" type="number" sx={{ width: 120 }} disabled={!editable} value={it.categoryId ?? ''} onChange={(e) => upd(i, { categoryId: e.target.value ? Number(e.target.value) : undefined })} />
            <FormControlLabel control={<Checkbox disabled={!editable} checked={!!it.promotional} onChange={(e) => upd(i, { promotional: e.target.checked })} />} label="Promo" />
            <Button size="small" disabled={!editable || i === 0} onClick={() => onChange(items.map((x, j) => (j === i - 1 ? items[i] : j === i ? items[i - 1] : x)))}>Up</Button>
            <Button size="small" disabled={!editable || i === items.length - 1} onClick={() => onChange(items.map((x, j) => (j === i + 1 ? items[i] : j === i ? items[i + 1] : x)))}>Down</Button>
            <Button size="small" disabled={!editable || depth >= 3} onClick={() => upd(i, { children: [...it.children, { label: '', children: [] }] })}>Add sub-item</Button>
            <Button size="small" color="error" disabled={!editable} onClick={() => onChange(items.filter((_, j) => j !== i))}>Remove</Button>
          </Stack>
          {it.children.length > 0 && <NavEditor items={it.children} editable={editable} depth={depth + 1} onChange={(children) => upd(i, { children })} />}
        </Box>
      ))}
      <Button size="small" sx={{ alignSelf: 'flex-start' }} disabled={!editable} onClick={() => onChange([...items, { label: '', children: [] }])}>Add item</Button>
    </Stack>
  );
}

const normNav = (list?: Row[]): NavItem[] => (list ?? []).map((i) => ({ label: i.label ?? '', url: i.url, categoryId: i.categoryId, promotional: i.promotional, children: normNav(i.children) }));

export function NavigationScreen({ storefront, can }: ScreenProps) {
  const docs = useDocuments(storefront.id);
  const doc = findDoc(docs.data, 'NAVIGATION');
  const [menu, setMenu] = useState<'header' | 'footer' | 'mobile'>('header');
  return (
    <>
      <Loading loading={docs.loading} error={docs.error} />
      {doc && (
        <DocumentPanel doc={doc} can={can} validate={(c) => (['header', 'footer', 'mobile'] as const).flatMap((m) => validateNav(normNav(c[m])))}>
          {({ content, setContent, editable }) => (
            <Stack spacing={2}>
              <Select size="small" value={menu} onChange={(e) => setMenu(e.target.value as typeof menu)} sx={{ maxWidth: 220 }}>
                {(['header', 'footer', 'mobile'] as const).map((m) => <MenuItem key={m} value={m}>{m[0].toUpperCase() + m.slice(1)} menu ({countNavItems(normNav(content[m]))} items)</MenuItem>)}
              </Select>
              {menu === 'mobile' && <Alert severity="info">Leave the mobile menu empty to reuse the header menu on phones.</Alert>}
              <NavEditor items={normNav(content[menu])} editable={editable} onChange={(items) => setContent({ ...content, [menu]: items })} />
            </Stack>
          )}
        </DocumentPanel>
      )}
    </>
  );
}

// ---- theme & branding -------------------------------------------------------------------------------------------------

const ENUMS: Record<string, string[]> = {
  headerLayout: ['CLASSIC', 'CENTERED', 'MINIMAL', 'MEGA'], footerLayout: ['SIMPLE', 'COLUMNS', 'MINIMAL'], buttonStyle: ['SQUARE', 'ROUNDED', 'PILL'],
  productCardStyle: ['FLAT', 'ELEVATED', 'BORDERED'], navigationStyle: ['UNDERLINE', 'PILL', 'PLAIN'], mobileMenu: ['DRAWER', 'FULLSCREEN', 'BOTTOM_BAR'], colorMode: ['LIGHT', 'DARK', 'AUTO'],
};

export function ThemeScreen({ storefront, can }: ScreenProps) {
  const docs = useDocuments(storefront.id);
  const doc = findDoc(docs.data, 'THEME');
  const media = useLoad(() => sf.media(storefront.id), [storefront.id]);
  const presets = useLoad(() => sf.presets(), []);
  const { run, view } = useNotice();
  const [presetName, setPresetName] = useState('');
  return (
    <Stack spacing={2}>
      {view}
      <Loading loading={docs.loading} error={docs.error} />
      {doc && (
        <DocumentPanel doc={doc} can={can} validate={validateTheme}>
          {({ content, setContent, editable }) => {
            const colors = content.colors ?? {};
            const setColor = (k: string, v: string) => setContent({ ...content, colors: { ...colors, [k]: v } });
            const ratio = contrastRatio(colors.text ?? '', colors.background ?? '');
            return (
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                  <Typography variant="subtitle2">Preset</Typography>
                  <Select size="small" displayEmpty disabled={!editable} value="" onChange={(e) => { const p = (presets.data ?? []).find((x) => x.id === e.target.value); if (p) setContent({ ...content, ...p.content, logoMediaId: content.logoMediaId, faviconMediaId: content.faviconMediaId }); }} sx={{ minWidth: 200 }}>
                    <MenuItem value="">Apply a preset…</MenuItem>{(presets.data ?? []).map((p) => <MenuItem key={p.id} value={p.id}>{p.name}{p.builtin ? '' : ' (yours)'}</MenuItem>)}
                  </Select>
                  {can('storefront:edit') && <><TextField size="small" label="Save current as preset" value={presetName} onChange={(e) => setPresetName(e.target.value)} />
                    <Button disabled={presetName.trim() === ''} onClick={() => { void run(() => sf.savePreset(storefront.id, presetName.trim(), content), 'Preset saved.', () => { setPresetName(''); void presets.reload(); }); }}>Save preset</Button></>}
                </Stack>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {['primary', 'secondary', 'accent', 'background', 'surface', 'text'].map((k) => (
                    <Stack key={k} direction="row" spacing={0.5} alignItems="center">
                      <input aria-label={`${k} colour`} type="color" disabled={!editable} value={/^#[0-9a-fA-F]{6}$/.test(colors[k] ?? '') ? colors[k] : '#000000'} onChange={(e) => setColor(k, e.target.value)} />
                      <TextField size="small" label={k} sx={{ width: 120 }} disabled={!editable} value={colors[k] ?? ''} onChange={(e) => setColor(k, e.target.value)} />
                    </Stack>
                  ))}
                </Stack>
                {ratio > 0 && <Chip size="small" color={ratio >= 4.5 ? 'success' : 'warning'} label={`Text/background contrast ${ratio.toFixed(1)}:1 ${ratio >= 4.5 ? '(AA)' : '(below AA 4.5:1)'}`} sx={{ alignSelf: 'flex-start' }} />}
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {(['headingFont', 'bodyFont'] as const).map((k) => (
                    <Select key={k} size="small" displayEmpty disabled={!editable} value={content[k] ?? ''} onChange={(e) => setContent({ ...content, [k]: e.target.value || undefined })} sx={{ minWidth: 190 }}>
                      <MenuItem value="">{k === 'headingFont' ? 'Heading font…' : 'Body font…'}</MenuItem>{FONTS.map((f) => <MenuItem key={f} value={f}>{f}</MenuItem>)}
                    </Select>
                  ))}
                  {Object.entries(ENUMS).map(([k, opts]) => (
                    <Select key={k} size="small" disabled={!editable} value={content[k] ?? opts[0]} onChange={(e) => setContent({ ...content, [k]: e.target.value })}>{opts.map((o) => <MenuItem key={o} value={o}>{k.replace(/([A-Z])/g, ' $1').toLowerCase()}: {o.toLowerCase()}</MenuItem>)}</Select>
                  ))}
                </Stack>
                <Stack direction="row" spacing={1}>
                  {([['logoMediaId', 'LOGO', 'Logo…'], ['faviconMediaId', 'FAVICON', 'Favicon…']] as const).map(([k, kind, label]) => (
                    <Select key={k} size="small" displayEmpty disabled={!editable} value={content[k] ?? ''} onChange={(e) => setContent({ ...content, [k]: e.target.value === '' ? undefined : Number(e.target.value) })} sx={{ minWidth: 190 }}>
                      <MenuItem value="">{label}</MenuItem>{(media.data ?? []).filter((m) => m.kind === kind).map((m) => <MenuItem key={m.id} value={m.id}>{m.fileName}</MenuItem>)}
                    </Select>
                  ))}
                </Stack>
                <Paper variant="outlined" sx={{ p: 2, background: colors.background, color: colors.text, fontFamily: content.bodyFont }}>
                  <Typography sx={{ fontFamily: content.headingFont, color: colors.primary }} variant="h6">Brand preview</Typography>
                  <Typography variant="body2">Sample text on your background. <span style={{ color: colors.accent }}>Accent</span></Typography>
                  <Button variant="contained" size="small" sx={{ mt: 1, background: colors.primary, borderRadius: content.buttonStyle === 'PILL' ? 99 : content.buttonStyle === 'SQUARE' ? 0 : 2 }}>Button</Button>
                </Paper>
              </Stack>
            );
          }}
        </DocumentPanel>
      )}
    </Stack>
  );
}

// ---- settings-backed screens --------------------------------------------------------------------------------------------

export function ProductDisplayScreen({ storefront, can }: ScreenProps) {
  const docs = useDocuments(storefront.id);
  const doc = findDoc(docs.data, 'SETTINGS');
  return (
    <>
      <Loading loading={docs.loading} error={docs.error} />
      {doc && <DocumentPanel doc={doc} can={can}>
        {({ content, setContent, editable }) => {
          const display = content.display ?? {};
          const set = (patch: Row) => setContent({ ...content, display: { ...display, ...patch } });
          const badges: string[] = display.badges ?? [];
          return (
            <Stack spacing={1}>
              <Typography variant="body2" color="text.secondary">Controls how catalog products are presented. Product data itself always comes from the catalog.</Typography>
              <FormControlLabel control={<Checkbox disabled={!editable} checked={display.showAvailability ?? true} onChange={(e) => set({ showAvailability: e.target.checked })} />} label="Show availability (in stock / low stock / sold out)" />
              <FormControlLabel control={<Checkbox disabled={!editable} checked={display.showSearch ?? true} onChange={(e) => set({ showSearch: e.target.checked })} />} label="Show search" />
              <FormControlLabel control={<Checkbox disabled={!editable} checked={display.showFilters ?? true} onChange={(e) => set({ showFilters: e.target.checked })} />} label="Show filters" />
              <Stack direction="row" spacing={1}>
                <Select size="small" disabled={!editable} value={display.defaultSort ?? 'NEWEST'} onChange={(e) => set({ defaultSort: e.target.value })}>{['NEWEST', 'PRICE_ASC', 'PRICE_DESC', 'NAME'].map((s) => <MenuItem key={s} value={s}>{s.replace('_', ' ')}</MenuItem>)}</Select>
                <TextField size="small" type="number" label="Products per page (4-96)" disabled={!editable} value={display.productsPerPage ?? 24} onChange={(e) => set({ productsPerPage: Number(e.target.value) })} />
              </Stack>
              <Stack direction="row" spacing={2} flexWrap="wrap">
                {['NEW', 'SALE', 'LOW_STOCK', 'SOLD_OUT', 'FEATURED'].map((b) => (
                  <FormControlLabel key={b} control={<Checkbox disabled={!editable} checked={badges.includes(b)} onChange={(e) => set({ badges: e.target.checked ? [...badges, b] : badges.filter((x) => x !== b) })} />} label={`${b.replace('_', ' ')} badge`} />
                ))}
              </Stack>
            </Stack>
          );
        }}
      </DocumentPanel>}
    </>
  );
}

export function RestaurantScreen({ storefront, can }: ScreenProps) {
  const docs = useDocuments(storefront.id);
  const doc = findDoc(docs.data, 'SETTINGS');
  return (
    <Stack spacing={2}>
      {storefront.storefrontType !== 'RESTAURANT' && <Alert severity="info">This storefront is a retail store. Restaurant settings apply to storefronts created as Restaurant.</Alert>}
      <Alert severity="warning">Dish and menu data is not part of this release: the platform has no restaurant catalogue service to read it from, and menu items are deliberately not duplicated into storefront content. Opening hours and ordering availability below are fully functional.</Alert>
      <Loading loading={docs.loading} error={docs.error} />
      {doc && <DocumentPanel doc={doc} can={can} validate={(c) => openingHoursProblems(c.restaurant?.openingHours ?? {})}>
        {({ content, setContent, editable }) => {
          const r = content.restaurant ?? {};
          const hours: Record<string, Row> = r.openingHours ?? {};
          const set = (patch: Row) => setContent({ ...content, restaurant: { ...r, ...patch } });
          return (
            <Stack spacing={1}>
              <FormControlLabel control={<Checkbox disabled={!editable} checked={!!r.orderingAvailable} onChange={(e) => set({ orderingAvailable: e.target.checked })} />} label="Online ordering available" />
              <FormControlLabel control={<Checkbox disabled={!editable} checked={r.showVegIndicator ?? true} onChange={(e) => set({ showVegIndicator: e.target.checked })} />} label="Show vegetarian / non-vegetarian indicators" />
              <FormControlLabel control={<Checkbox disabled={!editable} checked={!!r.showPreparationTime} onChange={(e) => set({ showPreparationTime: e.target.checked })} />} label="Show preparation time" />
              <Typography variant="subtitle2">Opening hours (24-hour, local time)</Typography>
              {DAYS.map((day) => {
                const h = hours[day] ?? { open: '09:00', close: '21:00' };
                const setDay = (patch: Row) => set({ openingHours: { ...hours, [day]: { ...h, ...patch } } });
                return (
                  <Stack key={day} direction="row" spacing={1} alignItems="center">
                    <Typography sx={{ width: 48 }}>{day}</Typography>
                    <FormControlLabel control={<Checkbox disabled={!editable} checked={!!hours[day] && !h.closed} onChange={(e) => (e.target.checked ? setDay({ closed: false }) : set({ openingHours: { ...hours, [day]: { closed: true } } }))} />} label="Open" />
                    {hours[day] && !h.closed && <>
                      <TextField size="small" type="time" disabled={!editable} value={h.open ?? ''} onChange={(e) => setDay({ open: e.target.value })} />
                      <TextField size="small" type="time" disabled={!editable} value={h.close ?? ''} onChange={(e) => setDay({ close: e.target.value })} />
                    </>}
                  </Stack>
                );
              })}
            </Stack>
          );
        }}
      </DocumentPanel>}
    </Stack>
  );
}

export function SettingsScreen({ storefront, can, onChanged }: ScreenProps) {
  const docs = useDocuments(storefront.id);
  const doc = findDoc(docs.data, 'SETTINGS');
  const audit = useLoad(() => (can('storefront:audit') ? sf.audit(storefront.id) : Promise.resolve([])), [storefront.id]);
  const [name, setName] = useState(storefront.name as string);
  const { run, view } = useNotice();
  return (
    <Stack spacing={2}>
      {view}
      {can('storefront:manage') && <Stack direction="row" spacing={1}><TextField size="small" label="Storefront name" value={name} onChange={(e) => setName(e.target.value)} />
        <Button disabled={name.trim() === '' || name === storefront.name} onClick={() => { void run(() => sf.rename(storefront.id, name), 'Renamed.', onChanged); }}>Rename</Button></Stack>}
      <Loading loading={docs.loading} error={docs.error} />
      {doc && <DocumentPanel doc={doc} can={can}>
        {({ content, setContent, editable }) => {
          const c = content.contact ?? {};
          const set = (patch: Row) => setContent({ ...content, contact: { ...c, ...patch } });
          return (
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
              <TextField size="small" label="Business name" disabled={!editable} value={c.name ?? ''} onChange={(e) => set({ name: e.target.value })} />
              <TextField size="small" label="Phone" disabled={!editable} value={c.phone ?? ''} onChange={(e) => set({ phone: e.target.value })} />
              <TextField size="small" label="Email" disabled={!editable} value={c.email ?? ''} onChange={(e) => set({ email: e.target.value })} />
            </Stack>
          );
        }}
      </DocumentPanel>}
      {can('storefront:audit') && <>
        <Typography variant="subtitle2">Audit history</Typography>
        <Loading loading={audit.loading} error={audit.error} />
        {audit.data && <DataTable rows={audit.data} empty="No activity." columns={[
          { header: 'When', render: (r) => shortDate(r.createdAt) }, { header: 'What', render: (r) => `${r.entityType} ${r.entityId}` }, { header: 'Action', render: (r) => r.action },
          { header: 'By', render: (r) => r.actorEmail ?? `user ${r.actorId}` }, { header: 'Detail', render: (r) => r.detail ?? '' },
        ]} />}
      </>}
    </Stack>
  );
}

export function LocalizationScreen({ storefront }: ScreenProps) {
  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2">Configured languages</Typography>
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>{String(storefront.supportedLocales).split(',').map((l) => <Chip key={l} label={l === storefront.defaultLocale ? `${l} (default)` : l} />)}</Stack>
      </Paper>
      <Alert severity="warning">Per-language page content is not available yet. The storefront records its supported locales, but pages, navigation and banners have a single content version. Changing locales after creation is not yet supported.</Alert>
    </Stack>
  );
}

// ---- SEO ----------------------------------------------------------------------------------------------------------------

export function SeoScreen({ storefront, can }: ScreenProps) {
  const docs = useDocuments(storefront.id);
  const pages = (docs.data ?? []).filter((d) => d.docType === 'HOMEPAGE' || d.docType === 'LANDING');
  const [docId, setDocId] = useState<number | null>(null);
  const doc = pages.find((p) => p.id === (docId ?? pages[0]?.id));
  const base = `https://${storefront.slug}.storefront.localhost`;
  return (
    <Stack spacing={2}>
      <Loading loading={docs.loading} error={docs.error} />
      {pages.length > 0 && <Select size="small" value={doc?.id ?? ''} onChange={(e) => setDocId(Number(e.target.value))} sx={{ maxWidth: 320 }}>{pages.map((p) => <MenuItem key={p.id} value={p.id}>{p.title}</MenuItem>)}</Select>}
      <Typography variant="caption" color="text.secondary">Sitemap: /api/products/public/storefront/{storefront.slug}/sitemap.xml · Robots: /api/products/public/storefront/{storefront.slug}/robots.txt (only published, indexable pages are listed)</Typography>
      {doc && <DocumentPanel key={doc.id} doc={doc} can={can}>
        {({ content, setContent, editable }) => {
          const seo = content.seo ?? {};
          const set = (patch: Row) => setContent({ ...content, seo: { ...seo, ...patch } });
          const preview = seoPreview(seo, doc.title, base, doc.docType === 'HOMEPAGE' ? '/' : `/p/${doc.slug}`);
          return (
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}><Stack spacing={1}>
                <TextField size="small" label={`SEO title (${(seo.title ?? '').length}/70)`} disabled={!editable} value={seo.title ?? ''} onChange={(e) => set({ title: e.target.value })} />
                <TextField size="small" multiline label={`Meta description (${(seo.description ?? '').length}/160)`} disabled={!editable} value={seo.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
                <TextField size="small" label="Canonical URL (https)" disabled={!editable} value={seo.canonicalUrl ?? ''} onChange={(e) => set({ canonicalUrl: e.target.value || undefined })} />
                <Select size="small" disabled={!editable} value={seo.robots ?? 'INDEX_FOLLOW'} onChange={(e) => set({ robots: e.target.value })}>{['INDEX_FOLLOW', 'NOINDEX_FOLLOW', 'INDEX_NOFOLLOW', 'NOINDEX_NOFOLLOW'].map((r) => <MenuItem key={r} value={r}>{r.replace('_', ', ').toLowerCase()}</MenuItem>)}</Select>
                <TextField size="small" label="Social share title" disabled={!editable} value={seo.ogTitle ?? ''} onChange={(e) => set({ ogTitle: e.target.value })} />
                <TextField size="small" label="Social share description" disabled={!editable} value={seo.ogDescription ?? ''} onChange={(e) => set({ ogDescription: e.target.value })} />
              </Stack></Grid>
              <Grid item xs={12} md={6}><Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="caption" color="text.secondary">Search result preview</Typography>
                <Typography sx={{ color: '#1a0dab' }}>{preview.title}</Typography>
                <Typography variant="caption" sx={{ color: '#006621' }}>{preview.url}</Typography>
                <Typography variant="body2">{preview.description || 'No description.'}</Typography>
                {preview.warnings.map((w) => <Alert key={w} severity="info" sx={{ mt: 1 }}>{w}</Alert>)}
              </Paper></Grid>
            </Grid>
          );
        }}
      </DocumentPanel>}
    </Stack>
  );
}

// ---- domains --------------------------------------------------------------------------------------------------------------

export function DomainsScreen({ storefront, can }: ScreenProps) {
  const domains = useLoad(() => sf.domains(storefront.id), [storefront.id]);
  const { run, view } = useNotice();
  const [host, setHost] = useState('');
  const canManage = can('storefront:domain');
  return (
    <Stack spacing={2}>
      {view}
      <Alert severity="info">A domain is shown as verified only after the platform finds your DNS challenge record, and SSL as active only after a real TLS check succeeds. Nothing is assumed.</Alert>
      {canManage && <Stack direction="row" spacing={1}><TextField size="small" label="Custom domain (e.g. shop.example.com)" value={host} onChange={(e) => setHost(e.target.value)} />
        <Button variant="contained" disabled={host.trim() === ''} onClick={() => { void run(() => sf.addDomain(storefront.id, host.trim()), 'Domain added. Create the DNS records below, then press Verify.', () => { setHost(''); void domains.reload(); }); }}>Add domain</Button></Stack>}
      <Loading loading={domains.loading} error={domains.error} />
      {(domains.data ?? []).map((d) => d.type === 'PLATFORM_SUBDOMAIN' ? (
        <Paper key="platform" variant="outlined" sx={{ p: 2 }}><Typography variant="subtitle2">{d.hostname} <Chip size="small" label="Platform subdomain" /></Typography><Typography variant="caption" color="text.secondary">{d.note}</Typography></Paper>
      ) : (
        <Paper key={d.id} variant="outlined" sx={{ p: 2 }}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography variant="subtitle2">{d.hostname}</Typography>{d.isPrimary && <Chip size="small" color="primary" label="Primary" />}
            <StatusChip status={d.verificationStatus} /><Chip size="small" variant="outlined" label={`SSL: ${String(d.sslStatus).replace('_', ' ').toLowerCase()}`} color={d.sslStatus === 'ACTIVE' ? 'success' : d.sslStatus === 'FAILED' ? 'error' : 'default'} />
            {d.sslExpiresAt && <Typography variant="caption">expires {shortDate(d.sslExpiresAt)}</Typography>}
          </Stack>
          {d.lastCheckMessage && <Typography variant="caption" color="text.secondary" display="block">Last check: {d.lastCheckMessage}</Typography>}
          {d.verificationStatus !== 'VERIFIED' && d.instructions && (
            <Box sx={{ mt: 1 }}>
              <Typography variant="caption">1. Add a <b>TXT</b> record: name <code>{d.instructions.step1.name}</code> value <code>{d.instructions.step1.value}</code></Typography><br />
              <Typography variant="caption">2. Add a <b>{d.instructions.step2.type}</b> record: name <code>{d.instructions.step2.name}</code> → <code>{d.instructions.step2.value}</code></Typography>
            </Box>
          )}
          {canManage && <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
            <Button size="small" onClick={() => run(() => sf.verifyDomain(d.id), 'Verification checked.', () => { void domains.reload(); })}>Verify</Button>
            <Button size="small" disabled={d.verificationStatus !== 'VERIFIED'} onClick={() => run(() => sf.checkSsl(d.id), 'SSL checked.', () => { void domains.reload(); })}>Check SSL</Button>
            <Button size="small" disabled={d.verificationStatus !== 'VERIFIED' || d.isPrimary} onClick={() => run(() => sf.primaryDomain(d.id), 'Primary domain set.', () => { void domains.reload(); })}>Make primary</Button>
            <Button size="small" color="error" onClick={() => { if (window.confirm(`Remove ${d.hostname}?`)) void run(() => sf.removeDomain(d.id), 'Domain removed.', () => { void domains.reload(); }); }}>Remove</Button>
          </Stack>}
        </Paper>
      ))}
    </Stack>
  );
}
