#!/usr/bin/env python3
"""CineTV, set up for the person who watches: an interview in their own language, then only what they
will use: the TV's language, Nuvio's addons, rows built around their tastes ("For you"), live channels
if they want them, and only the apps they need. Nothing they would never watch is installed.

    python3 scripts/personalize.py                       # interview, then set the TV up
    python3 scripts/personalize.py --interview-only      # just save cinetv-profile.json
    python3 scripts/personalize.py --profile cinetv-profile.json          # set up from saved answers
    python3 scripts/personalize.py --profile cinetv-profile.json --plan   # show what would happen

The questions live in scripts/personalize/questions.json; an AI agent asks the same ones in
conversation and writes the same cinetv-profile.json (AGENTS.md). Needs adb (connected to the TV)
and curl only for --apply; the standard library otherwise.
"""
import argparse
import base64
import json
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
QUESTIONS = os.path.join(HERE, "personalize", "questions.json")
UI_LANGS = ("en", "tr", "pt", "es", "de", "fr")

# Language code -> (Torrentio language, ISO 639-1)
LANGUAGES = {
    "en": ("english", "en"), "tr": ("turkish", "tr"), "pt-BR": ("portuguese", "pt"), "pt-PT": ("portuguese", "pt"),
    "es-ES": ("spanish", "es"), "es-419": ("latino", "es"), "de": ("german", "de"), "fr": ("french", "fr"),
    "it": ("italian", "it"), "nl": ("dutch", "nl"), "pl": ("polish", "pl"), "ru": ("russian", "ru"),
    "uk": ("ukrainian", "uk"), "ar": ("arabic", "ar"), "fa": ("persian", "fa"), "he": ("hebrew", "he"),
    "hi": ("hindi", "hi"), "ja": ("japanese", "ja"), "ko": ("korean", "ko"), "zh": ("chinese", "zh"),
    "id": ("indonesian", "id"), "vi": ("vietnamese", "vi"), "th": ("thai", "th"), "sv": ("swedish", "sv"),
    "da": ("danish", "da"), "no": ("norwegian", "no"), "fi": ("finnish", "fi"), "el": ("greek", "el"),
    "cs": ("czech", "cs"), "ro": ("romanian", "ro"), "hu": ("hungarian", "hu"), "bg": ("bulgarian", "bg"),
    "sr": ("serbian", "sr"), "hr": ("croatian", "hr"),
}

# TMDB watch-provider ids ("|" = any of)
PROVIDERS = {
    "netflix": "8", "prime": "9|119", "disney": "337", "max": "1899|384", "apple": "350", "paramount": "531",
    "hulu": "15", "peacock": "386", "crunchyroll": "283", "mubi": "11", "globoplay": "307", "clarovideo": "167",
    "canalplus": "381", "skyshowtime": "1773", "videoland": "72", "hotstar": "122", "zee5": "232", "blutv": "341",
    "discovery": "510", "curiosity": "190",
}

# TMDB keyword ids for anime moods; TV genres where a TMDB genre fits better
ANIME_KINDS = {"action": ("10759", None), "fantasy": ("10765", "237451"), "romance": (None, "9840|9914"),
               "scifi": ("10765", "384065|10046"), "mystery": ("9648", None)}
VIDEO = ("films", "series", "anime", "docs", "kids", "reality", "music")
EMOJI = {"films": "🎬", "series": "📺", "anime": "🌸", "sports": "🏆", "docs": "🌍", "kids": "🧸", "reality": "🎤",
         "music": "🎵", "services": "⭐", "13042": "⚽", "6496": "🏀", "1488": "🎾", "233981,242128": "🏎️",
         "9917,209476": "🥊", "579": "🏈", "1480": "⚾", "5605": "🏐", "180491": "🚴", "2702": "⛳", "316": "♟️"}

ADDONS = {
    "torrentio": "torrentio.strem.fun/{config}manifest.json",
    "opensubtitles": "opensubtitles-v3.strem.io/manifest.json",
    "tr_altyazi": "tr-altyazi.altyazi.workers.dev/manifest.json",
    "anime_kitsu": "anime-kitsu.strem.fun/manifest.json",
    "youtube": "v3-channels.strem.io/manifest.json",
    "top10": "7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/{config}/manifest.json",
    "channels": "263f84288ddb-nexotv.baby-beamup.club/{config}/manifest.json",
}
ADDON_NAMES = {"torrentio": "Torrentio", "opensubtitles": "OpenSubtitles", "tr_altyazi": "TR Altyazı",
               "anime_kitsu": "Anime Kitsu", "youtube": "YouTube", "top10": "Netflix Top 10", "channels": "NexoTV"}
NEXOTV = "https://263f84288ddb-nexotv.baby-beamup.club"
ANTHOLOGY = "https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json"
# Paid channels restreamed without the broadcaster's permission: never switched on
ANTHOLOGY_SKIP = ("Anthology Canlı Spor", "Anthology Sinema Kanalları")


def fetch_json(url, body=None):
    """JSON over HTTPS through curl: python.org's macOS Python often has no root certificates."""
    cmd = ["curl", "-fsSL", "-m", "30", url]
    if body is not None:
        cmd[1:1] = ["-X", "POST", "-H", "Content-Type: application/json", "--data-binary", json.dumps(body)]
    out = subprocess.run(cmd, capture_output=True, text=True, timeout=40)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip() or "curl failed")
    return json.loads(out.stdout)


def anthology_plugins():
    """Every plugin in Anthology's repository today (so new ones come along), without ANTHOLOGY_SKIP."""
    try:
        names = [x.get("name", "") for x in fetch_json(ANTHOLOGY).get("scrapers", [])]
        return [n for n in names if n and n not in ANTHOLOGY_SKIP]
    except Exception:
        return None


def load_questions():
    with open(QUESTIONS, encoding="utf-8") as f:
        return json.load(f)


def ui_lang(lang):
    base = (lang or "en").split("-")[0]
    return base if base in UI_LANGS else "en"


def tr(value, ui):
    """A text in the interview language (falls back to English)."""
    if isinstance(value, dict):
        return value.get(ui) or value.get("en") or next(iter(value.values()))
    return value or ""


class Texts:
    def __init__(self, data, ui):
        self.data, self.ui = data, ui

    def __call__(self, key, **kw):
        text = tr(self.data["strings"].get(key, key), self.ui)
        return text.format(**kw) if kw else text

    def option(self, list_name, value):
        for o in self.data["lists"].get(list_name, []):
            if o["v"] == value:
                return tr(o["t"], self.ui)
        return value


# ---- the interview -------------------------------------------------------------------------------

def options_for(q, data, answers):
    if "options" in q:
        return q["options"]
    if q.get("options_from") == "lang":
        return next(x for x in data["questions"] if x["id"] == "lang")["options"]
    if q.get("from"):
        src = next(x for x in data["questions"] if x["id"] == q["from"])
        chosen = answers.get(q["from"]) or []
        return [o for o in src["options"] if o["v"] in chosen]
    return data["lists"][q["options_from"]]


def visible(q, answers):
    w = q.get("when")
    if not w:
        return True
    got = answers.get(w["q"])
    values = got if isinstance(got, list) else ([got] if got else [])
    if "in" in w:
        return any(v in w["in"] for v in values)
    if "any" in w:
        return any(v in w["any"] for v in values)
    if "min" in w:
        return len(values) >= w["min"]
    return True


def tv(*args, check=False):
    try:
        out = subprocess.run(["adb", "shell", *args], capture_output=True, text=True, timeout=30)
        return out.stdout.replace("\r", "").strip()
    except Exception:
        if check:
            raise
        return ""


def tv_locale():
    loc = tv("getprop", "persist.sys.locale") or tv("settings", "get", "system", "system_locales").split(",")[0]
    return "" if loc in ("", "null") else loc


def default_lang(locale):
    if not locale:
        return "en"
    lang, _, region = locale.partition("-")
    if lang == "pt":
        return "pt-PT" if region == "PT" else "pt-BR"
    if lang == "es":
        return "es-ES" if region == "ES" else "es-419"
    return lang if lang in LANGUAGES else "en"


def ask(q, data, answers, texts, defaults):
    ui = texts.ui
    opts = options_for(q, data, answers) if q["type"] in ("single", "multi") else []
    default = defaults.get(q["id"], q.get("default"))
    print()
    print("  " + tr(q["text"], ui))
    if q["type"] in ("single", "multi"):
        width = len(str(len(opts)))
        cols = 2 if len(opts) > 12 else 1
        rows = (len(opts) + cols - 1) // cols
        for r in range(rows):
            line = ""
            for c in range(cols):
                i = r + c * rows
                if i < len(opts):
                    mark = "•" if (default == opts[i]["v"] or (isinstance(default, list) and opts[i]["v"] in default)) else " "
                    line += f"   {mark}{str(i + 1).rjust(width)}  {tr(opts[i]['t'], ui)[:34]:<36}"
            print(line.rstrip())
    while True:
        hint = {"single": texts("pick_one"), "multi": texts("pick_many"),
                "yesno": f"{texts('yes')}/{texts('no')}"}.get(q["type"], "")
        shown = ""
        if default:
            if q["type"] == "single":
                shown = next((tr(o["t"], ui) for o in opts if o["v"] == default), str(default))
            elif q["type"] == "multi":
                shown = ", ".join(tr(o["t"], ui) for o in opts if o["v"] in default)
            elif q["type"] == "yesno":
                shown = texts("yes") if default == "y" else texts("no")
            else:
                shown = str(default)
        try:
            raw = input(f"  › {hint}{' [' + shown + ']' if shown else ''}: ").strip()
        except EOFError:
            raw = ""
        if not raw:
            if default is not None and default != "":
                return default
            if q.get("optional"):
                return [] if q["type"] == "multi" else ""
            if q["type"] == "text" and not q.get("pattern"):
                return ""
            print("  " + texts("again"))
            continue
        if q["type"] == "text":
            if q.get("pattern") and not re.match(q["pattern"], raw):
                print("  " + texts("again"))
                continue
            return raw.upper() if q["id"] == "country" else raw
        if q["type"] == "yesno":
            r = raw.lower()
            if r[:1] in ("y", "e", "s", "j", "o", "1"):
                return "y"
            if r[:1] in ("n", "h", "0"):
                return "n"
            print("  " + texts("again"))
            continue
        picks = [p for p in re.split(r"[ ,;]+", raw) if p]
        try:
            chosen = [opts[int(p) - 1]["v"] for p in picks]
        except (ValueError, IndexError):
            print("  " + texts("again"))
            continue
        if q["type"] == "single":
            if len(chosen) != 1:
                print("  " + texts("again"))
                continue
            return chosen[0]
        return list(dict.fromkeys(chosen))


def interview(data):
    locale = tv_locale()
    answers = {}
    defaults = {"lang": default_lang(locale), "country": (locale.split("-")[1] if "-" in locale else ""),
                "audio": "dubbed", "quality": "4k", "phone": "android"}
    texts = Texts(data, ui_lang(defaults["lang"]))
    print("\n" + texts("intro"))
    for q in data["questions"]:
        if not visible(q, answers):
            continue
        answers[q["id"]] = ask(q, data, answers, texts, defaults)
        if q["id"] == "lang":
            texts = Texts(data, ui_lang(answers["lang"]))
            defaults["subs"] = [answers["lang"]]
    return answers


# ---- the plan ------------------------------------------------------------------------------------

def as_list(v):
    return v if isinstance(v, list) else ([v] if v else [])


def plan_for(p, data):
    """Turns the answers into exactly what this person's TV gets."""
    lang = p.get("lang") or "en"
    texts = Texts(data, ui_lang(lang))
    country = (p.get("country") or "").upper()
    loves = as_list(p.get("loves"))
    main = p.get("main") or (loves[0] if loves else "films")
    torr_lang, iso = LANGUAGES.get(lang, ("english", "en"))
    video = any(x in loves for x in VIDEO) or (
        "sports" in loves and any(w in as_list(p.get("sports_what")) for w in ("docs", "films")))
    addons, plugins, follow = [], [], []

    # Streams and subtitles, only for someone who watches films or series of any kind
    if video:
        langs = []
        audio = p.get("audio", "either")
        extra = [LANGUAGES[x][0] for x in as_list(p.get("more_audio")) if x in LANGUAGES]
        if audio in ("dubbed", "either"):
            langs.append(torr_lang)
        langs += extra
        if "anime" in loves:
            if p.get("anime_audio") in ("sub", "either"):
                langs.append("japanese")
            elif torr_lang not in langs:
                langs.append(torr_lang)
        langs = [x for x in dict.fromkeys(langs) if x != "english"]
        hide = {"3d": "threed"}
        qf = [hide.get(x, x) for x in as_list(p.get("hide") or ["cam", "scr", "480p"])]
        qf += {"1080p": ["4k", "brremux"], "720p": ["4k", "brremux", "1080p"]}.get(p.get("quality"), [])
        parts = ["sort=qualitysize"]
        if langs:
            parts.append("language=" + ",".join(langs))
        if qf:
            parts.append("qualityfilter=" + ",".join(dict.fromkeys(qf)))
        addons.append(("torrentio", ADDONS["torrentio"].format(config="|".join(parts) + "/")))
        addons.append(("opensubtitles", ADDONS["opensubtitles"]))
    subs = as_list(p.get("subs")) or [lang]
    if video and any(s.startswith("tr") for s in subs):
        addons.append(("tr_altyazi", ADDONS["tr_altyazi"]))
    if "anime" in loves:
        addons.append(("anime_kitsu", ADDONS["anime_kitsu"]))
    services = as_list(p.get("services"))
    if "top10" in services and country:
        cfg = f"::{country}:{int(time.time())}:0:1:{country}"
        addons.append(("top10", ADDONS["top10"].format(config=base64.urlsafe_b64encode(cfg.encode()).decode().rstrip("="))))
    if "youtube" in loves or "music" in loves or "highlights" in as_list(p.get("sports_what")):
        addons.append(("youtube", ADDONS["youtube"]))
        follow = [t.strip() for t in (p.get("teams") or "").split(",") if t.strip()]

    # Free live channels: iptv-org's public index for the chosen countries (paid sports channels excluded)
    channels = None
    if "channels" in loves:
        cats = []
        for k in as_list(p.get("channel_kinds")):
            cats += k.split(",")
        countries = [c.strip().upper() for c in (p.get("channel_countries") or country).split(",") if c.strip()]
        channels = {"provider": "iptv-org", "iptvOrgCountry": ",".join(countries), "iptvOrgCategory": ",".join(dict.fromkeys(cats))}
        addons.append(("channels", None))
        if "TR" in countries:
            free = ["Anthology Ulusal Kanallar"]
            kinds = as_list(p.get("channel_kinds"))
            if "news" in kinds:
                free.append("Anthology Canlı Haber")
            if any(k.startswith(("kids", "documentary")) for k in kinds):
                free.append("Anthology Belgesel & Çocuk")
            if any(k.startswith(("music", "entertainment")) for k in kinds):
                free.append("Anthology Müzik & Eğlence")
            plugins += free

    # Every Turkish-speaking profile gets all of Anthology (films, series, anime, channels, archives);
    # others who watch Turkish titles get its official archives
    anthology_all = lang == "tr"
    origins = [country if o == "@home" else o for o in as_list(p.get("series_origins")) + as_list(p.get("film_origins"))]
    if anthology_all:
        plugins = anthology_plugins() or plugins
    elif "TR" in origins:
        plugins += ["Anthology Dizi", "YouTube Dizi & Film", "PuhuTV"]
    plugins = list(dict.fromkeys(plugins))

    collections = build_collections(p, data, texts, country, main)

    # Only the apps this person will open
    apps = ["nuvio"] if addons or collections or plugins or anthology_all else []
    if "youtube" in loves:
        apps.append("youtube")
    if p.get("cast", "y") == "y":
        apps.append("nova")
    if p.get("phone") in ("iphone", "both"):
        apps.append("airplay")
    skipped = [a for a in ("nuvio", "youtube", "nova", "airplay") if a not in apps]
    return {"lang": lang, "ui": texts.ui, "iso": iso, "country": country, "city": p.get("city") or "",
            "main": main, "addons": addons, "channels": channels, "plugins": plugins, "follow": follow,
            "anthology_all": anthology_all, "subs": subs, "autoplay": p.get("autoplay", "y") == "y", "collections": collections,
            "apps": apps, "skipped": skipped, "notes": p.get("notes") or ""}


def source(media, title, sort="popularity.desc", **filters):
    f = {k: v for k, v in filters.items() if v not in (None, "", [])}
    return {"provider": "tmdb", "tmdbSourceType": "DISCOVER", "title": title, "mediaType": media, "sortBy": sort,
            "filters": f}


def build_collections(p, data, texts, country, main):
    loves = as_list(p.get("loves"))
    areas = {}
    origin_name = lambda o: texts.option("origins", o) if o != "@home" else (
        texts.option("origins", country) if country else texts.option("origins", o))
    resolve = lambda o: (country if o == "@home" else o).replace(",", "|")

    def folder(area, key, title, sources, emoji=None):
        areas.setdefault(area, []).append({"id": f"cinetv-{area}-{key}", "title": title, "coverEmoji": emoji or EMOJI.get(area),
                                           "tileShape": "SQUARE", "sources": sources})

    if "films" in loves:
        avoid = ",".join(as_list(p.get("film_avoid")))
        eras = as_list(p.get("film_eras"))
        bounds = {"classic": ("", "1969-12-31"), "70s90s": ("1970-01-01", "1999-12-31"),
                  "00s": ("2000-01-01", "2014-12-31"), "recent": ("2015-01-01", "")}
        gte = min((bounds[e][0] for e in eras), default="") if eras and "classic" not in eras else ""
        lte = max((bounds[e][1] for e in eras), default="") if eras and "recent" not in eras else ""
        common = dict(withoutGenres=avoid, releaseDateGte=gte, releaseDateLte=lte)
        style = p.get("film_style", "mix")
        if style in ("popular", "mix"):
            folder("films", "popular", texts("row_popular_films"), [source("movie", texts("row_popular_films"), voteCountGte=300, **common)])
        if style in ("acclaimed", "mix"):
            folder("films", "acclaimed", texts("row_acclaimed"), [source("movie", texts("row_acclaimed"), "vote_average.desc", voteAverageGte=7.6, voteCountGte=800, **common)])
        if style == "gems":
            folder("films", "gems", texts("row_gems"), [source("movie", texts("row_gems"), "vote_average.desc", voteAverageGte=7.2, voteCountGte=60, **common)])
        for g in as_list(p.get("film_genres")):
            t = texts("row_films", genre=texts.option("genres", g))
            folder("films", "g" + g, t, [source("movie", t, withGenres=g, voteCountGte=150, **common)])
        for o in as_list(p.get("film_origins")):
            t = texts("row_films_from", country=origin_name(o))
            folder("films", "o" + resolve(o), t, [source("movie", t, withOriginCountry=resolve(o), voteCountGte=40, **common)])

    origins = [resolve(o) for o in as_list(p.get("series_origins"))]
    only = "|".join(origins) if p.get("series_only_origins") == "y" and origins else None
    if "series" in loves:
        if not only:
            folder("series", "popular", texts("row_popular_series"), [source("tv", texts("row_popular_series"), voteCountGte=200)])
        for g in as_list(p.get("series_genres")):
            name = next((tr(x["t"], texts.ui) for x in data["lists"]["tv_genres"] if x["v"] == g), g)
            t = texts("row_series", genre=name)
            folder("series", "g" + g, t, [source("tv", t, withGenres=g, withOriginCountry=only, voteCountGte=20 if only else 100)])
        for o in as_list(p.get("series_origins")):
            t = texts("row_series_from", country=origin_name(o))
            folder("series", "o" + resolve(o), t, [source("tv", t, withOriginCountry=resolve(o), voteCountGte=10)])

    if "anime" in loves:
        kinds = as_list(p.get("anime_kind")) or ["series", "films"]
        if "series" in kinds:
            folder("anime", "series", texts("row_anime_series"), [source("tv", texts("row_anime_series"), withGenres="16", withOriginalLanguage="ja", voteCountGte=50)])
        if "films" in kinds:
            folder("anime", "films", texts("row_anime_films"), [source("movie", texts("row_anime_films"), withGenres="16", withOriginalLanguage="ja", voteCountGte=50)])
        for k in kinds:
            if k in ANIME_KINDS:
                genre, kw = ANIME_KINDS[k]
                label = next(tr(o["t"], texts.ui) for o in next(q for q in data["questions"] if q["id"] == "anime_kind")["options"] if o["v"] == k)
                t = texts("row_anime_kind", kind=label)
                folder("anime", k, t, [source("tv", t, withGenres="16" + ("," + genre if genre else ""), withKeywords=kw, withOriginalLanguage="ja", voteCountGte=20)])

    if "sports" in loves:
        what = as_list(p.get("sports_what"))
        sports = as_list(p.get("sports"))
        if "films" in what:
            for s in sports:
                kw = s.replace(",", "|")
                t = texts("row_sport", sport=texts.option("sports", s))
                folder("sports", "s" + kw, t, [source("movie", t, withKeywords=kw, voteCountGte=10), source("tv", t, withKeywords=kw, voteCountGte=5)], EMOJI.get(s))
        if "docs" in what:
            kw = "|".join(["159290"] + [s.replace(",", "|") for s in sports])
            t = texts("row_sport_docs")
            folder("sports", "docs", t, [source("movie", t, withGenres="99", withKeywords=kw, voteCountGte=5), source("tv", t, withGenres="99", withKeywords=kw, voteCountGte=3)])

    if "docs" in loves:
        for topic in as_list(p.get("doc_topics")) or ["221355,9902"]:
            kw = topic.replace(",", "|")
            t = texts("row_doc", topic=texts.option("doc_topics", topic))
            folder("docs", kw, t, [source("movie", t, withGenres="99", withKeywords=kw, voteCountGte=10), source("tv", t, withGenres="99", withKeywords=kw, voteCountGte=5)])

    kids = "kids" in loves or p.get("household") == "kids"
    if kids:
        young = any(a in as_list(p.get("kids_ages")) for a in ("0-4", "5-8"))
        folder("kids", "series", texts("row_kids_series"), [source("tv", texts("row_kids_series"), withGenres="10762", voteCountGte=10)])
        folder("kids", "films", texts("row_kids_films"), [source("movie", texts("row_kids_films"), withGenres="16,10751" if young else "10751", voteCountGte=100)])

    if "reality" in loves:
        folder("reality", "reality", texts("row_reality"), [source("tv", texts("row_reality"), withGenres="10764", voteCountGte=5)])
        folder("reality", "talk", texts("row_talk"), [source("tv", texts("row_talk"), withGenres="10767", voteCountGte=5)])
    if "music" in loves:
        folder("music", "music", texts("row_music"), [source("movie", texts("row_music"), withGenres="10402", voteCountGte=30)])

    # What each service has in this country, only in the kinds this person watches
    wants_movies = any(x in loves for x in ("films", "kids", "docs", "music"))
    wants_tv = any(x in loves for x in ("series", "anime", "kids", "docs", "reality"))
    for svc in as_list(p.get("services")):
        if svc in PROVIDERS and country:
            t = texts("row_on", service=texts.option("services", svc))
            sources = []
            if wants_movies:
                sources.append(source("movie", t, watchRegion=country, withWatchProviders=PROVIDERS[svc]))
            if wants_tv:
                sources.append(source("tv", t, watchRegion=country, withWatchProviders=PROVIDERS[svc], withOriginCountry=only))
            if sources:
                folder("services", svc, t, sources)

    loves_opts = next(q for q in data["questions"] if q["id"] == "loves")["options"]
    area_name = lambda a: texts("area_services") if a == "services" else next(tr(o["t"], texts.ui) for o in loves_opts if o["v"] == a)
    order = [main] + [a for a in ("films", "series", "anime", "sports", "docs", "kids", "reality", "music", "services") if a != main]
    name = (p.get("name") or "").strip()
    out = []
    for i, a in enumerate(x for x in order if x in areas):
        title = area_name(a)
        if i == 0:
            title = (texts("for_name", name=name) if name else texts("for_you")) + " · " + title
        out.append({"id": f"cinetv-{a}", "title": title, "pinToTop": i == 0, "showAllTab": True, "folders": areas[a]})
    return out


def describe(plan, data):
    texts = Texts(data, plan["ui"])
    lang_name = next(o["t"] for o in next(q for q in data["questions"] if q["id"] == "lang")["options"] if o["v"] == plan["lang"])
    lines = [texts("summary"), "  • " + texts("plan_language", lang=lang_name, subs=", ".join(plan["subs"]))]
    rows = sum(len(c["folders"]) for c in plan["collections"])
    if rows:
        lines.append("  • " + texts("plan_rows", title=plan["collections"][0]["title"], n=rows))
        for c in plan["collections"]:
            lines.append("      " + c["title"] + ": " + ", ".join(f["title"] for f in c["folders"]))
    if plan["addons"]:
        lines.append("  • " + texts("plan_addons", list=", ".join(ADDON_NAMES[a] for a, _ in plan["addons"])))
    if plan["anthology_all"]:
        lines.append("  • " + texts("plan_anthology", n=len(plan["plugins"]) or "", skip=", ".join(ANTHOLOGY_SKIP)))
    elif plan["plugins"]:
        lines.append("  • " + texts("plan_plugins", list=", ".join(plan["plugins"])))
    if plan["follow"]:
        lines.append("  • " + texts("plan_youtube", list=", ".join(plan["follow"])))
    names = {"nuvio": "Nuvio", "youtube": "YouTube (TizenTube)", "nova": "Nova Video Player", "airplay": "AirPlay"}
    lines.append("  • " + texts("plan_apps", list=", ".join(names[a] for a in plan["apps"]) or texts("none")))
    if plan["skipped"]:
        lines.append("  • " + texts("plan_skipped", list=", ".join(names[a] for a in plan["skipped"])))
    return "\n".join(lines)


# ---- applying it to the TV -----------------------------------------------------------------------

def channels_url(config):
    token = fetch_json(NEXOTV + "/encrypt", config)["token"]
    return ADDONS["channels"].format(config=token)


def install_addon(path):
    # Single quotes for the TV's shell: Torrentio options contain "|".
    subprocess.run(["adb", "shell", f"am start -a android.intent.action.VIEW -d 'stremio://{path}' com.nuvio.tv"],
                   capture_output=True, timeout=30)
    time.sleep(4)


def apply(plan, data, out_dir):
    texts = Texts(data, plan["ui"])
    if subprocess.run(["adb", "get-state"], capture_output=True, text=True).stdout.strip() != "device":
        sys.exit("No adb device. Run: adb connect <TV_IP>:5555")

    locale = tv_locale()
    if locale and default_lang(locale).split("-")[0] != plan["lang"].split("-")[0]:
        tv("am", "start", "-a", "android.settings.LOCALE_SETTINGS")
        lang_name = next(o["t"] for o in next(q for q in data["questions"] if q["id"] == "lang")["options"] if o["v"] == plan["lang"])
        print("  ! " + texts("tv_language", lang=lang_name))

    if plan["apps"]:
        subprocess.run(["bash", os.path.join(HERE, "setup_apps.sh"), "--only", ",".join(plan["apps"])])

    if "nuvio" in plan["apps"]:
        for name, path in plan["addons"]:
            if name == "channels":
                try:
                    path = channels_url(plan["channels"])
                except Exception as e:
                    print(f"  ! {ADDON_NAMES[name]}: {e}")
                    continue
            install_addon(path)
            print(f"  ✓ {ADDON_NAMES[name]}")
        if plan["collections"]:
            local = os.path.join(out_dir, "nuvio-collections.json")
            with open(local, "w", encoding="utf-8") as f:
                json.dump(plan["collections"], f, ensure_ascii=False, indent=1)
            subprocess.run(["adb", "push", local, "/sdcard/Download/nuvio-collections.json"], capture_output=True)
            tv("am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file:///sdcard/Download/nuvio-collections.json")

    prefs = {"language": plan["lang"], "subtitles": plan["subs"], "autoplay_best_source": plan["autoplay"],
             "plugin_repository": ANTHOLOGY if plan["plugins"] or plan["anthology_all"] else None,
             "plugins_to_enable": "all except plugins_to_leave_off" if plan["anthology_all"] and not plan["plugins"] else plan["plugins"],
             "plugins_to_leave_off": list(ANTHOLOGY_SKIP) if plan["plugins"] or plan["anthology_all"] else [],
             "youtube_follow": plan["follow"], "collections_file": "/sdcard/Download/nuvio-collections.json" if plan["collections"] else None,
             "notes": plan["notes"]}
    with open(os.path.join(out_dir, "nuvio-preferences.json"), "w", encoding="utf-8") as f:
        json.dump(prefs, f, ensure_ascii=False, indent=2)

    if "nuvio" in plan["apps"]:
        print("\n" + texts("steps"))
        if plan["collections"]:
            print("  1. " + texts("import_rows"))
        if plan["anthology_all"]:
            print("  2. " + texts("step_repo_all", url=ANTHOLOGY, skip=", ".join(ANTHOLOGY_SKIP)))
        elif plan["plugins"]:
            print("  2. " + texts("step_repo", url=ANTHOLOGY, list=", ".join(plan["plugins"])))
        print("  3. " + texts("step_subs", list=", ".join(plan["subs"]), auto=texts("yes") if plan["autoplay"] else texts("no")))
        if plan["follow"]:
            print("  4. " + texts("step_youtube", list=", ".join(plan["follow"])))
    print("\n" + texts("done"))


def main():
    ap = argparse.ArgumentParser(description="Set CineTV up for the person who watches it.")
    ap.add_argument("--profile", help="answers saved earlier (cinetv-profile.json) instead of the interview")
    ap.add_argument("--interview-only", action="store_true", help="only ask and save the answers")
    ap.add_argument("--plan", action="store_true", help="show what would be set up, change nothing")
    ap.add_argument("--out", default=".", help="where to write cinetv-profile.json and the Nuvio files")
    args = ap.parse_args()
    data = load_questions()
    if args.profile:
        with open(args.profile, encoding="utf-8") as f:
            profile = json.load(f)
    else:
        profile = interview(data)
        path = os.path.join(args.out, "cinetv-profile.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(profile, f, ensure_ascii=False, indent=2)
        print("\n" + Texts(data, ui_lang(profile.get("lang")))("saved") + " " + path)
    if args.interview_only:
        return
    plan = plan_for(profile, data)
    print("\n" + describe(plan, data))
    if args.plan:
        return
    apply(plan, data, args.out)


if __name__ == "__main__":
    main()
