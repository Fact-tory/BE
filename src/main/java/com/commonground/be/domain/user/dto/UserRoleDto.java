package com.commonground.be.domain.user.dto;

import com.commonground.be.domain.user.utils.UserRole;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class UserRoleDto {

	private String username;
	private UserRole userRole;

	public UserRoleDto(String username, UserRole userRole) {
		this.username = username;
		this.userRole = userRole;
	}
}
