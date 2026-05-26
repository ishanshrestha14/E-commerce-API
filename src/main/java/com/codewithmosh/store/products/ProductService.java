package com.codewithmosh.store.products;

import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Cacheable(value = "products", key = "'list-' + #categoryId")
    public List<ProductDto> getAllProducts(Byte categoryId) {
        if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId)
                    .stream().map(productMapper::toDto).toList();
        }
        return productRepository.findAllWithCategory()
                .stream().map(productMapper::toDto).toList();
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductDto createProduct(ProductDto productDto) {
        var category = categoryRepository.findById(productDto.getCategoryId()).orElse(null);
        if (category == null) return null;

        var product = productMapper.toEntity(productDto);
        product.setCategory(category);
        productRepository.save(product);
        productDto.setId(product.getId());
        return productDto;
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductDto updateProduct(Long id, ProductDto productDto) {
        var category = categoryRepository.findById(productDto.getCategoryId()).orElse(null);
        if (category == null) return null;

        var product = productRepository.findById(id).orElseThrow(ProductNotFoundException::new);
        productMapper.update(productDto, product);
        product.setCategory(category);
        productRepository.save(product);
        productDto.setId(product.getId());
        return productDto;
    }

    @CacheEvict(value = "products", allEntries = true)
    public boolean deleteProduct(Long id) {
        var product = productRepository.findById(id).orElse(null);
        if (product == null) return false;
        productRepository.delete(product);
        return true;
    }
}
