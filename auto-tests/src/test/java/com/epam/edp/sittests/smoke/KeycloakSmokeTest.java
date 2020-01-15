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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import com.openshift.internal.restclient.model.Secret;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.ClientBuilder;
import io.qameta.allure.Feature;
import com.openshift.restclient.IClient;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

import static com.epam.edp.sittests.smoke.StringConstants.OPENSHIFT_MASTER_URL;
import static com.epam.edp.sittests.smoke.StringConstants.OPENSHIFT_PASSWORD;
import static com.epam.edp.sittests.smoke.StringConstants.OPENSHIFT_USERNAME;

/**
 * @author Pavlo_Yemelianov
 */
public class KeycloakSmokeTest {
    private String keycloakAccessToken;
    private String realm;
    private IClient openShiftClient;

    @Feature("Setup Openshift Client")
    @BeforeClass
    public void setUpOpenShiftClient() {
        this.openShiftClient = new ClientBuilder()
                .toCluster(OPENSHIFT_MASTER_URL)
                .withUserName(OPENSHIFT_USERNAME)
                .withPassword(OPENSHIFT_PASSWORD)
                .sslCertCallbackWithDefaultHostnameVerifier(false)
                .build();
    }

    @BeforeClass
    @Parameters("ocpEdpPrefix")
    public void setUp(String ocpEdpPrefix) {
        this.realm = StringUtils.isEmpty(ocpEdpPrefix) ? "edp" : ocpEdpPrefix + "-edp-cicd-main";
    }

    @BeforeMethod
    public void setUpAccessToken() {
        Secret secret = openShiftClient.get(ResourceKind.SECRET, "keycloak", "security");

        String userName = new String(secret.getData("username")).trim();
        String userPassword = new String(secret.getData("password")).trim();

        this.keycloakAccessToken = given()
                .contentType(ContentType.URLENC)
                .param("client_id", "admin-cli")
                .param("grant_type", "password")
                .param("username", userName)
                .param("password", userPassword)
                .when()
                .post(StringConstants.KEYCLOAK_URL + "/auth/realms/master/protocol/openid-connect/token")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(ContentType.JSON)
                .extract()
                .path("access_token");
    }

    @Test
    public void testKeycloakShouldHaveRealmCI() {
        given().pathParam("realm", realm)
                .auth()
                .oauth2(keycloakAccessToken)
                .when()
                .get(StringConstants.KEYCLOAK_URL + "/auth/admin/realms/{realm}")
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void testKeycloakShouldHaveDeveloperRolesInRealmCI() {
        given().pathParam("realm", realm)
                .auth()
                .oauth2(keycloakAccessToken)
                .when()
                .get(StringConstants.KEYCLOAK_URL + "/auth/admin/realms/{realm}/roles")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("findAll().name", hasItems("sonar-users", "jenkins-users", "gerrit-users"));
    }

    @Test
    public void testKeycloakShouldHaveAdministratorRolesInRealmCI() {
        given().pathParam("realm", realm)
                .auth()
                .oauth2(keycloakAccessToken)
                .when()
                .get(StringConstants.KEYCLOAK_URL + "/auth/admin/realms/{realm}/roles")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("findAll().name", hasItems("sonar-administrators", "jenkins-administrators", "gerrit-administrators"));
    }

    @Test
    public void testKeycloakShouldHaveCompositeRolesInRealmCI() {
        given().pathParam("realm", realm)
                .auth()
                .oauth2(keycloakAccessToken)
                .when()
                .get(StringConstants.KEYCLOAK_URL + "/auth/admin/realms/{realm}/roles")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("findAll {it.composite == true}.name", hasItems("administrator", "developer"));
    }

    @Test
    public void testKeycloakCreatedUserHasRoleDeveloperByDefault() {
        String userLocation = given()
                .contentType(ContentType.JSON)
                .body("{\"username\": \"test\"}")
                .pathParam("realm", realm)
                .auth()
                .oauth2(keycloakAccessToken)
                .when()
                .post(StringConstants.KEYCLOAK_URL + "/auth/admin/realms/{realm}/users")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .header("Location");

        given().auth()
                .oauth2(keycloakAccessToken)
                .when()
                .get(userLocation + "/role-mappings")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("realmMappings.findAll().name", hasItems("developer"));

        given().auth()
                .oauth2(keycloakAccessToken)
                .when()
                .delete(userLocation);
    }
}
