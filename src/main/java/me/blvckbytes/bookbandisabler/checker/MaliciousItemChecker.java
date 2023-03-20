/*
 * MIT License
 *
 * Copyright (c) 2023 BlvckBytes
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.blvckbytes.bookbandisabler.checker;

import me.blvckbytes.utilitytypes.Tuple;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MaliciousItemChecker implements IMaliciousItemChecker {

  /*
    totalCount packetSize delta
    2_592_081  2_727_602  135_521

    ~320 wide UTF8 characters per page
    320 * 100 = 32_000 characters total
    3 bytes per character, 96_000 bytes total
    27 slots * 96_000 = 2_592_000

    1.17   2_097_152
    1.17.1 8_388_608
   */
  // There can be quite some overhead (not yet fully identified), thus we rather play it safe
  private static final int MAX_TOTAL_UTF_BYTES_PER_INVENTORY = 1_400_000;

  @Override
  public void checkItem(Player player, @Nullable ItemStack item, Runnable removalFunction) {
    if (item == null)
      return;

    ItemMeta itemMeta = item.getItemMeta();

    if (itemMeta == null)
      return;

    IInventory containerInventory = getContainerInventory(item, itemMeta);

    if (containerInventory != null)
      checkInventory(player, containerInventory);
  }

  private @Nullable IInventory getContainerInventory(ItemStack item, ItemMeta itemMeta) {
    if (itemMeta instanceof BlockStateMeta) {
      BlockStateMeta blockStateMeta = (BlockStateMeta) itemMeta;
      BlockState blockState = blockStateMeta.getBlockState();

      if (blockState instanceof Container) {
        Inventory containerInventory = ((Container) blockState).getInventory();

        return new SingleInventory(containerInventory, () -> {
          blockStateMeta.setBlockState(blockState);
          item.setItemMeta(itemMeta);
        });
      }
    }

    return null;
  }

  private int scanInventory(IInventory inventory, List<Tuple<Integer, Runnable>> removalFunctions, List<IInventory> encounteredInventories) {
    encounteredInventories.add(inventory);

    int totalByteCount = 0;
    int inventorySize = inventory.getSize();

    for (int i = 0; i < inventorySize; i++) {
      ItemStack item = inventory.getItem(i);

      if (item == null)
        continue;

      ItemMeta itemMeta = item.getItemMeta();

      IInventory containerInventory = getContainerInventory(item, itemMeta);

      if (containerInventory != null) {
        totalByteCount += scanInventory(containerInventory, removalFunctions, encounteredInventories);
        continue;
      }

      if (!(itemMeta instanceof BookMeta))
        continue;

      List<String> pages = ((BookMeta) itemMeta).getPages();
      int byteCount = getTotalUtf8ByteCount(pages);

      totalByteCount += byteCount;

      int slot = i;
      removalFunctions.add(new Tuple<>(byteCount, () -> {
        inventory.setItem(slot, null);
      }));
    }

    return totalByteCount;
  }

  @Override
  public void checkInventory(Player player, IInventory inventory) {
    List<Tuple<Integer, Runnable>> removalFunctions = new ArrayList<>();
    List<IInventory> encounteredInventories = new ArrayList<>();

    int totalByteCount = scanInventory(inventory, removalFunctions, encounteredInventories);

    int numberOfRemovedBooks = 0;
    while (totalByteCount >= MAX_TOTAL_UTF_BYTES_PER_INVENTORY) {
      int maxIndex = -1;
      int maxByteCount = -1;

      int size = removalFunctions.size();
      for (int i = 0; i < size; i++) {
        Tuple<Integer, Runnable> entry = removalFunctions.get(i);
        int byteCount = entry.a;

        if (byteCount > maxByteCount) {
          maxByteCount = byteCount;
          maxIndex = i;
        }
      }

      if (maxByteCount < 0)
        break;

      removalFunctions.remove(maxIndex).b.run();

      totalByteCount -= maxByteCount;
      numberOfRemovedBooks++;
    }

    if (numberOfRemovedBooks > 0) {
      player.sendMessage("§cRemoved " + numberOfRemovedBooks + " malicious books");

      for (IInventory encounteredInventory : encounteredInventories)
        encounteredInventory.write();
    }
  }

  /**
   * Get the total number of bytes that would be required to byte[]-serialize
   * all pages as UTF-8 strings, including the leading byte-count var-int
   */
  private int getTotalUtf8ByteCount(List<String> pages) {
    int byteCount = 0;

    for (String page : pages) {
      int length = page.length();
      char currentChar;

      for (int i = 0; i < length; i++) {
        currentChar = page.charAt(i);

        if ((currentChar >= 0x0001) && (currentChar <= 0x007F)) {
          byteCount++;
          continue;
        }

        if (currentChar > 0x07FF) {
          byteCount += 3;
          continue;
        }

        byteCount += 2;
      }
    }

    /*
      1 byte = 8 bit, var-int: first bit indicates if there's another byte
      2^(7*1) = 128
      2^(7*2) = 16_384
      2^(7*3) = 2_097_152
      2^(7*4) = 268_435_456
     */

    if (byteCount < 128)
      byteCount += 1;
    else if (byteCount < 16_384)
      byteCount += 2;
    else if (byteCount < 2_097_152)
      byteCount += 3;
    else
      byteCount += 4;

    return byteCount;
  }
}
