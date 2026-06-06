package com.sadday.app.profile.dto;

import java.util.List;

public record ProfileCompletionStatusResponse(
        boolean      profileComplete,
        boolean      canEnrollActivities,
        List<String> missingRequirements,
        List<String> pendingDocuments,
        List<String> expiredDocuments
) {}
