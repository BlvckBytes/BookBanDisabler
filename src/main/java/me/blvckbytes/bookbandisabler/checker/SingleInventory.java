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

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SingleInventory implements IInventory {

  private final Inventory inventory;
  private final @Nullable Runnable writeFunction;
  private boolean dirty;

  public SingleInventory(Inventory inventory, @Nullable Runnable writeFunction) {
    this.inventory = inventory;
    this.writeFunction = writeFunction;
  }

  @Override
  public int getSize() {
    return this.inventory.getSize();
  }

  @Override
  public @Nullable ItemStack getItem(int slot) {
    return this.inventory.getItem(slot);
  }

  @Override
  public void setItem(int slot, @Nullable ItemStack item) {
    this.inventory.setItem(slot, item);
    this.dirty = true;
  }

  @Override
  public void write() {
    if (!this.dirty)
      return;

    if (this.writeFunction != null)
      this.writeFunction.run();

    this.dirty = false;
  }
}
