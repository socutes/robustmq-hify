package com.hify.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatMessage {

    /** user / assistant / system / tool */
    private String role;

    private String content;

    /** assistant 发出 tool_calls 时携带，role=assistant 时有值 */
    private java.util.List<ChatResponse.ToolCall> toolCalls;

    /** role=tool 时必填，对应 assistant tool_calls 里的 id */
    private String toolCallId;

    public static ChatMessage user(String content) {
        ChatMessage m = new ChatMessage();
        m.role = "user";
        m.content = content;
        return m;
    }

    public static ChatMessage assistant(String content) {
        ChatMessage m = new ChatMessage();
        m.role = "assistant";
        m.content = content;
        return m;
    }

    public static ChatMessage system(String content) {
        ChatMessage m = new ChatMessage();
        m.role = "system";
        m.content = content;
        return m;
    }
}
