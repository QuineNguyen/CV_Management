package com.training.cvmanagementbe.repository.projection;

import com.training.cvmanagementbe.enums.users.RequestStatus;

import java.util.UUID;

public interface BatchStatusCount {

    UUID getBatchRequestId();

    RequestStatus getStatus();

    Long getRequestCount();
}
