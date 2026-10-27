# Releasing Deck Lab's Forge

A release lets someone running [Deck Lab](https://github.com/matt439/DeckLab) on their own PC download this fork's build instead of cloning it and building it with Maven. Each release is one build of the `decklab` branch: the Forge desktop release it's based on (`BASE_TAG` in `install.py`) with this branch's jar and changed `res` files put in, packed as `forge-<build>.zip` with a SHA-256 checksum. Its tag is the build name, such as `2.0.15-decklab-52387e83f`.

Make a release only of the build Deck Lab expects: the one in Deck Lab's `decklab/lib/forge/simjar.py` (`V`, which `CHECKED_BUILDS` maps to), once `cardlog-check` has passed with it. Deck Lab's SELF-HOSTING.md links to the release whose tag is that build.

## Make one

**On GitHub (the usual way):** Actions, **Deck Lab release**, **Run workflow** on the `decklab` branch, with the build's commit (the part after `-decklab-`) and **Also make a draft GitHub release** ticked. It downloads the base Forge release, runs `install.py` and `package.py` on Linux with JDK 21, uploads `forge-<build>.zip`, its `.sha256` and `release-notes.md` as the run's artifact, and makes a draft release tagged with the build name. Check the draft (the build name, the size, the notes), then publish it. A draft is visible only to people who can push to this repository.

**On the PC:** with a clean working tree at the build's commit (`git status` shows no changes; a `-dirty` build is refused):

```
python decklab/install.py --release C:/Tools/forge --out <scratch folder>
python decklab/package.py --install <scratch folder> --dist dist
```

Then make a release on GitHub tagged with the printed tag, targeting that commit, with `dist/release-notes.md` as its text and `forge-<build>.zip` and `forge-<build>.zip.sha256` as its files. `dist/` is ignored by git.

A build from the workflow and one from the PC have the same build name, engine release and res hash, but not the same bytes (Maven stamps times into the jar), so their checksums differ. That's expected: the release's checksum is the one in its own `.sha256` file and notes.

## When Forge moves on

Deck Lab's README, "Moving to a new Forge release", rebases this branch onto the new tag, checks the new build and records it in `simjar.CHECKED`. Making its release is the last step: run the workflow above with the new build's commit, publish the draft, then update the build and link in Deck Lab's SELF-HOSTING.md ("Forge simulations").
