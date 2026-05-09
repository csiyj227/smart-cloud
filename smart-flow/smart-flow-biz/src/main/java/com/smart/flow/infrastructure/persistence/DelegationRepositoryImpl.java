package com.smart.flow.infrastructure.persistence;

import com.smart.flow.domain.audit.DelegationRepository;
import com.smart.flow.infrastructure.persistence.entity.FlowDelegationEntity;
import com.smart.flow.infrastructure.persistence.mapper.FlowDelegationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class DelegationRepositoryImpl implements DelegationRepository {

    private final FlowDelegationMapper mapper;

    @Override
    public void append(FlowDelegationEntity entity) {
        if (entity.getOccurredAt() == null) {
            entity.setOccurredAt(LocalDateTime.now());
        }
        mapper.insert(entity);
    }
}
