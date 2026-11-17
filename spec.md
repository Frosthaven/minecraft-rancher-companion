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

Open: recipes (a draft on the page), and whether bedding does anything for checked-in drekki.

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
  - Beside the hatchery that leaves 3 blocks of air above the floor, which fits a standing stage 3 (2.3 tall). A
    ridden stage 3 is about 3.3 tall.
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

The drekki grows with travel time. Travel is elective: travel time counts whenever the drekki is out in the world,
and the player decides by carrying it or letting it out. Time in a carrier counts for nothing.

### Carriers

Two craftable items:

- Small carrier, shaped: `S L S / L W L / S _ S` (4 sticks, 4 leather, 1 wool of any color). No iron, so a new
  player can make one right after the hatch.
- Large carrier, shaped: `I L I / L C L / I _ I` (4 iron ingots, 3 leather, 1 small carrier). An upgrade: a drekki
  in the small carrier stays inside.
- Small drekki carrier: holds a stage 1 drekki.
- Large drekki carrier: holds a stage 2 drekki.
- Stage 3 cannot use a carrier at all.
- Right click a drekki with an empty carrier of the right size to put it inside. The carrier is then an item in the
  inventory with the drekki in it. A name-tagged drekki keeps its name.
- Right click the ground with a full carrier to let the drekki out. The carrier is empty again (assumption).

### Stage 1

- Length: 14 game days of travel.
- Size: very small, about a slime.
- Fits the small carrier.
- No attacks, no cooldown abilities.

### Stage 2

- Length: 14 game days of travel.
- Size: about a wolf.
- Needs the large carrier.
- Has its diet's basic attack (Bite or Elemental ball), no cooldown ability.

### Stage 3

- Length: permanent (fully grown).
- Size: a horse, or slightly smaller.
- Can be mounted and ridden like a horse.
- Every ability. No built-in glide or slow fall: flight comes only from the Wings of Hope trait (elytra-style
  while ridden, dive to gain speed, pull up to climb), so only a ridden stage 3 flies.

## Drekki screen

Right click the drekki at any stage to open its screen. It shows the drekki's inventory and a 3D model. Revised
2026-11-17 (design page, UI tab): one view, no tabs. The model, bloodline, astral, diet, biome, the mood band (its name in
its color; the value, effects and reasons such as "Hungry: -2 a minute" in a hover tooltip), the equipment slots the
stage has, then the traits (each with a hover tooltip saying what it does; the negative marked; sleeping traits named),
then the pack from stage 2 (nothing in its place at stage 1). No learning progress is shown.
Revised 2026-11-16, laid out like a Sophisticated Backpacks screen: the drekki part on top (equipment slots: hat; saddle
and barding at stage 3; the model; details or traits, with Drekki and Traits tabs), then its pack, then the player's
inventory and hotbar, so items move between them by click and shift-click. Equipment slots take only their own item.

Health and food: under the model, as vanilla's HUD draws a player's: a row of hearts (full, half, empty; ten to a
row, so a stage 3 drekki's extra hearts wrap) and a row of ten drumstick shanks for the hunger meter. Max health per
stage is open (the page uses 10, 20 and 30 as placeholders).

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
- Stage 2: a pack of 9 slots (one row).
- Stage 3: a pack of 27 slots (three rows), a saddle slot (needed to ride) and a barding slot.
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

## Affinities

Each affinity changes in real time until it locks in. After lock-in it is permanent.

| Affinity | Driven by                    | Values               | Locks in            | Shows on the model                         |
|----------|------------------------------|----------------------|---------------------|--------------------------------------------|
| Astral   | Where stage 1 travel happens | Dormant, then Solar, Lunar, Aero, Underworld, Deep, End | 25% through stage 1 | Eye color |
| Diet     | What the drekki eats         | Carnivore raw or cooked, Herbivore seed or root | Start of stage 2 | Talons, wings, tail plumage |
| Biome    | Time spent in each biome     | Traits per biome     | 50% through stage 2 | Foot tint                                  |

### Astral affinity

- Starts Dormant: matte brown eyes with no glow. The glow starts at lock-in.
- Gained in traveling mode only: a tick counts only when it also counts as travel time (see Growth), so time at
  the stable or near a bed shapes nothing. Each travel tick in the first quarter of stage 1 counts toward one of
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
  number of times) and death activities (die to a piglin, survive with a totem) always teach it. Mood scales the
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
  learn ("has too much on its mind").
- Learning message: when a drekki learns a trait, its owner and every other player within 30 blocks of the drekki
  get a chat message, "<name> has learned [<trait>]",
  where the trait name in brackets shows the trait's tooltip on hover (name and effect), the way advancement messages
  do. An unnamed drekki is "Your drekki" to
  its owner and "<owner>'s drekki" to everyone else.
- At the cap the trait is not learned, and the same players get "<name> wanted to learn something, but has too much on its
  mind." instead.
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
  - Stamp Out puts the fire out and gives 5 s of Fire Resistance; Huddle Up thaws the player and stops freezing for
    5 s. Both keep the 2-minute cooldown.
  - Patchwork (was Jerryrigging) covers every item with durability.
  - Hide & Seek: ghasts ignore every player within 10 blocks of the drekki; fireballs already fired still hit.
  - Mount controls: the jump key jumps, double jumps (Cloud Stompers) and third jumps (Launch Protocol); the sprint
    key or a double tap of forward dashes (Four Winds).
  - Creature Comforts stacks with Hero of the Village, not with other drekki.
  - Cloud Stompers is learned by bouncing on slime blocks 10 times.
  - Night Watch (added by the team): kill 5 phantoms. Phantoms cannot spawn within 30 blocks of the drekki
    horizontally, at any height.
  - Soft Landing (added by the team): learned from taking fall damage. When a player within 10 blocks of the drekki
    would fall more than 6 blocks, the drekki casts Slow Falling on them. 2-minute cooldown.
  - Undertow (added by the team as Deep Sea Diver): learned by running air down to the last bubble (25% each time).
    When a player within 10 blocks of the drekki is down to 2 air bubbles, a current lifts them fast to the surface
    like a soul sand bubble column, with Water Breathing while they rise. It cannot pull through a solid ceiling;
    with no open water above, they get 10 s of Water Breathing instead. 5-minute cooldown.
  - Hearty (added by the team): learned by healing the drekki 5 times with potions or spells (any heal from
    outside the mod: `LivingHealEvent`, so spell mods need no compat code). A heal counts only while the drekki is
    missing health, at most once per 30 s; home regeneration, Draconic Vampirism and Rejuvenate do not count.
    Effect: +6 max health (3 hearts), and the drekki regenerates 1 health every 10 s everywhere, 1 every 5 s in its home
    terrain. Hunger still stops all regeneration; Blood-thirsty halves it.
  - Hibernation Plan slows both the player's hunger and the drekki's (see Hunger).
  - Phoenix Heart works on every death and the trait is kept. The egg goes to the owner's inventory when they are
    online with room, otherwise it is held and given on their next login or at the hatchery. It is never dropped.

Effect numbers, written into the sheet's rows (starting points, to tune in testing). `snowy_beach` is in Snow & Ice only:

- Balm & Salve: only harmful effects that milk cures; beneficial effects are kept.
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
  Spawn spots (above) must lie in the area too. It never wanders into unloaded chunks. Their area traits (Homes & Gardens, Creature
  Comforts and the like) work there, under the no-stacking rule.
- Checkout, check-in and swaps happen only at a hatchery screen, and only for drekki that live at that hatchery
  (revised 2026-11-16): one living elsewhere shows Check out, Pair and Trade disabled ("Lives at <name>: check it out
  there"); Move… brings it over first. A drekki with no hatchery can be checked out or moved from any hatchery.
- Placement (revised 2026-11-16), one rule for every time a drekki appears at a hatchery (check-out, check-in, a move,
  a hatch, the roster placing a missing one): within 3 blocks of the hatchery block, preferring right next to it. A spot
  fits when it stands on a solid block, out of liquid, with room for the stage's hitbox (stage 3 needs 2x3x2 clear).
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
  the drekki out; anyone else gets "This drekki belongs to <owner>." So a carrier is never a way around a trade.
- Every trade is written to the server log (both players, both drekki). Server config `allow_trading`, default true.

## Hunger

Decided 2026-11-04, revised 2026-11-16: hunger has no penalties of its own. It lowers mood, and mood carries every
effect (see Mood), so other systems can soften or sharpen it later.

- The meter drains slowly while the drekki travels or fights, about one diet food per in-game day.
- While hungry, mood falls 2 a minute. Eating gives +5 mood and ends the drain. It never starves to death.
  Hibernation Plan makes the meter drain 33% slower.
- Food: the player's hand at any time. A checked-out drekki on the road eats from its pack. A drekki at home
  (checked in) goes to the troughs first, then its pack.

### Feeding at troughs (proposal 2026-11-16)

- A hungry drekki looks for a trough when it is not fighting, ridden or following its owner on the move.
- Search area: a checked-in drekki searches its hatchery's area (see Checkout, Wandering), not around itself, so every
  trough of the stable counts wherever it has wandered. A trough counts only if its eating spot lies inside the area,
  within 3 blocks of a path, bedding or the hatchery; place troughs along the paths. A checked-out drekki
  searches 16 blocks around itself, and only while its owner stands still.
- A trough counts when it is loaded, reachable and free. Reachable: stages 1 and 2 need a standing spot in front of
  the open side; stage 3 eats from the top at any side that is not blocked. Free: each trough segment serves one
  drekki at a time. A drekki claims a segment, walks to it, eats, and releases it, so a line of 10 feeds 10.
- Before the diet locks (stage 1) any trough with food counts and it picks the nearest; each meal counts toward its
  diet, so placing a diet's trough nearest the hatchery steers a hatchling. After the lock, only troughs holding its
  own diet count, still the nearest.
- A meal is one item. A short pause follows before it can claim again, so others get a turn.
- A path that fails for a few seconds marks that trough unreachable for about a minute; it tries the next. With no
  usable trough it eats from its pack; with an empty pack it stays hungry, and its mood shows it.
- Open: whether any drekki may eat from any trough, or only drekki of the trough's owner.

## Mood

A value from 0 to 100. A new drekki starts at 50. Rates are a proposal. Revised 2026-11-16: mood carries every
effect, in bands.

| Mood | Band | Effects |
|------|------|---------|
| 75-100 | Happy | One-off activities teach at 50% (double) |
| 50-74 | Content | Normal (one-off activities teach at 25%) |
| 25-49 | Uneasy | No regeneration; one-off activities teach at 12.5% |
| 0-24 | Miserable | No regeneration, no one-off learning, 25% slower, no home terrain bonus |

- Rises: +1 a minute in its home terrain, +5 each time it eats, +3 for each hostile mob killed within 10 blocks
  of it (by it or by a player).
- Falls: -2 a minute while hungry, -1 for each 2 health it loses, -1 a minute while checked out and more than
  30 blocks from its owner.
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

### Ghost drekki

Decided 2026-11-05:

- When a drekki dies, there is a 15% chance it turns into a ghost drekki instead of being lost.
- A ghost is like a normal drekki, but it cannot use abilities (so it has no cooldowns), cannot be mounted, and
  gives no area traits to players near it.
- It can still be checked in and out, and it can breed.
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
  - Bite: melee attack.
  - Pounce: jumping attack from up to 5 blocks away, also usable close up. 2x Bite damage. 15 s cooldown.
- Cooked (magical damage): stays at max range.
  - Elemental ball: ranged, slightly less damage than Bite.
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
blocks behind, never tramples crops, and from stage 2 its pack picks up what the player cannot carry.

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
- Stage 1 never attacks and mobs never target it.
- Drekki screen (0.3.x, built for review): its owner right-clicks it with an empty hand. The hat slot (its hat: in
  puts it on, out takes it off), the model, bloodline, stage, eyes, diet and health hearts, the player's inventory below;
  pixel style like the hatchery and trough screens. A hat in hand still goes on with a click.
- Petting: sneak + right-click with an empty hand (the arm swings, as on any use). The drekki is in `#c:capturing_not_supported`, so Carry On (whose
  default blacklist reads that tag) and other capture mods leave it alone: the roster keeps it, nothing else carries it. A happy sound, hearts, a short lean-in. Later a small mood gain
  with a cooldown (no mood yet).
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
