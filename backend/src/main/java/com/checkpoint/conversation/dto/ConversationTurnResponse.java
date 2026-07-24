package com.checkpoint.conversation.dto;

import com.checkpoint.conversation.TopicTag;

public record ConversationTurnResponse(
        Integer turnNo, String question, String answer, TopicTag topicTag) {}
