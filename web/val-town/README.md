# Ultra TV proxy — Val Town deployment

[Val Town](https://www.val.town/) is the fastest of the four proxy options to set
up: no CLI, no build, no account-level config. You paste `proxy.ts` into the web
editor and you get a public HTTPS URL.

## Setup (60 seconds)

1. Sign in at https://www.val.town/ (free tier is enough).
2. Click **New → HTTP val**.
3. Open `proxy.ts` from this folder and paste the **entire contents** into the editor.
4. Click **Save**. Val Town gives you a URL of the form
   `https://<username>-<valname>.web.val.run`.
5. In Ultra TV, open **Settings → Network**, paste that URL, click **Save**.

That's it.

## Optional: restrict upstream hosts

On the val's **Environment Variables** panel, add:

```
ALLOWED_HOSTS=cf.your-iptv.example.com,another.host.tv
```

Comma-separated, no spaces around commas. Leave unset to allow any upstream.

## Why use Val Town vs the other proxies?

| Proxy | Setup | Cold start | Free quota | Notes |
|---|---|---|---|---|
| **Val Town** | paste in web UI | ~200 ms | generous (HTTP vals) | easiest, but uses Deno runtime — same as the Deno Deploy variant |
| **Cloudflare Worker** | `wrangler deploy` | ~5 ms | 100k req/day | fastest, but can't reach other CF-protected upstreams (403 / code:1003) |
| **Vercel Edge** | `vercel deploy` | ~50 ms | generous | works for CF-protected upstreams; what the public Ultra TV uses by default |
| **Deno Deploy** | `deployctl deploy` | ~50 ms | generous | identical runtime to Val Town, more deploy ceremony |

If your IPTV provider is itself on Cloudflare, **avoid the Cloudflare Worker** —
the CF anti-loop will return 403. Use Val Town, Vercel or Deno instead.
