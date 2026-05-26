package com.codewithmosh.store.products;

import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Cacheable(
        value = "products",
        key = "#page + '-' + #size + '-' + #sortBy + '-' + #sortDir + '-' + #categoryId + '-' + #search"
    )
    public PagedResponse<ProductDto> getAllProducts(
            Byte categoryId, String search, int page, int size, String sortBy, String sortDir
    ) {
        var sort = sortDir != null && sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy != null ? sortBy : "name").descending()
                : Sort.by(sortBy != null ? sortBy : "name").ascending();
        var pageable = PageRequest.of(page, size, sort);

        boolean hasCategory = categoryId != null;
        boolean hasSearch = search != null && !search.isBlank();

        Page<Product> products;
        if (hasCategory && hasSearch) {
            products = productRepository.findByCategoryIdAndNameContainingIgnoreCase(categoryId, search, pageable);
        } else if (hasCategory) {
            products = productRepository.findByCategoryId(categoryId, pageable);
        } else if (hasSearch) {
            products = productRepository.findByNameContainingIgnoreCase(search, pageable);
        } else {
            products = productRepository.findAllWithCategory(pageable);
        }

        return PagedResponse.from(products.map(productMapper::toDto));
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
