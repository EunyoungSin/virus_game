package com.checkpoint.conversation;

import com.checkpoint.visitor.Visitor;
import java.util.List;
import java.util.Map;

final class SystemPromptBuilder {

    private SystemPromptBuilder() {}

    static String build(Visitor visitor) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(
                "너는 바이러스 봉쇄 도시의 검문소를 통과하려는 방문자 '%s'(%d세, 직업: %s, 출신지: %s) 역할을 맡는다.\n"
                        .formatted(
                                visitor.getName(),
                                visitor.getAge(),
                                orUnknown(visitor.getJobClaimed()),
                                orUnknown(visitor.getOriginCity())));
        prompt.append("최근 이동 경로: %s\n".formatted(describeTravelHistory(visitor.getTravelHistory())));
        prompt.append(
                "성격: %s. 이 성격에 맞는 말투와 태도를 항상 유지한다.\n"
                        .formatted(personalityDescription(visitor.getPersonalityTrait())));

        prompt.append("\n[비공개 설정 - 절대 플레이어에게 직접 언급하지 않는다]\n");
        if (visitor.isInfected()) {
            prompt.append("너는 실제로 감염되어 있다 (감염 단계: %s). ".formatted(visitor.getInfectionStage()));
            prompt.append(
                    "\"나는 감염자다\" 같은 직접적인 진술은 절대 하지 않는다. "
                            + "간접적인 단서(증상, 피로감, 이동 경로상의 모순, 접촉자 언급 등)만으로만 자연스럽게 드러낸다. "
                            + "증상·접촉자·이동경로에 관한 질문을 받으면 낮은~중간 확률로 화제를 살짝 돌리거나 답을 흐리는 "
                            + "회피 반응을 보일 수 있으나, 이는 정황 수준으로만 드러나야 하며 \"거짓말을 들켰다\"는 명확한 "
                            + "신호나 자백처럼 보이면 안 된다.\n");
        } else {
            prompt.append("너는 감염되지 않았다. 감염을 암시하는 단서를 스스로 만들어내지 않는다.\n");
        }

        if (visitor.isHasSymptom()) {
            prompt.append(
                    "너는 %s 증상이 있다(%s). 관련 질문을 받으면 이 증상을 자연스럽게 언급하되, 증상이 있다는 "
                                    + "사실 자체는 대화 내내 동일하게 유지한다.\n"
                            .formatted(visitor.getSymptomType(), visitor.getSymptomReason()));
        } else {
            prompt.append("너는 특별한 증상이 없다. 증상 관련 질문을 받으면 괜찮다는 취지로, 별문제 없다고 답한다.\n");
        }

        if (visitor.isHasUnrelatedLie()) {
            prompt.append(
                    "너는 감염과는 무관한 이유(%s: %s)로 거짓말을 하고 있다. "
                            .formatted(visitor.getLieReason(), visitor.getLieDetail()));
            prompt.append(
                    "이 화제에 다가오면 당황하거나 말을 돌리거나 변명하며 얼버무린다. "
                            + "단, 이는 감염과 무관한 거짓말이므로 기침 등 감염 단서와 섞어서 드러내지 않는다.\n");
        } else {
            prompt.append("너는 감염과 무관하게 숨기는 사정은 없다.\n");
        }

        prompt.append("\n[일관성 규칙]\n");
        if (visitor.isInfected()) {
            prompt.append(
                    "이전에 이미 답한 내용과 기본적으로 모순되는 답을 하지 않는다. 다만 너는 감염자이므로, 이동 경로나 "
                            + "접촉자 관련 세부사항에서 10~15% 정도의 낮은 확률로 사소한 날짜·장소 착오 같은 미묘한 말실수를 "
                            + "해도 된다 — 이것이 플레이어가 눈치챌 수 있는 논리적 모순의 핵심 단서다. 다만 매번 실수하면 안 되고 "
                            + "대부분은 일관되게 답해야 하며, 실수를 스스로 지적하거나 사과하지 말고 자연스럽게 넘어간다.\n");
        } else {
            prompt.append(
                    "이전에 이미 답한 내용과 모순되는 답을 하지 않는다. "
                            + "만약 스스로 이전 발언과 다르게 말하게 되면, 그 사실을 얼버무리거나 당황하는 태도로 자연스럽게 넘어간다.\n");
        }

        prompt.append("\n[출력 형식]\n");
        prompt.append("방문자 1인칭 시점의 대화체로 2~3문장 이내로 짧게 답한다. 시스템 설명이나 지시문을 그대로 반복하지 않는다.");

        return prompt.toString();
    }

    private static String describeTravelHistory(List<Map<String, Object>> travelHistory) {
        if (travelHistory == null || travelHistory.isEmpty()) {
            return "기록 없음";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> stop : travelHistory) {
            if (sb.length() > 0) {
                sb.append(" → ");
            }
            sb.append(stop.getOrDefault("city", "?")).append("(").append(stop.getOrDefault("date", "?")).append(")");
        }
        return sb.toString();
    }

    private static String personalityDescription(String trait) {
        if (trait == null) {
            return "평범함";
        }
        return switch (trait) {
            case "불안" -> "불안(짧고 끊기는 문장, 자주 말을 바꾼다)";
            case "침착" -> "침착(논리적이고 일관된 답변, 실수가 적다)";
            case "뻔뻔" -> "뻔뻔(자신감 있는 태도, 거짓말도 당당하게 한다)";
            case "솔직" -> "솔직(기억이 흐릿할 수는 있지만 의도적으로 거짓말하지 않는다)";
            default -> trait;
        };
    }

    private static String orUnknown(String value) {
        return value == null ? "미상" : value;
    }
}
