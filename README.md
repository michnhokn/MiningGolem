# Mining Golem

A Fabric mod for Minecraft 26.3 that adds a diamond-textured copper-golem-like helper
which digs a serpentine strip-mine network for you — gathering loot, lighting tunnels
with torches and bridging over holes, even while you are away.

**Download:** [Modrinth](https://modrinth.com/mod/mininggolem) — requires [Fabric API](https://modrinth.com/mod/fabric-api).

---

## Getting a golem

- **Spawn ritual** — right-click the *top face* of a diamond block while holding a jack
  o'lantern. Both blocks are consumed and the golem spawns on the spot.
  - The golem's dig direction = the direction you are looking when placing.
  - You are recorded as its *owner* (chat messages go to you).
  - Sneak to place a real jack o'lantern on a diamond block without triggering the ritual.
- **Spawn egg** — available in the creative spawn-eggs tab.
- **Picked-up golem** — a spawn egg that carries a stored golem (see below) restores the
  exact same golem: inventory, name, job plan and everything.

## Interacting with the golem

- **Right-click** — opens the GUI (works with any item in hand).
- **Sneak + right-click** — picks the golem up. It turns into a spawn egg item in your
  inventory holding *all* of its state (tools, torches, loot, name, direction). Place
  that egg again to re-deploy it, fully restored — **at the new position**: the old dig
  site plan is discarded, the golem is idle and will start a fresh serpentine where you
  placed it.
- The golem is **knockback-immune**, does **not wander** when idle, and never despawns.
- When it dies, it drops its entire inventory.

## The GUI

| Area                | What it is                                                                 |
|---------------------|----------------------------------------------------------------------------|
| Status (top right)  | Color-coded live status with a state dot: 🟢 Mining · ⚪ Idle · 🟠 Paused (out of torches/tools/liquid) · 🔴 Inventory full · 🔵 Done |
| Name field          | Type a name and press **Enter** (tooltip reminds you). While typing, *no key* closes the GUI (Escape first stops typing, then closes). The name shows above the golem and in its chat messages. |
| N / E / S / W       | **Dig direction** (current one is grayed out; hover for "Dig north/east/…"). Changing it stops a running job and clears the plan — press Start again to dig the new way. Clicking the current direction is always a no-op. |
| Start / Stop        | Sits beside the name field; toggles the mining job, the label reflects the current state |
| Left column (4)     | **Tool slots** — pickaxes *and* shovels, with greyed-out vanilla item icons as hints |
| Right column (4)    | **Torch slots** — torches (normal or soul)                                 |
| Center 4×9 grid     | **Loot** — everything the golem mines lands here, never on the ground       |
| Stats (right of your inventory) | **Blocks:** how many blocks the tools in its slots can still break (remaining durability, `0` when empty) · **Tunnel:** how far it can dig with its torches (one torch per 10 columns) |

The name, dig-direction and start/stop controls sit above the golem's inventory;
the background uses vanilla-style rounded corners.

The GUI live-updates while the golem works — you can watch loot and tool durability change.

## How it mines

The golem digs an **endless chain of closed rings**: a 100-block tunnel row in the
chosen direction, a short connector, a return row back, and a 2-block rung that
closes the loop. Every pair of rows is connected at both ends, and rings are chained
on alternating sides, so the whole network stays one connected maze of loops. There
is no fixed length: the plan is extended ring by ring, and the golem keeps going
until a stop condition hits (inventory full, no tools, unbreakable block, or you
press Stop). **Stop + Start continues the same network** — old and new tunnels stay
connected. Only changing the dig direction in the GUI, or picking the golem up and
placing it somewhere else, starts a fresh pattern.

The bore is strictly horizontal, 1 wide × 2 tall, at the Y level where the job
started, and the golem mines only in front of itself — no wandering off, no digging
into walls at random.

- **Ores:** an ore in the bore is vein-mined: the whole connected cluster goes into
  the inventory (up to 48 blocks per vein). After each column, the newly exposed
  faces are checked, and ores the golem can actually harvest with a tool in its
  inventory are mined (with vein mining) before the tunnel continues.
- **Tools:** the matching tool is chosen per block — pickaxe for stone-type, shovel
  for dirt/gravel — always the fastest tool that can really harvest the block (a
  wooden pickaxe won't touch diamond ore). Break time scales with block hardness and
  tool tier, like a player's. Each broken block wears the tool; when one breaks, the
  golem switches to the next tool slot automatically.
- **Torches:** one every **10 columns**, placed on the ground behind it (wall-mounted
  as fallback, retried next column if the spot is blocked).
- **Loot:** mined blocks go straight into the inventory. Only when the inventory is
  full does excess drop on the ground — and the job stops with the status
  *Inventory full*.

## Safety & resilience

The golem is built to keep working in ugly conditions:

- **Gravel / sand:** suffocation is impossible. Blocks that land *inside* it are dug out
  immediately; falling blocks still airborne in the tunnel make it wait until they land,
  then mine them. A blocked bore is re-mined until it is clear.
- **Water / lava:** fire-immune, breathes underwater, cannot be pushed by currents, and
  paths straight through flooded or burning tunnels — no pausing.
- **No gaps in the floor:** holes below the golem and below the next column are bridged
  using solid blocks from its loot inventory (cobblestone, dirt, …).
- **Stays on its level:** if it falls into a hole it jumps back up; for deeper falls it
  stacks a lift block and climbs out.
- **Works while you're away:** while a job runs, the golem keeps its chunks loaded and
  ticking (vanilla chunk tickets) — it continues mining with no player nearby.
- **Resumes after save/reload:** waypoints, progress, inventory, direction and name all
  persist; an interrupted job continues where it stopped.

## Chat messages

Sent to the owner (or, if offline, all players within 32 blocks):

- `<Name> started digging.` / `<Name> resumed digging.`
- `<Name>: Paused — out of torches.` / `out of tools.` *(refill and it resumes on its own)*
- `<Name>: Stopped — inventory full.` / `unbreakable block ahead.`

## Debug mode

```
/mininggolem debug            # toggle
/mininggolem debug <true|false>
```

Requires op-level permissions. When on:

- mining is **10× slower** and walking runs at quarter speed — perfect for watching it work
- break/place targets are highlighted with dust particles:
  - 🔴 red — the block currently being broken
  - 🔵 blue — the torch spot candidate behind the dig position
  - 🟢 green — the bridging spot below the next column
