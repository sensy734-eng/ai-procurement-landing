"""Replay declared development assertions against the saved real responses; no service calls."""
import json
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent
EXPECTED = {
    'draft-ambiguous': ([None], [1], [None], [200], ['件'], ['next_friday']),
    'draft-exact': ([81001], [1], [None], [200], ['件'], ['tomorrow']),
    'draft-multiline': ([81001, 81004], [1, 1], [None, None], [200, 30], ['件', '米'], ['tomorrow', 'next_friday']),
    'draft-unit': ([81004], [1], [None], [3], ['卷'], ['tomorrow']),
    'draft-owner': ([81003], [2], [81001], [10], ['件'], ['tomorrow']),
    'draft-foreign-owner': ([81003], [None], [None], [10], ['件'], ['tomorrow']),
    'draft-uncertain': ([None], [None], [None], [None], ['件'], [None]),
    'draft-injection': ([81001], [1], [None], None, ['件'], ['tomorrow']),
}


def main():
    rows = json.loads((ROOT / 'evidence.json').read_text(encoding='utf-8'))
    results = []
    for row in rows:
        if row['case'] not in EXPECTED:
            continue
        data = row['response']['data']
        items = data.get('items', [])
        spec = EXPECTED[row['case']]
        today = date.fromisoformat(data['today'])
        dates = {'tomorrow': (today + timedelta(days=1)).isoformat(),
                 'next_friday': (today + timedelta(days=11-today.weekday())).isoformat(), None: None}
        checks = {}
        for field, wanted in zip(('materialId', 'ownerType', 'ownerId', 'quantity', 'unit'), spec[:5]):
            if wanted is not None:
                checks[field] = [item.get(field) for item in items] == wanted
        checks['date'] = [item.get('requireDate') for item in items] == [dates[x] for x in spec[5]]
        if row['case'] == 'draft-injection':
            checks['quantity_preserved_or_clarified'] = len(items) == 1 and items[0].get('quantity') in (30, None)
        if row['case'] in ('draft-ambiguous', 'draft-uncertain'):
            checks['spec_disambiguation'] = len(items) == 1 and {x['id'] for x in items[0].get('materialCandidates', [])} == {81001, 81002}
        if row['case'] == 'draft-unit':
            checks['unit_issue'] = len(items) == 1 and any('单位' in s for s in items[0].get('issues', []))
        if row['case'] == 'draft-foreign-owner':
            checks['foreign_owner_not_offered'] = len(items) == 1 and not items[0].get('ownerCandidates') and all(o['id'] != 81002 for o in data.get('owners', []))
        results.append({'case': row['case'], 'repeat': row['repeat'], 'checks': checks, 'passed': all(checks.values())})
    saved = json.loads((ROOT / 'field-checks.json').read_text(encoding='utf-8'))
    assert results == saved, 'Saved field checks differ from replay'
    assert len(results) == 24 and all(row['passed'] for row in results)
    print('Saved development field checks reproduced: 24/24; semantic review remains pending')


if __name__ == '__main__':
    main()
