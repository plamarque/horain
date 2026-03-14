package com.horain.service;

import com.horain.dto.ActivityTypeDto;
import com.horain.model.ActivityType;
import com.horain.repository.ActivityTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for activity type (nature + TJM) CRUD.
 */
@Service
public class ActivityTypeService {

    private final ActivityTypeRepository activityTypeRepository;

    public ActivityTypeService(ActivityTypeRepository activityTypeRepository) {
        this.activityTypeRepository = activityTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityTypeDto> findAll() {
        return activityTypeRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ActivityTypeDto> findByCode(String code) {
        return activityTypeRepository.findById(code).map(this::toDto);
    }

    @Transactional
    public ActivityTypeDto create(ActivityTypeDto dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (activityTypeRepository.existsById(dto.getCode().trim())) {
            throw new IllegalArgumentException("Activity type already exists: " + dto.getCode());
        }
        if (dto.getDailyRateCents() == null || dto.getDailyRateCents() <= 0) {
            throw new IllegalArgumentException("dailyRateCents must be positive");
        }
        ActivityType entity = new ActivityType();
        entity.setCode(dto.getCode().trim().toUpperCase());
        entity.setLabel(dto.getLabel() != null ? dto.getLabel().trim() : "");
        entity.setDailyRateCents(dto.getDailyRateCents());
        entity.setDescription(dto.getDescription() != null && !dto.getDescription().isBlank() ? dto.getDescription().trim() : null);
        ActivityType saved = activityTypeRepository.save(entity);
        return toDto(saved);
    }

    @Transactional
    public ActivityTypeDto update(String code, ActivityTypeDto patch) {
        ActivityType entity = activityTypeRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Activity type not found: " + code));
        if (patch.getLabel() != null) {
            entity.setLabel(patch.getLabel().trim());
        }
        if (patch.getDailyRateCents() != null) {
            if (patch.getDailyRateCents() <= 0) {
                throw new IllegalArgumentException("dailyRateCents must be positive");
            }
            entity.setDailyRateCents(patch.getDailyRateCents());
        }
        if (patch.getDescription() != null) {
            entity.setDescription(patch.getDescription().isBlank() ? null : patch.getDescription().trim());
        }
        return toDto(activityTypeRepository.save(entity));
    }

    @Transactional
    public void deleteByCode(String code) {
        if (!activityTypeRepository.existsById(code)) {
            throw new IllegalArgumentException("Activity type not found: " + code);
        }
        activityTypeRepository.deleteById(code);
    }

    private ActivityTypeDto toDto(ActivityType a) {
        ActivityTypeDto dto = new ActivityTypeDto();
        dto.setCode(a.getCode());
        dto.setLabel(a.getLabel());
        dto.setDailyRateCents(a.getDailyRateCents());
        dto.setDescription(a.getDescription());
        return dto;
    }
}
