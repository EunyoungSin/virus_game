package com.checkpoint.visitor.dto;

import java.util.List;
import java.util.Map;

public record VisitorResponse(
        Long visitorId,
        Integer dayIndex,
        Integer orderInDay,
        String name,
        Integer age,
        String jobClaimed,
        String originCity,
        List<Map<String, Object>> travelHistory) {}
