---
page_ref: /docs/apps/ghostex-android/index.html
---

# Ghostex Android Docs

<!--
CDXC:AndroidBranding 2026-05-17-16:38:
Repo documentation should introduce Ghostex Android as the shipped product.
Keep upstream Termux references as lineage/supporting implementation context,
not as the primary docs landing page.
-->

Ghostex Android connects an Android terminal surface to persistent Ghostex sessions running on a Mac.

It uses Tailscale SSH to reach the Mac, calls the Ghostex CLI for the live session list, and attaches to ZMX-backed sessions. The first Android release supports ZMX only.

See the repository README for setup, release verification, and upstream-sync notes.
