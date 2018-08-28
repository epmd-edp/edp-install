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

import org.apache.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import static com.epam.edp.sittests.smoke.StringConstants.OPENSHIFT_CICD_NAMESPACE;
import static com.epam.edp.sittests.smoke.StringConstants.RABBITMQ_SERVICE_NAME;
import static io.restassured.RestAssured.given;

/**
 * @author Alexander Morozov
 */
public class AddServiceSmokeTest {
    private UrlBuilder urlBuilder;
    private String ocpEdpPrefix;

    @BeforeClass
    @Parameters("ocpEdpPrefix")
    public void setUp(String ocpEdpPrefix) {
        this.urlBuilder = new UrlBuilder(ocpEdpPrefix);
        this.ocpEdpPrefix = ocpEdpPrefix;
    }

    @Test
    public void testServiceTemplateHasBeenAdded() {
        given().log().all()
                .pathParam("service", RABBITMQ_SERVICE_NAME)
                .pathParam("project", ocpEdpPrefix + "-edp")
                .urlEncodingEnabled(false)
                .when()
                .get(urlBuilder.buildUrl("https",
                        "gerrit", OPENSHIFT_CICD_NAMESPACE,
                        "projects/{project}/branches/master/files/deploy-templates%2F{service}.yaml/content"))
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
}
