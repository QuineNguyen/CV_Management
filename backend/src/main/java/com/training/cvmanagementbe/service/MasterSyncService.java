package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.entity.models.Cv;
import com.training.cvmanagementbe.record.cvs.CvContent;

/*
 * Structure sync: Carries a change in the master's structure into the drafts of the
 * other languages of the same profile. Never writes a version, never leaves the profile.
 */
public interface MasterSyncService {

    /*
     * Called by VersionPublisher inside the publishing transaction, for every source
     * (APPROVAL, DIRECT_EDIT, ROLLBACK). previous is null when the master published its v1.
     */
    void onMasterPublished(Cv master, CvContent previous, CvContent current);
}
