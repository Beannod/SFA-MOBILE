# Local Development Testing Guide

## ✅ Setup Complete

All files have been configured for local testing with automatic environment detection.

### Files Modified/Created:

1. **`.dev/dev-overrides.js`** (created - NOT committed)
   - Sets `window.__ENV__.API_BASE_URL = 'http://localhost:5000'`
   - Only loaded on localhost/127.0.0.1
   
2. **`frontend/web-ui/app.html`** (patched)
   - Added conditional script loader for dev-overrides.js
   - Only loads when `location.hostname === 'localhost'`
   
3. **`create.bat`** (patched)
   - Automatically copies `.dev/dev-overrides.js` → `frontend/web-ui/dev-overrides.js`
   - Copies run before backend/frontend start
   
4. **`.gitignore`** (updated)
   - Excludes `.dev/` directory
   - Excludes `frontend/web-ui/dev-overrides.js`
   - Dev files never committed to repo ✅

---

## 🚀 Quick Start: Run Locally

### Step 1: Start Everything
```powershell
cd d:\Binod\sfa-mobile
.\create.bat
```

This will:
- Copy dev-overrides.js to frontend directory
- Start backend on `http://localhost:5000`
- Start frontend static server on `http://localhost:3000`
- Launch database (if docker-compose running)

### Step 2: Verify Frontend → Backend Connection
1. Open browser: **http://localhost:3000**
2. Press **F12** to open DevTools
3. Go to **Console** tab
4. Look for message: 
   ```
   [dev-overrides] API_BASE_URL set to: http://localhost:5000
   ```
5. Verify in console:
   ```javascript
   window.API_BASE_URL  // Should return: http://localhost:5000
   ```

### Step 3: Test API Calls
1. Go to DevTools → **Network** tab
2. Try any action:
   - Login
   - Load customers
   - Create order
   - Fetch products
3. Verify API calls:
   - ✅ Should go to: `http://localhost:5000/api/*`
   - ❌ NOT: `http://localhost:3000/api/*`

### Step 4: Test Database Persistence
1. Create a customer → database persists
2. Create an order → database persists
3. Refresh page → data still there ✅
4. Restart backend → data still there ✅

---

## 🌐 Environment Detection (Automatic)

The **same code** works everywhere because of automatic environment detection:

| Environment | Frontend URL | API Base | Override Applied |
|---|---|---|---|
| **Local Dev** | `http://localhost:3000` | `http://localhost:5000` | ✅ Yes (dev-overrides.js) |
| **Production** | `https://yourdomain.com` | `https://render-backend.com` | ❌ No (gitignored) |

**How it works:**
- Local: `dev-overrides.js` sets API base to localhost
- Production: Cloudflare Functions inject `/env.js` with production backend URL
- Fallback: `auth.js` uses `window.location.origin` if no override

**Result:** Push same code to GitHub → works on Cloudflare + Render + AWS RDS

---

## 🧪 Testing Workflow

### Local Testing (All 3 components)
```
✅ Frontend: http://localhost:3000 (static files)
✅ Backend:  http://localhost:5000 (API)
✅ Database: Local MSSQL (docker-compose)
```

**Test:** Create order → verify calls backend API → verify saved in local DB

### Push to Production (Same code, no changes)
```
✅ Frontend: Cloudflare Pages (https://yourdomain.com)
✅ Backend:  Render (https://render-api.com)
✅ Database: AWS RDS (production connection string)
```

**Result:** All API calls automatically route to production backend

---

## 🐛 Troubleshooting

### Frontend Shows 404 for API Calls
- Check browser console (F12) for: `[dev-overrides] API_BASE_URL set to...`
- If missing: dev-overrides.js not loading
  - Verify `frontend/web-ui/dev-overrides.js` exists
  - Run `.\create.bat` again to copy it

### Backend Won't Start
- Check port 5000 isn't already in use
- Run: `dotnet run --project backend/server/SfaApi.csproj` manually
- Check for compilation errors

### Frontend Server Won't Start
- Verify Python 3.x is installed: `python --version`
- Check port 3000 isn't already in use
- Run manually: `cd frontend\web-ui && python -m http.server 3000`

### Database Connection Failed
- Ensure docker-compose is running:
  ```powershell
  docker-compose up -d
  ```
- Verify connection string in `backend/server/appsettings.json`

---

## 📋 Checklist Before Committing

Before pushing to GitHub:
- [ ] Local testing: frontend calls backend API ✅
- [ ] Local testing: database persists data ✅
- [ ] Run: `git check-ignore .dev/dev-overrides.js` → should show ignored
- [ ] Verify: `frontend/web-ui/dev-overrides.js` won't be committed
- [ ] Backend builds: `dotnet build -c Debug` → success
- [ ] App logic tested: orders, customers, products CRUD all work

Once verified locally, push to main → GitHub Actions deploy to Cloudflare + Render ✅

---

## 📚 Key Files

```
.dev/dev-overrides.js              ← Local-only (NOT committed)
frontend/web-ui/dev-overrides.js   ← Generated at runtime (NOT committed)
frontend/web-ui/app.html           ← Loads override only on localhost
frontend/web-ui/auth.js            ← Reads window.API_BASE_URL
create.bat                          ← Copies dev-overrides at startup
.gitignore                          ← Excludes dev-only files
```

---

## ✨ Same Code Works Everywhere

The key insight: **No code changes needed for different environments.**

- Local: Python loads dev-overrides.js → localhost:5000
- Production: Cloudflare injects /env.js → production backend
- Database: Connection string from environment variables (no hardcoding)

This is why the **same repository** works for local, staging, and production! 🚀
