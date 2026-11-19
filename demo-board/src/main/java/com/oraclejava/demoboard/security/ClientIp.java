package com.oraclejava.demoboard.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 제한 기준이 되는 클라이언트 IP. 위조할 수 있는 X-Forwarded-For는 신뢰하지 않고 연결 주소만 쓴다.
 * 리버스 프록시 뒤에 두면 모든 요청이 프록시 주소로 보이므로, 그때는 프록시가 채우는 주소를 신뢰하도록 따로 설정해야 한다.
 */
public final class ClientIp {

	private ClientIp() {
	}

	public static String of(HttpServletRequest request) {
		String ip = request.getRemoteAddr();
		return ip == null || ip.isBlank() ? "unknown" : ip;
	}
}
