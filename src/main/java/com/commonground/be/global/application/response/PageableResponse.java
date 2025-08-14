package com.commonground.be.global.application.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
public class PageableResponse<T> {

	private List<T> content;

	public PageableResponse(Page<T> page) {
		this.content = page.getContent();
	}
}
