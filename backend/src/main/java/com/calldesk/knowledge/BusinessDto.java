package com.calldesk.knowledge;

public record BusinessDto(String name, String greeting, String hours, String address, String handoffNumber) {
    public static BusinessDto from(BusinessProfile profile) {
        return new BusinessDto(profile.name(), profile.greeting(), profile.hours(), profile.address(), profile.handoffNumber());
    }
}
