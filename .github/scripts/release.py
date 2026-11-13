# SPDX-License-Identifier: GPL-3.0-or-later
"""Version and artifact operations shared by the release workflows."""

import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from urllib.parse import quote


BUILD_FILE = Path("app/build.gradle.kts")
CLIFF_CONFIG = Path("cliff.toml")
DEFAULT_GH_REPO = "edgardmessias/easygamepad"
GIT_CLIFF_PACKAGE = "git-cliff@2.10.0"
VERSION_PATTERN = r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
NAME_FIELD = re.compile(r'(?m)^([ \t]*versionName[ \t]*=[ \t]*")([^"\r\n]+)("[ \t]*\r?$)')
CODE_FIELD = re.compile(r"(?m)^([ \t]*versionCode[ \t]*=[ \t]*)([0-9]+)([ \t]*\r?$)")
MAX_VERSION_CODE = 2_100_000_000


def parse_version(version):
    if not re.fullmatch(VERSION_PATTERN, version):
        raise ValueError("Version must be X.Y.Z with no prefix, suffix or leading zeroes.")
    return tuple(int(part) for part in version.split("."))


def tag_version(tag):
    if not tag.startswith("v"):
        raise ValueError("Release tag must be vX.Y.Z.")
    version = tag[1:]
    parse_version(version)
    return version


def resolve_release_tag(tag):
    tag_version(tag)
    if "-" in tag:
        raise ValueError("Release tag must be a stable vX.Y.Z tag.")
    result = subprocess.run(["git", "show-ref", "--verify", "--quiet", f"refs/tags/{tag}"], check=False)
    if result.returncode != 0:
        raise ValueError(f"Tag {tag} does not exist.")


def validate_release_tag(tag):
    resolve_release_tag(tag)
    version = tag[1:]
    tagged_commit = subprocess.check_output(
        ["git", "rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}"],
        text=True,
    ).strip()
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if head != tagged_commit:
        raise ValueError("Checked-out commit does not match the release tag.")
    build_version, build_code = read_version_fields(BUILD_FILE.read_text(encoding="utf-8"))
    if build_version != version:
        raise ValueError(f"versionName {build_version!r} does not match tag {tag!r}.")
    if not 1 <= build_code <= MAX_VERSION_CODE:
        raise ValueError("versionCode is outside Android's supported range.")


def read_version_fields(text):
    names = NAME_FIELD.findall(text)
    codes = CODE_FIELD.findall(text)
    if len(names) != 1 or len(codes) != 1:
        raise ValueError("Expected exactly one literal versionName and versionCode in the app build file.")
    version, code = names[0][1], int(codes[0][1])
    parse_version(version)
    if not 1 <= code <= MAX_VERSION_CODE:
        raise ValueError("versionCode is outside Android's supported range.")
    return version, code


def updated_version(text, version):
    requested = parse_version(version)
    current, code = read_version_fields(text)
    if requested < parse_version(current):
        raise ValueError("Requested version is lower than the current version.")
    if code == MAX_VERSION_CODE:
        raise ValueError("versionCode cannot be incremented beyond Android's supported range.")
    text = NAME_FIELD.sub(lambda match: match[1] + version + match[3], text)
    return CODE_FIELD.sub(lambda match: match[1] + str(code + 1) + match[3], text)


def prepare_version(version):
    parse_version(version)
    tag = f"v{version}"
    result = subprocess.run(["git", "show-ref", "--verify", "--quiet", f"refs/tags/{tag}"], check=False)
    if result.returncode == 0:
        raise ValueError(f"Tag {tag} already exists; use a new version or retry Release on the existing tag.")
    if result.returncode != 1:
        raise ValueError("Could not check existing Git tags.")
    text = BUILD_FILE.read_bytes().decode("utf-8")
    updated = updated_version(text, version)
    BUILD_FILE.write_bytes(updated.encode("utf-8"))
    _, code = read_version_fields(updated)
    print(f"Prepared {tag} (versionCode {code}).")


def restore_keystore():
    required = (
        "ANDROID_KEYSTORE_BASE64", "ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD",
        "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD",
    )
    missing = [name for name in required if not os.environ.get(name, "").strip()]
    if missing:
        raise ValueError("Missing signing configuration: " + ", ".join(missing))
    try:
        data = base64.b64decode(os.environ["ANDROID_KEYSTORE_BASE64"].strip(), validate=True)
    except (ValueError, binascii.Error) as error:
        raise ValueError("ANDROID_KEYSTORE_BASE64 must contain a valid Base64-encoded keystore.") from error
    if not data:
        raise ValueError("The signing keystore is empty.")
    # Never persist passwords or write the private key into the checkout/cache.
    descriptor = os.open(os.environ["ANDROID_KEYSTORE_PATH"], os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as target:
        target.write(data)


def package_release(tag):
    version = tag_version(tag)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    tagged_commit = subprocess.check_output(
        ["git", "rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}"], text=True
    ).strip()
    if head != tagged_commit:
        raise ValueError("The checked-out commit does not match the release tag.")

    build_version, build_code = read_version_fields(BUILD_FILE.read_text(encoding="utf-8"))
    apk_dir = Path("app/build/outputs/apk/release")
    metadata = json.loads((apk_dir / "output-metadata.json").read_text(encoding="utf-8"))
    elements = metadata.get("elements", [])
    if metadata.get("variantName") != "release" or len(elements) != 1:
        raise ValueError("Expected one release APK in the build metadata.")
    element = elements[0]
    if build_version != version or element.get("versionName") != version:
        raise ValueError("Release tag does not match the versionName in the build and APK metadata.")
    if element.get("versionCode") != build_code or element.get("outputFile") != "app-release.apk":
        raise ValueError("Expected the signed release APK with the project's versionCode.")

    dist = Path("dist")
    dist.mkdir(exist_ok=True)
    if any(dist.iterdir()):
        raise ValueError("Release output directory must be empty to avoid including stale artifacts.")
    apk = dist / f"EasyGamepad-{version}.apk"
    source = dist / f"EasyGamepad-{version}-source.zip"
    shutil.copyfile(apk_dir / "app-release.apk", apk)
    subprocess.run(
        ["git", "archive", "--format=zip", f"--prefix=EasyGamepad-{version}/", f"--output={source}", head],
        check=True,
    )
    checksums = "".join(
        f"{hashlib.sha256(asset.read_bytes()).hexdigest()}  {asset.name}\n" for asset in (apk, source)
    )
    (dist / "SHA256SUMS.txt").write_text(checksums, encoding="utf-8", newline="\n")


def _shield_static_v1(label, message, color="informational"):
    return (
        "https://img.shields.io/static/v1?"
        + f"label={quote(label)}&message={quote(message)}&color={quote(color)}"
    )


def read_android_sdk_levels():
    text = BUILD_FILE.read_text(encoding="utf-8")
    min_match = re.search(r"minSdk\s*=\s*(\d+)", text)
    target_match = re.search(r"targetSdk\s*=\s*(\d+)", text)
    if not min_match or not target_match:
        raise ValueError("Could not read minSdk and targetSdk from the app build file.")
    return int(min_match.group(1)), int(target_match.group(1))


def previous_stable_tag(tag):
    tag_version(tag)
    result = subprocess.run(
        ["git", "tag", "--merged", f"{tag}^", "--sort=-version:refname"],
        capture_output=True,
        text=True,
        check=True,
    )
    for line in result.stdout.splitlines():
        candidate = line.strip()
        if candidate and "-" not in candidate:
            return candidate
    return ""


def git_cliff_release_body(tag, git_range=None):
    if not CLIFF_CONFIG.is_file():
        raise ValueError(f"Missing {CLIFF_CONFIG} for git-cliff.")
    args = [
        "npx", "--yes", GIT_CLIFF_PACKAGE,
        "--config", str(CLIFF_CONFIG),
        "--strip", "all",
        "--ignore-tags", "-",
    ]
    if git_range:
        args.append(git_range)
    else:
        args.extend(["--tag", tag])
    result = subprocess.run(args, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip() or f"exit code {result.returncode}"
        raise ValueError(f"git-cliff failed: {detail}")
    body = result.stdout.strip()
    if not body:
        raise ValueError("git-cliff returned an empty release body.")
    return body


def prepare_release_notes(tag):
    tag_version(tag)
    repository = os.environ.get("GH_REPO", DEFAULT_GH_REPO).strip() or DEFAULT_GH_REPO
    previous = previous_stable_tag(tag)
    git_range = f"{previous}..{tag}" if previous else None
    body = git_cliff_release_body(tag, git_range)
    min_sdk, target_sdk = read_android_sdk_levels()
    lines = [
        f"![GitHub Downloads](https://img.shields.io/github/downloads/{repository}/{tag}/total)",
        f"![Android]({_shield_static_v1('Android', f'minSdk {min_sdk}', 'informational')})",
        f"![Target SDK]({_shield_static_v1('targetSdk', str(target_sdk), 'blue')})",
        "",
        body,
    ]
    print("\n".join(lines) + "\n")


def dispatch_release(tag):
    tag_version(tag)
    command = ["gh", "workflow", "run", "release.yml"]
    repo = os.environ.get("GH_REPO", "").strip()
    if repo:
        command.extend(["--repo", repo])
    command.extend(["--ref", "main", "-f", f"tag={tag}", "-f", "run_instrumentation=false"])
    result = subprocess.run(command, check=False)
    recovery = " ".join(command)
    summary = f"Commit and tag `{tag}` were pushed.\n\n"
    if result.returncode:
        summary += f"Publication dispatch failed. Keep the commit and tag; retry only publication:\n\n```bash\n{recovery}\n```\n"
        print(summary, file=sys.stderr)
    else:
        summary += "The Release workflow was dispatched. Follow its run in the Actions tab.\n"
        print(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as target:
            target.write(summary)
    if result.returncode:
        raise ValueError("Release dispatch failed; use the recovery command above.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("prepare-version").add_argument("version")
    commands.add_parser("resolve-tag").add_argument("tag")
    commands.add_parser("validate-tag").add_argument("tag")
    commands.add_parser("restore-keystore")
    commands.add_parser("package").add_argument("tag")
    commands.add_parser("prepare-notes").add_argument("tag")
    commands.add_parser("dispatch").add_argument("tag")
    args = parser.parse_args()
    try:
        if args.command == "prepare-version":
            prepare_version(args.version)
        elif args.command == "resolve-tag":
            resolve_release_tag(args.tag)
        elif args.command == "validate-tag":
            validate_release_tag(args.tag)
        elif args.command == "restore-keystore":
            restore_keystore()
        elif args.command == "package":
            package_release(args.tag)
        elif args.command == "prepare-notes":
            prepare_release_notes(args.tag)
        elif args.command == "dispatch":
            dispatch_release(args.tag)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release error: {error}\n")


if __name__ == "__main__":
    main()
