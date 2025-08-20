package com.commonground.be.domain.analysis.controller;

import com.commonground.be.domain.analysis.dto.request.AnalysisRestartRequest;
import com.commonground.be.domain.analysis.dto.request.AnalysisStartRequest;
import com.commonground.be.domain.analysis.dto.response.AnalysisResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisResultResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisStatusResponse;
import com.commonground.be.domain.analysis.dto.response.MyAnalysesResponse;
import com.commonground.be.domain.analysis.service.AnalysisService;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

	private final AnalysisService analysisService;

	/**
	 * 분석 시작
	 * POST /api/v1/analysis/start
	 */
	@PostMapping("/start")
	public ResponseEntity<HttpResponseDto> startAnalysis(
			@AuthenticationPrincipal UserDetails userDetails,
			@RequestBody AnalysisStartRequest request
	) {
		try {
			if (userDetails == null) {
				return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS);
			}
			
			log.info("분석 시작 요청 - userId: {}, type: {}", userDetails.getUsername(), request.getType());

			AnalysisResponse response = analysisService.startAnalysis(userDetails.getUsername(), request);

			return ResponseUtils.of(
					ResponseCodeEnum.SUCCESS,
					response
			);
		} catch (CommonException e) {
			// CommonException은 GlobalExceptionAdvice에서 처리하도록 다시 던지기
			throw e;
		} catch (Exception e) {
			log.error("분석 시작 예상치 못한 오류 - userId: {}", userDetails != null ? userDetails.getUsername() : "unknown", e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 분석 결과 조회
	 * GET /api/v1/analysis/result/{analysisId}
	 */
	@GetMapping("/result/{analysisId}")
	public ResponseEntity<HttpResponseDto> getAnalysisResult(
			@AuthenticationPrincipal UserDetails userDetails,
			@PathVariable Long analysisId
	) {
		try {
			if (userDetails == null) {
				return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS);
			}
			
			log.info("분석 결과 조회 - userId: {}, analysisId: {}", userDetails.getUsername(), analysisId);

			AnalysisResultResponse response = analysisService.getAnalysisResult(userDetails.getUsername(), analysisId);

			return ResponseUtils.of(
					ResponseCodeEnum.SUCCESS,
					response
			);
		} catch (CommonException e) {
			throw e;
		} catch (Exception e) {
			log.error("분석 결과 조회 예상치 못한 오류 - userId: {}, analysisId: {}", userDetails != null ? userDetails.getUsername() : "unknown", analysisId, e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 분석 상태 조회
	 * GET /api/v1/analysis/status/{analysisId}
	 */
	@GetMapping("/status/{analysisId}")
	public ResponseEntity<HttpResponseDto> getAnalysisStatus(
			@AuthenticationPrincipal UserDetails userDetails,
			@PathVariable Long analysisId
	) {
		try {
			if (userDetails == null) {
				return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS);
			}
			
			log.info("분석 상태 조회 - userId: {}, analysisId: {}", userDetails.getUsername(), analysisId);

			AnalysisStatusResponse response = analysisService.getAnalysisStatus(userDetails.getUsername(), analysisId);

			return ResponseUtils.of(
					ResponseCodeEnum.SUCCESS,
					response
			);
		} catch (CommonException e) {
			throw e;
		} catch (Exception e) {
			log.error("분석 상태 조회 예상치 못한 오류 - userId: {}, analysisId: {}", userDetails != null ? userDetails.getUsername() : "unknown", analysisId, e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 내 분석 목록 조회
	 * GET /api/v1/analysis/my
	 */
	@GetMapping("/my")
	public ResponseEntity<HttpResponseDto> getMyAnalyses(
			@AuthenticationPrincipal UserDetails userDetails,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int limit
	) {
		try {
			if (userDetails == null) {
				return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS);
			}
			
			log.info("내 분석 목록 조회 - userId: {}, page: {}, limit: {}", userDetails.getUsername(), page, limit);

			// 1-based page to 0-based page, ensure positive values
			int safePage = Math.max(page - 1, 0);
			int safeLimit = Math.max(Math.min(limit, 50), 1);
			Pageable pageable = PageRequest.of(safePage, safeLimit);
			MyAnalysesResponse response = analysisService.getMyAnalyses(userDetails.getUsername(), pageable);

			return ResponseUtils.of(
					ResponseCodeEnum.SUCCESS,
					response
			);
		} catch (Exception e) {
			log.error("내 분석 목록 조회 실패 - userId: {}, page: {}, limit: {}", userDetails != null ? userDetails.getUsername() : "unknown", page, limit, e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 분석 취소
	 * DELETE /api/v1/analysis/{analysisId}
	 */
	@DeleteMapping("/{analysisId}")
	public ResponseEntity<HttpResponseDto> cancelAnalysis(
			@AuthenticationPrincipal UserDetails userDetails,
			@PathVariable Long analysisId
	) {
		try {
			if (userDetails == null) {
				return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS);
			}
			
			log.info("분석 취소 요청 - userId: {}, analysisId: {}", userDetails.getUsername(), analysisId);

			analysisService.cancelAnalysis(userDetails.getUsername(), analysisId);

			return ResponseUtils.of(
					ResponseCodeEnum.SUCCESS,
					Map.of("message", "분석이 취소되었습니다.")
			);
		} catch (CommonException e) {
			throw e;
		} catch (Exception e) {
			log.error("분석 취소 예상치 못한 오류 - userId: {}, analysisId: {}", userDetails != null ? userDetails.getUsername() : "unknown", analysisId, e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 분석 재시작
	 * POST /api/v1/analysis/{analysisId}/restart
	 */
	@PostMapping("/{analysisId}/restart")
	public ResponseEntity<HttpResponseDto> restartAnalysis(
			@AuthenticationPrincipal UserDetails userDetails,
			@PathVariable Long analysisId,
			@RequestBody AnalysisRestartRequest request
	) {
		try {
			if (userDetails == null) {
				return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS);
			}
			
			log.info("분석 재시작 요청 - userId: {}, analysisId: {}", userDetails.getUsername(), analysisId);

			AnalysisResponse response = analysisService.restartAnalysis(userDetails.getUsername(), analysisId, request);

			return ResponseUtils.of(
					ResponseCodeEnum.SUCCESS,
					response
			);
		} catch (CommonException e) {
			throw e;
		} catch (Exception e) {
			log.error("분석 재시작 예상치 못한 오류 - userId: {}, analysisId: {}", userDetails != null ? userDetails.getUsername() : "unknown", analysisId, e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 분석 사용량 체크
	 * GET /api/v1/analysis/usage
	 */
	@GetMapping("/usage")
	public ResponseEntity<HttpResponseDto> checkAnalysisUsage(
			@AuthenticationPrincipal UserDetails userDetails
	) {
		try {
			if (userDetails == null) {
				return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS);
			}
			
			log.info("분석 사용량 체크 - userId: {}", userDetails.getUsername());

			boolean canStart = analysisService.canStartNewAnalysis(userDetails.getUsername());

			return ResponseUtils.of(
					ResponseCodeEnum.SUCCESS,
					Map.of(
						"canStartNewAnalysis", canStart,
						"message", canStart ? "새로운 분석을 시작할 수 있습니다." : "일일 분석 한도에 도달했습니다."
					)
			);
		} catch (Exception e) {
			log.error("분석 사용량 체크 실패 - userId: {}", userDetails != null ? userDetails.getUsername() : "unknown", e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
		}
	}
}