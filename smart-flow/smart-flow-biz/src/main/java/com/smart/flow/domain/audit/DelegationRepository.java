package com.smart.flow.domain.audit;

import com.smart.flow.infrastructure.persistence.entity.FlowDelegationEntity;

public interface DelegationRepository {

    void append(FlowDelegationEntity entity);
}
