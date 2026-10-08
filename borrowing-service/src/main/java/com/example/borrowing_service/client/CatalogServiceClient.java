package com.example.borrowing_service.client;

import com.example.borrowing_service.exception.BookNotFoundException;
import com.example.borrowing_service.exception.DownstreamServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class CatalogServiceClient {

    private final RestClient restClient;

    public CatalogServiceClient(@Qualifier("catalogServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** Throws BookNotFoundException if Catalog Service returns 404. */
    public BookView getBookOrThrow(Long bookId) {
        try {
            return restClient.get()
                    .uri("/books/{id}", bookId)
                    .retrieve()
                    .body(BookView.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new BookNotFoundException(bookId);
        } catch (ResourceAccessException ex) {
            throw new DownstreamServiceException("Catalog Service is unreachable: " + ex.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            throw new DownstreamServiceException("Catalog Service returned an unexpected error: " + ex.getStatusCode());
        }
    }
}
