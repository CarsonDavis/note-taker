# Note Taker — Wireframes

All screens are dark mode only.

## 1. Note Input (Home)

The default screen. Always opens here.

### Normal State
```
┌──────────────────────────────┐
│ 📖 Frankenstein          [⚙] │
├──────────────────────────────┤
│                              │
│  [                        ]  │
│  [    type your note...   ]  │
│  [                        ]  │
│  [                        ]  │
│                              │
│         [ Submit ]           │
│                              │
├──────────────────────────────┤
│ ▾ Recent                     │
│  ✓ 2:31 PM — "The monster…" │
│  ✓ 2:28 PM — "New topic, …" │
│  ✓ 1:15 PM — "Chapter 3 q…" │
└──────────────────────────────┘
```

- **Top bar**: sticky topic (read-only) on the left, settings gear icon on the right
- **Text field**: main body of the screen
- **Submit button**: below the text field
- **Recent submissions**: collapsible list at the bottom

### No Topic Set
```
┌──────────────────────────────┐
│ No topic set             [⚙] │
├──────────────────────────────┤
│                              │
│  ...                         │
```

Topic area shows "No topic set" in a muted/dimmed style.

### Success State (after submit)
```
┌──────────────────────────────┐
│ 📖 Frankenstein          [⚙] │
├──────────────────────────────┤
│                              │
│  [                        ]  │
│  [    type your note...   ]  │  ← field cleared
│  [                        ]  │
│  [                        ]  │
│                              │
│         [ Submit ]           │
│                              │
│  ┌────────────────────────┐  │
│  │  ✓ Note saved          │  │  ← brief snackbar, auto-dismiss
│  └────────────────────────┘  │
├──────────────────────────────┤
│ ▾ Recent                     │
│  ✓ 2:35 PM — "So the crea…" │  ← new entry at top
│  ✓ 2:31 PM — "The monster…" │
│  ✓ 2:28 PM — "New topic, …" │
└──────────────────────────────┘
```

### Error State
```
│  ┌────────────────────────┐  │
│  │  ✗ No network — note   │  │  ← snackbar, stays until dismissed
│  │    not saved            │  │
│  └────────────────────────┘  │
```

Text field is NOT cleared on error so the user doesn't lose their note.

### Loading State (submit in progress)
```
│         [ ··· Saving ]       │  ← submit button shows spinner, disabled
```

### Loading State (fetching topic on open)
```
┌──────────────────────────────┐
│ ···                      [⚙] │  ← spinner or shimmer in topic area
├──────────────────────────────┤
```

Topic area shows a loading indicator. Text field is usable immediately — don't block input on topic fetch.

---

## 2. Settings

Accessible via the gear icon on the note input screen. If launched from lock screen, triggers `requestDismissKeyguard()` before opening.

```
┌──────────────────────────────┐
│ ← Settings                   │
├──────────────────────────────┤
│                              │
│ GitHub Account               │
│ ✓ Signed in as CarsonDavis   │
│ [ Sign Out ]                 │
│                              │
│ Repository                   │
│ CarsonDavis/notes            │
│ Sign out to change           │
│ repository or token          │
│                              │
│ ─────────────────────────    │
│                              │
│ Digital Assistant             │
│ ✓ Set as default             │
│                              │
└──────────────────────────────┘
```

Repository is shown read-only. To change repo or rotate token, user signs out and re-enters setup.

### GitHub Account — Not Signed In
```
│ GitHub Account               │
│ Not signed in                │
```

### Digital Assistant — Not Configured
```
│ Digital Assistant             │
│ ⚠ Not set as default         │
│ [ Open System Settings ]     │
```

Shows a warning and a button that opens the system's default assistant picker.

---

## 3. First Run / PAT Setup

On first run (or when not authenticated), the app shows a 4-step guided setup screen in a scrollable column:

```
┌──────────────────────────────┐
│                              │
│        GitJot Setup          │
│  Your voice notes are saved  │
│  as markdown files in a      │
│  GitHub repository you own.  │
│                              │
│ ┌──────────────────────────┐ │
│ │ 1  Fork the Notes Repo   │ │
│ │ [ Fork on GitHub       ] │ │
│ │                          │ │
│ │ 2  Enter Your Repo   (?) │ │
│ │ ┌────────────────────┐   │ │
│ │ │ owner/repo or URL  │   │ │
│ │ └────────────────────┘   │ │
│ │                          │ │
│ │ 3  Generate a PAT        │ │
│ │ [ Generate Token     ]   │ │
│ │                          │ │
│ │ 4  Paste Your Token  (?) │ │
│ │ ┌────────────────────┐   │ │
│ │ │ ghp_...          👁│   │ │
│ │ └────────────────────┘   │ │
│ │                          │ │
│ │     [ Continue ]         │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

- Steps 1-4 are numbered with teal step numbers
- Step 1: "Fork on GitHub" opens the template repo fork page
- Step 2: Repo field accepts `owner/repo` or full GitHub URL; `(?)` icon shows help dialog
- Step 3: "Generate Token on GitHub" first shows an AlertDialog with PAT creation instructions, then opens the GitHub PAT page
- Step 4: Token field is password-masked with visibility toggle; `(?)` icon explains token storage security
- "Continue" validates token via `GET /user` (401 → "Personal access token is invalid"), then validates repo via `GET /repos/{owner}/{repo}` (404 → "Repository not found"), then navigates to note input
- Column is scrollable for small screens

---

## Design Decisions

- **Text field**: grows to fill available vertical space (via `weight(1f)`), scrolls internally when content overflows
- **Submit button**: smaller centered button, easy to press one-handed
- **Recent history**: collapsed by default
- **Long topic names**: wrap to second line
