#!/usr/bin/env python3
"""Controleert data/regelingen.json voordat de routine het publiceert.

Gebruik: python3 scripts/valideer.py data/regelingen.json
Exitcode 0 = in orde, 1 = fouten (die worden afgedrukt).
"""
import datetime
import json
import sys

CATEGORIEEN = {"korte film", "speelfilm", "telefilm", "documentaire", "animatie", "ontwikkeling", "talent", "overig"}


def main(pad: str) -> int:
    fouten = []
    with open(pad, encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data.get("bijgewerkt"), str) or not data["bijgewerkt"]:
        fouten.append("'bijgewerkt' ontbreekt")
    regelingen = data.get("regelingen")
    if not isinstance(regelingen, list) or not regelingen:
        fouten.append("'regelingen' ontbreekt of is leeg")
        regelingen = []
    gezien = set()
    for i, r in enumerate(regelingen):
        label = f"regeling {i} ({r.get('naam', '?')})"
        for veld in ("naam", "fonds", "url"):
            if not isinstance(r.get(veld), str) or not r[veld].strip():
                fouten.append(f"{label}: '{veld}' ontbreekt")
        sleutel = (r.get("fonds", "").lower().strip(), r.get("naam", "").lower().strip())
        if sleutel in gezien:
            fouten.append(f"{label}: dubbel (zelfde fonds en naam)")
        gezien.add(sleutel)
        for c in r.get("categorieen", []):
            if c not in CATEGORIEEN:
                fouten.append(f"{label}: onbekende categorie '{c}'")
        for d in r.get("deadlines", []):
            try:
                datetime.date.fromisoformat(d.get("datum", ""))
            except ValueError:
                fouten.append(f"{label}: ongeldige datum '{d.get('datum')}' (verwacht JJJJ-MM-DD)")
        if not isinstance(r.get("voorwaarden", []), list):
            fouten.append(f"{label}: 'voorwaarden' moet een lijst zijn")
    for fout in fouten:
        print("FOUT:", fout)
    if not fouten:
        print(f"OK: {len(regelingen)} regelingen, bijgewerkt {data['bijgewerkt']}")
    return 1 if fouten else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "data/regelingen.json"))
