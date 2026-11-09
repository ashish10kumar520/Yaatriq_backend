# Yaatriq backend

Spring Boot REST API for Yaatriq. It uses PostgreSQL for user accounts and saved travel plans, cookie-based sessions for authentication, and Groq's API for itinerary generation.

## Run locally

Create a PostgreSQL database and user, then set these variables as needed:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5432/Yaatriq_db` | JDBC URL |
| `DATABASE_USERNAME` | `ashishbehera` | Database user |
| `DATABASE_PASSWORD` | unset | Database password; provide through environment, never commit it |
| `SERVER_PORT` | `8080` | HTTP port |
| `FRONTEND_URL` | `https://yaatriq.ai` | OAuth success redirect |
| `CORS_ALLOWED_ORIGINS` | `https://yaatriq.ai,https://www.yaatriq.ai,http://localhost:5173` | Comma-separated frontend origins allowed to call the API with cookies; localhost and 127.0.0.1 HTTP ports are also allowed for development |
| `GOOGLE_REDIRECT_URI` | `http://localhost:8080/login/oauth2/code/google` | Exact Google OAuth callback URI; must also be registered in Google Cloud Console |
| `SESSION_COOKIE_SECURE` | `true` | Set to `false` only for local HTTP development; required with `SameSite=None` |
| `SESSION_COOKIE_SAME_SITE` | `None` | Set to `Lax` only for local same-site development; use `None` for cross-site OAuth callbacks |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | unset | Enable Google OAuth when both are set; alternatively pass the matching `spring.security.oauth2.client.registration.google.*` properties |
| `GROQ_API_KEY` | unset | Groq API key required for itinerary generation; separate from Google OAuth credentials |
| `GROQ_BASE_URL` | `https://api.groq.com/openai/v1` | Groq OpenAI-compatible API base URL |
| `GROQ_MODEL` | `llama-3.3-70b-versatile` | Groq model used for itinerary generation; set it to a model available to your Groq API key |

Start the service with `mvn spring-boot:run`. Flyway creates the schema on first startup. Hibernate validates the schema rather than modifying it. OAuth providers without credentials are not registered, so the application can run without setting up social login.

## Authentication and CSRF

The API uses server-side sessions for signed-in users. OAuth authorization requests are stored temporarily in PostgreSQL keyed by the unguessable OAuth `state` value, so the callback does not depend on the browser returning the original session cookie or reaching the same backend instance. Requests expire after 10 minutes and are deleted on callback. Start OAuth as a top-level browser navigation to the backend's `/oauth2/authorization/google` endpoint; do not start it with `fetch`/XHR. Keep the `JSESSIONID` cookie and send cookies on subsequent cross-origin API requests (`credentials: "include"` / `withCredentials: true`). The session cookie defaults to `SameSite=None; Secure` for cross-site deployment. For local HTTP development, set `SESSION_COOKIE_SAME_SITE=Lax` and `SESSION_COOKIE_SECURE=false`. Set `FRONTEND_URL` to the frontend URL users should return to after OAuth. Set `CORS_ALLOWED_ORIGINS` to the comma-separated frontend origins (scheme and host, no trailing slash); the defaults include `https://yaatriq.ai`, `https://www.yaatriq.ai`, and `http://localhost:5173`. HTTP origins on localhost and 127.0.0.1 are allowed on any port for local development. Mutating requests require a fresh CSRF token from `GET /api/auth/csrf` in `X-XSRF-TOKEN` or `X-CSRF-TOKEN`, along with cookies. Refresh the token after login and logout. `POST /api/auth/logout` is the sole CSRF exception because it only invalidates the caller's current session and must also work if the CSRF cookie has expired.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | Return a CSRF token as `{ "token": "..." }` and set the CSRF cookie |
| `POST` | `/api/auth/register` | Register with `{ "displayName", "email", "password" }` (the legacy `name` field is also accepted); password must be at least 8 characters |
| `POST` | `/api/auth/login` | Sign in with `{ "email", "password" }`; establishes a session |
| `GET` | `/api/auth/me` | Return the signed-in user |
| `POST` | `/api/auth/logout` | Invalidate the current session; idempotent if the session has already expired |
| `GET` | `/oauth2/authorization/google` | Start Google OAuth when configured; navigate the browser to the backend endpoint |

Authentication endpoints return a user object containing `id`, `name`, `email`, and `createdAt`. Registration does not sign in automatically; call the login endpoint after registration.

For local Google OAuth, set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, add `http://localhost:8080/login/oauth2/code/google` to **Google Cloud Console → APIs & Services → Credentials → your OAuth 2.0 Client ID → Authorized redirect URIs**, and have the frontend navigate to `http://localhost:8080/oauth2/authorization/google` (not call it with an AJAX request). If the backend is hosted elsewhere or uses another port, set `GOOGLE_REDIRECT_URI` to that exact public backend callback URL and register the same URL in Google Cloud Console.

Google login is disabled when either credential is missing. Configure both in your IDE's run configuration environment variables (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) or as Spring properties supplied by your run configuration. A verified Google email can be linked to an existing password account with the same email; unverified addresses are not used to link accounts. Do not add empty OAuth registration entries to `application.yml`: Spring validates declared registrations during startup. Do not commit OAuth secrets. Restart the backend after setting them.

## Travel plans

All travel plan endpoints require a signed-in session. Plans are private to their owner.

Hotel search links are available without signing in:

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `GET` | `/api/hotels/search-links?destination=Hanoi` | Return Booking.com, Airbnb, and Agoda search URLs for the destination |

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `POST` | `/api/travel-plans/generate` | Generate and save a plan; accepts `{ "prompt", "destination", "days", "budget", "currency" }` |
| `POST` | `/api/itineraries/generate` | Generate and save a plan; accepts `{ "prompt", "destination", "days", "budget", "currency", "travelStyle" }` |
| `GET` | `/api/travel-plans` | List the signed-in user's plans, newest first |
| `GET` | `/api/travel-plans/{planId}` | Fetch one of the signed-in user's plans |
| `DELETE` | `/api/travel-plans/{planId}` | Delete one of the signed-in user's plans |

`prompt` is required; `days` (1–60), `budget`, `currency` (three-letter code), `destination`, and `travelStyle` are optional. Generation returns `503 Service Unavailable` if `GROQ_API_KEY` is not set and `502 Bad Gateway` if Groq rejects the request or returns an invalid itinerary. Transient Groq server errors and connection failures are retried up to two times with a short backoff; permanent errors such as a missing model are not retried. For a model-related error, verify `GROQ_MODEL` is available to your Groq API key. Generated itineraries are saved as PostgreSQL `jsonb`.

Example request:

```json
{
  "prompt": "I need a travel plan to Bali for 7 days under ₹2 lakh.",
  "destination": "Bali",
  "days": 7,
  "budget": 200000,
  "currency": "INR",
  "travelStyle": "Balanced"
}
```

The `201 Created` response contains plan metadata (`id`, `prompt`, `destination`, `days`, `budget`, `currency`, `travelStyle`) and an `itinerary` object generated by the configured AI provider.

Since this endpoint uses the signed-in session and changes server state, fetch a fresh CSRF token after login and send both cookies and the token header on the generate request. Do not reuse a token obtained before login; Spring Security may rotate it during authentication.

```js
const api = "http://localhost:8080";

const csrfResponse = await fetch(`${api}/api/auth/csrf`, {
  credentials: "include",
});
if (!csrfResponse.ok) throw new Error("Could not get CSRF token");
const { token, headerName } = await csrfResponse.json();

const response = await fetch(`${api}/api/itineraries/generate`, {
  method: "POST",
  credentials: "include",
  headers: {
    "Content-Type": "application/json",
    [headerName]: token,
  },
  body: JSON.stringify({
    prompt: "I need a travel plan to Bali for 7 days under ₹2 lakh.",
    destination: "Bali",
    days: 7,
    budget: 200000,
    currency: "INR",
    travelStyle: "Balanced",
  }),
});
```

The user must already be signed in. A missing or stale CSRF token results in `403 Forbidden`; a missing session results in `401 Unauthorized`.

OAuth failures are logged as a safe error code (without logging authorization codes or tokens) and redirect to `{FRONTEND_URL}/login?error=oauth_failed`. API errors use a JSON object with `timestamp`, `status`, `error`, and `message`.
