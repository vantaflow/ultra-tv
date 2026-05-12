# Ultra TV — config dashboard

Cloudflare Worker exposing a per-MAC remote config + admin dashboard.

## Deploy

```bash
cd cloudflare-config
npm i -g wrangler   # if not installed

# 1) Create the KV namespace and copy the returned ID into wrangler.toml.
wrangler kv:namespace create CONFIG
wrangler kv:namespace create CONFIG --preview

# 2) Set the admin password (replaces the placeholder in wrangler.toml).
wrangler secret put ADMIN_PASSWORD

# 3) Deploy.
wrangler deploy
```

The Worker URL printed by wrangler is what you paste in the app's Settings
under "Cloudflare Worker URL".

## Endpoints

| Method   | Path                  | Auth         | Purpose                            |
|----------|-----------------------|--------------|------------------------------------|
| `GET`    | `/`                   | Basic admin  | Dashboard HTML                     |
| `GET`    | `/api/list`           | Basic admin  | Known MACs                         |
| `GET`    | `/api/config/:mac`    | none         | App fetches its config             |
| `POST`   | `/api/config/:mac`    | Basic admin  | Save JSON config for MAC           |
| `DELETE` | `/api/config/:mac`    | Basic admin  | Remove config                      |

## Provider JSON schema

```json
{
  "providers": [
    { "kind": "XTREAM",  "name": "My Xtream",  "url": "http://host:80",
      "username": "user", "password": "pass" },
    { "kind": "M3U",     "name": "My M3U",     "url": "https://my.host/list.m3u" },
    { "kind": "STALKER", "name": "MAG portal", "url": "http://host:8080",
      "mac": "00:1A:79:XX:XX:XX" }
  ]
}
```

Unknown fields are ignored. Each provider is added and synced sequentially; one
failing provider does not abort the others.
