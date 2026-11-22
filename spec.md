# Drekki Rancher

A NeoForge mod for Minecraft 1.21.1. The player gets a drekki (plural drekki), a companion animal that grows with
them over time, in the spirit of chocobo breeding (Final Fantasy) and Seikrets (Monster Hunter Wilds). In lore,
drekki come from The Faraway.

## Overview

1. Early game: the player buys an Intriguing Egg from the wandering trader.
2. The player builds a hatchery and puts the egg in it. After 3 in-game days the egg is ready, and the player
   hatches it with a click.
3. The drekki grows through three stages. Growth is driven by time spent traveling with the player.
4. Travel also shapes three affinities (astral, diet, biome). Each affinity changes the drekki's look, and each
   locks in permanently at a set point in its growth.
5. The drekki collects traits over time, from biomes and from other activities.

## Egg

- Lore: the egg comes from The Faraway.
- Source: the vanilla wandering trader, in the early game. Every trader carries exactly one Intriguing Egg: the
  offer is always in its list and has one use, so each trader sells one egg and no more.
- Price: 6 eggs (vanilla chicken eggs). No emeralds.
- Implementation: NeoForge's `WandererTradesEvent` only adds to the random pools, so the offer is appended when a
  player opens the trader (server side, if the offer list does not hold it yet). The cost is an `ItemCost` of
  6 `Items.EGG`. This also works when the `trade_rebalance` feature flag is on, which the event does not cover.
- Tooltip: "This peculiar egg seems ready to hatch. Maybe I can put together a hatchery of some sort..."
- Fur color: each egg gives the drekki a random muted fur color. Special colors come only from breeding (purity
  tiers, mixed hues, rare lines). Fur never changes after the hatch.

## Style

Proposal 2026-11-16 (design page, Blocks tab, Style). Every block, item and screen the mod adds: medieval fantasy in dark
tones. Each list runs shadow to highlight.

- Steel `#2b2e35 #41454e #5d636e #8d939e`: always dark and worn, never bright iron. Frames, bands, hinges, buckles,
  rivets.
- Leather `#2e1a33 #4a2a52 #64396e #8a5a92`: moody purple, for anything padded, strapped or stitched (cushions,
  straps, carriers, saddles, screen panels).
- Wood `#2a1e16 #3b2a1f #523b2a`: dark oak for bodies and crates.
- Straw `#7e6430 #b0903f #d4b665` and feather `#3a3344 #6a5d7a #d9cfbd`: only where a nest, bedding or a creature
  calls for them.
- Leather takes dye everywhere it appears (hatchery nest, Tufted Leather, screens): a dye darkens into the same moody
  range as the purple (crease, base, puff shades at -62%, -38%, -18%).
- Thread `#d8c79a` for text on leather; glow `#7ff0dc` only for magic states (ready egg, astral eyes, rare sheen).
- Screens: a riveted steel frame, a leather panel, slots sunk into dark leather, thread text, straw progress bars.
- Open: whether the existing items (hats, carriers, tonics, barding icons) move to this palette.

### Functional blocks

The blocks the game needs, in the style (design page, Blocks tab, Functional):

- Drekki Hatchery: see Hatchery below.
- Drekki Trough: holds food; a hungry drekki eats its own diet's food from it, hatchlings through the side opening,
  bigger stages from the top. The silhouette stays the feeder the feeding loops use (`TroughBlock`'s shape). Look: a
  dark oak box with dark steel corner posts and two riveted bands, a padded leather roll along the top of every wall
  (the dyeable part, right-click with a dye), a straw bed inside with the food on it (herbivore food on fresh greens).
  A dark steel plaque on the back and each end shows the diet it holds (below).
- Trough lines (proposal 2026-11-16):
  - A trough joins the trough beside it when both face the same way (side openings on the same side) and stand side
    by side along that face, at the same height. The walls between them drop away; floor, straw and rolls run on.
  - A trough joins only along the line of its own face, so a line is always straight: one placed around a corner
    faces another way and stays on its own. No L shapes.
  - At most 10 in a line; an 11th at either end stays a separate trough with its own walls.
  - A line acts as one trough: one diet on every plaque, and food anywhere in it feeds a drekki at any part.
- One diet per trough (decided 2026-11-16, replacing one family): a trough, or a line, holds one diet: Raw, Cooked,
  Seed or Root. The first food in sets it. Food of any other diet is refused with a message ("This trough holds Raw
  food"). Empty again, the plaques go blank and it takes any diet. Which diet a food is comes from the server food
  lists (see Diet affinity).
- Plaque: shows the one diet, centered: an uncooked porkchop (Raw), a browned drumstick (Cooked), wheat seeds (Seed)
  or a carrot (Root). Bare when empty. Cooked meat is drawn browned on the straw.
- Pipes and hoppers: the trough exposes an item handler and the same rule runs there. Only listed foods of its diet
  are valid (`isItemValid`); anything else is refused and returned whole, so a pipe keeps it or routes it elsewhere.
  Into an empty trough a simulated insert of any listed food succeeds without setting the diet; the first real insert
  sets it, so a second diet arriving in the same tick is refused. Food can be extracted from below like a chest, and
  the diet clears when the last item leaves. A line shares one handler, reachable through any of its troughs.
- Hatchlings: a stage 1 drekki has no diet yet; what it eats most decides it. A trough holding one diet steers it
  there. To leave its diet open, use troughs of several diets or feed it by hand.
- Herbivore food lies on a bed of fresh greens in the trough, so a herbivore trough reads green from any side.
- Placing (proposal 2026-11-16): the open side faces the player, like a furnace's front; sneak-place flips it to face
  away. The facing decides joining. Open: whether a trough placed against the end of a line copies the line's facing.
- A trough whose open side is walled off cannot feed stages 1 and 2; they look elsewhere (see Feeding at troughs).

### Decor blocks

Proposal 2026-11-16 (design page, Blocks tab, Decor). Optional decoration in the style; nothing needs them.

| Block | For | Forms | Look |
|-------|-----|-------|------|
| Rancher Planks | Floors, walls, wainscot | Block, slab, stairs | Dark oak boards, staggered joints, steel nail heads |
| Rancher Beam | Posts, ceiling joists | Block on any axis (like a log), slab | Dark oak grain, a riveted steel band at each end; ends show rings in a steel rim. The slab has its own textures at half height (bands down both ends, a whole ring on each end), so no face shows a cropped band |
| Tufted Leather | Walls, stall dividers | Block, slab; undyed plus the 16 dyes, each its own block as wool is (a block and a dye in a crafting grid) | Leather tufted in diamonds, a steel stud at each tuft; undyed is the style's purple, a dye darkens into the same moody range |
| Rancher Shingles | Roofs, ceilings | Block, slab, stairs | Dark slate shingles in staggered rows: steel grey, one in five tinted purple, some nail heads (not straw: too close to a hay-bale roof) |
| Straw Bedding | Floor cover | 1 px layer, like a carpet | Loose straw; no collision box, so no ceiling |
| Rancher Fence | Pens, stall fronts, corner posts | Fence, fence gate | Beam posts with a dark steel cap, plank rails; joins neighbours like any fence, so a stack is a bare pole |

Straw Bedding's recipe: two Farmer's Delight straw side by side make 3 (its rope is two straw stacked, so no clash).
Without Farmer's Delight, two wheat side by side, so plain NeoForge can still make it. Checked against vanilla and
every Haven pack mod: no recipe of ours matches another recipe's grid.

Open: the other recipes (a draft on the page), and whether bedding does anything for checked-in drekki.

## Hatchery

A block the player builds. Requirements:

- A ceiling over the 3x3 centered on the hatchery. With this ceiling the hatchery counts as a stable. Proposal
  2026-11-16 (design page, Hatchery tab):
  - Revised 2026-11-16: above the hatchery there must be a 3x3 group of covered columns that includes the
    hatchery's own column; it need not be centered on it, so a hatchery in a corner needs no overhang. A column is
    covered by a block with a collision box (planks, glass, leaves, slabs; not torches or carpet) with 2 to 9 blocks
    of air between the hatchery's level and it: counting the hatchery's own block as the 1st, the 4th to 11th block
    up. Lower cover (a post, a wall) is fine but does not count as roof.
  - The two blocks directly above the hatchery are air (no collision box).
  - Code, with h the hatchery's y: some offset (ox, oz) in -2..0 x -2..0 such that for every dx, dz in 0..2, some y
    in h+3..h+10 at (x+ox+dx, z+oz+dz) has a non-empty collision shape; and (x, h+1), (x, h+2) have none.
  - The roof can be any size or shape, sloped or uneven, as long as such a 3x3 sits over the hatchery.
  - Beside the hatchery that leaves 3 blocks of air above the floor, which fits a standing stage 3 (1.725 tall since
    2026-11-20). A ridden stage 3 is about 2.7 tall.
- Light level above 0 on all sides of the block.

Look (proposal, design page): in the mod's style (see Style). A dark oak chest bound in dark steel (corner posts,
two riveted bands), a leather nest with a padded rim on top (the style's purple, or any of the 16 dyes: right-click
the hatchery with a dye; one block with a color state), filled with a bed of straw (to the rim, heaped
around the egg, spilling over the sides), a few feathers tucked in. About 12 px tall. The egg rests on the straw.

The player puts the egg in the hatchery. Incubation takes 3 in-game days; the egg sits still. Then the egg is Ready:
it rocks now and then, its teal speckles pulse, a crack shows and a few motes rise, with a tooltip line. It waits as
long as the player likes.

Hatching is a player action: right-click the Ready egg. Every condition is checked at that moment, the ordinary ones
(light, ceiling) and any rare-line ones. A failed check leaves the egg Ready and tells the player what failed. The
hatchery screen also shows which conditions currently pass, so a player can line up a rare line without a wiki.

## Growth

In game the stages are named, never numbered (decided 2026-11-17): stage 1 is a **Pup**, stage 2 a **Fledgling**,
stage 3 **Mature**. The spec keeps the numbers.

The drekki grows with travel time. Travel is elective: travel time counts whenever the drekki is out in the world,
and the player decides by carrying it or letting it out. Time in a carrier counts for nothing.

### Carriers

Two craftable items:

- Small carrier, shaped: `S L S / L W L / S _ S` (4 sticks, 4 leather, 1 wool of any color). No iron, so a new
  player can make one right after the hatch.
- Large carrier, shaped: `I L I / L C L / I _ I` (4 iron ingots, 3 leather, 1 small carrier). An upgrade: a drekki
  in the small carrier stays inside.
- Their items are 3D models (2026-11-20; not placeable): a crate with leather sides, a grated door, feet and a handle,
  the small one framed in wood, the large one in iron with rivets.
- Small drekki carrier: holds a stage 1 drekki.
- Large drekki carrier: holds a stage 2 drekki.
- Stage 3 cannot use a carrier at all.
- Right click a drekki with an empty carrier of the right size to put it inside. The carrier is then an item in the
  inventory with the drekki in it. A name-tagged drekki keeps its name.
- Right click the ground with a full carrier to let the drekki out. The carrier is empty again (assumption).

### Stage 1

- Length: 6 game days of travel (revised 2026-11-20, was 7; server config). Eye lock-in at 3 days, 60 minutes of play.
- Size: very small, about a slime.
- Fits the small carrier.
- No attacks, no cooldown abilities.

### Stage 2

- Length: 6 game days of travel (revised 2026-11-20, was 7; server config).
- Size: about a wolf. Revised 2026-11-20: 75% of the page's pixel model (hitbox 0.6 wide since 2026-11-20, a wolf's width, so it fits an open door; 0.975 tall; was 1.0 x 1.3).
- Needs the large carrier.
- Has its diet's basic attack (Bite or Elemental ball), no cooldown ability.

### Stage 3

- Length: permanent (fully grown).
- Size: a horse, or slightly smaller. Revised 2026-11-20: 75% of the page's pixel model, about a player's height (hitbox
  0.6 wide since 2026-11-20 so it fits an open door, whose panel leaves 0.625 at the block's middle; was 1.125 by scale, 1.5 at full size; 1.725 tall). The page's pixel table and loops stay as they are; one scale per stage
  (1, 0.75, 0.75) sets the model, hitbox, shadow, particles, travel speeds and the room it needs in the world.
- Can be mounted and ridden like a horse.
- Every ability. No built-in glide or slow fall: flight comes only from the Wings of Hope trait (elytra-style
  while ridden, dive to gain speed, pull up to climb), so only a ridden stage 3 flies.

## Drekki screen

Right click the drekki at any stage to open its screen. It shows the drekki's inventory and a 3D model. Revised
2026-11-17 (design page, UI tab): the equipment slots the stage has and the model, then two tabs beside them, one
shown at a time, with the stance buttons (stage 2 and 3) at the foot of that column, level with the hearts and food, so they stay put
whichever tab is open. Details: the health hearts and food shanks on top, then bloodline, astral, diet, biome and the mood band (its name in its color; the value, effects
and reasons such as "Hungry: -2 a minute" in a hover tooltip). Traits: two columns of five (the most a drekki carries:
9 slots and Naturally Gifted), each with a hover tooltip saying what it does, the negative marked, sleeping traits
named. Stance buttons: three icons (a sword for Aggressive, a shield for Reactive, a white flag for Passive), each with a
tooltip naming the stance and what it does. Then the pack from stage 2
(nothing in its place at stage 1). No learning progress is shown. The screen is vanilla's container width.
Revised 2026-11-16, laid out like a Sophisticated Backpacks screen: the drekki part on top (equipment slots: hat; saddle
and barding at stage 3; the model; details or traits, with Drekki and Traits tabs), then its pack, then the player's
inventory and hotbar, so items move between them by click and shift-click. Equipment slots take only their own item.
Equipment slots have no text label: an empty slot shows its item's icon, dimmed (the hat slot a straw hat). The astral
line reads "Astral: <name>".

Health and food: at the top of the Details tab, as vanilla's HUD draws a player's: a row of hearts (full, half, empty; ten to a
row, so a stage 3 drekki's extra hearts wrap) and a row of ten drumstick shanks for the hunger meter. Max health:
10, 30 and 50 by stage (decided 2026-11-18; Fledgling and Mature raised 2026-11-20 from 20 and 30: a Fledgling at 20
had half a tamed wolf's 40 and barely won a fight with one skeleton).

Screen style (all the mod's screens), after the UI remix's Sophisticated Backpacks redesign (minecraft-create-ui-remix,
`backpack-art.js`): the top is the remix's own leather, the body of its generated
`sophisticatedcore:textures/gui/storage_background_9.png` (sort button painted out) as a 9-slice: the 17-row top band
stays whole, and the sides and bottom edge carry the seam. The texture's body below the band is one row repeated (the
remix averages it per column, `xOnly`, so Sophisticated can glue rows), so the mod's own screens paint the body with the
remix's leather recipe instead, without that averaging: #3a2619 with `TEX.grain` at 0.12 and `TEX.blotch` at 0.18 in
overlay (`material.js`), a tile that repeats both ways. Nothing is scaled sideways; screens are Sophisticated's 9-column width (176 logical px), with vanilla's 18 px slots and 7 px margins.
Slots on the leather are the remix's storage cell (`slots_background`): dark wells. The remix draws its grey layer only
through Dark Mode Everywhere: not installed, the cells stay dark; installed and off, the layer shows light grey
(vanilla-like); the darker shades bring it back toward the art. The mod should ship the same art (or draw from the same
source) rather than a lookalike. The title sits right on the leather, with plain slots and thin rules on it.
Tabs and buttons are the dyeable leather. The player's inventory hangs below in vanilla's design (light grey panel and
bevel, vanilla slot wells, the #404040 label). Dark Mode Everywhere (in the pack) cycles four shades; as in the remix,
only the plain greys follow it (the vanilla inventory, slot wells, meter tracks) while the leather keeps its look.
Rendered by default without dark mode: the game's own light greys. The model
shows the fur color and changes with the affinities below.

The screen shows:

- Mood
- Astral affinity
- Diet affinity
- Biome affinity
- Bloodline and purity tier (for example Slate, vivid), or the rare line's name
- A traits tab:
  - Slots: one icon per slot, 3/6/9 by stage, filled first, the negative marked. Hover shows the trait's tooltip.
  - Naturally Gifted, when it has it, beside the slots as a free trait.
  - Sleeping: inherited traits waiting for a slot; they wake at random at each stage-up.
  - Learning: a progress bar for each counted activity the drekki has started (for example 3/5 phantoms). One-off
    triggers stay hidden until learned.

Inventory by stage. Items stay in their slots when the drekki grows.

- Stage 1: no pack (the hat slot only, see Look).
- Revised 2026-11-20: a drekki gets no storage from growing. Storage comes from a bag in its accessory slot (stage 2 and 3;
  accessory, not bag: other accessories may come later),
  drawn on the drekki: two bags side by side on top of the back, either side of the spine, behind the saddle, with a
  strap over both (not on the flanks: the folded wings flare outward toward the back and would cut through them).
  - Drekki Satchel (stage 2 and 3): 9 slots; small pouches with a brass buckle.
  - Saddlebag (stage 3): 27 slots; tiers as Sophisticated Backpacks has them (the bag ringed by 8 ingots keeps its
    contents): iron 36, gold 45, diamond 54, netherite 63 (smithing table). The trim shows the tier.
  - With Sophisticated Backpacks installed, the satchel and saddlebags are registered as its backpack items (its
    storage and upgrades; upgrade slots 1, 1, 2, 3, 4, 6 from the satchel up). Slots and upgrade slots
    stay under Sophisticated's backpack of the same tier at every tier (satchel/leather 9+1 vs 27+1, saddlebag/copper
    27+1 vs 45+1, iron 36+2 vs 54+2, gold 45+3 vs 81+3, diamond 54+4 vs 108+5, netherite 63+6 vs 120+7); only these bags go in the accessory slot, never
    Sophisticated's own. Without it they are plain bags, their contents on the item as a shulker box keeps them.
    Opening the bag a drekki wears (2026-11-20): right-click it in the drekki screen's accessory slot (a left click
    still takes it), as a placed shulker box opens; with Sophisticated, also its own Open Backpack key (whatever it is
    bound to) while looking at one's own drekki, and over the bag in the drekki screen's accessory slot or in one's own
    inventory, as over a backpack. That screen is Sophisticated's own storage screen (its slots, upgrade slots, upgrade tabs and
    sorting) through Core's StorageContainerMenuBase, the base its storage-on-entities screens use (2026-11-20); only
    its settings screen stays with the held bag. The drekki ticks the bag's upgrades where it stands (magnet, pickup),
    except feeding: on a drekki a feeding upgrade feeds the drekki only (its pack meals), never players around it.
  - Placing (2026-11-20): sneak + right-click on a block places a bag, as a Sophisticated backpack is placed. With
    Sophisticated it becomes a block of ours drawn as the satchel or saddlebag (not a backpack), one per bag, that is
    Sophisticated's backpack block underneath (its block entity, added to their block entity type), so its screen,
    upgrades, Create contraptions and breaking work as for any backpack; the leather and trim take the bag's dyes and
    the metal its tier. Without it, a Drekki Bag
    block of its own: right-click opens it, breaking it gives the same bag back with what it holds and its dyes, as a
    shulker box (also in creative when it holds anything); pick-block gives the bag.
  - Packs in packs (2026-11-20): without Sophisticated a bag is a shulker box of its size (no bag or shulker box goes
    inside). With it, its own rule holds for every backpack, ours included, both ways: refused unless the outer one
    has an Inception upgrade and the inner one has none; whether a bag fits in shulker boxes and bundles is
    Sophisticated's setting.
  - Pack meals (2026-11-20): a drekki eats from its bag only when the bag has an enabled feeding upgrade, and only
    food that upgrade's filter allows; without Sophisticated the bags are storage only, like shulker boxes of their size. Our bags
    cannot be placed as a block or worn by a player. Sophisticated's dye recipe colors them, with the same column rule.
  - Unequipping a bag that holds items: the items stay in it (the bag carries them); it can go back on any drekki of
    the right stage, or be opened in hand with Sophisticated. A drekki that dies for good leaves its hat, barding and
    bag in its tombstone (see Tombstone).
  - Icons (2026-11-20): the bags' items are their 3D model (the placed bag's model, dyed), not flat icons.
  - Outgrown barding (Fledgling barding on a drekki that grows to Mature) goes to its owner's inventory, or drops.
- Stage 2: an accessory slot and a barding slot. Stage 3: an accessory slot, a saddle slot (needed to ride) and a barding
  slot.
- Barding is split by stage (2026-11-20): Fledgling barding and Mature barding are separate items in every tier. The
  existing barding items are the Mature barding (ids kept). Fledgling barding recipe: `M _ M / M L M` (5 material, 1
  leather; leather barding `L _ L / L L L`); netherite: Fledgling diamond barding in a smithing table, as for Mature.
- Barding works as wolf armor does (2026-11-20): while it holds, damage goes into its durability, one point per point
  of damage, and the drekki takes none; damage in vanilla's `bypasses_wolf_armor` tag still reaches the drekki. It
  breaks when used up and is repaired with its material on an anvil. The armor values above only show in tooltips.
  Durability (Fledgling 30 health, Mature 50; a wolf in wolf armor is 40 + 64 = 104):

  | Tier | Fledgling | Mature |
  |------|-----------|--------|
  | Wood | 24 | 34 |
  | Leather | 32 | 45 |
  | Stone | 48 | 67 |
  | Iron | 74 (30 + 74 = an armored wolf) | 104 (50 + 104 = 154, above it) |
  | Gold | 90 | 126 |
  | Diamond | 120 | 169 |
  | Netherite | 150 | 211 |

- Dyeing (2026-11-20), barding and bags alike, as Sophisticated Backpacks dyes: two colors, main (barding plates, bag
  leather) and trim (barding rims, bag flaps and strap). In the crafting grid only the column counts: dyes left of the
  item color the main, right of it the trim, in its column both. Each craft averages the dyes with the side's current
  color (one sample, however it was made; an undyed side does not count), vanilla's leather rule, so re-dyeing blends
  a step at a time. A water cauldron washes both. The bags' metal (clasps, tier strips) never takes dye.
  Washing and bleach (2026-11-20): every dyeable item of the mod (barding, bags with or without Sophisticated, the
  hatchery, the trough) uses one named cauldron action, `LeatherWash`, which the Haven pack's bleach runs by name, so
  bleach takes both dyes off all of them.
- Barding icons (2026-11-20): 3D, a pile of the pieces laid down: the chest piece lying flat (its rim along the front,
  two strapped buckles across it), two neck bands resting on it, overlapping and tilted over its sides at 22.5 degrees;
  a Mature pile is bigger with a face and a knee plate peeking out from under it. Plates show the main dye (or the tier's color) with the tier's material pattern, rims the trim dye.
- Barding: new craftable drekki armor in seven tiers, drawn on the rig as plates over the chest, neck, head, legs (a greave down the front of each
  shank and a sabaton wrapping the front half of the foot, from the ankle to just before the toes and talons) and tail (a ring on each segment but the tip).
  - Armor: wood 2, leather 3, stone 4, iron 5, gold 7, diamond 11 (horse armor's values where it has the tier);
    netherite 13, 2 toughness, 10% knockback resistance.
  - Recipe, shaped: `M _ M / M L M / M M _` (6 of the material, 1 leather in the middle; mirrored works too).
    Leather barding is 7 leather in the same shape. Every tier takes dye the way leather armor does (barding plus
    dyes in a crafting grid; dyes mix as vanilla, a water cauldron washes it): the dye becomes the plate color and
    the tier keeps only its material's texture (wood grain, cobble, brushed metal, diamond facets). Wood takes any planks
    (`#minecraft:planks`), stone any stone tool material (`#minecraft:stone_tool_materials`).
  - Later, not at launch: special barding where a dye colors only one trim part and the rest keeps its own look.
    So each barding names which of its parts take the dye (every part for the launch tiers), not a single color.
  - Netherite: diamond barding, a netherite upgrade template and a netherite ingot in a smithing table.
  - Checked against every vanilla 1.21.1 shaped recipe: no overlap with horse armor, wolf armor or anything else.

### Stay and follow (decided 2026-11-21)

A checked-out drekki follows its owner (the default) or stays. Every stage, pups too.

- **Buttons.** Stay and Follow sit at the right of the stance row in the drekki screen, on every stage of a checked-out
  drekki. The chosen one has a 1 px green border (`Art.ON`), as the chosen stance has; nothing else marks it.
- **Stay.** It walks nowhere: no follow, no catch-up teleport, no stroll, no trip to a trough, no chase. It still bites
  what comes within its reach and shoots from where it stands; its traits work from there.
- **Drekki Whistle** (shapeless: bone, iron nugget, string; one per stack; a one second cooldown). It changes the
  owner's checked-out drekki between staying and following. A drekki that is not with its owner (another dimension, an
  unloaded chunk, or more than 30 blocks away) always comes to them and follows instead, whatever it was doing.
  Told to follow by the whistle it walks to its owner when it is within 20 blocks and a path reaches them; from
  further, or with no way to walk there, it jumps to them. With no safe spot around them (they fly) it lands on their
  own position, unless that is in lava or fire. One tone for stay; two short tones, the second
  higher, for follow.
- **Message** on the action bar, from the buttons and the whistle alike: "Your drekki is staying" or "Your drekki is
  following you", with its name when it has one.
- **Where it stays.** The roster keeps the spot (`Entry.stay`). While that chunk is loaded and the owner is online the
  drekki stands there; when the chunk unloads it goes with the chunk and comes back with it. When the owner logs out it
  leaves the world as any checked-out drekki does, and at login it is back at the same spot, still staying. A copy a
  chunk loads while the owner is offline, or away from the stay spot, is refused. Checking it in, carrying it or checking
  it out again makes it follow.

## Affinities

Each affinity changes in real time until it locks in. After lock-in it is permanent.

| Affinity | Driven by                    | Values               | Locks in            | Shows on the model                         |
|----------|------------------------------|----------------------|---------------------|--------------------------------------------|
| Astral   | Where stage 1 travel happens | Dormant, then Solar, Lunar, Aero, Underworld, Deep, End | 50% through stage 1 | Eye color |
| Diet     | What the drekki eats         | Carnivore raw or cooked, Herbivore seed or root | Start of stage 2 | Talons, wings, tail plumage |
| Biome    | Time spent in each biome     | Traits per biome     | 50% through stage 2 | Foot tint                                  |

### Astral affinity

- Starts Dormant: matte brown eyes with no glow. The glow starts at lock-in.
- At lock-in the chat says "<name>'s eyes kindle: <affinity>" to its owner and every player within 30 blocks, as a
  learned trait does (2026-11-20; an unnamed drekki is "<owner>'s drekki" to the others). Growing up is shared the same
  way: "<name> is now a Fledgling!" and "<name> is now a Mature!".
- Gained in traveling mode only: a tick counts only when it also counts as travel time (see Growth), so time at
  the stable or near a bed shapes nothing. Each travel tick in the first half of stage 1 (revised 2026-11-18) counts toward one of
  six, by where the drekki is. At lock-in the largest count wins, and a tie is settled at random among the tied.
  Each tick counts for exactly one, by the first test that matches, so Solar and Lunar never overlap the
  others. Thresholds are a proposal:
  - Underworld: in the Nether.
  - End: in the End.
  - Deep: in the Overworld below y 0, the deepslate level. No sky check: y alone decides.
  - Aero: in the Overworld at y 128 or higher.
  - Lunar: in the Overworld from y 0 to y 127, at night.
  - Solar: in the Overworld from y 0 to y 127, by day.
- Eye color, all light emissive once locked: amber for Solar, cyan for Lunar, silver for Aero, red for Underworld,
  green for Deep, violet for End. Dormant is matte brown.
- Bred children of one bloodline: the astral comes from the parents, not from travel (see Breeding), and the eyes
  are lit from the hatch. They are never Dormant. Children of mixed bloodlines hatch Dormant and lock by travel,
  like a trader egg: mixing rerolls the astral as it rerolls the color.
- Home terrain: the place that locked the affinity, by the same six tests. While the drekki is in its home terrain
  it regenerates 1 health every 10 s, its mood rises, and the cooldowns of its abilities are
  halved. The abilities are listed under Combat. Outside its home terrain nothing is lost.

### Diet affinity

Four diets, in two families. The family sets the look and the threat response. The diet inside it sets which
combat axis the drekki is built for:

| Family    | Diet   | Rule                                                        | Axis             |
|-----------|--------|-------------------------------------------------------------|------------------|
| Carnivore | Raw    | raw animal flesh, fish included                             | physical damage  |
| Carnivore | Cooked | cooked animal flesh, fish included                          | magical damage   |
| Herbivore | Seed   | plants grown from a separate seed or sapling item           | physical support |
| Herbivore | Root   | plants grown by planting the crop or the plant itself       | magical defense  |

Every vanilla item it eats, by diet. Anything not listed is not food for it:

- Raw: beef, porkchop, mutton, chicken, rabbit, cod, salmon, tropical fish, pufferfish, rotten flesh, spider eye.
- Cooked: steak, cooked porkchop, cooked mutton, cooked chicken, cooked rabbit, cooked cod, cooked salmon.
- Seed: wheat, wheat seeds, beetroot, beetroot seeds, melon slice, melon seeds, pumpkin, pumpkin seeds, apple.
- Root: potato, baked potato, poisonous potato, carrot, sweet berries, glow berries, kelp, dried kelp, cocoa
  beans, red mushroom, brown mushroom, chorus fruit, bamboo, cactus, sugar cane, crimson fungus, warped fungus,
  nether wart.
- Not food: anything crafted (rabbit stew, mushroom stew, suspicious stew, beetroot soup, pumpkin pie, cookie,
  cake, bread, hay bale, sugar), the golden foods (golden apple, enchanted golden apple, golden carrot), egg, milk
  bucket, honey bottle, torchflower seeds, pitcher pod, and every other item.

Rules behind the lists:

- Nothing crafted: no stews, soups, pie, cookie, cake, bread, hay bale or sugar. Smelted items stay: baked
  potato, dried kelp and the cooked meats.
- Apples are Seed because the tree grows from a sapling, a separate item. Berries are Root because the berry is
  what gets planted.
- Harmful foods (rotten flesh, spider eye, pufferfish, poisonous potato) count for their diet and do not hurt it
  (proposal).
- Eating meat makes the drekki a Carnivore. Eating plants makes it a Herbivore. The diet inside the family is
  whichever of its two foods it ate most during stage 1.
- Locks in the moment stage 2 starts.
- "Magical" means the vanilla magic damage types: potions, evoker fangs, dragon breath, guardian beams, wither,
  sonic boom. The exact tag list is TBD at build time.
- Look: horns are ivory until the diet locks at the start of stage 2. From then, physical diets (Raw, Seed) keep
  ivory horns and magical diets (Cooked, Root) turn them ebony. Talons match the horns. Fangs stay ivory
  (assumption).
- Position and abilities: see Combat.
- The ball and the breath are magenta. Open: their element.
- Carnivore: talons, one fang on each side of the lip from stage 2, and leathery membrane wings. Herbivore: no
  talons, no fangs, feathered wings, and more plumage on the tail.
- Threat response: Carnivore is offensive. Herbivore is defensive.
- Default lists reviewed against every vanilla 1.21.1 food, crop and seed and against NeoForge's convention tags
  (2026-11-16). Read in this order:
  - `never` first (never food): `#c:foods/golden`, torchflower seeds, pitcher pod.
  - raw: the vanilla raw meats and fish, rotten flesh, spider eye, then `#c:foods/raw_meat`, `#c:foods/raw_fish`.
  - cooked: the vanilla cooked meats and fish, then `#c:foods/cooked_meat`, `#c:foods/cooked_fish`.
  - seed: the vanilla list plus the whole melon, then `#c:seeds` (every seed item).
  - root: the vanilla list, then `#c:foods/berry` (berries are planted themselves).
  - Last, the crop rule (`crops_by_seed_tags`, default on): every item in a `c:crops/<name>` tag is Seed when
    `c:seeds/<name>` has items (wheat, beetroot, melon, pumpkin: grown from a separate seed), else Root (cactus,
    carrot, cocoa, nether wart, potato, sugar cane: the crop itself is planted). Modded crops that follow the
    convention tags land correctly; anything a list already named keeps its diet.
  - Not used: `#c:foods/fruit` and `#c:foods/vegetable` mix diets (chorus fruit and golden apples, beetroot with carrots).
  - The server log prints one line with each diet's count whenever the map is built.
- Server food lists (2026-11-16): the server config holds four lists, `diet.raw`, `diet.cooked`, `diet.seed` and
  `diet.root`, which default to the lists above. Entries are item ids or item tags (`#c:foods/raw_meat`), so a server
  can add modded food or take vanilla food out. Read order is raw, cooked, seed, root, each list top to bottom, with a
  tag expanded in place. The first category an item is read into is its only one: a later list that names it again is
  skipped, and the server log warns with the item, the category it kept and the one skipped. Unknown ids are skipped
  with a warning. One resolved map drives everything that asks "which diet is this food": eating, the stage 1 diet
  count, the trough's diet and plaque, and tooltips; it is sent to clients on join and on config reload.

### Biome affinity

- Time spent in each biome category is counted until lock-in. At lock-in the category with the most time wins, and
  the drekki gets that category's traits: one positive and one negative. Travel gives exactly one biome category.
- Cover every vanilla biome, including the Nether and other dimensions. Overworld time below y 0 counts as the Cave
  category, whatever the biome there (in 1.21.1 the surface biome reaches down to bedrock outside the three cave
  biomes).
- Traits per biome must be configurable, so modded biomes can be covered too. A full list of vanilla and modded
  biomes is needed.
- Foot tint comes from the biome affinity. The tint is muted. Tints are defined per biome, in the same config as
  the traits. The shank above the foot takes the wing's main color (dark membrane for carnivores, light feathers
  for herbivores). From stage 2 the light chest patch and a small trim ring near the tail tip also take the foot
  tint, and on stage 3 the base segment of each horn does too.
- Biome capstone traits: a spread of capstone traits that lock in at the start of stage 3.

## Traits

The drekki collects traits from three sources:

- Biome traits, from the biome affinity above.
- Biome capstone traits, a spread that locks in at the start of stage 3.
- Activity traits, learned from specific activities.

The trait list is the team's trait sheet (Activities, Biomes and Breeding tabs):
https://docs.google.com/spreadsheets/d/1X9WXM477EFGVopwLCk41VUD08IhGRFqoAZoE7ZHNA_g. The feasibility review of every
row is on the review page listed in `artifacts.md`. Rules, decided 2026-11-04:

- Checkout: a player has one drekki checked out at a time (see Checkout). Only the checked-out drekki learns, and
  only while within 10 blocks of its owner.
- Teacher: only the owner's activities teach the drekki.
- Learning chance: a one-off activity (catch fire, freeze, take fall damage, run out of air, cure with milk, break an item) teaches
  its trait only 25% of the times it happens. Counted activities (kill X mobs, catch X things, do something a set
  number of times) and death activities (survive with a totem) always teach it. Mood scales the
  25%: 50% at high mood, 12.5% at low mood (see Mood).
- Who benefits: every player within 10 blocks of the drekki gets the player half of its traits, like Steel edge.
  A row can narrow this, for example "only while mounted".
- No stacking: a player in range of several drekki with the same trait gets it once, the strongest copy. Training
  Day still drops XP from each drekki.
- Biome traits work everywhere once learned, not only in their biome.
- Caps (proposal): 3 traits at stage 1, 6 at stage 2, 9 at stage 3. A negative trait takes a slot like any other.
- Sleeping traits: a child keeps every inherited trait that passes, up to 9, but only as many as its stage cap are
  active. The rest sleep. Each stage-up wakes random sleeping traits into the new slots. Sleeping traits come first:
  a learned trait only takes a slot no sleeping trait is waiting for, so a richly bred drekki may have no room to
  learn.
- Offers (decided 2026-11-19): a finished activity does not teach at once. Its owner alone gets "<name> wants to learn
  [<trait>]"; the link has the trait's tooltip, and a click opens the drekki screen on its Traits tab, only while the
  drekki is checked out, out of its carrier and within 10 blocks. The offer sits in its first free slot in gold, with a
  check (learn it) and a cross (pass). One offer at a time: a new one replaces the old, whose count stays full, so it
  comes back on its next count. A pass sets a counted trait's count to 0; a one-off trait can roll again on the next
  activity. An offer stays until it is answered or replaced (saved with the drekki).
- Learning message: when the owner says yes, its owner and every other player within 30 blocks of the drekki
  get a chat message, "<name> has learned [<trait>]!",
  where the trait name in brackets shows the trait's tooltip on hover (name and effect), the way advancement messages
  do. An unnamed drekki is "Your drekki" to
  its owner and "<owner>'s drekki" to everyone else.
- At the cap a drekki learns nothing new: activities count nothing, no offer is made, and nothing is said in chat.
- Inheritance: each parent passes each of its traits, positive or negative, at 33% (up to 9 kept; see Sleeping traits). A trait
  both parents hold passes at 56% (1 - 0.67^2). Combos form from the traits that pass, and a combo replaces its
  parts: the child never holds a combo and one of its parts, and does not learn a part later that a held combo covers.
  An inherited biome positive is extra to the one that travel gives.
- One negative, ever: a drekki holds at most one negative trait. If several negatives pass, one is kept at random.
  If neither parent has a negative, the egg has a 33% chance of a breeding-only negative, picked at random from a pool:
  Late bloomer (grows through the stages 25% slower), Absent-minded (each activity trait it would learn has a 25%
  chance to be forgotten instead, "<name> almost learned something, but forgot."; biome and breeding traits are
  unaffected), Recessive (its traits pass to its children at half the chance, about 16%), Weak bite (its basic
  attack deals 15% less damage). If no negative passes or rolls, the child hatches with Naturally Gifted: a free trait
  (outside the cap, never inherited, earned only at birth) that blocks its biome's negative at lock-in. So a
  Naturally Gifted adult has no negative. A drekki that already has a negative gets only its biome's positive at
  lock-in. A trader egg is not bred, so it never has Naturally Gifted and always gains its biome's negative.
- Each biome category has a positive and a negative trait (see Negative biome traits).
- Poly traits (breeding) add a second astral home terrain: Polyphasic Solaris (Solar and Lunar), Polystratic Caelum
  (Aero and Deep), Polyplanar Eschaton (Underworld and End). Polytria Opus, from the full set, makes every terrain home.
- Poly eyes: a drekki with a poly home trait has a different astral color in each eye. The pair is drawn at random
  from the poly pairs it qualifies for (Solaris: Solar and Lunar; Caelum: Aero and Deep; Eschaton: Underworld and
  End; Opus qualifies for all three), and the side each color goes on is random. It is set at the hatch and never
  changes.
  There are no astral penalties to remove.
- Trait details decided 2026-11-04 (the sheet holds the full wording):
  - Ocean Walker and Lava Walker: the drekki always stays on top and cannot dive.
  - Nether Forest teaches Hoglin Ward: hoglins avoid the drekki as they avoid warped fungus.
  - Stamp Out puts the fire out and keeps it out for 5 s (still in fire or lava, the player would light again), with 5 s of Fire Resistance; Huddle Up thaws the player and stops freezing for
    5 s. Both keep the 2-minute cooldown.
  - Patchwork (was Jerryrigging) covers every item with durability.
  - Hide & Seek: ghasts ignore every player within 10 blocks of the drekki; fireballs already fired still hit.
  - Mount controls: the jump key jumps, double jumps (Cloud Stompers) and third jumps (Launch Protocol); the sprint
    key or a double tap of forward dashes (Four Winds).
  - Creature Comforts stacks with Hero of the Village, not with other drekki.
  - Cloud Stompers is learned by bouncing on slime blocks 10 times.
  - The Fallen Emperor (changed 2026-11-18, was dying to a piglin): kill 10 piglins; brutes and zombified piglins
    count too. Piglins then treat its owner as if wearing gold.
  - Night Watch (added by the team): kill 5 phantoms. Phantoms cannot spawn within 30 blocks of the drekki
    horizontally, at any height.
  - Soft Landing (added by the team): learned from taking fall damage. When a player within 10 blocks of the drekki
    would fall more than 6 blocks, the drekki casts Slow Falling on them. 2-minute cooldown. A fall over 6 blocks
    the cast misses (the last stretch and the landing arrive in one move) lands with no damage instead.
  - Undertow (added by the team as Deep Sea Diver): learned by running air down to the last bubble (25% each time).
    When a player within 10 blocks across of the drekki (any depth) takes drowning damage, a current lifts them fast to the surface
    like a soul sand bubble column, with Water Breathing while they rise. It cannot pull through a solid ceiling;
    with no open water above, they get 10 s of Water Breathing instead. 5-minute cooldown.
  - Hearty (added by the team): learned by healing the drekki 5 times with potions or spells (any heal from
    outside the mod: `LivingHealEvent`, so spell mods need no compat code). A heal counts only while the drekki is
    missing health, at most once per 30 s; home regeneration, fed healing, Draconic Vampirism and Rejuvenate do not count.
    Effect: +6 max health (3 hearts), and the drekki regenerates 1 health every 10 s everywhere, 1 every 5 s in its home
    terrain. Hunger still stops all regeneration; Blood-thirsty halves it.
  - Duck & Cover (added by the team, 2026-11-20): learned by blocking 10 arrows or tridents with a shield (counted).
    An arrow or trident (not the owner's own) about to hit its owner within 10 blocks bounces off as from a shield
    (the game's reverse deflection, with the shield sound). 6-second cooldown (3 s in its home terrain): about 1 in 3
    of a skeleton's arrows on Normal. Other projectiles (fireballs, potions, shulker bullets) are not stopped.
  - Hibernation Plan slows both the player's hunger and the drekki's (see Hunger).
  - Phoenix Heart works on every death and the trait is kept. The egg goes to the owner's inventory when they are
    online with room, otherwise it is held and given on their next login or at the hatchery. It is never dropped.

Effect numbers, written into the sheet's rows (starting points, to tune in testing). `snowy_beach` is in Snow & Ice only:

- Balm & Salve: only harmful effects that milk cures; beneficial effects are kept. Once it acts, every such effect is
  kept off for 5 s (2026-11-20: a lingering cloud or a wither rose applies its effect again every few ticks).
- Hook Line & Sinker: a 50% chance per tick of one extra countdown tick, about a third less waiting.
- The Open Road: +10% movement speed for the player and the ridden drekki.
- Downstream: +25% swim speed, oxygen bonus 0.25 (air lasts exactly 1.25 times as long).
- Four Winds: the camel's dash at twice its speed, with the camel's 55-tick cooldown.
- Training Day: 18 XP every 6,000 ticks (3.5 skeletons at 5 XP, rounded up).
- Patchwork (was Jerryrigging): the 7 days are elapsed game time, 168,000 ticks, so sleeping does not shorten them.
- Inverse Luminance: light 14 minus the light where it stands, at most 14 (a torch).
- Draconic Vampirism: 15% rounded to the nearest point, at most 4 health (2 hearts) a second.
- Predator's Eye: 24 blocks, mobs in the monster category.
- Homes & Gardens: a 25% chance of one extra growth step after each natural one, 20 blocks as a sphere.

## Checkout

Decided 2026-11-04. A player has one drekki checked out at a time; the rest are checked in at hatcheries.

- Names (revised 2026-11-16): a hatchery is named with an anvil, like a chest; the name stays on the block and on the
  item when broken. The screen's title is the name, with "Drekki Hatchery" and the coordinates under it; unnamed, the
  title is "Drekki Hatchery". Every hatchery screen shows the owner's whole roster, every drekki they own and where it
  is: "Here", "With you", "At <name>", "At another hatchery" for an unnamed one, or "No hatchery".
- Move… (2026-11-16): any drekki that is not checked out can be moved from any hatchery screen to any of the owner's
  hatcheries. The picker lists them by name (unnamed ones by coordinates) with each one's count against its cap; a
  full one is disabled. The target's chunk is loaded briefly to place it.
- Roster on the hatchery screen (proposal 2026-11-16, design page UI tab): one row per drekki the player owns, with a
  portrait of its head rendered from its own model, face on (in the game: the entity renderer drawn into the row, as
  the inventory draws the player), its name, stage and bloodline, and where it is (Here, With you, or
  At <hatchery name>). Pick a row, then Check out (or Check in), Pair or Trade, plain labels. Pair only for a stage 3
  drekki that lives here; Trade not while it is checked out; Check out swaps with the one already out. A disabled
  action says why on hover. The hatchery screen has no player inventory: the egg goes in by right-clicking the
  hatchery with it.
  The list shows four rows and scrolls; its header counts the drekki living at this hatchery against its cap.
- Follow-up screens (proposal 2026-11-16, design page UI tab), each in the same panel with Back:
  - Move: the drekki's card, every hatchery (name or coordinates, count against its cap; full and current ones
    disabled), then "Move to <name>".
  - Pair: the picked drekki plus a partner from the stage 3 drekki living here (resting or hungry ones disabled with
    the reason), the optional tonic slot, and a preview of the child by the breeding rules (fur tier or new hue, eyes,
    horns, trait odds, the 3-day rest). Pair needs an empty nest, since the egg goes into this hatchery.
  - Trade: nearby players (over 8 blocks disabled), both offers side by side as cards (traits, mood resetting to 50,
    equipment), each side switching between its drekki and Nothing (a swap, or a gift either way; both empty cannot
    be confirmed, and Trade opens from any row so a player can offer nothing and receive), Confirm on each side, and the rules (any change clears both, Confirm unlocks 3 s after it, the pack
    must be empty, 8 blocks or 60 s cancels).
- Hatchery cap (decided 2026-11-16): up to 20 drekki live at one hatchery (server config `hatchery_capacity`,
  default 20). A full hatchery takes no more: a plain check-in and a move into it are refused with a message naming
  the cap. The cap is checked on the result of an action, so a swap at the cap goes through (one out, one in).
- Hatching when full (decided 2026-11-16): refused, and the egg stays Ready in the nest as long as needed ("Paddock is
  full (20/20): move a drekki out or check one out to make room"). The hatchery screen lists it with the other hatch
  checks ("Room for the hatchling", with the count). Pairing stays allowed when full: the egg does not count, only
  the hatchling.
- Swaps (decided 2026-11-16): checking out a drekki while another is out swaps them in one step. The one that was out
  is checked in here by the Placement rule, pulled back from wherever it is (its rider is dismounted first); the new
  one comes out by the same rule and follows the player. Pack, saddle, barding and hat stay with each. With no room
  for either within 3 blocks nothing changes ("No room around the hatchery"): never two out, never none by accident.
- Checked-in drekki live at their hatchery and wander within 25 blocks of it. They are placed by the Placement rule below
  (within 3 blocks of the hatchery), so a fenced pen around it holds them.
- Spawning (proposal 2026-11-16): the owner's roster (saved data: each drekki's whole record and its hatchery) is the
  source of truth. Checked-in drekki are real entities while their hatchery's chunk is loaded; they save and unload
  with it like any mob. When the hatchery's chunk loads, and every so often after, the hatchery checks its roster
  against the drekki around it: a missing one (pushed out of range, removed by a command) is placed again from its
  record (see Placement), and a duplicate of the same drekki is removed.
- Wandering (proposal 2026-11-16, stricter than MineColonies' citizens): each checked-in drekki is leashed to its
  hatchery, 25 blocks out at most, and never goes more than 3 blocks from an anchor block: a dirt path, Straw
  Bedding, or the hatchery itself. The area it may use is every spot within 3 blocks of an anchor, inside the 25-block leash. Anchors chain
  (revised 2026-11-17): an anchor counts when it lies within 3 blocks of an anchor that counts, starting from the
  hatchery; nothing else (no walk, no fence check) decides the area. So players draw the stable with paths and bedding; with none, a drekki keeps to
  3 blocks around the hatchery. Stage 1 the same (decided 2026-11-17, after two corrections): anchors chain, so a
  hatchling that reaches bedding may go on to a path within 3 blocks of it, and so on, inside the 25-block leash; with
  no anchors it keeps to 3 blocks around the hatchery. Bedding draws it: most of its strolls end on bedding, and at
  night it walks to a bedding spot and sleeps until morning (the sleep loop); with no bedding in reach it sleeps by the
  hatchery. Its pathfinding treats spots outside the area as blocked, and one found outside (after
  a block is broken) walks back in. The hatchery caches its anchors and refreshes them when a block in range changes.
  Spawn spots (above) must lie in the area too. It never wanders into unloaded chunks. Every age walks through an open door or gate; none opens a closed one (2026-11-20). Their area traits (Homes & Gardens, Creature
  Comforts and the like) work there, under the no-stacking rule.
- Checkout, check-in and swaps happen only at a hatchery screen, and only for drekki that live at that hatchery
  (revised 2026-11-16): one living elsewhere shows Check out, Pair and Trade disabled ("Lives at <name>: check it out
  there"); Move… brings it over first. A drekki with no hatchery can be checked out or moved from any hatchery.
- Placement (revised 2026-11-16), one rule for every time a drekki appears at a hatchery (check-out, check-in, a move,
  a hatch, the roster placing a missing one): within 3 blocks of the hatchery block, preferring right next to it. A spot
  fits when it stands on a solid block, out of liquid, with room for the stage's hitbox (stage 3 needs 1x2x1 clear since its narrower hitbox, 2026-11-20; stage 2, 1x1x1).
  Revised 2026-11-17: the search walks out from the hatchery (a flood fill in walking steps), stepping only between
  neighboring spots a drekki can walk between, so a fence, wall or closed gate stops it; it takes the first spot that
  fits, so spots inside a pen come first, nearest walk first. Only when none fits does it try the spots it could not
  walk to, ring by ring (distance 1, then 2, then 3). Cost: at most a 7x7 area a few blocks high, run only when a
  drekki appears; the wander area's walk from the hatchery shares the code and its cache. With no room within 3 blocks the
  action is refused with "No room around the hatchery" (a roster placement waits as data and the screen says so).
  A checked-out drekki then follows its owner (the baseline catch-up teleport); a checked-in one starts wandering.
- Breaking a hatchery (revised 2026-11-16): its drekki go to "No hatchery": kept as data with the owner, never lost,
  out of the world. From any hatchery screen the owner moves them to a hatchery or checks one out, at any time. The checked-out drekki is not affected: it is tied to
  no hatchery while out and checks in at any. An egg inside drops as its item and keeps its incubation progress.
  Proposal: only the owner can break a hatchery.
- On logout, the checked-out drekki is saved with the player and appears beside them on the next login. It is never
  left in the world while its owner is offline.

## Trading

Proposal 2026-11-05 (the user asked for player-to-player trades and left the design open). A drekki changes owner only
through a trade at a hatchery, so nobody loses one by accident or to a trick.

- Start: at a hatchery screen the owner picks one of their checked-in drekki and chooses Trade, then a player within
  8 blocks. That player gets a trade window.
- The window shows each side's offer as the drekki's own card: name, bloodline and tier (or rare line), stage, astral,
  diet, traits with progress, mood, and its equipment. Each side offers one drekki or nothing, so a trade is a gift or
  a swap. Items are not part of it; players hand those over as usual.
- Both players must press Confirm. Any change to either offer clears both confirmations, and Confirm only works 3 s
  after the last change, so an offer cannot be swapped at the last moment. Closing the window, moving more than
  8 blocks apart or 60 s without both confirming cancels it.
- Equipment travels with the drekki: hat, saddle and barding. The pack must be empty before the drekki can be offered,
  so nobody gives away items by mistake.
- After the trade the drekki belongs to the new owner and joins their roster at their nearest hatchery, or is held as
  data until they place one (the same rule as a broken hatchery). It keeps everything it is: lineage, traits and their
  progress, astral, diet, biome, horns, stage and growth. Its mood resets to 50, the start of a new home, and owner-bound
  traits (Teacher and the like) now follow the new owner.
- A checked-out drekki cannot be offered (check it in first). A ghost can be traded; its traits stay asleep.
- A drekki in a carrier still belongs to its owner. A carrier can change hands like any item, but only the owner can let
  the drekki out: for anyone else using it does nothing, and its tooltip says "Belongs to <owner>. Only they can let it
  out." So a carrier is never a way around a trade.
- The owner can check a carried drekki in at the hatchery without the carrier (lost, destroyed, given away). The roster
  brings it home; each carry has its own code on the carrier and the roster, so that carrier, and any older one of the
  same drekki, reads "The carrier is empty." when used (decided 2026-11-18).
- Every trade is written to the server log (both players, both drekki). Server config `allow_trading`, default true.

## Hunger

Decided 2026-11-04, revised 2026-11-16: hunger has no penalties of its own. It lowers mood, and mood carries every
effect (see Mood), so other systems can soften or sharpen it later.

- Revised 2026-11-18: ten shanks. The meter drains wherever the drekki is live (at a hatchery, out with its owner),
  faster while it travels (the rates and the food values are below). Revised again 2026-11-18: hunger runs only while the drekki is live (its chunk loaded and ticking).
  Time it was not ticking (unloaded, in a carrier, its owner offline) costs nothing, so a player back after a week
  does not find every drekki starving; there is no catch-up and no simulated meals. Hungry is 0 shanks; it eats
  whenever it has room for a shank.
- While hungry, mood falls 2 a minute. Eating gives +5 mood and ends the drain. It never starves to death.
  Hibernation Plan makes the meter drain 33% slower.
- Food values and saturation (decided 2026-11-21, replaces "one diet food fills one shank"): a food gives its own
  nutrition (2 points to a shank) and its own saturation, as it gives a player; a diet food with no food values
  (wheat, seeds) gives one shank and 0.6 of a shank of saturation. Saturation is hidden, never above the shanks, and
  is spent before them. What does not fit in the bar is lost. It still eats only with room for a whole shank.
- Drain (revised 2026-11-22: a quarter of the first rate, on the user's word): half a shank a game day, one while it
  travels (server config `shanks_per_day`, `travel_shanks_per_day`; the old keys and their 1 and 2 are gone).
- Fed healing (decided 2026-11-21), as a player's on its 20 point bar, from the drekki's own bar only: at 9 shanks or
  more a hurt drekki heals 1 health every 4 s; with a full bar and saturation, 1 every half second, paid from
  saturation only. Each health costs 0.75 of a shank (a player's 6 exhaustion). Mood does not stop it, and it is
  apart from the home terrain and Hearty regeneration. It does not count toward Hearty. A ghost does not heal this way.
- A food from its owner's hand also heals 8 health at once (decided 2026-11-22), and a hurt drekki takes one even on
  a full bar (the food then adds saturation only). Its diet only, as ever. Trough and bag meals do not heal at once.
  It does not count toward Hearty.
- Food: the player's hand at any time. A checked-out drekki eats at a trough while its owner stands still, else
  from its bag. A drekki at home (checked in) goes to the troughs first, then its bag. Its bag feeds it only with a
  Sophisticated feeding upgrade (see Accessory: bags).

### Trough storage and pipes (decided 2026-11-22)

- Each trough holds one stack. The troughs of a line share their slots, one per trough, as a Create vault: food put
  into any trough of the line (by hand, in the screen, by a pipe) goes to the same food with room first, then to an
  empty slot. Breaking a trough drops only its own stack.
- A line is one inventory to pipes, funnels, chutes and hoppers, from any side of any of its troughs. It takes only
  food a drekki eats, of the line's diet (the first food sets it), where a slot has room. Anything else is refused,
  so it waits in the pipe and is never dropped. Nothing can be pulled out: a chute under a trough would empty it.
- The screen shows the line's slots, up to five in a row, a longer line in two rows; the diet is beside the title.
- A trough saved before 0.4.9 held up to four stacks. On its first tick it keeps one; the rest go into the line's
  slots, and what does not fit drops on the trough. Nothing is lost.
- A drekki with a set diet uses only troughs of that diet, in a line or alone; a pup (open diet) uses any.

### Feeding at troughs (proposal 2026-11-16)

- A hungry drekki looks for a trough when it is not fighting, ridden or following its owner on the move.
- Search area: a checked-in drekki searches its hatchery's area (see Checkout, Wandering), not around itself, so every
  trough of the stable counts wherever it has wandered. A trough counts only if its eating spot lies inside the area,
  within 3 blocks of a path, bedding or the hatchery; place troughs along the paths. A checked-out drekki
  searches 16 blocks around itself, and only while its owner stands still.
- A trough counts when it is loaded, reachable and free. Reachable: stages 1 and 2 need a standing spot in front of
  the open side; stage 3 eats from the top at any side that is not blocked. Spots (decided 2026-11-18): each trough
  segment has a front spot (any stage; the only one stages 1 and 2 can use) and a back spot (stage 3 over the wall),
  and the two segments at a line's ends have an end spot each (stage 3). Each spot feeds one drekki at a time: a lone
  trough feeds up to 4, a line of 10 up to 22. A blocked spot feeds nobody. A drekki claims a spot, walks to it, eats,
  and releases it.
- Owner only (decided 2026-11-18): a trough records who placed it, and feeds only that player's drekki.
- Choice (decided 2026-11-18): it picks a random trough among those it can use, not the nearest; separating diets is
  the player's job (the trough Ponder shows how). Before the diet locks (stage 1) any trough with food counts; each
  meal counts toward its diet. After the lock, only troughs holding its own diet count.
- A meal is one item. Then it releases the spot, strolls a few blocks off and waits a short pause before it can claim
  again, so a waiting drekki gets the spot.
- A path that fails for a few seconds marks that trough unreachable for about a minute; it tries the next. With no
  usable trough it eats from its bag (with a feeding upgrade); otherwise it stays hungry, and its mood shows it.

How a meal looks (decided 2026-11-21): the drekki walks to its spot, steps onto the middle of it and turns to face the
trough, then plays the eating part of the page's feed loop in full, 4.2 seconds: head down, chew, lift, chew, swallow.
The food leaves the trough at the swallow. Then it steps away. The loop's first 2 seconds, a walk up to the feeder, are
for the showcase only; a live drekki has made that walk itself. Before, the game played those 2 seconds in place of the
eating, and the food went in an instant.

## Mood

A value from 0 to 100. A new drekki starts at 50. Rates are a proposal. Revised 2026-11-16: mood carries every
effect, in bands.

| Mood | Band | Effects |
|------|------|---------|
| 85-100, Recently pet and above half food | Thriving | One-off activities teach at 75%; each counted activity counts double |
| 75-100 | Happy | One-off activities teach at 50% (double) |
| 50-74 | Content | Normal (one-off activities teach at 25%) |
| 25-49 | Uneasy | No home or Hearty regeneration; one-off activities teach at 12.5% |
| 0-24 | Miserable | No home or Hearty regeneration, no one-off learning, 25% slower, no home terrain bonus |

- Rises: +1 a minute in its home terrain, +5 each time it eats, +3 for each hostile mob killed within 10 blocks
  of it (by it or by a player).
- Falls: -2 a minute while hungry, -1 for each 2 health it loses, -1 a minute while checked out and more than
  30 blocks from its owner.
- Recently pet (decided 2026-11-18, changed 2026-11-20): petting gives a buff for 1 hour of real time (72,000 ticks)
  that lifts the band one step (Miserable to Uneasy, Uneasy to Content, Content to Happy); petting again resets it.
  Happy goes to Thriving only from 85 with above half food, else it stays Happy; without the buff a drekki is never
  Thriving, so only active care reaches it. The mood value itself does not change. It shows as a buff icon on the
  drekki screen. (Was +10 mood, which often left the band where it was.)
- Kinship (decided 2026-11-18): +25/9 mood (about 2.8) for each other drekki whose home is the same hatchery, checked
  out or not, up to 9 others (+25). With 9 kin its mood never falls below 25, so it is never Miserable. A buff icon
  shows the count.
- Counted and death activities always teach, whatever the mood (unchanged; open whether Miserable stops them too).
- Cue: an icon floats above the drekki's head only while Uneasy or Miserable, drawn the way MineColonies draws its
  citizens' status icons (a camera-facing sprite above the entity). Nothing shows while Content or Happy. The icons
  read by color and shape at a distance: Uneasy is a solid amber bubble with a bold frown, Miserable a dark red bubble
  with a rain cloud, both with a near-black outline. The drekki
  screen shows the band, the value and the reasons (for example Hungry), see the UI tab on the design page.

## Look

- Body plan: a bipedal bird mount, from the chocobo (Final Fantasy) and the Seikret (Monster Hunter Wilds).
- Legs: reverse-jointed like a chocobo's. The thigh swings from the hip, the hock folds backward and the foot
  comes through high. Movements on the design page, at every stage: walk, run, jump, fall, glide with the wings
  spread flat, dash (only with the Four Winds or Sunrunner trait), sleep curled up like a dog, and feeding at a one-block feeder (the adult from the
  top opening, stages 1 and 2 from the side opening). A jump leaves the ground at once, as vanilla jumps do: the legs curl back with the toes
  down through the arc and snap forward under the body on landing, where it squashes.
- Lean toward a dragon pup, by way of Toothless and the Deadly Nadder (How to Train Your Dragon): wide flat head,
  fanned ear flaps, swept horns, dorsal spines, folded wings, rudder fins at the tail tip. Not Yoshi.
- Eyes: Minecraft style, as the pack's animals draw them. White sclera, a colored iris ring, and a pupil in a
  darker shade of the same color. The iris carries the astral color and is light emissive, like spider eyes.
  Before lock-in the eyes are Dormant: matte brown, no glow.
- Fur: one random muted color per egg (see Egg).
- Horns: several styles (swept, curled, antler, bull, crown). The style is set when the egg hatches (random from
  a trader egg, inherited within a bloodline, see Breeding) and never changes. Every stage shows that style, and
  the horns grow with the drekki. Stage 3 horns are built from several tapering
  segments so the curve of each style shows.
- Fangs are ivory. Horns and talons are ivory through stage 1, then ivory on the physical diets and ebony on the
  magical diets.
- Models are built at 16 px per block with whole-pixel cubes. Thin parts (straps, feathers) are flat planes.
- Herbivore tail plumage: a short feather fan on the hatchling that grows into long arching sickle feathers, in
  the spirit of a chocobo tail.
- Wings: both diets share one folded-wing frame that covers the flank from the shoulder, with the tip rising past
  the rump. Carnivore dresses it in leathery membrane panels. Herbivore dresses it in stepped
  coverts, a rising primary bundle, and U-shaped sickle plumes.
- Snout (stage 3): the upper muzzle hooks down into a narrow point just below the lower jaw.
- Head crest and dorsal line (stages 2 and 3): dark fins on carnivores along the head, neck, back and tail;
  light feather tufts with dark tips on herbivores in the same places. On stage 3 each fin or tuft has a long low
  base that runs along the body, so little bare back shows between them.
- Hats: equippable cosmetics, not from the egg. Ideas so far: top hat, drinking hat, viking helmet, wizard hat,
  straw hat, crown, party cone, bucket, cowboy hat, chef toque, miner helmet, pirate tricorne, propeller beanie,
  flower crown, jester cap. A hat hides the head crest; horns poke through it.
  - Every stage wears one: a hat slot on the drekki screen from stage 1, the hat scaled to the head. At stage 1 it
    is the only slot.
  - Crafted, shaped (rows split by `/`, `_` empty; checked against every vanilla 1.21.1 shaped recipe and each
    other, no overlaps):
    - Straw hat: `_ W _ / W W W` (wheat). Cowboy hat: the same shape in leather.
    - Chef toque: `W W W / W _ W` (white wool). Flower crown: the same shape in any small flowers.
    - Miner helmet: `I I I / I T I` (iron ingots, a torch).
    - Viking helmet: `B _ B / I I I / I _ I` (bones, iron ingots).
    - Party cone: `_ P _ / P D P` (paper, any dye).
    - Bucket: `S / B` (string over a bucket).
    - Drinking hat: `G H G` (glass bottles either side of a leather helmet).
  - Wandering trader only: crown, top hat, wizard hat, pirate tricorne, jester cap, propeller beanie. Each trader
    sells one of these at random, one use, for emeralds (price a proposal: 12).
- Previews: see artifacts.md in the repo root.

## Breeding (proposal)

Breeding two drekki gives an egg whose color is purer than the parents'.

- Every egg color is a bloodline (the nine muted colors). Each bloodline climbs toward its own pure color.
- Purity has four tiers: muted (a fresh egg), clear, vivid, pure. No tier carries a sheen.
- Sheen: only the rare and ultra rare lines glow. Their fur is partly lit from within in the line's own sheen color
  as on the design page. The purity tiers, pure included, have none. Every line adds the same amount of light (its
  strength is 0.06 over the glint color's linear luminance), so a pale gold glint and a dark violet one glow alike. The
  glow is added on top of the lit fur, as the page's emissive, the same by day and by night.
- Same bloodline: the child's tier is the lower parent tier plus one, capped at pure. Two pure parents give a pure
  child.
- Mixed bloodlines: the child's color is rolled from the whole hue wheel, any color at all, never one the
  player's stable already holds. Its tier is one below the lower parent's, never below muted: variety costs
  purity. No egg needs buying, so one stable breeds for purity (same line) or for variety (mixed). The child joins the nearest of the
  nine bloodlines for breeding purposes, so it can be purified like any other.
- Inherited within a bloodline: astral and horn style. Both parents the same gives the child that value,
  guaranteed. Different parents give one of the two at random. The child hatches with lit eyes, never Dormant.
- Mixed bloodlines reroll both: the child hatches Dormant and locks its astral by travel, like a trader egg, and
  its horn style is rolled at random.
- Not inherited: the diet and biome affinities (look and lock-in) come from how the child is raised. Biome traits
  pass like any other trait.
- Pure colors proposed per bloodline: Moss to green, Slate to sapphire, Rose to crimson, Sand to gold, Ash to
  silver, Clay to ember, Lavender to violet, Teal to teal, Soot to onyx with a cool glint.

### Pairing and tonics

Decided 2026-11-04. Recipes are proposals.

- Pairing happens in the hatchery screen: pick two of your drekki that live at that hatchery and press Pair. Both
  must be stage 3 and not hungry. Each parent then rests 3 in-game days before it can pair again. The egg goes
  straight into that hatchery and incubates as usual.
- The pairing screen has one tonic slot. A tonic is optional, crafted at a crafting table, and used up on Pair. Only
  one tonic fits a pairing, so a player chooses which roll to push.

| Tonic | Recipe | Effect |
|-------|--------|--------|
| Bloodline Tonic | glass bottle, golden apple, honey bottle | Positive traits pass at 50% instead of 33% |
| Heirloom Tonic | glass bottle, book, emerald | Pick one positive trait from either parent; it always passes. It still forms combos, and sleeps past the cap like any other passed trait |
| Clean Slate Tonic | glass bottle, milk bucket, sugar | Halves the negative odds: each negative passes at about 16%, and a clean pair's breeding-only roll drops to about 16% |
| Starlight Tonic | glass bottle, amethyst shard, glow berries | Pick which parent's astral the child takes, also for mixed bloodlines (whose child would otherwise hatch Dormant) |
| Purity Tonic | glass bottle, glow ink sac, diamond | The purity step is +2 tiers instead of +1, so pure takes two generations |
| Rarity Tonic | glass bottle, nether star, echo shard | A rare or ultra rare line takes on the first try (ultra rare is otherwise 1 in 16) |

### Rare lines (proposal)

Above pure. Pairing alone never reaches them: each needs a pure pair of the right line plus a hatch condition,
checked when the player hatches the Ready egg. The child carries a glowing sheen, which no purity tier has, and each line has its own
particle effect. The recipes constrain the parents only: the child is raised like any other, so its diet and biome
are its own, and its astral follows the parents. Every bloodline has at least one rare line (Moss, Slate and Rose
have two); the ultra rare lines are crosses of rare lines.

Each line needs its parents and a place to discover in the world (decided 2026-11-05). No line asks for an
astral: the child inherits its parents' eye color as usual. The ultra rare lines, all crosses, also
inherit one of the two parents' astral at random instead of hatching Dormant.

Detection: every structure place is a vanilla structure the world records at generation, so the check reads
`ServerLevel.structureManager()`: "inside" is `getStructureWithPieceAt` (the hatchery stands in one of its
pieces), "within N blocks" is a structure start in the nearby chunks whose bounding box, grown by N, holds the
hatchery. Structure tags (for example `rancher_companion:hatch/ruined_portal`, holding all seven ruined portal
variants) keep it open to modded structures. The record outlives the blocks, so a looted or broken structure still
counts; chunks generated before 1.21 have no trail ruins or trial chambers. Biome places read the biome at the
hatchery; the geode is a feature, not a structure, so it is checked by its blocks (budding amethyst), and so is the enchanting table (vanilla's own bookshelf check, all
15 in place).

- Glowberry (vivid green, pops warm orange-gold sparkles like glow berries): two pure Moss parents. The egg hatches in a lush caves biome, with moss blocks
  under the hatchery and a glow berry vine within 5 blocks.
- Drowned (deep sapphire, twinkles like stars): two pure Slate parents. The egg hatches within 16 blocks of ocean ruins (cold or
  warm).
- Pharaoh (radiant gold, white-hot glint, throws slow solar sparks): two pure Sand parents. The egg hatches within
  16 blocks of a desert pyramid, with sandstone or gold blocks under the hatchery.
- Bosun (deep ocean teal, wet shimmer, rises bubbles): two pure Teal parents. The hatchery is fully submerged (the block
  is waterlogged or has water touching it on all four sides; an air pocket does not count) within 16 blocks of a
  shipwreck.
- Amethyst (deep violet, pale crystal glint, drifts star-dust): two pure Lavender parents. The egg hatches inside a
  natural geode: budding amethyst within 5 blocks of the hatchery.
- Railwright (polished steel-silver all over, sheds tiny copper and silver flecks): two pure Ash parents. The egg hatches inside an abandoned mineshaft.
- Idol (sun-baked terracotta orange all over, like a clay temple idol, drifts tiny vine leaves): two pure Clay
  parents. The egg hatches within 16 blocks
  of a jungle temple.
- Ruin (obsidian black all over with a hint of purple, charcoal-black wings, sheds small fiery orange-red embers): two
  pure Soot parents. The egg
  hatches within 8 blocks of a ruined portal.
- Sakura (vivid blossom pink, soft pink glint, drifts falling cherry petals): two pure Rose parents. The egg hatches in a
  cherry grove.
- Brew (lime all over, small bubbles popping off it like a cauldron): two pure Moss parents.
  The egg hatches within 16 blocks of a witch hut.
- Summit (pale sky blue all over, white glint, drifts wisps of cloud): two pure Slate parents. The egg hatches at y 200 or
  higher in any peaks biome, with open sky above it.
- Prospector (brick red all over, sheds chips in several glaze colors): two pure Rose parents. The egg hatches within 16 blocks of trail ruins.

### Ultra rare lines (proposal)

Rule (decided 2026-11-05): every ultra rare line is a cross of two rare-line parents of different bloodlines, plus
its own place. Bakeneko is the one exception: a ghost drekki and any other drekki, no place. The design page shows
every ultra rare line's parents to all except Bakeneko's (a one-off); the place and the look stay behind the password.

Look rule (decided 2026-11-05): rare lines are single-tone, pure colors: body and trim are one color (the trim is the
body's own hue, a touch lighter). Ultra rare lines are twin-tone: a dark or a light body with a vivid trim in a second color. A line's
wing arms (the bones that join the wing) take the body color and the wing's large parts take the trim
(a carnivore's membranes and shanks, a herbivore's feathers), so the two tones show at every diet. The harder line
looks the harder.

The same rules as the rare lines, but the egg takes only one time in sixteen (the Rarity Tonic guarantees it) Listed from easiest to find without knowing the recipe to hardest:

- Tome (deep lapis-blue body, vivid enchanting-glint violet trim, violet glint, enchanting glyphs drift up off it): Brew
  and Amethyst parents. The egg hatches within 4 blocks of an enchanting table with all 15 bookshelves.
- Bakeneko (pale white body, charcoal-gray trim like a cat spirit, trails white wisps; a steady 35% see-through, less than
  a ghost's 65%, without the ghost's blue tint; its rider stays solid): a ghost drekki and any
  other drekki, paired. One egg in sixteen takes; the Rarity Tonic guarantees it. No place rule.
- Vault (deep burnished-copper body, vivid verdigris-green trim, warm copper glint, sheds tiny copper and gold flecks): Railwright
  and Prospector parents. The egg hatches inside trial chambers.
- Deep (deep navy body, vivid electric-blue trim, slow blue motes drift around it like light in the deep): Drowned and
  Bosun parents. The egg hatches fully submerged on the floor of a deep ocean biome, at y 30 or below.
- Prismarine (deep prismarine-teal body, vivid sea-lantern aqua trim, prismarine-teal glint, sheds prismarine shards): Bosun and Summit parents.
  The egg hatches within 32 blocks of an ocean monument.
- Weeping (near-black crimson body, vivid weeping-vine red trim, sheds red and amber embers): Ruin and Sakura parents. The egg hatches in a crimson
  forest, with crimson nylium under the hatchery. No mobs are needed.
- Basalt (dark basalt-gray body, molten orange trim like lava glowing through cracks, sheds flickering embers): Idol and Pharaoh parents. The egg hatches in
  the basalt deltas biome, with magma blocks under the hatchery.
- Bastion (blackstone-black body, vivid gold trim like the piglin vaults, sheds glinting gold flecks): Ruin and Pharaoh
  parents. The egg hatches inside a bastion remnant.
- Echo (near-black sculk-teal body, glowing sculk-cyan trim, trails teal smoke): Amethyst and Glowberry parents. The egg hatches inside an
  ancient city, where the hatchery may sit at light level 0: the one exception to its light rule.
- Purpur (pale end-stone lilac body, vivid purpur-violet trim, sheen drifts through every hue, glitters pastel confetti): any two rare-line parents of
  different bloodlines (the open cross). The egg hatches within 16 blocks of an End city.
- Glitch (black body, FF06B5 magenta trim, flickers glitch squares and scanlines in red and magenta): Summit and Sakura
  parents. The hatchery sits at x 19, y 83, any z, in any dimension (1983, an easter egg). Both numbers are
  server config values (`glitch_x`, `glitch_y`) that default to 19 and 83, so a server can move the spot.

### Tombstone

Decided 2026-11-20 (the pack has the Corpse mod; this mod does not depend on it):

- A drekki that dies for good (no ghost, no Phoenix Heart), or turns into a ghost, leaves a Drekki Tombstone where it fell,
  holding its worn gear: hat, barding and accessory (the bag keeps what it holds). Wearing nothing, it leaves none (the
  marker still shows where). The spot is the death spot or the nearest free one around
  and above it, on solid ground when it can be; with no free spot the gear drops as before.
- Model: a headstone of our own on a plinth, a stepped round top, a drekki's horned head carved on its face, a name line
  and a small purple crystal at its foot. Its item is the same 3D model.
- Right-click opens it in the mod's screen style (the trough's frame): "Here lies <name>", its three gear slots (take only)
  and "Died <date>". Anyone may take the gear (as the pack's corpses).
- It cannot be mined while it holds anything. Mined empty (pickaxe), it drops itself with the drekki's name and the real
  date it died (MM-dd-yyyy) on its tooltip; placed again it keeps them, and Jade's hover shows them.
- It keeps the drekki's record as it fell (2026-11-20): look (stage, bloodline, diet, astral, horn, hat, barding tier
  and dyes), life (traits, learning, mood, stats) and name, without the worn items (those are in the tombstone). Kept
  for a later mechanic and never shown: not in a tooltip, not sent to clients with the placed block; a mined tombstone
  item carries it and gives it back when placed.
- A death's tombstone vanishes 30 s (600 ticks) after it is emptied, the Corpse mod's default for an empty corpse; one
  a player places stays.
- Map marker: with JourneyMap installed, the owner gets a death waypoint where the tombstone stands, JourneyMap's death
  X in magenta ("<name> died"), like the player's own red one. An owner who is away gets it at the next login.

### Ghost drekki

Decided 2026-11-05:

- When a drekki dies, there is a 33% chance (raised from 15% on 2026-11-20) it turns into a ghost drekki instead of
  being lost.
- A ghost is like a normal drekki, but it cannot use abilities (so it has no cooldowns), cannot be mounted, and
  gives no area traits to players near it.
- It can still be checked in and out, and it can breed.
- Revised 2026-11-20: a ghost wears nothing and is mostly hands-off. Turning into a ghost puts its hat, barding and bag
  in a tombstone where it died, with the owner's death marker (see Tombstone). Its screen opens read-only (traits,
  health, stance shown; gear slots and stance locked, "A ghost does not fight." on the stance buttons). It follows its
  owner and takes name tags; no petting, hats, hand feeding or carrier-free changes of gear, and it never fights. It
  does not get hungry or eat (troughs, pack meals), so its food stays where it was until it is revived. Carriers, check
  in and out, breeding and the revive steps work as before.
- Only its owner ever sees a ghost (2026-11-20): the server sends it to its owner alone, so other players never see,
  hear or touch it; the soul leaving it is the owner's alone too. Revived, everyone sees it again. No mob targets a
  ghost, and a mob already after it lets go.
- A ghost cannot die again (2026-11-20): nothing hurts it (fire, lava, falls, drowning, players). Only /kill removes it,
  gone for good; one that falls out of the world comes back to its owner (when out with them) or home.
- Traits: it keeps every trait, but none work while it is a ghost, and it cannot learn new ones. They all return
  on revive, and they pass in breeding as usual.
- Revive, like curing a zombie villager: hit it with a splash potion of Weakness, then give it a golden apple. It
  shakes and turns back into a normal drekki after 3 minutes. The apple is used up; it is not food for any diet.
- Look: its own fur and eyes, 65% see-through (more than a Bakeneko) with a faint pale-blue tint and a slow bob, with small
  soul-fire particles drifting off it.
- Phoenix Heart always wins: a Phoenix Heart drekki returns as its egg, and no ghost roll happens.
- Pairing a ghost with any other drekki gives a Bakeneko child one time in sixteen (see Ultra rare lines). Tonics
  work as usual, so the Rarity Tonic guarantees it.

Other ways in, for later: a rare Peculiar Egg from the wandering trader that is already pure; a biome capstone
trait that unlocks a line; a named egg as an easter egg.

## Combat

Stances (decided 2026-11-17), for stage 2 and 3; stage 1 never fights and is never targeted. Set on the drekki
screen; a new stage 2 drekki starts Reactive.

No player hurts a drekki (decided 2026-11-21): not its owner, not another player, by hand, tool, arrow, spell (Ars
Nouveau, Iron's Spells) or a splash of a harmful potion, whatever the server's PvP or a mod's friendly fire setting. Any
damage whose source or direct entity is a player lands for no damage, so its push (knockback) still moves the drekki,
a pup too; it wears no barding, starts no fight and gives no kill credit. A harmful effect a player applies is refused. An owner's area
spells kept hitting her own drekki. Monsters and the rest of the world still hurt it.

Barding of every tier gives Hostile Knockback Immunity (decided 2026-11-21; its own blue line in the tooltip): a
mob's hit does not push a drekki that wears barding. A player's push still moves it.

Safety (decided 2026-11-21): with no rider, lava and falls never hurt a drekki, and lava does not set it on fire. No
Create machine hurts it either: a train running it over, a drill, a saw, crushing wheels, a roller, a fan's fire (every
damage type of Create and its addons, by the `create...` namespace). It
keeps out of lava and fire all the same: with no rider it never walks into either, whatever the detour. A catch-up or the whistle lands it on any
spot that is not itself on fire or in lava: lava beside the spot or below it does not rule the spot out. The exception is a drekki lava cannot harm anyway (immune by nature, or under Fire
Resistance from a trait or a potion): it may walk and land there. One knocked into lava floats, to get out. Because a
fall cannot hurt it, it takes drops of up to 24 blocks to reach its owner (the game's mobs stop at 3).

- Aggressive: fights any hostile mob within 10 blocks, and everything Reactive answers. Hostile means a monster;
  neutral mobs (endermen, zombified piglins, wolves, bees) only while they are angry. It leaves creepers alone, as
  wolves do. A checked-in drekki stays in its wander area, so it fights what comes into the stable.
- Reactive: fights whatever hits it or its owner, and whatever its owner attacks (the wolf rule).
- Passive: never deals damage in a fight. It still uses its defensive abilities: Rejuvenate, Steel edge, and
  defensive traits (Heal pulse, Arrow guard, Bubble, Phase ward, Thorns).
- Every stance: players only on a server with PvP on. A ridden drekki picks no fights; its rider steers it. A
  negative trait that forces a fight (Crimson temper) overrides the stance.

Diet picks the position (where it fights from) and the two abilities. Damage is relative to Bite until balancing:

- Raw (physical damage): stays in melee range and closes the distance with Pounce.
  - Bite: melee attack. It reaches as far as the game's melee mobs do (a zombie's rule), and a bite begun in reach
    lands 6 ticks later even if the drekki was knocked back meanwhile (decided 2026-11-21).
  - Pounce: jumping attack from up to 5 blocks away, also usable close up. 2x Bite damage. 15 s cooldown.
- Cooked (magical damage): stays at max range.
  - Elemental ball: ranged, slightly less damage than Bite. A target too close for a ball in flight is hit at once.
  - Elemental breath: a cone up to 4 blocks that hits everything in it, slightly more damage than the ball. 15 s cooldown.
- Seed (physical support): stays in melee range, goes to the player to use Steel edge.
  - Bite: melee attack.
  - Steel edge: +2 attack damage for every player within 10 blocks, for 5 s. 15 s cooldown. The drekki itself does
    not take it.
- Root (magical defense): stays at the ball's max range, goes to the player to use Rejuvenate.
  - Elemental ball: ranged, slightly less damage than Bite.
  - Rejuvenate: heals itself and every player within 10 blocks, and gives very slow regeneration for 5 s. 15 s
    cooldown.
- Stage 1 has no attacks. Bite and Elemental ball start in stage 2. The cooldown abilities (Pounce, Elemental
  breath, Steel edge, Rejuvenate) only activate in stage 3.
- In its astral home terrain every cooldown is halved.

No trait sets anything on fire: a burning mob lights the player and the ground. Heat damage, knockback and smite
stand in. Traits use vanilla status effects wherever one fits.

Baseline for every drekki, no trait needed: one-block step like a horse, catch-up teleport when more than 24
blocks behind (decided 2026-11-20, eased 2026-11-21: only to a spot with room and firm ground that is not itself in lava or fire,
unless fire cannot hurt it, as with Fire Resistance; with no such spot it stays put; it arrives unhurt, with no fall
carried over and no fire; the server then sends it to players as new, not as a move: owners saw no drekki after a
catch-up while other players did, 2026-11-21), comes along to another dimension with its owner (on the game's dimension change, and when the
owner joins another level, which catches portal mods that skip that event, as Immersive Portals in the Haven pack does;
no timer; 2026-11-20. After a level change it appears on a spot its owner stood on there, once they are 2 blocks
past it (decided 2026-11-21: a spot a player walked over is on the right side of the portal, in the open and in their
path; spots picked around the owner landed in the frame, behind it and out of sight). Until then it is kept as data. An
owner who does not walk within 3 seconds gets it by the catch-up rule. The server looks each tick only while an owner
waits. An owned drekki never goes through a portal itself and is never placed in a portal block: it
looks 2 to 3 blocks from its owner, then up to 6, first for a spot the owner can see (a clear line from their eyes), and while the owner still stands in the portal, only on the
owner's side of it: one landed behind the frame, walled off, 2026-11-21. One left on its owner's block, on the portal's surface, was carried
back through by Immersive Portals and stuck in the frame, 2026-11-21), jumps to its owner from any distance after 2 seconds with no way to walk to them, always when a catch-up put it there,
and otherwise when it is 3 blocks or more above or below them (a ledge, a pit, a cave under the floor; one that walked
up to a closed door on their level waits, so the door keeps it out; 2026-11-21), never tramples crops, and from stage 2 its pack picks up what the player cannot carry.

## Breed traits (proposal)

The pattern names below (Hunter, Brawler, Warden, Medic) predate the diet abilities. Hunter and Brawler map to
Raw and Cooked, Warden and Medic to Seed and Root, pending a rewrite.

Pure tier gives one bloodline trait, each with a travel half (with an aura for the player within about 8 blocks
while it follows) and a fight-or-fix half. Breed traits sit in their own slot.

- Moss, Treewalker: foliage never slows it or the player / Heal pulse: after combat, Regeneration I for the player
  and tamed animals nearby.
- Slate, Night stride: Speed at night, Night Vision after dark for both / Phantom ward: phantoms do not swoop near it.
- Rose, High step: two-block ledges without jumping, Jump Boost for the player / Cleave: its attacks hit every
  mob in a short arc.
- Sand, Sure-footed: no slowdown on sand, soul sand or gravel, Speed on open ground for both / Creeper sense: shoves
  creepers away from the player, Resistance against blasts for both.
- Ash, Snowstrider: powder snow, ice, no freezing, for both / Frost bite: its hits apply Slowness II; stray arrows
  give the player no Slowness.
- Clay, Cinder tread: magma and campfires harmless, fast on netherrack, short Fire Resistance when the player
  catches fire / Thorns: attackers take the damage back, no ignition.
- Lavender, Featherfall: no fall damage for either, Slow Falling for the player on drops over three blocks / Ender
  calm: endermen never aggro the player near it.
- Teal, Skimmer: runs on water while sprinting, rider dry, Dolphin's Grace for the player / Arrow guard:
  blocks arrows and tridents aimed at the player when beside them.
- Soot, Darkstalker: hostiles notice it at half range and the player at three-quarters in the dark, faster below
  light level 7 / Night guard: a mob that hits the player in the dark takes triple from its next bite.

Rare and ultra rare lines keep the parent bloodline's trait and add a travel passive plus one ability per diet,
each in a pattern:

- Glowberry, Canopy leap (extra jump for both). Hunter Pounce: leaps on the mob that hit the player from eight
  blocks, knocks it down, marks it Glowing. Medic Thicket: a ring around the player, Regeneration inside, Slowness
  and Weaving outside.
- Drowned, Moonstride (Night stride doubled, night glide, Speed and Night Vision aura after dark). Hunter
  Moonfang: at night Strength II on itself, bites apply Weakness. Medic Lullaby: at night, hostiles within eight
  blocks get Slowness IV and Weakness for a few seconds.
- Basalt, Lavawalker (walks on lava like a strider, both fireproof, Fire Resistance aura, lava within four blocks
  holds the player). Brawler Ember breath: a cone of heat damage, no ignition. Warden Heat shield: attackers that
  hit the player are knocked back and take a burst of heat damage, no ignition.
- Pharaoh, Sunrunner (fast in daylight, dash on command, Speed aura in daylight). Hunter Solar lance: smites
  undead with bonus damage in daylight, no burning. Medic Dawn flare: Blindness on mobs around the player, Instant
  Health for the player.
- Bosun, Deep diver (ridden underwater, Conduit Power aura). Hunter Riptide: lunges through water and drags the
  target under. Warden Bubble: an Absorption shell on the player that eats projectiles.
- Echo, Shade (both unseen below light level 7). Hunter Reprisal: whatever hits the player in the dark takes
  triple from its next bite and gets Darkness. Warden Veil: when the player is hit, Invisibility on them for a few
  seconds.
- Purpur, Prism (both parents' travel traits and auras, Luck for the player). Brawler Splinter: hits fork into three.
  Warden Refract: turns projectiles aimed at the player back at the shooter.
- Bakeneko, Drift (Levitation on command for lift, no fall damage, Slow Falling aura, floats up to meet the player).
  Hunter Haunt: attacks ignore armor and apply Weakness. Warden Phase ward: once a minute, lethal damage to the
  player becomes a burst of Absorption.
- Glitch, Surface clip (runs on water and lava alike, any liquid within four blocks holds the player). Brawler
  Afterimage: two glitch copies that bite for ten seconds. Warden Afterimage: two copies that taunt and burst into
  Nausea and a stun when killed.

## Negative biome traits

Decided 2026-11-04; each biome category's drawback, in the sheet's Negative Trait column. A drekki holds at most
one negative trait; see the inheritance rule under Traits. Breeding clears negatives by chance: a bred child with no
negative gets Naturally Gifted and stays clean for life. A drekki that already holds one never gains its biome's
negative at lock-in.

- Snow & Ice, Burn-prone: takes 50% more fire damage.
- Plains, Claustrophobic: under a roof (no sky above) its attacks deal 20% less damage.
- Dry, Water-logged: after leaving water it cannot jump for 3 s.
- Forest, Fire-fearful: refuses to go within 4 blocks of fire, lava, campfires or lit furnaces.
- Taiga, Winded: after each dash it cannot jump for 2 s. Kept as is (decided 2026-11-05): a drekki that inherits
  it without a dash trait is not affected.
- Jungle, Cold-blooded: in snowy and cold biomes it cannot use its cooldown abilities.
- Wet, Bog-heavy: jumps 20% lower.
- Hills & Mountains, Thin-air lungs: runs out of breath underwater 25% sooner.
- Water & Coast, Salt-dry: in dry biomes its hunger meter drains 25% faster.
- Ocean, Storm-skittish: in thunderstorms each thunderclap or lightning strike throws its rider off.
- Cave, Sun-dazzled: in direct sunlight its attacks miss 15% of the time.
- Nether Forest, Crimson temper: when hurt it rages for 5 s, ignoring its owner and attacking whatever is nearest.
- Nether Barren, Ember-hungry: its hunger meter drains twice as fast in the Overworld and the End.
- End, Blood-thirsty: its natural regeneration is halved.
- End Exploration, Untapped Respiration: after 15 s of flight it must land and rest 10 s.

No negative is a plain slowdown: the user rejected those.

## Sound

A dragon pup: chirps and growls rather than roars, pitched up and lighter at stage 1 and 2 (pitch 1.7, 1.3, 1;
level 0.55, 0.8, 1). The design page and the mod play the same files on the same loop phases, one cell at a time on
the page and muted until asked. Cues marked "rendered" are the page's synthesizer rendered to files; the rest are the
game's own recordings.

- Walk, feed walk: a light step (rendered) each foot plant. Run: a mid step, a huff every other step.
- Dash: mid steps louder in the burst, a whoosh and a short growl at the burst, wind while it lasts.
- Jump: a grunt and an upward whoosh at take-off, one wing beat, a heavy step on landing.
- Fall: faint wind while it drops, a heavy step and a small yelp on landing.
- Glide: steady wind, a soft whoosh on each wing rock, a call every 3.5 s.
- Sleep: a light step as it lies down, a nose sigh, a panda nose exhale on each breath out, a snore every third.
- Feed: a sniff at the trough, the game's eating in time with the jaw, then softer chews. No swallow.
- Bite: the fox bite (bites 1 and 3) timed so the jaws meet as ours shut, and a trident pierce on the impact.
- Pounce: a leap whoosh, a wing beat and a push-off step, soft feet on landing, the bite and a pierce as the jaws hit.
- Elemental ball: the ghast fireball and fire ignite with a short breath burst on the spit; the explosion (cut to 1 s)
  with a long breath burst on impact.
- Elemental breath: an inhale, a growl, then a looping hiss with a low rumble that fades out.
- Steel edge: a heavy stomp with a shield block, iron armor going on and an amethyst chime, then waves of a chime and
  a chainmail rattle while the buff lasts.
- Rejuvenate: azalea leaves and bone meal with a high chime, then waves of two low chimes over softer leaves.

The game's sound engine cannot filter, plays pitch only from 0.5 to 2 and volume only up to 1. What the page needs
past that is baked into the mod's files (and the page plays the same files): the bite per stage at its speed and tone
(the bigger stages slower, duller and fuller), the chime two octaves up for high notes, the fire sounds an octave up.
The page caps each sound at the game's full volume and fires cues on 20 ticks a second, as the game does. Only the
game's distance falloff is not on the page.

## Extensibility

Decided 2026-11-05:

- Biome categories are biome tags, one per category (for example `rancher_companion:teaches/ocean`). The mod ships
  tags for every vanilla biome and may include common tags (`c:is_ocean`), so a datapack or mod adds a modded biome
  by adding it to a tag.
- Other developers must be able to code their own activity traits, affinities, biome traits (positive and
  negative) and rare line colors, through NeoForge custom registries and events:
  - Registries: `rancher_companion:trait` (activity, biome positive and negative, breed and poly traits),
    `rancher_companion:affinity` and `rancher_companion:rare_line` (colors, sheen, particles, parents). A mod
    registers its own entries with a `DeferredRegister`, the NeoForge way.
  - Our own content registers through the same registries, so the API stays complete.
  - Activity triggers: a small public API, for example `DrekkiApi.progress(player, trait, amount)`, counts an
    activity for the player's checked-out drekki and applies the learning rules (checkout, range, mood, chance).
  - Events on the NeoForge bus so mods can react: trait learned, affinity lock-in, egg hatched.
  - The API lives in its own package so other mods can compile against it.

## Open questions

- Trader hats: the mod adds one offer (a random one of the six hats, one use, 12 emeralds) to the wandering trader's
  generic pool, so not every trader has one. The rule above wants one per trader: move it to the egg's way of adding
  its offer once that trade is built.
- Barding: first pass on the design page (Model tab, Barding): a breastplate with shoulder plates, two bands per
  neck segment with a throat plate, and a face and brow plate; each tier a plate color with a darker rim (wood,
  leather brown, stone, iron, gold, diamond, netherite). Review the shapes, then port them to the rig.
  Enchanted barding shows the enchantment glint (a violet shimmer across the plates; the page's Enchanted toggle).
  Open: which enchantments barding accepts, and how (enchanting table, anvil, or both).
- Effect numbers for every trait and ability.
- Poly eyes: one color per eye on the design page and in the rig's eye sheet.
- Railwright, Idol, Ruin, Sakura, Brew, Summit, Prospector, Prismarine, Vault, Tome, Deep and Bastion: their travel passives and
  abilities (see Breed traits).
- Ghost drekki: port the design page's ghost look (65% see-through, pale-blue tint, bob, soul fire) to the mod.
  Bakeneko is a steady 35% see-through, without the tint; its rider and barding stay solid.

## Teaser release (0.2.0)

- Ships: every block with its recipe, the trough's food and dye behavior, the dyeable blocks, and the wandering
  trader's egg (6 eggs, one per trader visit). The hatchery is inert.
- The hats, barding, carriers and tonics stay registered (they show in the creative tab), but nothing makes them: their
  recipe files carry `"neoforge:conditions": [{"type": "neoforge:false"}]`, and the trader hat offer
  (`ItemHooks.addTraderHats`) is not registered. Remove the condition and restore the listener to bring them back.

## Pup release (0.3.0, decided 2026-11-17)

Hatch a first egg, keep the pup, carry it, name it, pet it. Growth and learning stay off: every drekki is stage 1.

- Hatching: the egg goes into the hatchery by right-clicking the hatchery with it. Incubation shows no progress in a
  screen; the egg item's tooltip says where it is (also on hover in the hatchery): up to 1 day "The egg is warm.", up
  to 2 days "The egg is making noises.", up to 3 days "The egg is really active!", after 3 days "The egg is ready to
  hatch!". A hatch places the pup in the world at once (Placement rule) and adds it to the owner's roster.
- Names: a hatchling has no name. Messages say "Your drekki"; the roster shows its bloodline. A name tag names it,
  in an anvil or with the pack's quick rename (ctrl + right-click).
- Creative eggs: "Creative Egg (Day 1..4)" stacks, the egg at that point of incubation, in the creative tab and JEI
  only (no recipe), for testing.
- Release: on the hatchery screen, a selected drekki can be released to the wild after a confirm step that names it.
  It poofs on the spot (smoke, no walk off) and drops its hat; its record is deleted from the roster.
- Small carrier only (the large one keeps `neoforge:false`). A drekki in a carrier does nothing and learns nothing.
  Out of the carrier, a checked-out pup follows its owner and teleports when too far.
- Stage 1 never attacks and mobs never target it. It takes no damage at all (players, mobs, explosions, falls, lava,
  fire, cactus, drowning), so it needs no healing; only the void and /kill reach it (decided 2026-11-18). A pup hurt
  before 0.3.2 heals to full.
- Drekki screen (0.3.x, built for review): its owner right-clicks it with an empty hand or any item that has no use of its own on a drekki (a weapon, a block, food; not a carrier, hat, name tag, lead or spawn egg). The hat slot (its hat: in
  puts it on, out takes it off; a dim straw hat when empty, no label), the model, the name with the stage beside it, then
  health hearts, bloodline, astral ("Astral: Dormant"), diet, the player's inventory below;
  pixel style like the hatchery and trough screens. A hat in hand still goes on with a click.
- Petting: sneak + right-click with an empty hand (the arm swings, as on any use). The drekki is in `#c:capturing_not_supported`, so Carry On (whose
  default blacklist reads that tag) and other capture mods leave it alone: the roster keeps it, nothing else carries it. A happy sound, hearts, a short lean-in. From 0.4.0 it gives Recently pet (see Mood).
- Screens (hatchery, trough): pixel art in the mod's palette after Create's own screens (frogport, stock link), not
  the leather look. The trough gets an inventory screen; the drekki has no hunger meter yet.
- Hats: no recipes; every wandering trader offers 2 different random hats (of all 15), 3 of each. Barding has its
  recipes back and takes dyes (vanilla's armor dye, fan dyeing and bleach). Tooltips name the kind under the item's
  name: Drekki Hat, Drekki Barding, Drekki Carrier, Drekki Tonic, Drekki Egg.
- JEI (optional): every barding tier with each dye shows as a crafting recipe (display only; real dyeing mixes).
- Ponder (optional, comes with Create): build guides. Hatchery: a stable (the roof rule, light, the egg, the hatch,
  the wander area: anchors, chaining, fences, 20 per hatchery). Trough: a line of 10 and the 11th apart, the four
  diets, and feeding (open side for stages 1 and 2, the top for stage 3, one drekki per trough at a time). Feeding
  itself comes with hunger, after this release; the guide shows the rules it will follow.

## Growth release (0.4.0, decided 2026-11-18)

Pups grow, eat, feel and learn; growth stops at stage 2 (Fledgling) until a later release.

- Growth: travel time counts (out of the carrier, checked out); 6 days per stage (was 7 until 2026-11-20), server config. Existing drekki
  start at 0, no head start. A stage 2 drekki does not grow further yet. Stage-up: a chat line to the owner ("Your
  drekki is now a Fledgling") and a particle burst.
- Astral: locks 50% through stage 1 (see Astral affinity). A chat line to the owner names it (hover for what it
  does), with a burst of its color and the amethyst chime.
- Diet: counted from every meal in stage 1, locked at stage 2 with its look.
- Hunger, troughs and mood: as in their sections. Jade (optional, `compat/jade`): a row of shanks under the hearts.
- Biome: stage 2 counts time per biome category from now on; the lock-in and its traits come later.
- Every biome counts (decided 2026-11-18): each category is a biome tag (`rancher_companion:teaches/<category>`, a
  datapack can move biomes). Modded biomes go to the category they look like (Haven's mods: Biomes O' Plenty, Ars
  Nouveau and Elemental, Climate Rivers, Create Abyss go by their look; Cave takes BOP's grottoes and nests, Ars
  Elemental's vexing caves and the abyss). Space by look, to revisit with mod support: Mars to Dry, Glacio to Snow &
  Ice, Venus and Mercury to Nether Barren, the Moon, orbit and open space to End Exploration. A biome in no tag falls
  back on its `c:` tags (snowy, desert, forest...), then on Plains (the rule for any biome nothing names), so no
  time is lost.
- Astral off the three vanilla dimensions (decided 2026-11-18): by look too, as dimension tags
  (`rancher_companion:astral/<name>`): Mars, Venus and Mercury Underworld, the Moon Lunar, Glacio Aero, orbit and
  space End; any other dimension uses the Overworld's height and day rules.
- Traits: a drekki learns at every stage, up to its stage cap: 3 as a pup, 6 as a Fledgling (decided 2026-11-18). A trait that needs riding or a
  later stage is learned now and works once that exists; its tooltip says so. Frost Strider: the drekki walks on
  powder snow too.
- Stage 2 (Fledgling): the large carrier's recipe, a 9-slot pack, the three stances (see Combat) with its diet's
  basic attack: Bite 4 damage, Elemental ball 3 (starting numbers, to tune). It takes damage.
- Death (stage 2 on): a red chat line to the owner. A 15% chance it returns as a ghost drekki (see Ghost drekki); a
  ghost still grows, never fights in this release (whatever its stance), and has the spec's look and revive. A ghost
  that dies is gone for good, as is a drekki whose roll fails. Phoenix Heart wins over both.
- Ponder: the trough scenes show the spots (front, back, ends), owner-only troughs and diet planning (keep diets
  apart to steer a hatchling).
- Drekki screen (as built): the name over two tabs, Details and Traits (the name only since 2026-11-20: no stage, no ghost tag). Details: hearts (ten to a row), ten
  food shanks, bloodline, astral, diet, and the mood band in its color (hover: band name, effect, reasons; no number), then the buff
  icons, Recently pet (effect, gold Duration and red "Ends in" lines, as a trait's cooldown) and Kinship (kin and bonus). Traits: one line per slot of its stage, learned ones
  in green with their effect on hover, the offer in gold with a check and a cross, free ones dim; no learning progress. From stage 2 the three stance buttons sit
  at the foot of the info well (icons from the design page, tooltip each). The
  top is 220 wide so trait names fit Minecraft's font on one line; the pack and the inventory keep vanilla's 176, centered, and the pack row (9) over the inventory.
  A ghost's stage reads "Fledgling, ghost".
- Hand feeding: its owner right-clicks it with food it eats while it has room for it or is hurt; otherwise the screen opens.
  Petting with sneak + empty hand also starts Recently pet. A ghost under Weakness takes a golden apple.
- Undertow reaches 10 blocks across at any depth (fixed 2026-11-18): a drekki stays at the surface while its owner
  dives, so a 10-block reach in all directions missed a dive deeper than about 10 blocks below it. It starts at the
  player's first drowning damage, not at 2 air bubbles (decided 2026-11-18: it gives Water Breathing, so it waits
  until the player is actually drowning).
- Cloud Stompers counts a slime landing from the player's tick too, once per landing: some pack mods take over slime
  landings and the game's fall event never fires (seen in the Haven pack).
- Traits help their owner only (decided 2026-11-18), within 10 blocks; Night Watch and Creature Comforts act on the
  drekki's surroundings (phantom spawns, villagers near it) and work for everyone there. The Fallen Emperor was
  owner-only already.
- Cooldowns are each trait's own number, halved in its astral home terrain (the astral tooltip's promise, now done).
  Tooltips show them as a gold line in real-world time ("Cooldown: 2h 20m" for Patchwork's 7 game days); a trait that
  is recharging shows dimmer with a small gold hourglass on the traits tab, and a red "Ready in 1m 12s" line.
- Trait and astral tooltips are one builder (trait/Tips) for the screen and chat; in chat the client draws them as the
  screen does (title, then wrapped to at most 4 rows). The details tab shows the astral name in its eye color with the
  same tooltip as the chat message when it settles.
- Food (fixed 2026-11-18): the shank bar rounds up to half a shank, so an empty shank shows exactly when it eats at a
  trough (a whole shank missing). Hand feeding works whenever the bar is not full (half a shank or more missing) or the drekki is hurt. Food it will not take says why on the
  action bar ("Not hungry", "Eats Seed food only") instead of opening the screen.
- Patchwork is learned when an item wears through (its durability runs out), not when the last item of a stack is
  used up (fixed 2026-11-18: the game's item-destroyed event fires for both).
- Tooltips on the mod's screens use the game's text size: a title line, then each line wrapped at the narrowest width
  from 170 px that keeps it to 4 rows (wider only when needed). Nothing is shortened.
- Ponder's stable scene is the design page's decor booth (Blocks tab), open toward the viewer, the hatchery in a back
  corner; each anchor's 3-block square is drawn on its own, so the area shows as their union.
- Hearty is learned by a Fledgling (a pup is never hurt): 5 heals from outside the mod while hurt, 30 s apart.
- Performance: trait hooks look only at the live drekki holding that trait (an index rebuilt when one joins, leaves or
  changes); kin are counted per hatchery as records change; the learner is found by its roster id.
- Tests: a smoke test of every learnable trait (its learning trigger and, where it works now, its effect), and
  screenshots of each changed screen, tooltip and Ponder scene.
