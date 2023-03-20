# BookBanDisabler

This plugin tries to disable the so-called "book banning" edge case, which is achieved by filling a
writable book to the brim with UTF-8 characters, chosen to take the most bytes, in order to make it blow
up in size. These books are then duplicated and filled into various containers like ordinary chests,
shulker boxes and the like, which - when transmitted over the network - blow read/write limiters, that
cause an exception in the player's netty pipe, which then again closes the connection. Item entities
in chunks, items in the inventory, opened containers, etc. can all cause this disconnect and thus, if
done right, cause the player to be unable to join again without being disconnected almost instantly.

## Countermeasures

### Block Break

When a container block is about to be broken, it's inventory is checked.

### Inventory Open

When an inventory is about to be opened, it is checked.

### Item Drop

When an item is about to be dropped, it is checked.

### Item Pickup

When an item is about to be picked up, it is checked. The player inventory is checked at the next tick, to
ensure that if the player already holds malicious items, the total size of the inventory update will still
end up below the threshold. This is a countermeasure against kicking players by giving them books incrementally.

### Joining

When a player is about to join the server, their inventory is checked.