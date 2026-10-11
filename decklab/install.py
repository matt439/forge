"""Build this branch's Forge and install it for Deck Lab.

Usage (from the repo root, with Maven and a JDK 21 or later):

    python decklab/install.py [--release C:/Tools/forge] [--out C:/Tools/forge-decklab] [--no-build]

The decklab branch starts from a Forge release tag (BASE_TAG) and carries Deck Lab's changes as commits.
This builds forge-gui-desktop's jar-with-dependencies with the build named after the commit
(Implementation-Version <version>-decklab-<commit>, "-dirty" added when the working tree has changes),
so Deck Lab can tell one build from another. Then it makes the install folder (--out) from the release
install of BASE_TAG (--release: its res folder, launch scripts and docs), with:
  - this build's desktop jar in place of the release's;
  - each card script this branch changed since BASE_TAG put into res/cardsfolder/cardsfolder.zip in place
    of the release's (one it deleted taken out);
  - every other file it changed under forge-gui/res copied into res;
  - decklab-build.txt: the build name, commit and base tag.
The release install is never changed. The new folder is made beside --out and swapped in at the end, so a
failed run leaves the old install as it was. Deck Lab's config forge_dir points at --out.
"""

import argparse
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE_TAG = "forge-2.0.15"
JAR_GLOB = "forge-gui-desktop-*-jar-with-dependencies.jar"
RES = "forge-gui/res/"
CARDS = RES + "cardsfolder/"
SCRIPTS_ZIP = Path("res/cardsfolder/cardsfolder.zip")


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def build_name():
    commit = git("rev-parse", "--short=9", "HEAD").strip()
    dirty = bool(git("status", "--porcelain", "--untracked-files=no").strip())
    return commit, f"-decklab-{commit}{'-dirty' if dirty else ''}"


def build(snapshot_name):
    mvn = shutil.which("mvn") or shutil.which("mvn.cmd") or "C:/Tools/maven/bin/mvn.cmd"
    subprocess.run([mvn, "-q", "-B", "-pl", "forge-gui-desktop", "-am", "-DskipTests",
                    f"-DsnapshotName={snapshot_name}", "package"], cwd=ROOT, check=True)


def changed_res():
    """{path under forge-gui/res/: True (changed or added) / False (deleted)} since BASE_TAG, committed or not."""
    out = {}
    for line in git("diff", "--name-status", "--no-renames", BASE_TAG, "--", RES).splitlines():
        status, path = line.split("\t", 1)
        out[path] = status != "D"
    return out


def jar_build(jar):
    """The build name in a jar's manifest (Implementation-Version)."""
    with zipfile.ZipFile(jar) as z:
        manifest = z.read("META-INF/MANIFEST.MF").decode("utf-8")
    for line in manifest.splitlines():
        if line.startswith("Implementation-Version:"):
            return line.split(":", 1)[1].strip()
    sys.exit(f"error: {jar} has no Implementation-Version")


def install(release, target, commit):
    jars = sorted((ROOT / "forge-gui-desktop" / "target").glob(JAR_GLOB))
    if len(jars) != 1:
        sys.exit(f"error: expected one {JAR_GLOB} in forge-gui-desktop/target, found {len(jars)}")
    name = jar_build(jars[0])
    if "-decklab-" not in name:
        sys.exit(f"error: {jars[0].name} is build {name}, not one this script built (run it without --no-build)")
    release_jars = sorted(release.glob(JAR_GLOB))
    if len(release_jars) != 1:
        sys.exit(f"error: expected one {JAR_GLOB} in {release}, found {len(release_jars)}")
    staging = target.with_name(target.name + ".new")
    if staging.exists():
        shutil.rmtree(staging)
    shutil.copytree(release, staging, ignore=shutil.ignore_patterns(release_jars[0].name))
    shutil.copy2(jars[0], staging / release_jars[0].name)

    changes = changed_res()
    scripts = {p[len(CARDS):]: keep for p, keep in changes.items() if p.startswith(CARDS)}
    if scripts:
        zpath = staging / SCRIPTS_ZIP
        tmp = zpath.with_suffix(".tmp")
        with zipfile.ZipFile(zpath) as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
            names = set(src.namelist())
            for info in src.infolist():
                if info.filename in scripts:
                    if scripts[info.filename]:
                        dst.writestr(info, (ROOT / CARDS / info.filename).read_bytes())
                else:
                    dst.writestr(info, src.read(info.filename))
            for entry, keep in sorted(scripts.items()):
                if keep and entry not in names:
                    dst.writestr(entry, (ROOT / CARDS / entry).read_bytes())
        tmp.replace(zpath)
    for path, keep in changes.items():
        if path.startswith(CARDS):
            continue
        dest = staging / "res" / path[len(RES):]
        if keep:
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / path, dest)
        elif dest.exists():
            dest.unlink()

    (staging / "decklab-build.txt").write_text(
        f"build={name}\ncommit={commit}\nbase={BASE_TAG}\nscripts_changed={sum(scripts.values())}\n", encoding="utf-8")
    if target.exists():
        old = target.with_name(target.name + ".old")
        if old.exists():
            shutil.rmtree(old)
        target.rename(old)
        staging.rename(target)
        shutil.rmtree(old)
    else:
        staging.rename(target)
    print(f"installed {name} in {target} ({sum(scripts.values())} card scripts changed)")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--release", type=Path, default=Path("C:/Tools/forge"))
    ap.add_argument("--out", type=Path, default=Path("C:/Tools/forge-decklab"))
    ap.add_argument("--no-build", action="store_true", help="install the jar already in forge-gui-desktop/target")
    a = ap.parse_args()
    if a.release.resolve() == a.out.resolve():
        sys.exit("error: --out must not be the release install")
    commit, snapshot = build_name()
    if not a.no_build:
        build(snapshot)
    install(a.release, a.out, commit)


if __name__ == "__main__":
    main()
