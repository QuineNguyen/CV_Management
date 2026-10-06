package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.record.cvs.ContentDiff;
import com.training.cvmanagementbe.record.cvs.CvContent;

import java.util.UUID;

public interface DiffService {

    /*
     * Compares two snapshots section -> item -> field, unchanged content included.
     * - oldContent null: Compare with an empty CV, so everything comes out ADDED.
     * - Avatar ids are compared as the personal_info field "avatar_image_id".
     */
    ContentDiff diff(CvContent oldContent, UUID oldAvatarImageId,
                     CvContent newContent, UUID newAvatarImageId);
}
