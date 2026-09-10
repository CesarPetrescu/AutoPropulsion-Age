"""The new summary must explain failures without making the release gate weaker."""
import contextlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import required_checks as gate

ROOT = Path(__file__).resolve().parents[3]


def passing():
    return {name: {'result': 'success', 'outputs': {}} for name in gate.REQUIRED_JOBS}


def environment(data=None):
    return {
        'RESULTS': json.dumps(passing() if data is None else data),
        'GITHUB_REPOSITORY': 'CesarPetrescu/AutoPropulsion-Age',
        'GITHUB_SHA': 'a' * 40,
        'GITHUB_REF': 'refs/pull/12/merge',
        'GITHUB_EVENT_NAME': 'pull_request',
        'GITHUB_RUN_ID': '123456',
        'GITHUB_RUN_NUMBER': '118',
        'GITHUB_RUN_ATTEMPT': '2',
    }


class RequiredChecks(unittest.TestCase):
    def run_gate(self, env):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            result = gate.main(env)
        return result, output.getvalue()

    def test_all_required_success_returns_zero(self):
        code, text = self.run_gate(environment())
        self.assertEqual(code, 0)
        self.assertIn('Required checks: PASS', text)
        self.assertIn('Every required test job passed.', text)

    def test_each_non_success_result_blocks_every_required_group(self):
        for name in gate.REQUIRED_JOBS:
            for status in ('failure', 'cancelled', 'skipped'):
                with self.subTest(group=name, result=status):
                    data = passing(); data[name]['result'] = status
                    code, text = self.run_gate(environment(data))
                    self.assertEqual(code, 1)
                    self.assertIn('Required checks: BLOCKED', text)
                    self.assertIn(name + '=' + status, text)
                    self.assertIn('Publication is blocked', text)
                    self.assertNotIn('Every required test job passed.', text)

    def test_failure_summary_names_eln_and_explains_duplicate_red_gate(self):
        data = passing(); data['companion']['result'] = 'failure'
        _, text = self.run_gate(environment(data))
        self.assertIn('ElectricalAge compatibility / companion', text)
        self.assertIn('not another Minecraft test', text)
        self.assertIn('not evidence of two independent mod defects', text)

    def test_missing_required_group_blocks_release(self):
        for name in gate.REQUIRED_JOBS:
            data = passing(); del data[name]
            with self.subTest(missing=name):
                code, text = self.run_gate(environment(data))
                self.assertEqual(code, 1)
                self.assertIn('Missing required job groups: ' + name, text)

    def test_empty_or_wrong_json_shape_cannot_pass(self):
        for data in ({}, [], None, True, 'success', 1):
            with self.subTest(data=data):
                self.assertEqual(self.run_gate(environment(data) | {'RESULTS': json.dumps(data)})[0], 1)

    def test_malformed_or_missing_json_cannot_pass(self):
        for raw in ('', '{', '{"result":"success"'):
            self.assertEqual(self.run_gate(environment() | {'RESULTS': raw})[0], 1)

    def test_bad_result_types_or_unknown_result_cannot_pass(self):
        for status in (None, True, [], {}, 0, '', 'pending', 'neutral', 'Success'):
            data = passing(); data['client']['result'] = status
            with self.subTest(status=status):
                self.assertEqual(self.run_gate(environment(data))[0], 1)

    def test_malformed_job_objects_cannot_pass(self):
        for job in (None, [], 'success', {}, {'outputs': {}}):
            data = passing(); data['client'] = job
            self.assertEqual(self.run_gate(environment(data))[0], 1)

    def test_duplicate_result_key_cannot_override_failure(self):
        raw = json.dumps(passing()).replace('"result": "success"', '"result": "failure", "result": "success"', 1)
        code, text = self.run_gate(environment() | {'RESULTS': raw})
        self.assertEqual(code, 1)
        self.assertIn('Duplicate JSON key', text)

    def test_additional_job_is_not_silently_ignored(self):
        for status, expected in (('success', 0), ('failure', 1), ('skipped', 1)):
            data = passing(); data['extra_test'] = {'result': status}
            code, text = self.run_gate(environment(data))
            self.assertEqual(code, expected)
            self.assertIn('extra_test', text)

    def test_summary_records_run_attempt_event_and_tested_merge_sha(self):
        _, text = self.run_gate(environment())
        for value in ('#118, attempt 2', '/actions/runs/123456', 'pull_request', 'refs/pull/12/merge', 'a' * 40):
            self.assertIn(value, text)
        self.assertIn('Tested checkout:', text)

    def test_pass_does_not_claim_release_was_published(self):
        _, text = self.run_gate(environment())
        self.assertIn('does not say a JAR has been published', text)
        self.assertIn('Pull requests do not publish releases', text)
        self.assertIn('Publish tested alpha JAR', text)
        self.assertIn('build-info.json', text)

    def test_summary_links_current_branch_and_separates_historical_evidence(self):
        info = gate.context(environment())
        query = parse_qs(urlsplit(info['current']).query)
        self.assertEqual(query, {'query': ['branch:main event:push']})
        text = gate.render(gate.evaluate(environment()['RESULTS']), info)
        self.assertIn('original commit/ref', text)
        self.assertIn('not a claim that the current branch or release is green', text)
        self.assertIn('/releases/latest', text)
        self.assertIn('/blob/' + 'a' * 40 + '/docs/CI_STATUS.md', text)

    def test_release_branch_override_is_encoded_as_data(self):
        info = gate.context(environment() | {'RELEASE_BRANCH': 'release/next'})
        self.assertEqual(parse_qs(urlsplit(info['current']).query)['query'], ['branch:release/next event:push'])
        self.assertTrue(info['branch_url'].endswith('/tree/release%2Fnext'))

    def test_summary_appends_without_erasing_previous_step_output(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'summary.md'; path.write_text('Earlier summary\n')
            code, text = self.run_gate(environment() | {'GITHUB_STEP_SUMMARY': str(path)})
            self.assertEqual(code, 0)
            self.assertTrue(path.read_text().startswith('Earlier summary\n## Required checks: PASS'))
            self.assertIn('a' * 40, path.read_text())

    def test_failed_summary_is_written_even_when_gate_exits_nonzero(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'summary.md'
            data = passing(); data['companion']['result'] = 'failure'
            code, _ = self.run_gate(environment(data) | {'GITHUB_STEP_SUMMARY': str(path)})
            self.assertEqual(code, 1)
            self.assertIn('Publication is blocked', path.read_text())

    def test_unwritable_summary_does_not_fake_success(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertEqual(self.run_gate(environment() | {'GITHUB_STEP_SUMMARY': temp})[0], 1)

    def test_missing_or_malformed_run_context_is_not_called_verified(self):
        for key, values in {
            'GITHUB_SHA': ('', 'a' * 39, 'a' * 41, 'x' * 40),
            'GITHUB_REPOSITORY': ('', 'not-a-repository', 'owner/repo/extra'),
            'GITHUB_RUN_ID': ('', '../123', 'pending'),
        }.items():
            for value in values:
                with self.subTest(key=key, value=value):
                    self.assertEqual(self.run_gate(environment() | {key: value})[0], 1)

    def test_ref_markup_cannot_insert_raw_html_or_break_table(self):
        _, text = self.run_gate(environment() | {'GITHUB_REF': 'refs/heads/a|`<script>\nnext'})
        self.assertNotIn('<script>', text)
        self.assertIn('a&#124;&#96;&lt;script&gt; next', text)

    def test_outputs_are_never_dumped_into_summary(self):
        data = passing(); data['build']['outputs']['private'] = 'DONT-PRINT-OUTPUTS'
        _, text = self.run_gate(environment(data))
        self.assertNotIn('DONT-PRINT-OUTPUTS', text)

    def test_annotation_escapes_workflow_command_characters(self):
        self.assertEqual(gate.annotation('bad%\r\n::notice::not-a-command'), 'bad%25%0D%0A::notice::not-a-command')

    def test_cli_propagates_failure_exit_code(self):
        import os
        data = passing(); data['companion']['result'] = 'failure'
        env = {key: value for key, value in os.environ.items() if not key.startswith('GITHUB_')}
        env.update(environment(data))
        process = subprocess.run([sys.executable, str(ROOT / 'tools/ci/required_checks.py')], env=env, text=True, capture_output=True, timeout=10)
        self.assertEqual(process.returncode, 1)
        self.assertIn('companion=failure', process.stdout)

    def test_workflow_keeps_original_required_groups_and_fail_closed_execution(self):
        workflow = (ROOT / '.github/workflows/ci.yml').read_text()
        section = workflow.split('\n  required:\n', 1)[1].split('\n  release:\n', 1)[0]
        needs = re.search(r'needs: \[([^\]]+)\]', section).group(1)
        self.assertEqual(set(map(str.strip, needs.split(','))), set(gate.REQUIRED_JOBS))
        self.assertIn('if: always()', section)
        self.assertIn('RESULTS: ${{ toJSON(needs) }}', section)
        self.assertIn('run: python3 tools/ci/required_checks.py', section)
        self.assertIn('persist-credentials: false', section)
        self.assertNotIn('continue-on-error', section)
        self.assertNotIn('|| true', section)
        release = workflow.split('\n  release:\n', 1)[1]
        self.assertIn('needs: [required, build]', release)
        self.assertIn("github.event_name == 'push'", release)
        self.assertIn("info['commit'] == os.environ['GITHUB_SHA']", release)


if __name__ == '__main__':
    unittest.main()
