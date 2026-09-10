#!/usr/bin/env python3
"""Fail-closed release gate with a readable, run-specific Actions summary.

No API calls, status rewrites or implicit retries. RESULTS is the actual
``toJSON(needs)`` context from this run, not a previous successful run.
"""
from __future__ import annotations

import html
import json
import os
from pathlib import Path
import re
from typing import Mapping
from urllib.parse import quote, urlencode

REQUIRED_JOBS = {
    'workflow': 'Workflow and release gate tests',
    'build': 'Build, simulation and dedicated server',
    'resources': 'Resources, audio and geometry',
    'client': 'Native client matrix (expand for individual modes)',
    'multiplayer': 'Two native clients and dedicated server',
    'companion': 'ElectricalAge compatibility / companion',
    'visibility': 'Glass and charger visibility matrix',
}
RESULTS = {'success', 'failure', 'cancelled', 'skipped'}


def unique_object(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key: ' + key)
        result[key] = value
    return result


def evaluate(raw: str) -> dict[str, str]:
    """Return every job's actual result; missing/malformed context is fatal."""
    data = json.loads(raw, object_pairs_hook=unique_object)
    if not isinstance(data, dict):
        raise ValueError('RESULTS must be the needs object')
    missing = sorted(set(REQUIRED_JOBS) - data.keys())
    if missing:
        raise ValueError('Missing required job groups: ' + ', '.join(missing))
    results = {}
    for name, job in data.items():
        if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_-]*', name):
            raise ValueError('Invalid job identifier')
        if (not isinstance(job, dict) or not isinstance(job.get('result'), str)
                or job['result'] not in RESULTS):
            raise ValueError('Missing or invalid result for ' + name)
        results[name] = job['result']
    return results


def cell(value: str) -> str:
    # Refs can contain Markdown syntax; do not let them inject summary tables.
    return (html.escape(value, quote=True).replace('|', '&#124;')
            .replace('`', '&#96;').replace('\r', ' ').replace('\n', ' '))


def context(env: Mapping[str, str]) -> dict[str, str]:
    repo = env.get('GITHUB_REPOSITORY', '')
    sha = env.get('GITHUB_SHA', '')
    run = env.get('GITHUB_RUN_ID', '')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
        raise ValueError('Missing or invalid GITHUB_REPOSITORY')
    if not re.fullmatch(r'(?:[0-9a-f]{40}|[0-9a-f]{64})', sha):
        raise ValueError('Missing or invalid GITHUB_SHA')
    if not run.isdigit():
        raise ValueError('Missing or invalid GITHUB_RUN_ID')
    origin = 'https://github.com/' + repo
    branch = env.get('RELEASE_BRANCH', 'main') or 'main'
    return {
        'origin': origin,
        'run': origin + '/actions/runs/' + run,
        'sha': sha,
        'commit': origin + '/commit/' + sha,
        'ref': env.get('GITHUB_REF', 'unknown'),
        'event': env.get('GITHUB_EVENT_NAME', 'unknown'),
        'number': env.get('GITHUB_RUN_NUMBER', run),
        'attempt': env.get('GITHUB_RUN_ATTEMPT', '1'),
        'branch': branch,
        'current': origin + '/actions/workflows/ci.yml?' + urlencode({'query': 'branch:' + branch + ' event:push'}),
        'branch_url': origin + '/tree/' + quote(branch, safe=''),
    }


def render(results: dict[str, str], info: dict[str, str]) -> str:
    blocked = {name: result for name, result in results.items() if result != 'success'}
    lines = [
        '## Required checks: ' + ('BLOCKED' if blocked else 'PASS'), '',
        f"Run [#{cell(info['number'])}, attempt {cell(info['attempt'])}]({info['run']})"
        f" · event <code>{cell(info['event'])}</code>",
        f"Tested checkout: [`{info['sha']}`]({info['commit']})",
        f"Ref: <code>{cell(info['ref'])}</code>", '',
        '**This is an aggregate gate, not another Minecraft test.** It reports the upstream job groups below.', '',
        '| Required job group | Result |', '|---|---|',
    ]
    for name, result in results.items():
        lines.append(f'| {cell(REQUIRED_JOBS.get(name, name))} (<code>{cell(name)}</code>) | **{result.upper()}** |')
    if blocked:
        lines += ['', '**Publication is blocked.** Open this run\'s job graph and inspect the failed or cancelled group; expand matrix jobs to find the actual failing step.',
                  'A red dependency plus a red Required checks gate is not evidence of two independent mod defects. Skipped required tests also block publication.']
    else:
        lines += ['', 'All required groups passed **for this checkout only**. This does not say a JAR has been published.',
                  'Pull requests do not publish releases. For a release-branch push, also verify the **Publish tested alpha JAR** job and the release\'s `build-info.json`.']
    lines += ['', '### Compare with the current branch before changing anything', '',
              f"[Current {cell(info['branch'])} push runs]({info['current']}) · "
              f"[Branch source]({info['branch_url']}) · [Latest published release]({info['origin']}/releases/latest)", '',
              'Those links are navigation, not a claim that the current branch or release is green. Compare its commit with the tested checkout above.',
              'Historical runs keep their own results. Re-running an old run uses its original commit/ref, not the newest code. A newer fix cannot retroactively turn an old run green.',
              f"[CI status and release troubleshooting]({info['origin']}/blob/{info['sha']}/docs/CI_STATUS.md)", '']
    return '\n'.join(lines)


def annotation(message: str) -> str:
    return message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


def main(env: Mapping[str, str] | None = None) -> int:
    env = os.environ if env is None else env
    try:
        results = evaluate(env.get('RESULTS', ''))
        text = render(results, context(env))
        print(text)
        if env.get('GITHUB_STEP_SUMMARY'):
            with Path(env['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as stream:
                stream.write(text + '\n')
        bad = {name: result for name, result in results.items() if result != 'success'}
        if bad:
            detail = ', '.join(name + '=' + result for name, result in bad.items())
            print('::error title=Release gate blocked::' + annotation('Required job groups: ' + detail + '. Inspect their steps in this run; this gate is the dependency summary.'))
            return 1
        print('Every required test job passed.')
        return 0
    except (ValueError, TypeError, OSError) as error:
        print('::error title=Release gate context invalid::' + annotation(str(error)))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
