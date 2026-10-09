# Greenlight Admin

A local web console for managing Greenlight: users, couples and groups, live app settings,
push notifications, the audit log and data resets.

It runs on your PC (`https://localhost`) and signs in with your Google admin account.

## Run it

**Visual Studio:** open `Greenlight.Admin.sln`, pick the **API + Web** launch profile (the dropdown next to Start) and press F5.
The solution targets .NET 9 (`global.json` pins the .NET 9 SDK), so it works in Visual Studio 2022 17.12 or newer.

**Or from a terminal:**

```powershell
cd C:\Users\KG9\source\Personal\Greenlight\admin
.\run.ps1 -Trust     # first time: also trusts the local HTTPS certificate
.\run.ps1            # after that
```

`run.ps1` builds the solution, then starts both apps in their own windows and opens the console:

| App | URL |
|---|---|
| Web console | https://localhost:7142 |
| API | https://localhost:7141 (interactive API reference at `/scalar/v1`) |

Sign in with **Sign in with Google**, using an admin account (`Admin:Emails` in
`Greenlight.Admin.Api/appsettings.json`).

## Architecture (same layout as SCAR)

| Project | Role |
|---|---|
| `Greenlight.Admin.Api` | ASP.NET Core 9 REST API (`api/v1.0/...`) with Wolverine (mediator only), Scalar, Serilog and URL versioning. Validates Firebase ID tokens. |
| `Greenlight.Admin.Application` | CQRS handlers in `Features/<Area>/Commands|Queries`, repository interfaces, a hand-written `Mapper`, validation, and a 60 s list cache |
| `Greenlight.Admin.Domain` | Plain entities |
| `Greenlight.Admin.Persistence` | `FunctionsGateway` plus repositories. Calls Greenlight's admin Cloud Functions with *your* ID token. |
| `Greenlight.Admin.Shared` | `Response<T>` and request/response DTOs |
| `Greenlight.Admin.Web` | MVC console with Bootstrap 5, Chart.js and DataTables. Talks only to the API. |
| `Greenlight.Admin.UnitTests` | xUnit tests |

**Request flow:** browser → Web (cookie session) → API (Firebase JWT) → Application handler → Persistence → Cloud Functions → Firestore / Auth.

**No service-account key is needed.** The console uses the same admin functions as the app's
admin screens. They check the verified admin email, refuse impersonated sessions, and write
every change to `/adminAudit`.

## What you can do

- **Dashboard**
  - Users, activity, couples and groups, matches and plans
  - Charts: 30-day matches by Watch/Eat/Do, new users, category split
  - Recent admin activity
- **Users**
  - Search, sort and export to CSV
  - Edit name, onboarding and notifications
  - Disable or enable an account, delete it, or create one
- **Couples & groups**
  - Rename a group or reset its code
  - Add or remove members
  - Delete matches, plans or the whole group
- **App settings** (the app picks these up live)
  - Announcement banner and maintenance mode
  - Turn features on or off: Watch, Eat, Do, friend groups, share cards
  - Defaults: distance steps and default distance, plan length and start time, round size
  - Streaming services and Eat/Do categories to hide
  - Cache durations
  - Minimum app version
- **Notifications**
  - Broadcast to everyone, one couple or group, or one person
  - Live phone preview and a recipient count before sending
- **Audit log**
  - Every admin action, filterable by type
- **Danger zone**
  - Start fresh (keeps user accounts), or delete everything including users
- **Extras**
  - Ctrl+K quick search
  - Cloud Functions status and response time
  - Toasts and a review-changes step before saving settings

## Configuration

- **API** (`Greenlight.Admin.Api/appsettings.json`):
  - `Firebase:ProjectId`
  - `Firebase:FunctionsBaseUrl`: point it at the emulator (`http://127.0.0.1:5001/demo-greenlight/africa-south1/`) to test locally.
  - `Admin:Emails`
  - `Cors:Origins`
- **Web** (`Greenlight.Admin.Web/appsettings.json`):
  - `Api:BaseUrl`
  - `Firebase:Web:*`: the public web config of the "Greenlight Admin" Firebase web app. It's meant to be public; access is protected by sign-in and the admin checks.

## Tests

```powershell
dotnet test Greenlight.Admin.UnitTests\Greenlight.Admin.UnitTests.csproj
```
