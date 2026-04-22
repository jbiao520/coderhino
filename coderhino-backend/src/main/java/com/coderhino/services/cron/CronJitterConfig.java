package com.coderhino.services.cron;

public record CronJitterConfig(long windowMs, double jitterFraction) {}
