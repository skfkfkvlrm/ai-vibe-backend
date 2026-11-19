package com.oraclejava.demoboard.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oraclejava.demoboard.board.Board;
import com.oraclejava.demoboard.member.MemberResponse;

/**
 * 관리자 전용 API. 비로그인은 401, 일반 회원은 403.
 * 관리자 여부는 토큰이 아니라 요청마다 DB의 현재 role로 판정한다(MemberAccess).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("@memberAccess.isAdmin(authentication)")
public class AdminController {

	private final AdminService adminService;

	public AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	@GetMapping("/boards/deleted")
	public List<DeletedBoardResponse> deletedBoards() {
		return adminService.deletedBoards();
	}

	@PostMapping("/boards/{id}/restore")
	public Board restore(@PathVariable Long id) {
		return adminService.restore(id);
	}

	@GetMapping("/members")
	public List<MemberResponse> members() {
		return adminService.members();
	}
}
