// Pure logic for Storefront Management (no React, so it is unit-testable with node --test).
// The server re-validates everything; this module only gives editors fast, accurate feedback.

export type ScreenId =
  | 'dashboard' | 'overview' | 'homepage' | 'landing' | 'banners' | 'navigation' | 'collections' | 'products' | 'restaurant'
  | 'theme' | 'content' | 'seo' | 'domains' | 'localization' | 'publishing' | 'history' | 'settings';

export interface ScreenDefinition { id: ScreenId; label: string; anyOf: string[] }

export const SCREENS: readonly ScreenDefinition[] = [
  { id: 'dashboard', label: 'Storefront Dashboard', anyOf: ['storefront:read'] },
  { id: 'overview', label: 'Website Overview', anyOf: ['storefront:read'] },
  { id: 'homepage', label: 'Homepage Builder', anyOf: ['storefront:read'] },
  { id: 'landing', label: 'Landing Pages', anyOf: ['storefront:read'] },
  { id: 'banners', label: 'Banner Management', anyOf: ['storefront:read'] },
  { id: 'navigation', label: 'Navigation Management', anyOf: ['storefront:read'] },
  { id: 'collections', label: 'Category & Collection Display', anyOf: ['storefront:read'] },
  { id: 'products', label: 'Product Display Settings', anyOf: ['storefront:read'] },
  { id: 'restaurant', label: 'Restaurant Menu Display', anyOf: ['storefront:read'] },
  { id: 'theme', label: 'Theme & Branding', anyOf: ['storefront:read'] },
  { id: 'content', label: 'Content Management', anyOf: ['storefront:read'] },
  { id: 'seo', label: 'SEO Management', anyOf: ['storefront:read'] },
  { id: 'domains', label: 'Domain Management', anyOf: ['storefront:domain', 'storefront:read'] },
  { id: 'localization', label: 'Localization', anyOf: ['storefront:read'] },
  { id: 'publishing', label: 'Preview & Publishing', anyOf: ['storefront:read'] },
  { id: 'history', label: 'Version History', anyOf: ['storefront:read'] },
  { id: 'settings', label: 'Storefront Settings', anyOf: ['storefront:read'] },
];

export function can(role: string | undefined, permissions: readonly string[] | undefined, wanted: string): boolean {
  if (role === 'SUPER_ADMIN') return true;
  if (!permissions) return false;
  return permissions.includes('*') || permissions.includes(wanted);
}

export function visibleScreens(role: string | undefined, permissions: readonly string[] | undefined): ScreenDefinition[] {
  return SCREENS.filter((s) => s.anyOf.some((p) => can(role, permissions, p)));
}

// ---- sections ------------------------------------------------------------------------------------------------------

export type SectionType =
  | 'HERO_BANNER' | 'PROMO_BANNER' | 'FEATURED_PRODUCTS' | 'FEATURED_CATEGORIES' | 'PRODUCT_GRID' | 'BEST_SELLERS' | 'NEW_ARRIVALS'
  | 'TRENDING' | 'TESTIMONIALS' | 'BRAND_HIGHLIGHTS' | 'COUNTDOWN' | 'NEWSLETTER' | 'CUSTOM_CONTENT';

export interface Section {
  id: string; type: SectionType; visible: boolean;
  layout: { desktop: { columns: number; visible: boolean }; mobile: { columns: number; visible: boolean } };
  props: Record<string, any>;
}

export const SECTION_LABELS: Record<SectionType, string> = {
  HERO_BANNER: 'Hero banner', PROMO_BANNER: 'Promotional banner', FEATURED_PRODUCTS: 'Featured products', FEATURED_CATEGORIES: 'Featured categories',
  PRODUCT_GRID: 'Product grid', BEST_SELLERS: 'Best sellers', NEW_ARRIVALS: 'New arrivals', TRENDING: 'Trending products', TESTIMONIALS: 'Testimonials',
  BRAND_HIGHLIGHTS: 'Brand highlights', COUNTDOWN: 'Promotional countdown', NEWSLETTER: 'Newsletter subscription', CUSTOM_CONTENT: 'Custom content',
};

/** Sections that show catalog items, used by the "Category & Collection Display" screen. */
export const COLLECTION_TYPES: readonly SectionType[] = ['FEATURED_CATEGORIES', 'PRODUCT_GRID', 'FEATURED_PRODUCTS', 'NEW_ARRIVALS', 'BEST_SELLERS', 'TRENDING'];

export function newSectionId(existing: readonly Section[]): string {
  const used = new Set(existing.map((s) => s.id));
  let n = existing.length + 1;
  while (used.has(`s${n}`)) n += 1;
  return `s${n}`;
}

export function newSection(type: SectionType, existing: readonly Section[]): Section {
  const props: Record<string, any> = {};
  if (type === 'NEW_ARRIVALS') props.limit = 8;
  if (type === 'PRODUCT_GRID') { props.limit = 12; props.sort = 'NEWEST'; }
  if (['FEATURED_PRODUCTS', 'BEST_SELLERS', 'TRENDING'].includes(type)) props.productIds = [];
  if (type === 'FEATURED_CATEGORIES') props.categoryIds = [];
  if (['TESTIMONIALS', 'BRAND_HIGHLIGHTS'].includes(type)) props.items = [];
  if (type === 'CUSTOM_CONTENT') props.blocks = [];
  return { id: newSectionId(existing), type, visible: true, layout: { desktop: { columns: 3, visible: true }, mobile: { columns: 1, visible: true } }, props };
}

export function moveSection<T>(list: readonly T[], from: number, to: number): T[] {
  if (from < 0 || from >= list.length || to < 0 || to >= list.length || from === to) return [...list];
  const copy = [...list];
  const [item] = copy.splice(from, 1);
  copy.splice(to, 0, item);
  return copy;
}

const MARKUP = /(<|>|javascript:|data:text|vbscript:|on[a-z]+\s*=|&#x?[0-9a-f]+;)/i;

export function isPlainText(value: string): boolean { return !MARKUP.test(value); }

/** Same rule as the server: an in-site path or an https URL; nothing else. */
export function isSafeUrl(value: string): boolean {
  const v = value.trim();
  if (v === '') return true;
  if (!isPlainText(v) || v.length > 500) return false;
  if (v.startsWith('/')) return !v.startsWith('//') && !v.includes('\\') && !v.includes('..');
  if (!v.startsWith('https://')) return false;
  try { const u = new URL(v); return u.hostname !== '' && u.username === '' && u.password === ''; } catch { return false; }
}

export function parseIdList(text: string): number[] {
  return [...new Set(text.split(/[\s,;]+/).filter(Boolean).map(Number).filter((n) => Number.isInteger(n) && n > 0))];
}

/** Editor-side checks that mirror the server's section rules. Returns human-readable problems. */
export function validateSections(sections: readonly Section[]): string[] {
  const errors: string[] = [];
  if (sections.length > 50) errors.push('A page can have at most 50 sections.');
  const ids = new Set<string>();
  sections.forEach((s, i) => {
    const label = `${i + 1}. ${SECTION_LABELS[s.type] ?? s.type}`;
    if (ids.has(s.id)) errors.push(`${label}: duplicate section id ${s.id}.`);
    ids.add(s.id);
    for (const [key, value] of Object.entries(s.props)) {
      if (typeof value === 'string' && !isPlainText(value)) errors.push(`${label}: "${key}" contains markup or script-like text, which is not allowed.`);
      if (/url$/i.test(key) && typeof value === 'string' && !isSafeUrl(value)) errors.push(`${label}: "${key}" must be a path starting with / or an https:// link.`);
    }
    if (['BEST_SELLERS', 'TRENDING', 'FEATURED_PRODUCTS'].includes(s.type) && (s.props.productIds ?? []).length === 0) errors.push(`${label}: pick at least one product.`);
    if (s.type === 'COUNTDOWN' && (!s.props.endsAt || Number.isNaN(Date.parse(s.props.endsAt)))) errors.push(`${label}: set a valid end date and time.`);
    for (const device of ['desktop', 'mobile'] as const) {
      const c = s.layout[device].columns;
      if (!Number.isInteger(c) || c < 1 || c > 6) errors.push(`${label}: ${device} columns must be 1-6.`);
    }
  });
  return errors;
}

// ---- navigation ------------------------------------------------------------------------------------------------------

export interface NavItem { label: string; url?: string; categoryId?: number; promotional?: boolean; children: NavItem[] }

export function validateNav(items: readonly NavItem[], depth = 1, counter = { n: 0 }): string[] {
  const errors: string[] = [];
  if (depth > 3) return ['Navigation can be at most 3 levels deep.'];
  for (const item of items) {
    counter.n += 1;
    if (counter.n > 100) { errors.push('At most 100 navigation items.'); break; }
    if (item.label.trim() === '') errors.push('Every item needs a label.');
    else if (!isPlainText(item.label)) errors.push(`"${item.label}" contains markup, which is not allowed.`);
    if (item.url && !isSafeUrl(item.url)) errors.push(`"${item.label}": link must be a path starting with / or an https:// link.`);
    if (!item.url && !item.categoryId && item.children.length === 0) errors.push(`"${item.label}" needs a link, a category or sub-items.`);
    errors.push(...validateNav(item.children, depth + 1, counter));
  }
  return errors;
}

export function countNavItems(items: readonly NavItem[]): number {
  return items.reduce((n, i) => n + 1 + countNavItems(i.children), 0);
}

// ---- theme, SEO and misc ----------------------------------------------------------------------------------------------

export const FONTS = ['Inter', 'Roboto', 'Open Sans', 'Lato', 'Montserrat', 'Poppins', 'Merriweather', 'Playfair Display', 'Source Sans 3', 'Nunito'] as const;
export const HEX = /^#[0-9a-fA-F]{6}$/;

/** WCAG contrast ratio between two #RRGGBB colours (4.5 is the AA minimum for body text). */
export function contrastRatio(a: string, b: string): number {
  const lum = (hex: string) => {
    const [r, g, bl] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  if (!HEX.test(a) || !HEX.test(b)) return 0;
  const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

export function validateTheme(theme: { colors?: Record<string, string>; headingFont?: string; bodyFont?: string }): string[] {
  const errors: string[] = [];
  for (const [k, v] of Object.entries(theme.colors ?? {})) if (v && !HEX.test(v)) errors.push(`Colour ${k} must be #RRGGBB.`);
  for (const f of [theme.headingFont, theme.bodyFont]) if (f && !(FONTS as readonly string[]).includes(f)) errors.push(`Font "${f}" is not supported.`);
  const c = theme.colors ?? {};
  if (c.text && c.background && HEX.test(c.text) && HEX.test(c.background) && contrastRatio(c.text, c.background) < 4.5) errors.push('Text and background colours have low contrast (below WCAG AA 4.5:1).');
  return errors;
}

export function slugify(title: string): string {
  return title.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80);
}

export interface SeoInput { title?: string; description?: string; canonicalUrl?: string; robots?: string }

/** Snippet preview and character budgets (search engines truncate around 60 / 160 characters). */
export function seoPreview(seo: SeoInput, fallbackTitle: string, baseUrl: string, path: string) {
  const title = (seo.title?.trim() || fallbackTitle).slice(0, 70);
  const description = (seo.description ?? '').slice(0, 160);
  const warnings: string[] = [];
  if (!seo.title?.trim()) warnings.push('No SEO title: the page title is used.');
  if ((seo.title ?? '').length > 60) warnings.push('Title is over 60 characters and may be truncated in search results.');
  if (!seo.description?.trim()) warnings.push('Add a meta description (aim for 120-160 characters).');
  if ((seo.description ?? '').length > 160) warnings.push('Description is over 160 characters.');
  if (seo.canonicalUrl && !seo.canonicalUrl.startsWith('https://')) warnings.push('Canonical URL must be an absolute https URL.');
  if ((seo.robots ?? '').startsWith('NOINDEX')) warnings.push('This page is excluded from search engines and from the sitemap.');
  return { title, description, url: `${baseUrl.replace(/\/$/, '')}${path}`, warnings };
}

export function statusTone(status: string): 'default' | 'success' | 'warning' | 'error' | 'info' {
  switch (status) {
    case 'PUBLISHED': case 'VERIFIED': case 'ACTIVE': return 'success';
    case 'IN_REVIEW': case 'APPROVED': case 'PENDING': return 'warning';
    case 'FAILED': return 'error';
    case 'ARCHIVED': return 'default';
    default: return 'info';
  }
}

/** Which workflow buttons make sense for a version, given the user's permissions. */
export function versionActions(status: string, scheduled: boolean, can2: (p: string) => boolean): string[] {
  const out: string[] = [];
  if (status === 'DRAFT' && can2('storefront:edit')) out.push('submit');
  if (status === 'IN_REVIEW') { if (can2('storefront:approve')) out.push('approve', 'send back'); if (can2('storefront:edit')) out.push('withdraw'); }
  if (status === 'APPROVED') {
    if (can2('storefront:publish')) out.push(...(scheduled ? ['cancel schedule'] : ['publish now', 'schedule']));
    if (can2('storefront:edit') && !scheduled) out.push('withdraw');
  }
  if (status === 'ARCHIVED' && can2('storefront:publish')) out.push('restore');
  return out;
}

export function openingHoursProblems(hours: Record<string, { open?: string; close?: string; closed?: boolean }>): string[] {
  const errors: string[] = [];
  const t = /^([01]\d|2[0-3]):[0-5]\d$/;
  for (const [day, h] of Object.entries(hours)) {
    if (h.closed) continue;
    if (!h.open || !h.close || !t.test(h.open) || !t.test(h.close)) errors.push(`${day}: use HH:mm for both times.`);
    else if (h.open >= h.close) errors.push(`${day}: opening time must be before closing time.`);
  }
  return errors;
}
