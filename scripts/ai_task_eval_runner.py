"""Capture real-model task outputs with explicit mechanical checks and pending human review."""
import argparse
import hashlib
import json
import os
import subprocess
import time
from decimal import Decimal, InvalidOperation
from pathlib import Path
from evaluation_provenance import source_provenance
from smoke_workbench import call, ROOT, BASE


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dataset', type=Path, default=ROOT/'docs/evaluation/ai-task-dev-cases.json')
    parser.add_argument('--output-dir', type=Path)
    parser.add_argument('--repeats', type=int, default=1, choices=range(1, 6))
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    raw = args.dataset.read_bytes()
    dataset = json.loads(raw)
    ids = [c['id'] for c in dataset['cases']]
    if len(set(ids)) != len(ids) or any(c['task'] not in ('review','draft') or not c['rubric'] for c in dataset['cases']):
        raise SystemExit('Invalid task dataset')
    if args.check:
        print(f'AI task development dataset valid: {len(ids)} cases (not a quality score)')
        return 0
    out = args.output_dir or ROOT/'artifacts/ai-task-assistants'/('real-model-'+time.strftime('%Y%m%d-%H%M%S'))
    if out.exists():
        raise SystemExit('Output already exists; choose another directory to preserve previous results')
    # Check configuration before any bill or task calls. Never treat offline templates as real AI quality.
    token = None
    admin_token = None
    results = []
    try:
        auth = call('/auth/login', body={'username':os.getenv('AI_TASK_ADMIN','admin'),'password':os.getenv('EVAL_PASSWORD','123456')})
        if auth.get('code') != 'Success': raise RuntimeError('Admin login failed')
        admin_token = auth['data']['token']
        manifest = call('/m10/retrieval-tests/manifest', admin_token).get('data',{})
        if manifest.get('chatModel',{}).get('enabled') is not True:
            raise SystemExit('Real model evaluation blocked: chat model disabled. Use smoke_ai_tasks.py for offline testing.')
        auth = call('/auth/login', body={'username':os.getenv('AI_TASK_USER','ai_buyer'),'password':os.getenv('EVAL_PASSWORD','123456')})
        if auth.get('code') != 'Success': raise RuntimeError('Task account login failed')
        token = auth['data']['token']
        out.mkdir(parents=True)
        for repeat in range(args.repeats):
            for case in dataset['cases']:
                start=time.perf_counter()
                if case['task']=='review':
                    response=call(f"/assistant/tasks/orders/{case['orderId']}/review",token,{'question':case['question'],'focus':case['focus']})
                else:
                    response=call('/assistant/tasks/request-draft',token,{'text':case['text']})
                data=response.get('data') or {}
                mechanical={'success':response.get('code')=='Success','generated':data.get('mode')=='generated'}
                if 'expectedCount' in case: mechanical['item_count']=len(data.get('items',[]))==case['expectedCount']
                if 'expectedQuantities' in case:
                    try:
                        mechanical['quantities']=sorted(Decimal(str(i.get('quantity'))) for i in data.get('items',[]))==sorted(Decimal(str(q)) for q in case['expectedQuantities'])
                    except InvalidOperation:
                        mechanical['quantities']=False
                results.append({'case':case['id'],'repeat':repeat+1,'mechanical_checks':mechanical,
                                'human_review':'pending','rubric':case['rubric'],'http_elapsed_ms':round((time.perf_counter()-start)*1000,2),'response':response})
                (out/'evidence.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        meta={**source_provenance(),'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
              'dataset_sha256':hashlib.sha256(raw).hexdigest(),'manifest':manifest,'base':BASE,
              'mode':'real-model','prompt_versions':['order-review-v1','request-draft-v1'],
              'business_write_guard':'not_run_in_this_runner; covered separately by AiTaskIT',
              'manual_semantic_review':'required; mechanical checks are not answer accuracy',
              'status':'CAPTURED_AWAITING_HUMAN_REVIEW'}
        (out/'metadata.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(f'Captured {len(results)} real-service attempts; semantic review pending. {out}')
        return 0 if all(all(r['mechanical_checks'].values()) for r in results) else 1
    finally:
        for value in (token,admin_token):
            if value:
                try: call('/auth/logout',value,{})
                except OSError: pass


if __name__=='__main__':
    raise SystemExit(main())
