package com.commonground.be.domain.journal;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "journalists")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Journalist {

	@Id
	private String id;

	@NotBlank
	@Indexed
	private String name;

	@NotBlank
	@Indexed
	private String mediaOutletId;

	private String email;
	private String bio;
	private List<String> specialization;

	private Boolean isActive = true;

	@CreatedDate
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;
}
