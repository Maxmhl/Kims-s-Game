#!/usr/bin/env python3
"""Erzeugt Vector Drawables (ic_*.xml) und assets/catalog.json.

Aufruf: python3 tools/gen_assets.py <ordner-mit-material-symbols-svg>
Quelle der Icons: Material Symbols (Apache 2.0), Gewicht 300, Stil "outlined".
15 Kategorien x 5 Grundobjekte x 4 Farben = 300 Objekte.
"""
import json, re, sys, pathlib

SRC = pathlib.Path(sys.argv[1])
ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable"
ASSETS = ROOT / "app/src/main/assets"

CATEGORIES = {
    "werkzeug": [("key", "Schlüssel"), ("build", "Schraubenschlüssel"), ("content_cut", "Schere"), ("straighten", "Lineal"), ("handyman", "Werkzeug")],
    "elektro": [("bolt", "Blitz"), ("battery_full", "Batterie"), ("power", "Stecker"), ("lightbulb", "Glühbirne"), ("memory", "Chip")],
    "fahrzeuge": [("directions_car", "Auto"), ("flight", "Flugzeug"), ("directions_boat", "Boot"), ("pedal_bike", "Fahrrad"), ("train", "Zug")],
    "tiere": [("pets", "Pfote"), ("cruelty_free", "Hase"), ("flutter_dash", "Vogel"), ("set_meal", "Fisch"), ("pest_control", "Käfer")],
    "essen": [("local_cafe", "Tasse"), ("restaurant", "Besteck"), ("cake", "Kuchen"), ("egg", "Ei"), ("icecream", "Eis")],
    "symbole": [("star", "Stern"), ("favorite", "Herz"), ("anchor", "Anker"), ("flag", "Flagge"), ("notifications", "Glocke")],
    "formen": [("circle", "Kreis"), ("square", "Quadrat"), ("change_history", "Dreieck"), ("hexagon", "Sechseck"), ("diamond", "Raute")],
    "natur": [("eco", "Blatt"), ("water_drop", "Tropfen"), ("wb_sunny", "Sonne"), ("nightlight", "Mond"), ("ac_unit", "Schneeflocke")],
    "buero": [("edit", "Stift"), ("book", "Buch"), ("attach_file", "Büroklammer"), ("mail", "Brief"), ("calculate", "Rechner")],
    "medien": [("music_note", "Note"), ("headphones", "Kopfhörer"), ("mic", "Mikrofon"), ("videocam", "Kamera"), ("photo_camera", "Fotoapparat")],
    "sport": [("sports_soccer", "Fußball"), ("sports_basketball", "Basketball"), ("fitness_center", "Hantel"), ("sports_tennis", "Tennisschläger"), ("skateboarding", "Skateboard")],
    "haushalt": [("chair", "Stuhl"), ("bed", "Bett"), ("door_front", "Tür"), ("kitchen", "Kühlschrank"), ("shower", "Dusche")],
    "technik": [("watch", "Uhr"), ("keyboard", "Tastatur"), ("tv", "Fernseher"), ("mouse", "Maus"), ("router", "Router")],
    "kleidung": [("checkroom", "Kleiderbügel"), ("dry_cleaning", "Hemd"), ("eyeglasses", "Brille"), ("umbrella", "Schirm"), ("luggage", "Koffer")],
    "labor": [("science", "Kolben"), ("biotech", "Mikroskop"), ("medication", "Tablette"), ("vaccines", "Spritze"), ("stethoscope", "Stethoskop")],
}
# Bei diesen Grundobjekten ist eine Drehung nicht erkennbar.
NOT_ROTATABLE = {"circle", "square", "diamond"}
COLORS = ["amber", "cyan", "white", "coral"]

XML = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="40dp"
    android:height="40dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="{d}" />
    </group>
</vector>
"""

catalog = []
for cat, bases in CATEGORIES.items():
    for base, label in bases:
        svg = (SRC / f"{base}.svg").read_text()
        d = re.search(r'<path d="([^"]+)"', svg).group(1)
        (RES / f"ic_{base}.xml").write_text(XML.replace("{d}", d))
        for c in COLORS:
            catalog.append({
                "id": f"{base}_{c}", "category": cat, "base": base, "label": label,
                "variant": "standard", "color": c, "icon": f"ic_{base}", "group": cat,
                "rotatable": base not in NOT_ROTATABLE,
            })
seen = sorted({e["icon"] for e in catalog})
reg = ["package de.max.gedaechtnis.core", "", "import de.max.gedaechtnis.R", "",
       "/** Erzeugt von tools/gen_assets.py: Katalog-Icon-Name -> Vector Drawable. */",
       "object IconRegistry {", "    val icons: Map<String, Int> = mapOf("]
reg += [f'        "{i}" to R.drawable.{i},' for i in seen]
reg += ["    )", "}", ""]
(ROOT / "app/src/main/java/de/max/gedaechtnis/core/IconRegistry.kt").write_text("\n".join(reg))
(ASSETS / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=1))
# UI-Icons (ui_*.xml) für Navigation und Buttons
UI_ICONS = ["arrow_back", "close", "settings", "add", "home", "bar_chart", "style", "person", "check",
            "extension", "backspace", "delete", "notifications", "volume_up", "schedule"]
for n in UI_ICONS:
    svg = (SRC / f"{n}.svg").read_text()
    d = re.search(r'<path d="([^"]+)"', svg).group(1)
    (RES / f"ui_{n}.xml").write_text(XML.replace("{d}", d).replace('"40dp"', '"24dp"'))
print(len(catalog), "Objekte")
