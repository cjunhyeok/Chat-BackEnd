package com.chat.api;

import com.chat.api.request.chatroom.InviteMembersRequest;
import com.chat.api.request.chatroom.RenameSpaceRequest;
import com.chat.api.response.chatroom.RenameSpaceResponse;
import com.chat.api.response.chatroom.SpaceInviteCodeResponse;
import com.chat.api.response.chatroom.SpaceInviteInfoResponse;
import com.chat.api.response.chatroom.SpaceJoinResponse;
import com.chat.api.response.chatroom.SpaceMemberResponse;
import com.chat.api.response.chatroom.SpaceResponse;
import com.chat.api.response.chatroom.SpaceSummaryResponse;
import com.chat.api.request.chatroom.SaveSpaceRequest;
import com.chat.utils.consts.SessionConst;
import com.chat.service.SpaceService;
import com.chat.service.dtos.SaveSpaceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SpaceApiController {

    private final SpaceService spaceService;

    @PostMapping("/api/chat/room")
    public Result<SpaceResponse> chatRoom(@RequestBody SaveSpaceRequest request,
                                          @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        SaveSpaceDTO saveChatRooomDto = SaveSpaceDTO
                .builder()
                .senderId(loginMemberId)
                .title(request.getTitle())
                .build();
        Long chatRoomId = spaceService.saveSpace(saveChatRooomDto);

        return Result
                .<SpaceResponse>builder()
                .data(new SpaceResponse(chatRoomId))
                .status(HttpStatus.OK)
                .message("채팅방 생성이 완료됐습니다.")
                .build();
    }

    @GetMapping("/api/chat/rooms")
    public Result<List<SpaceSummaryResponse>> chatRooms(@SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        List<SpaceSummaryResponse> spaces = spaceService.findSpaces(loginMemberId);

        return Result
                .<List<SpaceSummaryResponse>>builder()
                .data(spaces)
                .status(HttpStatus.OK)
                .message("채팅방 목록 조회가 완료됐습니다.")
                .build();
    }

    @DeleteMapping("/api/chat/room/{chatRoomId}")
    public Result<Void> leaveChatRoom(@PathVariable Long chatRoomId,
                                      @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        spaceService.leaveSpace(loginMemberId, chatRoomId);

        return Result.<Void>builder()
                .status(HttpStatus.OK)
                .message("채팅방에서 나갔습니다.")
                .build();
    }

    @PatchMapping("/api/chat/room/{chatRoomId}")
    public Result<RenameSpaceResponse> renameChatRoom(@PathVariable Long chatRoomId,
                                       @RequestBody RenameSpaceRequest request,
                                       @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        RenameSpaceResponse response = spaceService.renameSpace(loginMemberId, chatRoomId, request.getTitle());

        return Result.<RenameSpaceResponse>builder()
                .data(response)
                .status(HttpStatus.OK)
                .message("채팅방 이름이 변경됐습니다.")
                .build();
    }

    @GetMapping("/api/chat/room/{chatRoomId}/members")
    public Result<List<SpaceMemberResponse>> chatRoomMembers(@PathVariable Long chatRoomId,
                                                             @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {
        List<SpaceMemberResponse> members = spaceService.findSpaceMembers(loginMemberId, chatRoomId);

        return Result.<List<SpaceMemberResponse>>builder()
                .data(members)
                .status(HttpStatus.OK)
                .message("멤버 목록 조회가 완료됐습니다.")
                .build();
    }

    @PostMapping("/api/chat/room/{chatRoomId}/members")
    public Result<Void> inviteMembers(@PathVariable Long chatRoomId,
                                      @RequestBody InviteMembersRequest request,
                                      @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        spaceService.inviteMembers(loginMemberId, chatRoomId, request.getMemberIds());

        return Result.<Void>builder()
                .status(HttpStatus.OK)
                .message("멤버 초대가 완료됐습니다.")
                .build();
    }

    @GetMapping("/api/spaces/{spaceId}/invite-code")
    public Result<SpaceInviteCodeResponse> getInviteCode(@PathVariable Long spaceId,
                                                         @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        SpaceInviteCodeResponse response = spaceService.getSpaceInviteCode(loginMemberId, spaceId);

        return Result.<SpaceInviteCodeResponse>builder()
                .data(response)
                .status(HttpStatus.OK)
                .message("초대 코드 조회가 완료됐습니다.")
                .build();
    }

    @GetMapping("/api/spaces/invite/{inviteCode}")
    public Result<SpaceInviteInfoResponse> getSpaceByInviteCode(@PathVariable String inviteCode,
                                                                @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        SpaceInviteInfoResponse response = spaceService.findSpaceByInviteCode(loginMemberId, inviteCode);

        return Result.<SpaceInviteInfoResponse>builder()
                .data(response)
                .status(HttpStatus.OK)
                .message("Space 정보 조회가 완료됐습니다.")
                .build();
    }

    @PostMapping("/api/spaces/invite/{inviteCode}/join")
    public Result<SpaceJoinResponse> joinSpaceByInviteCode(@PathVariable String inviteCode,
                                                           @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        Long spaceId = spaceService.joinSpaceByInviteCode(loginMemberId, inviteCode);

        return Result.<SpaceJoinResponse>builder()
                .data(new SpaceJoinResponse(spaceId))
                .status(HttpStatus.OK)
                .message("Space에 참여했습니다.")
                .build();
    }
}
