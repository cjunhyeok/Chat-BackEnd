package com.chat;

import com.chat.entity.Member;
import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.SpaceMember;
import com.chat.repository.MemberRepository;
import com.chat.repository.MessageRepository;
import com.chat.repository.SpaceMemberRepository;
import com.chat.repository.SpaceRepository;
import com.chat.service.utils.PasswordEncoder;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 01-cold-room-entry 성능 테스트용 샘플 데이터 생성기
 *
 * 생성되는 데이터:
 * - 테스트 계정: 200명 (user1~user200, 비밀번호 = username)
 * - 공유 테스트 룸: 1개 ("Performance Test Room")
 * - 모든 테스트 계정이 동일 룸에 참여
 * - 초기 메시지: 100개 (메시지 없을 때만)
 *
 * 멱등성: 서버 재시작 시 중복 생성 없이 안전하게 skip
 * 활성화: --spring.profiles.active=dev 또는 local
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"dev", "local"})
public class SampleDataInitializer {

    private final SampleDataService sampleDataService;

    @PostConstruct
    public void init() {
        sampleDataService.generateSampleData();
    }

    @Component
    @RequiredArgsConstructor
    static class SampleDataService {

        private final MemberRepository memberRepository;
        private final SpaceRepository spaceRepository;
        private final SpaceMemberRepository spaceMemberRepository;
        private final MessageRepository messageRepository;
        private final PasswordEncoder passwordEncoder;
        private final EntityManager entityManager;

        private static final int TEST_USER_COUNT = 200;
        private static final String PERFORMANCE_TEST_ROOM_TITLE = "Performance Test Room";
        private static final int INITIAL_MESSAGE_COUNT = 100;
        private static final int BATCH_SIZE = 50;

        private static final String[] SAMPLE_MESSAGES = {
            "안녕하세요!", "오늘 작업 어떻게 돼가요?", "코드 리뷰 부탁드립니다",
            "빌드 완료됐습니다", "배포 준비 중입니다", "PR 올렸습니다 확인해주세요",
            "이슈 확인했습니다", "수정 완료했습니다", "테스트 통과했습니다",
            "다음 스프린트 계획 잡아야 할 것 같아요", "ㅇㅇ 알겠습니다", "감사합니다",
            "조금 늦을 것 같아요", "지금 확인해볼게요", "공유 감사해요",
            "버그 발견했습니다", "핫픽스 배포했습니다", "로그 확인해주세요",
        };

        private final Random random = new Random(42);

        @Transactional
        public void generateSampleData() {
            long startTime = System.currentTimeMillis();
            log.info("======================================================");
            log.info("테스트 데이터 생성 시작 (01-cold-room-entry)");
            log.info("======================================================");

            List<Long> memberIds = createOrFindMembers();
            log.info("테스트 계정 {}명 준비 완료 (user1~user{})", memberIds.size(), TEST_USER_COUNT);

            Long roomId = findOrCreateTestRoom();
            log.info("테스트 룸 준비 완료: id={}, title={}", roomId, PERFORMANCE_TEST_ROOM_TITLE);

            int joined = joinMembersToRoom(memberIds, roomId);
            log.info("룸 참여 처리: {}명 신규 추가", joined);

            int messages = createInitialMessagesIfEmpty(memberIds, roomId);
            log.info("메시지 {}개 준비 완료", messages);

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("======================================================");
            log.info("테스트 데이터 생성 완료 ({}초)", duration);
            log.info("로그인: user1/user1 ~ user{0}/user{0}", TEST_USER_COUNT);
            log.info("공유 룸 ID: {}", roomId);
            log.info("======================================================");
        }

        private List<Long> createOrFindMembers() {
            List<Long> memberIds = new ArrayList<>();

            for (int i = 1; i <= TEST_USER_COUNT; i++) {
                String username = "user" + i;
                String nickname = "사용자" + i;

                Long memberId = memberRepository.findByUsername(username)
                        .map(Member::getId)
                        .orElseGet(() -> {
                            String encoded = passwordEncoder.encode(username);
                            Member saved = memberRepository.save(Member.of(username, encoded, nickname));
                            return saved.getId();
                        });

                memberIds.add(memberId);

                if (i % BATCH_SIZE == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }

            entityManager.flush();
            entityManager.clear();
            return memberIds;
        }

        private Long findOrCreateTestRoom() {
            List<Long> existingIds = entityManager
                    .createQuery("SELECT s.id FROM Space s WHERE s.title = :title", Long.class)
                    .setParameter("title", PERFORMANCE_TEST_ROOM_TITLE)
                    .getResultList();

            if (!existingIds.isEmpty()) {
                return existingIds.get(0);
            }

            Space room = spaceRepository.save(Space.of(PERFORMANCE_TEST_ROOM_TITLE));
            entityManager.flush();
            return room.getId();
        }

        private int joinMembersToRoom(List<Long> memberIds, Long roomId) {
            Set<Long> existingMemberIds = spaceMemberRepository.findAllFetchMemberBy(roomId)
                    .stream()
                    .map(sm -> sm.getMember().getId())
                    .collect(Collectors.toSet());

            Space room = entityManager.getReference(Space.class, roomId);
            int count = 0;

            for (Long memberId : memberIds) {
                if (!existingMemberIds.contains(memberId)) {
                    Member member = entityManager.getReference(Member.class, memberId);
                    spaceMemberRepository.save(SpaceMember.of(member, room));
                    count++;
                }
            }

            if (count > 0) {
                entityManager.flush();
                entityManager.clear();
            }

            return count;
        }

        private int createInitialMessagesIfEmpty(List<Long> memberIds, Long roomId) {
            boolean hasMessages = messageRepository.findLastMessageIdBy(roomId).isPresent();
            if (hasMessages) {
                return 0;
            }

            Space room = entityManager.getReference(Space.class, roomId);

            for (int i = 0; i < INITIAL_MESSAGE_COUNT; i++) {
                Long senderId = memberIds.get(random.nextInt(memberIds.size()));
                Member sender = entityManager.getReference(Member.class, senderId);
                String content = SAMPLE_MESSAGES[random.nextInt(SAMPLE_MESSAGES.length)];
                messageRepository.save(Message.of(content, sender, room));
            }

            entityManager.flush();
            entityManager.clear();
            return INITIAL_MESSAGE_COUNT;
        }
    }
}
