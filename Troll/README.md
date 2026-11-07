# Troll

Paper 1.21.11, Java 21. Owner-only troll menu.

## Build
```
mvn clean package
```
Drop `target/Troll-1.0.0.jar` into `plugins/`.

## 1. Permission (owner only)
Nobody has `troll.use` by default, not even OPs. Give it with LuckPerms:
```
/lp group owner permission set troll.use true
```
or just for you:
```
/lp user YourName permission set troll.use true
```
Anyone without it sees the vanilla "Unknown command" message and the command doesn't show in tab-complete.
`troll.immune` makes a player untrollable.

## 2. Resource pack setup (needed for the jumpscare image and all the sounds)
`TrollPack.zip` is already built: the jumpscare image as a full-screen overlay, plus your three sounds converted to `.ogg`
(Minecraft can't play mp3). The source files are in `resourcepack/`.

1. Upload `TrollPack.zip` somewhere with a **direct download link**. Easiest is your GitHub repo: commit the zip, then the link is
   `https://github.com/YOUR-USER/YOUR-REPO/raw/main/TrollPack.zip` (the repo has to be public). Dropbox/Google Drive share pages don't work.
2. Paste the link into `plugins/Troll/config.yml` under `resource-pack.url`.
3. `/troll reload` (or restart). Players get the pack when they join (`resource-pack.send-on-join`).
4. Test on yourself: `/troll YourName jumpscare`. Check the console for pack errors if nothing shows.

`resource-pack.sha1` is already filled in for this exact zip. If you ever change the zip, set it to `""` and the plugin works out the new hash itself.
`resource-pack.force: true` kicks players who decline the pack, so only turn it on after you've confirmed the link works.

SHA-1 of the included zip: `b97e93d59a342e82a917a555a5eeba991825adde`

## Commands
| Command | What it does |
|---|---|
| `/troll` | Menu: pick a player, then a troll |
| `/troll <player>` | Troll menu for that player |
| `/troll <player> jumpscare` | Full-screen image + very loud sound for 3 seconds |
| `/troll <player> disconnect` | Disconnect sound, then they can't walk or jump (they can still look) until they rejoin. Run it again to undo. |
| `/troll <player> resource` | Removes and re-adds the pack 5 times, so their client keeps reloading |
| `/troll <player> reach` | Reach 1 block (entities and blocks). Run again to undo. |
| `/troll <player> ascend` | Levitates them to the height limit while the ascending song plays, then slow falling so they land safely |
| `/troll <player> reset` | Clears everything on them |
| `/troll reload` | Reload the config |

The victim never gets a message from the plugin. Every confirmation goes to whoever ran the troll.

## Notes
- **Jumpscare** works by briefly putting an item on their head that has a camera overlay (the same way a pumpkin blurs your screen). It
  only shows in first person. Their real helmet is saved on them and put back, even after a crash or logout.
- **Loudness:** the jumpscare file is already at digital maximum, so it can't get louder. Players' master volume still applies.
- **Ascend** uses levitation, so vanilla's "flying is not enabled" kick can't trigger.
- **pack.mcmeta** accepts resource pack formats 69 to 99. If a client ever shows a "made for a different version" warning, the pack still loads.
