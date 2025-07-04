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
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceOverrideService {

    private final PriceOverrideRepository overrideRepository;
    private final CatalogItemRepository itemRepository;
    private final OutboxEventService outboxEventService;

    @Value("${kafka.topics.priceOverride}")
    private String priceOverrideEventsTopic;

    private static final String AGGREGATE_TYPE_PRICE_OVERRIDE = "PriceOverride";

    @Transactional
    public PriceOverrideDto createOverride(CreatePriceOverrideRequest request, String userId, String userRole) {
        log.info("Creating price override for item ID: {}, price: {}", request.getItemId(), request.getOverridePrice());
        CatalogItemEntity item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", request.getItemId()));

        if (request.getStartTime() != null && request.getEndTime() != null && request.getStartTime().isAfter(request.getEndTime())) {
            throw new InvalidRequestException("Start time must be before end time for price override.");
        }

        PriceOverrideEntity override = PriceOverrideEntity.builder()
                .catalogItem(item)
                .overridePrice(request.getOverridePrice())
                .startTime(request.getStartTime() != null ? request.getStartTime() : Instant.now()) // Default start to now if null
                .endTime(request.getEndTime())
                .enabled(request.isEnabled())
                .createdByUserId(userId)
                .createdByRole(userRole)
                .build();

        PriceOverrideEntity savedOverride = overrideRepository.save(override);
        log.info("Price override created with ID: {}", savedOverride.getId());

        PriceOverrideDto overrideDto = convertToDto(savedOverride);
        outboxEventService.saveOutboxEvent(
                AGGREGATE_TYPE_PRICE_OVERRIDE,
                savedOverride.getId(),
                "price.override.created",
                priceOverrideEventsTopic,
                overrideDto
        );
        return overrideDto;
    }

    @Transactional(readOnly = true)
    public PriceOverrideDto getOverrideById(UUID overrideId) {
        return overrideRepository.findById(overrideId)
                .map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("PriceOverride", "id", overrideId));
    }

    @Transactional(readOnly = true)
    public List<PriceOverrideDto> findOverridesByItemId(UUID itemId) {
         if (!itemRepository.existsById(itemId)) {
             throw new ResourceNotFoundException("CatalogItem", "id", itemId);
        }
        return overrideRepository.findByCatalogItemId(itemId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PriceOverrideDto> findActiveOverridesForItem(UUID itemId) {
        if (!itemRepository.existsById(itemId)) {
             throw new ResourceNotFoundException("CatalogItem", "id", itemId);
        }
        return overrideRepository.findActiveOverridesForItemAtTime(itemId, Instant.now()).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }


    @Transactional
    @Retryable(value = {OptimisticLockException.class, CannotAcquireLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public PriceOverrideDto updateOverride(UUID overrideId, UpdatePriceOverrideRequest request, String updatedByUserId, String updatedByRole) {
        log.info("Updating price override with ID: {}", overrideId);
        PriceOverrideEntity override = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new ResourceNotFoundException("PriceOverride", "id", overrideId));

        if (!override.getCatalogItem().getId().equals(request.getItemId())) {
            throw new UnsupportedOperationException("Cannot change the item ID of an existing price override.");
        }
        if (request.getStartTime() != null && request.getEndTime() != null && request.getStartTime().isAfter(request.getEndTime())) {
            throw new InvalidRequestException("Start time must be before end time for price override.");
        }

        override.setOverridePrice(request.getOverridePrice());
        override.setStartTime(request.getStartTime());
        override.setEndTime(request.getEndTime());
        override.setEnabled(request.getEnabled());
        // Optionally update modifier info - depends on business logic if original creator is preserved or last modifier
        override.setCreatedByUserId(updatedByUserId); // Example: set last modifier as creator for simplicity
        override.setCreatedByRole(updatedByRole);   // Or add separate updatedBy fields to entity
        override.setUpdatedAt(Instant.now());


        PriceOverrideEntity updatedOverride = overrideRepository.save(override);
        log.info("Price override updated with ID: {}", updatedOverride.getId());

        PriceOverrideDto overrideDto = convertToDto(updatedOverride);
        outboxEventService.saveOutboxEvent(
                AGGREGATE_TYPE_PRICE_OVERRIDE,
                updatedOverride.getId(),
                "price.override.updated",
                priceOverrideEventsTopic,
                overrideDto
        );
        return overrideDto;
    }

    @Transactional
    public void deleteOverride(UUID overrideId) {
        log.info("Deleting price override with ID: {}", overrideId);
        PriceOverrideEntity override = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new ResourceNotFoundException("PriceOverride", "id", overrideId));

        PriceOverrideDto overrideDto = convertToDto(override);
        outboxEventService.saveOutboxEvent(
                AGGREGATE_TYPE_PRICE_OVERRIDE,
                override.getId(),
                "price.override.deleted",
                priceOverrideEventsTopic,
                overrideDto // Sending the DTO for consistency
        );

        overrideRepository.delete(override);
        log.info("Price override deleted with ID: {}", overrideId);
    }

    private PriceOverrideDto convertToDto(PriceOverrideEntity entity) {
        return PriceOverrideDto.builder()
                .id(entity.getId())
                .itemId(entity.getCatalogItem().getId())
                .itemSku(entity.getCatalogItem().getSku()) // Denormalized
                .overridePrice(entity.getOverridePrice())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .enabled(entity.isEnabled())
                .createdByUserId(entity.getCreatedByUserId())
                .createdByRole(entity.getCreatedByRole())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}
