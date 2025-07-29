package com.commonground.be.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ProfileUpdateRequestDto {

	@Email
	private String email;
	@NotBlank(message = "Required Nickname")
	private String nickname;
}
