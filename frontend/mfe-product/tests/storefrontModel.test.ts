import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  contrastRatio, countNavItems, isPlainText, isSafeUrl, moveSection, newSection, openingHoursProblems, parseIdList, seoPreview, slugify,
  validateNav, validateSections, validateTheme, versionActions, visibleScreens,
} from '../src/storefront/storefrontModel.ts';
import type { NavItem, Section } from '../src/storefront/storefrontModel.ts';

test('menu shows all 17 screens to storefront readers and none to others', () => {
  assert.equal(visibleScreens('X', ['storefront:read']).length, 17);
  assert.equal(visibleScreens('USER', ['catalog:manage']).length, 0);
  assert.equal(visibleScreens('SUPER_ADMIN', undefined).length, 17);
  assert.deepEqual(visibleScreens('X', ['storefront:domain']).map((s) => s.id), ['domains']);
});

test('links must be in-site paths or https; scripts and protocol-relative links are refused', () => {
  for (const ok of ['', '/sale', '/p/summer-sale?x=1', 'https://partner.example/offer']) assert.equal(isSafeUrl(ok), true, ok);
  for (const bad of ['javascript:alert(1)', 'JaVaScRiPt:alert(1)', '//evil.example', 'http://insecure.example', 'data:text/html;base64,AAA', '/../etc', 'https://user:pw@host.example', 'ftp://x.example', '/a\\b'])
    assert.equal(isSafeUrl(bad), false, bad);
  assert.equal(isPlainText('Hello <b>x</b>'), false);
  assert.equal(isPlainText('onclick=alert(1)'), false);
  assert.equal(isPlainText('Fish & chips'), true);
});

test('sections: add with defaults, unique ids, reorder, validate', () => {
  const a = newSection('HERO_BANNER', []);
  const b = newSection('NEW_ARRIVALS', [a]);
  const c = newSection('FEATURED_PRODUCTS', [a, b]);
  assert.deepEqual([a.id, b.id, c.id], ['s1', 's2', 's3']);
  assert.equal(b.props.limit, 8);
  assert.equal(a.layout.mobile.columns, 1);
  assert.deepEqual(moveSection([1, 2, 3], 0, 2), [2, 3, 1]);
  assert.deepEqual(moveSection([1, 2, 3], 5, 0), [1, 2, 3]);

  assert.ok(validateSections([a, b, c]).some((e) => e.includes('pick at least one product')));
  const ok: Section[] = [{ ...a, props: { heading: 'Welcome', buttonUrl: '/sale' } }, { ...c, props: { productIds: [1] } }];
  assert.deepEqual(validateSections(ok), []);
  assert.ok(validateSections([{ ...a, props: { heading: '<script>x</script>' } }]).some((e) => e.includes('markup')));
  assert.ok(validateSections([{ ...a, props: { buttonUrl: 'javascript:alert(1)' } }]).some((e) => e.includes('buttonUrl')));
  assert.ok(validateSections([a, { ...b, id: a.id }]).some((e) => e.includes('duplicate')));
  assert.ok(validateSections([{ ...newSection('COUNTDOWN', []), props: {} }]).some((e) => e.includes('end date')));
  const bad = newSection('HERO_BANNER', []); bad.layout.desktop.columns = 9;
  assert.ok(validateSections([bad]).some((e) => e.includes('columns')));
});

test('navigation validation: depth, targets, unsafe links, markup', () => {
  const leaf = (label: string, url?: string): NavItem => ({ label, url, children: [] });
  assert.deepEqual(validateNav([{ label: 'Shop', url: '/shop', children: [leaf('Sale', '/sale')] }]), []);
  assert.ok(validateNav([leaf('Bad', 'javascript:alert(1)')]).length > 0);
  assert.ok(validateNav([leaf('Empty')]).some((e) => e.includes('needs a link')));
  assert.ok(validateNav([leaf('<b>x</b>', '/x')]).some((e) => e.includes('markup')));
  const deep: NavItem = { label: 'L1', children: [{ label: 'L2', children: [{ label: 'L3', children: [leaf('L4', '/x')] }] }] };
  assert.ok(validateNav([deep]).some((e) => e.includes('3 levels')));
  assert.equal(countNavItems([{ label: 'a', url: '/a', children: [leaf('b', '/b'), leaf('c', '/c')] }]), 3);
});

test('theme: hex colours, supported fonts and contrast', () => {
  assert.deepEqual(validateTheme({ colors: { primary: '#1D4ED8', text: '#0F172A', background: '#FFFFFF' }, headingFont: 'Poppins' }), []);
  assert.ok(validateTheme({ colors: { primary: 'blue' } }).length > 0);
  assert.ok(validateTheme({ headingFont: 'Comic Sans' }).length > 0);
  assert.ok(validateTheme({ colors: { text: '#777777', background: '#888888' } }).some((e) => e.includes('contrast')));
  assert.ok(contrastRatio('#000000', '#FFFFFF') > 20);
  assert.equal(contrastRatio('red', '#FFFFFF'), 0);
});

test('SEO preview gives budgets, fallbacks and noindex warning', () => {
  const p = seoPreview({}, 'Summer Sale', 'https://shop.example.com/', '/p/summer-sale');
  assert.equal(p.title, 'Summer Sale');
  assert.equal(p.url, 'https://shop.example.com/p/summer-sale');
  assert.ok(p.warnings.length >= 2);
  const long = seoPreview({ title: 'x'.repeat(80), description: 'y'.repeat(200), robots: 'NOINDEX_FOLLOW', canonicalUrl: 'http://x.example' }, 'T', 'https://a.example', '/');
  assert.equal(long.title.length, 70);
  assert.equal(long.description.length, 160);
  assert.ok(long.warnings.some((w) => w.includes('excluded')));
  assert.ok(long.warnings.some((w) => w.includes('https')));
});

test('workflow buttons follow status and permission', () => {
  const editor = (p: string) => ['storefront:edit'].includes(p);
  const reviewer = (p: string) => ['storefront:approve', 'storefront:publish'].includes(p);
  assert.deepEqual(versionActions('DRAFT', false, editor), ['submit']);
  assert.deepEqual(versionActions('DRAFT', false, reviewer), []);
  assert.deepEqual(versionActions('IN_REVIEW', false, reviewer), ['approve', 'send back']);
  assert.deepEqual(versionActions('APPROVED', false, reviewer), ['publish now', 'schedule']);
  assert.deepEqual(versionActions('APPROVED', true, reviewer), ['cancel schedule']);
  assert.deepEqual(versionActions('ARCHIVED', false, reviewer), ['restore']);
  assert.deepEqual(versionActions('PUBLISHED', false, reviewer), []);
});

test('helpers: slugs, id lists, opening hours', () => {
  assert.equal(slugify('  Summer Sale 2026! '), 'summer-sale-2026');
  assert.equal(slugify('Café & Bar'), 'cafe-bar');
  assert.deepEqual(parseIdList('1, 2;2 x 0 -3 4.5 7'), [1, 2, 7]);
  assert.deepEqual(openingHoursProblems({ MON: { open: '09:00', close: '22:00' }, SUN: { closed: true } }), []);
  assert.equal(openingHoursProblems({ MON: { open: '25:00', close: '22:00' } }).length, 1);
  assert.equal(openingHoursProblems({ TUE: { open: '22:00', close: '09:00' } }).length, 1);
});
