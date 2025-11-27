package API_BoPhieu.service.poll;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import API_BoPhieu.dto.poll.OptionStatsResponse;
import API_BoPhieu.dto.poll.PollDTO;
import API_BoPhieu.dto.poll.PollResponse;
import API_BoPhieu.dto.poll.PollStatsResponse;
import API_BoPhieu.dto.poll.UpdatePollDTO;
import API_BoPhieu.dto.poll.VoteDTO;
import API_BoPhieu.entity.Event;
import API_BoPhieu.entity.Option;
import API_BoPhieu.entity.Poll;
import API_BoPhieu.entity.User;
import API_BoPhieu.entity.Vote;
import API_BoPhieu.exception.AuthException;
import API_BoPhieu.exception.ConflictException;
import API_BoPhieu.exception.EventException;
import API_BoPhieu.exception.PollException;
import API_BoPhieu.mapper.PollMapper;
import API_BoPhieu.repository.EventRepository;
import API_BoPhieu.repository.OptionRepository;
import API_BoPhieu.repository.PollRepository;
import API_BoPhieu.repository.UserRepository;
import API_BoPhieu.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollServiceImpl implements PollService {
    private final PollRepository pollRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final OptionRepository optionRepository;
    private final VoteRepository voteRepository;

    @Override
    @Transactional
    public PollResponse createPoll(PollDTO pollDTO, Authentication auth) {
        final String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Không tìm thấy người dùng!"));

        Event event = eventRepository.findById(pollDTO.getEventId()).orElseThrow(
                () -> new EventException("Không tìm thấy sự kiện với ID: " + pollDTO.getEventId()));

        // Validate thời gian
        if (pollDTO.getEndTime() != null && pollDTO.getStartTime() != null
                && !pollDTO.getEndTime().isAfter(pollDTO.getStartTime())) {
            throw new ConflictException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        Poll poll = new Poll();
        poll.setEventId(event.getId());
        poll.setTitle(pollDTO.getTitle());
        poll.setPollType(pollDTO.getPollType());
        poll.setIsDelete(false);
        poll.setStartTime(pollDTO.getStartTime());
        poll.setEndTime(pollDTO.getEndTime());
        poll.setCreatedBy(user.getId());

        pollRepository.save(poll);

        List<Option> options = pollDTO.getOptions().stream().map(optionRequest -> {
            Option option = new Option();
            option.setPollId(poll.getId());
            option.setContent(optionRequest.getContent());
            option.setImageUrl(optionRequest.getImageUrl());
            return option;
        }).collect(Collectors.toList());
        optionRepository.saveAll(options);

        List<Option> savedOptions = optionRepository.findByPollId(poll.getId());

        Map<Integer, Integer> optionVoteCounts =
                getVoteCountsFromDatabase(poll.getId(), savedOptions);
        return PollMapper.toPollResponse(poll, savedOptions, optionVoteCounts);
    }

    @Override
    @Transactional(readOnly = true)
    public PollResponse getPoll(Integer pollId, Integer userId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new EventException("Không tìm thấy poll với ID: " + pollId));

        List<Option> options = optionRepository.findByPollId(pollId);

        Map<Integer, Integer> optionVoteCounts = getVoteCountsFromDatabase(pollId, options);

        PollResponse response = PollMapper.toPollResponse(poll, options, optionVoteCounts);

        boolean hasVoted = voteRepository.existsByPollIdAndUserId(pollId, userId);
        response.setHasVoted(hasVoted);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PollResponse> getPollsByEvent(Integer eventId) {
        List<Poll> polls = pollRepository.findByEventId(eventId);
        List<PollResponse> responses = new ArrayList<>();

        for (Poll poll : polls) {
            List<Option> options = optionRepository.findByPollId(poll.getId());

            Map<Integer, Integer> optionVoteCounts =
                    getVoteCountsFromDatabase(poll.getId(), options);

            responses.add(PollMapper.toPollResponse(poll, options, optionVoteCounts));
        }

        return responses;
    }

    @Override
    @Transactional
    public void vote(Integer pollId, VoteDTO voteRequest, Authentication auth) {
        final String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Không tìm thấy người dùng!"));
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new EventException("Không tìm thấy poll với ID: " + pollId));

        if (poll.getIsDelete() == true) {
            throw new PollException("Poll không mở để vote");
        }

        List<Vote> oldVotes = voteRepository.findByPollIdAndUserId(pollId, user.getId());
        if (!oldVotes.isEmpty()) {
            voteRepository.deleteAll(oldVotes);
        }

        for (Integer optionId : voteRequest.getOptionIds()) {
            Vote vote = new Vote();
            vote.setPollId(pollId);
            vote.setUserId(user.getId());
            vote.setOptionId(optionId);
            voteRepository.save(vote);
        }

        log.info("User {} đã vote cho poll {} với options: {}", user.getId(), pollId,
                voteRequest.getOptionIds());

    }

    @Override
    @Transactional(readOnly = true)
    public List<PollStatsResponse> getPollStatsByEvent(Integer eventId) {
        List<Poll> polls = pollRepository.findByEventId(eventId);
        List<PollStatsResponse> statsResponses = new ArrayList<>();

        for (Poll poll : polls) {
            PollStatsResponse stats = getPollStats(poll.getId());
            statsResponses.add(stats);
        }

        return statsResponses;
    }

    @Override
    @Transactional(readOnly = true)
    public PollStatsResponse getPollStats(Integer pollId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new EventException("Không tìm thấy poll với ID: " + pollId));

        List<Option> options = optionRepository.findByPollId(pollId);

        Map<Integer, Integer> optionVoteCounts = getVoteCountsFromDatabase(pollId, options);

        int totalVotes = optionVoteCounts.values().stream().mapToInt(Integer::intValue).sum();

        Integer totalVotersCount = voteRepository.countDistinctVotersByPollId(pollId);
        int totalVoters = totalVotersCount != null ? totalVotersCount : 0;

        List<OptionStatsResponse> optionStats = new ArrayList<>();
        for (Option option : options) {
            int voteCount = optionVoteCounts.getOrDefault(option.getId(), 0);
            double percentage = totalVotes > 0 ? (voteCount * 100.0 / totalVotes) : 0.0;

            OptionStatsResponse stat = new OptionStatsResponse();
            stat.setId(option.getId());
            stat.setContent(option.getContent());
            stat.setVoteCount(voteCount);
            stat.setPercentage(percentage);
            optionStats.add(stat);
        }

        PollStatsResponse statsResponse = new PollStatsResponse();
        statsResponse.setId(poll.getId());
        statsResponse.setTitle(poll.getTitle());
        statsResponse.setPollType(poll.getPollType());
        statsResponse.setIsDelete(poll.getIsDelete());
        statsResponse.setTotalVotes(totalVotes);
        statsResponse.setTotalVoters(totalVoters);
        statsResponse.setOptions(optionStats);
        statsResponse.setStartTime(poll.getStartTime());
        statsResponse.setEndTime(poll.getEndTime());

        return statsResponse;
    }

    @Override
    @Transactional
    public PollResponse closePoll(Integer pollId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new EventException("Không tìm thấy poll với ID: " + pollId));

        poll.setIsDelete(true);
        pollRepository.save(poll);

        List<Option> options = optionRepository.findByPollId(pollId);
        Map<Integer, Integer> optionVoteCounts = getVoteCountsFromDatabase(pollId, options);

        log.info("Poll {} đã được đóng", pollId);
        return PollMapper.toPollResponse(poll, options, optionVoteCounts);
    }

    @Override
    @Transactional
    public PollResponse updatePoll(UpdatePollDTO pollDto, Integer pollId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new EventException("Không tìm thấy poll với ID: " + pollId));

        // Validate thời gian
        if (pollDto.getEndTime() != null && pollDto.getStartTime() != null
                && !pollDto.getEndTime().isAfter(pollDto.getStartTime())) {
            throw new ConflictException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        poll.setTitle(pollDto.getTitle());
        poll.setPollType(pollDto.getPollType());
        poll.setStartTime(pollDto.getStartTime());
        poll.setEndTime(pollDto.getEndTime());

        pollRepository.save(poll);

        List<Option> options = pollDto.getOptions().stream().map(optionRequest -> {
            Option option = optionRepository.findById(optionRequest.getOptionId())
                    .orElseThrow(() -> new EventException(
                            "Không tìm thấy option với ID: " + optionRequest.getOptionId()));
            option.setPollId(poll.getId());
            option.setContent(optionRequest.getContent());
            return option;
        }).collect(Collectors.toList());

        optionRepository.saveAll(options);

        List<Option> savedOptions = optionRepository.findByPollId(poll.getId());
        Map<Integer, Integer> optionVoteCounts = getVoteCountsFromDatabase(pollId, savedOptions);

        log.info("Poll {} đã được cập nhật", pollId);
        return PollMapper.toPollResponse(poll, savedOptions, optionVoteCounts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> getVotedOptionIdsByUser(Integer pollId, Integer userId) {
        List<Vote> votes = voteRepository.findByPollIdAndUserId(pollId, userId);
        return votes.stream().map(Vote::getOptionId).collect(Collectors.toList());
    }

    private Map<Integer, Integer> getVoteCountsFromDatabase(Integer pollId, List<Option> options) {
        Map<Integer, Integer> counts = new HashMap<>();

        // Initialize all options with 0
        for (Option option : options) {
            counts.put(option.getId(), 0);
        }

        // Get vote counts from database
        List<Object[]> voteCounts = voteRepository.countVotesByOptionAndPollId(pollId);
        for (Object[] result : voteCounts) {
            Integer optionId = ((Number) result[0]).intValue();
            Long count = ((Number) result[1]).longValue();
            counts.put(optionId, count != null ? count.intValue() : 0);
        }

        return counts;
    }

}
