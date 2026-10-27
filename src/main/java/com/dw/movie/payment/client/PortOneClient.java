package com.dw.movie.payment.client;

import com.dw.movie.payment.exception.PaymentVerificationFailedException;
import com.dw.movie.payment.exception.PortOneApiException;
import com.dw.movie.payment.exception.RefundFailedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class PortOneClient {

    private static final Logger log = LoggerFactory.getLogger(PortOneClient.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String apiSecret;
    private final boolean includeSandbox;

    public PortOneClient(@Value("${portone.api-key}") String apiKey, @Value("${portone.api-secret}") String apiSecret,
                         @Value("${portone.include-sandbox:false}") boolean includeSandbox) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.includeSandbox = includeSandbox;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.iamport.kr")
                .requestFactory(requestFactory)
                .build();
    }

    public PaymentInfo getPaymentInfo(String impUid) {
        String accessToken = getAccessToken();

        PaymentApiResponse response;
        try {
            response = restClient.get()
                    .uri("/payments/{impUid}?include_sandbox={includeSandbox}", impUid, includeSandbox)
                    .header("Authorization", accessToken)
                    .retrieve()
                    .body(PaymentApiResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("포트원 결제 조회 실패 (impUid={}, status={}): {}", impUid, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 404) {
                throw new PaymentVerificationFailedException("포트원에서 결제 정보를 찾을 수 없습니다: " + impUid);
            }
            throw new PortOneApiException("포트원 결제 조회에 실패했습니다.");
        } catch (RestClientException e) {
            log.warn("포트원 결제 조회 실패 (impUid={}): {}", impUid, e.getMessage());
            throw new PortOneApiException("포트원 결제 조회에 실패했습니다.");
        }

        if (response == null || response.code() != 0 || response.response() == null) {
            throw new PaymentVerificationFailedException("존재하지 않는 결제 정보입니다: " + impUid);
        }

        return response.response();
    }

    public void cancelPayment(String impUid, String reason) {
        String accessToken = getAccessToken();

        PaymentApiResponse response;
        try {
            response = restClient.post()
                    .uri("/payments/cancel")
                    .header("Authorization", accessToken)
                    .body(new CancelRequest(impUid, reason))
                    .retrieve()
                    .body(PaymentApiResponse.class);
        } catch (RestClientException e) {
            throw new RefundFailedException("포트원 환불 요청에 실패했습니다.");
        }

        if (response == null || response.code() != 0) {
            throw new RefundFailedException("환불에 실패했습니다: " + impUid);
        }
    }

    private String getAccessToken() {
        TokenRequest request = new TokenRequest(apiKey, apiSecret);

        TokenApiResponse response;
        try {
            response = restClient.post()
                    .uri("/users/getToken")
                    .body(request)
                    .retrieve()
                    .body(TokenApiResponse.class);
        } catch (RestClientException e) {
            throw new PortOneApiException("포트원 인증 토큰 발급에 실패했습니다.");
        }

        if (response == null || response.code() != 0 || response.response() == null) {
            throw new PortOneApiException("포트원 인증 토큰 발급에 실패했습니다.");
        }

        return response.response().accessToken();
    }

    private record TokenRequest(
            @JsonProperty("imp_key") String impKey,
            @JsonProperty("imp_secret") String impSecret
    ) {}

    private record CancelRequest(
            @JsonProperty("imp_uid") String impUid,
            String reason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenApiResponse(Integer code, String message, TokenResponse response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PaymentApiResponse(Integer code, String message, PaymentInfo response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentInfo(
            @JsonProperty("imp_uid") String impUid,
            Long amount,
            String status
    ) {}
}