package com.chat.utils.message;

import com.chat.service.dtos.chat.EnterRoomRequest;
import com.chat.service.dtos.chat.ReadUpToRequest;
import com.chat.service.dtos.chat.RoomActiveRequest;
import com.chat.service.dtos.chat.RoomInactiveRequest;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.SendDiscussionMessage;
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
@JsonSubTypes({
        @JsonSubTypes.Type(value = SendChat.class, name = "CHAT_MESSAGE"),
        @JsonSubTypes.Type(value = EnterRoomRequest.class, name = "ENTER_ROOM"),
        @JsonSubTypes.Type(value = RoomActiveRequest.class, name = "ROOM_ACTIVE"),
        @JsonSubTypes.Type(value = RoomInactiveRequest.class, name = "ROOM_INACTIVE"),
        @JsonSubTypes.Type(value = SendDiscussionMessage.class, name = "DISCUSSION_MESSAGE"),
        @JsonSubTypes.Type(value = ReadUpToRequest.class, name = "READ_UP_TO"),
        @JsonSubTypes.Type(value = BaseWebSocketMessage.class, name = "DEFAULT")
})
public class BaseWebSocketMessage {
    private MessageType messageType;
}
