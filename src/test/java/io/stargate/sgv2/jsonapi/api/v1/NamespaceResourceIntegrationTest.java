package io.stargate.sgv2.jsonapi.api.v1;

import static io.restassured.RestAssured.given;
import static io.stargate.sgv2.common.IntegrationTestUtils.getAuthToken;
import static org.hamcrest.Matchers.blankString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.stargate.sgv2.api.common.config.constants.HttpConstants;
import io.stargate.sgv2.common.CqlEnabledIntegrationTestBase;
import io.stargate.sgv2.common.testresource.StargateTestResource;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusIntegrationTest
@QuarkusTestResource(StargateTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NamespaceResourceIntegrationTest extends CqlEnabledIntegrationTestBase {

  @BeforeAll
  public static void enableLog() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @Nested
  class CreateCollection {

    @Test
    public void happyPath() {
      String json =
          """
          {
            "createCollection": {
              "name": "%s"
            }
          }
          """
              .formatted("col" + RandomStringUtils.randomNumeric(16));

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(json)
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body("status.ok", is(1));
    }
  }

  @Nested
  class DeleteCollection {

    @Test
    public void happyPath() {
      String collection = RandomStringUtils.randomAlphabetic(16);

      // first create
      String createJson =
          """
          {
            "createCollection": {
              "name": "%s"
            }
          }
          """
              .formatted(collection);

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(createJson)
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body("status.ok", is(1));

      // then delete
      String json =
          """
          {
            "deleteCollection": {
              "name": "%s"
            }
          }
          """
              .formatted(collection);

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(json)
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body("status.ok", is(1));
    }

    @Test
    public void notExisting() {
      String collection = RandomStringUtils.randomAlphabetic(16);

      // delete not existing
      String json =
          """
          {
            "deleteCollection": {
              "name": "%s"
            }
          }
          """
              .formatted(collection);

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(json)
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body("status.ok", is(1));
    }

    @Test
    public void invalidCommand() {
      String json =
          """
          {
            "deleteCollection": {
            }
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(json)
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body("errors[0].message", is(not(blankString())))
          .body("errors[0].exceptionClass", is("ConstraintViolationException"));
    }
  }

  @Nested
  class ClientErrors {

    @Test
    public void tokenMissing() {
      given()
          .contentType(ContentType.JSON)
          .body("{}")
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body(
              "errors[0].message",
              is(
                  "Role unauthorized for operation: Missing token, expecting one in the X-Cassandra-Token header."));
    }

    @Test
    public void malformedBody() {
      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body("{wrong}")
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body("errors[0].message", is(not(blankString())))
          .body("errors[0].exceptionClass", is("JsonParseException"));
    }

    @Test
    public void unknownCommand() {
      String json =
          """
          {
            "unknownCommand": {
            }
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(json)
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body("errors[0].message", startsWith("Could not resolve type id 'unknownCommand'"))
          .body("errors[0].exceptionClass", is("InvalidTypeIdException"));
    }

    @Test
    public void emptyBody() {
      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .when()
          .post(NamespaceResource.BASE_PATH, keyspaceId.asInternal())
          .then()
          .statusCode(200)
          .body("errors[0].message", is(not(blankString())))
          .body("errors[0].exceptionClass", is("ConstraintViolationException"));
    }
  }
}
