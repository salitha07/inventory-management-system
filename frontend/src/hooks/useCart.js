import { useState, useCallback } from "react";
import {
  addItemToCart,
  removeItemFromCart,
  updateItemQuantity,
  increaseItemQuantity,
  decreaseItemQuantity,
  clearCartItems,
  calculateCartTotals,
} from "../utils/cartOperations";

/**
 * Custom hook to manage shopping cart state in the POS module.
 *
 * @param {Array} [initialItems=[]] - Initial cart items
 * @returns {Object} Cart state and mutation methods
 */
export function useCart(initialItems = []) {
  const [cart, setCart] = useState(initialItems);
  const [discount, setDiscount] = useState(0);

  const addToCart = useCallback((product, quantity = 1) => {
    setCart((prevCart) => addItemToCart(prevCart, product, quantity));
  }, []);

  const removeFromCart = useCallback((productId) => {
    setCart((prevCart) => removeItemFromCart(prevCart, productId));
  }, []);

  const updateQuantity = useCallback((productId, newQuantity) => {
    setCart((prevCart) => updateItemQuantity(prevCart, productId, newQuantity));
  }, []);

  const increaseQuantity = useCallback((productId, step = 1) => {
    setCart((prevCart) => increaseItemQuantity(prevCart, productId, step));
  }, []);

  const decreaseQuantity = useCallback((productId, step = 1) => {
    setCart((prevCart) => decreaseItemQuantity(prevCart, productId, step));
  }, []);

  const clearCart = useCallback(() => {
    setCart(clearCartItems());
    setDiscount(0);
  }, []);

  const totals = calculateCartTotals(cart, discount);

  return {
    cart,
    discount,
    setDiscount,
    addToCart,
    removeFromCart,
    updateQuantity,
    increaseQuantity,
    decreaseQuantity,
    clearCart,
    subtotal: totals.subtotal,
    totalItemsCount: totals.totalItemsCount,
    appliedDiscount: totals.discount,
    rawDiscount: totals.rawDiscount,
    totalAmount: totals.totalAmount,
    isDiscountExceeded: totals.isDiscountExceeded,
  };
}
