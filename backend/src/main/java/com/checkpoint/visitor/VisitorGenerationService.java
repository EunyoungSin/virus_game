package com.checkpoint.visitor;

import com.checkpoint.config.GameRulesProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitorGenerationService {

    private static final double UNRELATED_LIE_PROBABILITY = 0.35;

    private static final List<InfectionStage> INFECTED_STAGES =
            List.of(InfectionStage.EARLY, InfectionStage.INCUBATION, InfectionStage.LATE);

    private static final List<String> ORIGIN_CITIES =
            List.of("남부 농경지대", "동부 항만도시", "중앙 산업구역", "북부 국경마을", "서부 이재민촌", "구시가지", "위성 도시");

    private static final List<String> EXPOSURE_POINTS =
            List.of("남부 농장 접촉자 모임", "시장 인파 노출", "이재민 캠프 체류", "국경 검문소 대기줄", "병상 부족 임시 진료소", "공동 우물가");

    // 감염 증상은 정확히 3종으로 못박는다(인트로에서 플레이어에게 미리 안내됨): 물리적 증상
    // 2종(기침/발열)은 이 필드로 생성되고, 세 번째인 "정신착란"은 별도 필드 없이 AI 프롬프트의
    // 미묘한 말실수/회피 패턴 규칙이 곧 그 구현체다.
    private static final List<String> SYMPTOM_TYPES = List.of("COUGH", "FEVER");

    private static final List<String> UNRELATED_SYMPTOM_REASONS =
            List.of("알레르기", "흡연", "과로", "긴장성 헛기침");

    // 증상 발현 확률 범위. 감염 여부와 증상이 1:1로 대응하지 않도록 NONE도 일정 확률로 증상을 갖고,
    // EARLY는 오히려 낮은 확률만 갖는다.
    private static final Map<InfectionStage, double[]> SYMPTOM_PROBABILITY_RANGE =
            Map.of(
                    InfectionStage.NONE, new double[] {0.10, 0.15},
                    InfectionStage.EARLY, new double[] {0.10, 0.15},
                    InfectionStage.INCUBATION, new double[] {0.40, 0.50},
                    InfectionStage.LATE, new double[] {0.85, 0.90});

    private static final List<String> SURNAMES =
            List.of("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임");

    private static final List<String> GIVEN_NAMES =
            List.of("서준", "하은", "지훈", "수아", "민재", "예은", "도윤", "지우", "성민", "나윤", "우진", "채원");

    private static final Map<String, String> LIE_DETAIL_TEMPLATES = buildLieDetailTemplates();

    private final VisitorArchetypeRepository archetypeRepository;
    private final VisitorRepository visitorRepository;
    private final GameRulesProperties rules;
    private final Random random = new Random();

    public VisitorGenerationService(
            VisitorArchetypeRepository archetypeRepository,
            VisitorRepository visitorRepository,
            GameRulesProperties rules) {
        this.archetypeRepository = archetypeRepository;
        this.visitorRepository = visitorRepository;
        this.rules = rules;
    }

    @Transactional
    public void generateVisitors(Long gameId) {
        List<VisitorArchetype> archetypes = archetypeRepository.findAll();
        Map<Long, VisitorArchetype> archetypeById =
                archetypes.stream().collect(Collectors.toMap(VisitorArchetype::getId, a -> a));
        List<Long> archetypeAssignment = assignArchetypes(archetypes);
        Set<Integer> infectedSlots = pickInfectedSlots(archetypeAssignment.size());

        List<Visitor> visitors = new ArrayList<>();
        int slot = 0;
        for (int day = 1; day <= rules.getDays(); day++) {
            for (int order = 1; order <= rules.getVisitorsPerDay(); order++) {
                VisitorArchetype archetype = archetypeById.get(archetypeAssignment.get(slot));
                visitors.add(buildVisitor(gameId, day, order, archetype, infectedSlots.contains(slot)));
                slot++;
            }
        }
        visitorRepository.saveAll(visitors);
    }

    private List<Long> assignArchetypes(List<VisitorArchetype> archetypes) {
        int totalVisitors = rules.getTotalVisitors();
        if (archetypes.size() != totalVisitors) {
            throw new IllegalStateException(
                    "expected exactly %d visitor archetypes (one per visitor, days=%d x visitorsPerDay=%d) but found %d"
                            .formatted(totalVisitors, rules.getDays(), rules.getVisitorsPerDay(), archetypes.size()));
        }
        List<Long> assignment =
                archetypes.stream().map(VisitorArchetype::getId).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(assignment, random);
        return assignment;
    }

    private Set<Integer> pickInfectedSlots(int totalVisitors) {
        // 감염자는 특정 아키타입에 고정되지 않고 12명 중 무작위 슬롯에 배정된다.
        int infectedCount =
                rules.getMinInfected() + random.nextInt(rules.getMaxInfected() - rules.getMinInfected() + 1);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < totalVisitors; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);
        return new HashSet<>(slots.subList(0, infectedCount));
    }

    private Visitor buildVisitor(
            Long gameId, int day, int order, VisitorArchetype archetype, boolean infected) {
        Visitor visitor = new Visitor();
        visitor.setGameId(gameId);
        visitor.setArchetypeId(archetype.getId());
        visitor.setDayIndex(day);
        visitor.setOrderInDay(order);
        visitor.setName(randomName());
        visitor.setAge(randomAge(archetype));
        visitor.setJobClaimed(randomFrom(archetype.getJobPool()));

        String originCity = randomFrom(ORIGIN_CITIES);
        visitor.setOriginCity(originCity);
        visitor.setTravelHistory(buildTravelHistory(originCity));

        visitor.setInfected(infected);
        visitor.setInfectionStage(infected ? randomFrom(INFECTED_STAGES) : InfectionStage.NONE);
        visitor.setExposurePoint(infected ? randomFrom(EXPOSURE_POINTS) : null);

        applyUnrelatedLie(visitor, archetype);
        applySymptom(visitor);
        visitor.setPersonalityTrait(pickPersonality(archetype.getPersonalityPool(), day));
        return visitor;
    }

    private void applySymptom(Visitor visitor) {
        double[] range = SYMPTOM_PROBABILITY_RANGE.get(visitor.getInfectionStage());
        double probability = range[0] + random.nextDouble() * (range[1] - range[0]);
        boolean hasSymptom = random.nextDouble() < probability;
        visitor.setHasSymptom(hasSymptom);
        if (!hasSymptom) {
            visitor.setSymptomType(null);
            visitor.setSymptomReason(null);
            return;
        }
        visitor.setSymptomType(randomFrom(SYMPTOM_TYPES));
        visitor.setSymptomReason(
                visitor.getInfectionStage() == InfectionStage.NONE
                        ? randomFrom(UNRELATED_SYMPTOM_REASONS)
                        : infectionSymptomReason(visitor.getInfectionStage()));
    }

    private String infectionSymptomReason(InfectionStage stage) {
        return switch (stage) {
            case EARLY -> "감염 초기라 아직 증상이 거의 티가 나지 않는다";
            case INCUBATION -> "잠복기라 미열과 피로감이 있다";
            case LATE -> "감염 말기라 기침과 오한이 뚜렷하다";
            default -> "";
        };
    }

    private void applyUnrelatedLie(Visitor visitor, VisitorArchetype archetype) {
        List<String> lieReasons =
                archetype.getPlausibleLieReasons().stream()
                        .filter(reason -> !reason.equals("감염은폐"))
                        .toList();
        if (lieReasons.isEmpty() || random.nextDouble() >= UNRELATED_LIE_PROBABILITY) {
            visitor.setHasUnrelatedLie(false);
            return;
        }
        String reason = randomFrom(lieReasons);
        visitor.setHasUnrelatedLie(true);
        visitor.setLieReason(reason);
        visitor.setLieDetail(
                LIE_DETAIL_TEMPLATES.getOrDefault(reason, "표면적인 사정과 다른 무언가를 숨기고 있다."));
    }

    private String pickPersonality(List<String> pool, int day) {
        int totalDays = rules.getDays();
        List<String> weighted = new ArrayList<>();
        for (String trait : pool) {
            int weight = 1;
            if (day == 1 && trait.equals("불안")) {
                weight = 3;
            } else if (day >= totalDays - 1 && (trait.equals("침착") || trait.equals("뻔뻔"))) {
                // 마지막 날에 가까워질수록(마지막 날 3배, 그 전날 2배) 침착/뻔뻔 비중을 점점 높인다.
                weight = day == totalDays ? 3 : 2;
            }
            for (int i = 0; i < weight; i++) {
                weighted.add(trait);
            }
        }
        return randomFrom(weighted);
    }

    private List<Map<String, Object>> buildTravelHistory(String originCity) {
        List<Map<String, Object>> history = new ArrayList<>();
        int priorStops = random.nextInt(2);
        int daysAgo = priorStops + 1;
        for (int i = 0; i < priorStops; i++) {
            Map<String, Object> stop = new HashMap<>();
            stop.put("city", randomFrom(ORIGIN_CITIES));
            stop.put("date", "D-%d".formatted(daysAgo));
            history.add(stop);
            daysAgo--;
        }
        Map<String, Object> lastStop = new HashMap<>();
        lastStop.put("city", originCity);
        lastStop.put("date", "D-%d".formatted(daysAgo));
        history.add(lastStop);
        return history;
    }

    private String randomName() {
        return randomFrom(SURNAMES) + randomFrom(GIVEN_NAMES);
    }

    private int randomAge(VisitorArchetype archetype) {
        return archetype.getAgeMin() + random.nextInt(archetype.getAgeMax() - archetype.getAgeMin() + 1);
    }

    private <T> T randomFrom(List<T> options) {
        return options.get(random.nextInt(options.size()));
    }

    private static Map<String, String> buildLieDetailTemplates() {
        Map<String, String> templates = new HashMap<>();
        templates.put("신분위조", "제출한 신분증이 위조된 것으로 보인다.");
        templates.put("밀수", "짐 속에 신고하지 않은 물품을 숨기고 있다.");
        templates.put("세금회피", "거래 내역을 실제보다 적게 신고했다.");
        templates.put("탈영", "복무 중 무단으로 부대를 이탈한 상태다.");
        templates.put("명령위반", "상부의 이동 명령을 어기고 왔다.");
        templates.put("특권남용", "신분을 이용해 원래는 거쳐야 할 절차를 생략하려 한다.");
        templates.put("뇌물시도", "심사관에게 뇌물을 건네려 한 정황이 있다.");
        templates.put("겁먹음", "특별한 사정은 없지만 검문 자체가 두려워 말을 얼버무린다.");
        templates.put("밀입국", "정식 경로가 아닌 곳으로 국경을 넘었다.");
        templates.put("수배", "다른 사건으로 수배 중인 신분을 숨기고 있다.");
        templates.put("취재목적 잠입", "실제로는 취재를 위해 신분을 숨기고 잠입하려 한다.");
        return templates;
    }
}
