"""Failure-injection tests for the shared, integrity-preserving APT bootstrap."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import install_packages as apt


class AptBootstrapTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.report = self.root / 'report'
        self.key = self.root / 'ubuntu-archive-keyring.gpg'
        self.key.write_bytes(b'test-key-placeholder-never-used-by-real-apt')
        for target, value in (
            ('KEYRING', self.key),
            ('platform.freedesktop_os_release', lambda: {'ID': 'ubuntu', 'VERSION_CODENAME': 'noble'}),
        ):
            p = patch.object(apt, target, value) if '.' not in target else patch('install_packages.' + target, value)
            p.start(); self.addCleanup(p.stop)
        self.commands = []
        self.codes = []
        self.calls = []
        p = patch.object(apt.subprocess, 'run', side_effect=self.fake_run)
        p.start(); self.addCleanup(p.stop)
        p = patch.object(apt.subprocess, 'check_output', side_effect=self.fake_output)
        p.start(); self.addCleanup(p.stop)
        p = patch.object(apt.time, 'sleep')
        self.sleep = p.start(); self.addCleanup(p.stop)

    def fake_output(self, command, **kwargs):
        if command[0] == 'dpkg':
            return 'amd64\n'
        return ''.join(f'{name}\t1.0\tii \n' for name in command[3:])

    def fake_run(self, command, **kwargs):
        self.commands.append(command)
        self.calls.append(kwargs)
        values = dict(arg.split('=', 1) for arg in command if '=' in arg)
        source = Path(values['Dir::Etc::sourcelist'])
        self.assertTrue(source.is_file())
        self.assertIn('Signed-By:', source.read_text())
        self.assertEqual(list(Path(values['Dir::Etc::sourceparts']).iterdir()), [])
        self.assertNotIn('google', source.read_text())
        self.assertEqual(values['APT::Update::Error-Mode'], 'any')
        self.assertEqual(kwargs['env']['DEBIAN_FRONTEND'], 'noninteractive')
        return subprocess.CompletedProcess(command, self.codes.pop(0) if self.codes else 0,
                                           'synthetic apt result\n')

    def test_amd64_sources_keep_security_updates_and_signatures(self):
        text = apt.ubuntu_sources('noble', 'amd64')
        self.assertIn('https://archive.ubuntu.com/ubuntu', text)
        self.assertIn('https://security.ubuntu.com/ubuntu', text)
        self.assertIn('noble-updates noble-backports', text)
        self.assertIn('Suites: noble-security', text)
        self.assertEqual(text.count('Signed-By:'), 2)
        self.assertNotIn('trusted:', text.lower())

    def test_arm64_uses_signed_ubuntu_ports(self):
        text = apt.ubuntu_sources('noble', 'arm64')
        self.assertEqual(text.count('https://ports.ubuntu.com/ubuntu-ports'), 2)
        self.assertIn('Architectures: arm64', text)
        self.assertNotIn('archive.ubuntu.com', text)

    def test_wrong_os_is_rejected_before_apt(self):
        with patch.object(apt.platform, 'freedesktop_os_release', return_value={'ID': 'debian'}):
            with self.assertRaises(ValueError): apt.install('native', self.report)
        self.assertEqual(self.commands, [])

    def test_unknown_release_and_architecture_fail_closed(self):
        for release, architecture in [('jammy', 'amd64'), ('noble\nTrusted: yes', 'amd64'), ('noble', 'riscv64')]:
            with self.subTest(release=release, architecture=architecture):
                with self.assertRaises(ValueError): apt.ubuntu_sources(release, architecture)

    def test_missing_keyring_never_runs_apt(self):
        self.key.unlink()
        with self.assertRaises(ValueError): apt.install('native', self.report)
        self.assertEqual(self.commands, [])

    def test_empty_keyring_never_runs_apt(self):
        self.key.write_bytes(b'')
        with self.assertRaises(ValueError): apt.install('native', self.report)
        self.assertEqual(self.commands, [])

    def test_update_and_install_use_identical_private_scopes(self):
        apt.install('native', self.report)
        update, install = self.commands
        self.assertEqual(update[1:update.index('update')], install[1:install.index('install')])
        for key in ('Dir::Etc::sourcelist', 'Dir::Etc::sourceparts', 'Dir::State::lists',
                    'Dir::Cache::archives', 'Dir::Cache::pkgcache', 'Dir::Cache::srcpkgcache'):
            option = next(arg for arg in update if arg.startswith(key + '='))
            path = Path(option.split('=', 1)[1])
            self.assertTrue(str(path).startswith('/tmp/apa-ci-apt-'))
            self.assertFalse(path.exists(), 'Private cache is cleaned after installation')
        self.assertTrue(json.loads((self.report / 'result.json').read_text())['passed'])
        self.assertTrue((self.report / 'packages.tsv').is_file())

    def test_assets_share_same_integrity_protected_installer(self):
        apt.install('assets', self.report)
        self.assertEqual(self.commands[-1][-2:], ['ffmpeg', 'blender'])
        self.assertIn('--no-install-recommends', self.commands[-1])
        for command in self.commands:
            text = ' '.join(command).lower()
            for unsafe in ('--allow-unauthenticated', 'allowinsecurerepositories=true', 'verify-peer=false', 'ignore-missing'):
                self.assertNotIn(unsafe, text)

    def test_transient_update_retries_then_installs(self):
        self.codes = [100, 100, 0, 0]
        apt.install('native', self.report)
        self.assertEqual(len(self.commands), 4)
        self.assertEqual(self.sleep.call_args_list[0].args, (5,))
        self.assertEqual(self.sleep.call_args_list[1].args, (10,))
        self.assertEqual(json.loads((self.report / 'result.json').read_text())['update_attempts'], 3)

    def test_persistent_update_error_blocks_install_and_records_failure(self):
        self.codes = [100, 100, 100]
        with self.assertRaises(subprocess.CalledProcessError) as caught:
            apt.install('native', self.report)
        self.assertEqual(caught.exception.returncode, 100)
        self.assertEqual(len(self.commands), 3)
        self.assertTrue(all('install' not in command for command in self.commands))
        self.assertFalse(json.loads((self.report / 'result.json').read_text())['passed'])
        self.assertTrue((self.report / 'update-3.log').is_file())

    def test_transient_install_error_is_retried(self):
        self.codes = [0, 100, 0]
        apt.install('native', self.report)
        self.assertEqual(json.loads((self.report / 'result.json').read_text())['install_attempts'], 2)

    def test_persistent_install_error_cannot_turn_green(self):
        self.codes = [0, 100, 100, 100]
        with self.assertRaises(subprocess.CalledProcessError): apt.install('native', self.report)
        self.assertFalse(json.loads((self.report / 'result.json').read_text())['passed'])
        self.assertEqual(len(self.commands), 4)

    def test_timeout_is_not_retried_over_a_possibly_running_dpkg(self):
        with patch.object(apt.subprocess, 'run', side_effect=subprocess.TimeoutExpired(['apt-get'], 600)) as run:
            with self.assertRaises(subprocess.TimeoutExpired): apt.install('native', self.report)
        self.assertEqual(run.call_count, 1)
        self.assertFalse(json.loads((self.report / 'result.json').read_text())['passed'])

    def test_killed_process_is_not_retried(self):
        self.codes = [-15]
        with self.assertRaises(subprocess.CalledProcessError): apt.install('native', self.report)
        self.assertEqual(len(self.commands), 1)

    def test_success_requires_installed_packages_not_just_apt_exit_zero(self):
        with patch.object(apt.subprocess, 'check_output', side_effect=['amd64\n', 'xvfb\t1.0\trc \n']):
            with self.assertRaises(RuntimeError): apt.install('native', self.report)
        self.assertFalse(json.loads((self.report / 'result.json').read_text())['passed'])

    def test_prepare_never_deletes_neighbor_sources(self):
        untouched = self.root / 'google-chrome.list'
        untouched.write_text('deb https://dl.google.com/linux/chrome/deb stable main\n')
        private = self.root / 'private'; private.mkdir()
        apt.prepare(private, apt.ubuntu_sources('noble', 'amd64'))
        self.assertIn('dl.google.com', untouched.read_text())
        self.assertEqual(private.stat().st_mode & 0o777, 0o755)

    def test_unknown_profile_does_not_execute_options_as_packages(self):
        with self.assertRaises(KeyError): apt.install('--allow-unauthenticated', self.report)
        self.assertEqual(self.commands, [])


class WorkflowBootstrapTests(unittest.TestCase):
    ROOT = Path(__file__).resolve().parents[3]

    def test_workflows_do_not_bypass_the_shared_apt_scope(self):
        for path in (self.ROOT / '.github').rglob('*.yml'):
            with self.subTest(path=str(path)):
                self.assertNotIn('apt-get ', path.read_text())

    def test_companion_client_requires_successful_native_setup(self):
        text = (self.ROOT / '.github/workflows/eln-compat.yml').read_text()
        native = text.split('- name: Native Minecraft combined-mod client verification', 1)[1]
        condition = native.split('working-directory:', 1)[0]
        self.assertIn("steps.auto_build.outcome == 'success'", condition)
        self.assertIn("steps.native_setup.outcome == 'success'", condition)
        self.assertNotIn('continue-on-error', text)
        self.assertIn('auto/build/ci/', text)

    def test_composite_resolves_script_from_its_own_checkout(self):
        action = (self.ROOT / '.github/actions/setup-native-runtime/action.yml').read_text()
        self.assertIn('$GITHUB_ACTION_PATH/../../../tools/ci/install_packages.py', action)
        self.assertIn('mkdir -p "$REPORT_DIRECTORY/apt"', action)
        self.assertIn('glxinfo -B', action)
        self.assertNotIn('|| true', action)
        for name in ('full-stack-audit', 'historical-native-audit', 'independent-boundaries'):
            text = (self.ROOT / f'.github/workflows/{name}.yml').read_text()
            self.assertIn('path: ci-bootstrap', text)
            self.assertIn('uses: ./ci-bootstrap/.github/actions/setup-native-runtime', text)
            self.assertIn("steps.native_setup.outcome == 'success'", text)

    def test_negative_control_is_required_and_release_gate_stays_strict(self):
        text = (self.ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn('python3 tools/ci/apt_smoke.py', text)
        self.assertIn('workflow, build, resources, client, multiplayer, companion, visibility', text)
        self.assertIn('run: python3 tools/ci/required_checks.py', text)
        # The gate moved out of YAML: exercise it instead of requiring its old
        # source expression to remain inside the workflow file.
        import required_checks as gate
        needs = {name: {'result': 'success'} for name in gate.REQUIRED_JOBS}
        env = {'GITHUB_REPOSITORY': 'CesarPetrescu/AutoPropulsion-Age',
               'GITHUB_SHA': 'a' * 40, 'GITHUB_RUN_ID': '123456'}
        with patch('builtins.print'):
            self.assertEqual(gate.main(env | {'RESULTS': json.dumps(needs)}), 0)
            for result in ('failure', 'cancelled', 'skipped'):
                needs['workflow']['result'] = result
                self.assertEqual(gate.main(env | {'RESULTS': json.dumps(needs)}), 1)


if __name__ == '__main__':
    unittest.main()
