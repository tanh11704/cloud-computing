package API_BoPhieu.service.poll;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import API_BoPhieu.constants.ExportFormat;
import API_BoPhieu.dto.poll.OptionStatsResponse;
import API_BoPhieu.dto.poll.OptionStatsWithEmailsResponse;
import API_BoPhieu.dto.poll.PollStatsResponse;
import API_BoPhieu.dto.poll.PollStatsWithEmailsResponse;
import API_BoPhieu.entity.Poll;
import API_BoPhieu.entity.User;
import API_BoPhieu.entity.Vote;
import API_BoPhieu.exception.EventException;
import API_BoPhieu.repository.EventRepository;
import API_BoPhieu.repository.PollRepository;
import API_BoPhieu.repository.UserRepository;
import API_BoPhieu.repository.VoteRepository;
import API_BoPhieu.service.file.FileExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollExportService {

    private final PollService pollService;
    private final FileExportService fileExportService;
    private final EventRepository eventRepository;
    private final PollRepository pollRepository;
    private final VoteRepository voteRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public byte[] exportPollStats(final Integer pollId, final ExportFormat format)
            throws IOException {
        log.info("Bắt đầu xuất kết quả poll ID {} ra định dạng {}", pollId, format);

        // Get poll statistics with emails
        final PollStatsWithEmailsResponse pollStatsWithEmails = getPollStatsWithEmails(pollId);

        // Get event title
        final String eventTitle = getEventTitle(pollId);

        // Hiện tại chỉ hỗ trợ xuất Excel
        return fileExportService.exportPollStatsToExcel(pollStatsWithEmails, eventTitle);
    }

    /**
     * Gets poll statistics with voter emails for export. Follows DRY principle by centralizing data
     * retrieval logic.
     */
    private PollStatsWithEmailsResponse getPollStatsWithEmails(final Integer pollId) {
        // Get basic poll stats
        final PollStatsResponse pollStats = pollService.getPollStats(pollId);

        // Get all votes for this poll
        final List<Vote> allVotes = voteRepository.findByPollId(pollId);

        // Group votes by optionId and get userIds
        final Map<Integer, List<Integer>> optionToUserIds =
                allVotes.stream().collect(Collectors.groupingBy(Vote::getOptionId,
                        Collectors.mapping(Vote::getUserId, Collectors.toList())));

        // Get all unique user IDs
        final Set<Integer> allUserIds =
                allVotes.stream().map(Vote::getUserId).collect(Collectors.toSet());

        // Fetch all users in one query (batch loading for performance)
        final Map<Integer, User> userMap = userRepository.findAllById(new ArrayList<>(allUserIds))
                .stream().collect(Collectors.toMap(User::getId, user -> user));

        // Build options with emails
        final List<OptionStatsWithEmailsResponse> optionsWithEmails = new ArrayList<>();
        if (pollStats.getOptions() != null) {
            for (final OptionStatsResponse option : pollStats.getOptions()) {
                final List<Integer> userIds =
                        optionToUserIds.getOrDefault(option.getId(), new ArrayList<>());
                final List<String> emails = userIds.stream().map(userMap::get)
                        .filter(user -> user != null && user.getEmail() != null).map(User::getEmail)
                        .sorted() // Sort emails alphabetically
                        .collect(Collectors.toList());

                final OptionStatsWithEmailsResponse optionWithEmails =
                        OptionStatsWithEmailsResponse.builder().id(option.getId())
                                .content(option.getContent()).voteCount(option.getVoteCount())
                                .percentage(option.getPercentage()).voterEmails(emails).build();

                optionsWithEmails.add(optionWithEmails);
            }
        }

        return PollStatsWithEmailsResponse.builder().id(pollStats.getId())
                .title(pollStats.getTitle()).pollType(pollStats.getPollType())
                .isDelete(pollStats.getIsDelete()).totalVotes(pollStats.getTotalVotes())
                .totalVoters(pollStats.getTotalVoters()).options(optionsWithEmails)
                .startTime(pollStats.getStartTime()).endTime(pollStats.getEndTime()).build();
    }

    /**
     * Gets the event title for the given poll. Follows DRY principle by centralizing event lookup.
     */
    private String getEventTitle(final Integer pollId) {
        final Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new EventException("Không tìm thấy poll với ID: " + pollId));

        return eventRepository.findById(poll.getEventId()).map(event -> event.getTitle())
                .orElseThrow(
                        () -> new EventException("Không tìm thấy sự kiện cho poll ID: " + pollId));
    }
}

