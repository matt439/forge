"""Pack an install made by decklab/install.py as a release archive, with its checksum.

Usage (from the repo root, after install.py):

    python decklab/package.py --install C:/Tools/forge-decklab [--dist dist]

A newcomer to Deck Lab downloads this instead of cloning and building the fork. It refuses an install
whose decklab-build.txt is missing, names a "-dirty" build, or names another build than its desktop jar's
Implementation-Version, so a release is always a clean build of one commit. It writes, in --dist:
  - forge-<build>.zip: the whole install folder under one top folder of the same name (file modes kept,
    so forge.sh stays executable after unzipping on Linux or macOS);
  - forge-<build>.zip.sha256: its SHA-256 in sha256sum's format ("<hex>  <file name>");
  - release-notes.md: the release's text, naming the Forge release it's based on, the build, the commit,
    the res hash and the checksum.
The release's tag is the build name (such as 2.0.15-decklab-52387e83f): Deck Lab's simjar names the build it
expects, so its docs link to that tag. The steps for making a release are in decklab/RELEASE.md.
"""

import argparse
import hashlib
import os
import sys
import zipfile
from pathlib import Path

JAR_GLOB = "forge-gui-desktop-*-jar-with-dependencies.jar"
BUILD_FILE = "decklab-build.txt"
REPO = "https://github.com/matt439/forge"


def read_build(install):
    """decklab-build.txt's fields as {key: value}."""
    path = install / BUILD_FILE
    if not path.is_file():
        sys.exit(f"error: {path} isn't there: pack a folder decklab/install.py made")
    fields = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            fields[key.strip()] = value.strip()
    for key in ("build", "commit", "base", "res_sha256"):
        if not fields.get(key):
            sys.exit(f"error: {path} has no {key}= (made by an older install.py?)")
    return fields


def jar_build(jar):
    """The build name in a jar's manifest (Implementation-Version), or None."""
    with zipfile.ZipFile(jar) as z:
        manifest = z.read("META-INF/MANIFEST.MF").decode("utf-8")
    for line in manifest.splitlines():
        if line.startswith("Implementation-Version:"):
            return line.split(":", 1)[1].strip()
    return None


def check(install, fields):
    build = fields["build"]
    if build.endswith("-dirty"):
        sys.exit(f"error: {build} was built from a working tree with changes: commit them and build again")
    if "-decklab-" not in build:
        sys.exit(f"error: {build} isn't a Deck Lab build")
    jars = sorted(install.glob(JAR_GLOB))
    if len(jars) != 1:
        sys.exit(f"error: expected one {JAR_GLOB} in {install}, found {len(jars)}")
    found = jar_build(jars[0])
    if found != build:
        sys.exit(f"error: {jars[0].name} is build {found}, but {BUILD_FILE} says {build}")
    return jars[0]


def pack(install, archive, top):
    """Zip every file under install as top/<its path>, sorted, with its file mode. Returns the file count."""
    files = sorted(p for p in install.rglob("*") if p.is_file())
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path in files:
            rel = path.relative_to(install).as_posix()
            info = zipfile.ZipInfo.from_file(path, f"{top}/{rel}")
            info.compress_type = zipfile.ZIP_DEFLATED
            mode = path.stat().st_mode & 0o777
            if os.name == "nt" and path.suffix in (".sh", ".command"):
                mode = 0o755                # Windows keeps no execute bit: give the launch scripts one
            info.external_attr = ((0o100000 | (mode or 0o644)) << 16)
            with path.open("rb") as src, z.open(info, "w") as dst:
                while chunk := src.read(1 << 20):
                    dst.write(chunk)
    return len(files)


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def notes(fields, archive, digest):
    build = fields["build"]
    version = build.split("-decklab-", 1)[0]
    return f"""Deck Lab's build of Forge {version}: the Forge {version} desktop release ([{fields['base']}](https://github.com/Card-Forge/forge/releases/tag/{fields['base']})) with this fork's `decklab` branch built in, ready for [Deck Lab](https://github.com/matt439/DeckLab)'s Forge simulations.

- Build: `{build}` (commit [{fields['commit']}]({REPO}/commit/{fields['commit']}))
- Based on: Forge {version} (`{fields['base']}`)
- Card scripts changed: {fields.get('scripts_changed', '?')}
- res hash: `{fields['res_sha256']}`
- SHA-256 of `{archive.name}`: `{digest}`

Download `{archive.name}`, check its SHA-256 matches (`sha256sum -c {archive.name}.sha256` on Linux, `shasum -a 256 -c {archive.name}.sha256` on macOS, `certutil -hashfile {archive.name} SHA256` on Windows), unzip it and point Deck Lab's `forge_dir` at the `{archive.stem}` folder. It needs a Java JDK 21 or later. Deck Lab's SELF-HOSTING.md, "Forge simulations", has the steps.

Forge is GPL-3.0: the source of this build is this repository at the commit above.
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--install", type=Path, required=True, help="the folder decklab/install.py made (its --out)")
    ap.add_argument("--dist", type=Path, default=Path("dist"), help="where to write the archive (default dist)")
    a = ap.parse_args()
    install = a.install.resolve()
    fields = read_build(install)
    check(install, fields)
    top = f"forge-{fields['build']}"
    a.dist.mkdir(parents=True, exist_ok=True)
    archive = a.dist / f"{top}.zip"
    partial = archive.with_name(archive.name + ".part")
    count = pack(install, partial, top)
    partial.replace(archive)
    digest = sha256(archive)
    (a.dist / f"{archive.name}.sha256").write_text(f"{digest}  {archive.name}\n", encoding="utf-8")
    (a.dist / "release-notes.md").write_text(notes(fields, archive, digest), encoding="utf-8")
    print(f"packed {count} files of {fields['build']} as {archive} ({archive.stat().st_size / 1e6:.0f} MB)")
    print(f"sha256 {digest}")
    print(f"tag {fields['build']}")


if __name__ == "__main__":
    main()
