package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.PriceOverrideEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.PriceOverrideRepository;
import com.mysillydreams.catalogservice.dto.CreatePriceOverrideRequest;
import com.mysillydreams.catalogservice.dto.PriceOverrideDto;
import com.mysillydreams.catalogservice.dto.UpdatePriceOverrideRequest;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceOverrideServiceTest {

    @Mock
    private PriceOverrideRepository overrideRepository;

    @Mock
    private CatalogItemRepository itemRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private PriceOverrideService priceOverrideService;

    private final String testTopic = "test-price-override-events";
    private CatalogItemEntity catalogItem;
    private PriceOverrideEntity overrideEntity;
    private UUID catalogItemId = UUID.randomUUID();
    private UUID overrideId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(priceOverrideService, "priceOverrideEventsTopic", testTopic);

        catalogItem = CatalogItemEntity.builder().id(catalogItemId).sku("ITEM002").basePrice(BigDecimal.valueOf(50)).build();
        overrideEntity = PriceOverrideEntity.builder()
                .id(overrideId)
                .catalogItem(catalogItem)
                .overridePrice(BigDecimal.valueOf(40))
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().plusSeconds(3600))
                .enabled(true)
                .createdByUserId("user-test-1")
                .createdByRole("ROLE_ADMIN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Test
    void createOverride_whenItemExists_shouldSaveOverrideAndOutboxEvent() {
        // Arrange
        CreatePriceOverrideRequest request = CreatePriceOverrideRequest.builder()
                .itemId(catalogItemId)
                .overridePrice(BigDecimal.valueOf(35))
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(7200))
                .enabled(true)
                .build();
        String userId = "creator-id";
        String userRole = "CREATOR_ROLE";

        when(itemRepository.findById(catalogItemId)).thenReturn(Optional.of(catalogItem));
        when(overrideRepository.save(any(PriceOverrideEntity.class))).thenAnswer(invocation -> {
            PriceOverrideEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", overrideId);
            ReflectionTestUtils.setField(saved, "version", 0L);
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        // Act
        PriceOverrideDto resultDto = priceOverrideService.createOverride(request, userId, userRole);

        // Assert
        assertThat(resultDto).isNotNull();
        assertThat(resultDto.getId()).isEqualTo(overrideId);
        assertThat(resultDto.getItemId()).isEqualTo(catalogItemId);
        assertThat(resultDto.getOverridePrice()).isEqualTo(BigDecimal.valueOf(35));

        verify(itemRepository).findById(catalogItemId);
        verify(overrideRepository).save(any(PriceOverrideEntity.class));

        ArgumentCaptor<PriceOverrideDto> dtoCaptor = ArgumentCaptor.forClass(PriceOverrideDto.class);
        verify(outboxEventService).saveOutboxEvent(
                eq("PriceOverride"),
                eq(overrideId),
                eq("price.override.created"),
                eq(testTopic),
                dtoCaptor.capture()
        );
        assertThat(dtoCaptor.getValue().getId()).isEqualTo(overrideId);
        assertThat(dtoCaptor.getValue().getOverridePrice()).isEqualTo(BigDecimal.valueOf(35));
    }

    @Test
    void createOverride_whenItemNotFound_shouldThrowResourceNotFound() {
        // Arrange
        CreatePriceOverrideRequest request = CreatePriceOverrideRequest.builder().itemId(catalogItemId).build();
        when(itemRepository.findById(catalogItemId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> priceOverrideService.createOverride(request, "user", "role"));
        verify(overrideRepository, never()).save(any());
        verify(outboxEventService, never()).saveOutboxEvent(any(), any(), any(), any(), any());
    }

    @Test
    void createOverride_withInvalidTimes_shouldThrowInvalidRequestException() {
        // Arrange
        CreatePriceOverrideRequest request = CreatePriceOverrideRequest.builder()
                .itemId(catalogItemId)
                .overridePrice(BigDecimal.valueOf(35))
                .startTime(Instant.now().plusSeconds(7200)) // Start time after end time
                .endTime(Instant.now())
                .enabled(true)
                .build();
         when(itemRepository.findById(catalogItemId)).thenReturn(Optional.of(catalogItem));

        // Act & Assert
        assertThrows(InvalidRequestException.class, () -> priceOverrideService.createOverride(request, "user", "role"));
        verify(overrideRepository, never()).save(any());
        verify(outboxEventService, never()).saveOutboxEvent(any(), any(), any(), any(), any());
    }


    @Test
    void updateOverride_whenOverrideExists_shouldUpdateAndSaveOutboxEvent() {
        // Arrange
        UpdatePriceOverrideRequest request = UpdatePriceOverrideRequest.builder()
                .itemId(catalogItemId) // Assuming itemId cannot be changed
                .overridePrice(BigDecimal.valueOf(38))
                .startTime(overrideEntity.getStartTime().plusSeconds(60))
                .endTime(overrideEntity.getEndTime().plusSeconds(60))
                .enabled(false)
                .build();
        String updaterUserId = "updater-id";
        String updaterUserRole = "UPDATER_ROLE";

        when(overrideRepository.findById(overrideId)).thenReturn(Optional.of(overrideEntity));
        when(overrideRepository.save(any(PriceOverrideEntity.class))).thenReturn(overrideEntity);

        // Act
        PriceOverrideDto resultDto = priceOverrideService.updateOverride(overrideId, request, updaterUserId, updaterUserRole);

        // Assert
        assertThat(resultDto).isNotNull();
        assertThat(resultDto.getId()).isEqualTo(overrideId);
        assertThat(resultDto.getOverridePrice()).isEqualTo(BigDecimal.valueOf(38));
        assertThat(resultDto.isEnabled()).isFalse();

        verify(overrideRepository).findById(overrideId);
        verify(overrideRepository).save(overrideEntity);

        ArgumentCaptor<PriceOverrideDto> dtoCaptor = ArgumentCaptor.forClass(PriceOverrideDto.class);
        verify(outboxEventService).saveOutboxEvent(
                eq("PriceOverride"),
                eq(overrideId),
                eq("price.override.updated"),
                eq(testTopic),
                dtoCaptor.capture()
        );
        assertThat(dtoCaptor.getValue().getId()).isEqualTo(overrideId);
        assertThat(dtoCaptor.getValue().getOverridePrice()).isEqualTo(BigDecimal.valueOf(38));
    }

    @Test
    void updateOverride_whenOverrideNotFound_shouldThrowResourceNotFound() {
        // Arrange
        UpdatePriceOverrideRequest request = UpdatePriceOverrideRequest.builder().itemId(catalogItemId).overridePrice(BigDecimal.ONE).build();
        when(overrideRepository.findById(overrideId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> priceOverrideService.updateOverride(overrideId, request, "user", "role"));
        verify(overrideRepository, never()).save(any());
        verify(outboxEventService, never()).saveOutboxEvent(any(), any(), any(), any(), any());
    }

    @Test
    void deleteOverride_whenOverrideExists_shouldDeleteAndSaveOutboxEvent() {
        // Arrange
        when(overrideRepository.findById(overrideId)).thenReturn(Optional.of(overrideEntity));
        doNothing().when(overrideRepository).delete(overrideEntity);

        // Act
        priceOverrideService.deleteOverride(overrideId);

        // Assert
        verify(overrideRepository).findById(overrideId);
        verify(overrideRepository).delete(overrideEntity);

        ArgumentCaptor<PriceOverrideDto> dtoCaptor = ArgumentCaptor.forClass(PriceOverrideDto.class);
        verify(outboxEventService).saveOutboxEvent(
                eq("PriceOverride"),
                eq(overrideId),
                eq("price.override.deleted"),
                eq(testTopic),
                dtoCaptor.capture()
        );
        assertThat(dtoCaptor.getValue().getId()).isEqualTo(overrideEntity.getId());
        assertThat(dtoCaptor.getValue().getOverridePrice()).isEqualTo(overrideEntity.getOverridePrice());
    }

    @Test
    void deleteOverride_whenOverrideNotFound_shouldThrowResourceNotFound() {
        // Arrange
        when(overrideRepository.findById(overrideId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> priceOverrideService.deleteOverride(overrideId));
        verify(overrideRepository, never()).delete(any());
        verify(outboxEventService, never()).saveOutboxEvent(any(), any(), any(), any(), any());
    }
}
