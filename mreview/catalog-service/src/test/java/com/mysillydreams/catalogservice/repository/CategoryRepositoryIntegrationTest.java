package com.mysillydreams.catalogservice.repository;

import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CategoryRepositoryIntegrationTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void whenSaveCategory_thenSuccess() {
        CategoryEntity electronics = CategoryEntity.builder()
                .name("Electronics")
                .type(ItemType.PRODUCT)
                .path("/electronics/")
                .build();
        CategoryEntity savedElectronics = categoryRepository.save(electronics);

        assertThat(savedElectronics).isNotNull();
        assertThat(savedElectronics.getId()).isNotNull();
        assertThat(savedElectronics.getName()).isEqualTo("Electronics");
        assertThat(savedElectronics.getCreatedAt()).isNotNull();
        assertThat(savedElectronics.getUpdatedAt()).isNotNull();
    }

    @Test
    void whenFindById_thenSuccess() {
        CategoryEntity books = CategoryEntity.builder().name("Books").type(ItemType.PRODUCT).path("/books/").build();
        categoryRepository.save(books);

        Optional<CategoryEntity> found = categoryRepository.findById(books.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Books");
    }

    @Test
    void whenSaveCategoryWithNullName_thenThrowsException() {
        CategoryEntity category = CategoryEntity.builder().type(ItemType.PRODUCT).build();
        assertThrows(DataIntegrityViolationException.class, () -> {
            categoryRepository.saveAndFlush(category); // saveAndFlush to trigger constraints immediately
        });
    }

    @Test
    void whenSaveDuplicateName_thenThrowsException() {
        CategoryEntity cat1 = CategoryEntity.builder().name("UniqueCat").type(ItemType.PRODUCT).path("/uniquecat/").build();
        categoryRepository.save(cat1);

        CategoryEntity cat2 = CategoryEntity.builder().name("UniqueCat").type(ItemType.SERVICE).path("/uniquecat_alt/").build();
        // Unique constraint on name in CategoryEntity
        assertThrows(DataIntegrityViolationException.class, () -> {
            categoryRepository.saveAndFlush(cat2);
        });
    }

    @Test
    void testFindByParentCategoryIsNull() {
        CategoryEntity parent = CategoryEntity.builder().name("Parent").type(ItemType.PRODUCT).path("/parent/").build();
        categoryRepository.save(parent);

        CategoryEntity child = CategoryEntity.builder().name("Child").type(ItemType.PRODUCT).parentCategory(parent).path("/parent/child/").build();
        parent.addChildCategory(child); // This sets the bidirectional relationship
        categoryRepository.save(parent); // Save parent again to persist child due to cascade (or save child explicitly)


        List<CategoryEntity> topLevelCategories = categoryRepository.findByParentCategoryIsNull();
        assertThat(topLevelCategories).hasSize(1);
        assertThat(topLevelCategories.get(0).getName()).isEqualTo("Parent");
         assertThat(topLevelCategories.get(0).getChildCategories()).hasSize(1);
    }

    @Test
    void testFindDescendantsByPath() {
        CategoryEntity root = CategoryEntity.builder().name("Root").type(ItemType.PRODUCT).path("/root/").build();
        categoryRepository.save(root);

        CategoryEntity child1 = CategoryEntity.builder().name("Child1").type(ItemType.PRODUCT).parentCategory(root).path("/root/child1/").build();
        root.addChildCategory(child1);
        categoryRepository.save(root); // Save root to cascade save child1 (or save child1 separately)


        CategoryEntity grandchild1 = CategoryEntity.builder().name("Grandchild1").type(ItemType.PRODUCT).parentCategory(child1).path("/root/child1/grandchild1/").build();
        child1.addChildCategory(grandchild1);
        categoryRepository.save(child1); // Save child1 to cascade save grandchild1

        List<CategoryEntity> descendants = categoryRepository.findDescendantsByPath(root.getPath(), root.getId());
        assertThat(descendants).hasSize(2).extracting(CategoryEntity::getName).containsExactlyInAnyOrder("Child1", "Grandchild1");

        List<CategoryEntity> child1Descendants = categoryRepository.findDescendantsByPath(child1.getPath(), child1.getId());
        assertThat(child1Descendants).hasSize(1).extracting(CategoryEntity::getName).containsExactly("Grandchild1");
    }

    @Test
    @Sql("/sql/delete-all-data.sql") // Clean up before this test if needed, or manage state carefully
    void testFindByParentCategoryIdAndName_whenExists_returnsCategory() {
        CategoryEntity parent = categoryRepository.save(CategoryEntity.builder().name("Parent Category").type(ItemType.PRODUCT).path("/parentcat/").build());
        CategoryEntity child = categoryRepository.save(CategoryEntity.builder().name("Child Category").parentCategory(parent).type(ItemType.PRODUCT).path("/parentcat/childcat/").build());

        Optional<CategoryEntity> found = categoryRepository.findByParentCategoryIdAndName(parent.getId(), "Child Category");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(child.getId());
    }

    @Test
    @Sql("/sql/delete-all-data.sql")
    void testFindByParentCategoryIdAndName_whenNotExists_returnsEmpty() {
        CategoryEntity parent = categoryRepository.save(CategoryEntity.builder().name("Another Parent").type(ItemType.PRODUCT).path("/anotherparent/").build());
        Optional<CategoryEntity> found = categoryRepository.findByParentCategoryIdAndName(parent.getId(), "NonExistentChild");
        assertThat(found).isNotPresent();
    }
}
