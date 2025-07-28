package com.commonground.be.domain.user.dto;


import com.commonground.be.domain.user.utils.UserIdentity;
import lombok.Getter;

@Getter
public class PasswordFindRequest implements UserIdentity {

	private String username;
	private String name;
	private String email;
}
