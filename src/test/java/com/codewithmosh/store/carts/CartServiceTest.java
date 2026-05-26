package com.codewithmosh.store.carts;

import com.codewithmosh.store.products.Product;
import com.codewithmosh.store.products.ProductNotFoundException;
import com.codewithmosh.store.products.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock CartMapper cartMapper;
    @Mock ProductRepository productRepository;
    @InjectMocks CartService cartService;

    @Test
    void createCart_savesNewCartAndReturnsDto() {
        var expectedDto = new CartDto();
        when(cartMapper.toDto(any(Cart.class))).thenReturn(expectedDto);

        var result = cartService.createCart();

        verify(cartRepository).save(any(Cart.class));
        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    void addToCart_cartNotFound_throwsCartNotFoundException() {
        var cartId = UUID.randomUUID();
        when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addToCart(cartId, 1L))
                .isInstanceOf(CartNotFoundException.class);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addToCart_productNotFound_throwsProductNotFoundException() {
        var cartId = UUID.randomUUID();
        when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.of(new Cart()));
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addToCart(cartId, 1L))
                .isInstanceOf(ProductNotFoundException.class);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addToCart_happyPath_savesCartAndReturnsItemDto() {
        var cartId = UUID.randomUUID();
        var cart = mock(Cart.class);
        var product = new Product();
        var cartItem = new CartItem();
        var expectedDto = new CartItemDto();

        when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cart.addItem(product)).thenReturn(cartItem);
        when(cartMapper.toDto(cartItem)).thenReturn(expectedDto);

        var result = cartService.addToCart(cartId, 1L);

        verify(cartRepository).save(cart);
        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    void updateItem_itemNotInCart_throwsProductNotFoundException() {
        var cartId = UUID.randomUUID();
        var cart = mock(Cart.class);
        when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.of(cart));
        when(cart.getItem(1L)).thenReturn(null);

        assertThatThrownBy(() -> cartService.updateItem(cartId, 1L, 3))
                .isInstanceOf(ProductNotFoundException.class);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void removeItem_cartFound_removesItemAndSavesCart() {
        var cartId = UUID.randomUUID();
        var cart = mock(Cart.class);
        when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.of(cart));

        cartService.removeItem(cartId, 1L);

        verify(cart).removeItem(1L);
        verify(cartRepository).save(cart);
    }
}
