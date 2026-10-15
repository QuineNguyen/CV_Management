package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.skills.MoveSkillRequest;
import com.training.cvmanagementbe.dto.request.skills.SkillRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.skills.SkillOptionResponse;
import com.training.cvmanagementbe.dto.response.skills.SkillResponse;
import com.training.cvmanagementbe.record.cvs.CvContent;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

// Shared skill catalogue. No delete operation exists on purpose.
public interface SkillService {

    // Picker / filter lookup, open to every signed-in user
    PagedResponse<SkillOptionResponse> listOptions(String keyword, Pageable pageable);

    // Management list with creator and usage count (Admin/HR)
    PagedResponse<SkillResponse> list(String keyword, Pageable pageable);

    SkillResponse create(SkillRequest request);

    SkillResponse update(UUID id, SkillRequest request);

    void move(UUID id, MoveSkillRequest request);

    /*
     * Points every skills entry of the content at a catalogue row, creating rows for names typed
     * for the first time. Must run inside the caller's transaction.
     */
    CvContent linkCatalog(CvContent content);
}
