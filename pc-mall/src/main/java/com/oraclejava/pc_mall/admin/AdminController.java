package com.oraclejava.pc_mall.admin;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oraclejava.pc_mall.security.CurrentMember;

/**
 * 관리자 전용 회원 관리 API. 비로그인은 401, 일반 회원은 403(SecurityConfig의 /api/admin/** 규칙).
 * 관리자 여부는 토큰이 아니라 요청마다 DB의 현재 role로 판정된다.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final AdminService adminService;

	public AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	@GetMapping("/members")
	public List<AdminMemberResponse> members() {
		return adminService.members();
	}

	@PutMapping("/members/{id}/role")
	public AdminMemberResponse changeRole(@PathVariable Long id, @RequestBody RoleRequest request,
			Principal principal) {
		return adminService.changeRole(CurrentMember.idOf(principal), id, request.role());
	}

	@PostMapping("/members/{id}/ban")
	public AdminMemberResponse ban(@PathVariable Long id, @RequestBody BanRequest request, Principal principal) {
		return adminService.ban(CurrentMember.idOf(principal), id, request);
	}

	@DeleteMapping("/members/{id}/ban")
	public AdminMemberResponse unban(@PathVariable Long id) {
		return adminService.unban(id);
	}
}
