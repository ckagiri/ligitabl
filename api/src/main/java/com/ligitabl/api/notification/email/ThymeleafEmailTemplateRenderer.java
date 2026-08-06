package com.ligitabl.api.notification.email;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.ligitabl.api.shared.Either;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ThymeleafEmailTemplateRenderer implements EmailTemplateRenderer {
    private final TemplateEngine templateEngine;

    @Override
    public Either<EmailError, EmailContent> render(EmailCommand.EmailType emailType, Map<String, Object> templateData) {
        try {
            Context context = new Context();
            context.setVariables(templateData);

            String templateName = templateNameFor(emailType);
            String subject = subjectFor(emailType, templateData);
            String htmlBody = templateEngine.process(templateName, context);

            return Either.right(new EmailContent(subject, htmlBody, toPlainText(htmlBody)));
        } catch (Exception e) {
            return Either.left(new EmailError.TemplateRenderError(emailType.name(), e.getMessage()));
        }
    }

    /**
     * The {@code text/plain} alternative, derived from the rendered HTML.
     *
     * <p>Deliberately still a regex rather than a parser: these are our own templates, not
     * arbitrary input, and adding an HTML parser to send email is a poor trade. The tests assert
     * the properties that matter ({@code no braces, no entities}) rather than exact output.
     */
    static String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        return html
                // Whole elements whose content is code, not prose. Must run before tag-stripping,
                // which would otherwise leave the bodies behind.
                .replaceAll("(?is)<(style|script|head)\\b.*?</\\1>", "")
                // Block boundaries become line breaks so the text isn't one run-on paragraph.
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|h[1-6]|li)>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&middot;", "·")
                .replace("&ndash;", "–")
                .replace("&mdash;", "—")
                .replace("&bull;", "•")
                .replace("&copy;", "©")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&rsquo;", "’")
                // &amp; last: unescaping it earlier would turn "&amp;lt;" into "<".
                .replace("&amp;", "&")
                // Trailing spaces from the HTML's own indentation, then runs of blank lines.
                .replaceAll("[ \\t]+\n", "\n")
                .replaceAll("\n[ \\t]+", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String templateNameFor(EmailCommand.EmailType emailType) {
        return switch (emailType) {
            case PASSWORD_RESET -> "email/password-reset";
            case PASSWORD_RESET_CONFIRMATION -> "email/password-reset-confirmation";
            case EMAIL_VERIFICATION -> "email/email-verification";
            case ROUND_RESULTS -> "email/round-results";
            case JOIN_REMINDER -> "email/join-reminder";
            case SEASON_WELCOME -> "email/season-welcome";
            case SEGMENT_RESULTS -> "email/segment-results";
        };
    }

    private String subjectFor(EmailCommand.EmailType emailType, Map<String, Object> templateData) {
        return switch (emailType) {
            case PASSWORD_RESET -> "Reset your LigiPredictor password";
            case PASSWORD_RESET_CONFIRMATION -> "Your LigiPredictor password has been changed";
            case EMAIL_VERIFICATION -> "Verify your LigiPredictor email";
            case ROUND_RESULTS -> "Your Gameweek %s Results — %s points!"
                    .formatted(templateData.get("round"), templateData.get("score"));
            case JOIN_REMINDER -> joinReminderSubject((Integer) templateData.get("stage"));
            case SEASON_WELCOME -> "The season's underway — you have 5 swaps";
            case SEGMENT_RESULTS -> segmentResultsSubject(templateData);
        };
    }

    /**
     * Names the <em>largest</em> segment the reader placed in, so someone who took both a sprint
     * and the quarter it closes reads the quarter — the bigger achievement — rather than whichever
     * block happens to sort first. The enqueuer picks that headline; this only formats it.
     */
    private String segmentResultsSubject(Map<String, Object> templateData) {
        String name = String.valueOf(templateData.get("headlineName"));
        int rank = (Integer) templateData.get("headlineRank");

        if (Boolean.TRUE.equals(templateData.get("isSeasonFinale"))) {
            return rank == 1
                    ? "You won the season 🏆"
                    : "The season's done — you finished %s".formatted(ordinal(rank));
        }
        return rank == 1
                ? "%s is yours 🏆".formatted(name)
                : "%s wrapped — you finished %s".formatted(name, ordinal(rank));
    }

    /** Only ever called with a podium rank, but written generally so 4th never renders "4rd". */
    private static String ordinal(int n) {
        int lastTwo = Math.abs(n) % 100;
        if (lastTwo >= 11 && lastTwo <= 13) {
            return n + "th";
        }
        return n
                + switch (Math.abs(n) % 10) {
                    case 1 -> "st";
                    case 2 -> "nd";
                    case 3 -> "rd";
                    default -> "th";
                };
    }

    /**
     * Escalating urgency as the reminder stage climbs — the exact stage-day thresholds are
     * config (see {@code ligitabl.email.join-reminder.stage-days}), so this buckets by range
     * rather than matching specific day counts, and stays sensible if that config changes.
     */
    private String joinReminderSubject(Integer stage) {
        if (stage == null || stage <= 1) {
            return "Set your table for the season!";
        }
        if (stage < 10) {
            return "Still haven't set your table?";
        }
        return "Last chance to set your table before reminders stop";
    }
}
