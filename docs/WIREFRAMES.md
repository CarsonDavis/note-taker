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
│ [ CarsonDavis/notes      ] ▾ │
│                              │
│ ─────────────────────────    │
│                              │
│ Digital Assistant             │
│ ✓ Set as default             │
│                              │
└──────────────────────────────┘
```

### GitHub Account — Not Signed In
```
│ GitHub Account               │
│ [ Sign in with GitHub ]      │
```

Tapping triggers the device flow (see First Run below).

### Digital Assistant — Not Configured
```
│ Digital Assistant             │
│ ⚠ Not set as default         │
│ [ Open System Settings ]     │
```

Shows a warning and a button that opens the system's default assistant picker.

---

## 3. First Run / Device Flow Auth

On first run (or when not authenticated), the app shows the auth flow directly:

### Step 1: Initiate
```
┌──────────────────────────────┐
│                              │
│       Welcome to             │
│       Note Taker             │
│                              │
│  [ Sign in with GitHub ]     │
│                              │
└──────────────────────────────┘
```

### Step 2: Device Code
```
┌──────────────────────────────┐
│                              │
│  Go to:                      │
│  github.com/login/device     │
│                              │
│  Enter code:                 │
│  ┌────────────────────────┐  │
│  │      ABCD-1234         │  │  ← tap to copy
│  └────────────────────────┘  │
│                              │
│  Waiting for authorization…  │  ← polling indicator
│                              │
│  [ Open Browser ]            │
│                              │
└──────────────────────────────┘
```

"Open Browser" opens `github.com/login/device` in the default browser. Code is tappable to copy to clipboard.

### Step 3: Select Repo
```
┌──────────────────────────────┐
│                              │
│  ✓ Signed in as CarsonDavis  │
│                              │
│  Select a repository:        │
│                              │
│  ○ CarsonDavis/notes         │
│  ○ CarsonDavis/note-taker    │
│  ○ CarsonDavis/dotfiles      │
│  ...                         │
│                              │
│  [ Continue ]                │
│                              │
└──────────────────────────────┘
```

Fetches user's repos via GitHub API. After selection, navigates to the note input screen.

---

## Design Decisions

- **Text field**: fixed height, scrolls internally when content overflows
- **Submit button**: smaller centered button, easy to press one-handed
- **Recent history**: collapsed by default
- **Long topic names**: wrap to second line
