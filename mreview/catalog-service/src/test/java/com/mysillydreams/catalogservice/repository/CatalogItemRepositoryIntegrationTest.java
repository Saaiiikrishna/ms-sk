package com.mysillydreams.catalogservice.repository;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;


import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CatalogItemRepositoryIntegrationTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private CategoryEntity electronicsCategory;
    private CategoryEntity booksCategory;

    @BeforeEach
    @Sql("/sql/delete-all-data.sql") // Clean before each test
    void setUp() {
        electronicsCategory = categoryRepository.save(CategoryEntity.builder()
                .name("Electronics")
                .type(ItemType.PRODUCT)
                .path("/electronics/")
                .build());
        booksCategory = categoryRepository.save(CategoryEntity.builder()
                .name("Books")
                .type(ItemType.PRODUCT)
                .path("/books/")
                .build());
    }

    @Test
    void whenSaveCatalogItem_thenSuccess() {
        CatalogItemEntity laptop = CatalogItemEntity.builder()
                .category(electronicsCategory)
                .sku("LAPTOP-001")
                .name("Super Laptop")
                .description("A very fast laptop")
                .itemType(ItemType.PRODUCT)
                .basePrice(new BigDecimal("1200.99"))
                .metadata(Map.of("ram", "16GB", "storage", "512GB SSD"))
                .active(true)
                .build();
        CatalogItemEntity savedItem = catalogItemRepository.save(laptop);

        assertThat(savedItem).isNotNull();
        assertThat(savedItem.getId()).isNotNull();
        assertThat(savedItem.getSku()).isEqualTo("LAPTOP-001");
        assertThat(savedItem.getCategory().getName()).isEqualTo("Electronics");
        assertThat(savedItem.getMetadata()).containsEntry("ram", "16GB");
    }

    @Test
    void whenFindBySku_thenSuccess() {
        CatalogItemEntity item = CatalogItemEntity.builder().category(electronicsCategory).sku("UNIQUE-SKU-01").name("Test Item").itemType(ItemType.PRODUCT).basePrice(BigDecimal.TEN).build();
        catalogItemRepository.save(item);

        Optional<CatalogItemEntity> found = catalogItemRepository.findBySku("UNIQUE-SKU-01");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Item");
    }

    @Test
    void whenSaveItemWithDuplicateSku_thenThrowsException() {
        CatalogItemEntity item1 = CatalogItemEntity.builder().category(electronicsCategory).sku("DUP-SKU-001").name("Item 1").itemType(ItemType.PRODUCT).basePrice(BigDecimal.ONE).build();
        catalogItemRepository.save(item1);

        CatalogItemEntity item2 = CatalogItemEntity.builder().category(booksCategory).sku("DUP-SKU-001").name("Item 2").itemType(ItemType.PRODUCT).basePrice(BigDecimal.TEN).build();
        assertThrows(DataIntegrityViolationException.class, () -> {
            catalogItemRepository.saveAndFlush(item2);
        });
    }

    @Test
    void whenSaveItemWithNullCategory_thenThrowsException() {
        CatalogItemEntity item = CatalogItemEntity.builder().sku("NO-CAT-SKU").name("No Category Item").itemType(ItemType.PRODUCT).basePrice(BigDecimal.ONE).build();
        assertThrows(DataIntegrityViolationException.class, () -> {
            catalogItemRepository.saveAndFlush(item);
        });
    }

    @Test
    void findByCategoryId_returnsMatchingItems() {
        catalogItemRepository.save(CatalogItemEntity.builder().category(electronicsCategory).sku("ELEC-001").name("Laptop").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("1000")).build());
        catalogItemRepository.save(CatalogItemEntity.builder().category(electronicsCategory).sku("ELEC-002").name("Mouse").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("25")).build());
        catalogItemRepository.save(CatalogItemEntity.builder().category(booksCategory).sku("BOOK-001").name("Java Programming").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("50")).build());

        Page<CatalogItemEntity> electronicsItemsPage = catalogItemRepository.findByCategoryId(electronicsCategory.getId(), PageRequest.of(0, 10));
        assertThat(electronicsItemsPage.getContent()).hasSize(2);
        assertThat(electronicsItemsPage.getContent()).extracting(CatalogItemEntity::getName).containsExactlyInAnyOrder("Laptop", "Mouse");

        Page<CatalogItemEntity> booksItemsPage = catalogItemRepository.findByCategoryId(booksCategory.getId(), PageRequest.of(0, 10));
        assertThat(booksItemsPage.getContent()).hasSize(1);
        assertThat(booksItemsPage.getContent().get(0).getName()).isEqualTo("Java Programming");
    }

    @Test
    void findActiveItemsByCategoryPath_returnsItemsFromCategoryAndSubcategories() {
        CategoryEntity programmingBooks = categoryRepository.save(CategoryEntity.builder()
                .name("Programming Books")
                .parentCategory(booksCategory)
                .type(ItemType.PRODUCT)
                .path("/books/programming/")
                .build());
        booksCategory.addChildCategory(programmingBooks);
        categoryRepository.save(booksCategory);


        catalogItemRepository.save(CatalogItemEntity.builder().category(booksCategory).sku("BOOK-GEN-001").name("General Book").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("30")).active(true).build());
        catalogItemRepository.save(CatalogItemEntity.builder().category(programmingBooks).sku("BOOK-PROG-001").name("Specific Programming Book").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("70")).active(true).build());
        catalogItemRepository.save(CatalogItemEntity.builder().category(programmingBooks).sku("BOOK-PROG-002").name("Inactive Programming Book").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("60")).active(false).build());
        catalogItemRepository.save(CatalogItemEntity.builder().category(electronicsCategory).sku("ELEC-CON-001").name("Console").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("300")).active(true).build());


        Page<CatalogItemEntity> itemsInBooksPath = catalogItemRepository.findActiveItemsByCategoryPath(booksCategory.getPath(), PageRequest.of(0, 10));
        assertThat(itemsInBooksPath.getContent()).hasSize(2)
                .extracting(CatalogItemEntity::getName)
                .containsExactlyInAnyOrder("General Book", "Specific Programming Book");

        Page<CatalogItemEntity> itemsInProgrammingBooksPath = catalogItemRepository.findActiveItemsByCategoryPath(programmingBooks.getPath(), PageRequest.of(0, 10));
        assertThat(itemsInProgrammingBooksPath.getContent()).hasSize(1)
                .extracting(CatalogItemEntity::getName)
                .containsExactly("Specific Programming Book");
    }
}
