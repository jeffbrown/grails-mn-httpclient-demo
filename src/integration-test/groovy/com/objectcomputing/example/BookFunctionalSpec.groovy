package com.objectcomputing.example

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Integration
class BookFunctionalSpec extends Specification {

    @Shared
    @AutoCleanup
    HttpClient client

    @Autowired
    BookClient bookClient

    @Shared
    String baseUrl


    @OnceBefore
    void init() {
        this.client  = HttpClient.create(new URL(Test.BASE_URL))
    }

    String getResourcePath() {
        "/book"
    }

    Map getValidJson() {
        ["title": "The Stand"]
    }

    Map getInvalidJson() {
        ['title': '']
    }

    void "Test the index action"() {
        when:"The index action is requested"
        HttpResponse<List<Map>> response = bookClient.index()

        then:"The response is correct"
        response.status == HttpStatus.OK
        response.body() == []
    }

    @Rollback
    void "Test the save action correctly persists an instance"() {
        when:"The save action is executed with no content"
        client.toBlocking().exchange(HttpRequest.POST(resourcePath, ""))

        then:"The response is correct"
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.UNPROCESSABLE_ENTITY

        when:"The save action is executed with invalid data"
        client.toBlocking().exchange(HttpRequest.POST(resourcePath, invalidJson))

        then:"The response is correct"
        e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.UNPROCESSABLE_ENTITY

        when:"The save action is executed with valid data"
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST(resourcePath, validJson), Map)

        then:"The response is correct"
        response.status == HttpStatus.CREATED
        response.body().id
        Book.count() == 1

        cleanup:
        def id = response.body().id
        def path = "${resourcePath}/${id}"
        response = client.toBlocking().exchange(HttpRequest.DELETE(path))
        assert response.status() == HttpStatus.NO_CONTENT
    }

    void "Test the update action correctly updates an instance"() {
        when:"The save action is executed with valid data"
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST(resourcePath, validJson), Map)

        then:"The response is correct"
        response.status == HttpStatus.CREATED
        response.body().id

        when:"The update action is called with invalid data"
        String path = "${resourcePath}/${response.body().id}"
        client.toBlocking().exchange(HttpRequest.PUT(path, invalidJson), Map)

        then: "The response is unprocessable entity"
        path
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.UNPROCESSABLE_ENTITY

        when: "The update action is called with valid data"
        response = client.toBlocking().exchange(HttpRequest.PUT(path, validJson), Map)

        then:"The response is correct"
        response.status == HttpStatus.OK
        response.body()

        cleanup:
        response = client.toBlocking().exchange(HttpRequest.DELETE(path))
        assert response.status() == HttpStatus.NO_CONTENT
    }

    void "Test the show action correctly renders an instance"() {
        when:"The save action is executed with valid data"
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST(resourcePath, validJson), Map)

        then:"The response is correct"
        response.status == HttpStatus.CREATED
        response.body().id

        when:"When the show action is called to retrieve a resource"
        def id = response.body().id
        String path = "${resourcePath}/${id}"
        response = client.toBlocking().exchange(HttpRequest.GET(path), Map)

        then:"The response is correct"
        response.status == HttpStatus.OK
        response.body().id == id

        cleanup:
        client.toBlocking().exchange(HttpRequest.DELETE(path))
    }

    @Rollback
    void "Test the delete action correctly deletes an instance"() {
        when:"The save action is executed with valid data"
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST(resourcePath, validJson), Map)

        then:"The response is correct"
        response.status == HttpStatus.CREATED
        response.body().id

        when:"When the delete action is executed on an unknown instance"
        def id = response.body().id
        def path = "${resourcePath}/99999"
        client.toBlocking().exchange(HttpRequest.DELETE(path))

        then:"The response is correct"
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.NOT_FOUND

        when:"When the delete action is executed on an existing instance"
        path = "${resourcePath}/${id}"
        response = client.toBlocking().exchange(HttpRequest.DELETE(path))

        then:"The response is correct"
        response.status == HttpStatus.NO_CONTENT
    }
}
