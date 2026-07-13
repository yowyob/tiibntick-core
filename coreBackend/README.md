# coreBackend — Agency ERP modules (Jeff / BFF migration)

This folder hosts **Agency-specific ERP** code that will live inside TiiBnTick Core
(`tnt-agency-back-core`), separate from logistics modules under `logistics/`.

## Git remotes (team workflow)

| Remote   | URL                                              | Pull | Push |
|----------|--------------------------------------------------|------|------|
| `origin` | GitLab `tiibntick-org/tiibntick-core`            | **Yes** | Only after Core lead MR merge |
| `github` | GitHub `tiibntick-org/TiiBnTick-core` (deployed) | **No**  | **Yes** (feature branches) |

```bash
# Update local main from GitLab only
git pull origin main

# Publish Agency ERP work for Core lead review (deployed mirror)
git push -u github jeff/tnt-agency-back-core
```

The Core maintainer merges into GitLab `main`; we never pull from GitHub.

## Layout

```
coreBackend/
  tnt-agency-back-core/          ← Maven aggregator + child modules
    tnt-agency-org-core/
    tnt-agency-staff-core/
    tnt-agency-workforce-core/
    tnt-agency-sync-core/
    …
```
