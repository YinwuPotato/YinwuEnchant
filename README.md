# YinwuEnchant — Custom Enchantment System

21 custom enchantments registered via Paper Registry.

## Enchantment List

| Enchantment | Effect | Slot |
|------------|--------|------|
| Clearsight | Negate darkness | Helmet |
| Darkspeed | Speed in dark areas | Boots |
| Safefall | Reduce fall damage | Leggings |
| Sonic Boom | Charge and fire sonic wave | Chestplate |
| Undermine | Faster mining below sea level | Tools |
| Resonate | Shield reflect damage | Shield |
| Shrieker Sense | Highlight wardens/sculk with spyglass | Spyglass |
| Cat's Paw | Scare creepers | Boots |
| Nasus | Scare skeletons | Helmet |
| Phantom Protection | Repel phantoms | Chestplate/Elytra |
| Master of Beef Slicing | Extra meat drops (Looting+Fire Aspect synergy) | Sword |
| Harvest | Right-click harvest crops | Hoe |
| Smelt | Auto-smelt mined blocks | Tools |
| Soulbound | Keep items on death | All |
| Lava Walker | Walk on lava (5s revert) | Boots |
| Emerald Till | Chance for emeralds from grass | Hoe |
| Step Up | Increased step height | Boots |
| Airbag | Reduce Elytra collision damage | Elytra |
| Bless | Totem triggers teleport to spawn | Chestplate |
| Vampire Curse | [Cursed] Burn in sun, regen at night | Helmet |
| Insomnia | [Cursed] Cannot sleep | Helmet |

## Features

- Vanilla mutual exclusion via `exclusiveWith()`
- All Folia thread-safe with correct cross-region scheduling
- Registered via `RegistryEvents.ENCHANTMENT.compose()`

## Tech Stack

- Java 21, Paper API 1.21+, Folia compatible
- PluginBootstrap + RegistryComposeEvent
- PDC + vanilla enchantment dual storage
