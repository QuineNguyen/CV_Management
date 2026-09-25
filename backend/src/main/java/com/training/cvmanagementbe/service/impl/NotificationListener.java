package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;
import com.training.cvmanagementbe.enums.approvals.DecisionResult;
import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import com.training.cvmanagementbe.enums.notifications.EmailTemplateVar;
import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import com.training.cvmanagementbe.enums.notifications.NotificationLink;
import com.training.cvmanagementbe.enums.users.AccountStatus;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.record.events.*;
import com.training.cvmanagementbe.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;

import static com.training.cvmanagementbe.enums.notifications.EmailTemplateVar.*;

/*
 * Turns committed domain events into notification pairs.
 * - AFTER_COMMIT: a rolled-back business transaction notifies nobody.
 * - @Async: the committing request never waits for these writes or for the broker.
 * - Only people who can act on the event
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    // An owner withdrawing an unseen draft notifies nobody; only a review in flight has an audience.
    private static final Set<DraftStatus> UNDER_REVIEW =
            EnumSet.of(DraftStatus.PENDING_TECH_LEAD, DraftStatus.PENDING_HR);

    private static final String UNKNOWN_PERSON = "Someone";
    private static final String UNKNOWN_VALUE = "-";
    private static final String TARGET_CV = "CV";
    private static final String TARGET_PROFILE = "competency profile and all the CVs in it";

    private final NotificationDispatcher dispatcher;
    private final CvDraftRepository cvDraftRepository;
    private final CvRepository cvRepository;
    private final CvProfileRepository cvProfileRepository;
    private final UserRepository userRepository;
    private final ApprovalDecisionRepository decisionRepository;
    private final ProfileUpdateRequestRepository profileUpdateRequestRepository;

    // ---------- Approval workflow ----------

    // The reviewer now holding the draft.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDraftSubmitted(DraftSubmittedEvent event) {
        if (event.assigneeId() == null) {
            return;
        }
        loadDraft(event.draftId()).ifPresent(draft -> {
            String level = levelLabel(event.level());
            dispatcher.dispatch(new NotificationCommand(
                    event.assigneeId(), null, NotificationEventType.DRAFT_SUBMITTED,
                    "%s submitted a CV for your %s: %s".formatted(draft.ownerName(), level, draft.label()),
                    NotificationLink.DRAFT_REVIEW.path(event.draftId()),
                    "CV awaiting your %s - %s".formatted(level, draft.ownerName()),
                    draftVars(draft)
                            .with(LEVEL_LABEL, level)
                            .with(LEVEL1_SKIPPED, event.skipped())
                            .with(FORWARDED, false)
                            .build()
            ));
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDraftApproved(DraftApprovedEvent event) {
        loadDraft(event.draftId()).ifPresent(draft -> {
            if (event.level() == ApprovalLevel.LEVEL_1) {
                notifyForwarded(event, draft);
            } else {
                notifyPublished(event, draft);
            }
        });
    }

    // The owner gets the reason and a link to the draft with its inline comments.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDraftRejected(DraftRejectedEvent event) {
        loadDraft(event.draftId()).ifPresent(draft -> {
            // The decision row carries reason and reviewer; the event deliberately does not.
            Optional<ApprovalDecision> decision = latestRejection(event.draftId());
            String reason = decision.map(ApprovalDecision::getReason).orElse(null);
            String reviewer = decision.map(ApprovalDecision::getApproverId).map(this::nameOf).orElse(UNKNOWN_PERSON);
            String level = levelLabel(event.level());

            dispatcher.dispatch(new NotificationCommand(
                    event.ownerId(), null, NotificationEventType.DRAFT_REJECTED,
                    withReason("%s returned your CV %s at %s".formatted(reviewer, draft.label(), level), reason),
                    NotificationLink.CV_EDIT.path(draft.cvId()),
                    "Your CV needs changes - %s".formatted(draft.label()),
                    draftVars(draft)
                            .with(LEVEL_LABEL, level)
                            .with(REVIEWER_NAME, reviewer)
                            .with(REASON, reason)
                            .build()
            ));
        });
    }

    // ---------- Owner and the reviewer holding it, only when a review was actually running.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDraftCancelled(DraftCancelledEvent event) {
        if (!UNDER_REVIEW.contains(event.previousStatus())) {
            return;
        }
        loadDraft(event.draftId()).ifPresent(draft -> {
            String reason = event.reason();
            List<NotificationCommand> commands = new ArrayList<>();

            commands.add(new NotificationCommand(
                    event.ownerId(), null, NotificationEventType.DRAFT_CANCELLED,
                    withReason("Your CV draft %s was cancelled while under review".formatted(draft.label()), reason),
                    NotificationLink.CV_DETAIL.path(draft.cvId()),
                    "Your CV draft was cancelled - %s".formatted(draft.label()),
                    draftVars(draft).with(OWNER_VIEW, true).with(REASON, reason).build()
            ));

            if (event.assigneeId() != null) {
                commands.add(new NotificationCommand(
                        event.assigneeId(), null, NotificationEventType.DRAFT_CANCELLED,
                        withReason("The review of %s's CV %s was cancelled"
                                .formatted(draft.ownerName(), draft.label()), reason),
                        NotificationLink.APPROVAL_QUEUE.path(),
                        "Review cancelled - %s".formatted(draft.ownerName()),
                        draftVars(draft).with(OWNER_VIEW, false).with(REASON, reason).build()
                ));
            }
            dispatcher.dispatchAll(commands);
        });
    }

    // The new reviewer takes over, the previous one learns the work has left them.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssignmentReassigned(AssignmentReassignedEvent event) {
        loadDraft(event.draftId()).ifPresent(draft -> {
            String level = levelLabel(event.level());
            String reason = event.reason();
            String newName = nameOf(event.newAssigneeId());
            String previousName = nameOf(event.previousAssigneeId());
            List<NotificationCommand> commands = new ArrayList<>();

            commands.add(new NotificationCommand(
                    event.newAssigneeId(), null, NotificationEventType.ASSIGNMENT_REASSIGNED,
                    withReason("%s's CV %s was handed over to you for %s"
                            .formatted(draft.ownerName(), draft.label(), level), reason),
                    NotificationLink.DRAFT_REVIEW.path(event.draftId()),
                    "CV review handed over to you - %s".formatted(draft.ownerName()),
                    draftVars(draft)
                            .with(LEVEL_LABEL, level)
                            .with(INCOMING, true)
                            .with(OTHER_REVIEWER_NAME, previousName)
                            .with(REASON, reason)
                            .build()
            ));

            if (event.previousAssigneeId() != null) {
                commands.add(new NotificationCommand(
                        event.previousAssigneeId(), null, NotificationEventType.ASSIGNMENT_REASSIGNED,
                        withReason("Your review of %s's CV %s was reassigned to %s"
                                .formatted(draft.ownerName(), draft.label(), newName), reason),
                        NotificationLink.APPROVAL_QUEUE.path(),
                        "CV review reassigned - %s".formatted(draft.ownerName()),
                        draftVars(draft)
                                .with(LEVEL_LABEL, level)
                                .with(INCOMING, false)
                                .with(OTHER_REVIEWER_NAME, newName)
                                .with(REASON, reason)
                                .build()
                ));
            }
            dispatcher.dispatchAll(commands);
        });
    }

    // ---------- CV and profile lifecycle ----------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCvDeleted(CvDeletedEvent event) {
        notifyCvLifecycle(NotificationEventType.CV_DELETED, event.cvId(), event.ownerId(), event.actorId(),
                NotificationLink.CV_PROFILES.path());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCvRestored(CvRestoredEvent event) {
        notifyCvLifecycle(NotificationEventType.CV_RESTORED, event.cvId(), event.ownerId(), event.actorId(),
                NotificationLink.CV_DETAIL.path(event.cvId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCvProfileDeleted(CvProfileDeletedEvent event) {
        String profileName = profileNameOf(event.profileId());
        String actor = nameOf(event.actorId());
        String action = actionLabel(NotificationEventType.CV_PROFILE_DELETED);

        dispatcher.dispatch(new NotificationCommand(
                event.ownerId(), event.actorId(), NotificationEventType.CV_PROFILE_DELETED,
                "%s deleted your competency profile %s and all the CVs in it".formatted(actor, profileName),
                NotificationLink.CV_PROFILES.path(),
                "Your competency profile was deleted - %s".formatted(profileName),
                vars()
                        .with(ACTOR_NAME, actor)
                        .with(ACTION_LABEL, action)
                        .with(TARGET_LABEL, TARGET_PROFILE)
                        .with(PROFILE_NAME, profileName)
                        .with(LANGUAGE, null)
                        .build()
        ));
    }

    // ---------- Personal information updates ----------

    // Exactly the people who may decide it - HR never hears about an HR request.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileUpdateSubmitted(ProfileUpdateSubmittedEvent event) {
        Optional<ProfileUpdateRequest> request = profileUpdateRequestRepository.findById(event.requestId());
        Optional<User> requester = userRepository.findById(event.requesterId());
        if (request.isEmpty() || requester.isEmpty()) {
            return;
        }

        List<String> fields = changedFields(request.get());
        String requesterName = requester.get().getFullName();

        List<NotificationCommand> commands = reviewersOf(requester.get().getRole()).stream()
                .map(reviewer -> new NotificationCommand(
                        reviewer.getId(), event.requesterId(), NotificationEventType.PROFILE_UPDATE_SUBMITTED,
                        "%s requested changes to their personal information: %s"
                                .formatted(requesterName, String.join(", ", fields)),
                        NotificationLink.PROFILE_UPDATE_REQUESTS.path(),
                        "Personal information update request - %s".formatted(requesterName),
                        vars()
                                .with(EMPLOYEE_NAME, requesterName)
                                .with(CHANGED_FIELDS, fields)
                                .with(DECIDED, false)
                                .with(APPROVED, false)
                                .build()
                ))
                .toList();

        dispatcher.dispatchAll(commands);
    }

    // The requester only, with the decider's name and the verbatim reason.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileUpdateDecided(ProfileUpdateDecidedEvent event) {
        String reviewer = nameOf(event.reviewerId());
        String outcome = event.approved() ? "approved" : "rejected";

        dispatcher.dispatch(new NotificationCommand(
                event.requesterId(), event.reviewerId(), NotificationEventType.PROFILE_UPDATE_DECIDED,
                withReason("%s %s your personal information update".formatted(reviewer, outcome), event.reason()),
                NotificationLink.MY_PROFILE.path(),
                "Your personal information update was %s".formatted(outcome),
                vars()
                        .with(REVIEWER_NAME, reviewer)
                        .with(DECIDED, true)
                        .with(APPROVED, event.approved())
                        .with(REASON, event.reason())
                        .build()
        ));
    }

    // ---------- Account ----------

    // The password goes to the email only; the in-app row is stored and must not hold it.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordReset(PasswordResetEvent event) {
        String actor = nameOf(event.actorId());

        dispatcher.dispatch(new NotificationCommand(
                event.targetUserId(), event.actorId(), NotificationEventType.PASSWORD_RESET,
                "%s reset your password. Sign in with the temporary password sent to your email and choose a new one"
                        .formatted(actor),
                NotificationLink.CHANGE_PASSWORD.path(),
                "Your password has been reset",
                vars()
                        .with(ACTOR_NAME, actor)
                        .with(TEMPORARY_PASSWORD, event.temporaryPassword())
                        .build()
        ));
    }

    // ---------- Recipients per event ----------

    // Level 1 passed: only the HR who now holds the draft can act on it.
    private void notifyForwarded(DraftApprovedEvent event, DraftContext draft) {
        if (event.nextAssigneeId() == null) {
            return;
        }
        String level = levelLabel(ApprovalLevel.LEVEL_2);
        dispatcher.dispatch(new NotificationCommand(
                event.nextAssigneeId(), null, NotificationEventType.DRAFT_APPROVED_LEVEL_1,
                "%s's CV passed technical review and awaits your %s: %s"
                        .formatted(draft.ownerName(), level, draft.label()),
                NotificationLink.DRAFT_REVIEW.path(event.draftId()),
                "CV awaiting your %s - %s".formatted(level, draft.ownerName()),
                draftVars(draft)
                        .with(LEVEL_LABEL, level)
                        .with(LEVEL1_SKIPPED, false)
                        .with(FORWARDED, true)
                        .build()
        ));
    }

    private void notifyPublished(DraftApprovedEvent event, DraftContext draft) {
        dispatcher.dispatch(new NotificationCommand(
                event.ownerId(), null, NotificationEventType.DRAFT_APPROVED_LEVEL_2,
                "Your CV %s was approved and published as version %d".formatted(draft.label(), event.versionNumber()),
                NotificationLink.CV_DETAIL.path(draft.cvId()),
                "Your CV was published - version %d".formatted(event.versionNumber()),
                draftVars(draft).with(VERSION_NUMBER, event.versionNumber()).build()
        ));
    }

    // Admin/HR acted on someone else's CV: the owner must know what carries their name.
    private void notifyCvLifecycle(NotificationEventType type, UUID cvId, UUID ownerId, UUID actorId, String link) {
        Optional<Cv> cv = cvRepository.findById(cvId);
        if (cv.isEmpty()) {
            return;
        }
        String profileName = profileNameOf(cv.get().getProfileId());
        String language = cv.get().getLanguage().name();
        String actor = nameOf(actorId);
        String action = actionLabel(type);

        dispatcher.dispatch(new NotificationCommand(
                ownerId, actorId, type,
                "%s %s your CV %s (%s)".formatted(actor, action, profileName, language),
                link,
                "Your CV was %s - %s (%s)".formatted(action, profileName, language),
                vars()
                        .with(ACTOR_NAME, actor)
                        .with(ACTION_LABEL, action)
                        .with(TARGET_LABEL, TARGET_CV)
                        .with(PROFILE_NAME, profileName)
                        .with(LANGUAGE, language)
                        .build()
        ));
    }

    // ---------- Loading ----------

    private Optional<DraftContext> loadDraft(UUID draftId) {
        CvDraft draft = cvDraftRepository.findById(draftId).orElse(null);
        Cv cv = draft == null ? null : cvRepository.findById(draft.getCvId()).orElse(null);
        if (cv == null) {
            log.warn("Draft {} or its CV is gone; notification skipped", draftId);
            return Optional.empty();
        }
        return Optional.of(new DraftContext(
                cv.getId(),
                nameOf(draft.getOwnerId()),
                profileNameOf(cv.getProfileId()),
                cv.getLanguage().name()
        ));
    }

    private Optional<ApprovalDecision> latestRejection(UUID draftId) {
        List<ApprovalDecision> decisions = decisionRepository.findByDraftIdOrderByDecidedAtAsc(draftId);
        for (int i = decisions.size() - 1; i >= 0; i--) {
            if (decisions.get(i).getResult() == DecisionResult.REJECTED) {
                return Optional.of(decisions.get(i));
            }
        }
        return Optional.empty();
    }

    // Roles able to decide a request from requesterRole, active accounts only.
    private List<User> reviewersOf(Role requesterRole) {
        return Arrays.stream(Role.values())
                .filter(role -> role.manageableRoles().contains(requesterRole))
                .flatMap(role -> userRepository.findByRoleAndStatusOrderByFullNameAsc(role, AccountStatus.ACTIVE).stream())
                .toList();
    }

    private String nameOf(UUID userId) {
        if (userId == null) {
            return UNKNOWN_PERSON;
        }
        return userRepository.findById(userId).map(User::getFullName).orElse(UNKNOWN_PERSON);
    }

    private String profileNameOf(UUID profileId) {
        return cvProfileRepository.findById(profileId).map(CvProfile::getName).orElse(UNKNOWN_VALUE);
    }

    // ---------- Presentation ----------

    private static String levelLabel(ApprovalLevel level) {
        return level == ApprovalLevel.LEVEL_1 ? "technical review" : "format review";
    }

    private static String actionLabel(NotificationEventType type) {
        return switch (type) {
            case CV_DELETED, CV_PROFILE_DELETED -> "deleted";
            case CV_RESTORED -> "restored";
            default -> throw new IllegalArgumentException("Not a lifecycle event: " + type);
        };
    }

    private static String withReason(String sentence, String reason) {
        return reason == null || reason.isBlank() ? sentence : sentence + ". Reason: " + reason;
    }

    // Only fields that were really proposed; the request stores genuine differences only.
    private static List<String> changedFields(ProfileUpdateRequest request) {
        List<String> fields = new ArrayList<>();
        if (request.getRequestedFullName() != null) {
            fields.add("Full name");
        }
        if (request.getRequestedDateOfBirth() != null) {
            fields.add("Date of birth");
        }
        if (request.getRequestedPhoneNumber() != null) {
            fields.add("Phone number");
        }
        if (request.getRequestedAddress() != null) {
            fields.add("Address");
        }
        if (request.getRequestedAvatarImageId() != null) {
            fields.add("Avatar");
        }
        return fields;
    }

    private static TemplateVars vars() {
        return new TemplateVars();
    }

    private static TemplateVars draftVars(DraftContext draft) {
        return vars()
                .with(EMPLOYEE_NAME, draft.ownerName())
                .with(PROFILE_NAME, draft.profileName())
                .with(LANGUAGE, draft.language());
    }

    private record DraftContext(UUID cvId, String ownerName, String profileName, String language) {

        String label() {
            return "%s (%s)".formatted(profileName, language);
        }
    }

    // Keyed by EmailTemplateVar, so a template variable is never a loose string.
    private static final class TemplateVars {

        private final Map<String, Object> values = new LinkedHashMap<>();

        TemplateVars with(EmailTemplateVar key, Object value) {
            values.put(key.getKey(), value);
            return this;
        }

        Map<String, Object> build() {
            return values;
        }
    }
}
