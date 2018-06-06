/* Copyright 2018 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License. */

package com.epam.edp.sittests.smoke;

import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

/**
 * @author Pavlo_Yemelianov
 */
public class KeycloakSmokeTest {
    private UrlBuilder urlBuilder;
    private String keycloakAccessToken;

    @BeforeClass
    @Parameters("ocpEdpSuffix")
    public void setUp(String ocpEdpSuffix) {
        this.urlBuilder = new UrlBuilder(ocpEdpSuffix);
    }

    @BeforeMethod
    public void setUpAccessToken() {
        this.keycloakAccessToken =
        given()
            .contentType(ContentType.URLENC)
            .param("client_id", "admin-cli")
            .param("grant_type", "password")
            .param("username", "admin")
            .param("password", "admin")
        .when()
            .post(urlBuilder.buildUrl("https",
                    "keycloak",
                    "edp-cockpit",
                    "auth/realms/master/protocol/openid-connect/token"))
        .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(ContentType.JSON)
        .extract()
            .path("access_token");
    }

    @Test
    public void testKeycloakShouldHaveRealmCI() {
        given()
            .pathParam("realm", "CI")
            .auth()
            .oauth2(keycloakAccessToken)
        .when()
            .get(urlBuilder.buildUrl("https",
                    "keycloak",
                    "edp-cockpit",
                    "auth/admin/realms/{realm}"))
        .then()
            .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void testKeycloakShouldHaveJenkinsRolesInRealmCI() {
        given()
            .pathParam("realm", "CI")
            .auth()
            .oauth2(keycloakAccessToken)
        .when()
            .get(urlBuilder.buildUrl("https",
                    "keycloak",
                    "edp-cockpit",
                    "auth/admin/realms/{realm}/roles"))
        .then()
            .statusCode(HttpStatus.SC_OK)
            .body("findAll().name", hasItems("jenkins-dev", "jenkins-qa", "jenkins-admin"));
    }

    @Test
    public void testKeycloakShouldHaveGerritRolesInRealmCI() {
        given()
            .pathParam("realm", "CI")
            .auth()
            .oauth2(keycloakAccessToken)
        .when()
            .get(urlBuilder.buildUrl("https",
                    "keycloak",
                    "edp-cockpit",
                    "auth/admin/realms/{realm}/roles"))
        .then()
            .statusCode(HttpStatus.SC_OK)
            .body("findAll().name", hasItems("gerrit-dev", "gerrit-reviewer", "gerrit-admin", "gerrit-user"));
    }

    @Test
    public void testKeycloakShouldHaveCompositeRolesInRealmCI() {
        given()
            .pathParam("realm", "CI")
            .auth()
            .oauth2(keycloakAccessToken)
        .when()
            .get(urlBuilder.buildUrl("https",
                    "keycloak",
                    "edp-cockpit",
                    "auth/admin/realms/{realm}/roles"))
        .then()
            .statusCode(HttpStatus.SC_OK)
            .body("findAll {it.composite == true}.name", hasItems("developer", "teamlead", "qa", "devops"));
    }

    @Test
    public void testKeycloakCreatedUserHasRoleDeveloperByDefault() {
        String userLocation =
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\": \"test\"}")
            .pathParam("realm", "CI")
            .auth()
            .oauth2(keycloakAccessToken)
        .when()
            .post(urlBuilder.buildUrl("https",
                    "keycloak",
                    "edp-cockpit",
                    "auth/admin/realms/{realm}/users"))
        .then()
            .statusCode(HttpStatus.SC_CREATED)
        .extract()
            .header("Location");

        given()
            .auth()
            .oauth2(keycloakAccessToken)
        .when()
            .get(userLocation + "/role-mappings")
        .then()
            .statusCode(HttpStatus.SC_OK)
            .body("realmMappings.findAll().name", hasItems("developer"));

        given()
            .auth()
            .oauth2(keycloakAccessToken)
        .when()
            .delete(userLocation);
    }
}
