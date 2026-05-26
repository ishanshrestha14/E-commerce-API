package com.codewithmosh.store.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductMapper productMapper;
    @InjectMocks ProductService productService;

    @Test
    void getAllProducts_noCategoryNoSearch_callsFindAllWithCategory() {
        var product = new Product();
        var dto = new ProductDto();
        when(productRepository.findAllWithCategory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toDto(product)).thenReturn(dto);

        var result = productService.getAllProducts(null, null, 0, 20, "name", "asc");

        assertThat(result.content()).containsExactly(dto);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(productRepository).findAllWithCategory(any(Pageable.class));
    }

    @Test
    void getAllProducts_withCategoryFilter_callsFindByCategoryId() {
        var categoryId = (byte) 1;
        var product = new Product();
        var dto = new ProductDto();
        when(productRepository.findByCategoryId(eq(categoryId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toDto(product)).thenReturn(dto);

        var result = productService.getAllProducts(categoryId, null, 0, 20, "name", "asc");

        assertThat(result.content()).containsExactly(dto);
        verify(productRepository).findByCategoryId(eq(categoryId), any(Pageable.class));
        verify(productRepository, never()).findAllWithCategory(any(Pageable.class));
    }

    @Test
    void createProduct_invalidCategoryId_returnsNull() {
        var dto = new ProductDto();
        dto.setCategoryId((byte) 99);
        when(categoryRepository.findById((byte) 99)).thenReturn(Optional.empty());

        var result = productService.createProduct(dto);

        assertThat(result).isNull();
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_validCategory_savesAndReturnsDto() {
        var category = new Category();
        var dto = new ProductDto();
        dto.setCategoryId((byte) 1);
        var product = new Product();

        when(categoryRepository.findById((byte) 1)).thenReturn(Optional.of(category));
        when(productMapper.toEntity(dto)).thenReturn(product);

        var result = productService.createProduct(dto);

        verify(productRepository).save(product);
        assertThat(result).isEqualTo(dto);
    }

    @Test
    void deleteProduct_productNotFound_returnsFalse() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        var result = productService.deleteProduct(999L);

        assertThat(result).isFalse();
        verify(productRepository, never()).delete(any());
    }

    @Test
    void deleteProduct_productFound_deletesAndReturnsTrue() {
        var product = new Product();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        var result = productService.deleteProduct(1L);

        assertThat(result).isTrue();
        verify(productRepository).delete(product);
    }
}
