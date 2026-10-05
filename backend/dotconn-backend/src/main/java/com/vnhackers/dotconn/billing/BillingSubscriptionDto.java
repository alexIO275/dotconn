package com.vnhackers.dotconn.billing;

import java.time.Instant;

public record BillingSubscriptionDto(String plan, String status, String mode,
    int monthlyAnalysisLimit, int analysesUsed, int remainingAnalyses, Instant usageResetAt,
    Instant periodEnd, boolean cancelAtPeriodEnd, boolean canManage) {}
