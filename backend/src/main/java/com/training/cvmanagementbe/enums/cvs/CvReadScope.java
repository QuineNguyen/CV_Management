package com.training.cvmanagementbe.enums.cvs;

// Which CVs a caller may read; applied in the query, never as a post-filter
public enum CvReadScope {
    // Admin, HR
    ALL,
    // Tech lead: Members of the teams they lead, via team membership
    LED_TEAMS,
    // Employee: Own CVs only
    OWN
}
