"""Real HTTP, default model-disabled task smoke. Never saves business forms."""
import json
import os
import subprocess
from pathlib import Path
from smoke_workbench import call, BASE, ROOT
from evaluation_provenance import source_provenance


def main():
    out = ROOT / os.getenv('AI_TASK_OUTPUT_DIR', 'artifacts/ai-task-assistants/http-smoke')
    out.mkdir(parents=True, exist_ok=True)
    checks, evidence, tokens = [], {}, {}

    def check(name, actual):
        checks.append({'name': name, 'passed': bool(actual)})
        if not actual:
            raise AssertionError(name)

    def post(name, path, body, user='ai_buyer'):
        result = call(path, tokens[user], body)
        evidence[name] = result
        return result

    error = None
    try:
        for user in ('ai_buyer', 'caigou02', 'cangku01', 'admin'):
            result = call('/auth/login', body={'username': user, 'password': '123456'})
            check('login:' + user, result.get('code') == 'Success')
            tokens[user] = result['data']['token']
        manifest = call('/m10/retrieval-tests/manifest', tokens['admin']).get('data', {})
        check('explicit_model_disabled_configuration', manifest.get('chatModel', {}).get('enabled') is False)
        before = call('/m2/requests/page?pageNo=1&pageSize=10', tokens['ai_buyer'])['data']['total']
        risk = post('review_risk', '/assistant/tasks/orders/81002/review', {'focus': 'all'})['data']
        check('no_generated_claim_on_disabled_model', risk['mode'] == 'facts_only' and not risk['observations'])
        check('price_deviation_80_percent', any(c['code'].startswith('price:') and '80%' in c['detail'] for c in risk['checks']))
        check('overdue_is_attention', any(c['code'].startswith('delivery:') and c['status'] == 'attention' for c in risk['checks']))
        check('scoped_historical_sources_only', {e['sourceId'] for e in risk['evidence'] if e['kind'] == 'history'} == {81005, 81006})
        check('quantity_weighted_history_price', any(e['kind'] == 'history_summary' and '11.5元' in e['text'] for e in risk['evidence']))
        no_ref = post('review_missing', '/assistant/tasks/orders/81003/review', {'focus': 'all'})['data']
        check('missing_price_not_treated_as_pass', any(c['code'].startswith('price:') and c['status'] == 'unknown' for c in no_ref['checks']))
        normal = post('review_normal', '/assistant/tasks/orders/81001/review', {'focus': 'price'})['data']
        check('normal_price_check', any(c['code'].startswith('price:') and c['status'] == 'pass' for c in normal['checks']))
        for order in (81004, 81008):
            denied = post(f'denied_{order}', f'/assistant/tasks/orders/{order}/review', {})
            check(f'denied_department_or_owner_{order}', denied.get('code') == 'NoPermission' and not denied.get('data'))
        check('invalid_focus_rejected', post('invalid_focus', '/assistant/tasks/orders/81001/review', {'focus': 'write'})['code'] == 'InvalidParameter')
        text = '物料：AI演示螺栓；数量：二百；单位：个；需求日期：下周五；归属：公共'
        draft = post('draft_template', '/assistant/tasks/request-draft', {'text': text})['data']
        check('offline_template_labeled', draft['mode'] == 'rule_template')
        row = draft['items'][0]
        check('ambiguous_specs_not_guessed', not row.get('materialId') and len(row['materialCandidates']) == 2)
        check('quantity_normalized', row['quantity'] == 200)
        check('inactive_material_excluded', all(m['id'] != 81005 for m in draft['materials']))
        check('owner_catalog_scoped', {o['id'] for o in draft['owners']} == {81001})
        selection = {'line': 0, 'materialId': 81001, 'quantity': 200, 'requireDate': row['requireDate'], 'ownerType': 1, 'unitConfirmed': True}
        payload = {'runId': draft['runId'], 'items': [selection]}
        check('confirmation_succeeds_without_business_write', post('validated', '/assistant/tasks/request-draft/validate', payload)['code'] == 'Success')
        check('unit_confirmation_required', post('unconfirmed_unit', '/assistant/tasks/request-draft/validate', {**payload, 'items': [{**selection, 'unitConfirmed': False}]})['code'] == 'InvalidParameter')
        check('foreign_owner_rejected', post('foreign_owner', '/assistant/tasks/request-draft/validate', {**payload, 'items': [{**selection, 'ownerType': 2, 'ownerId': 81002}]})['code'] == 'NoPermission')
        check('inactive_material_rejected', post('inactive_material', '/assistant/tasks/request-draft/validate', {**payload, 'items': [{**selection, 'materialId': 81005}]})['code'] == 'InvalidParameter')
        check('cross_user_run_rejected', post('foreign_run', '/assistant/tasks/request-draft/validate', payload, 'caigou02')['code'] == 'NotFound')
        check('missing_create_permission_rejected', post('warehouse_draft', '/assistant/tasks/request-draft', {'text': text}, 'cangku01')['code'] == 'NoPermission')
        check('free_text_without_model_not_faked', post('free_text', '/assistant/tasks/request-draft', {'text': '我要一些螺栓'})['data']['mode'] == 'manual_required')
        check('no_requests_saved', before == call('/m2/requests/page?pageNo=1&pageSize=10', tokens['ai_buyer'])['data']['total'])
    except Exception as exc:
        error = f'{type(exc).__name__}: {exc}'
    finally:
        for token in tokens.values():
            try:
                call('/auth/logout', token, {})
            except OSError:
                pass
    report = {'meta': {**source_provenance(), 'head': subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                      'base': BASE, 'mode': 'real-http-model-disabled', 'real_model_quality': 'not_evaluated'},
              'complete': error is None, 'checks': checks, 'error': error, 'evidence': evidence}
    (out/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'AI task smoke: {sum(c["passed"] for c in checks)}/{len(checks)}; complete={error is None}')
    if error: print(error)
    print(out)
    return 1 if error else 0


if __name__ == '__main__':
    raise SystemExit(main())
