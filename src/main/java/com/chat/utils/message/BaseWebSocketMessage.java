package com.chat.utils.message;

import com.chat.service.dtos.chat.EnterChatRoom;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "messageType", // JSON의 "type" 필드를 사용
        visible = true
)
@JsonSubTypes({ // MessageType enum의 이름(name())과 매핑
        @JsonSubTypes.Type(value = SendChat.class, name = "CHAT_MESSAGE"),
        @JsonSubTypes.Type(value = EnterChatRoom.class, name = "CHAT_ENTER"), // Enum 이름과 동일하게
        @JsonSubTypes.Type(value = UpdateChatRoom.class, name = "UPDATE_CHAT_ROOM"),
        // 다른 타입이 있다면 여기에 추가
        @JsonSubTypes.Type(value = BaseWebSocketMessage.class, name = "DEFAULT") // 알 수 없는 타입 처리
})
public class BaseWebSocketMessage {
    private MessageType messageType;
}
