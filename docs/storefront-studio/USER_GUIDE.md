# Storefront Management — user guide

Open **Operations → Storefront Management**. Which screens you see depends on your role: *Content Editor* (edit drafts, upload images), *Storefront Manager* (create storefronts, domains, settings), *Storefront Publisher* (review, publish, restore, audit).

1. **Create a storefront** (first visit): name, address name (becomes `address.<platform-domain>`), your store ID, Retail or Restaurant. One storefront per store; create more for more stores and switch with the selector.
2. **Upload images** in *Content Management* (choose what the image is for; add alt text — it is required). Wrong size, wrong format or files over 5 MB are refused with the reason.
3. **Homepage Builder / Landing Pages**: add sections, move them up/down, hide them, set columns and visibility per desktop and mobile. Text is plain text; links must start with `/` or `https://`. *Save draft* stores your work privately.
4. **Theme & Branding**: pick a preset or set colours, fonts, layouts; the contrast badge warns below WCAG AA. **Navigation**: up to three levels; promo items are flagged.
5. **Banners**: desktop image required, mobile optional, start/end dates, priority (Up/Down), Active switch. **Product Display / Restaurant Menu Display** set how catalog data is shown; **SEO** has a live search-result preview with length hints.
6. **Preview & Publishing**: choose a page → *Preview* (desktop/mobile) → *Submit* → a **different** person *Approves* (or *Sends back* with a reason) → *Publish now* or *Schedule*. Until publishing, visitors keep seeing the previous published version. **Version History** lets you compare any two versions and *Restore* an earlier one (it becomes a new live version).
7. **Domains**: add a domain, create the TXT and CNAME records shown, press *Verify*, then *Check SSL*, then *Make primary*. A domain is only shown verified/active after the platform actually checked it.

Not available yet: dish-level restaurant menus, per-language content, S3 image storage, and drag-and-drop reordering (use Up/Down).
