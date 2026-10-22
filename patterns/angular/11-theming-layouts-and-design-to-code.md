# 11 — One theme for every screen, layout routes, and building from a Figma

> **One-liner:** restyle a working app to a design without touching its features: put the design's
> tokens into Material's *system* roles so every component follows, move the page chrome into
> layout routes, and take each screen's spacing and assets from its Figma frame.

**Last updated:** 2026-10-22 · **Roadmap:** UI.1–UI.9
**Code:**
- `src/styles/_tokens.scss`, `src/styles/_theme.scss`, `src/styles.scss`: tokens, the M3 theme, global bits;
- `core/layout/`: `NavBar`, `SiteFooter`, `PublicLayout`, `DashboardLayout`, `PageTitle`, `nav-items.ts`;
- `features/auth/auth-shell/`: the auth pages' split layout.

## 1. Theme at the system level, not per component ⭐⭐⭐

Material 3 components never hard-code a colour: they read **system roles** (`--mat-sys-primary`,
`--mat-sys-surface-container`, …). Our own SCSS did the same since Phase 3. So restyling the app
to the Figma was one file:

```scss
@include mat.theme((color: (theme-type: dark, primary: mat.$yellow-palette), typography: (plain-family: Inter, …)));
@include mat.theme-overrides((primary: #ffd60a, on-primary: #000814, surface: #000814,
  surface-container: #161d29, on-surface-variant: #afb2bf, outline-variant: #2c333f, …));
```

`mat.theme` generates a tonal palette from a seed; `theme-overrides` then **pins** each role to
the exact Figma colour (a generated yellow is close, not `#FFD60A`). Component tokens
(`mat.button-overrides`, `mat.form-field-overrides`) are kept for where the Figma differs from
M3's *component* spec, not its colours: 8 px buttons instead of pills, a 48 px field.

**Rule of thumb:** a colour belongs to a role → override the role. A shape or size of one
component → its component tokens. Neither expresses it (an inset edge, a filled *outlined*
button) → a short global rule, written down as such (`component-surfaces`).

The Figma palette is also emitted as `--mn-<scale>-<step>` custom properties from one SCSS map,
for the places a role doesn't fit (the nav bar is Rich Black 800, which is no role's colour).

## 2. Layout routes ⭐⭐⭐

```ts
{ path: '', component: PublicLayout, children: [ /* home, catalog, auth … */ ] },
{ path: 'courses/:slug/lectures/:lectureId', … },          // no layout: full-screen player
{ path: '', component: DashboardLayout, children: [ /* account, cart, instructor … */ ] },
```

A parent route with a component and `children` renders that component, whose `<router-outlet>`
takes the child. Pages stop including the chrome. Two `''` parents work: if no child of the first
matches the URL, the router **backtracks** and tries the second.

⚠️ Keep the guards on the children. `canMatch: [authGuard]` on the dashboard parent would also run
for a URL *no* child matches (`/nope`), and send a signed-out visitor to log in instead of to the
`**` redirect.

## 3. The page's name lives on the route ⭐⭐

`title: 'Cart'` on a route + a custom `TitleStrategy` (`PageTitle`) = the browser tab
("Cart · Masternova") **and** a signal the dashboard header renders as its `<h1>` and the last
breadcrumb step. A page with its own heading opts out with `data: { pageHeader: false }`.
Registering it: `{ provide: TitleStrategy, useExisting: PageTitle }`; `useExisting` so the router
and the header inject the **same** instance.

## 4. Deferring what most visitors never use ⭐⭐

- The nav bar's search box: `@defer (when searchOpen())`: the autocomplete code downloads the first
  time someone opens search.
- The footer: `@defer (on viewport)` with a 1 px `@placeholder` (viewport triggers need an element
  to watch): its code and its category request wait until someone scrolls down.

## 5. Testing layouts ⭐⭐

`TestBed.createComponent(DashboardLayout)` + navigating renders the layout **twice**: the fixture
and the route's component inside its own outlet. `RouterTestingHarness.create()` renders the route
the way the app does; `harness.routeNativeElement` is the layout with its child.

## 6. Reading the Figma when the MCP is out of calls ⭐

The Figma MCP on a Starter plan ran out after ~15 reads. The REST API with a read-only token
(`file_content:read`) gives more than the MCP's screenshots: `GET /v1/files/:key/nodes?ids=…`
returns every layer's exact geometry, fills, opacity, blend mode and effects; `GET /v1/images`
renders frames; `GET /v1/files/:key/images` lists the image fills. `design-export/` (git-ignored)
holds the scripts and the downloads.

## 7. Pitfalls met

- **macOS `grep -Z`** means *decompress*, not "NUL-separate" — a `grep -lZ | xargs -0` silently did
  nothing.
- **Sass colour names as map keys:** `yellow: (…)` is the *colour* yellow; `--mn-#{$scale}` then
  printed a colour. Quote the keys.
- **A filled field rounds only its top corners** in M3, and MDC's own rule beats a plain class:
  `.mat-mdc-form-field .mdc-text-field--filled { border-radius; overflow: hidden }`.
- **A grid of form fields overflows** with `1fr 1fr`: `1fr` is `minmax(auto, 1fr)`, and a field's
  auto minimum is its intrinsic width. `minmax(0, 1fr)`.
- **`getByLabel('Password')`** also matches a button whose `aria-label` is "Show password", and
  `getByLabel(…, { exact: true })` compares the label's whole text, required `*` included. The e2e
  helper uses `getByRole('textbox', { name: 'Password', exact: true })` (the accessible name skips
  the `aria-hidden` asterisk).
- **JPEG has no alpha.** The hero's "glow" was a transparent noise texture; converted to JPEG it
  became a white sheet. Keep alpha images PNG (or WebP).
- **A screenshot isn't the design.** The same texture is drawn at 20 % opacity in *soft light*;
  the picture's "white block" is a hard drop shadow; the glows are blurred gradient ellipses.
  None of that shows in a PNG export: the node JSON (`opacity`, `blendMode`, `effects`) does.
- **Component style budget (8 kB).** The home page's one stylesheet broke it. Splitting the page
  into section components (`CodePanel`, `CourseHighlights`, `SkillsSection`, `LearnerReviews`)
  fixed it, and gave each a single job, instead of raising the budget.
- **Material Symbols need the FILL axis loaded.** `font-variation-settings: 'FILL' 1` did nothing:
  the stylesheet URL asked for the static font. `…Material+Symbols+Outlined:opsz,wght,FILL,GRAD@…`
  loads the variable one; `star` vs `star_border` is then one glyph, filled or not.
- **A full-bleed band inside a centred grid:** `box-shadow: 0 0 0 100vmax <colour>` + `clip-path:
  inset(0 -100vmax)` paints the band's background to both screen edges while the content stays in
  its column (the course page's header beside its sticky buy box).
- **Virtual scroll and a grid:** the CDK viewport measures *items* of one fixed size. A grid becomes
  a list of rows (`rows = chunk(items, columns())`), with the row height fixed and the column count
  from `BreakpointObserver`; "near the end" is counted in rows.
- **Reserved error space:** Material keeps a line under every field for `<mat-error>`; the Figma's
  20 px gaps need `subscriptSizing: 'dynamic'` (app-wide `MAT_FORM_FIELD_DEFAULT_OPTIONS`).

## 8. Deviations from the StudyNotion Figma

| Screen | Figma | Masternova | Why |
|---|---|---|---|
| All | StudyNotion logo | an "M" mark (`public/logo.svg`) + "Masternova" | branding |
| All | Flaticon UIcons | Material Symbols | the icon font Material already loads |
| Nav bar | About us, Contact us | My Learning (signed in) | no such pages; the learner's courses are one click away |
| Nav bar | a search icon | the icon opens our typeahead in the bar | search is a real feature here |
| Nav bar | signed-out: one "Sign up" | "Log in" + "Sign up" | both are common entry points |
| Nav bar | a photo avatar | initials | users have no picture |
| Footer | StudyNotion's marketing columns | Learn / Teach / Account + the real categories as Subjects; no social icons or legal links | link only to what exists |
| Dashboard sidebar | Wishlist; "Courses" | no wishlist; Cart, My Courses (instructors), Admin (admins) | our features; role-based entries |
| Dashboard sidebar | Settings = profile + password | Settings = email preferences | the API has no profile edit or password change |
| Auth pages | announcement bar ("PI DAY SALE") | none | no promotions |
| Auth pages | Student / Instructor tabs | none | roles are granted by an admin, not chosen at signup |
| Sign up | first + last name, phone number | one display name; no phone | the API's `SignupRequest` |
| Log in | "Forgot password" | none | no password reset in the API |
| Verify email | a 6-box OTP code + "Resend it" | the emailed link verifies on arrival; states shown in the same layout | link-based verification (identity LLD) |
| Auth, Home | photos delivered at 3–4× their size (1.3–9 MB PNG/JPEG) | re-encoded JPEGs at 2× the displayed size (≈ 0.15–0.33 MB each; 22 MB → 0.9 MB for the home page) | identical at the size shown; a page shouldn't download 9 MB for one picture |
| Home | "Learn More" / "Book a Demo" | "Browse Courses" / "Create Account" (signed in: "My Learning") | no demo booking; each button goes somewhere real |
| Home | "Become an Instructor" for everyone | visitors → sign up; instructors → their courses; learners: no link, a hint | the instructor role is granted by an admin |
| Home | HTML snippets in the code panels | an Angular and a Java snippet in the spirit of Masternova's code | it's a code-teaching site about this stack |
| Home | three static course cards ("Learn HTML" …) | the three best-rated published courses | real data |
| Home | timeline: Leadership, Responsibility, Flexibility, Solve the problem; banner "10 years · 250 courses" | Masternova's pillars (Material icons); banner: the real number of subjects, "24/7" | no invented numbers |
| Home | "Your swiss knife for learning any language" (progress / compare / plan cards) | dropped | they advertise language-learning features we don't have |
| Home | review cards with photos and e-mail addresses | the latest reviews of the best-rated course, initials, the course's title | the API has no site-wide feed or avatars; e-mails are private |
| Home | — | the backend status card moved from the home page to the admin page | it was Phase 0's first screen |
| Catalog | a category landing page: carousels "Courses to get you started", "Top courses in …", "Frequently bought together" | one list in the same cards, the header band and tabs on top; filters (search, sub-category, level, price) kept under the tabs | our catalog is a filtered, paged search with its state in the URL (note 04); there are no "bought together" data |
| Catalog | tabs Most popular / New / Trending | the four sorts the API has (Newest, Highest rated, price ↑ / ↓) as tabs | no "trending" signal |
| Catalog | a carousel with arrows | a virtual-scrolled grid: rows of 3 / 2 / 1 cards | dozens of pages: only the visible rows exist in the DOM |
| Catalog | "Related resources" (docs, cheatsheets …) | "Subjects": the other categories | we host no such resources |
| Course cards, buy box | a photo per course; "Bestseller" badge | one of the Figma's 6 photos picked by the course id; a "Free" badge | the API has no cover image (a backend follow-up); no sales ranking |
| Course detail | "What you'll learn" bullet list | the course description in the same frame, titled "About this course" | the API has a description, not learning goals |
| Course detail | "Created at", students count | published month (`publishedAt`), enrolled learners (`enrollmentCount`) | real fields |
| Course detail | "30-Day Money-Back Guarantee"; includes "Full Lifetime access", "Access on Mobile and TV", "Certificate of completion" | no guarantee line; includes: video length, lectures/sections, lifetime access once paid, resume on any device | only what Masternova actually does (no certificates, refunds are an admin action) |
| Course detail | the author's photo and bio | initials and name | the API has neither |
| Course detail | lecture rows expand to a description | no expansion | lectures have no description |
| Course detail | "Add to Cart" primary, "Buy now" secondary | same, plus every other state the access engine can answer (start, continue, enroll for free, renew, sign in to buy …) | the call to action is the entitlement engine's (note 07) |
