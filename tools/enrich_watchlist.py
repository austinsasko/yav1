#!/usr/bin/env python3
"""Build the YaV1 aerial-enforcement watchlist from the FAA Releasable
Aircraft Registry + sdr-enthusiasts/plane-alert-db (plane-alert-pol.csv).

Output CSV columns: icao_hex,registration,agency,model,source,confidence
(the app's loader reads the first four and ignores extras).
"""
import csv, collections, re, sys, datetime

# usage: enrich_watchlist.py <faa_dir> [out_csv]
#   faa_dir: directory with the extracted FAA ReleasableAircraft files
#            (MASTER.txt, ACFTREF.txt) plus plane-alert-pol.csv from
#            github.com/sdr-enthusiasts/plane-alert-db
if len(sys.argv) < 2:
    sys.exit('usage: enrich_watchlist.py <faa_dir> [out_csv]')
FAA_DIR = sys.argv[1]

# states with documented aircraft speed enforcement programs
ENFORCEMENT_STATES = {'OH','TX','AK','VA','NC','FL','CA','WA'}

# state-level law-enforcement aviation (high confidence for fixed wing)
STATE_PATTERNS = [
    'HIGHWAY PATROL',
    'STATE PATROL',
    'STATE POLICE',
    'DEPT OF PUBLIC SAFETY',
    'DEPARTMENT OF PUBLIC SAFETY',
    'PUBLIC SAFETY DEPT',
    'AERIAL ENFORCEMENT',
]

# registrant names that indicate non-enforcement missions even when a
# pattern matches (precision guard)
EXCLUDE_SUBSTRINGS = [
    'FIRE DEPT', 'FIRE DEPARTMENT', 'FORESTRY', 'EMERGENCY MANAGEMENT',
    'UNIVERSITY', 'COLLEGE', 'SCHOOL',
]

TYPE_AIRCRAFT = {  # MASTER col "TYPE AIRCRAFT"
    '4': 'fixed-single', '5': 'fixed-multi', '6': 'rotorcraft',
}

def titlecase(name):
    return ' '.join(w.capitalize() for w in name.split())

def load_acftref():
    ref = {}
    with open(f'{FAA_DIR}/ACFTREF.txt', encoding='latin-1') as f:
        r = csv.reader(f)
        header = [h.strip() for h in next(r)]
        for row in r:
            if len(row) < 3:
                continue
            code = row[0].strip()
            mfr  = titlecase(row[1].strip())
            mdl  = row[2].strip()
            ref[code] = (mfr + ' ' + mdl).strip()
    return ref

def match_reason(name_uc, state):
    for pat in STATE_PATTERNS:
        if pat in name_uc:
            return 'state'
    if 'SHERIFF' in name_uc and state in ENFORCEMENT_STATES:
        return 'sheriff'
    return None

def main():
    ref = load_acftref()
    out = {}          # hex -> row dict
    stats = collections.Counter()

    with open(f'{FAA_DIR}/MASTER.txt', encoding='utf-8-sig', errors='replace') as f:
        r = csv.reader(f)
        header = [h.strip() for h in next(r)]
        idx = {h: i for i, h in enumerate(header)}
        I_N     = idx['N-NUMBER']
        I_MDL   = idx['MFR MDL CODE']
        I_NAME  = idx['NAME']
        I_STATE = idx['STATE']
        I_TYPE  = idx['TYPE AIRCRAFT']
        I_HEX   = idx['MODE S CODE HEX']

        for row in r:
            if len(row) <= I_HEX:
                continue
            name = row[I_NAME].strip()
            name_uc = name.upper()
            state = row[I_STATE].strip()

            reason = match_reason(name_uc, state)
            if not reason:
                continue
            if any(x in name_uc for x in EXCLUDE_SUBSTRINGS):
                stats['excluded-name'] += 1
                continue

            actype = row[I_TYPE].strip()
            if actype not in TYPE_AIRCRAFT:
                stats['excluded-airframe'] += 1   # gliders, balloons, UAV...
                continue

            hexcode = row[I_HEX].strip().upper()
            if not re.fullmatch(r'[0-9A-F]{6}', hexcode):
                stats['bad-hex'] += 1
                continue

            nnum = 'N' + row[I_N].strip()
            model = ref.get(row[I_MDL].strip(), '')
            frame = TYPE_AIRCRAFT[actype]

            if reason == 'sheriff' and frame == 'rotorcraft':
                stats['excluded-sheriff-rotorcraft'] += 1
                continue

            if frame == 'rotorcraft':
                confidence = 'low'          # patrol/medevac far more likely
            elif reason == 'sheriff':
                confidence = 'medium'
            else:
                confidence = 'high'

            out[hexcode] = {
                'hex': hexcode, 'reg': nnum,
                'agency': titlecase(name),
                'model': model,
                'source': 'faa',
                'confidence': confidence,
                'frame': frame, 'reason': reason, 'state': state,
            }
            stats[f'faa-{reason}-{frame}'] += 1

    # ---- plane-alert-db cross-check ------------------------------------
    US_OP = re.compile(r'(state police|highway patrol|state patrol|department of public safety)', re.I)
    pol_new = pol_tagged = 0
    with open(f'{FAA_DIR}/plane-alert-pol.csv', encoding='utf-8-sig') as f:
        for prow in csv.DictReader(f):
            hexcode = (prow.get('$ICAO') or '').strip().upper()
            regn    = (prow.get('$Registration') or '').strip().upper()
            oper    = (prow.get('$Operator') or '').strip()
            typ     = (prow.get('$Type') or '').strip()
            icaot   = (prow.get('$ICAO Type') or '').strip()
            tags    = [(prow.get(k) or '').strip() for k in ('$Tag 1', '$#Tag 2', '$#Tag 3')]
            speed_tag = any(t.lower() == 'speed enforced by aircraft' for t in tags)

            if not re.fullmatch(r'[0-9A-F]{6}', hexcode):
                continue
            # US-registered state-level agencies, or anything explicitly
            # tagged as aerial speed enforcement
            if not (speed_tag or (regn.startswith('N') and US_OP.search(oper))):
                continue

            heli = bool(re.search(r'(helicopter|bell |airbus h|eurocopter|as\d{3}|ec\d{2}|bo 105|uh-\d|md 5|md5|s-76|r44|r66|407|429|206|505)', typ, re.I)) \
                   or icaot.upper() in {'B06','B407','B429','B105','EC20','EC30','EC35','EC45','H60','R44','R66','S76','A109','A119','A139','AS50','AS55','MD52','MD60','H500','B505','B212','B412'}

            if speed_tag:
                pol_tagged += 1

            if hexcode in out:
                e = out[hexcode]
                e['source'] = 'faa+pol'
                if speed_tag:
                    e['confidence'] = 'high'
            else:
                pol_new += 1
                out[hexcode] = {
                    'hex': hexcode, 'reg': regn, 'agency': oper,
                    'model': typ,
                    'source': 'pol',
                    'confidence': ('high' if speed_tag and not heli
                                   else 'low' if heli else 'medium'),  # speed-tagged helis stay low
                    'frame': 'rotorcraft' if heli else 'fixed',
                    'reason': 'pol', 'state': '',
                }

    rows = sorted(out.values(), key=lambda e: (e['agency'].upper(), e['reg']))

    print('total entries:', len(rows))
    for k, v in sorted(stats.items()):
        print(' ', k, v)
    print('  pol-only new entries:', pol_new, ' speed-tagged rows:', pol_tagged)
    byconf = collections.Counter(e['confidence'] for e in rows)
    print('  confidence:', dict(byconf))
    bystate = collections.Counter(e['state'] for e in rows if e['state'] in ENFORCEMENT_STATES)
    print('  enforcement-state coverage:', dict(bystate))

    if len(sys.argv) > 2:
        with open(sys.argv[2], 'w', encoding='utf-8') as f:
            f.write(HEADER)
            for e in rows:
                agency = e['agency'].replace(',', ';')
                model  = e['model'].replace(',', ';')
                f.write(f"{e['hex']},{e['reg']},{agency},{model},{e['source']},{e['confidence']}\n")
        print('wrote', sys.argv[2])

HEADER = f"""# YaV1 aerial-enforcement watchlist
#
# Format: icao_hex,registration,agency,model,source,confidence
# The app reads the first four columns; source/confidence document
# per-entry provenance. Lines starting with '#' are comments. Matching is
# case-insensitive on the ICAO hex (Mode S) address first, then on the
# registration / callsign.
#
# Sources (regenerate with tools/enrich_watchlist.py):
#  - source=faa: FAA Releasable Aircraft Registry
#    (registry.faa.gov/database/ReleasableAircraft.zip), snapshot of
#    {datetime.date.today().isoformat()}. Registrant NAME matched against state-level
#    law-enforcement aviation patterns (HIGHWAY PATROL / STATE PATROL /
#    STATE POLICE / DEPT[ARTMENT] OF PUBLIC SAFETY / PUBLIC SAFETY DEPT /
#    AERIAL ENFORCEMENT) plus county SHERIFF registrants in states with
#    documented aircraft speed enforcement (OH TX AK VA NC FL CA WA);
#    sheriff ROTORCRAFT are excluded outright (metro patrol fleets would
#    swamp the list - e.g. 230 such helicopters matched in the 2026-07
#    snapshot and none are speed-timing platforms).
#    MODE S CODE HEX is the FAA-assigned ICAO 24-bit address. Gliders,
#    balloons and UAVs are excluded, as are FIRE/FORESTRY/EMERGENCY
#    MANAGEMENT/university registrants.
#  - source=pol: github.com/sdr-enthusiasts/plane-alert-db
#    plane-alert-pol.csv (community-curated), same snapshot date: US
#    state police / highway patrol / DPS operators and any aircraft
#    tagged "Speed Enforced by Aircraft" (the tag raises the entry's
#    confidence to high).
#  - source=faa+pol: present in both (FAA data wins for reg/model).
#
# Confidence:
#  - high:   state-level agency fixed-wing (the classic speed-timing
#            platform: Cessna 172/182/206, GA8, PC-12) or explicitly
#            speed-tagged in plane-alert-db
#  - medium: county sheriff fixed-wing in an enforcement state, or
#            state-agency aircraft known only from plane-alert-db
#  - low:    rotorcraft (mostly patrol / medevac; rarely time speed)
#
# Registrations change owners over time - refresh from the FAA registry
# periodically. Users can extend this list in
# <app storage>/aircraft/enforcement_user.csv
"""

if __name__ == '__main__':
    main()
