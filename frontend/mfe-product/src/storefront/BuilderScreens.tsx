import { useState } from 'react';
import {
  Alert, Box, Button, Checkbox, FormControlLabel, IconButton, MenuItem, Paper, Select, Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import { ArrowDownwardRounded, ArrowUpwardRounded, DeleteOutlineRounded } from '@mui/icons-material';
import { DataTable, Loading, useLoad } from '../pricing/common';
import { StatusChip, DocumentPanel, findDoc, useDocuments, useNotice } from './common';
import type { ScreenProps } from './common';
import { sf } from './storefrontApi';
import type { Row } from './storefrontApi';
import { COLLECTION_TYPES, SECTION_LABELS, moveSection, newSection, parseIdList, slugify, validateSections } from './storefrontModel';
import type { Section, SectionType } from './storefrontModel';

type FieldKind = 'text' | 'url' | 'number' | 'ids' | 'datetime' | 'sort';
const FIELDS: Record<SectionType, { key: string; label: string; kind: FieldKind }[]> = {
  HERO_BANNER: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'subheading', label: 'Subheading', kind: 'text' }, { key: 'buttonLabel', label: 'Button label', kind: 'text' }, { key: 'buttonUrl', label: 'Button link', kind: 'url' }, { key: 'altText', label: 'Image description', kind: 'text' }],
  PROMO_BANNER: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'subheading', label: 'Subheading', kind: 'text' }, { key: 'buttonLabel', label: 'Button label', kind: 'text' }, { key: 'buttonUrl', label: 'Button link', kind: 'url' }, { key: 'altText', label: 'Image description', kind: 'text' }],
  FEATURED_PRODUCTS: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'productIds', label: 'Product IDs (in display order)', kind: 'ids' }],
  BEST_SELLERS: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'productIds', label: 'Product IDs (hand-picked)', kind: 'ids' }],
  TRENDING: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'productIds', label: 'Product IDs (hand-picked)', kind: 'ids' }],
  NEW_ARRIVALS: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'limit', label: 'How many (1-24)', kind: 'number' }],
  PRODUCT_GRID: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'categoryId', label: 'Category ID (optional)', kind: 'number' }, { key: 'limit', label: 'How many (1-48)', kind: 'number' }, { key: 'sort', label: 'Sort', kind: 'sort' }],
  FEATURED_CATEGORIES: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'categoryIds', label: 'Category IDs', kind: 'ids' }],
  TESTIMONIALS: [{ key: 'heading', label: 'Heading', kind: 'text' }],
  BRAND_HIGHLIGHTS: [{ key: 'heading', label: 'Heading', kind: 'text' }],
  COUNTDOWN: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'endsAt', label: 'Ends at', kind: 'datetime' }, { key: 'buttonLabel', label: 'Button label', kind: 'text' }, { key: 'buttonUrl', label: 'Button link', kind: 'url' }],
  NEWSLETTER: [{ key: 'heading', label: 'Heading', kind: 'text' }, { key: 'description', label: 'Description', kind: 'text' }, { key: 'buttonLabel', label: 'Button label', kind: 'text' }],
  CUSTOM_CONTENT: [],
};

function SectionCard({ section, index, total, storefront, onChange, onMove, onDelete, editable }: {
  section: Section; index: number; total: number; storefront: Row; editable: boolean;
  onChange: (s: Section) => void; onMove: (to: number) => void; onDelete: () => void;
}) {
  const media = useLoad(() => sf.media(storefront.id), [storefront.id]);
  const set = (key: string, value: unknown) => onChange({ ...section, props: { ...section.props, [key]: value } });
  const hasImages = section.type === 'HERO_BANNER' || section.type === 'PROMO_BANNER';
  const pick = (key: string, kind: string) => (
    <Select size="small" displayEmpty disabled={!editable} value={section.props[key] ?? ''} onChange={(e) => set(key, e.target.value === '' ? undefined : Number(e.target.value))} sx={{ minWidth: 200 }}>
      <MenuItem value="">{kind === 'BANNER_DESKTOP' ? 'Desktop image…' : 'Mobile image…'}</MenuItem>
      {(media.data ?? []).filter((m) => m.kind === kind).map((m) => <MenuItem key={m.id} value={m.id}>{m.fileName} ({m.width}×{m.height})</MenuItem>)}
    </Select>
  );
  return (
    <Paper variant="outlined" sx={{ p: 2, opacity: section.visible ? 1 : 0.6 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
        <Typography variant="subtitle2" sx={{ flex: 1 }}>{index + 1}. {SECTION_LABELS[section.type]}</Typography>
        <Tooltip title="Move up"><span><IconButton size="small" disabled={!editable || index === 0} onClick={() => onMove(index - 1)}><ArrowUpwardRounded fontSize="small" /></IconButton></span></Tooltip>
        <Tooltip title="Move down"><span><IconButton size="small" disabled={!editable || index === total - 1} onClick={() => onMove(index + 1)}><ArrowDownwardRounded fontSize="small" /></IconButton></span></Tooltip>
        <Tooltip title="Delete section"><span><IconButton size="small" color="error" disabled={!editable} onClick={() => { if (window.confirm(`Delete "${SECTION_LABELS[section.type]}"?`)) onDelete(); }}><DeleteOutlineRounded fontSize="small" /></IconButton></span></Tooltip>
      </Stack>
      <Stack spacing={1}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} flexWrap="wrap">
          {FIELDS[section.type].map((f) => {
            const value = section.props[f.key];
            if (f.kind === 'sort') return (
              <Select key={f.key} size="small" disabled={!editable} value={value ?? 'NEWEST'} onChange={(e) => set(f.key, e.target.value)}>
                {['NEWEST', 'PRICE_ASC', 'PRICE_DESC', 'NAME'].map((s) => <MenuItem key={s} value={s}>{s.replace('_', ' ')}</MenuItem>)}
              </Select>
            );
            if (f.kind === 'ids') return <TextField key={f.key} size="small" label={f.label} disabled={!editable} defaultValue={(value ?? []).join(', ')} onBlur={(e) => set(f.key, parseIdList(e.target.value))} />;
            if (f.kind === 'number') return <TextField key={f.key} size="small" type="number" label={f.label} disabled={!editable} value={value ?? ''} onChange={(e) => set(f.key, e.target.value === '' ? undefined : Number(e.target.value))} />;
            if (f.kind === 'datetime') return <TextField key={f.key} size="small" type="datetime-local" label={f.label} InputLabelProps={{ shrink: true }} disabled={!editable}
              value={value ? String(value).slice(0, 16) : ''} onChange={(e) => set(f.key, e.target.value ? new Date(e.target.value).toISOString() : undefined)} />;
            return <TextField key={f.key} size="small" label={f.label} disabled={!editable} value={value ?? ''} onChange={(e) => set(f.key, e.target.value)} />;
          })}
        </Stack>
        {hasImages && <Stack direction="row" spacing={1}>{pick('desktopMediaId', 'BANNER_DESKTOP')}{pick('mobileMediaId', 'BANNER_MOBILE')}</Stack>}
        {section.type === 'TESTIMONIALS' && <ItemList editable={editable} items={section.props.items ?? []} fields={[['quote', 'Quote'], ['author', 'Author'], ['role', 'Role']]} onChange={(items) => set('items', items)} />}
        {section.type === 'BRAND_HIGHLIGHTS' && <ItemList editable={editable} items={section.props.items ?? []} fields={[['title', 'Title'], ['description', 'Description']]} onChange={(items) => set('items', items)} />}
        {section.type === 'CUSTOM_CONTENT' && <BlockList editable={editable} blocks={section.props.blocks ?? []} onChange={(b) => set('blocks', b)} />}
        <Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
          <FormControlLabel control={<Checkbox disabled={!editable} checked={section.visible} onChange={(e) => onChange({ ...section, visible: e.target.checked })} />} label="Visible" />
          {(['desktop', 'mobile'] as const).map((d) => (
            <Stack key={d} direction="row" spacing={1} alignItems="center">
              <FormControlLabel control={<Checkbox disabled={!editable} checked={section.layout[d].visible} onChange={(e) => onChange({ ...section, layout: { ...section.layout, [d]: { ...section.layout[d], visible: e.target.checked } } })} />} label={`Show on ${d}`} />
              <TextField size="small" type="number" label={`${d} columns`} sx={{ width: 120 }} disabled={!editable} value={section.layout[d].columns}
                onChange={(e) => onChange({ ...section, layout: { ...section.layout, [d]: { ...section.layout[d], columns: Number(e.target.value) } } })} />
            </Stack>
          ))}
        </Stack>
      </Stack>
    </Paper>
  );
}

function ItemList({ items, fields, onChange, editable }: { items: Row[]; fields: [string, string][]; onChange: (i: Row[]) => void; editable: boolean }) {
  return (
    <Stack spacing={1}>
      {items.map((item, i) => (
        <Stack key={i} direction="row" spacing={1}>
          {fields.map(([k, label]) => <TextField key={k} size="small" label={label} disabled={!editable} value={item[k] ?? ''} onChange={(e) => onChange(items.map((x, j) => (j === i ? { ...x, [k]: e.target.value } : x)))} />)}
          <Button size="small" color="error" disabled={!editable} onClick={() => onChange(items.filter((_, j) => j !== i))}>Remove</Button>
        </Stack>
      ))}
      <Button size="small" sx={{ alignSelf: 'flex-start' }} disabled={!editable || items.length >= 12} onClick={() => onChange([...items, {}])}>Add item</Button>
    </Stack>
  );
}

function BlockList({ blocks, onChange, editable }: { blocks: Row[]; onChange: (b: Row[]) => void; editable: boolean }) {
  return (
    <Stack spacing={1}>
      <Typography variant="caption" color="text.secondary">Plain text only. Markup and scripts are rejected; use blocks for structure.</Typography>
      {blocks.map((b, i) => (
        <Stack key={i} direction="row" spacing={1}>
          <Select size="small" disabled={!editable} value={b.type ?? 'PARAGRAPH'} onChange={(e) => onChange(blocks.map((x, j) => (j === i ? { type: e.target.value } : x)))}>
            {['HEADING', 'PARAGRAPH', 'BUTTON'].map((t) => <MenuItem key={t} value={t}>{t.toLowerCase()}</MenuItem>)}
          </Select>
          {b.type === 'BUTTON'
            ? <><TextField size="small" label="Label" disabled={!editable} value={b.label ?? ''} onChange={(e) => onChange(blocks.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))} />
              <TextField size="small" label="Link" disabled={!editable} value={b.url ?? ''} onChange={(e) => onChange(blocks.map((x, j) => (j === i ? { ...x, url: e.target.value } : x)))} /></>
            : <TextField size="small" fullWidth multiline label="Text" disabled={!editable} value={b.text ?? ''} onChange={(e) => onChange(blocks.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)))} />}
          <Button size="small" color="error" disabled={!editable} onClick={() => onChange(blocks.filter((_, j) => j !== i))}>Remove</Button>
        </Stack>
      ))}
      <Button size="small" sx={{ alignSelf: 'flex-start' }} disabled={!editable || blocks.length >= 30} onClick={() => onChange([...blocks, { type: 'PARAGRAPH', text: '' }])}>Add block</Button>
    </Stack>
  );
}

/** Visual section editor shared by the homepage, landing pages and the collection screen (which edits only catalog sections). */
export function SectionBuilder({ storefront, doc, can, only }: { storefront: Row; doc: Row; can: (p: string) => boolean; only?: readonly SectionType[] }) {
  const [adding, setAdding] = useState<SectionType>('HERO_BANNER');
  const types = (only ?? (Object.keys(SECTION_LABELS) as SectionType[]));
  return (
    <DocumentPanel doc={doc} can={can} validate={(c) => validateSections(c.sections ?? [])}>
      {({ content, setContent, editable }) => {
        const sections: Section[] = content.sections ?? [];
        const update = (next: Section[]) => setContent({ ...content, sections: next });
        const shown = sections.map((s, i) => ({ s, i })).filter(({ s }) => !only || only.includes(s.type));
        return (
          <Stack spacing={2}>
            {only && sections.length > shown.length && <Alert severity="info">{sections.length - shown.length} other section(s) are kept as they are; edit them in the Homepage Builder.</Alert>}
            {shown.map(({ s, i }) => (
              <SectionCard key={s.id} section={s} index={i} total={sections.length} storefront={storefront} editable={editable}
                onChange={(next) => update(sections.map((x, j) => (j === i ? next : x)))} onMove={(to) => update(moveSection(sections, i, to))} onDelete={() => update(sections.filter((_, j) => j !== i))} />
            ))}
            {shown.length === 0 && <Typography color="text.secondary">No sections yet.</Typography>}
            {editable && (
              <Stack direction="row" spacing={1}>
                <Select size="small" value={adding} onChange={(e) => setAdding(e.target.value as SectionType)}>{types.map((t) => <MenuItem key={t} value={t}>{SECTION_LABELS[t]}</MenuItem>)}</Select>
                <Button variant="outlined" disabled={sections.length >= 50} onClick={() => update([...sections, newSection(adding, sections)])}>Add section</Button>
              </Stack>
            )}
          </Stack>
        );
      }}
    </DocumentPanel>
  );
}

export function HomepageScreen({ storefront, can, collections }: ScreenProps & { collections?: boolean }) {
  const docs = useDocuments(storefront.id);
  const home = findDoc(docs.data, 'HOMEPAGE');
  return (
    <>
      <Loading loading={docs.loading} error={docs.error} />
      {collections && <Alert severity="info" sx={{ mb: 2 }}>Choose how categories and product collections appear. These are sections of the homepage; products and categories come live from the catalog, so prices and stock are never copied here.</Alert>}
      {home && <SectionBuilder storefront={storefront} doc={home} can={can} only={collections ? COLLECTION_TYPES : undefined} />}
    </>
  );
}

export function LandingScreen({ storefront, can }: ScreenProps) {
  const docs = useDocuments(storefront.id);
  const [selected, setSelected] = useState<number | null>(null);
  const [form, setForm] = useState({ title: '', slug: '' });
  const { run, view } = useNotice();
  const pages = (docs.data ?? []).filter((d) => d.docType === 'LANDING');
  const current = pages.find((p) => p.id === selected);
  return (
    <Stack spacing={2}>
      {view}
      <Loading loading={docs.loading} error={docs.error} />
      {can('storefront:edit') && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
            <TextField size="small" label="Page title" value={form.title} onChange={(e) => setForm({ title: e.target.value, slug: form.slug === slugify(form.title) || form.slug === '' ? slugify(e.target.value) : form.slug })} />
            <TextField size="small" label="URL slug" helperText="Lowercase letters, digits and hyphens" value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} />
            <Button variant="contained" disabled={form.title.trim() === '' || !/^[a-z0-9]+(-[a-z0-9]+)*$/.test(form.slug)}
              onClick={() => { void run(() => sf.createPage(storefront.id, form), 'Landing page created as a draft.', () => { setForm({ title: '', slug: '' }); void docs.reload(); }); }}>Create page</Button>
          </Stack>
        </Paper>
      )}
      <DataTable rows={pages} empty="No landing pages yet." columns={[
        { header: 'Title', render: (r) => r.title }, { header: 'URL', render: (r) => `/p/${r.slug}` },
        { header: 'Live version', render: (r) => (r.publishedVersionNo ? `v${r.publishedVersionNo}` : 'Not published') },
        { header: 'Latest', render: (r) => <StatusChip status={r.latestVersion.status} /> },
        { header: '', render: (r) => <Button size="small" onClick={() => setSelected(r.id)}>Edit</Button> },
      ]} />
      {current && <Box><Typography variant="h6" gutterBottom>{current.title}</Typography><SectionBuilder storefront={storefront} doc={current} can={can} /></Box>}
    </Stack>
  );
}
