package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.TemplateVars;
import com.training.cvmanagementbe.entity.models.CvProfile;
import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.notifications.DispatchOutcome;
import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import com.training.cvmanagementbe.enums.notifications.NotificationLink;
import com.training.cvmanagementbe.record.events.DispatchOptions;
import com.training.cvmanagementbe.record.events.NotificationCommand;
import com.training.cvmanagementbe.repository.CvProfileRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.training.cvmanagementbe.enums.notifications.EmailTemplateVar.*;

/*
 * The "CV update requested" notification, for single requests and batch children alike.
 * Every email carries the request id, so a final delivery failure flags that request.
 */
@Component
@RequiredArgsConstructor
public class UpdateRequestNotifier {

    private static final String UNKNOWN_PERSON = "Someone";
    private static final String UNKNOWN_VALUE = "-";
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String ACTION_UPDATE = "update";
    private static final String ACTION_CREATE = "create";

    private final NotificationDispatcher dispatcher;
    private final UserRepository userRepository;
    private final CvProfileRepository cvProfileRepository;
    private final AnchoredNoteCodec anchoredNoteCodec;

    // Email and in-app
    public DispatchOutcome notifyRequested(UpdateRequest request) {
        return dispatcher.dispatch(commandFor(request), DispatchOptions.tracked(request.getId()));
    }

    // Email only: The in-app row from the first send is still there
    public DispatchOutcome resendRequestedEmail(UpdateRequest request) {
        return dispatcher.dispatch(commandFor(request), DispatchOptions.trackedEmailOnly(request.getId()));
    }

    // Everything is read from the row, so a resend days later still says the right thing
    private NotificationCommand commandFor(UpdateRequest request) {
        String requester = nameOf(request.getCreatedBy());
        String language = request.getLanguage().name();
        String profileName = request.getProfileId() == null ? null : profileNameOf(request.getProfileId());
        String action = request.getCvId() == null ? ACTION_CREATE : ACTION_UPDATE;
        String target = profileName == null
                ? "a new CV (%s)".formatted(language)
                : "your CV %s (%s)".formatted(profileName, language);
        String deadline = DEADLINE_FORMAT.format(request.getDeadline());
        int noteCount = anchoredNoteCodec.read(request.getAnchoredNotes()).size();

        return new NotificationCommand(
                request.getEmployeeId(), request.getCreatedBy(), NotificationEventType.CV_UPDATE_REQUESTED,
                withNotes(withReason("%s asked you to %s %s by %s"
                        .formatted(requester, action, target, deadline), request.getReason()), noteCount),
                linkOf(request),
                "CV update requested - due %s".formatted(deadline),
                new TemplateVars()
                        .with(ACTOR_NAME, requester)
                        .with(PROFILE_NAME, profileName)
                        .with(LANGUAGE, language)
                        .with(REASON, request.getReason())
                        .with(DEADLINE, deadline)
                        .with(NOTE_COUNT, noteCount)
                        .with(CV_EXISTS, request.getCvId() != null)
                        .with(REQUEST_CANCELLED, false)
                        .build()
        );
    }

    /*
     * Existing CV: Edit it. No CV yet: The creation screen, preselected as far as the request knows.
     * Shared with the reminder job, so the request email and every reminder open the same screen.
     */
    public static String linkOf(UpdateRequest request) {
        String language = request.getLanguage().name();
        if (request.getCvId() != null) {
            return NotificationLink.CV_EDIT.path(request.getCvId());
        }
        if (request.getProfileId() != null) {
            return NotificationLink.CV_CREATE_FOR_PROFILE.path(request.getProfileId(), language);
        }
        return NotificationLink.CV_CREATE_FOR_LANGUAGE.path(language);
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

    private static String withReason(String sentence, String reason) {
        return reason == null || reason.isBlank() ? sentence : sentence + ". Reason: " + reason;
    }

    private static String withNotes(String sentence, int noteCount) {
        return noteCount == 0 ? sentence : "%s. %d feedback note(s) attached".formatted(sentence, noteCount);
    }
}
