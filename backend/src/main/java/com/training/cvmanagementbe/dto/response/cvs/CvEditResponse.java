package com.training.cvmanagementbe.dto.response.cvs;

/*
 * One shape for two outcomes, because the caller does not choose which one happens - the CV
 * owner's role does.
 *
 * - directPublish true when the edit produced a version immediately; false when it produced
 * a draft that still needs both approval levels
 */
public record CvEditResponse(
        boolean directPublish,
        CvDraftResponse draft,
        CvVersionSummary publishedVersion
) {

    public static CvEditResponse ofDraft(CvDraftResponse draft) {
        return new CvEditResponse(false, draft, null);
    }

    public static CvEditResponse ofVersion(CvVersionSummary version) {
        return new CvEditResponse(true, null, version);
    }
}
