# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise release validation without changing this repository's refs or secrets."""

import base64
from contextlib import chdir, redirect_stderr
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import release


BUILD = 'android {\r\n    versionCode = 7\r\n    versionName = "1.0.0"\r\n}\r\n'


class VersionTests(unittest.TestCase):
    def test_changes_only_version_fields_and_preserves_line_endings(self):
        self.assertEqual(
            release.updated_version(BUILD, "1.2.3"),
            BUILD.replace("versionCode = 7", "versionCode = 8").replace('"1.0.0"', '"1.2.3"'),
        )

    def test_numeric_order_and_first_release(self):
        self.assertIn('"1.10.0"', release.updated_version(BUILD.replace("1.0.0", "1.9.0"), "1.10.0"))
        self.assertIn("versionCode = 8", release.updated_version(BUILD, "1.0.0"))
        with self.assertRaisesRegex(ValueError, "lower"):
            release.updated_version(BUILD, "0.9.9")

    def test_rejects_invalid_versions_and_tags(self):
        for version in ("v1.0.0", "1.0", "01.0.0", "1.0.0-rc1", "1.0.0+build", "1.0.0\n", "1.0.$(id)"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                release.parse_version(version)
        for tag in ("main", "1.0.0", "v1.0.0-beta"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.tag_version(tag)

    def test_rejects_ambiguous_fields_and_code_overflow(self):
        for text in (BUILD + BUILD, BUILD.replace("versionName", "other"), BUILD.replace("= 7", "= 2100000000")):
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.updated_version(text, "1.1.0")


class SigningTests(unittest.TestCase):
    def test_missing_configuration_and_bad_base64_do_not_write_a_key(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "test.jks"
            values = {
                "ANDROID_KEYSTORE_PATH": str(target), "ANDROID_KEYSTORE_PASSWORD": "store-secret",
                "ANDROID_KEY_ALIAS": "test", "ANDROID_KEY_PASSWORD": "key-secret",
            }
            with patch.dict(os.environ, values, clear=True):
                with self.assertRaisesRegex(ValueError, "ANDROID_KEYSTORE_BASE64"):
                    release.restore_keystore()
                os.environ["ANDROID_KEYSTORE_BASE64"] = "not base64!"
                with self.assertRaisesRegex(ValueError, "Base64"):
                    release.restore_keystore()
            self.assertFalse(target.exists())

    def test_restores_exact_bytes_without_overwriting_a_key(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "test.jks"
            values = {
                "ANDROID_KEYSTORE_PATH": str(target), "ANDROID_KEYSTORE_PASSWORD": "store-secret",
                "ANDROID_KEY_ALIAS": "test", "ANDROID_KEY_PASSWORD": "key-secret",
                "ANDROID_KEYSTORE_BASE64": base64.b64encode(b"test-keystore").decode() + "\n",
            }
            with patch.dict(os.environ, values, clear=True):
                release.restore_keystore()
                self.assertEqual(target.read_bytes(), b"test-keystore")
                if os.name != "nt":
                    self.assertEqual(target.stat().st_mode & 0o777, 0o600)
                with self.assertRaises(FileExistsError):
                    release.restore_keystore()


class RepositoryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.context = chdir(self.directory.name)
        self.context.__enter__()
        self.addCleanup(self.context.__exit__, None, None, None)
        self.git("init", "--quiet")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "release-test@example.invalid")
        self.git("config", "core.autocrlf", "false")
        release.BUILD_FILE.parent.mkdir(parents=True)
        release.BUILD_FILE.write_bytes(BUILD.encode())
        Path("LICENSE").write_text("Test license", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "Fixture")
        self.git("tag", "-a", "v1.0.0", "-m", "Fixture release")
        apk_dir = Path("app/build/outputs/apk/release")
        apk_dir.mkdir(parents=True)
        (apk_dir / "app-release.apk").write_bytes(b"fixture-apk")
        self.metadata = apk_dir / "output-metadata.json"
        self.metadata.write_text(json.dumps({
            "variantName": "release", "elements": [{
                "versionName": "1.0.0", "versionCode": 7, "outputFile": "app-release.apk",
            }],
        }), encoding="utf-8")

    def git(self, *args):
        subprocess.run(["git", *args], check=True, capture_output=True)

    def test_existing_tag_is_rejected_without_modifying_build(self):
        with self.assertRaisesRegex(ValueError, "already exists"):
            release.prepare_version("1.0.0")
        self.assertEqual(release.BUILD_FILE.read_bytes(), BUILD.encode())

    def test_prepares_new_version_without_creating_tag(self):
        release.prepare_version("1.1.0")
        self.assertEqual(release.read_version_fields(release.BUILD_FILE.read_text()), ("1.1.0", 8))
        result = subprocess.run(["git", "show-ref", "--verify", "--quiet", "refs/tags/v1.1.0"], check=False)
        self.assertEqual(result.returncode, 1)

    def test_package_has_matching_checksums_and_only_committed_source(self):
        Path("private.jks").write_bytes(b"must not be archived")
        release.package_release("v1.0.0")
        dist = Path("dist")
        self.assertEqual(len(list(dist.iterdir())), 3)
        for line in (dist / "SHA256SUMS.txt").read_text().splitlines():
            digest, name = line.split("  ")
            self.assertEqual(hashlib.sha256((dist / name).read_bytes()).hexdigest(), digest)
        with zipfile.ZipFile(dist / "EasyGamepad-1.0.0-source.zip") as archive:
            names = archive.namelist()
            self.assertIn("EasyGamepad-1.0.0/LICENSE", names)
            self.assertFalse(any(name.endswith(".jks") or "/build/" in name for name in names))
        with self.assertRaisesRegex(ValueError, "empty"):
            release.package_release("v1.0.0")

    def test_package_rejects_version_and_unsigned_metadata(self):
        data = json.loads(self.metadata.read_text())
        for field, value in (("versionName", "2.0.0"), ("versionCode", 8), ("outputFile", "app-release-unsigned.apk")):
            with self.subTest(field=field):
                changed = json.loads(json.dumps(data))
                changed["elements"][0][field] = value
                self.metadata.write_text(json.dumps(changed), encoding="utf-8")
                with self.assertRaises(ValueError):
                    release.package_release("v1.0.0")
        self.assertFalse(Path("dist").exists())

    def test_package_rejects_a_different_checked_out_commit(self):
        self.git("commit", "--allow-empty", "--quiet", "-m", "Another revision")
        with self.assertRaisesRegex(ValueError, "commit"):
            release.package_release("v1.0.0")

    def test_resolve_and_validate_tag_require_matching_checkout(self):
        release.resolve_release_tag("v1.0.0")
        release.validate_release_tag("v1.0.0")
        self.git("commit", "--allow-empty", "--quiet", "-m", "Ahead of tag")
        with self.assertRaisesRegex(ValueError, "Checked-out commit"):
            release.validate_release_tag("v1.0.0")
        with self.assertRaisesRegex(ValueError, "does not exist"):
            release.resolve_release_tag("v9.9.9")


class ReleaseNotesTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.context = chdir(self.directory.name)
        self.context.__enter__()
        self.addCleanup(self.context.__exit__, None, None, None)
        release.BUILD_FILE.parent.mkdir(parents=True)
        release.BUILD_FILE.write_text(
            'android {\n    minSdk = 26\n    targetSdk = 37\n    versionCode = 1\n    versionName = "1.0.0"\n}\n',
            encoding="utf-8",
        )
        (Path("cliff.toml")).write_text("[git]\n", encoding="utf-8")

    def test_previous_stable_tag_skips_prerelease_tag_names(self):
        subprocess.run(["git", "init", "--quiet"], check=True)
        subprocess.run(["git", "config", "user.email", "t@example.invalid"], check=True)
        subprocess.run(["git", "config", "user.name", "t"], check=True)
        Path("f").write_text("a", encoding="utf-8")
        subprocess.run(["git", "add", "f"], check=True)
        subprocess.run(["git", "commit", "--quiet", "-m", "chore: fixture"], check=True)
        subprocess.run(["git", "tag", "-a", "v0.9.0", "-m", "previous"], check=True)
        subprocess.run(["git", "commit", "--allow-empty", "--quiet", "-m", "chore: second"], check=True)
        subprocess.run(["git", "tag", "-a", "v1.0.0", "-m", "stable"], check=True)
        self.assertEqual(release.previous_stable_tag("v1.0.0"), "v0.9.0")

    def test_prepare_notes_combines_badges_and_git_cliff_body(self):
        body = "## [1.0.0](https://example/releases/tag/v1.0.0) - 2026-01-01\n\n### Features\n\n- item"
        buffer = io.StringIO()
        with patch("release.git_cliff_release_body", return_value=body) as cliff, \
                patch("release.previous_stable_tag", return_value="v0.9.0"), \
                patch.dict(os.environ, {"GH_REPO": "owner/repo"}), \
                patch("sys.stdout", buffer):
            release.prepare_release_notes("v1.0.0")
        output = buffer.getvalue()
        self.assertIn("github/downloads/owner/repo/v1.0.0/total", output)
        self.assertIn("message=minSdk%2026", output)
        self.assertIn("targetSdk", output)
        self.assertIn("### Features", output)
        cliff.assert_called_once_with("v1.0.0", "v0.9.0..v1.0.0")


class DispatchTests(unittest.TestCase):
    def test_failure_keeps_tag_and_provides_recovery_command(self):
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary.md"
            error_output = io.StringIO()
            with patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(summary)}), \
                    patch("release.subprocess.run", return_value=subprocess.CompletedProcess([], 1)) as run, \
                    redirect_stderr(error_output):
                with self.assertRaisesRegex(ValueError, "dispatch failed"):
                    release.dispatch_release("v1.0.0")
            run.assert_called_once_with(
                ["gh", "workflow", "run", "release.yml", "--ref", "main",
                 "-f", "tag=v1.0.0", "-f", "run_instrumentation=false"],
                check=False,
            )
            self.assertIn(
                "gh workflow run release.yml --ref main -f tag=v1.0.0 -f run_instrumentation=false",
                summary.read_text(),
            )
            self.assertIn("Keep the commit and tag", error_output.getvalue())

    def test_dispatch_passes_repo_when_configured(self):
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary.md"
            with patch.dict(
                    os.environ,
                    {"GITHUB_STEP_SUMMARY": str(summary), "GH_REPO": "owner/name"},
                ), patch("release.subprocess.run", return_value=subprocess.CompletedProcess([], 1)) as run, \
                    redirect_stderr(io.StringIO()):
                with self.assertRaisesRegex(ValueError, "dispatch failed"):
                    release.dispatch_release("v1.0.0")
            run.assert_called_once_with(
                ["gh", "workflow", "run", "release.yml", "--repo", "owner/name", "--ref", "main",
                 "-f", "tag=v1.0.0", "-f", "run_instrumentation=false"],
                check=False,
            )

    def test_success_dispatches_existing_tag_without_instrumentation(self):
        output = io.StringIO()
        with patch.dict(os.environ, {}, clear=True), \
                patch("release.subprocess.run", return_value=subprocess.CompletedProcess([], 0)) as run, \
                patch("sys.stdout", output):
            release.dispatch_release("v1.1.0")
        run.assert_called_once_with(
            ["gh", "workflow", "run", "release.yml", "--ref", "main",
             "-f", "tag=v1.1.0", "-f", "run_instrumentation=false"],
            check=False,
        )
        self.assertIn("The Release workflow was dispatched", output.getvalue())


if __name__ == "__main__":
    unittest.main()
