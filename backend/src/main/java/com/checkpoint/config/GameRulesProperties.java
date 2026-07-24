package com.checkpoint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "game")
public class GameRulesProperties {

    private int days = 4;
    private int visitorsPerDay = 3;
    private int minInfected = 4;
    private int maxInfected = 6;

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public int getVisitorsPerDay() {
        return visitorsPerDay;
    }

    public void setVisitorsPerDay(int visitorsPerDay) {
        this.visitorsPerDay = visitorsPerDay;
    }

    public int getMinInfected() {
        return minInfected;
    }

    public void setMinInfected(int minInfected) {
        this.minInfected = minInfected;
    }

    public int getMaxInfected() {
        return maxInfected;
    }

    public void setMaxInfected(int maxInfected) {
        this.maxInfected = maxInfected;
    }

    public int getTotalVisitors() {
        return days * visitorsPerDay;
    }
}
