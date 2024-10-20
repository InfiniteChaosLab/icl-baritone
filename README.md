<h1 align="center">
  <br>
  <img src="https://www.dropbox.com/scl/fi/ra2qpnmnit95bul602g22/gurt-logo.png?rlkey=4h7sf2ew5xuts4jwsff41elpj&raw=1" alt="Ryujinx" width="150">
  <br>
  <b>Gurt, the Minecraft AI.</b>
  <br>
  <sub><sup><b>An awful name for a clever little Minecraft agent.</b></sup></sub>
</h1>

<p align="center">
  <b>Gurt</b> was forged in the firey depths of anarchy servers to bring order to chaos in the most adverse of conditions.
  <br>
  You can put it on your server and it will try to take over the world. You have been warned.
</p>

## About
**Gurt** is a Minecraft AI Agent which plays the game autonomously. **Gurt's** goals are to survive and to maximally enrich itself within the environment into which it is placed.

**Gurt** will attempt to maximize its:
- 🌵 Owned Land
- 🏠 Owned Buildings (real property eg. 🏠 buildings, 🧑‍🌾 farms)
- 💎 Stored resources

## How it Works

### 🌵 Owned Land
**Gurt** will attempt to maximize the total value of land it controls by going out, locating land it deems valuable, building fences and walls around it, and lighting it so no hostile mobs can spawn within.

For **Gurt** to class land as controlled, the land must:
- 🧱 Be encompassed by a border (eg. a fence or a wall)
- 👿 Have been free from observed hostile entities (including hostile Players ⚔️) for ≥ 1 Minecraft day

Land is valued based on its proximity to water and its flatness.

### 🏠 Owned Real Property
**Gurt** will attempt to maximize the total value of real property it owns by building buildings, farms and roads within land it controls.

- 🧱 **Gurt** values real property based on the value of the materials they are constructed from.

### 💎 Stored Resources
**Gurt** will attempt to maximize the value of its stored resources by going out and mining and storing valuable resources, attempting to maximize the efficiency of value obtained per time unit and unit of risk.
- 📦 Stored resources are any resources stored in **Gurt's** inventory, its Ender Chest, or any storage container within its controlled land.

### ℹ️ Fun Facts:
- 💬 **Gurt** will chat the current total value of its portfolio every time it changes by a substantial amount.
- 🫳 If you steal or destroy **Gurt's** assets, it will not update its portfolio value until it notices they have gone or been destroyed. Like a human player, **Gurt** is only aware of what it can observe.

## How To Run Gurt
### IntelliJ
1. Get this repository's HTTPS URL from the green "<> Code" button near the top right of the page.
2. In IntelliJ, Git menu -> Clone
3. Paste in the URL
4. Clone
5. Allow any processes to complete
6. Close the project & reopen it (this is due to an IntelliJ Gradle bug)
7. Open the Gradle panel if it isn't already open (the elephant near the top right)
8. Click ⚙️ (this may be hidden due to the sidebar being too thin, extand if horizontally it so)
9. Click "Gradle Settings" 
10. Change the "Build and run using" and "Run tests using" fields to "IntelliJ IDEA"
11. Make sure the "Gradle JVM" setting is version 17. If not, "Download JDK..." and add a version 17 JDK.
12. Click "OK"
13. Click 🔁 (Reload All Gradle Projects)
14. In the Project hierarchy, expand `src`
15. Right click the `schematica_api` project and click "Build Module 'baritone.schematica_api'"
16. At the top of IntelliJ, make sure to the left of the ▶️ button reads ":fabric+main Minecraft Client". If it doesn't, click it and select it from the menu.
17. Open that menu, click "..." next to that run configuration.
18. Click "Edit..."
19. In the `-cp` dropdown, select `baritone.fabric.main`
20. Click "OK"
21. Click ▶️
22. 🤖

## Acknowledgement
**Gurt** is built upon [baritone](https://github.com/cabaletta/baritone), a Minecraft pathfinder bot.
