package com.example.borrowing_service.client;

import com.example.borrowing_service.exception.DownstreamServiceException;
import com.example.borrowing_service.exception.MemberNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class MemberServiceClient {

    private final RestClient restClient;

    public MemberServiceClient(@Qualifier("memberServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Throws MemberNotFoundException if Member Service returns 404, or
     * DownstreamServiceException if Member Service is unreachable/erroring.
     * A successful response (member exists) simply returns.
     */
    public MemberView getMemberOrThrow(Long memberId) {
        try {
            return restClient.get()
                    .uri("/members/{id}", memberId)
                    .retrieve()
                    .body(MemberView.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new MemberNotFoundException(memberId);
        } catch (ResourceAccessException ex) {
            throw new DownstreamServiceException("Member Service is unreachable: " + ex.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            throw new DownstreamServiceException("Member Service returned an unexpected error: " + ex.getStatusCode());
        }
    }
}
