package com.checkpoint.conversation.dto;

import com.checkpoint.conversation.TopicTag;
import jakarta.validation.constraints.NotBlank;

public record ConversationRequest(@NotBlank String question, TopicTag topicTag) {}
