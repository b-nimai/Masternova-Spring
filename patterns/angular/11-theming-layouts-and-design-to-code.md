# 11 — One theme for every screen, layout routes, and building from a Figma

> **One-liner:** restyle a working app to a design without touching its features: put the design's
> tokens into Material's *system* roles so every component follows, move the page chrome into
> layout routes, and take each screen's spacing and assets from its Figma frame.

**Last updated:** 2026-10-22 · **Roadmap:** UI.1–UI.11 (Phase UI)
**Code:**
- `src/styles/_tokens.scss`, `src/styles/_theme.scss`, `src/styles.scss`: tokens, the M3 theme, global bits;
- `core/layout/`: `NavBar`, `SiteFooter`, `PublicLayout`, `DashboardLayout`, `PageTitle`, `nav-items.ts`;
- `features/auth/auth-shell/`: the auth pages' split layout;
- `features/home/` (section components), `features/catalog/`, `features/learn/lecture-sidebar/`;
- `shared/star-rating/`, `shared/course-cover.ts`, `shared/initials.ts`;
- `e2e/tests/a11y.spec.ts`: axe-core on every main page.

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
- **Theme the component, not each page.** Five pages still used `mat-card`; `mat.card-overrides`
  (outlined: Rich Black 800, a 700 outline, 8 px) turned them all into the Figma's dashboard box,
  and `mat.table-overrides` did the same for the orders table.
- **Stepper icons and lines.** A finished step shows Material's pencil ("edit") unless the stepper
  overrides it with `<ng-template matStepperIcon="edit">`. With `labelPosition="bottom"` the
  connector isn't `.mat-stepper-horizontal-line` but each header's `::before` / `::after`, under
  `:not(:first-child)` selectors that beat a plain rule.
- **Prettier changes the text you search for.** A scripted replace of `<mat-stepper … >` found
  nothing once Prettier had wrapped the tag over lines; it failed silently. Match with a pattern,
  or assert the replace happened.
- **Fields on a card.** A Rich Black 800 field on a Rich Black 800 card disappears; the Figma lifts
  it to 700. One custom property on the surface does it:
  `.mat-mdc-card, .mn-on-surface { --mat-form-field-filled-container-color: … }`.
- **Reserved error space:** Material keeps a line under every field for `<mat-error>`; the Figma's
  20 px gaps need `subscriptSizing: 'dynamic'` (app-wide `MAT_FORM_FIELD_DEFAULT_OPTIONS`).

## 8. The design vs WCAG: test the contrast ⭐⭐

A dark design's secondary text is where contrast fails, and a token change breaks it without a
single unit test noticing. `e2e/tests/a11y.spec.ts` runs axe-core (WCAG 2.1 A + AA) on every main
page, public, learner and instructor; serious and critical findings fail the build.

Its first run failed on the Figma's own colours:

| Text on | needs at least (4.5:1) | the Figma used |
|---|---|---|
| Rich Black 900 | Rich Black 300 (5.7:1) | 400 (4.2:1) for card meta, cart count |
| Rich Black 800 | Rich Black 300 (4.8:1) | 400 / 500 / 600 for footer links, card text, labels (2.0–3.5:1) |
| Rich Black 700 | Rich Black 200 (4.7:1) | — |
| white on Pink | Pink 300 (4.5:1) | Pink 100 for the badge (2.8:1) → Pink 400 |

Each change carries a one-line comment at the rule (`// WCAG AA: …`). Breadcrumb separators stay
dark: they're `aria-hidden` decoration.

## 9. Deviations from the StudyNotion Figma

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
| Sign up | Student / Instructor tabs that pick the account type | the same tabs (`mat-button-toggle-group`, the shared `.mn-pills` style); **Instructor files an application** an admin approves (UI.10); the account is a learner meanwhile | no one grants themself a role: the identity LLD's "Becoming an instructor" |
| Sign up | first + last name, phone number | one display name; no phone | the API's `SignupRequest` |
| Log in | "Forgot password" | none | no password reset in the API |
| Verify email | a 6-box OTP code + "Resend it" | the emailed link verifies on arrival; states shown in the same layout | link-based verification (identity LLD) |
| Auth, Home | photos delivered at 3–4× their size (1.3–9 MB PNG/JPEG) | re-encoded JPEGs at 2× the displayed size (≈ 0.15–0.33 MB each; 22 MB → 0.9 MB for the home page) | identical at the size shown; a page shouldn't download 9 MB for one picture |
| Home | "Learn More" / "Book a Demo" | "Browse Courses" / "Create Account" (signed in: "My Learning") | no demo booking; each button goes somewhere real |
| Home | "Become an Instructor" for everyone | visitors → sign up on the Instructor tab (`?as=instructor`); learners → "Teach on Masternova" on the account page (`#teach`); instructors → their courses | applying is self-service, granting is the admin's |
| My Profile | — | a "Teach on Masternova" card: apply (optional pitch), the pending date, re-apply after a rejection, or "Your courses" | where a learner asks to teach |
| Admin | — (no frame) | an instructor-application queue (approve / reject) above the roles editor, which now finds users **by email** | the admin's half of UI.10 |
| All | static pages | motion (§10): page transitions, scroll reveals, press feedback, skeleton loaders, a pointer glow, haptics; all off under "reduce motion" | a product that feels alive; the Figma is a still picture |
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
| My Profile | a photo, first / last name, phone; "Edit" buttons | initials, display name, email, verification status, roles; "Settings" and "My orders" | the API has no profile edit; what it knows is shown |
| Settings | profile form, password change, delete account | email preferences (the toggles) | the only settings the API has |
| Enrolled Courses | tabs All / Pending / Completed; "Durations"; a ⋮ menu (Mark as Completed, Remove) | All / In progress / Completed; "Lectures"; the next-step button (Start / Continue / Review) | the dashboard has no duration; completion is earned by watching; an enrollment is removed only by revoking it |
| Cart | "My Wishlist" with rating and instructor per row | "Cart": cover, title, price, Remove; total with the struck subtotal when a coupon applies, the coupon form, "Buy Now" (= checkout) | the cart stores id, title and price only (note 08) |
| Checkout | a page with name / email fields and "Pay" | none: "Buy Now" opens the payment provider's own checkout | Razorpay collects the payment details (ADR-0016) |
| Purchase History | — (no frame) | a table in the Enrolled Courses style | follows the closest frame |
| Instructor sidebar | an "Instructor" group (Dashboard with earnings, My Courses), "Earning", "Withdrawals" | "My Courses" in the one list, shown to instructors | no earnings or payouts in the API; one list keeps the sidebar short |
| My Courses | "Created: date | time"; edit + delete per row | lecture count and the publish date; edit, and view for a published course | the summary has no creation time; deleting a course isn't in the API (archive is the admin's) |
| My Courses | statuses Published / Drafted | Drafted / In review / Published / Archived, each with its colour | the course state machine has four states (note 05) |
| Course wizard | 3 steps: Course Information, Course Builder, Publish | 4 steps: Details, Pricing, Curriculum, Review & submit | pricing is its own confirmation (PRICE_NOT_SET), and publishing goes through review |
| Course wizard | thumbnail upload, tags, "Benefits", "Requirements" | title, subtitle, description, category, level, language | the API's course fields |
| Course wizard | labels above fields | Material's floating labels kept in the editor | a dense form autosaving on every change; the auth pages carry the Figma's label style |
| Course Upload Tips | StudyNotion's tips | the publish gate's real requirements (description length, price, sections, previews, ready videos) and the editor's shortcuts | tips that match what "Submit" checks |
| Course Builder | an "Editing Lecture" dialog (video, title, HH:MM:SS, description) | lectures edited in their row: title, minutes, preview, "Upload video" with live progress | inline editing autosaves, and the upload widget follows the transcode (note 06); lectures have no description |
| Video Detail | its own player skin (±10 s, volume, settings) | the browser's video controls (hls.js) | a custom skin is a project of its own; the native controls are accessible as they are |
| Video Detail | the lecture's description and date under the video | the lecture's title and the course's | lectures have no description |
| Video Detail | "Add Review" opens a dialog | "Add Review" (enrolled learners) goes to the course page's review form (`#reviews`) | one review form, where the reviews are |
| Video Detail | the footer below the page | no footer | the player is a full-height page outside the public layout |
| All | secondary greys Rich Black 400–600, a Pink 100 badge | Rich Black 300 (200 on 700) and Pink 400 | WCAG AA contrast, checked by axe in e2e (§8) |

## 10. Motion that respects the user ⭐⭐ (UI.11)

The Figma is a still picture; the app moves. One rule over all of it: **the OS "reduce motion"
setting turns every effect off** (WCAG 2.3.3). CSS puts each animation under
`@media (prefers-reduced-motion: no-preference)`; the directives ask
`prefersReducedMotion()` (`shared/motion/reduced-motion.ts`) before doing anything.

| Effect | How | Why this way |
|---|---|---|
| Page transitions | the router's `withViewTransitions()`; `onViewTransitionCreated` skips a navigation that keeps the path (`core/layout/page-transitions.ts`); CSS fades `::view-transition-old/new(root)`; the nav bar and sidebar get their own `view-transition-name`, so they stay put | the browser animates a snapshot: no animation code per page. A catalog filter is a query-param navigation; fading the page on each chip would be noise (the same rule as scroll-to-top) |
| Scroll reveals | `appReveal` (+ `[appRevealDelay]` for a stagger) adds `.mn-reveal`, ONE shared `IntersectionObserver` (`ScrollReveals`) adds `.mn-revealed` | the directive hides the element only once it is watching: no JS, no observer or reduced motion → never hidden. An **animation** (`backwards`), not a transition: components keep their own `transition` |
| Press feedback, card lift, star pop | global `.mat-mdc-button-base:active` scale; course card `translateY` + cover zoom; stars staggered by `nth-of-type` | transforms don't take layout space, so the virtual scroll's fixed row height holds |
| Rows that leave | `animate.leave="mn-leave"` (Angular's built-in leave animation: it waits for `animationend`, then removes) | a removed cart line or a decided application slides out instead of vanishing; no `@angular/animations` |
| Cart badge bump | `afterRenderEffect` on the count → the Web Animations API on `.mat-badge-content` | after render, so the new number is what grows; nothing to clean up |
| Skeletons | `<app-skeleton shape="card \| row \| band" [count]>` instead of first-load spinners | the layout is there before the data; nothing jumps when it lands. One `role="status"` "Loading…" |
| Pointer glow | `appPointerGlow` writes `--mn-pointer-x/y` (at most once per frame, outside change detection), CSS draws a radial gradient | the system cursor is never replaced; touch screens (`hover: none`) get nothing |
| Haptics | `Haptics.tap/success/error()` (`core/haptics.ts`) from click handlers: add to cart, enroll, review, coupon, apply to teach, approve | the Vibration API: Android vibrates; iPhone Safari has no API, so it's a silent no-op there |

The e2e suite runs with `reducedMotion: 'reduce'` (nothing fades under a click);
`e2e/tests/motion.spec.ts` turns motion back on and checks the wiring: a section reveals, a card
follows the pointer, a page change runs a view transition and a filter skips it.

## 11. 30-second recall

- Restyle at the **system** level: `mat.theme` + `theme-overrides` pins every M3 role to the
  design; component tokens only for shape and size; a written-down global rule for the rest.
- **Layout routes** (a component + `children`) own the chrome; two `''` parents backtrack; keep
  guards on the children.
- A **`TitleStrategy`** names the tab and feeds the header from each route's `title`.
- Read the design's **node JSON**, not just its picture: opacity, blend mode, effects, exact
  spacing. A screenshot hides how a glow or an offset block is made.
- **Contrast is a test**, not a review comment: axe in the e2e suite.
- Every departure from the design is a row in a table with its reason.
- **Motion is opt-out by the OS:** `prefers-reduced-motion` gates every effect; hide nothing a
  script must reveal unless that script is running.
