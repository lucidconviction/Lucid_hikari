#!/usr/bin/env python3
"""Generate the bundled SFW seed for Lucid Hikari's Web App Market.

This is a developer tool, not part of the APK. It mirrors the Kotlin FMHY
parser (FmhyMarketSource.kt): walk the 16 streaming sections on fmhy.net/video,
take the first real external link in each list item, skip mirrors and chat
links, de-duplicate by host+path, and keep up to 40 per section.

Run from the repository root:

    python3 tools/webapp_market_seed.py

It rewrites app/src/main/assets/webapp_market_seed.json. The adult catalogue is
deliberately not included: it is fetched at runtime only when NSFW is enabled.
"""

from __future__ import annotations

import html
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

FMHY_URL = "https://fmhy.net/video"
OUT = Path("app/src/main/assets/webapp_market_seed.json")

# Stable section ids on fmhy.net/video: (id, human label, cap).
CATEGORIES = [
    ("stream-aggregators", "Stream Aggregators", 40),
    ("p-stream-forks", "P-Stream Forks", 40),
    ("dedicated-server", "Dedicated Servers", 40),
    ("multi-server", "Multi-Server", 40),
    ("stream-lounges", "Stream Lounges", 40),
    ("free-w-ads", "Free with Ads", 40),
    ("video-streaming", "Video Streaming", 40),
    ("anime-streaming", "Anime Streaming", 40),
    ("cartoon-streaming", "Cartoon Streaming", 40),
    ("tv-streaming", "TV Streaming", 40),
    ("drama-streaming", "Drama Streaming", 40),
    ("classics-public-domain", "Classics / Public Domain", 40),
    ("film-archives", "Film Archives", 40),
    ("live-tv", "Live TV", 40),
    ("live-sports", "Live Sports", 40),
    ("sports-replays", "Sports Replays", 40),
]

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
)

# Social / chat / the wiki itself. YouTube, Twitch, GitHub and the sites linked
# from the list are all legitimate destinations and stay.
SKIP_HOSTS = {
    "fmhy.net",
    "discord.com",
    "discord.gg",
    "t.me",
    "telegram.me",
    "reddit.com",
    "www.reddit.com",
}

ZERO_WIDTH = dict.fromkeys(map(ord, "\u200b\u200c\u200d\u2060\ufeff"), None)


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html"})
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.read().decode("utf-8", "replace")


def strip_tags(value: str) -> str:
    value = re.sub(r"<[^>]+>", "", value)
    return html.unescape(value).translate(ZERO_WIDTH).strip()


def clean_name(value: str) -> str:
    value = re.sub(r"[\x00-\x1f\x7f]", " ", value)
    value = re.sub(r"\s+", " ", value).strip()
    return value


def host_key(url: str) -> str:
    rest = url.split("://", 1)[-1]
    host = rest.split("/", 1)[0].lower()
    return host[4:] if host.startswith("www.") else host


def path_key(url: str) -> str:
    rest = url.split("://", 1)[-1]
    path = "/" + rest.split("/", 1)[1] if "/" in rest else "/"
    path = path.split("?", 1)[0].split("#", 1)[0]
    return path.rstrip("/") or "/"


def first_site(li_html: str):
    """The first external anchor that is not a numbered mirror."""
    for href, inner in re.findall(r'<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', li_html, re.S):
        href = html.unescape(href).strip()
        if not href.startswith(("http://", "https://")):
            continue
        name = clean_name(strip_tags(inner))
        if not name or re.fullmatch(r"\d+", name):
            continue
        host = host_key(href)
        if not host or host in SKIP_HOSTS:
            continue
        # A "New" badge inside the anchor is not part of the site's name.
        if "new-badge" in inner:
            name = re.sub(r"\s*New\s*$", "", name).strip()
        return name, href, host
    return None


def section_html(document: str, section_id: str) -> str | None:
    match = re.search(
        r'<h3[^>]*id="' + re.escape(section_id) + r'"[^>]*>.*?</h3>', document, re.S
    )
    if not match:
        return None
    start = match.end()
    next_heading = re.search(r"<h[23][^>]*>", document[start:], re.S)
    end = start + next_heading.start() if next_heading else len(document)
    return document[start:end]


def main() -> int:
    print(f"Fetching {FMHY_URL} ...", file=sys.stderr)
    document = fetch(FMHY_URL)
    apps = []
    total = 0

    for category_id, label, cap in CATEGORIES:
        section = section_html(document, category_id)
        if section is None:
            print(f"  !! section missing: {category_id}", file=sys.stderr)
            continue
        seen = set()
        rank = 0
        for li in re.findall(r"<li[^>]*>(.*?)</li>", section, re.S):
            site = first_site(li)
            if site is None:
                continue
            name, url, host = site
            key = f"{host}{path_key(url)}"
            if key in seen:
                continue
            seen.add(key)
            rank += 1
            apps.append(
                {
                    "name": name,
                    "url": url,
                    "categoryId": category_id,
                    "source": "fmhy",
                    "rank": rank,
                    "note": "",
                    "adult": False,
                }
            )
            if rank >= cap:
                break
        total += rank
        print(f"  {category_id}: {rank}", file=sys.stderr)

    payload = {
        "schema": 1,
        # Milliseconds, matching MarketCatalog.fetchedAt. The market treats a
        # seed younger than 7 days as fresh and does not hit the network on the
        # first open.
        "fetchedAt": int(time.time() * 1000),
        "apps": apps,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} with {len(apps)} apps ({total} counted).", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
