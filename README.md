# Greenlight

A private Android app for **couples and groups of friends**. Everyone swipes on **movies, series, restaurants and activities** on their own, and the app tells you when you **match**. You can then turn matches into a **date night** (couples) or **meet up** (friends), with RSVPs, calendar and a shareable image.

- **Couples or friend groups.**
  - A couple pairs with a 15-minute code plus ACCEPT.
  - A friend group has a **permanent code** (e.g. `K7M-2QX`) that up to 20 people can join.
  - You can be in several at once and switch between them from the title bar.
- **Matching:**
  - A couple matches when **both** of you like something.
  - A friend group matches when **a majority** does, and match cards show "3 of 5 in" with avatars.
- **Watch**: Netflix, Amazon Prime Video and Disney+ titles **currently available in South Africa** (TMDB + JustWatch data, always `watch_region=ZA`, subscription-only).
- **Eat**: restaurants near you, or near any place you search for (Google Places API (New)). Each card has a **Menu / website** button.
- **Do**: activities such as bowling, escape rooms, mini golf, arcades, cinema, museums, parks, hikes, beaches, zoos, theme parks, spas, karaoke, markets and wine tasting. It uses the same near-me / search-a-location flow.
- **Private by design:**
  - PASS decisions are never visible to anyone else.
  - INTERESTED items are visible to the others only if you allow it.
  - Matches are visible to the whole couple or group.
- **Push notifications** for matches, joins, plans, RSVPs and reminders, with deep links that switch to the right group.
- **Share cards**: plans and matches are shared as a formatted image with the movie poster or venue photo.
- Installed as an **APK**; no Play Store needed.

### Greenlight (v0.6) at a glance
- **Look:** always-dark "Electric Emerald & Charcoal" theme (`ui/theme/Theme.kt`).
- **Shell:**
  - The ☰ menu holds settings, preferences, privacy, your data and admin.
  - The top bar is just ☰ and the switcher, which shows couple avatars or a group badge, with unread-match badges for other groups. Unread tracking lives in `data/session/GroupOverview.kt`, and "seen" times are stored on the phone only.
  - An in-app banner says "New match in X" for other groups.
  - Compact bottom tabs: Home | Watch | Eat | Do | Plan.
- **Group switching** rebuilds everything per group (`key(groupId)` in `MainScaffold`, plus `groupViewModel(...)` keys), so no screen can show another group's data.
- **Decks:**
  - Segments are For You | Partner/Friends | Mine; matches live in the Plan tab.
  - One control row above the card: the scope on the left (Movies | Series, or "Near me · 5 km") and a single **Filters** button on the right. Everything else is in the filter sheet. Partner/Mine lists only show that tab's category.
  - Tap a card for the details sheet (menu, call, map and credits live there, not on the card).
  - Pass / ⓘ / Interested buttons sit below the card.
- **Plan tab:** the global hub. Picture cards for scheduled plans, then a **Saved** tray: Matches | Partner likes | I like, filtered by All | Watch | Eat | Do. Matches have **Add to plan**; there's also a **+ Plan** button.
- **Home:** status widgets (new matches, picks waiting for you, whose turn it is to plan, who's active) instead of repeating the tab bar. Profile opens from the ☰ menu header.
- **Caching:**
  - Images are cached in memory plus up to 150 MB on disk, wiped every 7 days. About → Clear image cache clears them now.
  - TMDB API responses are cached for 6 hours.
  - Places searches are memoised in memory for 15 minutes.
- **Invite links:**
  - The share text includes `https://greenlight-za.web.app/join/<CODE>`, which opens the app on the Join screen (Android App Link).
  - The page and `/.well-known/assetlinks.json` live in `hosting/public`. Deploy them with `firebase deploy --only hosting`.
  - If you ever sign the APK with a different key, update the SHA-256 in `assetlinks.json`.
  - The Join screen also has a **Paste** button that finds the code inside a whole copied message.

---

## Architecture

```
 Android app (Kotlin, Compose, Material 3, MVVM)
   ├─ Firebase Auth (Google Sign-In, one account per person)
   ├─ Cloud Firestore (source of truth; offline cache queues swipes)
   ├─ Cloud Functions (callable + triggers)  ── FCM ──▶ members' phones
   ├─ TMDB REST API (titles, images, ZA watch providers via JustWatch)
   └─ Google Places API (New) REST (restaurant + activity search, details, photos, place autocomplete)
```

**Why there's a small Cloud Functions module:** a phone can't safely push a notification to another phone (that needs server credentials, which must never ship in an APK). Membership and match detection are also done server-side, so no phone ever reads someone else's PASS decisions. The functions are:

| Function | Type | Purpose |
|---|---|---|
| `createCouple`, `previewCouple`, `acceptCouple`, `declineCouple`, `cancelCouple` | callable | Couples: expiring `ABCD-1234` code and explicit accept |
| `createGroup`, `previewGroup`, `joinGroup`, `leaveGroup`, `resetGroupCode` | callable | Friend groups: permanent code, join, leave (leaving a couple unpairs both), owner resets the code |
| `onGroupWatchSwipe`, `onGroupRestaurantSwipe`, `onGroupActivitySwipe` | Firestore trigger | Majority matching; creates the match once (deterministic id), keeps the "X of Y in" count current, notifies everyone |
| `onGroupPlanWritten` | Firestore trigger | Date night / meet up created, changed or cancelled; RSVPs notify the organiser |
| `onGroupMemberActivity` | Firestore trigger | "Sam finished a round of Movies" (throttled) |
| `planReminders` | scheduled, every 30 min (`europe-west1`) | "Tomorrow/tonight" and "in 1 hour" (skips people who said they can't make it) |
| `groupSwipeReminders` | scheduled, daily 17:30 SAST (`europe-west1`) | Optional nudge after 3+ days without swiping |

The scheduled functions run in `europe-west1` because Cloud Scheduler isn't available in `africa-south1`. Everything else runs next to the database in Johannesburg.

Cloud Functions need the **Blaze (pay-as-you-go)** plan. For a few people the usage stays inside the free quotas, so the expected cost is R0. Set a budget alert anyway (step 1).

### Firestore data model

```
/users/{uid}                          private to the owner (email, groupIds*, notification settings)
/users/{uid}/devices/{deviceId}       FCM tokens (owner only)
/pairingCodes/{ABCD-1234}             couple codes: functions only; expire after 15 min
/groupCodes/{K7M-2QX}                 friend-group codes: functions only; permanent until reset
/groups/{gid}                         type (couple | friends), name, memberIds*, ownerId, code, status*
/groups/{g}/members/{uid}             member-visible profile: name, photo, streamingServices, foodPrefs, activityPrefs, shareInterests, activity
/groups/{g}/{watch|restaurant|activity}Swipes/{itemKey}_{uid}    decision: interested | passed
/groups/{g}/{watch|restaurant|activity}Matches/{itemKey}         * server-created; interestedUids, interestedCount, memberCount
/groups/{g}/plans/{id}                date night / meet up; rsvps{uid: going | maybe | not_going}
/groups/{g}/notifications/{id}        notification history (each sees their own)
```
`*` = only Cloud Functions can write it. `firestore.rules` enforces the rest; `npm run test:rules` checks it, including:
- nobody can read another member's PASS
- other groups are locked out
- you can only RSVP for yourself

---

## Prerequisites

| Tool | Notes |
|---|---|
| Android Studio (2025.x or newer) | Its bundled JDK (`<Android Studio>\jbr`) is used to build |
| Android SDK platform 37 | Install via Android Studio → SDK Manager |
| Node.js 22+ | For Cloud Functions and the Firebase CLI (installed locally via `npm install`) |
| A Google account | For Firebase, Google Cloud and sign-in |
| A TMDB account | Free, for the API key |

On Windows, run the Gradle commands from Git Bash or PowerShell with `JAVA_HOME` pointing at Android Studio's JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

---

## Setup (do these in order)

### 1. Create the Firebase project
1. Go to <https://console.firebase.google.com> → **Create a project** → name it e.g. `greenlight-za`. Google Analytics is optional; you can turn it off.
2. Open **Project settings (gear) → Usage and billing → Details & settings → Modify plan** and choose **Blaze**. Link or create a billing account. This is required for Cloud Functions and the Places API.
3. Recommended: in Google Cloud Console → **Billing → Budgets & alerts**, create a budget of e.g. R50/month with email alerts.

### 2. Enable Google and email sign-in
Firebase console → **Build → Authentication → Get started → Sign-in method → Google → Enable**. Choose a support email and save. This creates the OAuth "Web client" that the app uses for Google Sign-In.

Then, on the same page: **Add new provider → Email/Password → Enable** (leave "Email link" off) → **Save**. This lets people on phones without Google Play services (e.g. Huawei) sign in with an email and password.

### 3. Add the Android app to Firebase
Project settings → **Your apps → Add app → Android**:
- **Android package name:** `com.greenlight.app` (must match exactly)
- App nickname: anything
- Add the **SHA-1** now if you have it (step 5). You can also add it later.
- Click **Register app**.

### 4. Add `google-services.json`
Download `google-services.json` and save it as **`app/google-services.json`**. It's gitignored.

> Download it again **after** steps 2 and 5. The file must contain an `oauth_client` entry with `"client_type": 3`, or Google Sign-In fails with "default_web_client_id missing".

### 5. Configure SHA-1 / SHA-256 fingerprints
Google Sign-In and the restricted Places key both check the certificate that signed the APK.

Debug certificate (created automatically by Android Studio):
```bash
keytool -list -v -keystore "$HOME/.android/debug.keystore" -alias androiddebugkey -storepass android -keypass android
```
Or run `./gradlew signingReport`. Copy the **SHA1** and **SHA-256** values into Firebase → Project settings → Your apps → *Greenlight* → **Add fingerprint**.

For the release key (see step 12), add its SHA-1 and SHA-256 as well. After adding fingerprints, **download `google-services.json` again** (step 4).

### 6. Create the Firestore database
Firebase console → **Build → Firestore Database → Create database**:
- Location: **`africa-south1` (Johannesburg)** (recommended for South Africa).
- Start in **production mode**. The real rules are deployed in step 7.

> The Cloud Functions must run in the same region as the database. The region is set in two places, which must match:
> `functions/src/config.ts` → `REGION` and `app/src/main/java/com/greenlight/app/data/firebase/FirebaseEnv.kt` → `FUNCTIONS_REGION`.
> If you chose a different database location (e.g. `europe-west1`), change both.

### 7. Install security rules, indexes and Cloud Functions
```bash
cd functions
npm install
npx firebase login
cp ../.firebaserc.example ../.firebaserc   # then edit it: put your Firebase project id
npm run deploy                              # deploys functions + firestore rules + indexes
```
The first deploy asks to enable a few Google Cloud APIs (Cloud Functions, Cloud Build, Artifact Registry, Eventarc, Cloud Scheduler, Cloud Run). Answer **yes**. If a deploy fails right after enabling APIs, wait a minute and run it again.

### 8. Configure Firebase Cloud Messaging
Nothing else is needed on the Android side: `google-services.json` covers it. Check that **Firebase Cloud Messaging API (V1)** shows as *Enabled* under Project settings → **Cloud Messaging**. The Cloud Functions send messages with the Admin SDK; no server key goes in the app.

Devices must have **Google Play services** for FCM. Play *Store* distribution is **not** required.

### 9. Get TMDB API credentials
1. Create an account at <https://www.themoviedb.org/signup>.
2. Go to **Settings → API → Create → Developer**, accept the terms and fill in the form (personal / non-commercial).
3. Copy the **API Read Access Token** (long, starts with `eyJ…`). The shorter "API Key" also works.

### 10. Configure TMDB credentials
Copy `local.properties.example` to `local.properties` (gitignored) and set:
```properties
TMDB_API_KEY=eyJhbGciOi...
```
Alternatively, set it as an environment variable called `TMDB_API_KEY` for the build. The key is compiled into `BuildConfig`. Treat it as **public once the APK is shared**: anything in an APK can be extracted.

### 11. Configure the restaurant API (Google Places API (New))
1. Open <https://console.cloud.google.com/> and select the **same project** as Firebase.
2. **APIs & Services → Library → "Places API (New)" → Enable.**
3. **APIs & Services → Credentials → Create credentials → API key.** Then **Edit** it:
   - *Application restrictions:* **Android apps** → add package `com.greenlight.app` + your **SHA-1** (debug and release).
   - *API restrictions:* **Restrict key** → select only **Places API (New)**.
4. Put it in `local.properties`:
   ```properties
   PLACES_API_KEY=AIza...
   ```
5. Recommended: **APIs & Services → Places API (New) → Quotas** → cap requests per day (e.g. 300) so there's no surprise bill.

How the app uses it: Text Search around the chosen point (radius enforced client-side), Place Details for match screens, Autocomplete for "Search a location", and Place Photos. The app sends the `X-Android-Package` / `X-Android-Cert` headers, so the Android-restricted key works for these REST calls.

> **Cost note:** Places API (New) has monthly free usage per SKU. Text Search with ratings, opening hours and photos, and Place Photos, are billed SKUs beyond that. A couple doing a few searches a week normally stays within the free usage, but check current pricing at <https://developers.google.com/maps/billing-and-pricing/pricing>. The takeaway, delivery, sit-down and family-friendly filters request a pricier field set, so they're only requested when you turn those filters on.

### 12. Build the APK
```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
```
Release APK (smaller, optimised). First create your own signing key **once** and keep it safe:
```bash
keytool -genkeypair -v -keystore couples-release.jks -alias couples -keyalg RSA -keysize 4096 -validity 10000
```
Create `keystore.properties` in the project root (gitignored):
```properties
storeFile=couples-release.jks
storePassword=...
keyAlias=couples
keyPassword=...
```
Then:
```bash
./gradlew assembleRelease      # → app/build/outputs/apk/release/app-release.apk
```
Add the release key's SHA-1 and SHA-256 to Firebase (step 5) and to the Places key restriction (step 11). Without `keystore.properties`, the release build is signed with the debug key; that's fine for testing, but not for sharing.

### 13. Install the APK
- **USB:** enable *Developer options → USB debugging* on the phone, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- **No cable:** send the APK to the phone (Drive, WhatsApp, email), open it and allow **Install unknown apps** for that app when prompted.

When upgrading, install over the existing app with the **same signing key**, otherwise Android refuses and you have to uninstall first.

### 14. Test partner pairing
1. Install the APK on **both** phones. Each person signs in with **their own** Google account.
2. Phone A: **Create a couple** → a code like `ABCD-7392` appears (valid for 15 minutes).
3. Phone B: **Join a couple** → type the code → "*John wants to pair with you.*" → **ACCEPT**.
4. Both phones switch to Home showing "❤️ Paired with …", and A gets a "You're paired!" notification.
5. Try an invalid code (e.g. `ZZZZ-0000`) → "That code doesn't exist." Wait 15 minutes → "That code has expired."
6. Profile → **Your couples & groups** → **Unpair** ends the pairing for both.

**Friend groups:**
1. Anyone: **Create a group**, name it (e.g. *Braai Crew*), and **Share code** (e.g. `K7M-2QX`). The code never expires.
2. Friends: **Join a group** and enter the code. A preview shows *Braai Crew · 1 member*; tap **JOIN GROUP**. Everyone else gets "👋 Sam joined Braai Crew".
3. Use the title-bar switcher (❤️ / 👥) to move between your couple and your groups. Swipes, matches and meet ups are kept separate per group.
4. With 3 people, a match needs 2 of them. Matches show "2 of 3 in" with avatars, and the count updates as more people join in.
5. The group owner can **Reset code** (Profile → Your couples & groups) if a code was shared too widely. Anyone can **Leave group**.

**Two test accounts on one PC (debug builds only):**
```bash
cd functions && npm run serve        # Auth + Firestore + Functions emulators, UI at http://localhost:4000
```
In the app: Welcome → **Dev tools** → enable *Use Firebase emulators* (host `10.0.2.2` from the Android emulator, or your PC's LAN IP from a real phone) → **Save**, then force-stop and reopen the app → Dev tools → **Create + pair User A ↔ User B**, or **Create friend group A + B + C**. Run a second emulator/phone and **Sign in as User A**. The test accounts only exist in the Auth emulator and aren't in release APKs. Push notifications can't be sent from the emulator; test them with the real project.

### 15. Test notifications
1. On both phones, allow notifications when asked (Android 13+).
2. Phone A: Watch → swipe right (INTERESTED) on a title.
3. Phone B: Watch → **Partner** tab → "I'M INTERESTED TOO" on that title (or find it in the deck and swipe right).
4. Within a couple of seconds both phones get **"❤️ It's a match! You and … both want to watch …"**. Put phone A in the background or close the app first to see it arrive as a push.
5. Tap the notification → it opens **Match Details**.
6. Also try: plan a date night (partner gets "📅 Date night planned!") and set it for ~1 hour ahead (reminder).
7. In a friend group: create a meet up. Others get "🗓️ Meet up planned! … Are you in?". Their **Going / Maybe / Can't make it** notifies the organiser.
8. On a plan or match, tap **SHARE**: you get an image card (poster or venue photo, date, place, who's going) plus a text summary.

If nothing arrives, check: notifications are allowed for the app, the device has Google Play services, the functions deployed OK (`npx firebase functions:log`), and notification settings are on (Profile → Notification settings).

---

## Running the tests
```bash
./gradlew testDebugUnitTest     # JVM: ZA filter/query building, availability pipeline, place ranking, photo URLs, deep links
cd functions
npm run test:unit               # codes, majority rule, helpers, notification wording
npm run test:rules              # Firestore security rules against the emulator (needs Java)
npm run test:functions          # end-to-end couples + friend groups → swipes → majority match → RSVPs → leave
```
If `test:functions` fails with "Cannot determine backend specification. Timeout", the emulator started slowly. Run it again with a longer timeout:
```bash
FUNCTIONS_DISCOVERY_TIMEOUT=60 npm run test:functions
```

## Configuration reference
| Name | Where | Required | Purpose |
|---|---|---|---|
| `app/google-services.json` | file | yes | Firebase config (step 4) |
| `TMDB_API_KEY` | `local.properties` or env | yes, for Watch | TMDB v4 read token or v3 key |
| `PLACES_API_KEY` | `local.properties` or env | yes, for Eat + Do | Google Places API (New) key |
| `keystore.properties` | file | for release | Release signing |
| `REGION` / `FUNCTIONS_REGION` | code | yes | Must match the Firestore location |

A separate `GOOGLE_MAPS_API_KEY` isn't needed: the app doesn't embed a map SDK.

## Known limitations
- **Streaming availability** comes from TMDB's JustWatch data for ZA. It can lag behind the services by a few days, and the deck only shows subscription (flat-rate) availability, not rent/buy. Availability is cached in memory for at most 12 hours and never stored.
- **Disney+ in South Africa:** at the time of writing, TMDB/JustWatch list **no** South African Disney+ titles, so choosing only Disney+ gives an empty deck. The filter sheet says so; the app never invents availability.
- **Restaurant and activity data** depends on Google's coverage.
  - Takeaway, delivery, sit-down and family-friendly aren't known for every place; when those filters are on, places without the info are hidden.
  - "Pin on map" location picking isn't in the MVP; search by name, address or landmark instead.
- **Menus:** Google Places doesn't provide menus. The **Menu / website** button opens the restaurant's own website, or a web search for its menu if it has none.
- **Calendar**: "Add to calendar" opens your calendar app pre-filled.
  - Invites depend on that app; people aren't auto-invited because emails aren't shared between members.
  - Instead, the plan appears in everyone's app automatically, with reminders, and there's a Share button.
- **Groups** are limited to 20 people. Matches are "sticky": if people leave, or new people join later, existing matches stay.
- **FCM** needs Google Play services on the device (most Android phones; not Huawei devices without GMS).
- Not built yet (architecture leaves room): more services and countries, Showmax/Apple TV+/YouTube, iOS, "Decide for us".

## Data sources and attribution
Shown in the app under **Profile → About & data sources**:
- Movie and series data from **The Movie Database (TMDB)**. *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- Streaming availability data provided by **TMDB and JustWatch**.
- Restaurant and activity data and photos from **Google Places**. Photo author attributions are shown with each photo.

## Project layout
```
app/src/main/java/com/greenlight/app/
  data/        auth, user, group (couples + friend groups), swipe (swipes & matches), tmdb, places (restaurants + activities), plan, prefs, session
  domain/      models, WatchQuery (ZA filter logic), Place + PlaceRanking
  notifications/  FCM service, channels, deep links
  ui/          onboarding (group setup), home, watch, places (Eat + Do), matches, plans, share (image cards), profile, components, theme
app/src/debug/ Dev tools (emulator switch, test users). Not in release builds.
functions/     Cloud Functions (TypeScript) + unit, rules and integration tests
firestore.rules, firestore.indexes.json, firebase.json
```

## Admin tools
The admin account (`ADMIN_EMAILS` in `functions/src/config.ts`) sees two extra rows in **Profile**:
- **Manage users & data**: search every user and group.
  - Users: edit name, onboarded, notifications, disable the account; remove them from groups; create or delete accounts. Deleting also removes their swipes, and matches recount automatically.
  - Groups: rename, reset the code, add or remove members, delete matches or plans, or delete the whole group.
- **Impersonate a user**: use the app as that person. A banner stays on screen with **STOP** to switch back.
  - Their notifications are never sent to your phone, and admin tools are blocked while impersonating.
- **Manage users & data → APP** tab:
  - **Minimum app version.**
    - Every build gets a new version code: the minutes since 2020, shown in About.
    - Install the new APK on your phone, then tap **Block versions older than this one**. Anyone on an older build gets an "Update required" screen with your message and a button to email you.
    - **Allow all versions** undoes it. Admin accounts are never blocked.
    - Stored in `/config/app`, which anyone can read and only the `adminSetAppConfig` function can write.
  - **Start fresh.** Type `DELETE EVERYTHING` to delete every couple and group, with their swipes, matches, plans, notifications and codes. User accounts and settings are kept.
  - **Delete everything, including users.** Type `DELETE ALL USERS`. It does the same as Start fresh and also deletes every user account (sign-in, profile, phones), so everyone must register again. Your own admin account is kept.
- **Users' own privacy tools** (Profile → Your data): **Download my data** exports a JSON copy, and **Delete my account** removes everything straight away (functions `exportMyData` and `deleteMyAccount`). The privacy policy is in Profile and in About.
- **Crash reports** go to Firebase Crashlytics (Firebase console → Crashlytics).
- **Dev tools** (emulator switch) exist only in debug builds. They appear in Profile for the admin only; before sign-in, long-press the logo.

Security:
- Every admin action is checked on the server against the *verified* Google email.
- Every action is written to the `adminAudit` collection, which no app user can read or write.

**One-time setup for impersonation.** Firebase needs permission to create sign-in tokens:
1. Open Google Cloud console → **IAM** for the project.
2. Edit the principal `PROJECT_NUMBER-compute@developer.gserviceaccount.com` ("Default compute service account").
3. **Add role → Service Account Token Creator → Save.**
