package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.CategoryDto;
import com.mysillydreams.catalogservice.dto.CreateCategoryRequest;
import com.mysillydreams.catalogservice.exception.DuplicateResourceException;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;


import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CatalogItemRepository catalogItemRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private CategoryService categoryService;

    private UUID testParentId;
    private CategoryEntity parentCategoryEntity;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(categoryService, "categoryCreatedTopic", "cat.created");
        ReflectionTestUtils.setField(categoryService, "categoryUpdatedTopic", "cat.updated");
        ReflectionTestUtils.setField(categoryService, "categoryDeletedTopic", "cat.deleted");

        testParentId = UUID.randomUUID();
        parentCategoryEntity = CategoryEntity.builder()
                .id(testParentId)
                .name("Parent")
                .type(ItemType.PRODUCT)
                .path(CategoryService.PATH_SEPARATOR + testParentId.toString() + CategoryService.PATH_SEPARATOR)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createCategory_topLevel_success() {
        CreateCategoryRequest request = CreateCategoryRequest.builder().name("Electronics").type(ItemType.PRODUCT).build();
        UUID generatedId = UUID.randomUUID();

        CategoryEntity categoryToSave = new CategoryEntity();
        categoryToSave.setName(request.getName());
        categoryToSave.setType(request.getType());
        // Mock the first save (without path, id is generated)
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> {
            CategoryEntity c = invocation.getArgument(0);
            c.setId(generatedId); // Simulate ID generation
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });
        // Mock the second save (with path)
        // When categoryRepository.save is called the second time (with path), return the entity with path set.
        when(categoryRepository.save(argThat(c -> c.getId().equals(generatedId) && c.getPath() != null)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(categoryRepository.findByParentCategoryIsNullAndName(request.getName())).thenReturn(Optional.empty());
        when(catalogItemRepository.countByCategoryId(generatedId)).thenReturn(0L);


        CategoryDto result = categoryService.createCategory(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Electronics");
        assertThat(result.getId()).isEqualTo(generatedId);
        assertThat(result.getPath()).isEqualTo(CategoryService.PATH_SEPARATOR + generatedId.toString() + CategoryService.PATH_SEPARATOR);
        verify(kafkaProducerService).sendMessage(eq("cat.created"), eq(generatedId.toString()), any());
        verify(categoryRepository, times(2)).save(any(CategoryEntity.class)); // Called twice
    }

    @Test
    void createCategory_withParent_success() {
        CreateCategoryRequest request = CreateCategoryRequest.builder().name("Laptops").type(ItemType.PRODUCT).parentId(testParentId).build();
        UUID generatedId = UUID.randomUUID();

        when(categoryRepository.findById(testParentId)).thenReturn(Optional.of(parentCategoryEntity));
        when(categoryRepository.findByParentCategoryIdAndName(testParentId, request.getName())).thenReturn(Optional.empty());

        CategoryEntity categoryToSave = new CategoryEntity();
        categoryToSave.setName(request.getName());
        categoryToSave.setType(request.getType());
        categoryToSave.setParentCategory(parentCategoryEntity);

        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> {
            CategoryEntity c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(generatedId); // Simulate ID generation on first save
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            if (c.getParentCategory() != null && c.getParentCategory().getPath() == null) { // Ensure parent path is set for test
                 c.getParentCategory().setPath(CategoryService.PATH_SEPARATOR + c.getParentCategory().getId() + CategoryService.PATH_SEPARATOR);
            }
            return c;
        });
         when(catalogItemRepository.countByCategoryId(generatedId)).thenReturn(0L);


        CategoryDto result = categoryService.createCategory(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Laptops");
        assertThat(result.getParentId()).isEqualTo(testParentId);
        assertThat(result.getPath()).isEqualTo(parentCategoryEntity.getPath() + generatedId.toString() + CategoryService.PATH_SEPARATOR);
        verify(kafkaProducerService).sendMessage(eq("cat.created"), eq(generatedId.toString()), any());
    }

    @Test
    void createCategory_topLevel_duplicateName_throwsException() {
        CreateCategoryRequest request = CreateCategoryRequest.builder().name("Electronics").type(ItemType.PRODUCT).build();
        when(categoryRepository.findByParentCategoryIsNullAndName("Electronics")).thenReturn(Optional.of(new CategoryEntity()));

        assertThrows(DuplicateResourceException.class, () -> categoryService.createCategory(request));
    }

    @Test
    void createCategory_withParent_parentNotFound_throwsException() {
        CreateCategoryRequest request = CreateCategoryRequest.builder().name("Laptops").type(ItemType.PRODUCT).parentId(testParentId).build();
        when(categoryRepository.findById(testParentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.createCategory(request));
    }

    @Test
    void createCategory_typeMismatchWithParent_throwsException() {
        parentCategoryEntity.setType(ItemType.PRODUCT); // Parent is PRODUCT
        CreateCategoryRequest request = CreateCategoryRequest.builder()
            .name("Some Service Category")
            .type(ItemType.SERVICE) // Child is SERVICE
            .parentId(testParentId)
            .build();

        when(categoryRepository.findById(testParentId)).thenReturn(Optional.of(parentCategoryEntity));

        Exception exception = assertThrows(InvalidRequestException.class, () -> {
            categoryService.createCategory(request);
        });
        assertThat(exception.getMessage()).contains("Category type must match parent category type");
    }

    @Test
    void getCategoryById_found_returnsDto() {
        UUID categoryId = UUID.randomUUID();
        CategoryEntity categoryEntity = CategoryEntity.builder().id(categoryId).name("Test").type(ItemType.PRODUCT).path("/test/").build();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(categoryEntity));
        when(catalogItemRepository.countByCategoryId(categoryId)).thenReturn(0L);


        CategoryDto result = categoryService.getCategoryById(categoryId, false);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test");
    }

    @Test
    void getCategoryById_withChildren_returnsDtoWithChildren() {
        UUID parentId = UUID.randomUUID();
        CategoryEntity parent = CategoryEntity.builder().id(parentId).name("Parent").type(ItemType.PRODUCT).path("/parent/").build();
        UUID childId = UUID.randomUUID();
        CategoryEntity child = CategoryEntity.builder().id(childId).name("Child").type(ItemType.PRODUCT).parentCategory(parent).path("/parent/child/").build();

        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(categoryRepository.findByParentCategoryId(parentId)).thenReturn(List.of(child));
        when(catalogItemRepository.countByCategoryId(parentId)).thenReturn(0L);
        when(catalogItemRepository.countByCategoryId(childId)).thenReturn(0L);


        CategoryDto result = categoryService.getCategoryById(parentId, true);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Parent");
        assertThat(result.getChildren()).hasSize(1);
        assertThat(result.getChildren().get(0).getName()).isEqualTo("Child");
    }


    @Test
    void deleteCategory_categoryIsEmpty_success() {
        UUID categoryId = UUID.randomUUID();
        CategoryEntity categoryEntity = CategoryEntity.builder().id(categoryId).name("EmptyCat").type(ItemType.PRODUCT).path("/empty/").build();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(categoryEntity));
        when(catalogItemRepository.findByCategoryId(categoryId)).thenReturn(Collections.emptyList());
        // category.getChildCategories() is an empty list by default for a new CategoryEntity

        categoryService.deleteCategory(categoryId);

        verify(categoryRepository).delete(categoryEntity);
        verify(kafkaProducerService).sendMessage(eq("cat.deleted"), eq(categoryId.toString()), any());
    }

    @Test
    void deleteCategory_containsItems_throwsException() {
        UUID categoryId = UUID.randomUUID();
        CategoryEntity categoryEntity = CategoryEntity.builder().id(categoryId).name("NonEmptyCat").type(ItemType.PRODUCT).path("/nonempty/").build();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(categoryEntity));
        when(catalogItemRepository.findByCategoryId(categoryId)).thenReturn(List.of(mock(com.mysillydreams.catalogservice.domain.model.CatalogItemEntity.class)));

        assertThrows(InvalidRequestException.class, () -> categoryService.deleteCategory(categoryId));
    }

    @Test
    void updateCategory_rename_success() {
        UUID categoryId = UUID.randomUUID();
        CategoryEntity existingCategory = CategoryEntity.builder()
            .id(categoryId).name("Old Name").type(ItemType.PRODUCT).path("/oldname/")
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();

        CreateCategoryRequest updateRequest = CreateCategoryRequest.builder()
            .name("New Name").type(ItemType.PRODUCT).build(); // No parent change

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.findByParentCategoryIsNullAndName("New Name")).thenReturn(Optional.empty()); // Assuming top-level for simplicity
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogItemRepository.countByCategoryId(categoryId)).thenReturn(0L);


        CategoryDto result = categoryService.updateCategory(categoryId, updateRequest);

        assertThat(result.getName()).isEqualTo("New Name");
        verify(categoryRepository).save(argThat(cat -> cat.getName().equals("New Name")));
        verify(kafkaProducerService).sendMessage(eq("cat.updated"), eq(categoryId.toString()), any());
    }

    @Test
    void updateCategory_reparent_successAndPathUpdated() {
        UUID categoryToMoveId = UUID.randomUUID();
        CategoryEntity categoryToMove = CategoryEntity.builder()
            .id(categoryToMoveId).name("Movable Category").type(ItemType.PRODUCT).path("/movable/")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        UUID newParentId = UUID.randomUUID();
        CategoryEntity newParent = CategoryEntity.builder()
            .id(newParentId).name("New Parent").type(ItemType.PRODUCT)
            .path(CategoryService.PATH_SEPARATOR + newParentId.toString() + CategoryService.PATH_SEPARATOR)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        CreateCategoryRequest updateRequest = CreateCategoryRequest.builder()
            .name("Movable Category") // Name not changing
            .type(ItemType.PRODUCT)
            .parentId(newParentId) // Reparenting
            .build();

        when(categoryRepository.findById(categoryToMoveId)).thenReturn(Optional.of(categoryToMove));
        when(categoryRepository.findById(newParentId)).thenReturn(Optional.of(newParent));
        // Assume no name conflict under new parent
        when(categoryRepository.findByParentCategoryIdAndName(newParentId, "Movable Category")).thenReturn(Optional.empty());

        // Capture the argument to verify path update
        ArgumentCaptor<CategoryEntity> categoryCaptor = ArgumentCaptor.forClass(CategoryEntity.class);
        when(categoryRepository.save(categoryCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogItemRepository.countByCategoryId(categoryToMoveId)).thenReturn(0L);


        CategoryDto result = categoryService.updateCategory(categoryToMoveId, updateRequest);

        assertThat(result.getParentId()).isEqualTo(newParentId);

        CategoryEntity savedEntity = categoryCaptor.getValue();
        String expectedPath = newParent.getPath() + categoryToMoveId.toString() + CategoryService.PATH_SEPARATOR;
        assertThat(savedEntity.getPath()).isEqualTo(expectedPath); // Verify path was regenerated
        assertThat(result.getPath()).isEqualTo(expectedPath);

        verify(kafkaProducerService).sendMessage(eq("cat.updated"), eq(categoryToMoveId.toString()), any());
    }
}
